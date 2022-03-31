import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.*;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**     CHAT SERVER
 * Multithreaded Chat Server
 * Works with:
 *              PrintWriter/Scanner: in/out (println corespondation system)
 *              FLAGS: SUBMITNAME, NAMEACCEPTED, MESSAGE, COORDINATOR, MEMBERS
 *                      - at the begining of every output.
 *                      - generated by Server
 *                      - interpreted by Client
 *              CLIENT HANDLER: main handler
 *                      - run function to start client handler
 *                      - handles connect/disconnect, chat system (everithing)
 *                      - try/catch/finally
 *                          - try: handle login & chat
 *                          - finally: handle disconnect
 */
public class ChatServer {

    // names(Set) - stores usernames
    private static Set<String> names = new HashSet<>();

    // coordinator - username of coordinator
    private static String coordinator = null;

    // writers  - used to send messages
    //          - dictionary {name : PrintWriter}
    private static HashMap<String, PrintWriter> writers = new HashMap<>();

    // Start the Server
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");
        addToMemory("\n------------------------------------------------------------------------------------------------------------------------" +
                "\nSERVER START: "+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")) + "\n");
        ExecutorService pool = Executors.newFixedThreadPool(100);
        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }

    /**     CLIENT HANDLER     */
    private static class Handler implements Runnable {
        private String name;
        private Socket socket;
        private Scanner in;
        private PrintWriter out;
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**         RUN
         *  try:    JOIN & CHAT handelers
         *              Name Creation
         *              Messages
         *  finaly: DISCONNECT  handeler
         */
        public void run() {
            /**     JOIN & CHAT
             * when someone connects he is repeatedly asked to submit username, until a unique one is given and accepted
             * if first to join, get notified about it
             * with every join we update the coordinator & members for all clients
             * messages are exchanged either privately or with the group
             */
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);
                // Handle name creation
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.nextLine();
                    synchronized (names) {
                        if (!name.isEmpty() && !names.contains(name)) {
                            names.add(name);
                            if (coordinator == null) {
                                coordinator = name;
                            }
                            break;
                        }
                    }
                }

                // Flag -> NAME ACCEPT
                out.println("NAMEACCEPTED " + name);
                System.out.println(name + " joined");
                addToMemory(name +" joined (" +
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");

                // Send: Flag -> MESSAGE "<name> has joined <time>"
                for (HashMap.Entry<String, PrintWriter> writer : writers.entrySet()) {
                    writer.getValue().println("MESSAGE " + name + " has joined (" +
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))+ ")");
                }
                // Add writer {name: PrintWriter}
                writers.put(name, out);
                // if: First message + add coordinator
                if (names.size() == 1) {
                    writers.get(name).println("MESSAGE You are the first to join and the coordinator of this chat (" +
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                    writers.get(name).println("COORDINATOR " + coordinator);
                    addToMemory(coordinator + " is coordinator (" +
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                // else: Send ALL: Flag -> COORDINATOR, MEMBERS
                } else {
                    for (HashMap.Entry<String, PrintWriter> writer : writers.entrySet()) {
                        writer.getValue().println("COORDINATOR " + coordinator);
                        writer.getValue().println("MEMBERS " + names);
                    }
                }
                // Handle messages
                while (true) {
                    String input = in.nextLine();
                    // private messages (pm)
                    if (input.startsWith("/[")) {
                        // Wrong command check with try/catch
                        try {
                            String toName = new String(input.substring(input.indexOf("[")+1, input.indexOf("]")));
                            toName.replaceAll(" ", "");
                            writers.get(toName).println("MESSAGE " + name + "(pm)(" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "): " +
                                    input.substring(input.indexOf("]") + 1));
                            writers.get(name).println("MESSAGE pm to " + toName + "(" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "): " +
                                    input.substring(input.indexOf("]") + 1));
                            addToMemory("(private): " + name + " -> " + toName + ": " +
                                    input.substring(input.indexOf("]") + 1) + "(" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                        } catch (Exception wrongCommand){
                            writers.get(name).println("MESSAGE Wrong use of command! (" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                        }
                    // group messages
                    } else {
                        for (HashMap.Entry<String, PrintWriter> writer : writers.entrySet()) {
                            writer.getValue().println("MESSAGE " + name + "(" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))+ "): " + input);
                        }
                        addToMemory(name + "(" +
                                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))+ "): " + input);
                    }
                }
            } catch (Exception Disconnecting) {
                System.out.println(name + " is disconnecting");
            /**     DISCONNECT
             *  disconnects the user and changes the coordinator if needed
             *  notifies everyone about the changes
             */
            } finally {
                // Remove writer
                if (out != null) {
                    writers.remove(out);
                }
                // Handle Leaving
                if (name != null) {
                    names.remove(name);
                    writers.remove(name);
                    // if COORDINATOR
                    if (name == coordinator) {
                        if (names.isEmpty()){
                            coordinator = null;
                        }
                        for (HashMap.Entry<String, PrintWriter> writer : writers.entrySet()) {
                            coordinator = names.iterator().next();
                            writer.getValue().println("MESSAGE " + name + " has left. The new coordinator is: " +
                                    coordinator + "(" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                            writer.getValue().println("COORDINATOR " + coordinator);
                            writer.getValue().println("MEMBERS " + names);
                        }
                        addToMemory(name + " disconnected. " + coordinator + " is coordinator ("
                                + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                    } else {
                        // if MEMBER
                        for (HashMap.Entry<String, PrintWriter> writer : writers.entrySet()) {
                            writer.getValue().println("MESSAGE " + name + " has left (" +
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))+ ")");
                            writer.getValue().println("COORDINATOR " + coordinator);
                            writer.getValue().println("MEMBERS " + names);
                        }
                        addToMemory(name + " disconnected (" +
                                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                    }

                }
                // Close Socket
                try { socket.close(); System.out.println(name + " disconnected");
                } catch (IOException e) { System.out.println(e);}
            }
        }
    }
    // addToMemory function stores all
    private static void addToMemory(String line) {
        try{
            FileWriter writer = new FileWriter("src\\Memory.txt", true);
            writer.write(line + "\n");
            writer.close();
            System.out.println(line + " was stored to Memory");
        }catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
