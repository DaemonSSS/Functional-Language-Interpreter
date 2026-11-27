# F Language Interpreter

**Team Daemons — Diliia Faizullina, Ilya Muhin, Rushan Shafeev**

A Lisp-style language implemented in **Java 17**: a hand-written lexer, recursive-descent parser, semantic analyzer, AST optimizer, and tree-walking interpreter. The target platform is the **JVM** on macOS, Linux, or Windows. Execution produces a value directly; this project does not generate bytecode or machine code.

## Build and run

Requirements: JDK 17 or newer and Maven 3.8 or newer. Run commands from the repository root.

```sh
java -version
mvn -version
mvn clean verify
java -cp target/classes f.Main
```

Without arguments, the CLI reads **examples/demo.txt**. Edit that file and rerun the last command; changes to F programs do not require recompiling Java.

Run any UTF-8 text file:

```sh
java -cp target/classes f.Main examples/recursion.txt
java -cp target/classes f.Main --file examples/lists.txt
java -cp target/classes f.Main --file "path with spaces/program.txt"
```

Other modes:

```sh
java -cp target/classes f.Main --source "(plus 20 22)"
java -cp target/classes f.Main --demo
java -cp target/classes f.Main --help
```

`--demo` runs the `.txt` files directly inside `examples/`, sorted by filename, each in a fresh environment. It does not run `examples/errors/`. A leading UTF-8 BOM is accepted. Inline programs use the explicit `--source` option; an ordinary positional argument is a filename.

The CLI prints the source, positioned tokens, original AST, semantic-check result, optimized AST, and final value. Errors go to standard error. Exit codes: **0** for success, **1** for a language error, **2** for an input or usage error. A failed stage stops that program before later stages run.

## Pipeline and main data structures

```mermaid
flowchart LR
    S["UTF-8 source file"] --> L[Lexer]
    L --> T["List of Token"]
    T --> P[Parser]
    P --> A["Program / Expression AST"]
    A --> C["Analyzer + Scope"]
    C --> O[Optimizer]
    O --> I["Interpreter + Environment"]
    I --> V["Value"]
```

| Stage / structure | Responsibility |
|---|---|
| `TokenType`, `Token` | Token category, original lexeme, one-based line and column |
| `Lexer` | Scans characters, skips whitespace and `//` comments, recognizes words and numbers, appends EOF |
| `Parser` | Uses a token cursor and recursive descent to construct the AST; validates special-form shapes |
| `Node`, `Expression`, `Program` | Positioned AST nodes; the program contains an ordered list of expressions |
| AST subclasses | Literals, identifiers, quotation, lists/calls, assignment, functions, blocks, conditions, loops, and transfers |
| `Scope`, `Scope.Binding` | Static parent-linked name tables; each binding stores a partial type, optional arity, and whether the name is definite |
| `Builtins` | The shared registry of all 24 built-in names, arities, and known return types |
| `Analyzer` | Reads the AST and checks names, duplicate bindings, call arity, and definite type contradictions |
| `Optimizer` | Creates a rewritten tree, keeping the input tree unchanged |
| `Environment` | Runtime `Map<String, Value>` plus a parent; lookup walks outward, assignment writes locally |
| `Value` | A runtime type plus data: numbers, Boolean, null, atom, list, or function |
| `Interpreter.FunctionValue` | A closure: name if present, parameters, body, and defining environment |
| `QuotedData` | Preserves quoted syntax and converts between AST data and constructed lists for `eval` |
| Four stage exceptions | Identify lexical, syntax, static, and runtime failures |
| `Main` | Reads files and runs the complete pipeline |

For example, `(plus 20 22)` becomes positioned tokens and then:

```text
Program([ListExpr(plus, 20, 22)])
```

Analysis accepts two numeric arguments. The optimizer leaves numeric calls intact. The interpreter returns an integer `Value` whose printed result is `42`.

## Language syntax

Programs contain zero or more expressions. Calls use prefix notation:

```text
program     ::= expr* EOF
expr        ::= atom | "'" quoted-value | "(" special-form ")"
              | "(" expr expr* ")"
atom        ::= identifier | integer | real | boolean | null
integer     ::= ["+" | "-"] digit+
real        ::= ["+" | "-"] digit+ "." digit+
identifier  ::= letter (letter | digit)*
```

