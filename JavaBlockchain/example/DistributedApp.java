package example;

import java.io.*;
import java.util.Base64;

public class DistributedApp {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java example.DistributedApp <self_port> <is_miner_true_false> [peer_ip:peer_port_to_connect]");
            System.out.println("  Example (Miner Node 1): java example.DistributedApp 8888 true");
            System.out.println("  Example (User Node 2, connecting to Node 1 at 192.168.100.9): java example.DistributedApp 8889 false 192.168.100.9:8888");
            return;
        }

        int selfPort = Integer.parseInt(args[0]);
        boolean isMiner = Boolean.parseBoolean(args[1]);

        String peerToConnectIp = null;
        int peerToConnectPort = 0;

        if (args.length == 3) {
            try {
                String[] peerDetails = args[2].split(":");
                peerToConnectIp = peerDetails[0];
                peerToConnectPort = Integer.parseInt(peerDetails[1]);
            } catch (Exception e) {
                System.err.println("Invalid peer address format. Expected ip:port. Exiting.");
                return;
            }
        }

        String userFile = "user_" + selfPort + ".dat";
        User nodeUser = loadOrCreateUser(userFile); // Each node has its own identity
        System.out.println("Node starting...");
        System.out.println("  My Port: " + selfPort);
        System.out.println("  Is Miner: " + isMiner);
        System.out.println("  My Wallet Address: " + nodeUser.walletAddress);
        System.out.println("  My Public Key (short): " + Base64.getEncoder().encodeToString(nodeUser.publicKey.getEncoded()).substring(0, 20) + "...");


        P2PNode node = new P2PNode(selfPort, isMiner, nodeUser, peerToConnectIp, peerToConnectPort);
        // The P2PNode constructor starts the server and CLI thread.
    }

    private static User loadOrCreateUser(String filename) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            User user = (User) ois.readObject();
            System.out.println("Loaded user from " + filename);
            return user;
        } catch (Exception e) {
            System.out.println("No user file found, creating new user.");
            User user = new User();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
                oos.writeObject(user);
                System.out.println("Saved new user to " + filename);
            } catch (Exception ex) {
                System.err.println("Failed to save user: " + ex.getMessage());
            }
            return user;
        }
    }
}