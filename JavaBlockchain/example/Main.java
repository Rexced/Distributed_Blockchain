package example;

import java.security.*;
import java.util.Base64;
// No Bouncy Castle imports needed for these parts
import java.io.StringWriter; // Still need this if you implement PEM manually
import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;

// For PEM, you might need to handle DER encoding and Base64 manually or use a simpler approach
// For RIPEMD-160, you'd need an alternative or omit it.
// For secp256k1, standard JCE might not have it, or it might be named differently.

public class Main {

    // No need for Security.addProvider(new BouncyCastleProvider()); if not using BC features

    public static void main(String[] args) throws Exception {
        String name = "Saim Wajid";

        // Generate RSA Key Pair
        KeyPair rsaKeyPair = generateRSAKeyPair();
        System.out.println("Public Key (Standard Java format): " + Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded()));
        System.out.println("Private Key (Standard Java format): " + Base64.getEncoder().encodeToString(rsaKeyPair.getPrivate().getEncoded()));
        // Converting to PEM without BC is more involved (manually add headers/footers to Base64 of DER)

        // Hashing with SHA-256 (Built-in)
        System.out.println("SHA-256 Hash: " + hashWithAlgorithm("SHA-256", name));
        // SHA3-256 might require Java 9+ or a specific provider. Let's assume SHA-256 for simplicity here.

        // RIPEMD-160: Not standard in JCE. You would need to find an alternative or remove this.
        // System.out.println("RIPEMD-160 Hash: " + ripemd160Hash(name)); // Would need BC

        // Generate Bitcoin Wallet Address using ECC (secp256k1)
        // secp256k1 is often not available in the default JCE providers or has a different name.
        // This part would likely still require Bouncy Castle for secp256k1 specifically.
        // String bitcoinAddress = generateBitcoinWalletECC_NoBC_IfCurveAvailable();
        // System.out.println("Bitcoin Wallet Address (ECC - if curve available): " + bitcoinAddress);

        // Encrypt and Sign Name using RSA (Built-in)
        byte[] encryptedName = encryptRSA(name, rsaKeyPair.getPublic());
        System.out.println("Encrypted Name: " + Base64.getEncoder().encodeToString(encryptedName));

        byte[] signature = signData(name.getBytes(StandardCharsets.UTF_8), rsaKeyPair.getPrivate());
        System.out.println("Digital Signature: " + Base64.getEncoder().encodeToString(signature));
    }

    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    // PEM conversion without JcaPEMWriter is manual:
    public static String convertToPEMManual(Key key, String type) {
        StringWriter sw = new StringWriter();
        sw.write("-----BEGIN " + type + " KEY-----\n");
        // JCA keys are typically X.509 for public, PKCS#8 for private (DER encoded)
        String encodedKey = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        sw.write(encodedKey);
        sw.write("\n-----END " + type + " KEY-----\n");
        return sw.toString();
    }


    public static String hashWithAlgorithm(String algorithm, String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        // Convert bytes to hex string (you'd need a utility for this or write one)
        return bytesToHex(hashBytes);
    }

    public static byte[] encryptRSA(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding"); // Or just "RSA"
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] signData(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    // Utility to convert byte array to hex string
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}