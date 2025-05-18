package example;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

public class User implements Serializable { // User itself can be serializable if needed elsewhere
    private static final long serialVersionUID = 1L;
    public String walletAddress;
    public PrivateKey privateKey;
    public PublicKey publicKey;

    public User() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
            this.walletAddress = Blockchain.applySHA256(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        } catch (Exception e) {
            throw new RuntimeException("Error creating user keys/wallet", e);
        }
    }

    public byte[] signTransaction(String data) {
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(privateKey);
            rsa.update(data.getBytes(StandardCharsets.UTF_8));
            return rsa.sign();
        } catch (Exception e) {
            throw new RuntimeException("Error signing transaction data", e);
        }
    }

    public static boolean verifySignature(PublicKey publicKey, String data, byte[] signature) {
        if (publicKey == null || data == null || signature == null) {
            System.err.println("Invalid parameters for signature verification.");
            return false;
        }
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(publicKey);
            rsa.update(data.getBytes(StandardCharsets.UTF_8));
            return rsa.verify(signature);
        } catch (Exception e) {
            System.err.println("Error verifying signature: " + e.getMessage());
            return false;
        }
    }
}