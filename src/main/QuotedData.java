package f;

import f.ast.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

//переводит сохранённый код в данные и собирает код из списков для eval
final class QuotedData {
    private QuotedData() {
    }

    //внутри quote имена не ищем и выражения не выполняем
    static Value quote(Expression expression) {
        if (expression instanceof ValueExpr runtime) {
            return runtime.value;
        }
        if (expression instanceof LiteralExpr literal) {
            return switch (literal.type) {
                case INTEGER -> new Value(Type.INTEGER, new BigInteger(literal.lexeme));
                case REAL -> new Value(Type.REAL, new BigDecimal(literal.lexeme));
                case BOOLEAN -> new Value(Type.BOOLEAN, Boolean.valueOf(literal.lexeme));
                case NULL -> Value.NULL;
                default -> throw new InterpreterException("Unsupported literal", literal);
            };
        }
        return new Value(expression instanceof IdentifierExpr ? Type.ATOM : Type.LIST, expression);
    }

    //список может прийти из quote или быть собран через cons и tail
    static List<Value> items(Value value, Node owner) {
        if (value.type() != Type.LIST) {
            throw new InterpreterException("Expected list argument", owner);
        }
        if (value.data() instanceof List<?> list) {
            return list.stream().map(item -> (Value) item).toList();
        }
        return syntaxItems((Expression) value.data()).stream().map(QuotedData::quote).toList();
    }

    //сохранённое дерево оставляем с исходными координатами
    static Expression code(Value value, Node owner) {
        if (value.data() instanceof Expression expression) {
            return expression;
        }
        if (value.type() == Type.LIST) {
            return listCode(items(value, owner), owner);
        }
        TokenType literal = switch (value.type()) {
            case INTEGER -> TokenType.INTEGER;
            case REAL -> TokenType.REAL;
            case BOOLEAN -> TokenType.BOOLEAN;
            case NULL -> TokenType.NULL;
            default -> null;
        };
        if (literal != null) {
            return new LiteralExpr(literal, value.toString(), owner.diagnosticLine(), owner.diagnosticColumn());
        }
        return new ValueExpr(value, owner);
    }

    //специальные формы в списке для eval получают те же узлы, что и после парсера
    private static Expression listCode(List<Value> values, Node owner) {
        List<Expression> expressions = values.stream().map(value -> code(value, owner)).toList();
        if (expressions.isEmpty() || !(expressions.get(0) instanceof IdentifierExpr head)) {
            return new ListExpr(expressions, owner.diagnosticLine(), owner.diagnosticColumn());
        }
        List<Value> args = values.subList(1, values.size());
        int line = owner.diagnosticLine();
        int column = owner.diagnosticColumn();
        switch (head.name) {
            case "quote":
                arity(args, 1, owner);
                return new QuoteExpr(code(args.get(0), owner), line, column);
            case "setq":
                arity(args, 2, owner);
                return new SetqExpr(name(args.get(0), owner), code(args.get(1), owner), line, column);
            case "func":
                atLeast(args, 3, owner);
                return new FuncExpr(name(args.get(0), owner), names(args.get(1), owner),
                        expressions.subList(3, expressions.size()), line, column);
            case "lambda":
                atLeast(args, 2, owner);
                return new LambdaExpr(names(args.get(0), owner),
                        expressions.subList(2, expressions.size()), line, column);
            case "prog":
                atLeast(args, 2, owner);
                return new ProgExpr(names(args.get(0), owner),
                        expressions.subList(2, expressions.size()), line, column);
            case "while":
                atLeast(args, 2, owner);
                return new WhileExpr(expressions.get(1),
                        expressions.subList(2, expressions.size()), line, column);
            case "return":
                if (args.size() > 1) {
                    throw new InterpreterException("return accepts at most one value", owner);
                }
                return new ReturnExpr(args.isEmpty() ? null : expressions.get(1), line, column);
            case "break":
                arity(args, 0, owner);
                return new BreakExpr(line, column);
            case "cond":
                List<CondExpr.Clause> clauses = new ArrayList<>();
                boolean seenElse = false;
                for (Value arg : args) {
                    if (seenElse) {
                        throw new InterpreterException("else clause must be last in cond", owner);
                    }
                    List<Value> pair = items(arg, owner);
                    arity(pair, 2, owner);
                    Expression test = code(pair.get(0), owner);
                    seenElse = test instanceof IdentifierExpr id && id.name.equals("else");
                    clauses.add(new CondExpr.Clause(test, code(pair.get(1), owner), seenElse));
                }
                return new CondExpr(clauses, line, column);
            default:
                return new ListExpr(expressions, line, column);
        }
    }

