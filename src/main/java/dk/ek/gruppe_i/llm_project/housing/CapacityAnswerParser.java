package dk.ek.gruppe_i.llm_project.housing;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the model's answer: one JSON object {"capacity": 54, "confidence": "medium", "reasoning": "..."}.
 * Small local models add "thinking" blocks, code fences or text around the object, so the parser removes
 * those, finds the first balanced object and checks the values. No JSON library is needed for this flat object.
 */
public final class CapacityAnswerParser {

    public record Answer(int capacity, String confidence, String reasoning) {
    }

    public static class AnswerException extends RuntimeException {
        public AnswerException(String message) {
            super(message);
        }
    }

    private static final Set<String> CONFIDENCE = Set.of("low", "medium", "high");

    private CapacityAnswerParser() {
    }

    public static Answer parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AnswerException("empty answer");
        }
        String text = stripThinking(raw);
        Map<String, Object> object = new Reader(extractFirstObject(text)).readObject();
        Object capacityValue = object.get("capacity");
        double capacity;
        if (capacityValue instanceof Double number) {
            capacity = number;
        } else if (capacityValue instanceof String string) {
            try {
                capacity = Double.parseDouble(string.trim());
            } catch (NumberFormatException e) {
                throw new AnswerException("capacity is not a number: " + string);
            }
        } else {
            throw new AnswerException("answer has no capacity");
        }
        if (Double.isNaN(capacity) || Double.isInfinite(capacity) || capacity < 1 || capacity > 1_000_000) {
            throw new AnswerException("capacity must be a number from 1 to 1000000, got " + capacity);
        }
        String confidence = object.get("confidence") instanceof String s ? s.trim().toLowerCase(Locale.ROOT) : "";
        if (!CONFIDENCE.contains(confidence)) {
            confidence = "unknown";
        }
        String reasoning = object.get("reasoning") instanceof String s ? s.trim() : "";
        return new Answer((int) Math.rint(capacity), confidence, reasoning);
    }

    /** Removes {@code <think>...</think>} blocks (qwen3 and similar), also a block that was never closed. */
    static String stripThinking(String raw) {
        return raw.replaceAll("(?s)<think>.*?</think>", "").replaceAll("(?s)<think>.*$", "");
    }

    /** The first balanced {...}, ignoring braces inside strings. */
    static String extractFirstObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            throw new AnswerException("no JSON object in the answer");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw new AnswerException("JSON object is not closed");
    }

    /** Minimal reader for a flat JSON object: string, number, true, false, null values. */
    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        Map<String, Object> readObject() {
            Map<String, Object> result = new HashMap<>();
            skip();
            expect('{');
            skip();
            if (peek() == '}') {
                i++;
                return result;
            }
            while (true) {
                skip();
                String key = readString();
                skip();
                expect(':');
                skip();
                result.put(key, readValue());
                skip();
                char c = next();
                if (c == '}') {
                    return result;
                }
                if (c != ',') {
                    throw new AnswerException("expected ',' or '}' at position " + (i - 1));
                }
            }
        }

        private Object readValue() {
            char c = peek();
            if (c == '"') {
                return readString();
            }
            if (c == '{' || c == '[') {
                throw new AnswerException("nested values are not supported");
            }
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String token = s.substring(start, i);
            switch (token) {
                case "true":
                    return Boolean.TRUE;
                case "false":
                    return Boolean.FALSE;
                case "null":
                    return null;
                default:
                    try {
                        return Double.parseDouble(token);
                    } catch (NumberFormatException e) {
                        throw new AnswerException("not a JSON value: " + token);
                    }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw new AnswerException("string is not closed");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw new AnswerException("string is not closed");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw new AnswerException("bad unicode escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new AnswerException("bad unicode escape");
                        }
                        i += 4;
                    }
                    default -> out.append(e);
                }
            }
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw new AnswerException("unexpected end of JSON");
            }
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new AnswerException("expected '" + c + "' at position " + (i - 1));
            }
        }
    }
}
