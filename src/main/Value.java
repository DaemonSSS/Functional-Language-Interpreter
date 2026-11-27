package f;

import java.math.BigDecimal;
import java.util.stream.Collectors;

public record Value(Type type, Object data) {
    //Value.NULL - константа
    public static final Value NULL = new Value(Type.NULL, null);

    @Override
    public String toString() {
        if (type == Type.LIST) {
            return QuotedData.items(this, null).stream()
                    .map(Value::toString).collect(Collectors.joining(" ", "(", ")"));
        }
        //выдасть десятичное число без экспоненциальное записи
        if (data instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        //остальные значения
        return String.valueOf(data);
    }
}
