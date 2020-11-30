package br.ufs.dcomp.ExemploRabbitMQ;

import java.util.Scanner;
import java.util.ArrayList;
// mvn exec:java -D exec.mainClass=br.ufs.dcomp.ExemploRabbitMQ.Client
// mvn compile assembly:single
// java -jar target/Client.jar
// ssh -i rabbit-key.pem ubuntu@18.209.225.188

public class Client {

    public static void main(String[] argv) throws Exception {
        Scanner keyboard = new Scanner(System.in);

        System.out.print("User: ");
        String username = keyboard.nextLine();

        RabbitChat chat = new RabbitChat(
                RabbitChat.makeConnection("sieghart", "rabbit", "ec2-52-87-217-20.compute-1.amazonaws.com", "/"),
                username);

        chat.onNewMessage((ArrayList<RabbitChat.ChatMessage> messages) -> {
            try {
                if (System.getProperty("os.name").contains("Windows")) {
                    new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                } else {
                    Runtime.getRuntime().exec("clear");
                }
            } catch (Exception e) {
                System.out.println(e);
            }

            for (RabbitChat.ChatMessage message : messages) {
                System.out.println(message);
            }

            System.out.print("@" + chat.getQueueName() + ">>");
        });

        while (true) {
            if (chat.isQueueConnected()) {
                System.out.print("@" + chat.getQueueName() + ">>");
            } else {
                System.out.print(">>");
            }
            
            String readed = keyboard.nextLine();

            if (readed.equals("exit")) {
                break;
            }

            if (readed.contains("@")) {
                String queueName = readed.replace("@", "").trim();
                chat.setQueue(queueName);
            } else {
                if (chat.isQueueConnected()) {
                    chat.sendMessage(readed);
                }
            }
        }

        keyboard.close();
    }
}