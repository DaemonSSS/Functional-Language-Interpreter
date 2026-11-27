package f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

//хранит сведения об именах для анализа, без значений времени выполнения
public class Scope {
    private final Scope parent;
    private final Map<String, Binding> bindings = new HashMap<>();
    private boolean unknownNames;

    public Scope(Scope parent) {
        this.parent = parent;
    }

    //определение меняет только текущую область
    public void define(String name, Binding binding) {
        bindings.put(name, binding);
    }

    //если здесь имени нет, ищем у родителя
    public Binding find(String name) {
        Binding binding = bindings.get(name);
        return binding != null || parent == null ? binding : parent.find(name);
    }

    public boolean allowsUnknownNames() {
        return unknownNames || parent != null && parent.allowsUnknownNames();
    }

    //после динамического eval новые имена заранее неизвестны
    public void allowUnknownNames() {
        unknownNames = true;
    }

    //после неизвестных эффектов типам и сигнатурам больше нельзя доверять
    public void forgetValues() {
        for (String name : bindings.keySet()) {
            Binding binding = bindings.get(name);
            bindings.put(name, new Binding(Type.UNKNOWN, null, binding.definite));
        }
        if (parent != null) {
            parent.forgetValues();
        }
    }

    //копируем всю цепочку областей для независимой проверки ветки
    public Scope copy() {
        Scope copy = new Scope(parent == null ? null : parent.copy());
        copy.bindings.putAll(bindings);
        copy.unknownNames = unknownNames;
        return copy;
    }

    //сохраняем точный тип и сигнатуру только при согласии всех путей
    public void merge(List<Scope> alternatives) {
        if (alternatives.isEmpty()) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (Scope alternative : alternatives) {
            names.addAll(alternative.bindings.keySet());
        }
        bindings.clear();
        unknownNames = false;
        for (Scope alternative : alternatives) {
            unknownNames |= alternative.unknownNames;
        }
        for (String name : names) {
            Binding first = alternatives.get(0).bindings.get(name);
            Type type = first == null ? Type.UNKNOWN : first.type;
            Integer arity = first == null ? null : first.arity;
            boolean definite = first != null && first.definite;
            for (Scope alternative : alternatives) {
                Binding binding = alternative.bindings.get(name);
                if (binding == null || !binding.definite) {
                    definite = false;
                    type = Type.UNKNOWN;
                    arity = null;
                } else {
                    if (type != binding.type) {
                        type = Type.UNKNOWN;
                    }
                    if (!Objects.equals(arity, binding.arity)) {
                        arity = null;
                    }
                }
            }
            define(name, new Binding(type, arity, definite));
        }
        if (parent != null) {
            List<Scope> parents = new ArrayList<>();
            for (Scope alternative : alternatives) {
                parents.add(alternative.parent);
            }
            parent.merge(parents);
        }
    }

    public static final class Binding {
        public static final Binding UNKNOWN = new Binding(Type.UNKNOWN, null);

        public final Type type;
        public final Integer arity;
        public final boolean definite;

        public Binding(Type type, Integer arity) {
            this(type, arity, true);
        }

        public Binding(Type type, Integer arity, boolean definite) {
            this.type = definite ? type : Type.UNKNOWN;
            this.arity = this.type == Type.FUNCTION ? arity : null;
            this.definite = definite;
        }
    }
}
