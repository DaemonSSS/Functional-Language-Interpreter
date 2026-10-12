# Functional Language Interpreter — лексер и парсер языка F

Ручной лексер и синтаксический анализатор для функционального языка **F**, написанные на Java. Лексер разбивает исходный текст на токены, а парсер методом рекурсивного спуска строит **AST** — абстрактное синтаксическое дерево программы.

```text
Исходный текст → Lexer → токены → Parser → AST
```

Текущий этап проекта — лексический и синтаксический анализ. Парсер проверяет структуру выражений и специальных форм. Семантический анализ и выполнение программы относятся к следующему этапу.

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
├── LICENSE
└── src
    ├── main
    │   ├── TokenType.java        # типы токенов
    │   ├── Token.java            # тип, текст, строка и столбец токена
    │   ├── Lexer.java            # лексический анализатор
    │   ├── LexerException.java   # лексическая ошибка
    │   ├── Parser.java           # парсер рекурсивного спуска
    │   ├── ParserException.java  # синтаксическая ошибка с координатами
    │   ├── Main.java             # исходный текст → токены → AST
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
        └── ParserSpecialFormTest.java
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
```

Один метод:

```sh
mvn "-Dtest=LexerFoundationTest#emptyInputProducesOnlyEof" test
```

Тесты проверяют токены и их координаты, разбор выражений и вложенных списков, AST, специальные формы, правило `else` в `cond` и сообщения об ошибках. Отчёты Surefire находятся в `target/surefire-reports`.

## Запуск демо

Класс `f.Main` печатает исходный текст, поток токенов и AST. При ошибке выводится сообщение `Lexer error:` или `Parser error:` с координатами.

Сборка и запуск встроенного примера:

```sh
mvn -q -DskipTests package
java -cp target/classes f.Main
```

Все демонстрационные примеры:

```sh
java -cp target/classes f.Main --demo
```

Демо включает шесть групп: вложенные вызовы, определение и вызов функции, `setq` с цитируемым списком, `cond` с `else`, `lambda`/`while`/`prog` и синтаксические ошибки. Ошибки в последней группе добавлены специально.

Свой исходный текст можно передать аргументом:

```sh
java -cp target/classes f.Main "(setq x -12)"
```

Результирующее AST:

```text
Program([SetqExpr(x, -12)])
```

Примеры для проверки ошибок:

```sh
java -cp target/classes f.Main "(setq x)"
java -cp target/classes f.Main "()"
java -cp target/classes f.Main "(plus 1"
```

Сообщения соответственно:

```text
Parser error: Expected value after setq name, found ')' at 1:8
Parser error: Empty list at 1:1
Parser error: Unbalanced '(', reached end of input at 1:1
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
