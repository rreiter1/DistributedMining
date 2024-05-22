import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class LauncherServeur {
    private volatile boolean keepGoing = true;                              //  Pour lire tout les tentative de connexion
    private final int port = 1337;                                          //  port d'écoute du serveur
    private static final List<Socket> listWorker = new ArrayList<>();       //  L'ensemble des Worker's connecter au serveur
    private ConnexionAPI api;                                               //  connection a l'api
    private int difficulty;                                                  //  difficulter de la resolution

    public static void main(String[] args) {
        LauncherServeur server = new LauncherServeur();
        try {
            server.runServeur();                                // Lance le Thread qui vas écouter le port
            server.runCommande();                               // Lance le Thread qui vas lire et executer les commande saisie dans le pront
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public LauncherServeur() {
        api = new ConnexionAPI("rec2VEWOtKEwnfrgC");    //  Initialise la connexion au ServiceWeb
    }

    public void runServeur() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                System.out.println("Serveur démarré sur le port " + port);
                while (keepGoing) {
                    try {
                        Socket serverAccept = server.accept();
                        System.out.println("Nouveau worker connecté : " + serverAccept.getRemoteSocketAddress());
                        listWorker.add(serverAccept);
                        new Thread(() -> gestionWorker(serverAccept)).start(); //lance la connexion avec le worker et l'écoute de ces commande retour
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void gestionWorker(Socket client) {
        boolean listen = true;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            System.out.println("Connexion avec le worker : " + client.getRemoteSocketAddress());
            out.println("WHO_ARE_YOU_?");
            String response = in.readLine();
            if ("ITS_ME".equals(response)) {
                out.println("GIMME_PASSWORD");
                response = in.readLine();
                if ("PASSWD password".equals(response)) {
                    out.println("HELLO_YOU");
                    response = in.readLine();
                    if ("READY".equals(response)) {
                        out.println("OK");
                        while (listen) {
                            try {
                                response = in.readLine();
                                if (response.startsWith("FOUND")) {   // Si le worker envoie FOUND, c'est qu'il a trouver le resultat
                                    // Extraire les informations du message
                                    String[] parts = response.split(" ");
                                    String hash = parts[1];
                                    String nonce = parts[2];

                                    // Construire le corps de la requête pour valider le travail
                                    String requestBody = String.format("{\"d\":%d,\"n\":\"%s\",\"h\":\"%s\"}", difficulty, nonce, hash);

                                    System.out.println("Request Body: " + requestBody);
                                    try {
                                        boolean validateResponse = api.sendPostRequest("validate_work", requestBody);
                                        System.out.println("Réponse de validate_work : " + validateResponse);
                                        if (validateResponse) {   //  si le code retour du webService commence par 2 alors
                                            sendCancelle();      //  on stop la rechercher des autre worker
                                        }
                                    } catch (IOException e) {
                                        System.out.println("Erreur lors de la validation du travail : " + e.getMessage());
                                    }
                                }
                                if (response.startsWith("TESTING")) {                            // Le worker signal qui travail et indique sur qu'elle nonce il se trouve
                                    System.out.println("$ " + response);
                                } else if (response.equalsIgnoreCase("NOPE")) {     //  Le worker Sigal qui ne fait rien et attend
                                    System.out.println(response);
                                }
                            } catch (SocketException e) {
                                listen = false;
                                removeWorker(client);           //  en cas de probleme avec le worker on ne l'ecoute plus et on le retir de la liste des worker
                                System.out.println("La connection a été interrompue");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                client.close();                 //  on coupe la liaison avec worker
                removeWorker(client);            //  et on le suprime de la liste worker
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Thread des ecoute des commandes
    public void runCommande() {
        final Console console = System.console();
        new Thread(() -> {
            while (keepGoing) {
                final String commande = console.readLine("$ ");
                if (commande == null) break;

                try {
                    keepGoing = processCommand(commande.trim());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    //traitement des commandes
    private boolean processCommand(String cmd) {
        if ("quit".equalsIgnoreCase(cmd)) {         //  on quit l'application
            keepGoing = false;                      // on coupe l'ecoute du port
            for (Socket worker : listWorker) {      // et on coupe la liaison de tout les worker
                try {
                    worker.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.exit(1);                               // et on fini pas couper le serveur
            return false;
        } else if ("cancel".equalsIgnoreCase(cmd)) {             //  on cancel la tache actuel de tout les worker
            sendCancelle();
        } else if ("status".equalsIgnoreCase(cmd)) {             // envoie une demande au worker sur leur progression
            // TODO show workers status
        } else if ("progress".equalsIgnoreCase(cmd)) {
            for (Socket worker : listWorker) {
                try {
                    PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                    out.println("PROGRESS");
                    System.out.println("Sent PROGRESS to " + worker.getRemoteSocketAddress());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if ("help".equals(cmd.trim())) {
            System.out.println("\n • status - display informations about connected workers");
            System.out.println(" • SOLVE <d> - try to mine with given difficulty");
            System.out.println(" • CANCEL - cancel a task");
            System.out.println(" • help - describe available commands");
            System.out.println(" • quit - terminate pending work and quit");
        } else if (cmd.toLowerCase().startsWith("solve")) {
            String[] parts = cmd.split(" ");
            if (parts.length == 2) {                            // on verifie que la commande et complet
                try {
                    difficulty = Integer.parseInt(parts[1]);    //  on recupere la difficulté
                    String data = "";

                    try {
                        data = api.sendGetRequest("generate_work", difficulty);     //  on fait une demande au webService pour avoir une data selon la dificulté
                        System.out.println("data récupérer : " + data);
                    } catch (IOException e) {
                        System.out.println("Erreur lors de la récupération des données : " + e.getMessage());
                    }

                    int step = listWorker.size();
                    for (int i = 0; i < step; i++) {                //  Pour chaque worker on definie leur nonce et le step qui vont travaillé
                        try {
                            Socket worker = listWorker.get(i);
                            PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                            out.println("NONCE " + i + " " + step);
                            System.out.println("Sent NONCE " + i + " " + step + " to " + worker.getRemoteSocketAddress());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    for (Socket worker : listWorker) {            // pour chaque worker on leur défini la data récupéré
                        try {
                            PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                            out.println("PAYLOAD " + data);
                            System.out.println("Sent PAYLOAD " + data + " to " + worker.getRemoteSocketAddress());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    for (Socket worker : listWorker) {          // pour chaque worker on leur demande de resoudre selon une difficulté
                        try {
                            PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                            out.println("SOLVE " + difficulty);
                            System.out.println("Sent SOLVE " + difficulty + " to " + worker.getRemoteSocketAddress());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid difficulty level. Please enter a number.");
                }
            } else {
                System.out.println("Usage: solve <difficulty>");
            }
        }
        return true;
    }

    private void sendCancelle() {
        for (Socket worker : listWorker) {
            try {
                PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                out.println("CANCELLED");
                System.out.println("Sent CANCELLED to " + worker.getRemoteSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void removeWorker(Socket client) {
        listWorker.remove(client);
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Worker removed: " + client.getRemoteSocketAddress());
    }
}
