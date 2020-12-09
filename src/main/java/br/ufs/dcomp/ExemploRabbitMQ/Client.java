package br.ufs.dcomp.ExemploRabbitMQ;

// mvn exec:java -D exec.mainClass=br.ufs.dcomp.ExemploRabbitMQ.Client
// mvn compile assembly:single
// java -jar target/Client.jar
// ssh -i rabbit-key.pem ubuntu@18.209.225.188

import java.util.Scanner;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import br.ufs.dcomp.RabbitChat.Proto;

public class Client {

    static ArrayList<Proto.Message> messages = new ArrayList<>();
    static RabbitChat chat;

    public static void main(String[] argv) throws Exception {
        Scanner keyboard = new Scanner(System.in);

        System.out.print("User: ");
        String username = keyboard.nextLine();

        chat = new RabbitChat(
                RabbitChat.makeConnection("sieghart", "rabbit", "ec2-107-22-143-243.compute-1.amazonaws.com", "/"),
                username);

        chat.onMessageSended((Proto.Message message) -> {
            messages.add(message);

            printPrompt();
        });

        chat.onMessageReceived((Proto.Message message) -> {
            messages.add(message);

            printPrompt();
        });

        while (true) {
            if (chat.getQueue() != null) {
                System.out.print("@" + chat.getQueue() + ">>");
            } else if (chat.getGroup() != null) {
                System.out.print("#" + chat.getGroup() + ">>");
            } else {
                System.out.print(">>");
            }
            
            String readed = keyboard.nextLine();

            if (readed.equals("exit")) {
                System.exit(0);
                break;
            }

            if (readed.contains("@")) {
                String name = readed.replace("@", "").trim();
                chat.declareQueue(name);
            } else if (readed.contains("#")) {
                String name = readed.replace("#", "").trim();
                chat.setGroup(name);
            } else if (readed.contains("!addGroup")) {
                String[] split = readed.split(" ");
                chat.declareGroup(split[1]);
            } else if (readed.contains("!addUser")) {
                String[] split = readed.split(" ");
                chat.addUserToGroup(split[1], split[2]);
            } else if (readed.contains("!delFromGroup")) {
                String[] split = readed.split(" ");
                chat.removeUserFromGroup(split[1], split[2]);
            } else if (readed.contains("!removeGroup")) {
                String[] split = readed.split(" ");
                chat.removeGroup(split[1]);
            } else {
                if (chat.getQueue() != null || chat.getGroup() != null) {
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
            printMessage(x);
        }

        if (chat.getQueue() != null) {
            System.out.print("@" + chat.getQueue() + ">>");
        } else if (chat.getGroup() != null) {
            System.out.print("#" + chat.getGroup() + ">>");
        } else {
            System.out.print(">>");
        }
    }

    static private void printMessage(Proto.Message message)
    {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(message.getTimestamp().getSeconds()),
            ZoneId.systemDefault()
        );

        boolean isGroup = message.getGroup() != null && message.getGroup().length() != 0;

        System.out.println(
            "("
            +
            dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            +
            " as "
            +
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            +
            ") "
            +  message.getEmiter() + (isGroup ? "#" + message.getGroup() : "") + " diz: " + message.getContent().getBody().toStringUtf8()
        );
    }
}