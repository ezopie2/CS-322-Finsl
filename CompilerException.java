// Thrown when parsing or code generation fails
public class CompilerException extends Exception {
    private static final long serialVersionUID = 1L;

    public CompilerException(String message) {
        super(message);
    }
}