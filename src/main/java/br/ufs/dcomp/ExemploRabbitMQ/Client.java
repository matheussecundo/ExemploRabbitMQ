package br.ufs.dcomp.ExemploRabbitMQ;

import com.rabbitmq.client.*;

import java.io.IOException;

import java.util.Scanner;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// mvn exec:java -D exec.mainClass=br.ufs.dcomp.ExemploRabbitMQ.Client
// mvn compile assembly:single
// java -jar target/Client.jar
// ssh -i rabbit-key.pem ubuntu@18.209.225.188

public class Client {

  private static String username;

  private static String channelName;

  private static Scanner keyboard = new Scanner(System.in);

  public static void main(String[] argv) throws Exception {
    System.out.print("User: "); username = keyboard.nextLine();

    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername("sieghart");
    factory.setPassword("rabbit");
    factory.setHost("ec2-3-85-41-246.compute-1.amazonaws.com");
    factory.setVirtualHost("/");

    Connection connection = factory.newConnection();

    Channel channel = null;

    while (true)
    {
      String readed = prompt();

      if (readed.equals("exit")) {
        break;
      }

      if (readed.contains("@")) {
        channelName = readed.replace("@", "");

        channel = declareQueue(connection, channelName);
      } else {
        if (channel != null) {
          channel.basicPublish("", channelName, null, readed.getBytes("UTF-8"));
        }
      }
      
    }

    if (channel != null) {
      channel.close();
    }
    connection.close();
  }

  private static String prompt() {
    if (channelName != null) {
      System.out.print("@" + channelName + ">>");
    } else {
      System.out.print(">>");
    }
    
    return keyboard.nextLine().trim();
  }

  private static Channel declareQueue(Connection connection, String queue) throws IOException {
    Channel channel = connection.createChannel();

    channel.queueDeclare(queue, false, false, false, null);

    new Thread() {
      @Override
      public void run() {
        try {
          consumeQueue(channel, queue);
        } catch (Exception error) {
          System.out.println(error);
        }
      }
    }.start();

    return channel;
  }

  private static String consumeQueue(Channel channel, String queue) throws IOException {
    Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {

        String message = new String(body, "UTF-8");
        DateTimeFormatter dateFormater = DateTimeFormatter.ofPattern("yyyy/MM/dd às HH:mm");
        System.out.print("(" + LocalDateTime.now().format(dateFormater) + ") " + queue + " diz: " + message);

      }
    };

    System.out.println(" [*] Esperando recebimento de mensagens...");

    return channel.basicConsume(queue, true, consumer);
  }
}