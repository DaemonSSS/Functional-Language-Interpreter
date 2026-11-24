package f;

import f.ast.IdentifierExpr;

import java.util.HashMap;
import java.util.Map;

class Environment {
    private final Environment parent;
    //таблица значений переменных
    private final Map<String, Value> values = new HashMap<>();
     
    //создает окружение с собственной пустой таблицей и запоминает родителя
    Environment(Environment parent) {
        this.parent = parent;
    }
    
    //добавляет имя в текущую таблицу и кладёт под ним Java null, используется для prog в локальных переменных
    void declare(String name) {
        values.put(name, null);
    }
    //записывает имя и значение в таблицу
    void set(String name, Value value) {
        values.put(name, value);
    }

   //проверяет, есть ли имя в текущей таблице
   //если имя есть и значение присвоено — возвращает его
   //если имя есть, но внутри Java null — выдаёт Uninitialized variable
   //если имени здесь нет — ищет у родителя
   //если закончилась вся цепочка — выдаёт Unknown identifier
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
