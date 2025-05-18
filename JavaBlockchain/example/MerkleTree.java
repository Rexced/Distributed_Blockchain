package example;

import java.util.ArrayList;
import java.util.List;

public class MerkleTree {
    public static String getMerkleRoot(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return Blockchain.applySHA256("");
        }

        List<String> currentLevelHashes = new ArrayList<>();
        for (Transaction t : transactions) {
            currentLevelHashes.add(t.transactionId != null ? t.transactionId : Blockchain.applySHA256(t.toString())); // Use tx.toString hash if ID is null
        }

        if (currentLevelHashes.isEmpty()) return Blockchain.applySHA256("");
        if (currentLevelHashes.size() == 1) {
            return Blockchain.applySHA256(currentLevelHashes.get(0));
        }

        while (currentLevelHashes.size() > 1) {
            List<String> nextLevelHashes = new ArrayList<>();
            for (int i = 0; i < currentLevelHashes.size(); i += 2) {
                String left = currentLevelHashes.get(i);
                String right = (i + 1 < currentLevelHashes.size()) ? currentLevelHashes.get(i + 1) : left;
                String combined = left + right;
                nextLevelHashes.add(Blockchain.applySHA256(combined));
            }
            currentLevelHashes = nextLevelHashes;
        }
        return currentLevelHashes.get(0);
    }
}