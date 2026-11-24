package f;

import f.ast.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public class Interpreter {
    //запустить программу
    public Value execute(Program program) {
        try {
            //null значит что у самого внешнего окружения нет родителя -> начинаем с пустой таблицы переменных
            return evaluateSequence(program.exprs, new Environment(null));
            //если до самого верхнего уровня дошел return сигнал
        } catch (ReturnSignal signal) {
            return signal.value;
            //если дошел break сигнал
        } catch (BreakSignal signal) {
            return Value.NULL;
        }
    }
    
    //проходимся по выражениям внутри последовательности и оцениваем их
    Value evaluateSequence(List<Expression> expressions, Environment env) {
        Value result = Value.NULL;
        //результатом становится значение последнего выражения, иначе если список пуст то null 
        //значения переменных берутся из окружения в котором находится выражения 
        for (Expression expression : expressions) {
            result = evaluate(expression, env);
        }
        return result;
    }

    //функция для prog чтобы можно было с помощью return досрочно завершить этот prog
    private Value evaluateBody(List<Expression> expressions, Environment env) {
        try {
            return evaluateSequence(expressions, env);
        } catch (ReturnSignal signal) {
            return signal.value;
        }
    }
    
    //получает узел дерева и выбирает действие по типу узла
    Value evaluate(Expression expression, Environment env) {
        //если литерал
        if (expression instanceof LiteralExpr literal) {
            return switch (literal.type) {
                //слева именно тип токена т.е. фрагмента кода, справа тип значения итогового
                //"5"
                case INTEGER -> new Value(Type.INTEGER, new BigInteger(literal.lexeme));
                //"2.5"
                case REAL -> new Value(Type.REAL, new BigDecimal(literal.lexeme));
                //"false"
                case BOOLEAN -> new Value(Type.BOOLEAN, Boolean.valueOf(literal.lexeme));
                //"null"
                case NULL -> Value.NULL;
                default -> throw new InterpreterException("Unsupported literal '" + literal.lexeme + "'", literal);
            };
        }
        //текущее значение переменной (x) например
        if (expression instanceof IdentifierExpr identifier) {
            //чекаем есть ли оно в окружении
            return env.lookup(identifier);
        }
        if (expression instanceof QuoteExpr quote) {
            if (quote.inner instanceof LiteralExpr literal) {
                return evaluate(literal, env);
            }
            //сохраняем узел с его позицией, имена будут разрешены при вызове eval
            Type type = quote.inner instanceof IdentifierExpr ? Type.ATOM : Type.LIST;
            return new Value(type, quote.inner);
        }
        if (expression instanceof SetqExpr setq) {
            //сначала рекурсивно вычисляем правую часть например(setq x (plus 1 2))
            Value value = evaluate(setq.value, env);
            //в окружении записываем результат 
            env.set(setq.name, value);
            return value;
        }
        if (expression instanceof ProgExpr prog) {
            //в прог создается дочернее локальное окружение
            Environment local = new Environment(env);
            //в локальном окружении объявляются локальные имена
            for (String name : prog.locals) {
                local.declare(name);
            }
            //запуск тела блока чтобы можно было return сделать
            return evaluateBody(prog.body, local);
        }
        //сохраняет определение функции с текущим окружением на момент создания
        //записывает под ее именем и возвращает саму функцию как выражение, вызов функции самой позже
        if (expression instanceof FuncExpr function) {
            Value value = new Value(Type.FUNCTION, new NamedFunction(function, env));
            env.set(function.name, value);
            return value;
        }
        //
        if (expression instanceof CondExpr cond) {
            return evaluateCond(cond, env);
        }
        if (expression instanceof WhileExpr loop) {
            return evaluateWhile(loop, env);
        }
        if (expression instanceof ReturnExpr result) {
            Value value = result.value == null ? Value.NULL : evaluate(result.value, env);
            throw new ReturnSignal(value);
        }
        if (expression instanceof BreakExpr) {
            throw new BreakSignal();
        }
        if (expression instanceof ListExpr list) {
            return evaluateCall(list, env);
        }
        throw new InterpreterException("Unsupported expression " + expression.getClass().getSimpleName(), expression);
    }

    //идет по веткам сверху вниз, вычисляет условия и возвращает значения первой подходящей ветки
    private Value evaluateCond(CondExpr cond, Environment env) {
        for (CondExpr.Clause clause : cond.clauses) {
            if (clause.isElse) {
                return evaluate(clause.value, env);
            }
            if (requireBoolean(evaluate(clause.test, env), clause.test)) {
                return evaluate(clause.value, env);
            }
        }
        return Value.NULL;
    }
    //проверяет условие кондишна в самом начале - пока равно вычисляем выражение
    private Value evaluateWhile(WhileExpr loop, Environment env) {
        while (true) {
            try {
                if (!requireBoolean(evaluate(loop.condition, env), loop.condition)) {
                    break;
                }
                evaluateSequence(loop.body, env);
            } catch (BreakSignal signal) {
                break;
            }
        }
        return Value.NULL;
    }
    //проверяет что cond & while вычисляется как булеан
    private boolean requireBoolean(Value value, Expression expression) {
        if (value.type() != Type.BOOLEAN) {
            throw new InterpreterException("Condition must be boolean", expression);
        }
        return (Boolean) value.data();
    }

    //вызов функции
    private Value evaluateCall(ListExpr call, Environment env) {
        if (call.items.isEmpty() || call.items.get(0) instanceof LiteralExpr) {
            throw new InterpreterException("Data lists are not supported", call);
        }
        //название функции
        Expression operator = call.items.get(0);
        //если встроенная - то проверяет аргументы и вызывает
        if (operator instanceof IdentifierExpr identifier && Builtins.contains(identifier.name)) {
            checkArity(identifier.name, Builtins.getArity(identifier.name), call);
            return evaluateBuiltin(identifier, call, env);
        }
        
        //проверяем есть ли такое название пользовательской в окружении
        Value target = evaluate(operator, env);
        if (!(target.data() instanceof NamedFunction function)) {
            throw new InterpreterException("Expected a function", operator);
        }
        
        //если пользовательская - вычисляет, какая функция стоит на первом месте, проверяет число аргументов и создаёт окружение вызова
        //аргументы вычисляются слева направо в окружении вызывающего кода, а тело работает в новом окружении, родитель которого — окружение объявления функции
        FuncExpr definition = function.definition();
        checkArity(definition.name, definition.params.size(), call);
        Environment local = new Environment(function.definingEnvironment());
        for (int i = 0; i < definition.params.size(); i++) {
            Value argument = evaluate(call.items.get(i + 1), env);
            local.set(definition.params.get(i), argument);
        }
        return evaluateBody(definition.body, local);
    }
   //чекнуть количество аргов
    private void checkArity(String name, int expected, ListExpr call) {
        int actual = call.items.size() - 1;
        if (actual != expected) {
            throw new InterpreterException("Expected " + expected + " arguments for '" + name + "', got " + actual,
                    call.items.get(0));
        }
    }
    
    //по имени выбирает встроенную функцию
    private Value evaluateBuiltin(IdentifierExpr operator, ListExpr call, Environment env) {
        return switch (operator.name) {
            case "plus", "times", "less", "divide" -> evaluateIntegerBuiltin(operator.name, call, env);
            case "eval" -> {
                Value argument = evaluate(call.items.get(1), env);
                yield argument.data() instanceof Expression code ? evaluate(code, env) : argument;
            }
            default -> throw new InterpreterException("Unsupported built-in '" + operator.name + "'", operator);
        };
    }

    //считаем выражение встроенной функции
    private Value evaluateIntegerBuiltin(String name, ListExpr call, Environment env) {
        Expression leftOperand = call.items.get(1);
        Expression rightOperand = call.items.get(2);
        Value leftValue = evaluate(leftOperand, env);
        Value rightValue = evaluate(rightOperand, env);
        BigInteger left = requireInteger(leftValue, name, leftOperand);
        BigInteger right = requireInteger(rightValue, name, rightOperand);

        return switch (name) {
            case "plus" -> new Value(Type.INTEGER, left.add(right));
            case "times" -> new Value(Type.INTEGER, left.multiply(right));
            case "less" -> new Value(Type.BOOLEAN, left.compareTo(right) < 0);
            case "divide" -> new Value(Type.INTEGER, divide(left, right, rightOperand));
            default -> throw new IllegalStateException("Unexpected integer built-in '" + name + "'");
        };
    }

    private BigInteger requireInteger(Value value, String name, Expression operand) {
        if (value.type() != Type.INTEGER) {
            throw new InterpreterException("Expected integer argument for '" + name + "'", operand);
        }
        return (BigInteger) value.data();
    }

    private BigInteger divide(BigInteger left, BigInteger right, Expression divisor) {
        if (right.signum() == 0) {
            throw new InterpreterException("Division by zero", divisor);
        }
        return left.divide(right);
    }

    private record NamedFunction(FuncExpr definition, Environment definingEnvironment) {
        @Override
        public String toString() {
            return "<function " + definition.name + ">";
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        private final Value value;

        private ReturnSignal(Value value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private static final class BreakSignal extends RuntimeException {
        private BreakSignal() {
            super(null, null, false, false);
        }
    }
}
