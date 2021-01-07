package br.ufs.dcomp.ExemploRabbitMQ;

// protoc --java_out=src/main/java/ src/main/proto/*.proto

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.AMQP.Exchange;

import br.ufs.dcomp.RabbitChat.Proto;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

public class RabbitChat
{
    private Connection _connection;
    private Channel _channel;
    private Channel _fileChannel;
    private String _username;
    private String _queueName;
    private String _groupName;
    private java.util.function.Consumer<Proto.Message> _onMessageSendedCallback;
    private java.util.function.Consumer<Proto.Message> _onMessageReceivedCallback;
    private java.util.function.Consumer<Proto.Message> _onFileSendedCallback;
    private java.util.function.Consumer<Proto.Message> _onFileReceivedCallback;
    private Thread MESSAGE_CONSUMER_THREAD;
    private Thread FILE_CONSUMER_THREAD;

    public RabbitChat(Connection connection, String username) throws IOException
    {
        _connection = connection;
        _username = username;

        _channel = _connection.createChannel();
        _channel.queueDeclare(_username, false, false, true, null);

        _fileChannel = _connection.createChannel();
        _fileChannel.queueDeclare(_username + "-files", false, false, true, null);

        MESSAGE_CONSUMER_THREAD = new Thread() {
            @Override
            public void run() {
                try {
                    Consumer consumer = new DefaultConsumer(_channel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
                        {
                            Proto.Message message = Proto.Message.parseFrom(body);
            
                            if (_onMessageReceivedCallback != null) {
                                _onMessageReceivedCallback.accept(message);
                            }
                        }
                    };
                  
                    _channel.basicConsume(_username, true, consumer);
                } catch (Exception error) {
                    System.out.println(error);
                }
            }
        };

        FILE_CONSUMER_THREAD = new Thread() {
            @Override
            public void run() {
                try {
                    Consumer consumer = new DefaultConsumer(_fileChannel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
                        {
                            Proto.Message message = Proto.Message.parseFrom(body);
            
                            if (_onFileReceivedCallback != null) {
                                _onFileReceivedCallback.accept(message);
                            }
                        }
                    };
                  
                    _fileChannel.basicConsume(_username + "-files", true, consumer);
                } catch (Exception error) {
                    System.out.println(error);
                }
            }
        };

        MESSAGE_CONSUMER_THREAD.start();
        FILE_CONSUMER_THREAD.start();
    }
    
    public static Connection makeConnection(String username, String password, String host, String virtualHost) throws IOException, TimeoutException
    {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setHost(host);
        factory.setVirtualHost(virtualHost);

        return factory.newConnection();
    }

    public void sendMessage(String message) throws IOException
    {
        Instant time = Instant.now();
        
        Proto.Message.Builder builder = Proto.Message.newBuilder()
            .setEmiter(_username)
            .setTimestamp(
                Timestamp.newBuilder()
                    .setSeconds(time.getEpochSecond())
                    .setNanos(time.getNano())
            )
            .setContent(
                Proto.Content.newBuilder()
                    .setType("text/plain")
                    .setBody(ByteString.copyFrom(message.getBytes()))
            );
        
        if (_groupName != null) {
            builder = builder.setGroup(_groupName);
        }

        Proto.Message toSend = builder.build();

        Thread thread = sendToChannel(_channel, "", toSend.toByteArray(), () -> {
            if (_onMessageSendedCallback != null) {
                _onMessageSendedCallback.accept(toSend);
            }
        });

        thread.start();
    }

    public void sendFile(String filename) throws IOException
    {
        Path source = Paths.get(filename);
        String mime = Files.probeContentType(source);

        byte[] bytes = Files.readAllBytes(source);
        
        Instant time = Instant.now();

        Proto.Message.Builder builder = Proto.Message.newBuilder()
            .setEmiter(_username)
            .setTimestamp(
                Timestamp.newBuilder()
                    .setSeconds(time.getEpochSecond())
                    .setNanos(time.getNano())
            )
            .setContent(
                Proto.Content.newBuilder()
                    .setType(mime)
                    .setBody(ByteString.copyFrom(bytes))
                    .setName(source.getFileName().toString())
            );
        
        if (_groupName != null) {
            builder = builder.setGroup(_groupName);
        }

        Proto.Message toSend = builder.build();

        Thread thread = sendToChannel(_fileChannel, "-files", toSend.toByteArray(), () -> {
            if (_onFileSendedCallback != null) {
                _onFileSendedCallback.accept(toSend);
            }
        });
        
        thread.start();        
    }

    public Queue.DeclareOk declareUser(String queueName) throws IOException
    {
        Queue.DeclareOk ok = _channel.queueDeclare(queueName, false, false, true, null);
        setQueue(queueName);
        return ok;
    }

    public Exchange.DeclareOk declareGroup(String groupName) throws IOException
    {
        Exchange.DeclareOk ok = _channel.exchangeDeclare(groupName, "fanout");
        addUserToGroup(_username, groupName);
        setGroup(groupName);
        return ok;
    }

    public void setQueue(String queueName)
    {
        _queueName = queueName;
        _groupName = null;
    }

    public void setGroup(String groupName)
    {
        _groupName = groupName;
        _queueName = null;
    }

    public Queue.BindOk addUserToGroup(String username, String groupName) throws IOException
    {
        return _channel.queueBind(username, groupName, "");
    }

    public Queue.UnbindOk removeUserFromGroup(String username, String groupName) throws IOException
    {
        return _channel.queueUnbind(username, groupName, "");
    }

    public Exchange.DeleteOk removeGroup(String groupName) throws IOException
    {
        return _channel.exchangeDelete(groupName);
    }

    private Thread sendToChannel(Channel channel, String posfix, byte[] bytes, Runnable callback)
    {
        return new Thread() {
            @Override
            public void run() {
                try {
                    if (_groupName != null) {
                        channel.basicPublish(_groupName + posfix, "", null, bytes);
                    } else if (_queueName != null) {
                        channel.basicPublish("", _queueName + posfix, null, bytes);
                    }
                    if (callback != null) {
                        callback.run();
                    }
                } catch (Exception error) {
                    System.out.println(error);
                }
            }
        };
    }

    public void onMessageSended(java.util.function.Consumer<Proto.Message> onMessageSendedCallback)
    {
        _onMessageSendedCallback = onMessageSendedCallback;
    }
    
    public void onMessageReceived(java.util.function.Consumer<Proto.Message> onMessageReceivedCallback)
    {
        _onMessageReceivedCallback = onMessageReceivedCallback;
    }

    public void onFileSended(java.util.function.Consumer<Proto.Message> onFileSendedCallback)
    {
        _onFileSendedCallback = onFileSendedCallback;
    }
    
    public void onFileReceived(java.util.function.Consumer<Proto.Message> onFileReceivedCallback)
    {
        _onFileReceivedCallback = onFileReceivedCallback;
    }

    public String getQueue()
    {
        return _queueName;
    }

    public String getGroup()
    {
        return _groupName;
    }
}