package br.ufs.dcomp.ExemploRabbitMQ;

// mvn exec:java -D exec.mainClass=br.ufs.dcomp.ExemploRabbitMQ.Client
// mvn compile assembly:single
// java -jar target/Client.jar
// ssh -i rabbit-key.pem ubuntu@18.209.225.188

import java.util.Scanner;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import br.ufs.dcomp.RabbitChat.Proto;

public class Client {

    static ArrayList<String> log = new ArrayList<>();
    static RabbitChat chat;

    public static void main(String[] argv) throws Exception {
        Scanner keyboard = new Scanner(System.in);

        System.out.print("User: ");
        String username = keyboard.nextLine();

        chat = new RabbitChat(
                RabbitChat.makeConnection(RabbitChat.USERNAME, RabbitChat.PASSWORD, RabbitChat.HOSTNAME, RabbitChat.VHOST),
                username);

        chat.onMessageSended((Proto.Message message) -> {
            log.add(messageToString(message));

            printPrompt();
        });

        chat.onMessageReceived((Proto.Message message) -> {
            log.add(messageToString(message));

            printPrompt();
        });

        chat.onFileSended((Proto.Message message) -> {
            log.add("Arquivo \"" + message.getContent().getName() + "\" foi enviado para " + destinatary() + "!");

            printPrompt();
        });

        chat.onFileReceived((Proto.Message message) -> {
            File file = new File(message.getTimestamp().getSeconds()+"_"+message.getContent().getName());
            
            try {
                if (file.createNewFile()) {
                    try (FileOutputStream fos = new FileOutputStream(file, false)) {
                        byte[] bytes = message.getContent().getBody().toByteArray();
                        fos.write(bytes);
                        fos.close();

                        log.add(messageToString(message));

                        printPrompt();
                    }
                } else {
                    try (FileOutputStream fos = new FileOutputStream(file, false)) {
                        byte[] bytes = message.getContent().getBody().toByteArray();
                        fos.write(bytes);
                        fos.close();

                        log.add(messageToString(message));

                        printPrompt();
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        });

        while (true) {
            if (chat.getQueue() != null || chat.getGroup() != null) {
                System.out.print(destinatary() + ">>");
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

                chat.declareUser(name);
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
            } else if (readed.contains("!upload")) {
                int index = readed.indexOf("!upload") + "!upload".length();
                String filename = readed.substring(index).trim();
                
                try {
                    chat.sendFile(filename);

                    log.add("Enviando \"" + filename + "\" para " + destinatary() + ".");
                    printPrompt();
                } catch (Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }
            } else if (readed.contains("!listUsers")) {
                String[] split = readed.split(" ");

                String users = String.join(", ", chat.getUsers(split[1]));
                System.out.println(users);
            } else if (readed.contains("!listGroups")) {
                String groups = String.join(", ", chat.getGroups());
                System.out.println(groups);
            } else {
                if (chat.getQueue() != null || chat.getGroup() != null) {
                    chat.sendMessage(readed);
                }
            }
        }

        keyboard.close();
    }

    private static void printPrompt() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        for (String x : log) {      
            System.out.println(x);
        }

        if (chat.getQueue() != null || chat.getGroup() != null) {
            System.out.print(destinatary() + ">>");
        } else {
            System.out.print(">>");
        }
    }

    private static String messageToString(Proto.Message message)
    {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(message.getTimestamp().getSeconds()),
            ZoneId.systemDefault()
        );

        boolean isGroup = message.getGroup() != null && message.getGroup().length() != 0;

        if (!message.getContent().getName().isEmpty()) {
            return
                "("
                +
                dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                +
                " as "
                +
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                +
                ") "
                +  "Arquivo \"" + message.getContent().getName() + "\" recebido de @" + message.getEmiter() + "!";   
        }

        return
            "("
            +
            dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            +
            " as "
            +
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            +
            ") "
            +  message.getEmiter() + (isGroup ? "#" + message.getGroup() : "") + " diz: " + message.getContent().getBody().toStringUtf8();
    }

    private static String destinatary()
    {
        if (chat.getQueue() != null) {
            return "@"+chat.getQueue();
        }
        if (chat.getGroup() != null) {
            return "#"+chat.getGroup();
        }
        return null;
    }
}