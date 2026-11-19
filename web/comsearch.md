# Combinatorial Generation and Analysis of Algebraic Expressions

This project investigates the combinatorial structure of arithmetic expressions: how many mathematically distinct expressions can be constructed from a given set of numbers, how to generate them efficiently, and how to analyze their solutions to a target equation.

A useful example is the classic 24 game. Given four numbers, the objective is to combine them using addition, subtraction, multiplication, and division, using each number exactly once, to produce 24. Even with only four inputs, there are many possible arrangements of numbers, operations, and parentheses.

The difficulty becomes substantially greater as the number of inputs increases. A brute-force approach generates every possible expression and evaluates each one, but this produces enormous redundancy. For example, `a + b`, `b + a`, `(a + b) + c`, and `a + (b + c)` represent equivalent mathematical structures under commutativity and associativity.

This project implements algorithms to enumerate mathematically inequivalent expressions without repeatedly generating equivalent forms. It is based on the combinatorial enumeration techniques described in the research paper [ComSearch: Equation Searching with Combinatorial Strategy for Solving Math Word Problems with Weak Supervision](https://arxiv.org/abs/2210.07017), by Liu, Guan, Shen, Cheng, and Kurohashi.

Beyond recreating the paper's enumeration and counting methods, I implemented additional algorithms for identifying solution equivalences involving zero and one, analyzing distinct solution strategies, and estimating puzzle difficulty.

## Combinatorial Enumeration of Algebraic Expressions

The central problem is to enumerate all mathematically inequivalent expressions involving n distinct, labeled variables and the four standard arithmetic operations, without unary minus.

Rather than constructing every possible expression and removing duplicates afterward, the ComSearch approach transforms expressions into canonical structural representations, or *skeletons*. These representations capture the underlying algebraic structure while ignoring differences attributable to commutativity and associativity.

The algorithm divides expressions into two symmetric categories based on their outermost operations:

- **Additive expressions:** Sums and differences of multiplicative subexpressions.
- **Multiplicative expressions:** Products and quotients of additive subexpressions.

For example, the expressions `a/b - c + d` and `a/b - (c - d)` can be rewritten into the same canonical additive structure. Similarly, `(a + b) * (c - d/e)` and `(b + a) * (c - d/e)` share the same canonical representation.

This transformation allows the search to operate on structural equivalence classes rather than individual syntactic expressions.

### Depth-first structural generation

I implemented the ComSearch enumeration algorithm in Java through `ComSearchGenerator`.

The generator recursively partitions the input variables into groups and constructs expression trees from those partitions using depth-first search (DFS). The underlying combinatorial construction is related to Schröder's fourth problem, which concerns counting labeled series-reduced rooted trees.

The implementation uses several structural constraints to avoid redundant generation:

- `UnorderedPartition` enumerates the partitions of the input variables into groups.
- Additive groups use a (2^{g-1}) subset mask, accounting for the different sign assignments while fixing an overall sign convention.
- Multiplicative groups use (2^g-1) possible nonempty subset assignments.
- Expressions containing subtraction are also emitted in their algebraic opposite, ensuring that the search accounts for both signs without redundantly generating equivalent structures.
- The generator passes expressions to a consumer as they are constructed, rather than materializing the entire expression space in memory.

The result is a structural search that eliminates algebraically redundant expressions during construction, rather than relying on numerical evaluation or a post-generation duplicate filter.

### Target-based expression search

The Java `Solver` uses `ComSearchGenerator` to search for expressions matching a specified target.

Expressions are generated incrementally and evaluated using reverse Polish notation (RPN), which allows the expression tree to be evaluated using a stack. An `Expression` object is only constructed when a generated expression evaluates to the target.

The search does not require a precomputed database of expressions. It can terminate when a specified maximum number of solutions has been reached or when the user stops the search.

## Independent Combinatorial Counting and Validation

A second major component of the project is independently reproducing the mathematical counting results used to validate the enumeration algorithm.

Appendix A of the ComSearch paper derives an exponential generating function (EGF) for counting inequivalent expressions involving n labeled variables.

An exponential generating function encodes a sequence of combinatorial counts as the coefficients of a power series:

[  
F(z)=\sum_{n\geq 0}a_n\frac{z^n}{n!}  
]

The paper constructs two families of expressions, corresponding to the two possible outermost operation classes, and derives their generating functions using combinatorial set constructions.

I independently implemented the counting algorithm in both Java (`Counter.java`) and Python (`counter.py`), porting the recurrence from [OEIS A140606](https://oeis.org/A140606), which is the sequence referenced in the paper.

The implementation reproduces the paper's counting decomposition:

- Two recursively defined families, `sa` and `sc`, represent the additive and multiplicative outermost structures.
- `p2m1[g] = 2^g - 1` accounts for the number of nonempty subsets of a group of g subexpressions.
- `tryIndex` enumerates partitions of n into g parts, corresponding to the construction of sets of subexpressions.
- The final expression, (2sc-sc_1+2sa-sa_1), implements the paper's (S=T-U) construction: counting expressions under an expanded set of sign conventions and subtracting the invalid all-negative forms.

This provides an independent mathematical verification mechanism for the expression generator.

The reproduced sequence is:

| Number of inputs | Inequivalent expressions |
| ---------------- | ------------------------ |
| 1                | 1                        |
| 2                | 6                        |
| 3                | 68                       |
| 4                | 1,170                    |
| 5                | 27,142                   |
| 6                | 793,002                  |
| 7                | 27,914,126               |

The Java generator is tested against the independent counter through four inputs. The browser implementation also checks generated expression counts through six inputs, while the counting implementation independently calculates the seventh value.

An important distinction is that matching the total number of expressions validates the size of the generated set, but does not by itself prove that every individual expression is the correct representative of its equivalence class. This distinction is important when evaluating combinatorial enumeration algorithms.

## Additional Algebraic Equivalence Detection

The original ComSearch equivalence rules account for structural algebraic identities, including commutativity, associativity, and transformations involving subtraction.

I extended this analysis to identify another class of redundancies that only becomes apparent after substituting numerical values for the variables: expressions that differ only in how zero and one are incorporated into a solution.

For example, consider:

[  
8\times 3\times(3/3)  
]

and

[  
8\times 3+(3-3)  
]

Both expressions have the same underlying numerical core, (8\times3), but incorporate the remaining inputs through different constructions of one and zero.

These expressions are not equivalent under the original structural enumeration rules, because the subexpressions (3/3) and (3-3) are not algebraically interchangeable as symbolic expressions.

I implemented a separate identity-detection algorithm in Java (`IdentityDedupe`) and JavaScript (`identity-dedupe.js`) to identify this additional class of solution equivalences.

### Mark-based identity analysis

The algorithm traverses the expression using the same RPN evaluation process used by the solver, maintaining additional metadata alongside numerical values.

When an operand evaluates to zero or one, it is marked. This includes both original input values and intermediate results, such as (3/3=1).

The algorithm propagates or removes these marks according to the identity operations:

[  
x\times1=x  
]

[  
x/1=x  
]

[  
x+0=x  
]

[  
x-0=x  
]

When a zero or one is used in an operation that does not preserve its identity, the mark is cleared and the corresponding subtree becomes part of the expression's core.

The resulting canonical identity key consists of:

1. The remaining expression core, with pure sums and products flattened and their components ordered.
2. A multiset of the zero- or one-valued subexpressions removed during evaluation.

This allows expressions to be grouped according to their underlying solution structure without losing information about how the remaining inputs were used.

For example, `(1+9-7)*8` represents a different strategy from `7+8+9` with the remaining 1 multiplied or divided into a subexpression. The algorithm preserves that distinction.

Importantly, this is deliberately a narrower equivalence relation than full algebraic simplification. It does not cancel arbitrary common factors or equate constructions such as multiplying by two ones with adding one and subtracting one. The way a zero or one was constructed remains part of the identity key.

The original expressions are still displayed to the user. This additional equivalence analysis is used separately to measure the number of distinct underlying solution strategies.

## Quantitative Difficulty Estimation

I also developed a difficulty-scoring algorithm that estimates how challenging a numerical puzzle is based on the structure and distribution of its solutions.

Rather than simply counting the number of expressions that reach the target, the algorithm considers the complexity of individual operations and the number of distinct solution strategies.

### Expression-level costs

Each arithmetic operation incurs a cost based on the operands and result.

Values are represented as reduced fractions (p/q). The magnitude component is based on:

[  
\ln(1+|p|)  
]

The denominator incurs an additional cost:

[  
2.5\ln(q)  
]

The algorithm distinguishes between input values, intermediate values, and final results. Input numbers and the final result receive half weight on the logarithmic magnitude term, while intermediate results receive full weight when used in subsequent operations.

Negative values have the same cost as their absolute values. Zero has a magnitude cost of zero.

The cost of an expression is the sum of its individual operation costs, with a minimum expression cost of one.

### Combining distinct solutions

Each distinct identity key contributes its cheapest expression cost.

If the resulting solution strategies have costs (d_1,d_2,\ldots,d_n), the overall difficulty is calculated using a parallel-sum relationship:

[  
\frac{1}{D}=\sum_{i=1}^{n}\frac{1}{d_i}  
]

Equivalently:

[  
D=\left(\sum_{i=1}^{n}\frac{1}{d_i}\right)^{-1}  
]

This construction means that multiple independent ways of reaching the target reduce the overall difficulty. A puzzle with one solution has a difficulty equal to that solution's cost, while additional solution strategies lower the aggregate score.

### Accounting for repeated inputs

The algorithm also adjusts difficulty based on the frequency of repeated input numbers.

A number appearing once receives a weight of 1. A number appearing k times receives a weight of:

[  
w(k)=1+\frac{1}{k}  
]

The sum of these weights scales the aggregate difficulty.

For example, four distinct inputs produce a multiplier of 4, whereas four copies of the same input produce a multiplier of (5/4).

This models the difference between puzzles with distinct numbers and those with repeated values, which can have substantially different combinatorial structures.

The difficulty calculation is implemented in Java (`Difficulty`) and JavaScript (`difficulty.js`). It is only reported after a complete search: stopping early would omit potential solutions and incorrectly increase the estimated difficulty.

## Multiple Implementations and Experimental Analysis

The project includes implementations across Java, Python, and JavaScript, allowing the algorithms to be independently tested and compared.

**Java**

- `ComSearchGenerator`: Structural depth-first enumeration of inequivalent expressions.
- `Solver`: Target-based search and evaluation.
- `Counter`: Independent combinatorial counting.
- `IdentityDedupe`: Value-dependent zero/one identity detection.
- `Difficulty`: Quantitative difficulty estimation.
- `ComSearchExplorer`: Swing-based interface.
- `ComSearchSolverTest`: Tests the generator against the independent counting implementation.

**Python**

- `counter.py`: Independent implementation of the OEIS A140606 recurrence.
- `operation_counting.py`: Expression generation and operation-counting experiments.
- `ExpressionPowerSet`: Generates expressions individually for analysis.

**JavaScript**

- `expression-dynamic.js`: Browser-based expression generation.
- `counter.js`: Stored combinatorial reference counts.
- `identity-dedupe.js`: Additional identity detection.
- `difficulty.js`: Browser implementation of the difficulty model.

An additional part of the research involved comparing different approaches to duplicate elimination. In particular, the Python implementations that generate candidate expressions through permutations, operation combinations, and parenthesizations use numerical evaluation and a `seen` set to remove duplicates afterward. This differs from the Java ComSearch generator, which eliminates structural duplicates before constructing the full expression.

The browser's earlier dynamic generator also uses random truncators to filter duplicate expressions. Experimental analysis showed that using too few truncators can inadvertently discard valid expressions, including approximately 30 of the 27,914,126 expressions at seven inputs when only 10 truncators are used. This illustrates the importance of distinguishing between a heuristic filter and an exhaustive enumeration algorithm.

## Research Context and Further Work

This project recreates and extends the expression-enumeration and counting components of ComSearch, rather than the paper's complete math-word-problem-solving pipeline.

The original paper uses the generated equations to identify candidate mathematical explanations for numerical word problems. Even after eliminating algebraically equivalent expressions, multiple inequivalent equations can evaluate to the same answer. This is the paper's *false-matching problem*: numerical agreement does not necessarily imply that an equation represents the intended reasoning.

The paper addresses this through a ranking model that selects among candidate equations. My implementation focuses on the underlying combinatorial search, expression analysis, and quantitative evaluation rather than the machine-learning ranking component.

A natural next step would be to investigate the paper's false-matching problem directly: developing methods to distinguish equations that merely produce the correct numerical answer from those that represent the intended mathematical reasoning.

## References

- [ComSearch: Equation Searching with Combinatorial Strategy for Solving Math Word Problems with Weak Supervision](https://arxiv.org/abs/2210.07017) — Liu, Guan, Shen, Cheng, and Kurohashi.
- [OEIS A140606](https://oeis.org/A140606) — Counting sequence and recurrence implementation.
- [Counting Inequivalent Expressions over the Four Standard Operations with Additive Inversion](https://arxiv.org/abs/2405.13928) — Related work on extending the counting problem.

**Technologies and Concepts:** Java, Python, JavaScript, combinatorics, exponential generating functions, recurrence relations, depth-first search, combinatorial partitions, expression trees, reverse Polish notation, algebraic equivalence, algorithm validation, and mathematical optimization.
