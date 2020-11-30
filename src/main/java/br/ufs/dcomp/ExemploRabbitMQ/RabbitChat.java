package br.ufs.dcomp.ExemploRabbitMQ;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RabbitChat
{
    private Connection _connection;
    private Channel _channel;
    private String _username;
    private String _queueName;
    private ArrayList<ChatMessage> _messages;
    private java.util.function.Consumer<ArrayList<ChatMessage>> _newMessageCallback;

    public RabbitChat(Connection connection, String username)
    {
        _connection = connection;
        _username = username;
        _messages = new ArrayList<>();
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
        Gson gson = new Gson();

        String jsonMessage = gson.toJson(new ChatMessage(_username, message));

        _channel.basicPublish("", _queueName, null, jsonMessage.getBytes());
    }

    private String watchMessages() throws IOException
    {
        Consumer consumer = new DefaultConsumer(_channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException
            {
                Gson gson = new Gson();

                ChatMessage chatMessage = gson.fromJson(new String(body, "UTF-8"), ChatMessage.class);

                _messages.add(chatMessage);

                if (_newMessageCallback != null) {
                    _newMessageCallback.accept(_messages);
                }
            }
        };
      
        return _channel.basicConsume(_queueName, true, consumer);
    }
    
    public void onNewMessage(java.util.function.Consumer<ArrayList<ChatMessage>> newMessageCallback)
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

    public class ChatMessage
    {
        private String _username;
        private String _message;
        private String _datetime;

        public ChatMessage(String username, String message)
        {
            _username = username;
            _message = message;

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter dateFormater = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            DateTimeFormatter timeFormater = DateTimeFormatter.ofPattern("HH:mm");

            _datetime = "(" + now.format(dateFormater) + " ás " + now.format(timeFormater) + ")";
        }

        @Override
        public String toString() {
            return _datetime + " " + _username + " diz " + _message;
        }
    }
}