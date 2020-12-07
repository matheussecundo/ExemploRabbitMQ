package br.ufs.dcomp.ExemploRabbitMQ;

// protoc --java_out=src/main/java/ src/main/proto/*.proto

import com.rabbitmq.client.*;
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
    private java.util.function.Consumer<Proto.Message> _newMessageCallback;

    public RabbitChat(Connection connection, String username)
    {
        _connection = connection;
        _username = username;
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

    public void setQueue(String queueName) throws IOException
    {
        Channel channel = _connection.createChannel();

        channel.queueDeclare(queueName, false, false, true, null);

        _queueName = queueName;
        _channel = channel;

        new Thread() {
            @Override
            public void run() {
                try {
                    watchMessages();
                } catch (Exception error) {
                    System.out.println(error);
                }
            }
        }.start();
    }

    public void sendMessage(String message) throws IOException
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
    }

    private String watchMessages() throws IOException
    {
        Consumer consumer = new DefaultConsumer(_channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
            {
                Proto.Message message = Proto.Message.parseFrom(body);

                if (_newMessageCallback != null) {
                    _newMessageCallback.accept(message);
                }
            }
        };
      
        return _channel.basicConsume(_queueName, true, consumer);
    }
    
    public void onNewMessage(java.util.function.Consumer<Proto.Message> newMessageCallback)
    {
        _newMessageCallback = newMessageCallback;
    }

    public boolean isQueueConnected()
    {
        return _channel != null;
    }

    public String getQueueName()
    {
        return _queueName;
    }
}