package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * ComSearch generator with no evaluation / hash-set dedupe.
 *
 * <p>Additive {@code &} keeps the min-leaf child on the positive side (2^{g-1} masks).
 * Multiplicative {@code &} uses every nonempty numerator (2^g-1). For every emitted
 * expression that contains {@code -}, the algebraic opposite is also emitted (so
 * {@code a-b} and {@code b-a} both appear), matching OEIS A140606 / {@link Counter}.
 */
public class ComSearchGenerator {
    public static final byte OP_ADD = 0;
    public static final byte OP_SUB = 1;
    public static final byte OP_MUL = 2;
    public static final byte OP_DIV = 3;

    private final int n;
    private volatile boolean cancelled;

    public ComSearchGenerator(int n) {
        this.n = n;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void generate(Consumer<Expression> out) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        cancelled = false;
        int[] leaves = new int[n];
        for (int i = 0; i < n; i++) {
            leaves[i] = i;
        }
        if (n == 1) {
            out.accept(leaf(0));
            return;
        }

        Consumer<Expression> withOpposite = e -> {
            if (cancelled) {
                return;
            }
            out.accept(e);
            if (!cancelled && containsOp(e, OP_SUB)) {
                out.accept(negate(e));
            }
        };

        genAdd(leaves, withOpposite);
        if (!cancelled) {
            genMul(leaves, withOpposite);
        }
    }

    /**
     * Materialize all expressions into an {@link ExpressionList} sized by {@link Counter}.
     */
    public ExpressionList toExpressionList() {
        int expected = Counter.run(n).intValue();
        Expression[] expressions = new Expression[expected];
        int[] count = {0};
        generate(e -> {
            if (count[0] >= expected) {
                throw new IllegalStateException(
                        "ComSearch emitted more than Counter.run(" + n + ")=" + expected);
            }
            expressions[count[0]++] = e;
        });
        if (cancelled) {
            throw new IllegalStateException("ComSearch generation was cancelled");
        }
        if (count[0] != expected) {
            throw new IllegalStateException(
                    "ComSearch emitted " + count[0] + " expressions, expected " + expected);
        }
        return new ExpressionList(expressions, count[0], n);
    }

    public void genAdd(int[] leaves, Consumer<Expression> out) {
        if (cancelled) {
            return;
        }
        if (leaves.length == 1) {
            out.accept(leaf(leaves[0]));
            return;
        }

        UnorderedPartition.forEach(leaves, partition -> {
            if (cancelled) {
                return;
            }
            List<List<Expression>> childLists = new ArrayList<>(partition.size());
            for (int[] block : partition) {
                if (cancelled) {
                    return;
                }
                List<Expression> exprs = new ArrayList<>();
                genMul(block, exprs::add);
                childLists.add(exprs);
            }
            forEachCartesian(childLists, children -> combineAdditive(children, out));
        }, () -> cancelled);
    }

    public void genMul(int[] leaves, Consumer<Expression> out) {
        if (cancelled) {
            return;
        }
        if (leaves.length == 1) {
            out.accept(leaf(leaves[0]));
            return;
        }

        UnorderedPartition.forEach(leaves, partition -> {
            if (cancelled) {
                return;
            }
            List<List<Expression>> childLists = new ArrayList<>(partition.size());
            for (int[] block : partition) {
                if (cancelled) {
                    return;
                }
                List<Expression> exprs = new ArrayList<>();
                genAdd(block, exprs::add);
                childLists.add(exprs);
            }
            forEachCartesian(childLists, children -> combineMultiplicative(children, out));
        }, () -> cancelled);
    }

    /** Min-leaf child fixed in P; other children free → 2^{g-1} additive forms. */
    void combineAdditive(List<Expression> children, Consumer<Expression> out) {
        if (cancelled) {
            return;
        }
        int g = children.size();
        int minIndex = 0;
        int min = minLeaf(children.get(0));
        for (int b = 1; b < g; b++) {
            int leaf = minLeaf(children.get(b));
            if (leaf < min) {
                min = leaf;
                minIndex = b;
            }
        }

        int free = g - 1;
        for (int freeMask = 0; freeMask < (1 << free); freeMask++) {
            if (cancelled) {
                return;
            }
            List<Expression> positive = new ArrayList<>();
            List<Expression> negative = new ArrayList<>();
            positive.add(children.get(minIndex));
            int bit = 0;
            for (int b = 0; b < g; b++) {
                if (b == minIndex) {
                    continue;
                }
                if (((freeMask >> bit++) & 1) != 0) {
                    positive.add(children.get(b));
                } else {
                    negative.add(children.get(b));
                }
            }
            positive.sort(BY_MIN_LEAF);
            negative.sort(BY_MIN_LEAF);

            Expression left = foldForward(positive, OP_ADD);
            if (negative.isEmpty()) {
                out.accept(left);
            } else {
                out.accept(Expression.combineWithOp(
                        left, foldForward(negative, OP_ADD), OP_SUB));
            }
        }
    }

