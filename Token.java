// Stores a token type and its value
public class Token {
    private String type;
    private String value;

    public Token(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    // Prints token nicely
    @Override
    public String toString() {
        return "<\"" + type + "\", \"" + value + "\">";
    }
}