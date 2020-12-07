package br.ufs.dcomp.ExemploRabbitMQ;

// mvn exec:java -D exec.mainClass=br.ufs.dcomp.ExemploRabbitMQ.Client
// mvn compile assembly:single
// java -jar target/Client.jar
// ssh -i rabbit-key.pem ubuntu@18.209.225.188

import java.util.Scanner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import br.ufs.dcomp.RabbitChat.Proto;

public class Client {

    static ArrayList<Proto.Message> messages;
    static ZoneOffset zoneOffset = ZoneOffset.of(ZoneId.systemDefault().getId());
    static RabbitChat chat;

    public static void main(String[] argv) throws Exception {
        Scanner keyboard = new Scanner(System.in);

        System.out.print("User: ");
        String username = keyboard.nextLine();

        chat = new RabbitChat(
                RabbitChat.makeConnection("sieghart", "rabbit", "ec2-52-87-217-20.compute-1.amazonaws.com", "/"),
                username);

        chat.onNewMessage((Proto.Message message) -> {
            messages.add(message);

            printPrompt();
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

    static private void printPrompt() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        for (Proto.Message x : messages) {                
            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(
                x.getTimestamp().getSeconds(),
                x.getTimestamp().getNanos(),
                zoneOffset
            );

            System.out.println(
                "("
                +
                dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                +
                " as "
                +
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                +
                ")"
                + " " + x.getEmiter() + " diz " + x.getContent().getBody()
            );
        }

        System.out.print("@" + chat.getQueueName() + ">>");
    }
}