# Project F — Semantic Analysis and AST Optimizations

**Team Daemons — Diliia Faizullina, Ilya Muhin, Rushan Shafeev**
**Java 17, Maven, JUnit 5 — lab report, October 2026**

## 1. Pipeline and scope

The stage extends the finished lexer and parser with a semantic analyzer and safe AST
optimizations. The parser, AST classes, and their tests are unchanged.

```text
source -> Lexer -> tokens -> Parser -> original AST
                                          |
                                   Analyzer.check()     read-only checks
                                          |
                                   Optimizer.optimize() AST rewriting
                                          |
                                     optimized AST
```

`Analyzer.check(Program)` validates the program and never modifies the AST.
`Optimizer.optimize(Program)` returns a rewritten tree built with the existing AST
constructors and leaves the input tree unchanged. Checks always run before optimization,
so an optimization can never hide a semantic error in a branch it would later remove.
Each invocation starts with fresh analysis state; `QuoteExpr` is a traversal boundary in
both passes and `eval` is never executed at compile time.

Implemented checks: **C1** name resolution and scopes, **C2** duplicate bindings,
**C3** known-call arity, **C4** partial type checking. Implemented optimizations:
**O1** constant expression folding, **O2** constant `cond` simplification, and the optional
**O3** removal of unreachable code after a direct `return`/`break`. The minimum requirement
(two read-only checks, two AST-changing optimizations) is exceeded.

## 2. Analyzer foundation: contexts and bindings (Diliia)

The analyzer walks every AST node with a small recursive traversal over a parent-linked
`Scope`. Only `func`, `lambda`, and `prog` introduce local contexts; `cond` and `while`
share their enclosing context. Parameters and `prog` locals are available throughout
their bodies, and shadowing an outer name is valid. `setq` checks its value first and only
then defines or updates the name in the current context, so a self-reference on the right
of a first `setq` is still an error. Named functions are registered before their bodies are
checked, so self-recursion resolves; function bodies are inspected in isolated snapshots,
and an unresolved free name in a deferred body is not reported because the function may
run after later declarations. Branch snapshots are merged conservatively: a name defined
on only some paths becomes "possible" with unknown value information.

Quoted data and literal-headed data lists are not evaluated: `'(plus missing true)` and
`(4 minus times divide)` are accepted as data. Built-in names are recognized only in
call-head position; a bare `plus` needs a binding. C2 rejects repeated parameter names and
repeated `prog` locals inside one name list.

Example — valid nested shadowing (the inner `x` does not replace the outer binding):

```lisp
(setq x 5)
(prog (x) (setq x 10) x)
x
```

Example — C1 and C2 errors (positions from the AST nodes):

```text
(plus missing 1)    ->  Analyzer error: Unknown identifier 'missing' at 1:7
(func bad (x x) x)  ->  Analyzer error: Duplicate parameter 'x' at 1:2
(prog (a a) 1)      ->  Analyzer error: Duplicate local 'a' at 1:2
```

## 3. Call validation and partial types (Ilya)

`Builtins` provides the supported names, their arities, and result categories in the
shared `Type` enum (`INTEGER, REAL, BOOLEAN, NULL, LIST, ATOM, FUNCTION, UNKNOWN`).
C3 checks argument counts for built-ins, named functions, direct lambdas, and lambda
values assigned with `setq`; the operator is excluded from the count, so `(plus 1 2)` has
two arguments. Higher-order forms such as `((lambda (x) x) 5)` work; if a computed
operator's signature cannot be established, the call check is deferred instead of
inventing a fixed arity.

C4 reports only definite contradictions: arithmetic built-ins require numeric operands,
logic built-ins require Booleans, `cond` tests and `while` conditions must be Boolean when
their types are known, `head`/`tail` require a list, and `cons` requires a list or `null`
as its second argument. F has no declared types, so dynamic reassignment
(`(setq x 1)` then `(setq x true)`) is legal. Untyped parameters, unknown call results,
`eval` results, and uncertain branch merges are `UNKNOWN`; an incomplete inference is
never an error, and stale facts are dropped conservatively after `eval`, unknown calls,
loops, and branch merges.

Example — C3 and C4 errors:

```text
(plus 1)         ->  Analyzer error: Expected 2 arguments for 'plus', got 1 at 1:2
(not 1)          ->  Analyzer error: Expected boolean argument for 'not' at 1:6
(while 3 break)  ->  Analyzer error: Condition must be boolean
```

Accepted dynamic subset: computed calls with unknown signatures, quoted invalid-looking
calls, and `eval` arguments are treated as data or `UNKNOWN` and never rejected
speculatively.

## 4. AST optimizations and validation (Rushan)

**O1 — constant folding.** Bottom-up traversal of evaluated expressions replaces direct
calls to the pure Boolean built-ins `and`/`or`/`xor`/`not` with a Boolean literal when all
operands are Boolean literals; the folded literal keeps the call's position.

```text
(not (and true false))  ->  true
```

Numeric calls, comparisons, list operations, user calls, and `eval` stay unchanged: with
no evaluator there is no fixed arithmetic/overflow/rounding contract to match, and an
optimizer must not invent one. A non-literal operand is never discarded, because F
evaluates all ordinary call arguments eagerly — `(and false (eval '(setq x true)))` keeps
its `eval` argument.

