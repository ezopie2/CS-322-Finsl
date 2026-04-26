import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

// Converts ARITHMATICA source into JVM bytecode
public class ArithmaticaCompiler {

    private ArrayList<Token> tokens;
    private int pos;

    // Stores declared variables and their types
    private final Map<String, String> symbolTable = new HashMap<>();

    private ClassWriter cw;
    private MethodVisitor mv;
    private String className;

    // Small helper object for literals and variables
    private static class Operand {
        String type;      // "INT" or "DOUBLE"
        boolean literal;  // true if number literal, false if variable
        String value;     // literal text or variable name

        Operand(String type, boolean literal, String value) {
            this.type = type;
            this.literal = literal;
            this.value = value;
        }
    }

    // Main compile method
    public void compile(String source, String outputClassName) throws Exception {
        Lexer lexer = new Lexer();
        tokens = lexer.tokenize(source);
        pos = 0;
        className = outputClassName;
        symbolTable.clear();

        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);

        addDefaultConstructor();
        addScannerField();
        addClassInitializer();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();

        while (!isAtEnd()) {
            parseStatement(true);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        // Write .class file to Eclipse bin folder
        FileOutputStream fos = new FileOutputStream("./bin/" + outputClassName + ".class");
        fos.write(cw.toByteArray());
        fos.close();
    }

    // Adds default constructor
    private void addDefaultConstructor() {
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
    }

