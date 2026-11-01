# Functional Language Interpreter — лексер, парсер и оптимизатор языка F

Ручной лексер, синтаксический и семантический анализаторы для функционального языка **F**, написанные на Java. Лексер разбивает исходный текст на токены, парсер методом рекурсивного спуска строит **AST** — абстрактное синтаксическое дерево программы, анализатор проверяет имена, области видимости, вызовы и типы, оптимизатор выполняет безопасные преобразования дерева.

```text
Исходный текст → Lexer → токены → Parser → AST → Analyzer.check() → Optimizer.optimize() → оптимизированное AST
```

Лексический, синтаксический и семантический анализы и оптимизации AST завершены. Исполнение программ — отдельный этап.

## Возможности

- Скобки `(` `)` и quote `'`.
- Однострочные комментарии `// ...`.
- Ключевые слова: `quote`, `setq`, `func`, `lambda`, `prog`, `cond`, `while`, `return`, `break`.
- Логические литералы `true`, `false` и специальное значение `null`.
- Идентификаторы: первый символ — Unicode-буква, далее буквы или десятичные цифры. Примеры: `plus`, `times`, `divide`, `head`, `cons`, `greatereq`, `myFunc`.
- Целые числа (`INTEGER`): `123`, `-45`, `+7`.
- Вещественные числа (`REAL`): `3.14`, `-0.5`, `+12.25`.
- Вложенные списки, вызовы функций и несколько выражений верхнего уровня.
- Формы `'expr` и `(quote expr)`, создающие одинаковую структуру `QuoteExpr`.
- Проверка количества и формы аргументов специальных конструкций.
- Лексические и синтаксические ошибки с позицией `line:column`.

Обычные имена функций, например `plus` и `times`, являются идентификаторами. Их вызовы сохраняются как `ListExpr`; количество аргументов таких вызовов на этом этапе не проверяется. Пустой список `()` считается синтаксической ошибкой. Пустые списки параметров и локальных переменных разрешены.

## Требования

- **JDK 17** или новее.
- **Apache Maven 3.8** или новее.

Проверка окружения:

```sh
java -version
javac -version
mvn -version
```

Для сборки требуется JDK, включающий `javac`. Если команда `mvn` не найдена, установите Maven и добавьте его каталог `bin` в `PATH`. Команды ниже выполняются из корня проекта, где находится `pom.xml`.

## Структура проекта

```text
.
├── pom.xml                       # Java 17, JUnit 5
├── README.md
├── PLAN.md                       # план семантического этапа
├── REPORT.md                     # отчёт по этапу (2-3 страницы)
├── LICENSE
└── src
    ├── main
    │   ├── TokenType.java        # типы токенов
    │   ├── Token.java            # тип, текст, строка и столбец токена
    │   ├── Lexer.java            # лексический анализатор
    │   ├── LexerException.java   # лексическая ошибка
    │   ├── Parser.java           # парсер рекурсивного спуска
    │   ├── ParserException.java  # синтаксическая ошибка с координатами
    │   ├── Analyzer.java         # семантический анализатор (проверки C1-C4)
    │   ├── AnalyzerException.java# семантическая ошибка с координатами
    │   ├── Scope.java            # контексты и привязки имён
    │   ├── Type.java             # категории типов для частичной проверки
    │   ├── Builtins.java         # реестр встроенных функций и сигнатур
    │   ├── Optimizer.java        # оптимизации AST (O1-O3)
    │   ├── Main.java             # исходный текст → токены → AST → проверка → оптимизация
    │   └── ast
    │       ├── Node.java
    │       ├── Expression.java
    │       ├── Program.java
    │       ├── LiteralExpr.java
    │       ├── IdentifierExpr.java
    │       ├── QuoteExpr.java
    │       ├── ListExpr.java
    │       ├── SetqExpr.java
    │       ├── FuncExpr.java
    │       ├── LambdaExpr.java
    │       ├── ProgExpr.java
    │       ├── CondExpr.java
    │       ├── WhileExpr.java
    │       ├── ReturnExpr.java
    │       └── BreakExpr.java
    └── test
        ├── LexerFoundationTest.java
        ├── LexerWordReadingTest.java
        ├── LexerNumberTest.java
        ├── ParserFoundationTest.java
        ├── ParserExpressionTest.java
        ├── ParserSpecialFormTest.java
        ├── AnalyzerScopeTest.java
        ├── AnalyzerTypeTest.java
        ├── OptimizerTest.java
        └── AnalyzerIntegrationTest.java
```

