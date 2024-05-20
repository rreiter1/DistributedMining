import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LauncherWorker {
    private String password = "password";                       //  mots de passe
    private volatile boolean keepRunning = true;                //  pour que le worker soit toujours a l'écoute
    private boolean keepRunningSolv = false;                    //  pour demandé de continuer ou de s'arreté de resoudre
    private Socket server;                                       //  connection au serveur
    private long Start;                                         //  valeur ou va commencé le nonce
    private int Step;                                           //  valeur de step du nonce
    private String data;                                        //  data a hash avec le nonce
    private long nonce;                                         //  le nonce

    public static void main(String[] args) {
        LauncherWorker worker = new LauncherWorker();
        worker.run();
    }

    public void run() {
        try {
            final InetAddress remoteAddress = InetAddress.getByName("127.0.0.1");
            server = new Socket(remoteAddress, 1337);
            BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream()));
            PrintWriter out = new PrintWriter(server.getOutputStream(), true);
            gestionMessage(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //  reception des message serveur et réponce worker
    private void gestionMessage(BufferedReader in, PrintWriter out) {
        new Thread(() -> {
            try {
                String message = in.readLine();
                if ("WHO_ARE_YOU_?".equals(message)) {
                    System.out.println("serveur : " + message);
                    out.println("ITS_ME");
                    System.out.println("worker : ITS_ME");
                    message = in.readLine();
                    if ("GIMME_PASSWORD".equals(message)) {
                        System.out.println("serveur : " + message);
                        out.println("PASSWD " + password);
                        System.out.println("worker : PASSWD " + password);
                        message = in.readLine();
                        if ("HELLO_YOU".equals(message)) {
                            System.out.println("serveur : " + message);
                            out.println("READY");
                            System.out.println("worker : READY");
                            message = in.readLine();
                            if ("OK".equals(message)) {
                                System.out.println("serveur : " + message);
                                System.out.println("En attente que le serveur transmet les instructions..."); //  a partir d'ici la connexion est correctement établie entre le worker et le serveur
                                while (keepRunning) {                       //  ici on écoute tout les commande serveur
                                    String serverMessage = in.readLine();
                                    if (serverMessage == null) {
                                        System.out.println("Le serveur a fermé la connexion.");
                                        break;
                                    }
                                    if (serverMessage.startsWith("NONCE")) {    // ici le serveur a envoyé les paramertre pour le nonce
                                        String[] parts = serverMessage.split(" ");  // on initialise le debut
                                        Start = Long.parseLong(parts[1]);                // on initialise le step
                                        Step = Integer.parseInt(parts[2]);
                                       // System.out.println("Nonce start: " + Start + ", step: " + Step);
                                    } else if (serverMessage.startsWith("PAYLOAD")) { // ici le serveur a envoyé la data
                                        String[] parts = serverMessage.split(" ");
                                        data = parts[1];                                      // on initialise la data
                                        //System.out.println("Payload reçu : " + data);
                                    } else if (serverMessage.startsWith("SOLVE")) {   // ici le serveur nous demande de resoudre
                                        String[] parts = serverMessage.split(" ");
                                        int difficulty = Integer.parseInt(parts[1]);      // on initialise la dificulté
                                        keepRunningSolv = true;
                                        // on lance le traitement
                                        new Thread(() -> { solveTask(data, difficulty, out); }).start();



                                    } else {
                                        switch (serverMessage) {
                                            case "QUIT":            //ici le serveur a demande de coupé la liaison
                                                keepRunning = false;
                                                break;
                                            case "STATUS":      //ici le serveur a demande son status
                                                out.println("STATUS_OK");
                                                break;
                                            case "PROGRESS":        //ici le serveur a demande ça progression
                                                if (keepRunningSolv) {
                                                    out.println("TESTING " + nonce);
                                                } else {
                                                    out.println("NOPE");
                                                }
                                                break;
                                            case "CANCELLED":       //ici le serveur a demande de plus travaillé sur la data et le nonce actuelle
                                                keepRunningSolv = false;
                                                out.println("READY");
                                                System.out.println("worker : READY");
                                                break;
                                            default:
                                                System.out.println("Unknown message from server: " + serverMessage);
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void solveTask(String dataJson, int difficulty, PrintWriter out) {
        System.out.println("Solving task with difficulty " + difficulty + "...");
        String data = extractDataValue(dataJson);
        String target = "0".repeat(difficulty);
        nonce = Start;
        String hash = "";

        try {
            MessageDigest digest;

            while (keepRunningSolv) {
                digest = MessageDigest.getInstance("SHA-256");
                //String reversBinNonce = reversBinString(Long.toBinaryString(nonce));
                //String input = data + reversBinNonce;
                String input = data + Long.toBinaryString(nonce);

                System.out.println("Trying nonce: " + Long.toBinaryString(nonce) + " with input: " + input);

                byte[] hashBytes = digest.digest(input.getBytes("utf-8"));
                hash = bytesToHex(hashBytes);

                System.out.println("Generated hash: " + hash);

                if (hash.startsWith(target)) {
                    System.out.println("Nonce BIN found: " + Long.toBinaryString(nonce));
                    System.out.println("Nonce HEX found: " + Long.toHexString(Long.parseLong(String.valueOf(nonce))));
                    System.out.println("Nonce DEC found: " + nonce);
                    System.out.println("Hash: " + hash);
                    out.println("FOUND " + hash + " " + Long.toHexString(Long.parseLong(String.valueOf(nonce))));
                    break;
                }
                nonce += Step;
            }

            if (!keepRunningSolv) {
                System.out.println("Mining interrupted.");
            } else {
                System.out.println("Task solved!");
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String extractDataValue(String dataJson) {
        int startIndex = dataJson.indexOf("\"data\":\"") + 8;
        int endIndex = dataJson.indexOf("\"", startIndex);
        return dataJson.substring(startIndex, endIndex);
    }

    /*
    private String reversBinString(String binaryString) {
        return new StringBuilder(binaryString).reverse().toString();
    }
    */
}