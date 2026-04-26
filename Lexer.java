import java.util.ArrayList;

// Breaks the input string into tokens
public class Lexer {

    public ArrayList<Token> tokenize(String input) throws LexerException {
        ArrayList<Token> tokens = new ArrayList<>();
        int i = 0;

        // Loop through input one character at a time
        while (i < input.length()) {
            char c = input.charAt(i);

            // Skip spaces and new lines
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // End of statement
            if (c == ';') {
                tokens.add(new Token("eos", ""));
                i++;
                continue;
            }

            // + must be arithmetic only, so += is invalid
            if (c == '+') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                    throw new LexerException("Invalid token in source code");
                }
                tokens.add(new Token("ar", "+"));
                i++;
                continue;
            }

            // * must be arithmetic only
            if (c == '*') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                    throw new LexerException("Invalid token in source code");
                }
                tokens.add(new Token("ar", "*"));
                i++;
                continue;
            }

            // / must be arithmetic only
            if (c == '/') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                    throw new LexerException("Invalid token in source code");
                }
                tokens.add(new Token("ar", "/"));
                i++;
                continue;
            }

            // Handle minus: either negative number or arithmetic operator
            if (c == '-') {
                // Negative number
                if (i + 1 < input.length() && Character.isDigit(input.charAt(i + 1))) {
                    int start = i;
                    i++;

                    while (i < input.length() && Character.isDigit(input.charAt(i))) {
                        i++;
                    }

                    // Invalid like -12X
                    if (i < input.length() && Character.isLetter(input.charAt(i))) {
                        throw new LexerException("Invalid token in source code");
                    }

                    // Check for double
                    if (i < input.length() && input.charAt(i) == '.') {
                        i++;

                        if (i >= input.length() || !Character.isDigit(input.charAt(i))) {
                            throw new LexerException("Invalid token in source code");
                        }

                        while (i < input.length() && Character.isDigit(input.charAt(i))) {
                            i++;
                        }

                        // Invalid like -12.5X
                        if (i < input.length() && Character.isLetter(input.charAt(i))) {
                            throw new LexerException("Invalid token in source code");
                        }

                        tokens.add(new Token("double", input.substring(start, i)));
                    } else {
                        tokens.add(new Token("int", input.substring(start, i)));
                    }
                } else {
                    // -= is invalid in this language
                    if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                        throw new LexerException("Invalid token in source code");
                    }

                    tokens.add(new Token("ar", "-"));
                    i++;
                }
                continue;
            }

            // = or ==
            if (c == '=') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '=') {
                        tokens.add(new Token("comp", "=="));
                        i += 2;
                    } else if (next == '<' || next == '>' || next == '!') {
                        // invalid sequences like =< or => or =!
                        throw new LexerException("Invalid token in source code");
                    } else {
                        tokens.add(new Token("assn", ""));
                        i++;
                    }
                } else {
                    tokens.add(new Token("assn", ""));
                    i++;
                }
                continue;
            }

            // < or <= only
            if (c == '<') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '=') {
                        tokens.add(new Token("comp", "<="));
                        i += 2;
                    } else if (next == '<' || next == '>') {
                        // invalid sequences like << or <>
                        throw new LexerException("Invalid token in source code");
                    } else {
                        tokens.add(new Token("comp", "<"));
                        i++;
                    }
                } else {
                    tokens.add(new Token("comp", "<"));
                    i++;
                }
                continue;
            }

            // > or >= only
            if (c == '>') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '=') {
                        tokens.add(new Token("comp", ">="));
                        i += 2;
                    } else if (next == '>' || next == '<') {
                        // invalid sequences like >> or ><
                        throw new LexerException("Invalid token in source code");
                    } else {
                        tokens.add(new Token("comp", ">"));
                        i++;
                    }
                } else {
                    tokens.add(new Token("comp", ">"));
                    i++;
                }
                continue;
            }

            // != only
            if (c == '!') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                    tokens.add(new Token("comp", "!="));
                    i += 2;
                } else {
                    throw new LexerException("Invalid token in source code");
                }
                continue;
            }

            // Identifiers / keywords
            if (Character.isLetter(c)) {
                int start = i;
                i++;

                while (i < input.length() && Character.isLetterOrDigit(input.charAt(i))) {
                    i++;
                }

                String word = input.substring(start, i);
                tokens.add(new Token("id_key", word));
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                int start = i;

                while (i < input.length() && Character.isDigit(input.charAt(i))) {
                    i++;
                }

                // Invalid like 12X
                if (i < input.length() && Character.isLetter(input.charAt(i))) {
                    throw new LexerException("Invalid token in source code");
                }

                // Check for double
                if (i < input.length() && input.charAt(i) == '.') {
                    i++;

                    if (i >= input.length() || !Character.isDigit(input.charAt(i))) {
                        throw new LexerException("Invalid token in source code");
                    }

                    while (i < input.length() && Character.isDigit(input.charAt(i))) {
                        i++;
                    }

                    // Invalid like 12.5X
                    if (i < input.length() && Character.isLetter(input.charAt(i))) {
                        throw new LexerException("Invalid token in source code");
                    }

                    tokens.add(new Token("double", input.substring(start, i)));
                } else {
                    tokens.add(new Token("int", input.substring(start, i)));
                }
                continue;
            }

            // Anything else is invalid
            throw new LexerException("Invalid token in source code");
        }

        return tokens;
    }
}