Keywords can also be quoted as atoms, for example `'setq` or `(quote setq)`. Names are case-sensitive. Underscores, strings, exponent notation, and infix operators are not supported. Comments begin with `//` and continue to the end of the line.

An empty source program returns `null`. Literal `()` and `'()` are syntax errors; empty parameter/local-name lists are allowed. Runtime list operations can produce an empty list.

### Special forms

| Form | Syntax | Meaning |
|---|---|---|
| Quote | `(quote expr)` or `'expr` | Preserve an expression as data |
| Assignment | `(setq name expr)` | Evaluate the right side, write the current environment, return the value |
| Named function | `(func name (params) body...)` | Bind and return a closure; defer its body until a call |
| Lambda | `(lambda (params) body...)` | Return an anonymous closure |
| Local block | `(prog (locals) body...)` | Execute in a fresh child environment |
| Conditional | `(cond (test value)... (else value))` | Return the first selected value, or `null` |
| Loop | `(while condition body...)` | Repeat while the condition is Boolean true; return `null` |
| Return | `(return)` or `(return expr)` | Exit the nearest active `prog`, function, or lambda |
| Break | `(break)` | Exit the nearest executing loop |

Bodies must contain at least one expression. Parameter/local-name lists can be empty, but duplicate names in one list are rejected. `cond` accepts zero clauses; each clause contains exactly two expressions. `else` is optional and must be last. This implementation uses clause-based `cond`, rather than positional `(cond test then else)`.

A `return` reaching top level ends the program with its value; top-level `break` ends it with `null`. A `break` can propagate through a function or `prog` to a surrounding executing loop. A `return` is caught by the nearest active block or function, including when issued through `eval`.

## All 24 built-in functions

Built-ins take fixed numbers of arguments. Arguments are evaluated **exactly once, left to right**, before the operation is applied. This also applies to `and` and `or`: they are eager functions.

| Category | Names | Arguments | Result / rule |
|---|---|---:|---|
| Arithmetic | `plus`, `minus`, `times`, `divide` | 2 | Integer or real arithmetic |
| Comparison | `equal`, `nonequal`, `less`, `lesseq`, `greater`, `greatereq` | 2 | Boolean; compare two numbers or two Booleans |
| Logic | `and`, `or`, `xor` | 2 | Boolean operands and result |
| Negation | `not` | 1 | Boolean operand and result |
| List access | `head`, `tail` | 1 | First element / a new list containing the remaining elements |
| List construction | `cons` | 2 | Prepend a value to a list; `null` is accepted as the empty suffix |
| Type checks | `isint`, `isreal`, `isbool`, `isnull`, `isatom`, `islist` | 1 | Boolean indicating the exact runtime type |
| Dynamic evaluation | `eval` | 1 | Execute code/data in the caller's environment; scalar values evaluate to themselves |

A built-in name in direct call-head position always selects the built-in, even if a variable or user function has that name. Outside direct call-head position it is an ordinary name requiring a binding.

### Numbers and comparisons

- Integers use `BigInteger`, without fixed-width integer overflow.
- Real literals use `BigDecimal`.
- Arithmetic on two integers returns an integer. Integer division truncates toward zero.
- A real operand promotes the operation to real arithmetic with `MathContext.DECIMAL128`: 34 significant digits and HALF_EVEN rounding.
- Division by any numeric zero reports a positioned runtime error.
- Comparisons allow mixed integer/real operands. Real scale does not affect numeric equality: `(equal 2 2.0)` is `true`.
- Two Booleans can be compared, with `false < true`. Mixing a Boolean and a number is a runtime error. Comparisons do not accept lists, atoms, null, or functions.

```text
(divide -7 3)       -> -2
(plus 1 2.5)       -> 3.5
(divide 1.0 4)     -> 0.25
(not (and true false)) -> true
```

### Scope, functions, and closures

Lookup searches the current environment and then its parents. `setq` evaluates the right side first, then creates or updates a binding **only in the current environment**; it does not update an outer binding.

`prog` declares all its local names before executing its body. An uninitialized local hides an outer name; reading it is a runtime error. F's `null` is a valid initialized value and is distinct from the internal marker for an uninitialized variable.

Functions and lambdas capture a **reference to their defining environment**, not a snapshot of its values. A call evaluates its arguments in the caller's environment, binds parameters in a fresh child of the defining environment, and executes the body there. The last expression is the result unless `return` exits sooner. Named functions and lambdas assigned with `setq` can be recursive.

