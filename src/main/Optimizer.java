package f;

import f.ast.*;

import java.util.ArrayList;
import java.util.List;

public class Optimizer {

    public Program optimize(Program program) {
        return new Program(optimizeSequence(program.exprs), program.line, program.column);
    }

    private List<Expression> optimizeSequence(List<Expression> expressions) {
        List<Expression> optimized = new ArrayList<>();
        for (Expression expression : expressions) {
            Expression result = optimizeExpression(expression);
            optimized.add(result);
            if (result instanceof ReturnExpr || result instanceof BreakExpr) {
                break;
            }
        }
        return optimized;
    }

    private Expression optimizeExpression(Expression expression) {
        if (expression instanceof QuoteExpr
                || expression instanceof LiteralExpr
                || expression instanceof IdentifierExpr
                || expression instanceof BreakExpr) {
            return expression;
        }
        if (expression instanceof ReturnExpr result) {
            if (result.value == null) {
                return result;
            }
            return new ReturnExpr(optimizeExpression(result.value), result.line, result.column);
        }
        if (expression instanceof SetqExpr setq) {
            return new SetqExpr(setq.name, optimizeExpression(setq.value), setq.line, setq.column);
        }
        if (expression instanceof FuncExpr function) {
            return new FuncExpr(function.name, function.params,
                    optimizeSequence(function.body), function.line, function.column);
        }
        if (expression instanceof LambdaExpr lambda) {
            return new LambdaExpr(lambda.params,
                    optimizeSequence(lambda.body), lambda.line, lambda.column);
        }
        if (expression instanceof ProgExpr prog) {
            return new ProgExpr(prog.locals,
                    optimizeSequence(prog.body), prog.line, prog.column);
        }
        if (expression instanceof WhileExpr loop) {
            return new WhileExpr(optimizeExpression(loop.condition),
                    optimizeSequence(loop.body), loop.line, loop.column);
        }
        if (expression instanceof CondExpr cond) {
            return optimizeConditional(cond);
        }
        if (expression instanceof ListExpr list) {
            return optimizeList(list);
        }
        throw new IllegalArgumentException("Unsupported expression: " + expression.getClass().getSimpleName());
    }

    private Expression optimizeList(ListExpr list) {
        if (list.items.isEmpty() || list.items.get(0) instanceof LiteralExpr) {
            return list;
        }
        List<Expression> items = new ArrayList<>();
        items.add(list.items.get(0));
        for (int i = 1; i < list.items.size(); i++) {
            items.add(optimizeExpression(list.items.get(i)));
        }
        Expression folded = foldBooleanCall(items, list);
        return folded != null ? folded : new ListExpr(items, list.line, list.column);
    }

    private Expression foldBooleanCall(List<Expression> items, ListExpr list) {
        if (!(items.get(0) instanceof IdentifierExpr identifier)) {
            return null;
        }
        String name = identifier.name;
        Integer arity = Builtins.getArity(name);
        if (arity == null || arity != items.size() - 1) {
            return null;
        }
        if (!name.equals("and") && !name.equals("or") && !name.equals("xor") && !name.equals("not")) {
            return null;
        }
        List<Boolean> operands = new ArrayList<>();
        for (int i = 1; i < items.size(); i++) {
            Boolean value = booleanLiteral(items.get(i));
            if (value == null) {
                return null;
            }
            operands.add(value);
        }
        boolean result = switch (name) {
            case "and" -> operands.get(0) && operands.get(1);
            case "or" -> operands.get(0) || operands.get(1);
            case "xor" -> operands.get(0) ^ operands.get(1);
            default -> !operands.get(0);
        };
        return new LiteralExpr(TokenType.BOOLEAN, result ? "true" : "false", list.line, list.column);
    }

    private Expression optimizeConditional(CondExpr cond) {
        List<CondExpr.Clause> clauses = new ArrayList<>();
        for (CondExpr.Clause clause : cond.clauses) {
            Expression test = clause.isElse ? clause.test : optimizeExpression(clause.test);
            if (!clause.isElse && Boolean.FALSE.equals(booleanLiteral(test))) {
                continue;
            }
            clauses.add(new CondExpr.Clause(test, optimizeExpression(clause.value), clause.isElse));
        }
        if (clauses.isEmpty()) {
            return new LiteralExpr(TokenType.NULL, "null", cond.line, cond.column);
        }
        CondExpr.Clause first = clauses.get(0);
        if (first.isElse || Boolean.TRUE.equals(booleanLiteral(first.test))) {
            return first.value;
        }
        for (int i = 1; i < clauses.size(); i++) {
            CondExpr.Clause clause = clauses.get(i);
            if (clause.isElse) {
                break;
            }
            if (Boolean.TRUE.equals(booleanLiteral(clause.test))) {
                List<CondExpr.Clause> kept = new ArrayList<>(clauses.subList(0, i + 1));
                kept.set(i, new CondExpr.Clause(clause.test, clause.value, true));
                return new CondExpr(kept, cond.line, cond.column);
            }
        }
        return new CondExpr(clauses, cond.line, cond.column);
    }

    private Boolean booleanLiteral(Expression expression) {
        if (expression instanceof LiteralExpr literal && literal.type == TokenType.BOOLEAN) {
            return Boolean.valueOf(literal.lexeme);
        }
        return null;
    }
}