    // Scanner field used for INPUT
    private void addScannerField() {
        FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "SCANNER",
                "Ljava/util/Scanner;", null, null);
        fv.visitEnd();
    }

    // Initializes Scanner once
    private void addClassInitializer() {
        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();

        clinit.visitTypeInsn(NEW, "java/util/Scanner");
        clinit.visitInsn(DUP);
        clinit.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
        clinit.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
        clinit.visitFieldInsn(PUTSTATIC, className, "SCANNER", "Ljava/util/Scanner;");

        clinit.visitInsn(RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
    }

    // Parses one statement
    private void parseStatement(boolean allowIf) throws CompilerException {
        if (isAtEnd()) {
            return;
        }

        Token t = peek();

        if (!t.getType().equals("id_key")) {
            throw new CompilerException("Invalid statement");
        }

        String val = t.getValue();

        if (val.equals("INT") || val.equals("DOUBLE")) {
            parseDeclaration();
        } else if (val.equals("PRINT")) {
            parsePrint();
        } else if (val.equals("INPUT")) {
            parseInput();
        } else if (val.equals("IF")) {
            if (!allowIf) {
                throw new CompilerException("Invalid statement: nested IF-THEN is not allowed");
            }
            parseIfThen();
        } else if (val.equals("THEN")) {
            throw new CompilerException("Incorrect use of keyword THEN");
        } else {
            if (isKeyword(val)) {
                throw new CompilerException("Incorrect use of keyword " + val);
            }
            parseAssignment();
        }
    }

    // Handles INT X; or DOUBLE Y;
    private void parseDeclaration() throws CompilerException {
        String type = advance().getValue();
        String var = consumeIdentifier();

        if (symbolTable.containsKey(var)) {
            throw new CompilerException("Invalid statement: variable already declared");
        }

        symbolTable.put(var, type);

        String desc = getDescriptor(type);
        cw.visitField(ACC_PUBLIC + ACC_STATIC, var, desc, null, null).visitEnd();

        consume("eos");
    }

    // Handles X = 5; and X = A + 3;
    private void parseAssignment() throws CompilerException {
        String var = consumeIdentifier();

        if (!symbolTable.containsKey(var)) {
            throw new CompilerException("Invalid statement: variable used before declaration");
        }

        String targetType = symbolTable.get(var);

        consume("assn");

        Operand left = parseOperand();

        if (match("ar")) {
            String op = previous().getValue();
            Operand right = parseOperand();

            if (!left.type.equals(right.type)) {
                throw new CompilerException("Operations mixing double and int literals/variables are not allowed");
            }

            if (!left.type.equals(targetType)) {
                throw new CompilerException("Only direct assignment can perform type conversion");
            }

            emitLoadOperand(left);
            emitLoadOperand(right);
            emitArithmetic(op, targetType);
            emitStoreVariable(var, targetType);
        } else {
            emitLoadOperand(left);
            emitDirectConversionIfNeeded(left.type, targetType);
            emitStoreVariable(var, targetType);
        }

        consume("eos");
    }

    // Handles PRINT X; or PRINT 3.14;
    private void parsePrint() throws CompilerException {
        advance(); // PRINT

        Operand value = parseOperand();

        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        emitLoadOperand(value);

        if (value.type.equals("INT")) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(D)V", false);
        }

        consume("eos");
    }

    // Handles INPUT X;
    private void parseInput() throws CompilerException {
        advance(); // INPUT

        String var = consumeIdentifier();

        if (!symbolTable.containsKey(var)) {
            throw new CompilerException("Invalid statement: variable used before declaration");
        }

        String type = symbolTable.get(var);

        mv.visitFieldInsn(GETSTATIC, className, "SCANNER", "Ljava/util/Scanner;");

        if (type.equals("INT")) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextInt", "()I", false);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextDouble", "()D", false);
        }

        emitStoreVariable(var, type);

        consume("eos");
    }

    // Handles IF <operand> <comp> <operand> THEN <statement>
    private void parseIfThen() throws CompilerException {
        advance(); // IF

        Operand left = parseOperand();
        Token compToken = consume("comp");
        Operand right = parseOperand();

        if (!left.type.equals(right.type)) {
            throw new CompilerException("Operations mixing double and int literals/variables are not allowed");
        }

        Token thenToken = consume("id_key");
        if (!thenToken.getValue().equals("THEN")) {
            throw new CompilerException("Invalid statement: expected THEN");
        }

        Label skipLabel = new Label();

        emitLoadOperand(left);
        emitLoadOperand(right);
        emitJumpIfFalse(compToken.getValue(), left.type, skipLabel);

        parseStatement(false);

        mv.visitLabel(skipLabel);
    }

    // Parses either a literal or a declared variable
    private Operand parseOperand() throws CompilerException {
        if (isAtEnd()) {
            throw new CompilerException("Invalid statement");
        }

        Token t = advance();

        if (t.getType().equals("int")) {
            return new Operand("INT", true, t.getValue());
        }

        if (t.getType().equals("double")) {
            return new Operand("DOUBLE", true, t.getValue());
        }

        if (t.getType().equals("id_key")) {
            String val = t.getValue();

            if (isKeyword(val)) {
                throw new CompilerException("Incorrect use of keyword " + val);
            }

            if (!symbolTable.containsKey(val)) {
                throw new CompilerException("Invalid statement: variable used before declaration");
            }

            return new Operand(symbolTable.get(val), false, val);
        }

        throw new CompilerException("Invalid statement");
    }

    // Loads a literal or variable onto the stack
    private void emitLoadOperand(Operand operand) {
        if (operand.literal) {
            if (operand.type.equals("INT")) {
                mv.visitLdcInsn(Integer.parseInt(operand.value));
            } else {
                mv.visitLdcInsn(Double.parseDouble(operand.value));
            }
        } else {
            mv.visitFieldInsn(GETSTATIC, className, operand.value, getDescriptor(operand.type));
        }
    }

    // Used only for direct assignment conversions
    private void emitDirectConversionIfNeeded(String fromType, String toType) {
        if (fromType.equals(toType)) {
            return;
        }

        if (fromType.equals("INT") && toType.equals("DOUBLE")) {
            mv.visitInsn(I2D);
        } else if (fromType.equals("DOUBLE") && toType.equals("INT")) {
            mv.visitInsn(D2I);
        }
    }

    // Emits + - * /
    private void emitArithmetic(String op, String type) throws CompilerException {
        if (type.equals("INT")) {
            if (op.equals("+")) {
                mv.visitInsn(IADD);
            } else if (op.equals("-")) {
                mv.visitInsn(ISUB);
            } else if (op.equals("*")) {
                mv.visitInsn(IMUL);
            } else if (op.equals("/")) {
                mv.visitInsn(IDIV);
            } else {
                throw new CompilerException("Invalid statement");
            }
        } else {
            if (op.equals("+")) {
                mv.visitInsn(DADD);
            } else if (op.equals("-")) {
                mv.visitInsn(DSUB);
            } else if (op.equals("*")) {
                mv.visitInsn(DMUL);
            } else if (op.equals("/")) {
                mv.visitInsn(DDIV);
            } else {
                throw new CompilerException("Invalid statement");
            }
        }
    }

    // Stores stack top into variable field
    private void emitStoreVariable(String var, String type) {
        mv.visitFieldInsn(PUTSTATIC, className, var, getDescriptor(type));
    }

    // Jumps when IF condition is false
    private void emitJumpIfFalse(String comp, String type, Label falseLabel) throws CompilerException {
        if (type.equals("INT")) {
            if (comp.equals("==")) {
                mv.visitJumpInsn(IF_ICMPNE, falseLabel);
            } else if (comp.equals("!=")) {
                mv.visitJumpInsn(IF_ICMPEQ, falseLabel);
            } else if (comp.equals("<")) {
                mv.visitJumpInsn(IF_ICMPGE, falseLabel);
            } else if (comp.equals("<=")) {
                mv.visitJumpInsn(IF_ICMPGT, falseLabel);
            } else if (comp.equals(">")) {
                mv.visitJumpInsn(IF_ICMPLE, falseLabel);
            } else if (comp.equals(">=")) {
                mv.visitJumpInsn(IF_ICMPLT, falseLabel);
            } else {
                throw new CompilerException("Invalid statement");
            }
        } else {
            mv.visitInsn(DCMPL);

            if (comp.equals("==")) {
                mv.visitJumpInsn(IFNE, falseLabel);
            } else if (comp.equals("!=")) {
                mv.visitJumpInsn(IFEQ, falseLabel);
            } else if (comp.equals("<")) {
                mv.visitJumpInsn(IFGE, falseLabel);
            } else if (comp.equals("<=")) {
                mv.visitJumpInsn(IFGT, falseLabel);
            } else if (comp.equals(">")) {
                mv.visitJumpInsn(IFLE, falseLabel);
            } else if (comp.equals(">=")) {
                mv.visitJumpInsn(IFLT, falseLabel);
            } else {
                throw new CompilerException("Invalid statement");
            }
        }
    }

    // Returns JVM field descriptor
    private String getDescriptor(String type) {
        return type.equals("INT") ? "I" : "D";
    }

    // True if this is one of the language keywords
    private boolean isKeyword(String word) {
        return word.equals("INT") ||
               word.equals("DOUBLE") ||
               word.equals("PRINT") ||
               word.equals("INPUT") ||
               word.equals("IF") ||
               word.equals("THEN");
    }

    // Consumes an identifier that is not a keyword
    private String consumeIdentifier() throws CompilerException {
        Token t = consume("id_key");

        if (isKeyword(t.getValue())) {
            throw new CompilerException("Incorrect use of keyword " + t.getValue());
        }

        return t.getValue();
    }

    // Helper methods
    private Token consume(String type) throws CompilerException {
        if (isAtEnd() || !peek().getType().equals(type)) {
            throw new CompilerException("Invalid statement");
        }
        return advance();
    }

    private boolean match(String type) {
        if (isAtEnd()) {
            return false;
        }

        if (!peek().getType().equals(type)) {
            return false;
        }

        advance();
        return true;
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private boolean isAtEnd() {
        return pos >= tokens.size();
    }
}