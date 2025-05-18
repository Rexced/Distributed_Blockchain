package example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// Simple Message class for P2P communication
class P2PMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    enum Type { TRANSACTION, BLOCK, GET_BLOCKS, BLOCKS_BATCH }
    Type type;
    Object payload;

    public P2PMessage(Type type, Object payload) {
        this.type = type;
        this.payload = payload;
    }
}

public class P2PNode {
    private int port;
    private Blockchain blockchain;
    private User selfUser; // The user identity of this node
    private boolean isMiner;
    private String blockchainFile; // Remove the default value
    private final List<ObjectOutputStream> peerOutputStreams = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();


    public P2PNode(int port, boolean isMiner, User selfUser, String peerToConnectIp, int peerToConnectPort) {
        this.port = port;
        this.isMiner = isMiner;
        this.selfUser = selfUser;
        this.blockchainFile = "blockchain_p2p_" + port + ".dat"; // Use a unique file per port
        this.blockchain = loadBlockchain(); // Tries to load

        if (this.blockchain.getChain().isEmpty()) {
            if (isMiner && peerToConnectIp == null) { // This is the first miner node starting alone
                System.out.println("Blockchain is empty AND I am the initial Miner. Initializing with Gensis for self.");
                this.blockchain = new Blockchain(selfUser); // Initialize with genesis FOR THIS MINER
                saveBlockchain();
            } else {
                // If not the initial miner, or if connecting to a peer, blockchain will be synced.
                // An empty blockchain object is fine for now. It will be populated by sync.
                System.out.println("Blockchain is empty. Will attempt to sync from peer or wait for blocks.");
            }
        }


        startServer();
        if (peerToConnectIp != null) {
            connectToPeer(peerToConnectIp, peerToConnectPort);
        }

        if (isMiner) {
            // Periodically try to mine a block
            executorService.scheduleAtFixedRate(() -> {
                // Ensure blockchain access for mining is synchronized if other threads modify it.
                // The mineNewBlock method in Blockchain is already synchronized.
                Block newBlock = blockchain.mineNewBlock(this.selfUser); // Miner gets reward
                if (newBlock != null) {
                    System.out.println("MINER DEBUG (Block " + newBlock.index + "):" +
                            " index=" + newBlock.index +
                            " timestamp=" + newBlock.timestamp +
                            " previousHash=" + newBlock.previousHash +
                            " merkleRoot=" + newBlock.merkleRoot +
                            " nonce=" + newBlock.nonce +
                            " height=" + newBlock.height +
                            " transactions_hashCode=" + newBlock.transactions.hashCode() + // Be careful with this
                            " calculatedHash_before_send=" + newBlock.calculateHash() + // Should match newBlock.hash
                            " storedHash=" + newBlock.hash);
                    // ... saveBlockchain, broadcastMessage ...
                }
            }, 5, 30, TimeUnit.SECONDS); // Start after 5s, then every 5s
        }
        startCLI();
    }