**O2 — constant conditional simplification.** Children are folded first, so O1 can expose
a constant test. Clauses with literal `false` tests are removed; if the first remaining
clause is literal `true` (or `else`), the whole `CondExpr` is replaced by that clause's
value; if unknown tests precede a literal-`true` clause, those tests are preserved and the
true clause becomes the final fallback while later clauses are removed; if no clause
remains, the conditional becomes a `null` literal.

```text
(cond ((not false) 8) (else 0))                            ->  8        (O1 + O2)
(cond (false 1))                                           ->  null
(setq flag false) (setq x 0)
(cond (flag (setq x 1)) (true (setq x 2)) (else (setq x 3)))
  ->  (cond (flag (setq x 1)) (else (setq x 2)))            (x is 2 when flag is false, 1 when true)
```

**O3 — unreachable code.** In a sequential list, expressions after a direct `return` or
`break` are removed while the transfer itself is kept. Nested counterexample preserved:
`(prog () (prog () (return 1)) 2)` still ends with `2`, because the inner `return` exits
only the inner `prog`.

**Rewrite boundaries and behavior preservation.** Quoted contents, literal-headed data
lists, and computed call-head subtrees are never rewritten. This is essential for

```lisp
(setq plus (lambda (a b) (minus a b)))
((cond (true plus)) 5 3)
```

where the indirect call must keep invoking the bound lambda and return `2`; rewriting its
operator to a bare `plus` would select the built-in and return `8`. Each rewrite has an
explicit precondition (pure built-in, literal operands, literal tests, direct transfer) and
preserves values and types, effect order, runtime errors, and transfer targets; when a
precondition cannot be proved the expression is left unchanged.

**Validation.** `OptimizerTest` (20 tests) covers each optimization separately, the
O1-enables-O2 case, quote/data preservation, computed heads, source positions, no
compile-time `eval`, no dropped side effects, input-tree identity on failure-free runs,
rechecking the output, and idempotence (a second optimization is structurally stable).
`AnalyzerIntegrationTest` (11 tests) runs the full pipeline on the shared manual test set
and verifies that check errors are reported before an optimizer could erase their branch,
e.g. `(cond (false (plus missing 1)))` still fails analysis. Behavior preservation is
argued rule-by-rule against the contract above; execution equivalence is *not* claimed
experimentally, because the repository has no reference evaluator (AST snapshots alone do
not prove it).

**Result:** `mvn clean verify` — 188 tests, 0 failures (`f.Lexer*`, `f.Parser*`,
`f.AnalyzerScopeTest`, `f.AnalyzerTypeTest`, `f.OptimizerTest`,
`f.AnalyzerIntegrationTest`). Demo: `java -cp target/classes f.Main --demo` prints source,
tokens, original AST, semantic-check result, and optimized AST for every example.

## 5. Final review

| Contribution | Owner |
|---|---|
| Analyzer traversal, contexts, C1-C2, `Scope`, `Type`, `Builtins`, `AnalyzerException` | Diliia Faizullina |
| C3-C4: known-call arity, partial types, built-in signatures | Ilya Muhin |
| O1-O3 optimizer, `Main` integration, `OptimizerTest`, `AnalyzerIntegrationTest`, README, report assembly | Rushan Shafeev |

Supported F subset: special forms `quote`, `setq`, `func`, `lambda`, `prog`, clause-based
`cond`, `while`, `return`, `break`; 24 built-in names; integers, reals, Booleans, `null`,
lists, functions as values, and dynamic `eval`. Out of scope: interpreter, code generation,
numeric constant folding, function inlining, unused-variable elimination.

**Syntax difference.** The handout describes `cond` as `(cond test then [else])`. The
implemented parser and AST use clauses: `(cond (test value) ... (else value))`, with an
optional value in `return` and multiple body expressions. The stage keeps the existing
parser and tests unchanged and applies semantics to the clause form (first selected value,
`null` when nothing matches); the report records the difference rather than claiming exact
handout conformance.

Static diagnostics are honest about their limits: `eval` and computed calls may introduce
runtime information, unresolvable deferred names are accepted, and `prog` locals have no
assumed initialization value.

### Lab demonstration plan (5-7 minutes)

1. **Diliia** — the AST pipeline, a scoped binding, and a duplicate/unknown-name error.
2. **Ilya** — a known function/lambda call, a wrong argument count, a definite type error.
3. **Rushan** — original vs. optimized AST for O1 and O2, then a quoted expression that
   remains unchanged.
4. **All** — why checks run first and why `UNKNOWN` and `eval` limit static guarantees.

One-sentence summaries: *“I implemented the analyzer traversal and local contexts, so we
can resolve names and reject duplicate bindings without changing the AST.”* (Diliia);
*“I added argument-count and partial type checks; F has no declared types, so uncertain
cases remain unknown.”* (Ilya); *“I implemented constant folding and conditional
simplification, rebuilding AST nodes while preserving quoted data and evaluation
behavior.”* (Rushan).
