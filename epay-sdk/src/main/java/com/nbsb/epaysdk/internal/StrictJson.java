package com.nbsb.epaysdk.internal;

import com.nbsb.epaysdk.api.EPayException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 严格 JSON 词法树：递归拒绝重复键，保留数值词素，不经 DTO 重写后再验签。 */
public final class StrictJson {
  private static final int MAX_CHARS = 1024 * 1024;
  private static final int MAX_DEPTH = 64;
  private static final Pattern NUMBER =
      Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

  private StrictJson() {}

  public static Value parse(String json) {
    if (json == null || json.length() > MAX_CHARS) throw EPayException.protocol();
    try {
      Parser parser = new Parser(json);
      Value result = parser.value(0);
      parser.whitespace();
      if (parser.position != json.length()) throw EPayException.protocol();
      return result;
    } catch (RuntimeException e) {
      throw EPayException.protocol();
    }
  }

  public static ObjectValue parseObject(String json) {
    if (parse(json) instanceof ObjectValue object) return object;
    throw EPayException.protocol();
  }

  public sealed interface Value permits ObjectValue, ArrayValue, Scalar, NullValue {
    /** 原始 JSON 片段，仅供协议消费，不应写日志。 */
    String raw();
  }

  public static final class ObjectValue implements Value {
    private final Map<String, Value> fields;
    private final String raw;

    private ObjectValue(Map<String, Value> fields, String raw) {
      this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
      this.raw = raw;
    }

    public Map<String, Value> fields() {
      return fields;
    }

    public Value get(String key) {
      return fields.get(key);
    }

    public boolean contains(String key) {
      return fields.containsKey(key);
    }

    public String text(String key) {
      Value value = fields.get(key);
      if (value == null || value instanceof NullValue) return null;
      if (value instanceof Scalar scalar) return scalar.text();
      throw EPayException.protocol();
    }

    public String requireText(String key) {
      String value = text(key);
      if (value == null || value.isEmpty()) throw EPayException.protocol();
      return value;
    }

    public ObjectValue object(String key) {
      if (fields.get(key) instanceof ObjectValue object) return object;
      throw EPayException.protocol();
    }

    public ArrayValue array(String key) {
      if (fields.get(key) instanceof ArrayValue array) return array;
      throw EPayException.protocol();
    }

    /** 只适用于协议声明的平面签名对象；嵌套值不得静默丢弃。 */
    public Map<String, String> scalarParameters() {
      Map<String, String> values = new LinkedHashMap<>();
      fields.forEach((key, value) -> values.put(key, text(key)));
      return Collections.unmodifiableMap(values);
    }

    @Override
    public String raw() {
      return raw;
    }

    @Override
    public String toString() {
      return "JsonObject[已遮蔽]";
    }
  }

  public static final class ArrayValue implements Value {
    private final List<Value> values;
    private final String raw;

    private ArrayValue(List<Value> values, String raw) {
      this.values = List.copyOf(values);
      this.raw = raw;
    }

    public List<Value> values() {
      return values;
    }

    @Override
    public String raw() {
      return raw;
    }

    @Override
    public String toString() {
      return "JsonArray[已遮蔽]";
    }
  }

  public static final class Scalar implements Value {
    public enum Type {
      STRING,
      NUMBER,
      BOOLEAN
    }

    private final Type type;
    private final String text;
    private final String raw;

    private Scalar(Type type, String text, String raw) {
      this.type = type;
      this.text = text;
      this.raw = raw;
    }

    public Type type() {
      return type;
    }

    /** 字符串返回解码后的原值，数值返回完整词素，包括尾零和指数。 */
    public String text() {
      return text;
    }

    @Override
    public String raw() {
      return raw;
    }

    @Override
    public String toString() {
      return "JsonScalar[已遮蔽]";
    }
  }

  public static final class NullValue implements Value {
    private NullValue() {}

    @Override
    public String raw() {
      return "null";
    }