## Сборка

Полная сборка с тестами:

```sh
mvn clean verify
```

Успешная сборка завершается сообщением `BUILD SUCCESS`.

Сборка без запуска тестов:

```sh
mvn clean package -DskipTests
```

Очистка результатов сборки:

```sh
mvn clean
```

## Запуск тестов

Все тесты:

```sh
mvn test
```

Тесты с предварительной очисткой результатов сборки:

```sh
mvn clean test
```

Один класс тестов:

```sh
mvn -Dtest=LexerFoundationTest test
mvn -Dtest=LexerWordReadingTest test
mvn -Dtest=LexerNumberTest test
mvn -Dtest=ParserFoundationTest test
mvn -Dtest=ParserExpressionTest test
mvn -Dtest=ParserSpecialFormTest test
mvn -Dtest=AnalyzerScopeTest test
mvn -Dtest=AnalyzerTypeTest test
mvn -Dtest=OptimizerTest,AnalyzerIntegrationTest test
```

Один метод:

```sh
mvn "-Dtest=LexerFoundationTest#emptyInputProducesOnlyEof" test
```

Тесты проверяют токены и их координаты, разбор выражений и вложенных списков, AST, специальные формы, правило `else` в `cond` и сообщения об ошибках, семантические проверки, оптимизации AST и их согласованность. Отчёты Surefire находятся в `target/surefire-reports`.

## Запуск демо

Класс `f.Main` печатает исходный текст, поток токенов, исходное AST, результат семантической проверки и оптимизированное AST. При ошибке выводится сообщение `Lexer error:`, `Parser error:` или `Analyzer error:` с координатами. Программа с семантической ошибкой не оптимизируется.

Сборка и запуск встроенного примера:

```sh
mvn -q -DskipTests package
java -cp target/classes f.Main
```

Все демонстрационные примеры:

```sh
java -cp target/classes f.Main --demo
```

Демо включает группы: вложенные вызовы, определение и вызов функции, `setq` с цитируемым списком, `cond` с `else`, `lambda`/`while`/`prog`, оптимизации O1-O3 с примерами «до и после», случаи сохранения данных и управления, синтаксические и семантические ошибки. Ошибки в последних группах добавлены специально.

Свой исходный текст можно передать аргументом:

```sh
java -cp target/classes f.Main "(setq x -12)"
```

Результирующее AST:

```text
Program([SetqExpr(x, -12)])
```

Примеры оптимизаций:

```sh
java -cp target/classes f.Main "(not (and true false))"
java -cp target/classes f.Main "(cond ((not false) 8) (else 0))"
java -cp target/classes f.Main "(cond (false 1))"
```

Сообщения соответственно:

```text
Optimized AST:
  Program([true])
Optimized AST:
  Program([8])
Optimized AST:
  Program([null])
```

Примеры для проверки ошибок:

```sh
java -cp target/classes f.Main "(setq x)"
java -cp target/classes f.Main "()"
java -cp target/classes f.Main "(plus 1"
java -cp target/classes f.Main "(plus missing 1)"
```

Сообщения соответственно:

```text
Parser error: Expected value after setq name, found ')' at 1:8
Parser error: Empty list at 1:1
Parser error: Unbalanced '(', reached end of input at 1:1
Analyzer error: Unknown identifier 'missing' at 1:7
```

## Типы токенов

| TokenType | Пример | Описание |
|-----------|--------|----------|
| `LPAREN` | `(` | открывающая скобка |
| `RPAREN` | `)` | закрывающая скобка |
| `QUOTE` | `'` | сокращённая запись quote |
| `KEYWORD` | `lambda`, `setq` | специальная форма |
| `IDENTIFIER` | `head`, `myFunc` | имя функции или переменной |
| `INTEGER` | `123`, `-45`, `+7` | целое число |
| `REAL` | `3.14`, `-0.5` | вещественное число |
| `BOOLEAN` | `true`, `false` | логический литерал |
| `NULL` | `null` | пустое значение |
| `EOF` | конец ввода | завершение потока токенов |

