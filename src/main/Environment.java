package f;

import f.ast.IdentifierExpr;

import java.util.HashMap;
import java.util.Map;

class Environment {
    private final Environment parent;
    private final Map<String, Value> values = new HashMap<>();

    Environment(Environment parent) {
        this.parent = parent;
    }

    void declare(String name) {
        values.put(name, null);
    }

    void set(String name, Value value) {
        values.put(name, value);
    }

    Value lookup(IdentifierExpr identifier) {
        String name = identifier.name;
        if (values.containsKey(name)) {
            Value value = values.get(name);
            if (value == null) {
                throw new InterpreterException("Uninitialized variable '" + name + "'", identifier);
            }
            return value;
        }
        if (parent != null) {
            return parent.lookup(identifier);
        }
        throw new InterpreterException("Unknown identifier '" + name + "'", identifier);
    }
}
