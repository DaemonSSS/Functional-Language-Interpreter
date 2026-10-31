package f;

import f.ast.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Analyzer {
    public void check(Program program) {
        checkSequence(program.exprs, new Scope(null));
    }

    private void checkSequence(List<Expression> expressions, Scope scope) {
        for (Expression expression : expressions) {
            checkExpression(expression, scope);
        }
    }

    private Scope.Binding checkExpression(Expression expression, Scope scope) {
        if (expression instanceof LiteralExpr literal) {
            return literalBinding(literal);
        }
        if (expression instanceof IdentifierExpr identifier) {
            Scope.Binding binding = scope.find(identifier.name);
            if (binding == null && !scope.allowsUnknownNames()) {
                throw new AnalyzerException("Unknown identifier '" + identifier.name + "'",
                        identifier.line, identifier.column);
            }
            return binding == null ? Scope.Binding.UNKNOWN : binding;
        }
        if (expression instanceof QuoteExpr quote) {
            if (quote.inner instanceof LiteralExpr literal) {
                return literalBinding(literal);
            }
            return new Scope.Binding(quote.inner instanceof IdentifierExpr ? Type.ATOM : Type.LIST, null);
        }
        if (expression instanceof SetqExpr setq) {
            Scope.Binding value = checkExpression(setq.value, scope);
            scope.define(setq.name, new Scope.Binding(value.type, value.arity));
            return value;
        }
        if (expression instanceof FuncExpr function) {
            Scope.Binding binding = new Scope.Binding(Type.FUNCTION, function.params.size());
            scope.define(function.name, binding);
            checkFunction(function.params, function.body, function, scope, function.name);
            return Scope.Binding.UNKNOWN;
        }
        if (expression instanceof LambdaExpr lambda) {
            checkFunction(lambda.params, lambda.body, lambda, scope, null);
            return new Scope.Binding(Type.FUNCTION, lambda.params.size());
        }
        if (expression instanceof ProgExpr prog) {
            Scope local = new Scope(scope);
            defineNames(prog.locals, "local", prog, local);
            checkSequence(prog.body, local);
            return Scope.Binding.UNKNOWN;
        }
        if (expression instanceof CondExpr cond) {
            checkConditional(cond, scope);
            return Scope.Binding.UNKNOWN;
        }
        if (expression instanceof WhileExpr loop) {
            checkExpression(loop.condition, scope);
            Scope before = scope.copy();
            Scope iteration = scope.copy();
            checkSequence(loop.body, iteration);
            if (!Boolean.FALSE.equals(booleanLiteral(loop.condition))) {
                scope.merge(List.of(before, iteration));
            }
            return new Scope.Binding(Type.NULL, null);
        }
        if (expression instanceof ReturnExpr result) {
            if (result.value != null) {
                checkExpression(result.value, scope);
            }
            return Scope.Binding.UNKNOWN;
        }
        if (expression instanceof BreakExpr) {
            return Scope.Binding.UNKNOWN;
        }
        if (expression instanceof ListExpr list) {
            return checkCall(list, scope);
        }
        throw new IllegalArgumentException("Unsupported expression: " + expression.getClass().getSimpleName());
    }

    private void checkFunction(List<String> params, List<Expression> body,
                               Node owner, Scope scope, String name) {
        Scope captured = scope.copy();
        captured.forgetValues();
        if (name != null) {
            captured.define(name, new Scope.Binding(Type.FUNCTION, params.size()));
        }
        Scope local = new Scope(captured);
        local.allowUnknownNames();
        defineNames(params, "parameter", owner, local);
        checkSequence(body, local);
    }

    private void defineNames(List<String> names, String kind, Node owner, Scope scope) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                throw new AnalyzerException("Duplicate " + kind + " '" + name + "'", owner.line, owner.column);
            }
            scope.define(name, Scope.Binding.UNKNOWN);
        }
    }

    private void checkConditional(CondExpr cond, Scope scope) {
        Scope remaining = scope.copy();
        List<Scope> alternatives = new ArrayList<>();
        boolean reachable = true;
        for (CondExpr.Clause clause : cond.clauses) {
            if (!clause.isElse) {
                checkExpression(clause.test, remaining);
            }
            Scope branch = remaining.copy();
            checkExpression(clause.value, branch);
            Boolean constant = clause.isElse ? Boolean.TRUE : booleanLiteral(clause.test);
            if (reachable && !Boolean.FALSE.equals(constant)) {
                alternatives.add(branch);
            }
            if (Boolean.TRUE.equals(constant)) {
                reachable = false;
            }
        }
        if (reachable) {
            alternatives.add(remaining);
        }
        scope.merge(alternatives);
    }

    private Scope.Binding checkCall(ListExpr list, Scope scope) {
        if (list.items.isEmpty() || list.items.get(0) instanceof LiteralExpr) {
            return new Scope.Binding(Type.LIST, null);
        }
        Expression operator = list.items.get(0);
        String builtin = operator instanceof IdentifierExpr identifier && Builtins.contains(identifier.name)
                ? identifier.name : null;
        Scope.Binding target = Scope.Binding.UNKNOWN;
        if (builtin == null) {
            target = checkExpression(operator, scope);
        }
        for (int i = 1; i < list.items.size(); i++) {
            checkExpression(list.items.get(i), scope);
        }
        if ((builtin == null && target.type != Type.FUNCTION) || "eval".equals(builtin)) {
            scope.allowUnknownNames();
            scope.forgetValues();
        }
        return Scope.Binding.UNKNOWN;
    }

    private Scope.Binding literalBinding(LiteralExpr literal) {
        Type type = switch (literal.type) {
            case INTEGER -> Type.INTEGER;
            case REAL -> Type.REAL;
            case BOOLEAN -> Type.BOOLEAN;
            case NULL -> Type.NULL;
            default -> Type.UNKNOWN;
        };
        return new Scope.Binding(type, null);
    }

    private Boolean booleanLiteral(Expression expression) {
        if (expression instanceof LiteralExpr literal && literal.type == TokenType.BOOLEAN) {
            return Boolean.valueOf(literal.lexeme);
        }
        return null;
    }
}