## Синтаксис

Основная грамматика:

```text
program     -> { expr } EOF
expr        -> atom | quoted | list
atom        -> IDENTIFIER | INTEGER | REAL | BOOLEAN | NULL
quoted      -> QUOTE expr
list        -> LPAREN elements RPAREN
elements    -> KEYWORD special-form | expr { expr }
```

Специальная форма распознаётся, когда первым элементом списка является ключевое слово. В остальных случаях создаётся `ListExpr`.

В таблице `expr` означает выражение, `name` — идентификатор, `names` — ноль или больше идентификаторов, `body` — одно или больше выражений.

| Форма | Запись | Проверка |
|-------|--------|----------|
| `quote` | `(quote expr)` | ровно одно выражение |
| `setq` | `(setq name expr)` | имя и ровно одно значение |
| `func` | `(func name (names) body)` | имя функции, список параметров, непустое тело |
| `lambda` | `(lambda (names) body)` | список параметров, непустое тело |
| `prog` | `(prog (names) body)` | список локальных переменных, непустое тело |
| `cond` | `(cond (test value) ... (else value))` | по два выражения в ветке; необязательный `else` только последним |
| `while` | `(while expr body)` | условие и непустое тело |
| `return` | `(return)` или `(return expr)` | ноль или одно выражение |
| `break` | `(break)` | без аргументов |

`cond` может содержать ноль веток. `else` является обычным идентификатором; в позиции условия ветки `cond` он отмечает ветку по умолчанию. После неё другие ветки запрещены.

## AST

`Program` хранит список выражений верхнего уровня. Все узлы наследуются от `Node` и сохраняют начальную строку и столбец. Узлы специальных форм используют позицию ключевого слова, обычные списки — позицию открывающей скобки.

Числа, логические значения и `null` представлены узлами `LiteralExpr`, имена — `IdentifierExpr`, обычные списки — `ListExpr`. Для каждой специальной формы существует отдельный класс. Метод `toString()` выводит краткое представление дерева для демонстрации.

## Ошибки

Разбор останавливается при первой ошибке. Лексер выбрасывает `LexerException`, парсер — `ParserException`. Строки и столбцы нумеруются с единицы.

Лексические ошибки:

| Ввод | Причина | Сообщение |
|------|---------|-----------|
| `12.` | нет цифр после точки | `Malformed number '12.' at 1:1` |
| `.5` | нет цифр до точки | `Unexpected character '.' at 1:1` |
| `@` | недопустимый символ | `Unexpected character '@' at 1:1` |
| `-x` | знак не сопровождается цифрой | `Unexpected character '-' at 1:1` |

Синтаксические ошибки:

| Ввод | Причина | Сообщение |
|------|---------|-----------|
| `()` | пустой список | `Empty list at 1:1` |
| `(plus 1` | незакрытый список | `Unbalanced '(', reached end of input at 1:1` |
| `(setq x)` | отсутствует значение | `Expected value after setq name, found ')' at 1:8` |
| `(func 5 (x) x)` | имя функции не является идентификатором | `Expected function name after func, found '5' at 1:7` |
| `(break 1)` | лишний аргумент | `Expected ')' after break (no arguments), found '1' at 1:8` |

Для незакрытого обычного списка указывается позиция открывающей скобки. Для неверного или отсутствующего аргумента — позиция текущего токена либо конца ввода.

## Грамматика лексем

```text
number     ::= ['+' | '-'] digit+ [ '.' digit+ ]
identifier ::= letter (letter | digit)*
comment    ::= '//' [^\n]*
```

## Semantic analysis and AST optimizations

The semantic stage runs after parsing and consists of two passes:

```text
source -> Lexer -> tokens -> Parser -> original AST
                                          |
                                   Analyzer.check()
                                   (read-only checks)
                                          |
                                   Optimizer.optimize()
                                          |
                                     optimized AST
```

