package f;

import java.math.BigDecimal;

public record Value(Type type, Object data) {
    public static final Value NULL = new Value(Type.NULL, null);

    @Override
    public String toString() {
        if (data instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(data);
    }
}