```lisp
(setq makeadder (lambda (offset)
  (lambda (x) (plus x offset))))
(setq addten (makeadder 10))
(addten 7)
```

Result: `17`. The inner lambda keeps access to the `offset` binding after `makeadder` returns.

### Lists, quotation, and eval

A list whose first element is a literal is data: `(4 missing (divide 1 0))` returns a list without resolving `missing` or performing division. Quote also prevents evaluation. Identifiers in quoted data are atoms; `isatom` checks this specific type, so `(isatom 'x)` is true and `(isatom 5)` is false.

Quoted code retains its AST and source coordinates. Constructed lists contain immutable Java lists of `Value` objects. `head`, `tail`, and `cons` work with both representations. Neither `tail` nor `cons` modifies its input.

`null` and the empty list are distinct: `(tail '(1))` produces `()`, for which `islist` is true and `isnull` is false. `head` and `tail` require a nonempty list; applying either to an empty list is a runtime error.

```lisp
(setq code (cons 'plus '(20 22)))
(eval code)
```

Result: `42`. `eval` also supports stored quoted code, special forms assembled from lists, and a function value placed at the head of a constructed call.

For example, `(eval (cons 'setq '(x 4))) x` yields `4`. Dynamic code is checked by runtime validation rather than a second static-analysis pass.

## Static analysis

Checks run **before optimization**, including checks in unreachable branches and unused function bodies:

1. **Name resolution.** Definitely missing names are rejected. Function bodies and dynamic code can contain names whose existence is deferred until execution.
2. **Duplicate bindings.** Duplicate parameters and duplicate `prog` locals are rejected. Reassignment and shadowing are valid.
3. **Arity.** Known built-ins, named functions, direct lambdas, and lambda values have their argument counts checked.
4. **Partial types.** Definite contradictions are rejected, such as `(not 1)`, `(plus true 1)`, or a known non-Boolean loop condition. Unknown information is deferred to runtime.

`Scope.copy()` isolates branch/function analysis. `Scope.merge()` combines possible paths, retaining exact type and arity only when the paths agree. Possible bindings remain distinct from definitely available bindings. `eval` causes the analyzer to forget potentially invalid type/signature information and allow dynamically introduced names.

This is a partial analyzer, not a complete type system or a proof that execution will succeed.

## Optimizations

| Rule | Example |
|---|---|
| Boolean constant folding | `(not (and true false))` becomes `true` |
| Constant conditional simplification | `(cond (false 1) (else 8))` becomes `8` |
| Unreachable suffix removal | `(prog () (return 8) (divide 1 0))` keeps only the return |

The optimizer does not perform arithmetic folding, inlining, or unused-variable elimination. It preserves quoted contents, literal-headed data lists, and computed call-head subtrees. Nonconstant logical operands are retained, including their effects and failures.

The source AST stays unchanged. Replacement expressions retain the original expression's diagnostic position separately from the selected node's structural position, so simplifying a `cond` does not move a later runtime error to another source location. Repeated optimization preserves these positions too.

## Demo and diagnostics

| File | What to show | Result |
|---|---|---|
| `examples/demo.txt` | Named function; change 5 to 8 and rerun | `25`, then `64` |
| `examples/closures.txt` | Lambda and captured parameter | `17` |
| `examples/recursion.txt` | Factorial using `minus` and `lesseq` | `120` |
| `examples/control.txt` | Locals, condition, loop, break, return | `6` |
| `examples/lists.txt` | Construct and execute a call | `42` |
| `examples/numbers.txt` | Integer division and mixed arithmetic | `7.00` |
| `examples/optimizer.txt` | All three optimizer rules | `8` |

Deliberate failures are separate:

```sh
java -cp target/classes f.Main examples/errors/lexer.txt
java -cp target/classes f.Main examples/errors/parser.txt
java -cp target/classes f.Main examples/errors/analyzer.txt
java -cp target/classes f.Main examples/errors/runtime.txt
```

Their messages begin with `Lexer error:`, `Parser error:`, `Analyzer error:`, and `Runtime error:`. For a short runtime example:

```sh
java -cp target/classes f.Main --source "(divide 1 0)"
```

```text
Runtime error: Division by zero at 1:11
```

Errors in directly quoted code use the original source position. Newly assembled code uses the position of the argument supplied to `eval` when no original syntax node exists.

## Repository structure

