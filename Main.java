// Runs tests for lexer and compiler
public class Main {

    public static void main(String[] args) {
        Lexer lexer = new Lexer();

        try {
            System.out.println(lexer.tokenize("INPUT X;"));
            System.out.println(lexer.tokenize("X = 5 * 10;"));
            System.out.println(lexer.tokenize("IF X >= 5 THEN PRINT X;"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        String program =
                "INT X;\n" +
                "INT Y;\n" +
                "DOUBLE Z;\n" +
                "X = 5;\n" +
                "Y = X + 10;\n" +
                "Z = 1.5;\n" +
                "PRINT X;\n" +
                "PRINT Y;\n" +
                "PRINT Z;\n" +
                "IF Y >= X THEN PRINT Y;\n";

        ArithmaticaCompiler compiler = new ArithmaticaCompiler();

        try {
            compiler.compile(program, "TestProgram");
            System.out.println("Compiled successfully");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}