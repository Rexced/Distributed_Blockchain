package example;

import java.io.Serializable;
import java.util.Objects;

public class Token implements Serializable {
    private static final long serialVersionUID = 1L; // Good practice
    public String address; // Owner's walletAddress
    public int amount;

    public Token(String address, int amount) {
        this.address = address;
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return amount == token.amount &&
                Objects.equals(address, token.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, amount);
    }

    @Override
    public String toString() {
        return "Token{" +
                "address='" + (address != null && address.length() > 10 ? address.substring(0, 10) : address) + "...'," +
                " amount=" + amount +
                '}';
    }
}