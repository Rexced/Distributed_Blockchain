package example;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Blockchain implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Block> chain = new ArrayList<>();
    private List<Token> UTXO = new CopyOnWriteArrayList<>(); // Thread-safe for reads/writes
    private int difficulty = 2;
    private List<Transaction> mempool = new CopyOnWriteArrayList<>(); // Thread-safe

    public static class FundResult { // Keep this inner class
        public List<Token> inputs;
        public int totalInputAmount;
        public FundResult(List<Token> inputs, int totalInputAmount) {
            this.inputs = inputs;
            this.totalInputAmount = totalInputAmount;
        }
    }

    // Constructor for a new blockchain (e.g., by the first miner)
    public Blockchain(User genesisReceiver) {
        if (chain.isEmpty()) { // Only create genesis if chain is empty
            Block genesisBlock = createGenesisBlock(genesisReceiver);
            chain.add(genesisBlock);
            for (Transaction tx : genesisBlock.transactions) {
                UTXO.addAll(tx.output);
            }
            System.out.println("New blockchain initialized. Genesis block created for " + genesisReceiver.walletAddress.substring(0,10)+"...");
        }
    }

    // Default constructor for deserialization or when chain will be received
    public Blockchain() {
        System.out.println("Blockchain object created (possibly for loading or receiving).");
    }


    private Block createGenesisBlock(User receiver) {
        List<Token> genesisOutputs = List.of(new Token(receiver.walletAddress, 100));
        Transaction genesisTransaction = new Transaction(
                "tx0_genesis", "system_genesis", Collections.emptyList(), genesisOutputs, null, null);
        return new Block(0, "0", List.of(genesisTransaction), 0);
    }

    public synchronized List<Block> getChain() { return new ArrayList<>(chain); }
    public synchronized List<Token> getUTXO() { return new ArrayList<>(UTXO); }
    public List<Transaction> getMempool() { return new ArrayList<>(mempool); }
    public int getChainHeight() { return chain.size(); }
    public Block getLastBlock() { return chain.isEmpty() ? null : chain.get(chain.size() - 1); }

    public synchronized boolean addTransactionToMempool(Transaction transaction) {
        if (transaction == null || transaction.publicKey == null || transaction.signature == null) return false;
        if (!User.verifySignature(transaction.publicKey, transaction.transactionId, transaction.signature)) {
            System.err.println("MEMPOOL: Invalid signature for tx: " + transaction.transactionId);
            return false;
        }
        // Check against current UTXO for obvious double spends (more thorough check by miner)
        for(Token input : transaction.input) {
            if(!UTXO.contains(input)) {
                // This check might be too strict for a mempool if block confirmations are slow
                // System.err.println("MEMPOOL: Input token not found in UTXO for tx: " + transaction.transactionId + " input: " + input);
                // return false;
            }
        }
        for (Transaction tx : mempool) {
            if (tx.transactionId.equals(transaction.transactionId)) {
                System.out.println("MEMPOOL: Transaction " + transaction.transactionId + " already in mempool.");
                return false; // Already exists
            }
        }
        mempool.add(transaction);
        System.out.println("MEMPOOL: Added transaction: " + transaction.transactionId);
        return true;
    }

    public synchronized boolean addBlock(Block newBlock) {
        Block lastBlock = getLastBlock(); // Will be null if chain is empty
        if (newBlock == null) {
            System.err.println("CHAIN_ADD: Received null block.");
            return false;
        }

        // This USER DEBUG is from your logs, it's good for seeing received values
        // You might want to move it inside addBlock if it's not already, or call it from P2PNode before calling addBlock.
        // For now, let's assume it's called by P2PNode before this method.
        // System.out.println("USER DEBUG (Block " + newBlock.index + " received): index=" + newBlock.index + ...);

        String recalculatedHash = newBlock.calculateHash();
        // System.out.println("USER DEBUG: receivedHash=" + newBlock.hash + " recalculatedHash_on_receive=" + recalculatedHash); // Also good

        // 1. Check if the block's content matches its stored hash
        if (!newBlock.hash.equals(recalculatedHash)) {
            System.err.println("CHAIN_ADD: Invalid block hash (content mismatch) for block " + newBlock.index +
                            ". StoredHash=" + newBlock.hash + ", RecalculatedHash=" + recalculatedHash);
            return false;
        }

        // 2. Validate chain linkage (index and previous hash) AND Proof of Work
        if (chain.isEmpty() && newBlock.index == 0) { // CHANGED: was lastBlock == null
            // This is the genesis block being added to an empty chain.
            // Hash content check (done above) is the main validation. PoW is not typically applied or checked here.
            System.out.println("CHAIN_ADD: Accepting genesis block " + newBlock.index + " (PoW check skipped).");
        } else if (lastBlock == null) {
            // This case means chain is NOT empty but getLastBlock() returned null - indicates an inconsistent state.
            // Or, if chain IS empty but newBlock.index IS NOT 0.
            System.err.println("CHAIN_ADD: Cannot add block " + newBlock.index + ". Chain empty but not genesis, or inconsistent lastBlock.");
            return false;
        } else if (newBlock.index != lastBlock.index + 1) {
            System.err.println("CHAIN_ADD: Invalid block index for block " + newBlock.index +
                            ". Expected: " + (lastBlock.index + 1) + ", Got: " + newBlock.index);
            return false;
        } else if (!newBlock.previousHash.equals(lastBlock.hash)) {
            System.err.println("CHAIN_ADD: Invalid previous hash for block " + newBlock.index +
                            ". Expected: " + lastBlock.hash.substring(0,10) + "..., Got: " + newBlock.previousHash.substring(0,10) + "...");
            return false;
        } else {
            // This is a subsequent block (index > 0) being added to an existing chain
            // 3. Check Proof of Work for non-genesis blocks
            String target = String.format("%0" + difficulty + "d", 0); // e.g., "00"
            if (!newBlock.hash.substring(0, difficulty).equals(target)) {
                System.err.println("CHAIN_ADD: Proof of Work not met for block " + newBlock.index + ". Hash: " + newBlock.hash);
                return false;
            }
            System.out.println("CHAIN_ADD: PoW verified for block " + newBlock.index);
        }

        // 4. Transaction Validation (keep your existing loop)
        for (Transaction tx : newBlock.transactions) {
            if (!tx.transactionId.startsWith("coinbase_") && !tx.transactionId.equals("tx0_genesis")) {
                if (tx.publicKey == null || tx.signature == null || !User.verifySignature(tx.publicKey, tx.transactionId, tx.signature)) {
                    System.err.println("CHAIN_ADD: Invalid tx signature in block " + newBlock.index + " for tx " + tx.transactionId);
                    return false;
                }
            }
        }

        // If all checks pass, add the block
        chain.add(newBlock);
        System.out.println("CHAIN_ADD: Block " + newBlock.index + " successfully added. Hash: " + newBlock.hash.substring(0,10)+"...");

        // Update UTXO and remove mined transactions from mempool
        for (Transaction tx : newBlock.transactions) {
            System.out.println("CHAIN_ADD_UTXO: Processing TX_ID: " + tx.transactionId + " for UTXO update."); // DEBUG
            System.out.println("CHAIN_ADD_UTXO:  Inputs to remove: " + tx.input); // DEBUG
            UTXO.removeAll(tx.input);
            System.out.println("CHAIN_ADD_UTXO:  Outputs to add: " + tx.output); // DEBUG
            UTXO.addAll(tx.output);
            mempool.removeIf(memTx -> memTx.transactionId.equals(tx.transactionId));
        }
        System.out.println("CHAIN_ADD_UTXO: UTXO set size after block " + newBlock.index + ": " + UTXO.size()); // DEBUG
        return true;
    }

    public synchronized Block mineNewBlock(User minerRewardReceiver) {
        Block lastBlock = getLastBlock();
        if (lastBlock == null && !chain.isEmpty()) { // Should not happen if genesis is handled
            System.err.println("MINER: Chain exists but last block is null. Inconsistent state.");
            return null;
        }
        if (lastBlock == null && chain.isEmpty()) { // Very first block after construction
            System.out.println("MINER: Blockchain appears uninitialized. Genesis block should exist. Trying to create it if this is the designated first node.");
            // This scenario should ideally be handled by the Blockchain constructor ensuring genesis.
            // If we reach here, it means P2PNode might have called loadBlockchain, got an empty one,
            // but then didn't re-initialize with a genesis for selfUser.
            // For robustness, if chain is empty, and this node is a miner, it should ensure genesis.
            if (this.chain.isEmpty()) {
                System.out.println("MINER: Chain is indeed empty. Creating genesis for miner: " + minerRewardReceiver.walletAddress.substring(0,10)+"...");
                Block genesis = createGenesisBlock(minerRewardReceiver);
                if(addBlock(genesis)) { // addBlock also updates UTXO for genesis
                    System.out.println("MINER: Genesis block created and added.");
                    lastBlock = genesis; // Update lastBlock for the current mining operation
                } else {
                    System.err.println("MINER: Failed to add self-created genesis block.");
                    return null;
                }
            } else {
                // This case should ideally not be hit if constructor logic is correct.
                System.err.println("MINER: Cannot mine, blockchain not properly initialized.");
                return null;
            }
        }


        // Add coinbase transaction for miner reward
        List<Transaction> transactionsForBlock = new ArrayList<>(); // Start with empty list for this block
        Token rewardToken = new Token(minerRewardReceiver.walletAddress, 50); // 50 coin reward
        Transaction coinbaseTx = new Transaction("coinbase_" + (lastBlock.index + 1), "COINBASE",
                Collections.emptyList(), List.of(rewardToken), null, null);
        transactionsForBlock.add(coinbaseTx); // Coinbase is always part of a new block by this miner

        // Attempt to include transactions from the mempool
        List<Transaction> mempoolSnapshot = new ArrayList<>(mempool); // Take a snapshot
        if (!mempoolSnapshot.isEmpty()) {
            System.out.println("MINER: Mempool has " + mempoolSnapshot.size() + " transactions. Validating them...");
            List<Token> tempUTXO = new ArrayList<>(this.UTXO); // Use a copy of current UTXO for validation

            for (Transaction tx : mempoolSnapshot) {
                boolean txValid = true;
                if (tx.publicKey != null && tx.signature != null && !User.verifySignature(tx.publicKey, tx.transactionId, tx.signature)) {
                    System.err.println("MINER: Invalid signature for mempool tx " + tx.transactionId + ". Removing.");
                    mempool.remove(tx); // Remove from actual mempool
                    continue;
                }
                // Check inputs against tempUTXO
                int totalInputValueForTx = 0;
                List<Token> inputsUsedInThisTxValidation = new ArrayList<>();
                for (Token input : tx.input) {
                    if (tempUTXO.contains(input)) {
                        totalInputValueForTx += input.amount;
                        inputsUsedInThisTxValidation.add(input); // Mark for temporary removal
                    } else {
                        System.err.println("MINER: Input " + input + " for tx " + tx.transactionId + " not found in current UTXO context. Removing from mempool.");
                        mempool.remove(tx); // Remove from actual mempool
                        txValid = false;
                        break;
                    }
                }
                if (!txValid) continue;

                // Check if output <= input
                int totalOutputValueForTx = tx.output.stream().mapToInt(t -> t.amount).sum();
                if (totalOutputValueForTx > totalInputValueForTx) {
                    System.err.println("MINER: Output value ("+totalOutputValueForTx+") > input value ("+totalInputValueForTx+") for tx " + tx.transactionId + ". Removing from mempool.");
                    mempool.remove(tx);
                    continue;
                }

                transactionsForBlock.add(tx); // Add valid transaction to the block
                // Simulate spending for subsequent tx validation in this block by removing from tempUTXO
                tempUTXO.removeAll(inputsUsedInThisTxValidation);
                tempUTXO.addAll(tx.output); // And adding its outputs
                System.out.println("MINER: Added valid tx " + tx.transactionId + " to current mining block.");
            }
        }

        // Now, transactionsForBlock contains coinbase and any valid transactions from mempool
        // Even if no user transactions were valid or mempool was empty, it will contain coinbase.
        System.out.println("MINER: Creating block with " + transactionsForBlock.size() + " transaction(s).");

        Block newBlock = new Block(chain.size(), lastBlock.hash, transactionsForBlock, chain.size());
        newBlock.mineBlock(difficulty);

        if (addBlock(newBlock)) { // addBlock will update UTXO and clear *actually mined* txs from mempool
            return newBlock;
        } else {
            System.err.println("MINER: CRITICAL - Mined block failed to be added to own chain. This should not happen if validation is correct.");
            // This might indicate an issue in addBlock's own validation or a race condition if not properly synchronized.
            return null;
        }
    }

    public static String applySHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public FundResult findSufficientUTXOs(String ownerWalletAddress, int amountToSpend) {
        List<Token> spendableUTXOs = new ArrayList<>();
        int currentSum = 0;
        for (Token utxo : new ArrayList<>(this.UTXO)) { // Iterate over a copy
            if (utxo.address.equals(ownerWalletAddress)) {
                spendableUTXOs.add(utxo);
                currentSum += utxo.amount;
                if (currentSum >= amountToSpend) break;
            }
        }
        if (currentSum < amountToSpend) return new FundResult(Collections.emptyList(), 0);
        return new FundResult(new ArrayList<>(spendableUTXOs), currentSum);
    }

    public int getBalance(String walletAddress) {
        return UTXO.stream()
                .filter(token -> token.address.equals(walletAddress))
                .mapToInt(token -> token.amount)
                .sum();
    }

    public List<Block> getBlocksFromIndex(int index) {
        if (index < 0 || index >= chain.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(chain.subList(index, chain.size()));
    }
}