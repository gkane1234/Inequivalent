## Earlier Stochastic Expression Generator

The project also includes an earlier expression-generation implementation, developed approximately two years before the ComSearch-based generator. This implementation uses a fundamentally different approach: dynamically constructing expression sets from smaller previously generated sets, combined with stochastic filtering to identify algebraically equivalent expressions.

This earlier implementation is accessible through a separate tab in the application.

### Dynamic Construction and Stochastic Filtering

The initial implementation used brute-force enumeration, generating expressions from permutations of input numbers, operation sequences, and parenthesizations. Expressions were represented in Reverse Polish Notation (RPN), allowing efficient evaluation using a stack.

Although this approach successfully solved difficult four-number problems, such as the 24-game configurations (3,3,7,7) and (5,5,5,1), it generated substantial redundancy.

To address this, I developed a stochastic filtering algorithm using randomly generated sets of floating-point values, called *truncators*. Each candidate expression was evaluated using these values, and its resulting numerical signature was compared against a hash set of previously encountered signatures. Expressions producing previously unseen signatures were retained as unique candidates.

This provided a probabilistic method for detecting algebraic equivalence without requiring explicit symbolic manipulation.

I subsequently developed a dynamic construction approach to reduce the candidate search space itself. Let (f(x_1,\ldots,x_n)) represent the set of inequivalent expressions using a given set of n variables. I defined an operator (\oplus) that combines two expression sets by applying all six possible binary operations between their elements:

[  
f(X)\oplus f(Y)=  
{a+b,\ a-b,\ b-a,\ ab,\ a/b,\ b/a}  
]

where (a\in f(X)) and (b\in f(Y)).

Larger expression sets could then be constructed by combining previously generated sets corresponding to partitions of the input variables. For example, generating expressions from four variables involves combining partitions of sizes 1+3 and 2+2, considering the different ways of assigning variables to each partition.

This approach dramatically reduced the candidate space compared with generating every possible permutation, operation sequence, and parenthesization. However, the intermediate candidate sets still contained expressions that were algebraically equivalent, requiring the stochastic filtering step.

### Performance and Memory Optimization

As the number of input variables increased, the primary bottleneck shifted from computation time to memory consumption, particularly the storage and lookup of previously encountered floating-point signatures.

I rewrote the implementation in Java and made several optimizations:

- Replaced `Double` hash sets with `Float` hash sets to reduce memory consumption where double precision was unnecessary.
- Eliminated unnecessary boxing and unboxing of primitive numeric values.
- Replaced standard Java collections with Trove's `TFloatHashSet`, reducing memory overhead associated with storing primitive values.
- Implemented custom hashing and optimized data structures, including a fixed-size array for expected expression counts and a custom stack representation.
- Introduced multithreading to improve generation performance.
- Implemented the OEIS A140606 counting recurrence in Java and Python to independently calculate expected expression counts.

These changes improved performance by approximately 5× over the preceding implementation, bringing comparable six-variable calculations down to approximately six seconds.

### Stochastic Accuracy and Seven-Variable Generation

The stochastic filter introduced an important tradeoff between computational efficiency and the probability of incorrectly eliminating unique expressions.

For seven inputs, the expected number of inequivalent expressions was approximately 27.9 million. Experiments with ten independent truncators showed that the filter could miss approximately 30 expressions out of this total, demonstrating the limitations of relying on a finite collection of numerical signatures.

Increasing the number of truncators to twenty allowed the implementation to reproduce the expected expression count after a run exceeding one hour.

### Expression Compression and Disk-Backed Storage

Even after successfully generating the seven-variable expression set, storing and loading the resulting data remained a significant bottleneck.

I implemented a custom compression scheme that encodes each expression as a bit sequence packed into primitive `long` values. By packing expression data into contiguous arrays of longs, the implementation eliminated unused bits and substantially reduced storage overhead.

This reduced the serialized seven-variable expression set from approximately 1.7 GB to 100 MB and decreased loading time from approximately 20 seconds to 100 milliseconds.

I also implemented a custom hash-set interface backed by RocksDB, allowing most of the deduplication data to reside on disk rather than in RAM. This reduced peak memory requirements from more than 10 GB to less than 1 GB, at the cost of approximately four times longer generation time due to slower disk-backed lookups.

This earlier implementation provided the foundation for exploring the combinatorial enumeration problem and motivated the subsequent transition to the more structurally rigorous ComSearch-based generator.
