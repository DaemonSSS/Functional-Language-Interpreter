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
            Scope.Binding condBinding = checkExpression(loop.condition, scope);
            if (condBinding.type != Type.BOOLEAN && condBinding.type != Type.UNKNOWN) {
                throw new AnalyzerException("Condition must be boolean", loop.condition.line, loop.condition.column);
            }
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
                Scope.Binding testBinding = checkExpression(clause.test, remaining);
                if (testBinding.type != Type.BOOLEAN && testBinding.type != Type.UNKNOWN) {
                    throw new AnalyzerException("Condition must be boolean", clause.test.line, clause.test.column);
                }
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
        
        List<Scope.Binding> argBindings = new ArrayList<>();
        for (int i = 1; i < list.items.size(); i++) {
            argBindings.add(checkExpression(list.items.get(i), scope));
        }
        
        int arity = argBindings.size();
        
        if (builtin != null) {
            Integer expectedArity = Builtins.getArity(builtin);
            if (expectedArity != null && expectedArity != arity) {
                throw new AnalyzerException("Expected " + expectedArity + " arguments for '" + builtin + "', got " + arity, operator.line, operator.column);
            }

            checkBuiltinArguments(builtin, argBindings, list.items);
            
            if ("eval".equals(builtin)) {
                scope.allowUnknownNames();
                scope.forgetValues();
            }
            return new Scope.Binding(Builtins.getReturnType(builtin), null);
        }

        if (target.type == Type.FUNCTION && target.arity != null && target.arity != arity) {
            throw new AnalyzerException("Function expects " + target.arity + " arguments, got " + arity, operator.line, operator.column);
        }
        
        if (target.type != Type.FUNCTION) {
            scope.allowUnknownNames();
            scope.forgetValues();
        }
        
        return Scope.Binding.UNKNOWN;
    }

    private void checkBuiltinArguments(String builtin, List<Scope.Binding> args, List<Expression> items) {
        switch (builtin) {
            case "plus": case "minus": case "times": case "divide":
                for (int i = 0; i < args.size(); i++) {
                    Type t = args.get(i).type;
                    if (t != Type.INTEGER && t != Type.REAL && t != Type.UNKNOWN) {
                        throw new AnalyzerException("Expected numeric argument for '" + builtin + "'", items.get(i + 1).line, items.get(i + 1).column);
                    }
                }
                break;
            case "and": case "or": case "xor": case "not":
                for (int i = 0; i < args.size(); i++) {
                    Type t = args.get(i).type;
                    if (t != Type.BOOLEAN && t != Type.UNKNOWN) {
                        throw new AnalyzerException("Expected boolean argument for '" + builtin + "'", items.get(i + 1).line, items.get(i + 1).column);
                    }
                }
                break;
            case "equal": case "nonequal": case "less": case "lesseq": case "greater": case "greatereq":
                for (int i = 0; i < args.size(); i++) {
                    Type t = args.get(i).type;
                    if (t != Type.INTEGER && t != Type.REAL && t != Type.BOOLEAN && t != Type.UNKNOWN) {
                        throw new AnalyzerException("Expected numeric or boolean argument for '" + builtin + "'", items.get(i + 1).line, items.get(i + 1).column);
                    }
                }
                break;
            case "head": case "tail":
                Type t1 = args.get(0).type;
                if (t1 != Type.LIST && t1 != Type.UNKNOWN) {
                    throw new AnalyzerException("Expected list argument for '" + builtin + "'", items.get(1).line, items.get(1).column);
                }
                break;
            case "cons":
                Type t2 = args.get(1).type;
                if (t2 != Type.LIST && t2 != Type.NULL && t2 != Type.UNKNOWN) {
                    throw new AnalyzerException("Expected list or null as second argument for 'cons'", items.get(2).line, items.get(2).column);
                }
                break;
        }
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