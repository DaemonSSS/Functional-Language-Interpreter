package f;

import f.ast.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public class Interpreter {
    public Value execute(Program program) {
        try {
            return evaluateSequence(program.exprs, new Environment(null));
        } catch (ReturnSignal signal) {
            return signal.value;
        } catch (BreakSignal signal) {
            return Value.NULL;
        }
    }

    Value evaluateSequence(List<Expression> expressions, Environment env) {
        Value result = Value.NULL;
        for (Expression expression : expressions) {
            result = evaluate(expression, env);
        }
        return result;
    }

    private Value evaluateBody(List<Expression> expressions, Environment env) {
        try {
            return evaluateSequence(expressions, env);
        } catch (ReturnSignal signal) {
            return signal.value;
        }
    }

    Value evaluate(Expression expression, Environment env) {
        if (expression instanceof LiteralExpr literal) {
            return switch (literal.type) {
                case INTEGER -> new Value(Type.INTEGER, new BigInteger(literal.lexeme));
                case REAL -> new Value(Type.REAL, new BigDecimal(literal.lexeme));
                case BOOLEAN -> new Value(Type.BOOLEAN, Boolean.valueOf(literal.lexeme));
                case NULL -> Value.NULL;
                default -> throw new InterpreterException("Unsupported literal '" + literal.lexeme + "'", literal);
            };
        }
        if (expression instanceof IdentifierExpr identifier) {
            return env.lookup(identifier);
        }
        if (expression instanceof SetqExpr setq) {
            Value value = evaluate(setq.value, env);
            env.set(setq.name, value);
            return value;
        }
        if (expression instanceof ProgExpr prog) {
            Environment local = new Environment(env);
            for (String name : prog.locals) {
                local.declare(name);
            }
            return evaluateBody(prog.body, local);
        }
        if (expression instanceof FuncExpr function) {
            Value value = new Value(Type.FUNCTION, new NamedFunction(function, env));
            env.set(function.name, value);
            return value;
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

    private boolean requireBoolean(Value value, Expression expression) {
        if (value.type() != Type.BOOLEAN) {
            throw new InterpreterException("Condition must be boolean", expression);
        }
        return (Boolean) value.data();
    }

    private Value evaluateCall(ListExpr call, Environment env) {
        if (call.items.isEmpty() || call.items.get(0) instanceof LiteralExpr) {
            throw new InterpreterException("Data lists are not supported", call);
        }

        Expression operator = call.items.get(0);
        if (operator instanceof IdentifierExpr identifier && Builtins.contains(identifier.name)) {
            checkArity(identifier.name, Builtins.getArity(identifier.name), call);
            return evaluateBuiltin(identifier, call, env);
        }

        Value target = evaluate(operator, env);
        if (!(target.data() instanceof NamedFunction function)) {
            throw new InterpreterException("Expected a function", operator);
        }

        FuncExpr definition = function.definition();
        checkArity(definition.name, definition.params.size(), call);
        Environment local = new Environment(function.definingEnvironment());
        for (int i = 0; i < definition.params.size(); i++) {
            Value argument = evaluate(call.items.get(i + 1), env);
            local.set(definition.params.get(i), argument);
        }
        return evaluateBody(definition.body, local);
    }

    private void checkArity(String name, int expected, ListExpr call) {
        int actual = call.items.size() - 1;
        if (actual != expected) {
            throw new InterpreterException("Expected " + expected + " arguments for '" + name + "', got " + actual,
                    call.items.get(0));
        }
    }

    private Value evaluateBuiltin(IdentifierExpr operator, ListExpr call, Environment env) {
        return switch (operator.name) {
            case "plus", "times", "less", "divide" -> evaluateIntegerBuiltin(operator.name, call, env);
            default -> throw new InterpreterException("Unsupported built-in '" + operator.name + "'", operator);
        };
    }

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
