package example;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Block implements Serializable {
    private static final long serialVersionUID = 1L;
    public int index;
    public long timestamp;
    public String previousHash;
    public String hash;
    public String merkleRoot;
    public int nonce;
    public List<Transaction> transactions;
    public int height;

    public Block(int index, String previousHash, List<Transaction> transactions, int height) {
        this.index = index;
        this.timestamp = new Date().getTime();
        this.previousHash = previousHash;
        this.transactions = new ArrayList<>(transactions);
        this.merkleRoot = MerkleTree.getMerkleRoot(this.transactions);
        this.height = height;
        this.nonce = 0;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        return Blockchain.applySHA256(
                index +
                timestamp +
                previousHash +
                merkleRoot +
                nonce +
                height
        );
}


    public void mineBlock(int difficulty) {
        this.merkleRoot = MerkleTree.getMerkleRoot(this.transactions); // Recalculate before mining
        String target = String.format("%0" + difficulty + "d", 0); // e.g., "00"
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
        System.out.println("Block Mined: " + hash + " (Nonce: " + nonce + ")");
    }

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", hash='" + hash.substring(0,10) + "...'" +
                ", previousHash='" + (previousHash.equals("0") ? "0" : previousHash.substring(0,10) + "...") + '\'' +
                ", txCount=" + transactions.size() +
                '}';
    }
}