    private static void arity(List<?> args, int expected, Node owner) {
        if (args.size() != expected) {
            throw new InterpreterException("Expected " + expected + " items, got " + args.size(), owner);
        }
    }

    private static void atLeast(List<?> args, int expected, Node owner) {
        if (args.size() < expected) {
            throw new InterpreterException("Expected at least " + expected + " items", owner);
        }
    }

    private static String name(Value value, Node owner) {
        if (value.type() == Type.ATOM && value.data() instanceof IdentifierExpr id) {
            return id.name;
        }
        throw new InterpreterException("Expected identifier in name list or binding", owner);
    }

    private static List<String> names(Value value, Node owner) {
        List<String> names = items(value, owner).stream().map(item -> name(item, owner)).toList();
        if (new HashSet<>(names).size() != names.size()) {
            throw new InterpreterException("Duplicate name in binding list", owner);
        }
        return names;
    }

    //восстанавливаем элементы специальных форм, чтобы head и tail видели обычный список
    private static List<Expression> syntaxItems(Expression expression) {
        if (expression instanceof ListExpr list) {
            return list.items;
        }
        List<Expression> items = new ArrayList<>();
        if (expression instanceof QuoteExpr quote) {
            items.add(identifier("quote", expression));
            items.add(quote.inner);
        } else if (expression instanceof SetqExpr setq) {
            items.add(identifier("setq", expression));
            items.add(identifier(setq.name, expression));
            items.add(setq.value);
        } else if (expression instanceof FuncExpr function) {
            items.add(identifier("func", expression));
            items.add(identifier(function.name, expression));
            items.add(nameList(function.params, expression));
            items.addAll(function.body);
        } else if (expression instanceof LambdaExpr lambda) {
            items.add(identifier("lambda", expression));
            items.add(nameList(lambda.params, expression));
            items.addAll(lambda.body);
        } else if (expression instanceof ProgExpr prog) {
            items.add(identifier("prog", expression));
            items.add(nameList(prog.locals, expression));
            items.addAll(prog.body);
        } else if (expression instanceof CondExpr cond) {
            items.add(identifier("cond", expression));
            for (CondExpr.Clause clause : cond.clauses) {
                items.add(new ListExpr(List.of(clause.test, clause.value), expression.line, expression.column));
            }
        } else if (expression instanceof WhileExpr loop) {
            items.add(identifier("while", expression));
            items.add(loop.condition);
            items.addAll(loop.body);
        } else if (expression instanceof ReturnExpr result) {
            items.add(identifier("return", expression));
            if (result.value != null) {
                items.add(result.value);
            }
        } else if (expression instanceof BreakExpr) {
            items.add(identifier("break", expression));
        } else if (expression instanceof ValueExpr runtime) {
            return List.of(runtime);
        } else {
            throw new InterpreterException("Expected list data", expression);
        }
        return items;
    }

    private static IdentifierExpr identifier(String name, Node owner) {
        return new IdentifierExpr(name, owner.line, owner.column);
    }

    private static ListExpr nameList(List<String> names, Node owner) {
        List<Expression> items = names.stream().<Expression>map(name -> identifier(name, owner)).toList();
        return new ListExpr(items, owner.line, owner.column);
    }

    //готовое значение функции можно положить в список и вызвать через eval
    static final class ValueExpr extends Expression {
        final Value value;

        ValueExpr(Value value, Node owner) {
            super(owner.diagnosticLine(), owner.diagnosticColumn());
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
