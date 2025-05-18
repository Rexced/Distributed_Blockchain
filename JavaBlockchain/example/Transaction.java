package example;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    public String transactionId;
    public String owner;
    public List<Token> input = new ArrayList<>();
    public List<Token> output = new ArrayList<>();
    public byte[] signature;
    public PublicKey publicKey;

    public Transaction(String transactionId, String owner, List<Token> input, List<Token> output, byte[] signature, PublicKey publicKey) {
        this.transactionId = transactionId;
        this.owner = owner;
        this.input = new ArrayList<>(input);
        this.output = new ArrayList<>(output);
        this.signature = signature;
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", owner='" + (owner != null && owner.length() > 10 ? owner.substring(0,10) : owner) + "...'" +
                ", inputs=" + input.size() +
                ", outputs=" + output.size() +
                '}';
    }
}