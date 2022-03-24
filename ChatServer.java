import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A multithreaded chat room server. When a client connects the server requests a screen
 * name by sending the client the text "SUBMITNAME", and keeps requesting a name until
 * a unique one is received. After a client submits a unique name, the server acknowledges
 * with "NAMEACCEPTED". Then all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name. The broadcast messages are prefixed
 * with "MESSAGE".
 *
 * This is just a teaching example so it can be enhanced in many ways, e.g., better
 * logging. Another is to accept a lot of fun commands, like Slack.
 */
public class ChatServer {

    // All client names, so we can check for duplicates upon registration.
    private static Set<String> names = new HashSet<>();

    // Coordinator variable to store the name of the coordinator
    private static String coordinator = null;

    // The set of all the print writers for all the clients, used for broadcast.
    private static Set<PrintWriter> writers = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");
        ExecutorService pool = Executors.newFixedThreadPool(500);
        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }

    /**
     * The client handler task.
     */
    private static class Handler implements Runnable {
        private String name;
        private Socket socket;
        private Scanner in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket. All the interesting
         * work is done in the run method. Remember the constructor is called from the
         * server's main method, so this has to be as short as possible.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a screen name until a
         * unique one has been submitted, then acknowledges the name and registers the
         * output stream for the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                // Keep requesting a name until we get a unique one.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.nextLine();
                    if (name == null) {
                        return;
                    }
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

                // Now that a successful name has been chosen, add the socket's print writer
                // to the set of all writers so this client can receive broadcast messages.
                // But BEFORE THAT, let everyone else know that the new person has joined!
                out.println("NAMEACCEPTED " + name);
                for (PrintWriter writer : writers) {
                    DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
                    LocalTime time = LocalTime.now();
                    writer.println("MESSAGE " + name + " has joined (" + time.format(hhmm) + ")");
                    writer.println("COORDINATOR " + coordinator);
                    writer.println("MEMBERS " + names);
                }
                writers.add(out);
                if (names.size() == 1) {
                    for (PrintWriter writer : writers) {
                        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
                        LocalTime time = LocalTime.now();
                        writer.println("MESSAGE You are the first to join and the coordinator of this chat (" + time.format(hhmm) + ")");
                        writer.println("COORDINATOR " + coordinator);
                    }
                }
                // Accept messages from this client and broadcast them.
                while (true) {
                    String input = in.nextLine();
                    if (input.toLowerCase().startsWith("/quit")) {
                        return;
                    }
                    for (PrintWriter writer : writers) {
                        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
                        LocalTime time = LocalTime.now();
                        writer.println("MESSAGE " + name + "(" + time.format(hhmm) + "): " + input);
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                if (out != null) {
                    writers.remove(out);
                }
                if (name != null) {
                    System.out.println(name + " is leaving");
                    names.remove(name);
                    if (name == coordinator) {
                        if (names.isEmpty()){
                            coordinator = null;
                        }
                        for (PrintWriter writer : writers) {
                            DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
                            LocalTime time = LocalTime.now();
                            coordinator = names.iterator().next();
                            writer.println("MESSAGE " + name + " has left. The new coordinator is: " + coordinator + "(" + time.format(hhmm) + ")");
                            writer.println("COORDINATOR " + coordinator);
                            writer.println("MEMBERS " + names);
                        }
                    } else {
                        for (PrintWriter writer : writers) {
                            DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
                            LocalTime time = LocalTime.now();
                            writer.println("MESSAGE " + name + " has left (" + time.format(hhmm) + ")");
                            writer.println("COORDINATOR " + coordinator);
                            writer.println("MEMBERS " + names);
                        }
                    }
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}