```text
.
├── README.md
├── LICENSE
├── pom.xml
├── examples/
│   ├── demo.txt, closures.txt, recursion.txt
│   ├── control.txt, lists.txt, numbers.txt, optimizer.txt
│   └── errors/                  # one failing example per pipeline stage
└── src/
    ├── main/
    │   ├── Main.java
    │   ├── Lexer.java, Token.java, TokenType.java, LexerException.java
    │   ├── Parser.java, ParserException.java
    │   ├── Analyzer.java, Scope.java, Type.java, Builtins.java, AnalyzerException.java
    │   ├── Optimizer.java
    │   ├── Interpreter.java, Environment.java, Value.java, QuotedData.java
    │   ├── InterpreterException.java
    │   └── ast/                 # Program, Node, Expression, and expression subclasses
    └── test/                    # JUnit tests for every stage and the CLI
```

Build artifacts and automatic test reports are under ignored `target/`. There is no separate project report; the project documentation is this README.

## Verification

```sh
mvn clean verify
mvn -Dtest=InterpreterBuiltinsTest test
mvn -Dtest=MainTest test
```

The current suite has **327 tests in 17 classes**:

| Area | Tests |
|---|---:|
| Lexer | 37 |
| Parser | 74 |
| Analyzer | 58 |
| Optimizer | 20 |
| Interpreter | 131 |
| CLI | 7 |
| **Total** | **327** |

The final audit extended the original 233-test suite with execution coverage for every registered built-in, real arithmetic, closures, recursion, list/code conversion, CLI file handling, and optimizer diagnostic positions. Tests compare execution of original and optimized trees, including runtime-error messages and coordinates.

A separate deterministic audit also compared 10,000 generated programs on original and optimized trees and checked optimizer idempotence and input preservation. This is additional evidence for the tested cases, not a proof for every possible program.

Known boundaries: empty-list literals are rejected by the existing grammar; there are no strings, language-level I/O, REPL, bytecode generation, or tail-call optimization. Deep recursion uses the Java stack. Real arithmetic follows the precision rule above. Dynamic names, uninitialized locals, invalid values, and nontermination remain possible at runtime.

## Team contributions and defense

Historical ownership comes from implementation commits, excluding merge commits:

| Member | Main contributions | Example commits |
|---|---|---|
| Diliia Faizullina | Lexer foundation; parser special forms; scope/name analysis; runtime variables and environments | `4ee5cf1`, `ca9baba`, `3b81e5d`, `7c15dd9` |
| Ilya Muhin | Word tokens; parser expression core; arity/type checks; named function calls and integer built-ins | `5d0e3e2`, `f453f66`, `b0d9245`, `dfa7f18` |
| Rushan Shafeev | Numeric tokens; AST/parser foundation; AST optimization; interpreter control flow and integration | `688b9db`, `0f4af1b`, `6901158`, `9881329` |

The final runtime completion, file-based CLI, audit fixes, and documentation updates are uncommitted follow-up work, rather than contributions attributed retroactively to those commits.

Suggested 10-minute presentation:

| Slide | Topic | Speaker | Time |
|---|---|---|---|
| 1 | Team, language, platform, repository | Diliia | 0:00–0:30 |
| 2 | Complete pipeline | Rushan | 0:30–1:10 |
| 3 | Lexer and positioned tokens | Diliia | 1:10–1:50 |
| 4 | Parser and AST | Ilya | 1:50–2:35 |
| 5 | Names and scope analysis | Diliia | 2:35–3:20 |
| 6 | Arity and partial types | Ilya | 3:20–3:55 |
| 7 | Three AST optimizations | Rushan | 3:55–4:55 |
| 8 | Runtime values and environments | Diliia | 4:55–5:45 |
| 9 | Functions, lambdas, closures | Ilya | 5:45–6:45 |
| 10 | Built-ins and code as data | Ilya | 6:45–7:45 |
| 11 | Conditions, loops, return, break | Rushan | 7:45–8:45 |
| 12 | Verification and boundaries | Rushan | 8:45–9:25 |
| 13 | How to run and modify the demo | Diliia | 9:25–10:00 |

Each speaker has 3 minutes 20 seconds. Rehearse the file edit in advance. After the presentation, keep the repository open at `Lexer`, `Parser`, `Analyzer`, `Optimizer`, `Interpreter`, `Environment`, and `QuotedData` for code questions.

## License

[MIT](LICENSE).
