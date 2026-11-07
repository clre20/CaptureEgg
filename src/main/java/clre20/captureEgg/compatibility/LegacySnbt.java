package clre20.captureEgg.compatibility;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * 舊版 EggEmAll2 捕捉蛋的轉換工具。
 * <p>
 * EggEmAll2 把生物存成 SNBT 字串，且不記錄 Minecraft 資料版本。1.21.5 之後實體格式有變（例如名稱由 JSON 改成文字元件），
 * 直接讀取舊字串會讓名稱變成原始 JSON。這裡把 SNBT 轉成二進位 NBT 並標上舊資料版本，
 * 交給伺服器內建的資料升級器（與升級舊世界相同）處理。
 * <p>
 * 只處理 Paper EntitySnapshot#getAsString 會輸出的語法；巢狀深度與長度都有上限，避免偽造資料造成崩潰。
 */
public final class LegacySnbt {

    /** 1.20.4：物品還是 Count / tag 舊格式。 */
    private static final int DATA_VERSION_1_20_4 = 3700;
    /** 1.20.6：物品元件格式，但實體仍是 1.21.5 以前的格式。 */
    private static final int DATA_VERSION_1_20_6 = 3839;
    private static final int MAX_DEPTH = 256;

    private static final Pattern INT = Pattern.compile("[-+]?\\d+");
    private static final Pattern TYPED_INT = Pattern.compile("[-+]?\\d+[bBsSlL]");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?[fFdD]?");

    private LegacySnbt() {
    }

    /**
     * 判斷是否為 1.21.5 以前的格式，回傳應標記的資料版本；新格式回傳 -1（可直接讀取）。
     * 1.21.5 起實體一律有 fall_distance，舊版是 FallDistance。
     */
    public static int legacyDataVersion(String snbt) {
        if (snbt.contains("fall_distance:")) {
            return -1;
        }
        if (!snbt.contains("FallDistance:")) {
            return -1;
        }
        // 1.20.5 以前物品數量鍵是 Count（大寫）
        return snbt.contains("Count:") ? DATA_VERSION_1_20_4 : DATA_VERSION_1_20_6;
    }

    /** SNBT → gzip 壓縮的二進位 NBT（含 DataVersion），格式與 Bukkit.getUnsafe().serializeEntity 相同。 */
    public static byte[] toCompressedNbt(String snbt, int dataVersion) {
        Object root = new Parser(snbt).parseRoot();
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("root is not a compound");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> compound = (Map<String, Object>) map;
        compound.put("DataVersion", dataVersion);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(snbt.length());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeByte(10);
                out.writeUTF("");
                writeCompoundBody(out, compound);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // ------------------------------------------------------------------ 解析

    /** 用來區分 NBT 數字型別的包裝。 */
    private record Num(byte tag, Number value) {
    }

    private record Arr(byte tag, long[] values) {
    }

    private static final class Parser {
        private final String s;
        private int pos;
        private int depth;

        Parser(String s) {
            this.s = s;
        }

        Object parseRoot() {
            Object v = value();
            skipWs();
            if (pos != s.length()) {
                throw error("trailing data");
            }
            return v;
        }

        private Object value() {
            skipWs();
            if (pos >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return compound();
            }
            if (c == '[') {
                return listOrArray();
            }
            if (c == '"' || c == '\'') {
                return quoted();
            }
            return scalar(unquoted());
        }

        private Map<String, Object> compound() {
            enter();
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWs();
                String key = (peek() == '"' || peek() == '\'') ? quoted() : unquoted();
                if (key.isEmpty()) {
                    throw error("empty key");
                }
                skipWs();
                expect(':');
                map.put(key, value());
                skipWs();
                char c = next();
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw error("expected , or }");
                }
            }
            depth--;
            return map;
        }

