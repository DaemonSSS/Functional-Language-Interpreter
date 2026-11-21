package f;

import f.ast.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public class Interpreter {
    public Value execute(Program program) {
        return evaluateSequence(program.exprs, new Environment(null));
    }

    Value evaluateSequence(List<Expression> expressions, Environment env) {
        Value result = Value.NULL;
        for (Expression expression : expressions) {
            result = evaluate(expression, env);
        }
        return result;
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
            return evaluateSequence(prog.body, local);
        }
        throw new InterpreterException("Unsupported expression " + expression.getClass().getSimpleName(), expression);
    }
}