    /** Every nonempty numerator → 2^g-1 multiplicative forms. */
    void combineMultiplicative(List<Expression> children, Consumer<Expression> out) {
        if (cancelled) {
            return;
        }
        int g = children.size();
        for (int mask = 1; mask < (1 << g); mask++) {
            if (cancelled) {
                return;
            }
            List<Expression> numer = new ArrayList<>();
            List<Expression> denom = new ArrayList<>();
            for (int b = 0; b < g; b++) {
                if (((mask >> b) & 1) != 0) {
                    numer.add(children.get(b));
                } else {
                    denom.add(children.get(b));
                }
            }
            numer.sort(BY_MIN_LEAF);
            denom.sort(BY_MIN_LEAF);

            Expression left = foldForward(numer, OP_MUL);
            if (denom.isEmpty()) {
                out.accept(left);
            } else {
                out.accept(Expression.combineWithOp(
                        left, foldForward(denom, OP_MUL), OP_DIV));
            }
        }
    }

    static Expression leaf(int varIndex) {
        return new Expression(new byte[] {(byte) varIndex}, new byte[] {}, new boolean[] {true});
    }

    static Expression foldForward(List<Expression> parts, byte forwardOp) {
        Expression acc = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            acc = Expression.combineWithOp(acc, parts.get(i), forwardOp);
        }
        return acc;
    }

    static boolean containsOp(Expression e, byte op) {
        for (byte o : e.operations) {
            if (o == op) {
                return true;
            }
        }
        return false;
    }

    static Expression negate(Expression e) {
        return fromTree(negateNode(toTree(e)));
    }

    private static final class Node {
        final boolean isLeaf;
        final int var;
        final byte op;
        final Node left;
        final Node right;

        Node(int var) {
            this.isLeaf = true;
            this.var = var;
            this.op = -1;
            this.left = null;
            this.right = null;
        }

        Node(byte op, Node left, Node right) {
            this.isLeaf = false;
            this.var = -1;
            this.op = op;
            this.left = left;
            this.right = right;
        }
    }

    private static Node toTree(Expression e) {
        Node[] stack = new Node[e.order.length];
        int sp = 0;
        int vp = 0;
        int op = 0;
        for (boolean isNum : e.order) {
            if (isNum) {
                stack[sp++] = new Node(e.valueOrder[vp++] & 0xff);
            } else {
                Node b = stack[--sp];
                Node a = stack[--sp];
                stack[sp++] = new Node(e.operations[op++], a, b);
            }
        }
        return stack[0];
    }

    private static Expression fromTree(Node node) {
        if (node.isLeaf) {
            return leaf(node.var);
        }
        return Expression.combineWithOp(fromTree(node.left), fromTree(node.right), node.op);
    }

    private static boolean canAbsorbNegation(Node node) {
        if (node.isLeaf) {
            return false;
        }
        if (node.op == OP_SUB) {
            return true;
        }
        return canAbsorbNegation(node.left) || canAbsorbNegation(node.right);
    }

    /**
     * Push negation to a {@code -} (swap sides), or rewrite {@code +(x,y)} as a subtraction
     * when one side can absorb the sign.
     */
    private static Node negateNode(Node node) {
        if (node.isLeaf) {
            throw new IllegalStateException("Cannot negate a bare leaf");
        }
        if (node.op == OP_SUB) {
            return new Node(OP_SUB, node.right, node.left);
        }
        if (node.op == OP_ADD) {
            // -(x+y) = (-y)-x  or  (-x)-y
            if (canAbsorbNegation(node.right)) {
                return new Node(OP_SUB, negateNode(node.right), node.left);
            }
            if (canAbsorbNegation(node.left)) {
                return new Node(OP_SUB, negateNode(node.left), node.right);
            }
            throw new IllegalStateException("Cannot negate sum without a subtraction inside");
        }
        if (canAbsorbNegation(node.left)) {
            return new Node(node.op, negateNode(node.left), node.right);
        }
        if (canAbsorbNegation(node.right)) {
            return new Node(node.op, node.left, negateNode(node.right));
        }
        throw new IllegalStateException("Cannot negate expression");
    }

    private static final Comparator<Expression> BY_MIN_LEAF =
            Comparator.comparingInt(ComSearchGenerator::minLeaf);

    private static int minLeaf(Expression e) {
        int min = Integer.MAX_VALUE;
        for (byte v : e.valueOrder) {
            if ((v & 0xff) < min) {
                min = v & 0xff;
            }
        }
        return min;
    }

    private void forEachCartesian(List<List<Expression>> lists,
                                  Consumer<List<Expression>> out) {
        forEachCartesian(lists, 0, new ArrayList<>(lists.size()), out);
    }

    private void forEachCartesian(List<List<Expression>> lists, int index,
                                  List<Expression> current,
                                  Consumer<List<Expression>> out) {
        if (cancelled) {
            return;
        }
        if (index == lists.size()) {
            out.accept(new ArrayList<>(current));
            return;
        }
        for (Expression expr : lists.get(index)) {
            if (cancelled) {
                return;
            }
            current.add(expr);
            forEachCartesian(lists, index + 1, current, out);
            current.remove(current.size() - 1);
        }
    }

    public static void main(String[] args) {
        int maxN = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        for (int n = 1; n <= maxN; n++) {
            int[] count = {0};
            new ComSearchGenerator(n).generate(e -> count[0]++);
            long expected = Counter.run(n).longValue();
            System.out.println("n=" + n + ": got " + count[0] + ", expected " + expected
                    + (count[0] == expected ? " [OK]" : " [MISMATCH]"));
        }
    }
}