    private Blockchain loadBlockchain() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(blockchainFile))) {
            Blockchain loadedChain = (Blockchain) ois.readObject();
            System.out.println("Blockchain loaded from " + blockchainFile + ". Chain height: " + loadedChain.getChainHeight());
            return loadedChain;
        } catch (FileNotFoundException e) {
            System.out.println("No blockchain file found (" + blockchainFile + "). Creating new.");
            return new Blockchain(); // Will be initialized with genesis in constructor if empty
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading blockchain: " + e.getMessage() + ". Creating new.");
            return new Blockchain();
        }
    }

    private synchronized void saveBlockchain() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(blockchainFile))) {
            oos.writeObject(blockchain);
            System.out.println("Blockchain saved to " + blockchainFile);
        } catch (IOException e) {
            System.err.println("Error saving blockchain: " + e.getMessage());
        }
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Node listening on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Accepted connection from: " + clientSocket.getRemoteSocketAddress());
                    ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
                    peerOutputStreams.add(oos);
                    new ClientHandler(clientSocket, oos).start();
                }
            } catch (IOException e) {
                System.err.println("Server error: " + e.getMessage());
            }
        }).start();
    }

    private void connectToPeer(String host, int peerPort) {
        try {
            Socket socket = new Socket(host, peerPort);
            System.out.println("Connected to peer: " + host + ":" + peerPort);
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            peerOutputStreams.add(oos);
            new ClientHandler(socket, oos).start(); // Also listen for messages from this peer

            // Request blocks from this peer to sync up
            P2PMessage getBlocksMsg = new P2PMessage(P2PMessage.Type.GET_BLOCKS, blockchain.getChainHeight());
            oos.writeObject(getBlocksMsg);
            oos.flush();

        } catch (IOException e) {
            System.err.println("Connection to peer " + host + ":" + peerPort + " failed: " + e.getMessage());
        }
    }

    private void broadcastMessage(P2PMessage message) {
        System.out.println("Broadcasting message: " + message.type);
        for (ObjectOutputStream oos : new ArrayList<>(peerOutputStreams)) { // Iterate over a copy
            try {
                synchronized(oos) { // Synchronize on individual stream when writing
                    oos.writeObject(message);
                    oos.flush();
                }
            } catch (IOException e) {
                System.err.println("Error broadcasting to a peer, removing stream: " + e.getMessage());
                peerOutputStreams.remove(oos); // Remove problematic stream
            }
        }
    }

    @SuppressWarnings("unchecked") // Suppress warnings for casts within this inner class's method
    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private ObjectInputStream ois;
        private ObjectOutputStream oos; // The stream to this specific client

        public ClientHandler(Socket socket, ObjectOutputStream oos) {
            this.clientSocket = socket;
            this.oos = oos; // This is the stream for sending *to* this client
            try {
                this.ois = new ObjectInputStream(clientSocket.getInputStream());
            } catch (IOException e) {
                System.err.println("Error creating input stream for client: " + e.getMessage());
                // Consider closing the socket or handling this more gracefully
            }
        }

        @Override
        public void run() {
            if (ois == null) {
                System.err.println("Input stream is null for client " + clientSocket.getRemoteSocketAddress() + ". Handler thread exiting.");
                peerOutputStreams.remove(oos); // Clean up the output stream from the main list
                return;
            }
            try {
                while (true) {
                    P2PMessage message = (P2PMessage) ois.readObject(); // This cast is usually fine if protocol is followed
                    System.out.println("Received message: " + message.type + " from " + clientSocket.getRemoteSocketAddress());

                    // Synchronize on the blockchain instance for all read/write operations to it
                    synchronized (blockchain) {
                        Object payload = message.payload; // Get payload once
                        switch (message.type) {
                            case TRANSACTION:
                                if (payload instanceof Transaction) {
                                    Transaction tx = (Transaction) payload;
                                    if (blockchain.addTransactionToMempool(tx)) {
                                        // Simple flood: Re-broadcast to other peers
                                        P2PMessage reboundMsg = new P2PMessage(P2PMessage.Type.TRANSACTION, tx);
                                        for (ObjectOutputStream peerOos : new ArrayList<>(peerOutputStreams)) {
                                            if (peerOos != this.oos) { // Don't send back to the sender
                                                try { synchronized(peerOos) {peerOos.writeObject(reboundMsg); peerOos.flush();}} catch (IOException e) { peerOutputStreams.remove(peerOos); }
                                            }
                                        }
                                    }
                                } else {
                                    System.err.println("P2PNode: Received TRANSACTION, but payload was not a Transaction. Actual type: " + (payload != null ? payload.getClass().getName() : "null"));
                                }
                                break;
                            case BLOCK:
                                if (payload instanceof Block) {
                                    Block block = (Block) payload;
                                    boolean added = blockchain.addBlock(block);
                                    if (!added) {
                                        // If block index mismatch, request missing blocks
                                        int expectedIndex = blockchain.getChainHeight();
                                        if (block.index > expectedIndex) {
                                            System.out.println("Block index mismatch. Requesting missing blocks from " + expectedIndex);
                                            P2PMessage getBlocksMsg = new P2PMessage(P2PMessage.Type.GET_BLOCKS, expectedIndex);
                                            try {
                                                synchronized(this.oos) {
                                                    this.oos.writeObject(getBlocksMsg);
                                                    this.oos.flush();
                                                }
                                            } catch (IOException e) {
                                                System.err.println("Error requesting missing blocks: " + e.getMessage());
                                            }
                                        }
                                    } else {
                                        saveBlockchain();
                                        // Simple flood: Re-broadcast to other peers
                                        P2PMessage reboundBlockMsg = new P2PMessage(P2PMessage.Type.BLOCK, block);
                                        for (ObjectOutputStream peerOos : new ArrayList<>(peerOutputStreams)) {
                                            if (peerOos != this.oos) {
                                                try { synchronized(peerOos) {peerOos.writeObject(reboundBlockMsg); peerOos.flush();}} catch (IOException e) { peerOutputStreams.remove(peerOos); }
                                            }
                                        }
                                    }
                                }
                                break;
                            case GET_BLOCKS:
                                if (payload instanceof Integer) {
                                    Integer fromIndex = (Integer) payload;
                                    List<Block> blocksToSend = blockchain.getBlocksFromIndex(fromIndex);
                                    P2PMessage response = new P2PMessage(P2PMessage.Type.BLOCKS_BATCH, new ArrayList<>(blocksToSend)); // Send as ArrayList
                                    try {
                                        synchronized(this.oos) { // Synchronize on this specific client's output stream
                                            this.oos.writeObject(response);
                                            this.oos.flush();
                                        }
                                        System.out.println("Sent " + blocksToSend.size() + " blocks to " + clientSocket.getRemoteSocketAddress());
                                    } catch (IOException e) {
                                        System.err.println("Error sending blocks batch: " + e.getMessage());
                                        // Consider this connection dead, an outer catch will remove oos
                                        throw e; // Re-throw to be caught by outer handler for cleanup
                                    }
                                } else {
                                    System.err.println("P2PNode: Received GET_BLOCKS, but payload was not an Integer. Actual type: " + (payload != null ? payload.getClass().getName() : "null"));
                                }
                                break;
                            case BLOCKS_BATCH:
                                if (payload instanceof List) {
                                    List<?> rawList = (List<?>) payload; // Cast to a raw List
                                    List<Block> receivedBlocks = new ArrayList<>();
                                    boolean allElementsAreBlocks = true;

                                    for (Object item : rawList) {
                                        if (item instanceof Block) {
                                            receivedBlocks.add((Block) item);
                                        } else {
                                            System.err.println("P2PNode: Received BLOCKS_BATCH, but list contained a non-Block item: " +
                                                    (item != null ? item.getClass().getName() : "null"));
                                            allElementsAreBlocks = false;
                                            break;
                                        }
                                    }

                                    if (allElementsAreBlocks && !receivedBlocks.isEmpty()) {
                                        System.out.println("Received batch of " + receivedBlocks.size() + " blocks for sync.");
                                        boolean chainChanged = false;
                                        for (Block b : receivedBlocks) {
                                            if (blockchain.addBlock(b)) { // addBlock is synchronized
                                                chainChanged = true;
                                            }
                                        }
                                        if (chainChanged) {
                                            saveBlockchain(); // saveBlockchain is synchronized
                                        }
                                    } else if (!allElementsAreBlocks) {
                                        System.err.println("P2PNode: Malformed BLOCKS_BATCH received. Not processing.");
                                    } else if (receivedBlocks.isEmpty() && !rawList.isEmpty()){
                                        System.out.println("P2PNode: Received BLOCKS_BATCH with only non-block items or conversion failed.");
                                    } else {
                                        System.out.println("P2PNode: Received empty BLOCKS_BATCH.");
                                    }
                                } else {
                                    System.err.println("P2PNode: Received BLOCKS_BATCH, but payload was not a List. Actual type: " +
                                            (payload != null ? payload.getClass().getName() : "null"));
                                }
                                break;
                        }
                    }
                }
            } catch (EOFException e) {
                System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " disconnected (EOF).");
            } catch (SocketException e) {
                System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " connection reset or closed: " + e.getMessage());
            }
            catch (IOException | ClassNotFoundException e) {
                if (!clientSocket.isClosed()){ // Avoid spamming if socket already closed by this side
                    System.err.println("Error handling client " + clientSocket.getRemoteSocketAddress() + ": " + e.getMessage());
                    // e.printStackTrace(); // For deeper debugging
                }
            } finally {
                peerOutputStreams.remove(oos); // Remove this client's output stream from the broadcast list
                try {
                    if(ois != null) ois.close();
                    // oos is closed when the socket is closed, or if an error occurred during write in broadcast
                    if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket resources: " + e.getMessage());
                }
                System.out.println("Closed connection and cleaned up for " + clientSocket.getRemoteSocketAddress());
            }
        }
    }

    private void startCLI() {
        Scanner scanner = new Scanner(System.in);
        new Thread(() -> {
            while (true) {
                System.out.print("\nNode> ");
                String line = scanner.nextLine();
                if (line == null) { // Handle Ctrl+D or other EOF
                    System.out.println("CLI input stream closed. Exiting...");
                    System.exit(0);
                    break;
                }
                String[] parts = line.split("\\s+");
                if (parts.length == 0 || parts[0].isEmpty()) {
                    continue;
                }
                String command = parts[0].toLowerCase();

                try {
                    switch (command) {
                        case "tx": // tx <receiverWalletAddress> <amount>
                            if (parts.length == 3) {
                                String receiverAddress = parts[1];
                                int amount = Integer.parseInt(parts[2]);
                                createAndBroadcastTx(receiverAddress, amount);
                            } else {
                                System.out.println("Usage: tx <receiverWalletAddress> <amount>");
                            }
                            break;
                        case "balance":
                            System.out.println("Your Balance ("+selfUser.walletAddress.substring(0,10)+"...): " + blockchain.getBalance(selfUser.walletAddress));
                            break;
                        case "chain":
                            System.out.println("Current Blockchain (Height: "+blockchain.getChainHeight()+"):");
                            // Synchronize if reading from a list that might be modified by another thread
                            synchronized(blockchain) {
                                blockchain.getChain().forEach(System.out::println);
                            }
                            break;
                        case "mempool":
                            System.out.println("Mempool Transactions:");
                            synchronized(blockchain) {
                                blockchain.getMempool().forEach(System.out::println);
                                if(blockchain.getMempool().isEmpty()) System.out.println("(empty)");
                            }
                            break;
                        case "utxo":
                            System.out.println("Your UTXOs:");
                            synchronized(blockchain){
                                blockchain.getUTXO().stream()
                                        .filter(t -> t.address.equals(selfUser.walletAddress))
                                        .forEach(System.out::println);
                            }
                            break;
                        case "mine":
                            if(isMiner){
                                System.out.println("Attempting to mine a block manually...");
                                // mineNewBlock is synchronized
                                Block newBlock = blockchain.mineNewBlock(this.selfUser);
                                if (newBlock != null) {
                                    System.out.println("MINER: Manually Mined new block " + newBlock.index);
                                    saveBlockchain(); // saveBlockchain is synchronized
                                    broadcastMessage(new P2PMessage(P2PMessage.Type.BLOCK, newBlock));
                                } else {
                                    System.out.println("Mining did not produce a block (mempool might be empty or txs invalid).");
                                }
                            } else {
                                System.out.println("This node is not a miner.");
                            }
                            break;
                        case "peers":
                            System.out.println("Number of active peer output streams: " + peerOutputStreams.size());
                            // For more detailed peer info, you'd need to store Peer objects with IP/port alongside streams
                            break;
                        case "exit":
                            System.out.println("Exiting node...");
                            executorService.shutdownNow(); // Stop scheduled tasks like mining
                            // TODO: Gracefully close all peer connections in peerOutputStreams before exiting
                            for(ObjectOutputStream stream : peerOutputStreams) {
                                try { stream.close(); } catch (IOException e) { /* ignore */ }
                            }
                            System.exit(0);
                            return; // Exit CLI thread
                        default:
                            System.out.println("Unknown command. Try: tx, balance, chain, mempool, utxo, mine, peers, exit");
                    }
                } catch (NumberFormatException e){
                    System.out.println("Invalid number format in command.");
                } catch (Exception e) {
                    System.err.println("CLI Error: " + e.getMessage());
                    e.printStackTrace(); // For debugging unexpected errors
                }
            }
        }, "CLI-Thread").start();
    }

    private void createAndBroadcastTx(String receiverAddress, int amount) {
        // Access to blockchain's UTXO list needs to be safe if other threads modify it.
        // findSufficientUTXOs already iterates over a copy of UTXO.
        Blockchain.FundResult fundResult = blockchain.findSufficientUTXOs(selfUser.walletAddress, amount);
        if (fundResult.inputs.isEmpty() || fundResult.totalInputAmount < amount) {
            System.out.println("Insufficient funds. Required: " + amount + ", Available: " + fundResult.totalInputAmount);
            return;
        }

        List<Token> outputs = new ArrayList<>();
        outputs.add(new Token(receiverAddress, amount));
        if (fundResult.totalInputAmount > amount) {
            outputs.add(new Token(selfUser.walletAddress, fundResult.totalInputAmount - amount)); // Change
        }

        String txId = "tx_" + selfUser.walletAddress.substring(0, 4) + "_" + System.currentTimeMillis();
        Transaction newTx = new Transaction(txId, selfUser.walletAddress, fundResult.inputs, outputs,
                selfUser.signTransaction(txId), selfUser.publicKey);

        // addTransactionToMempool is synchronized if it modifies shared mempool directly.
        // Blockchain.addTransactionToMempool is not marked synchronized, but uses CopyOnWriteArrayList
        if (blockchain.addTransactionToMempool(newTx)) {
            broadcastMessage(new P2PMessage(P2PMessage.Type.TRANSACTION, newTx));
            System.out.println("Transaction " + txId + " created and broadcasted.");
        } else {
            System.out.println("Failed to add transaction to local mempool (possibly invalid or duplicate).");
        }
    }
}