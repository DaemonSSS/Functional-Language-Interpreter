package f;

import f.ast.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

//выполняет AST и передаёт значения между окружениями
public class Interpreter {
    //запустить программу
    public Value execute(Program program) {
        try {
            //внешнее окружение пустое и не имеет родителя
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
        //результат это последнее значение, для пустой последовательности остаётся null
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
        //готовое значение появляется при eval списка с функцией внутри
        if (expression instanceof QuotedData.ValueExpr runtime) {
            return runtime.value;
        }
        if (expression instanceof LiteralExpr literal) {
            return QuotedData.quote(literal);
        }
        if (expression instanceof IdentifierExpr identifier) {
            return env.lookup(identifier);
        }
        if (expression instanceof QuoteExpr quote) {
            return QuotedData.quote(quote.inner);
        }
        //(setq formula '(plus x 2))
        //(setq x 10)
        //(eval formula)
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
        //функция хранит ссылку на окружение объявления, тело выполнится при вызове
        if (expression instanceof FuncExpr function) {
            Value value = new Value(Type.FUNCTION, new FunctionValue(function.name, function.params, function.body, env));
            env.set(function.name, value);
            return value;
        }
        //лямбда захватывает окружение так же, как именованная функция
        if (expression instanceof LambdaExpr lambda) {
            return new Value(Type.FUNCTION, new FunctionValue(null, lambda.params, lambda.body, env));
        }
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
    //перед каждой итерацией заново проверяем условие цикла
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
            return QuotedData.quote(call);
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
        if (!(target.data() instanceof FunctionValue function)) {
            throw new InterpreterException("Expected a function", operator);
        }

        //аргументы считаем у вызывающего кода, тело запускаем в окружении замыкания
        checkArity(function.name() == null ? "lambda" : function.name(), function.params().size(), call);
        Environment local = new Environment(function.definingEnvironment());
        for (int i = 0; i < function.params().size(); i++) {
            Value argument = evaluate(call.items.get(i + 1), env);
            local.set(function.params().get(i), argument);
        }
        return evaluateBody(function.body(), local);
    }
    //проверяем число аргументов до их вычисления
    private void checkArity(String name, int expected, ListExpr call) {
        int actual = call.items.size() - 1;
        if (actual != expected) {
            throw new InterpreterException("Expected " + expected + " arguments for '" + name + "', got " + actual,
                    call.items.get(0));
        }
    }

    //сначала вычисляем все аргументы слева направо, потом применяем функцию
    private Value evaluateBuiltin(IdentifierExpr operator, ListExpr call, Environment env) {
        List<Value> args = new ArrayList<>();
        for (int i = 1; i < call.items.size(); i++) {
            args.add(evaluate(call.items.get(i), env));
        }
        String name = operator.name;
        Value first = args.get(0);
        Expression firstNode = call.items.get(1);
        return switch (name) {
            case "plus", "minus", "times", "divide" -> numeric(name, args, call);
            case "equal", "nonequal", "less", "lesseq", "greater", "greatereq" ->
                    compare(name, args, call);
            case "and", "or", "xor", "not" -> logical(name, args, call);
            case "isint" -> bool(first.type() == Type.INTEGER);
            case "isreal" -> bool(first.type() == Type.REAL);
            case "isbool" -> bool(first.type() == Type.BOOLEAN);
            case "isnull" -> bool(first.type() == Type.NULL);
            case "isatom" -> bool(first.type() == Type.ATOM);
            case "islist" -> bool(first.type() == Type.LIST);
            case "head", "tail" -> {
                List<Value> items = QuotedData.items(first, firstNode);
                if (items.isEmpty()) {
                    throw new InterpreterException("Cannot take " + name + " of an empty list", firstNode);
                }
                yield name.equals("head") ? items.get(0)
                        : new Value(Type.LIST, List.copyOf(items.subList(1, items.size())));
            }
            case "cons" -> {
                List<Value> items = new ArrayList<>();
                items.add(first);
                Value rest = args.get(1);
                if (rest.type() != Type.NULL) {
                    items.addAll(QuotedData.items(rest, call.items.get(2)));
                }
                yield new Value(Type.LIST, List.copyOf(items));
            }
            case "eval" -> evaluate(QuotedData.code(first, firstNode), env);
            default -> throw new InterpreterException("Unknown built-in '" + name + "'", operator);
        };
    }

    //два целых дают целое, при наличии real переходим к BigDecimal
    private Value numeric(String name, List<Value> args, ListExpr call) {
        Value left = args.get(0);
        Value right = args.get(1);
        requireNumber(left, name, call.items.get(1));
        requireNumber(right, name, call.items.get(2));
        if (name.equals("divide") && decimal(right).signum() == 0) {
            throw new InterpreterException("Division by zero", call.items.get(2));
        }
        if (left.type() == Type.INTEGER && right.type() == Type.INTEGER) {
            BigInteger a = (BigInteger) left.data();
            BigInteger b = (BigInteger) right.data();
            BigInteger result = switch (name) {
                case "plus" -> a.add(b);
                case "minus" -> a.subtract(b);
                case "times" -> a.multiply(b);
                default -> a.divide(b);
            };
            return new Value(Type.INTEGER, result);
        }
        BigDecimal a = decimal(left);
        BigDecimal b = decimal(right);
        //DECIMAL128 задаёт 34 значащие цифры и округление HALF_EVEN
        BigDecimal result = switch (name) {
            case "plus" -> a.add(b, MathContext.DECIMAL128);
            case "minus" -> a.subtract(b, MathContext.DECIMAL128);
            case "times" -> a.multiply(b, MathContext.DECIMAL128);
            default -> a.divide(b, MathContext.DECIMAL128);
        };
        return new Value(Type.REAL, result);
    }

    private void requireNumber(Value value, String name, Expression operand) {
        if (value.type() != Type.INTEGER && value.type() != Type.REAL) {
            throw new InterpreterException("Expected numeric argument for '" + name + "'", operand);
        }
    }

    private BigDecimal decimal(Value value) {
        return value.type() == Type.INTEGER ? new BigDecimal((BigInteger) value.data())
                : (BigDecimal) value.data();
    }

    //сравниваем числа между собой или два boolean, false меньше true
    private Value compare(String name, List<Value> args, ListExpr call) {
        Value left = args.get(0);
        Value right = args.get(1);
        int order;
        if (left.type() == Type.BOOLEAN && right.type() == Type.BOOLEAN) {
            order = Boolean.compare((Boolean) left.data(), (Boolean) right.data());
        } else {
            requireNumber(left, name, call.items.get(1));
            requireNumber(right, name, call.items.get(2));
            order = decimal(left).compareTo(decimal(right));
        }
        return bool(switch (name) {
            case "equal" -> order == 0;
            case "nonequal" -> order != 0;
            case "less" -> order < 0;
            case "lesseq" -> order <= 0;
            case "greater" -> order > 0;
            default -> order >= 0;
        });
    }

    //and и or тоже вычисляют оба аргумента, оптимизатор не должен терять их эффекты
    private Value logical(String name, List<Value> args, ListExpr call) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).type() != Type.BOOLEAN) {
                throw new InterpreterException("Expected boolean argument for '" + name + "'", call.items.get(i + 1));
            }
        }
        boolean left = (Boolean) args.get(0).data();
        if (name.equals("not")) {
            return bool(!left);
        }
        boolean right = (Boolean) args.get(1).data();
        return bool(switch (name) {
            case "and" -> left && right;
            case "or" -> left || right;
            default -> left ^ right;
        });
    }

    private Value bool(boolean value) {
        return new Value(Type.BOOLEAN, value);
    }

    //замыкание хранит параметры, тело и ссылку на окружение объявления
    private record FunctionValue(String name, List<String> params, List<Expression> body,
                                 Environment definingEnvironment) {
        @Override
        public String toString() {
            return name == null ? "<lambda>" : "<function " + name + ">";
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
