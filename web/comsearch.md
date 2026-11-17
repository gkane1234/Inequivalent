This is a recreation of a result from [ComSearch: Equation Searching with Combinatorial Strategy for Solving Math Word Problems with Weak Supervision](https://arxiv.org/pdf/2210.07017) (Liu, Guan, Shen, Cheng, and Kurohashi). The counters run the counting recurrence Appendix A uses. The Python listing code does not run ComSearch’s search. That search is `ComSearchGenerator` in the Java sources.

## How this matches ComSearch

Appendix A of that paper builds the exponential generating function for inequivalent expressions on n labeled variables using only addition, subtraction, multiplication, and division. No unary minus. Equation (20) is

1, 6, 68, 1170, 27142, 793002, 27914126, …

Those are the sizes this repository checks. `web/lib/counter.js` stores that prefix through n=7. The Java and Python `Counter` programs compute the same numbers from the [OEIS A140606](https://oeis.org/A140606) recurrence. Generation is accepted when the set for n has that many expressions: the browser check prints them for n≤6, and `Counter` reports 27,914,126 for n=7, the seventh term. `ComSearchSolverTest` checks that `ComSearchGenerator` yields `Counter.run(n)` expressions through n=4.

The expressions are the same kind of object. Both sides use n distinct values, the four operations, and parentheses, and treat two expressions as the same when algebra says they are (commutativity, associativity, and rewriting such as a−(b−c) versus a−b+c).

The counting code runs that algorithm. Appendix A says the derivation relies on [OEIS A140606](https://oeis.org/A140606). `Counter.java` and `Python_Version/counter.py` are a port of the program linked there, [`a140606_2.pl`](https://oeis.org/A140606/a140606_2.pl.txt). It does the same decomposition as the appendix:

- Two families, `sa` and `sc`, for the two choices of outermost operator (a sum of products, or a product of sums).
- `p2m1[g] = 2^g - 1`, the paper’s factor on a set of g subexpressions.
- `tryIndex` lists the partitions of n into g parts, which is the set-of-subexpressions construction.
- The returned value `2*sc - sc1 + 2*sa - sa1` is the paper’s S = T − U: count the versions that allow a leading minus, then subtract the illegal all-minus forms.

`ExpressionPowerSet` in `Python_Version/operation_counting.py` does yield expressions one at a time, and for n=2 those are a+b, a−b, b−a, a\*b, a/b, b/a. That is not the ComSearch search. ComSearch’s `enum` walks a list of subtrees depth-first and `smart4` refuses to build a duplicate, using commutativity, associativity, and a reversed minus, without evaluating the expression. `generate_rpn_expressions` and `generate_parenthetical_expressions` instead take the product of value permutations, operator choices, and parenthesizations, build each one, and drop it if a `seen` set already holds that numeric value.

## How a search runs

The solver walks those inequivalent expressions and checks each one against the goal. Nothing is stored ahead of time. It stops at the max solution count, or when you press Stop.

Leave **Dedupe repeated numbers** off to keep each hit in the index order the search already uses. The same line can show up more than once when an input number is repeated, because the leaves stay distinct.

Turn the option on and, for a hit only, each sum or product is flattened and its parts are ordered by their text. A set then keeps one copy of that line. Subtraction and division stay in operand order. The expressions that get checked do not change, and a miss still allocates nothing. The max solution count applies to those unique lines.

## Grouping ideas that only move a 0 or a 1

That repeated-number pass still keeps one line per expression. For 9, 8, 7, 1 the goal 24 has fifteen of those lines, and fourteen of them are 7+8+9 with the 1 multiplied or divided onto some subexpression. `(1+9-7)*8` is a different idea: there the 1 is added. `IdentityDedupe` in the Java sources, and `identitySignature` in `web/lib/identity-dedupe.js`, build a key for that distinction. The lines shown to the player do not change. A difficulty rating counts one key once.

The walk is the RPN evaluation already used to check a hit. Any operand whose value is 0 or 1, a dealt card or a computed subtree such as `3/3`, carries that subtree as a mark. `*1`, `/1`, `+0`, and `-0` drop the mark. The other operand keeps its own mark, so `1+0` is still a 1, and multiplying that 1 onto 5 parks both marks on the 5. `(1+0)*5` and `(5+0)*1` share a key. Several 0s and 1s are several marks on the stack at once. Countdown can deal two 1s. A dealt 0 does not occur in these games; a computed 0 does. Using the value for anything else (`1+9`, `1/7`, `0*8`, `0-x`) clears the mark and bakes the subtree into the core. The key is the core, with pure sums and pure products flattened and ordered, plus the multiset of subtrees that were dropped. Two lines match when those keys match. Where the 0 or 1 was attached is not part of the key.

This is a coarser duplicate than the ones already in the search. [ComSearch](https://arxiv.org/pdf/2210.07017) and the [OEIS A140606](https://oeis.org/A140606) count treat two expressions as the same from commutativity, associativity, and the reversed-minus rewrite, without looking at values. A subtree is not known to be 1 until the numbers are substituted, so the generator cannot drop these lines while it builds. `CommutativeOrder` is that same idea applied to a hit: it reorders sums and products, and it leaves `(8+9)/1+7` and `1*(8+9)+7` as two lines.

[Mark Dominus](https://blog.plover.com/math/24-puzzle-2.html) (21 August 2017) met the same fourteen-style pile on «1 3 3 5». His Ezpr normal form deletes a 1 from either bag of a product and a 0 from either bag of a sum, so `1×(3×(3+5))` and `3×((1×3)+5)` become one form. He also cancels a factor against the same factor in the denominator, so `(6÷3)×(4×3)` becomes `4×6`. The [4nums theory note](https://math24solver.com/math-24-game-theory) treats ×1 and ÷1 as the same operation and lets a factor of 1 sit on any subexpression. A later case there equates `a×b×1×1` with `a×b+1−1`. [Dan Goodman’s python-list note](https://mail.python.org/pipermail/python-list/2008-January/626365.html) (23 January 2008) lists the same unit laws, `a*1`, `a/1`, `a+0`, and `a-0`, among the equivalences for the Countdown numbers game, and several 24 solvers ([srihari-ganesh](https://srihari-ganesh.github.io/24solver/), [QiLing](https://qilingtools.com/en/math/24), [holynova/24points](https://github.com/holynova/24points)) collapse those when they print one canonical expression.

The mark walk follows the unit laws and Dominus’s deletion of a parked 1 or 0. It does not follow the rest of that cleanup. The dropped subtree stays in the key because every card has to be used, and the way a 0 or a 1 was made is part of the idea. `8*3*(3/3)` and `8*3+(3-3)` both leave the core `8*3`, and the payloads `3/3` and `3-3` keep them apart. That is the opposite of the 4nums case that equates two multiplied 1s with adding 1 and subtracting 1. `3*3*8/3` never has a 0 or a 1 as an operand, so this pass leaves it as its own line. The shown list stays the full set of expressions. The easy, medium, and hard bands still use that line count. The key is what a later difficulty rating counts, one idea at a time.

The Java port of that search is `ComSearchGenerator`. `generate` hands each expression to a consumer as it is built. Additive groups use a 2^{g−1} mask and multiplicative groups use 2^g−1, and any expression that contains subtraction is also emitted in its algebraic opposite. `UnorderedPartition` supplies the set splits. `Solver` searches with that generator and builds an `Expression` only when a value hits the goal. `ComSearchExplorer` is the Swing UI, and `CountingMain` launches it.

`ExpressionDynamic` in `web/lib/expression-dynamic.js` is the other generator still used by the browser. It keeps a set and drops duplicates with random truncators instead of yielding. With too few truncators that filter drops real expressions; the notes below record missing about 30 of the 27,914,126 at n=7 when only 10 truncators are used.

## Following the paper more closely

1. The counters already are the A140606 algorithm Appendix A uses. `Counter.java` and `Python_Version/counter.py` cite that appendix.
2. The Java search already uses ComSearch’s structural skip. `ComSearchGenerator` refuses the duplicate while it builds, the same idea as `enum` / `smart4`. `ExpressionPowerSet` in Python still builds every candidate and drops it after evaluation.
3. Treat a generated expression as correct when it is the one representative the enumerator yields, not only when the set size matches Equation (20).
4. Leave the weak-supervision ranker out of this claim. ComSearch then trains math-word-problem solvers. This repository stops at the expression set and the goal search.

A follow-up, once the enumeration matches Appendix D, is the paper’s false-matching problem. Several inequivalent expressions can evaluate to the same goal, and only some of them are the intended equation. The solver here already lists those hits (the 24-game examples are the small case). Ranking them, instead of returning every hit, is the next piece of the same paper. The 2024 counting paper cited below is a different extension: the same four operations plus additive inversion.

## Acknowledgments

Other work in the area:
[The OEIS page that has the values as well as the C code for generating the number of expressions](https://oeis.org/A140606)

[ComSearch](https://arxiv.org/pdf/2210.07017) enumerates inequivalent expressions without a stochastic filter, for a math-word-problem task. `ComSearchGenerator` is that search in the Java sources. The counts in its Appendix A are the result recreated here.

[A paper that details a counting algorithm for inequivalent expressions over the 4 standard operations with additive inversion](https://arxiv.org/pdf/2405.13928)