```java
Program program = new Parser(new Lexer(source).tokenize()).parse();
new Analyzer().check(program);
Program optimized = new Optimizer().optimize(program);
```

### Checks (read-only, the AST is never modified)

- **C1 — name resolution.** Reads of definitely unknown identifiers raise `AnalyzerException` with `line:column`. `setq` checks its value before defining or updating its name in the current context. `func`, `lambda`, and `prog` create local contexts; parameters and `prog` locals are available throughout their bodies, shadowing an outer name is valid. Function bodies are checked in isolated snapshots: unresolved free names are deferred because a function may run after later declarations. `cond` and `while` share their enclosing context; branches use separate analysis snapshots and a name defined on only some paths is marked as possible.
- **C2 — duplicate bindings.** Duplicate parameters in one `func`/`lambda` and duplicate names in one `prog` local list are errors. Reassignment is allowed.
- **C3 — call arity.** Argument counts are checked for known built-ins, named functions, direct lambdas, and lambda values assigned to variables. The operator is not counted as an argument. A computed operator whose signature cannot be established is deferred.
- **C4 — partial types.** Definite mismatches are rejected: numeric built-ins require numeric operands, logical built-ins require Booleans, `cond` tests and `while` conditions must be Boolean when their types are known, `head`/`tail` require a list, `cons` requires a list or `null` as its second argument. Untyped parameters, unknown call results, and `eval` results are `UNKNOWN`; an incomplete inference is never an error.

Quoted contents and literal-headed data lists such as `(4 minus times divide)` are data: they are not resolved or checked as calls. Built-in names are recognized only in call-head position; a bare `plus` otherwise needs a binding. Top-level `return` and `break` are allowed and terminate the program. All branches and body expressions are checked, including unreachable ones.

### Optimizations (return a rewritten tree; the input tree is left unchanged)

- **O1 — constant expression folding.** Direct calls to the Boolean built-ins `and`/`or`/`xor`/`not` with only Boolean literal operands are replaced by a Boolean literal at the call's position: `(not (and true false))` becomes `true`. Numeric calls, list operations, user calls, and `eval` are left intact; a non-literal operand is never discarded because F evaluates all arguments eagerly.
- **O2 — constant conditional simplification.** Children are folded first. Clauses with literal `false` tests are removed; if the first remaining clause is literal `true` or `else`, the whole `cond` is replaced by that clause's value. If unknown tests precede a literal-`true` clause, the unknown tests are kept in order and the `true` clause becomes the final fallback while later clauses are removed. If no clause remains, the conditional becomes `null`.
- **O3 — unreachable-code removal.** In `Program.exprs` and `func`/`lambda`/`prog`/`while` body lists, expressions after a direct `return` or `break` in the same sequence are removed. A nested function's `return` or a `return` inside a nested `prog` does not terminate the surrounding sequence.

Rewrite boundaries: `QuoteExpr` contents, literal-headed data lists, and computed call-head subtrees are never rewritten, so an indirect call can never turn into a syntactically recognized built-in call or a data list. Every rewrite has a checked precondition and preserves observable behavior (values and types, effects and their order, runtime errors, and control-transfer targets); when a precondition cannot be proved, the expression is left unchanged.

### Limitations

- This is partial static analysis: accepting a deferred or dynamic reference does not guarantee that it exists at runtime. No initialization value is assumed for `prog` locals.
- `eval` is never executed at compile time; its result type is `UNKNOWN`.
- Numeric constant folding is not performed: the optimizer must not invent arithmetic, overflow, or rounding semantics. Numeric calls remain intact until execution semantics are fixed.
- The implemented `cond` uses clause syntax `(cond (test value) ... (else value))` (see the syntax section), not the handout's `(cond test then [else])` form.
- No interpreter, function inlining, or unused-variable elimination.

### Commands

```sh
mvn clean verify
mvn -Dtest=AnalyzerScopeTest test
mvn -Dtest=AnalyzerTypeTest test
mvn -Dtest=OptimizerTest,AnalyzerIntegrationTest test
java -cp target/classes f.Main --demo
java -cp target/classes f.Main "(not (and true false))"
```

The shared report with examples and contributions is in `REPORT.md`.
