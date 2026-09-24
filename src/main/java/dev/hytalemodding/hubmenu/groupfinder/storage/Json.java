package dev.hytalemodding.hubmenu.groupfinder.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Маленький разборщик и писатель JSON без внешних библиотек.
 *
 * Нужен для файла настроек поиска группы: сервер не обязан отдавать плагину
 * Gson, а тащить зависимость ради одного файла настроек не хочется.
 *
 * Разбор возвращает обычные объекты Java:
 * объект → {@link LinkedHashMap}, массив → {@link ArrayList},
 * строка → String, число → Double, логическое → Boolean, null → null.
 *
 * Писатель выводит текст с отступами, чтобы файл можно было править руками.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ чтение

    /** Разбирает JSON-текст. Бросает {@link JsonException}, если текст битый. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("лишний текст после значения, позиция " + parser.index);
        }
        return value;
    }

    /** Ошибка разбора JSON. */
    public static class JsonException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {

        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return this.index >= this.text.length();
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char c = this.text.charAt(this.index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    this.index++;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("текст оборвался");
            }
            return this.text.charAt(this.index);
        }

        private Object readValue() {
            char c = peek();
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private void expect(String literal) {
            if (!this.text.startsWith(literal, this.index)) {
                throw new JsonException("ожидалось " + literal + ", позиция " + this.index);
            }
            this.index += literal.length();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            this.index++; // {
            skipWhitespace();
            if (peek() == '}') {
                this.index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                if (peek() != ':') {
                    throw new JsonException("ожидалось ':', позиция " + this.index);
                }
                this.index++;
                skipWhitespace();
                result.put(key, readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    this.index++;
                    continue;
                }
                if (c == '}') {
                    this.index++;
                    return result;
                }
                throw new JsonException("ожидалось ',' или '}', позиция " + this.index);
            }
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            this.index++; // [
            skipWhitespace();
            if (peek() == ']') {
                this.index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    this.index++;
                    continue;
                }
                if (c == ']') {
                    this.index++;
                    return result;
                }
                throw new JsonException("ожидалось ',' или ']', позиция " + this.index);
            }
        }

        private String readString() {
            if (peek() != '"') {
                throw new JsonException("ожидалась строка, позиция " + this.index);
            }
            this.index++;
            StringBuilder builder = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("строка не закрыта");
                }
                char c = this.text.charAt(this.index++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }
                char escaped = this.text.charAt(this.index++);
                switch (escaped) {
                    case '"':  builder.append('"');  break;
                    case '\\': builder.append('\\'); break;
                    case '/':  builder.append('/');  break;
                    case 'b':  builder.append('\b'); break;
                    case 'f':  builder.append('\f'); break;
                    case 'n':  builder.append('\n'); break;
                    case 'r':  builder.append('\r'); break;
                    case 't':  builder.append('\t'); break;
                    case 'u':
                        String hex = this.text.substring(this.index, this.index + 4);
                        builder.append((char) Integer.parseInt(hex, 16));
                        this.index += 4;
                        break;
                    default:
                        throw new JsonException("неизвестный escape \\" + escaped);
                }
            }
        }

        private Double readNumber() {
            int start = this.index;
            while (!atEnd()) {
                char c = this.text.charAt(this.index);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    this.index++;
                } else {
                    break;
                }
            }
            if (start == this.index) {
                throw new JsonException("ожидалось число, позиция " + this.index);
            }
            try {
                return Double.valueOf(this.text.substring(start, this.index));
            } catch (NumberFormatException exception) {
                throw new JsonException("непонятное число, позиция " + start);
            }
        }
    }

    // ------------------------------------------------------------------ запись

    /** Превращает значение в JSON-текст с отступами. */
    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, 0);
        builder.append('\n');
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value, int depth) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof Map) {
            writeObject(builder, (Map<?, ?>) value, depth);
        } else if (value instanceof List) {
            writeArray(builder, (List<?>) value, depth);
        } else if (value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Number) {
            builder.append(number((Number) value));
        } else {
            writeString(builder, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder builder, Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            builder.append("{}");
            return;
        }
        builder.append("{\n");
        int left = map.size();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(builder, depth + 1);
            writeString(builder, String.valueOf(entry.getKey()));
            builder.append(": ");
            writeValue(builder, entry.getValue(), depth + 1);
            if (--left > 0) {
                builder.append(',');
            }
            builder.append('\n');
        }
        indent(builder, depth);
        builder.append('}');
    }

    private static void writeArray(StringBuilder builder, List<?> list, int depth) {
        if (list.isEmpty()) {
            builder.append("[]");
            return;
        }
        builder.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(builder, depth + 1);
            writeValue(builder, list.get(i), depth + 1);
            if (i < list.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        indent(builder, depth);
        builder.append(']');
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  builder.append("\\\""); break;
                case '\\': builder.append("\\\\"); break;
                case '\n': builder.append("\\n");  break;
                case '\r': builder.append("\\r");  break;
                case '\t': builder.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        builder.append('"');
    }

    /** Целые числа пишем без хвоста ".0" — так файл читается приятнее. */
    private static String number(Number value) {
        double d = value.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static void indent(StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
    }

    // ------------------------------------------------------- безопасные геттеры

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return value instanceof List ? (List<Object>) value : new ArrayList<>();
    }

    public static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public static int integer(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public static double number(Map<String, Object> object, String key, double fallback) {
        Object value = object.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
