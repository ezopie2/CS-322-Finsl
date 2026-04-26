// Thrown when lexer finds bad input
public class LexerException extends Exception {
    private static final long serialVersionUID = 1L;

    public LexerException(String message) {
        super(message);
    }
}