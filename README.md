# Functional Language Interpreter — лексер языка F

Ручной (handwritten) лексер для функционального языка **F**. Проект разбивает исходный текст на поток токенов: скобки, quote, ключевые слова, идентификаторы, логические и числовые литералы.

## Возможности

- Скобки `(` `)`, quote `'`.
- Однострочные комментарии `// ...`.
- Ключевые слова (special forms): `quote`, `setq`, `func`, `lambda`, `prog`, `cond`, `while`, `return`, `break`.
- Логические литералы: `true`, `false`; специальное значение `null`.
- Идентификаторы по грамматике F: первый символ — Unicode-буква, далее буквы или десятичные цифры. Примеры: `plus`, `times`, `divide`, `head`, `cons`, `greatereq`, `myFunc`.
- Числовые литералы:
  - `INTEGER`: `123`, `-45`, `+7`
  - `REAL`: `3.14`, `-0.5`, `+12.25`
- Лексические ошибки с позицией `line:column`.

## Требования

- **JDK 17** или новее
- **Apache Maven 3.8** или новее

Проверка окружения:

```powershell
java -version
javac -version
mvn -version
```

Если `mvn` не найден, установите Maven и добавьте его каталог `bin` в `PATH`.

## Структура проекта

```
.
├── pom.xml                       # конфигурация Maven (Java 17, JUnit 5)
├── README.md
├── RUNNING.md                    # расширенная инструкция по окружению
└── src
    ├── main                      # исходный код
    │   ├── TokenType.java        # перечисление типов токенов
    │   ├── Token.java            # модель токена (type, lexeme, line, column)
    │   ├── Lexer.java            # сканер
    │   ├── LexerException.java   # ошибка лексирования
    │   └── Main.java             # демонстрационный запуск
    └── test                      # тесты JUnit 5
        ├── LexerFoundationTest.java
        ├── LexerWordReadingTest.java
        └── LexerNumberTest.java
```

## Сборка

Полная сборка с тестами:

```powershell
mvn clean verify
```

Сборка без тестов:

```powershell
mvn clean package -DskipTests
```

Очистка:

```powershell
mvn clean
```

## Запуск тестов

Все тесты:

```powershell
mvn test
```

Перезапуск без кэша:

```powershell
mvn clean test
```

Один класс:

```powershell
mvn -Dtest=LexerFoundationTest test
mvn -Dtest=LexerWordReadingTest test
mvn -Dtest=LexerNumberTest test
```

Один метод:

```powershell
mvn -Dtest=LexerFoundationTest#emptyInputProducesOnlyEof test
```

Отчёты Surefire находятся в `target/surefire-reports`.

## Запуск демо

Демонстрационный класс `f.Main` лексирует встроенный пример и печатает поток токенов.

```powershell
mvn -q -DskipTests package
java -cp target/classes f.Main
```

Можно передать свой исходник аргументами:

```powershell
java -cp target/classes f.Main "(setq x 3.14)"
```

## Типы токенов

| TokenType    | Пример              | Описание                        |
|--------------|---------------------|---------------------------------|
| `LPAREN`     | `(`                 | открывающая скобка              |
| `RPAREN`     | `)`                 | закрывающая скобка              |
| `QUOTE`      | `'`                 | quote                           |
| `KEYWORD`    | `lambda`, `setq`    | special form                    |
| `IDENTIFIER` | `head`, `myFunc`    | имя функции или переменной      |
| `INTEGER`    | `123`, `-45`, `+7`  | целое число                     |
| `REAL`       | `3.14`, `-0.5`      | вещественное число              |
| `BOOLEAN`    | `true`, `false`     | логический литерал              |
| `NULL`       | `null`              | пустое значение                 |
| `EOF`        |                     | конец ввода                     |

## Лексические ошибки

Ошибки выбрасываются как `LexerException` и содержат позицию `line:column`.

| Ввод   | Причина                                    | Сообщение                              |
|--------|--------------------------------------------|----------------------------------------|
| `12.`  | нет цифр после точки                       | `Malformed number '12.' at 1:1`        |
| `.5`   | нет цифр до точки                          | `Unexpected character '.' at 1:1`      |
| `@`    | недопустимый символ                        | `Unexpected character '@' at 1:1`      |
| `-x`   | знак не сопровождается цифрой              | `Unexpected character '-' at 1:1`      |

## Грамматика лексем (фрагмент)

```
number  ::= ['+' | '-'] digit+ [ '.' digit+ ]
identifier ::= letter (letter | digit)*
comment ::= '//' [^\n]*
```
