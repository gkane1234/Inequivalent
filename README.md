# Generation of Inequivalent Expressions of N Variables

![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)

Generates and allows exploration of the set of inequivalent expressions involving N variables under a specific set of operations.

$$\frac{(2222222-2)(222222-2222)}{22222-222}+22 = 22222222$$


## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Development](#development)
- [Acknowledgments](#acknowledgments)
- [Appendix](#appendix)

## Features

- Feature 1: Generation is done through a novel dynamic programming and stochastic approach, and is agnostic to operations used.
- Feature 2: Can be generated using low RAM usage even in generating 10s of millions of expressions
- Feature 3: Storing and reading the expressions is done using a custom compression algorithm.
- Feature 4: Implements a known algorithm for finding the number of inequivalent expressions in Java and Python.

## Installation

### Prerequisites
- Package Trove for the faster, more ram intensive approach for generation
- Package RocksDB for the slower but less ram intensive apporach

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/gkane1234/counting_operations.git
2. Interpret countingoperations as a java project.
3. Install and use dependencies for Trove and RocksDB with Maven or something similar.



## Usage

Using the Applet is straightforward -- you can choose how many numbers to be in the expressions and a goal value to find. 
Then you can either explicitly choose the numbers to appear in the expression, or choose parameters for them and have them be generated randomly.


## Development

To begin, consider the following puzzle. You are given seven numbers
300,200,100,20,10,2,1 with the goal to make 1000 using each of those
numbers exactly once in an expression including the 4 basic operations
and parentheses (without allowing unary minus or multiplicative
inversion).

Before approaching this solution, consider the easier question of
finding a way to make 24 using the four numbers 2,4,7,10. This is a
classic question from the card game called the 24 game. To make 24 we
can use the expression ((4 − 2) \* 7) + 10. We can also use the
expression (10 − 7) \* 4 \* 2, or even (10 − 2) \* (7 − 4).

A brute force way of calculating such a solution could be to create all
possible orderings of numbers, all possible orderings and selections of
operations, and all possible placements of parenthesis, and then
checking all elements of the product of these three sets to see if it
evaluates to the answer. Clearly this is a huge space of expressions.
Given n numbers the total number of expressions is
*n*!4<sup>(*n* − 1)</sup>(*n* − 1)!. For 4 numbers it is feasible to
check the 9216 expressions but it is computationally unreasonable to
check the 14 billion necessary to find answers for 7.

The question now becomes how can we reduce the space of expressions to
check. Or in other words, how many algebraically inequivalent
expressions are there with the given constraints and how can we best
store these expressions to minimize memory usage and computation time?

I began solving this question by writing the brute force algorithm in
python. Expressions were converted to Reverse Polish Notation (RPN) and
stored as a tuple of numbers and operations. They were evaluated using
the simple stack algorithm for evaluating RPN. This was immediately able
to solve hard 24-game questions, such as 3,3,7,7 or 5,5,5,1 – but would
return a plethora of double counting and was generally computationally
slow. This coupled with the fact that I was curious about the true
number of expressions that needed to be checked to find all
algebraically unique solutions led me to a stochastic filter that could
eliminate duplicate expressions.

The crux of this filter is the use of sets of randomly generated float
values, called truncators, and use them to evaluate each candidate
expression. The evaluated value is then compared to a hash set of
already seen values and, if it is unique, the expression is added to the
list of unique expressions. Once I got this working, I began to
calculate the first values of a sequence I would become very familiar
with: 1,6,68,1170. The number of inequivalent expressions of n variables
with the 4 basic operations. A quick google search revealed that this
was a known sequence on the OEIS (https://oeis.org/A140606). I soon
realized that my brute force method to find the space to search for
inequivalent expressions was creating much more expressions than the
final result would be, and this discrepancy would get exponentially
worse as n increased.

This led to the most important change, which was finding a new algorithm
for the space to search for expressions. I decided to use a dynamic
approach, using results from previous answers and a “naive guess” of new
inequivalent expressions with n variables taking into account
commutativity.

For this let’s define $$f(x_1,x_2,\ldots,x_n) \text{ to be the set of all inequivalent expressions using the variables } x_i.$$

Let ⊕, or naive product, be an operator on two of these sets
*f*(*x*<sub>1</sub>, …, *x*<sub>*n*</sub>),
and
*f*(*y*<sub>1</sub>, …, *y*<sub>*n*</sub>),
defined
*f*(*x*<sub>1</sub>, …, *x*<sub>*n*</sub>)
⊕
*f*(*y*<sub>1</sub>, …, *y*<sub>*n*</sub>)
 = {*e*<sub>*x*</sub> + *e*<sub>*y*</sub>, *e*<sub>*x*</sub> − *e*<sub>*y*</sub>, *e*<sub>*y*</sub> − *e*<sub>*x*</sub>, *e*<sub>*x*</sub> \* *e*<sub>*y*</sub>, *e*<sub>*x*</sub>/*e*<sub>*y*</sub>, *e*<sub>*y*</sub>/*e*<sub>*x*</sub> \|*e*<sub>*x*</sub> ∈ *f*(*x*<sub>*i*</sub>) and *e*<sub>*y*</sub> ∈ *f*(*y*<sub>*i*</sub>)}


For example the inequivalent expressions using the 2 variables a and b would be  f(a)⊕f(b) or    \{a+b,a-b,b-a,ab,a/b,b/a\}, since f(a)={a} and f(b)={b}

To construct, for example, the inequivalent expressions for 4 numbers, we could try putting together expressions of 1 and 3 numbers and also of 2 and 2 numbers, of course we don’t know which of the 4 numbers a,b,c,d are in the two groups, so we need to check all combinations of numbers for these two partitions:

f(a)⊕f(b,c,d), f(b)⊕f(a,c,d), f(c)⊕f(a,b,d), f(d)⊕f(a,b,c)
 f(a,b)⊕f(c,d), f(a,c)⊕f(b,d), f(a,d)⊕f(b,c)
This space becomes much smaller than the brute force method:
|f(a,b,c,d)|=|f(a)|*|f(b,c,d)|*6*(4 choose 1)+|f(a,b)|*|f(c,d)|*6*(4 choose 2) = 1*68*6*4!+6*6*6*4!/(2*2)=2928

And for 5 and above it becomes even more favorable:

$$|f(x_1,...,x_5)|=84060$$
$$|f(x_1,...,x_6)|=2,163,792$$
$$|f(x_1,...,x_7)|=87,240,636$$

At this point I was able to get solutions for 5 numbers relatively quickly but I was intrigued if I could calculate solutions to questions involving 6 or even 7 numbers. For this reason I rewrote the code in Java since I knew this alone would result in about a 20000% speed up.
Using java, it was able to find answers for 6 numbers, however it was unable to find answers for 7. 



Already I could find some pretty gnarly looking solutions to some questions, such as using 3,4,7,9,14,16 to make 2027: ((((4/7)+(16*9))*14)+3)=2027. Which was found in approximately 30 seconds including generating the set of inequivalent expressions to solve it. 

At this point I wanted to find answers for 7 values. However Java quickly ran out of space when trying to do this. To reduce the amount of space being used, which was primarily in the Double hash set used to check if values had been seen before, I switched to a Float Hash set, since I did not need the precision of doubles. I also noticed that java.lang.Double.valueOf() was accounting for almost
6% of cpu runtime. I tried to eliminate all boxing and unboxing of double primitives, and found that I was still doing this with the implementation of my apply method for doing operations.  The next speed up I could think of was to use TFloatHashSet, which utilizes primitive data types to save on memory used for boxing and unboxing the floats, and saved a few gb of memory over the Java.Util implementation. I also looked for other small improvements that I could do. Including trying to use a custom hash function, multithreading, rewriting code I
found on the OEIS website in python and java to directly find the number
of inequivalent expressions I was expecting to find so I use an array
instead of using a list and using a custom set size stack.

With these improvements, I was able to improve by a further 500% and it ran similar calculations on 6 numbers in about 6 seconds. 

I then went back to trying to solve for 7 values. By waiting about 30
minutes, I was able to find that using 10 truncators with 7 values
missed the expected number of expressions by around 30 (out of 29
million) which indicates that around 1 in a million times all 10
truncators falsely believe that a certain unique expression is
equivalent to ones before, indicating a 10% chance for any of them, incorrectly assuming independence and a uniform probability. By increasing the number of truncators to 20 and letting it run for over an hour, it was finally able to find all the expressions and save them to a file. 

After I got it able to load the expressions for 7 variables down to about 20 seconds, I created a compression algorithm to hold the large lists of expressions, this essential saves the data of each expression into a bitmap of a long primitive and then packs them together into an array of longs so that there are not bits unused. Getting this to work took a lot longer than expected, but doing so reduced the size of the saved file for 7 variables from 1.7 gb to 100mb and the read time down from 20 seconds to about 100ms.

The last optimization was to implement a simple hash set using RocksDB which allows for the hash set to be mostly saved off of RAM so that the generation could be done without using 10+gb of RAM, instead needing significantly less than 1 gb even for generating 7, however taking about 4 times as long due to the speed reduction on lookup.

Current goals:

1. Generating the expressions for 8. Napkin math suggests that using the current method the compressed saved file would take up about 6 gb, however the database hash sets would use around 400 gb during the generation process and the actual calculation would take no less than 300 hours
2. Implementing the enumeration approach. There is a paper that details generating this list of inequivalent expressions directly using a combinatorial approach. This seems like a nicer way to approach the problem, however I have been unable to figure out how to do this works.
3. Continue improving the UI. Right now it looks very barebones however it still does what it needs to.


## Acknowledgments

Other work in the area:
[The OEIS page that has the values as well as the C code for generating the number of expressions](https://oeis.org/A140606)

[A paper that details a method of enumerating inequivalent expressions without the need of a filter, used for a task in NLP](https://arxiv.org/pdf/2210.07017)

[A paper that details a counting algorithm for inequivalent expressions over the 4 standard operations with additive inversion](https://arxiv.org/pdf/2405.13928)



## Appendix

I leave you with what I’ve gotten from the 7 number solver so far –
since with 27,914,126 unique expressions there are a lot of
“interesting” results.

First an answer to the original problem:

(((((20+100)\*10)+200)/2)+300)\*1 = 1000 (\>200 solutions)

And some other solutions: 

(((((10+100)\*20)+300)\*2)\*200)\*1 = 1000000 (170 solutions)

((((20\*300)-(1/200))/10)-100)\*2 = 999.999 ( 50 solutions) 

(200-(((1/(20-10))/100)-300))\*2 = 999.998 ( 50 solutions) 

((100\*200)-(((10-1)\*2)/300))/20 = 999.997 ( 20 solutions)

((100/2)-(1/((200+300)\*10)))\*20= 999.996 ( 20 solutions)

Here are three entirely different ways of getting these 7 “random”
numbers to 1 

(((2365/1486)/6854)+(3568/483576))+(23465/23645) = 1

(3568/(483576-2365))-(23465/((6854/1486)-23645)) = 1

(((6854/1486)+23465)/23645)+(3568/(483576-2365)) = 1

Goal: 12345 Vales: 1 2 3 4 5 12 34 (((((34\*12)+4)\*2)-1)\*3)\*5=12345