        private Object listOrArray() {
            enter();
            pos++;
            if (pos + 1 < s.length() && s.charAt(pos + 1) == ';' && "BIL".indexOf(s.charAt(pos)) >= 0) {
                char type = s.charAt(pos);
                pos += 2;
                List<Long> vals = new ArrayList<>();
                skipWs();
                if (peek() != ']') {
                    while (true) {
                        skipWs();
                        String tok = unquoted();
                        if (!tok.isEmpty() && "bBlL".indexOf(tok.charAt(tok.length() - 1)) >= 0) {
                            tok = tok.substring(0, tok.length() - 1);
                        }
                        try {
                            vals.add(Long.parseLong(tok));
                        } catch (NumberFormatException e) {
                            throw error("bad array value");
                        }
                        skipWs();
                        char c = next();
                        if (c == ']') {
                            break;
                        }
                        if (c != ',') {
                            throw error("expected , or ]");
                        }
                    }
                } else {
                    pos++;
                }
                long[] arr = new long[vals.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = vals.get(i);
                }
                depth--;
                return new Arr(type == 'B' ? (byte) 7 : type == 'I' ? (byte) 11 : (byte) 12, arr);
            }
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                list.add(value());
                skipWs();
                char c = next();
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw error("expected , or ]");
                }
            }
            depth--;
            return list;
        }

        private String quoted() {
            char q = next();
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '\\') {
                    if (pos >= s.length()) {
                        throw error("bad escape");
                    }
                    sb.append(s.charAt(pos++));
                } else if (c == q) {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
        }

        private String unquoted() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '+') {
                    pos++;
                } else {
                    break;
                }
            }
            return s.substring(start, pos);
        }

        private Object scalar(String tok) {
            if (tok.isEmpty()) {
                throw error("empty value");
            }
            try {
                if (TYPED_INT.matcher(tok).matches()) {
                    char t = Character.toLowerCase(tok.charAt(tok.length() - 1));
                    String n = tok.substring(0, tok.length() - 1);
                    return switch (t) {
                        case 'b' -> new Num((byte) 1, Byte.parseByte(n));
                        case 's' -> new Num((byte) 2, Short.parseShort(n));
                        default -> new Num((byte) 4, Long.parseLong(n));
                    };
                }
                if (INT.matcher(tok).matches()) {
                    return new Num((byte) 3, Integer.parseInt(tok));
                }
                if (DECIMAL.matcher(tok).matches()) {
                    char last = Character.toLowerCase(tok.charAt(tok.length() - 1));
                    if (last == 'f') {
                        return new Num((byte) 5, Float.parseFloat(tok.substring(0, tok.length() - 1)));
                    }
                    String n = last == 'd' ? tok.substring(0, tok.length() - 1) : tok;
                    return new Num((byte) 6, Double.parseDouble(n));
                }
            } catch (NumberFormatException ignored) {
                // 超出範圍的數字當成字串
            }
            if (tok.equals("true")) {
                return new Num((byte) 1, (byte) 1);
            }
            if (tok.equals("false")) {
                return new Num((byte) 1, (byte) 0);
            }
            return tok;
        }

        private void enter() {
            if (++depth > MAX_DEPTH) {
                throw error("too deep");
            }
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= s.length()) {
                throw error("unexpected end");
            }
            return s.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw error("expected " + c);
            }
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("SNBT " + msg + " at " + pos);
        }
    }

    // ------------------------------------------------------------------ 輸出

    private static byte tagOf(Object v) {
        if (v instanceof Num n) {
            return n.tag();
        }
        if (v instanceof Integer) {
            return 3;
        }
        if (v instanceof Arr a) {
            return a.tag();
        }
        if (v instanceof String) {
            return 8;
        }
        if (v instanceof List) {
            return 9;
        }
        return 10;
    }

    private static void writeCompoundBody(DataOutputStream out, Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            out.writeByte(tagOf(e.getValue()));
            writeString(out, e.getKey());
            writePayload(out, e.getValue());
        }
        out.writeByte(0);
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        if (v instanceof Integer i) {
            out.writeInt(i);
        } else if (v instanceof Num n) {
            switch (n.tag()) {
                case 1 -> out.writeByte(n.value().byteValue());
                case 2 -> out.writeShort(n.value().shortValue());
                case 3 -> out.writeInt(n.value().intValue());
                case 4 -> out.writeLong(n.value().longValue());
                case 5 -> out.writeFloat(n.value().floatValue());
                default -> out.writeDouble(n.value().doubleValue());
            }
        } else if (v instanceof String str) {
            writeString(out, str);
        } else if (v instanceof Arr a) {
            out.writeInt(a.values().length);
            for (long x : a.values()) {
                switch (a.tag()) {
                    case 7 -> out.writeByte((int) x);
                    case 11 -> out.writeInt((int) x);
                    default -> out.writeLong(x);
                }
            }
        } else if (v instanceof List<?> list) {
            byte type = list.isEmpty() ? 0 : tagOf(list.get(0));
            for (Object o : list) {
                if (tagOf(o) != type) {
                    throw new IllegalArgumentException("mixed list");
                }
            }
            out.writeByte(type);
            out.writeInt(list.size());
            for (Object o : list) {
                writePayload(out, o);
            }
        } else {
            writeCompoundBody(out, (Map<String, Object>) v);
        }
    }

    /** NBT 字串使用 Java modified UTF-8，長度上限 65535 bytes。 */
    private static void writeString(DataOutputStream out, String s) throws IOException {
        if (s.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException("string too long");
        }
        out.writeUTF(s);
    }
}