    @Override
    public String toString() {
      return "JsonNull";
    }
  }

  private static final class Parser {
    private final String input;
    private int position;
    private int nodes;

    private Parser(String input) {
      this.input = input;
    }

    private Value value(int depth) {
      if (depth > MAX_DEPTH || ++nodes > 100000) throw EPayException.protocol();
      whitespace();
      int start = position;
      char token = current();
      if (token == '{') return object(depth, start);
      if (token == '[') return array(depth, start);
      if (token == '"') {
        String text = string();
        return new Scalar(Scalar.Type.STRING, text, input.substring(start, position));
      }
      while (position < input.length() && ",]} \t\r\n".indexOf(input.charAt(position)) < 0)
        position++;
      String text = input.substring(start, position);
      if (text.equals("null")) return new NullValue();
      if (text.equals("true") || text.equals("false"))
        return new Scalar(Scalar.Type.BOOLEAN, text, text);
      if (NUMBER.matcher(text).matches()) return new Scalar(Scalar.Type.NUMBER, text, text);
      throw EPayException.protocol();
    }

    private ObjectValue object(int depth, int start) {
      position++;
      Map<String, Value> values = new LinkedHashMap<>();
      whitespace();
      if (consume('}')) return new ObjectValue(values, input.substring(start, position));
      while (true) {
        whitespace();
        String key = string();
        if (values.containsKey(key)) throw EPayException.protocol();
        whitespace();
        expect(':');
        values.put(key, value(depth + 1));
        whitespace();
        if (consume('}')) return new ObjectValue(values, input.substring(start, position));
        expect(',');
      }
    }

    private ArrayValue array(int depth, int start) {
      position++;
      List<Value> values = new ArrayList<>();
      whitespace();
      if (consume(']')) return new ArrayValue(values, input.substring(start, position));
      while (true) {
        values.add(value(depth + 1));
        whitespace();
        if (consume(']')) return new ArrayValue(values, input.substring(start, position));
        expect(',');
      }
    }

    private String string() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (position < input.length()) {
        char c = input.charAt(position++);
        if (c == '"') {
          validateSurrogates(result);
          return result.toString();
        }
        if (c < 0x20) throw EPayException.protocol();
        if (c != '\\') {
          result.append(c);
          continue;
        }
        char escaped = current();
        position++;
        switch (escaped) {
          case '"', '\\', '/' -> result.append(escaped);
          case 'b' -> result.append('\b');
          case 'f' -> result.append('\f');
          case 'n' -> result.append('\n');
          case 'r' -> result.append('\r');
          case 't' -> result.append('\t');
          case 'u' -> result.append(unicode());
          default -> throw EPayException.protocol();
        }
      }
      throw EPayException.protocol();
    }

    private char unicode() {
      if (position + 4 > input.length()) throw EPayException.protocol();
      int code = 0;
      for (int i = 0; i < 4; i++) {
        char c = input.charAt(position++);
        int digit =
            c >= '0' && c <= '9'
                ? c - '0'
                : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
        if (digit < 0) throw EPayException.protocol();
        code = code * 16 + digit;
      }
      return (char) code;
    }

    private static void validateSurrogates(CharSequence value) {
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (Character.isHighSurrogate(c)) {
          if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
            throw EPayException.protocol();
          }
        } else if (Character.isLowSurrogate(c)) throw EPayException.protocol();
      }
    }

    private void whitespace() {
      while (position < input.length() && " \t\r\n".indexOf(input.charAt(position)) >= 0)
        position++;
    }

    private char current() {
      if (position >= input.length()) throw EPayException.protocol();
      return input.charAt(position);
    }

    private boolean consume(char token) {
      if (position < input.length() && input.charAt(position) == token) {
        position++;
        return true;
      }
      return false;
    }

    private void expect(char token) {
      if (!consume(token)) throw EPayException.protocol();
    }
  }
}
