package br.ufs.dcomp.ExemploRabbitMQ;

// protoc --java_out=src/main/java/ src/main/proto/*.proto

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.AMQP.Exchange;

import br.ufs.dcomp.RabbitChat.Proto;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

public class RabbitChat
{
    private Connection _connection;
    private Channel _channel;
    private String _username;
    private String _queueName;
    private String _groupName;
    private java.util.function.Consumer<Proto.Message> _onMessageSendedCallback;
    private java.util.function.Consumer<Proto.Message> _onMessageReceivedCallback;
    private Thread CONSUMER_THREAD;

    public RabbitChat(Connection connection, String username) throws IOException
    {
        _connection = connection;
        _username = username;

        _channel = _connection.createChannel();

        _channel.queueDeclare(_username, false, false, true, null);

        CONSUMER_THREAD = new Thread() {
            @Override
            public void run() {
                try {
                    consumeMessages();
                } catch (Exception error) {
                    System.out.println(error);
                }
            }
        };
        CONSUMER_THREAD.start();
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
        if (_groupName != null) {
            sendMessageToGroup(message);
        } else if (_queueName != null) {
            sendMessageToUser(message);
        }
    }

    private void sendMessageToUser(String message) throws IOException
    {
        Instant time = Instant.now();
        
        Proto.Message toSend = Proto.Message.newBuilder()
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
            )
            .build();

        _channel.basicPublish("", _queueName, null, toSend.toByteArray());

        if (_onMessageSendedCallback != null) {
            _onMessageSendedCallback.accept(toSend);
        }
    }

    private void sendMessageToGroup(String message) throws IOException
    {
        Instant time = Instant.now();
        
        Proto.Message toSend = Proto.Message.newBuilder()
            .setEmiter(_username)
            .setTimestamp(
                Timestamp.newBuilder()
                    .setSeconds(time.getEpochSecond())
                    .setNanos(time.getNano())
            )
            .setGroup(_groupName)
            .setContent(
                Proto.Content.newBuilder()
                    .setType("text/plain")
                    .setBody(ByteString.copyFrom(message.getBytes()))
            )
            .build();

        _channel.basicPublish(_groupName, "", null, toSend.toByteArray());

        if (_onMessageSendedCallback != null) {
            _onMessageSendedCallback.accept(toSend);
        }
    }

    public Queue.DeclareOk declareQueue(String queueName) throws IOException
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

    private String consumeMessages() throws IOException
    {
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
      
        return _channel.basicConsume(_username, true, consumer);
    }

    public void onMessageSended(java.util.function.Consumer<Proto.Message> onMessageSendedCallback)
    {
        _onMessageSendedCallback = onMessageSendedCallback;
    }
    
    public void onMessageReceived(java.util.function.Consumer<Proto.Message> onMessageReceivedCallback)
    {
        _onMessageReceivedCallback = onMessageReceivedCallback;
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