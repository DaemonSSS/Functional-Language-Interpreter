package f;

import java.math.BigDecimal;

public record Value(Type type, Object data) {
    //Value.NULL - константа
    public static final Value NULL = new Value(Type.NULL, null);

    @Override
    public String toString() {
        //выдасть десятичное число без экспоненциальное записи
        if (data instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        //остальные значения
        return String.valueOf(data);
    }
}
