package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ComSearch generator with memoized sub-block lists and reused scratch buffers.
 *
 * <p>Additive {@code &} keeps the min-leaf child on the positive side (2^{g-1} masks).
 * Multiplicative {@code &} uses every nonempty numerator (2^g-1). Expressions that
 * contain {@code -} also emit their algebraic opposite.
 */
public class ComSearchGenerator {
    public static final byte OP_ADD = 0;
    public static final byte OP_SUB = 1;
    public static final byte OP_MUL = 2;
    public static final byte OP_DIV = 3;

    private static final int ARRAY_MEMO_MAX_N = 20;

    private final int n;
    private final int fullBitset;
    private final Expression[] leafExprs;
    private final List<Expression>[] addMemo;
    private final List<Expression>[] mulMemo;
    private final Map<Integer, List<Expression>> addMap;
    private final Map<Integer, List<Expression>> mulMap;
    private final boolean useArrayMemo;

    private final Expression[] posBuf = new Expression[64];
    private final Expression[] negBuf = new Expression[64];
    private final Expression[] cartBuf = new Expression[64];

    private volatile boolean cancelled;

    @SuppressWarnings("unchecked")
    public ComSearchGenerator(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        if (n > 31) {
            throw new IllegalArgumentException("n must be <= 31 for bitset memoization");
        }
        this.n = n;
        this.fullBitset = (1 << n) - 1;
        this.leafExprs = new Expression[n];
        for (int i = 0; i < n; i++) {
            leafExprs[i] = leaf(i);
        }
        this.useArrayMemo = n <= ARRAY_MEMO_MAX_N;
        if (useArrayMemo) {
            int size = 1 << n;
            this.addMemo = new List[size];
            this.mulMemo = new List[size];
            this.addMap = null;
            this.mulMap = null;
        } else {
            this.addMemo = null;
            this.mulMemo = null;
            this.addMap = new HashMap<>();
            this.mulMap = new HashMap<>();
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void generate(Consumer<Expression> out) {
        cancelled = false;
        if (n == 1) {
            out.accept(leafExprs[0]);
            return;
        }
        streamRoot(true, withOpposite(out));
        if (!cancelled) {
            streamRoot(false, withOpposite(out));
        }
    }

    private Consumer<Expression> withOpposite(Consumer<Expression> out) {
        return e -> {
            if (cancelled) {
                return;
            }
            out.accept(e);
            if (!cancelled && containsOp(e, OP_SUB)) {
                out.accept(negate(e));
            }
        };
    }

    /** Stream top-level forms; memoize only proper sub-blocks. */
    private void streamRoot(boolean additive, Consumer<Expression> out) {
        int[] leaves = new int[n];
        for (int i = 0; i < n; i++) {
            leaves[i] = i;
        }
        UnorderedPartition.forEach(leaves, partition -> {
            if (cancelled) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Expression>[] childLists = new List[partition.size()];
            for (int i = 0; i < partition.size(); i++) {
                int blockBits = bitsetOf(partition.get(i));
                childLists[i] = additive ? getMul(blockBits) : getAdd(blockBits);
            }
            cartesianEmit(childLists, additive, out);
        }, () -> cancelled);
    }

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

    List<Expression> getAdd(int bitset) {
        List<Expression> cached = getCached(true, bitset);
        if (cached != null) {
            return cached;
        }
        List<Expression> built = buildLayer(bitset, true);
        putCached(true, bitset, built);
        return built;
    }

    List<Expression> getMul(int bitset) {
        List<Expression> cached = getCached(false, bitset);
        if (cached != null) {
            return cached;
        }
        List<Expression> built = buildLayer(bitset, false);
        putCached(false, bitset, built);
        return built;
    }

    private List<Expression> getCached(boolean add, int bitset) {
        if (useArrayMemo) {
            return add ? addMemo[bitset] : mulMemo[bitset];
        }
        return add ? addMap.get(bitset) : mulMap.get(bitset);
    }

    private void putCached(boolean add, int bitset, List<Expression> list) {
        if (useArrayMemo) {
            if (add) {
                addMemo[bitset] = list;
            } else {
                mulMemo[bitset] = list;
            }
        } else if (add) {
            addMap.put(bitset, list);
        } else {
            mulMap.put(bitset, list);
        }
    }

    private List<Expression> buildLayer(int bitset, boolean additive) {
        int count = Integer.bitCount(bitset);
        if (count == 1) {
            return Collections.singletonList(leafExprs[Integer.numberOfTrailingZeros(bitset)]);
        }
        int[] leaves = leavesFromBitset(bitset, count);
        List<Expression> out = new ArrayList<>();
        UnorderedPartition.forEach(leaves, partition -> {
            if (cancelled) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Expression>[] childLists = new List[partition.size()];
            for (int i = 0; i < partition.size(); i++) {
                int blockBits = bitsetOf(partition.get(i));
                childLists[i] = additive ? getMul(blockBits) : getAdd(blockBits);
            }
            cartesianCollect(childLists, additive, out);
        }, () -> cancelled);
        return out;
    }

    private void cartesianEmit(List<Expression>[] lists, boolean additive, Consumer<Expression> out) {
        cartesian(lists, 0, cartBuf, additive, null, out);
    }

    private void cartesianCollect(List<Expression>[] lists, boolean additive, List<Expression> out) {
        cartesian(lists, 0, cartBuf, additive, out, null);
    }

    private void cartesian(List<Expression>[] lists, int index, Expression[] cur,
                           boolean additive, List<Expression> collect, Consumer<Expression> emit) {
        if (cancelled) {
            return;
        }
        if (index == lists.length) {
            if (additive) {
                combineAdditive(cur, lists.length, collect, emit);
            } else {
                combineMultiplicative(cur, lists.length, collect, emit);
            }
            return;
        }
        List<Expression> list = lists[index];
        for (int i = 0; i < list.size(); i++) {
            if (cancelled) {
                return;
            }
            cur[index] = list.get(i);
            cartesian(lists, index + 1, cur, additive, collect, emit);
        }
    }

    private void combineAdditive(Expression[] children, int g,
                                 List<Expression> collect, Consumer<Expression> emit) {
        if (cancelled || g == 0) {
            return;
        }
        int minIndex = 0;
        int min = minLeaf(children[0]);
        for (int b = 1; b < g; b++) {
            int leaf = minLeaf(children[b]);
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
            int posN = 0;
            int negN = 0;
            posBuf[posN++] = children[minIndex];
            int bit = 0;
            for (int b = 0; b < g; b++) {
                if (b == minIndex) {
                    continue;
                }
                if (((freeMask >> bit++) & 1) != 0) {
                    posBuf[posN++] = children[b];
                } else {
                    negBuf[negN++] = children[b];
                }
            }
            sortByMinLeaf(posBuf, posN);
            sortByMinLeaf(negBuf, negN);

            Expression left = foldForward(posBuf, posN, OP_ADD);
            Expression result = negN == 0
                    ? left
                    : Expression.combineWithOp(left, foldForward(negBuf, negN, OP_ADD), OP_SUB);
            accept(result, collect, emit);
        }
    }

    private void combineMultiplicative(Expression[] children, int g,
                                       List<Expression> collect, Consumer<Expression> emit) {
        if (cancelled || g == 0) {
            return;
        }
        for (int mask = 1; mask < (1 << g); mask++) {
            if (cancelled) {
                return;
            }
            int posN = 0;
            int negN = 0;
            for (int b = 0; b < g; b++) {
                if (((mask >> b) & 1) != 0) {
                    posBuf[posN++] = children[b];
                } else {
                    negBuf[negN++] = children[b];
                }
            }
            sortByMinLeaf(posBuf, posN);
            sortByMinLeaf(negBuf, negN);

            Expression left = foldForward(posBuf, posN, OP_MUL);
            Expression result = negN == 0
                    ? left
                    : Expression.combineWithOp(left, foldForward(negBuf, negN, OP_MUL), OP_DIV);
            accept(result, collect, emit);
        }
    }

    private static void accept(Expression result, List<Expression> collect, Consumer<Expression> emit) {
        if (collect != null) {
            collect.add(result);
        }
        if (emit != null) {
            emit.accept(result);
        }
    }

    private static Expression foldForward(Expression[] parts, int len, byte forwardOp) {
        Expression acc = parts[0];
        for (int i = 1; i < len; i++) {
            acc = Expression.combineWithOp(acc, parts[i], forwardOp);
        }
        return acc;
    }

    private static void sortByMinLeaf(Expression[] arr, int len) {
        for (int i = 1; i < len; i++) {
            Expression key = arr[i];
            int keyMin = minLeaf(key);
            int j = i - 1;
            while (j >= 0 && minLeaf(arr[j]) > keyMin) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    private int[] leavesFromBitset(int bitset, int count) {
        int[] leaves = new int[count];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if ((bitset & (1 << i)) != 0) {
                leaves[k++] = i;
            }
        }
        return leaves;
    }

    private static int bitsetOf(int[] leaves) {
        int b = 0;
        for (int leaf : leaves) {
            b |= 1 << leaf;
        }
        return b;
    }

    static Expression leaf(int varIndex) {
        return new Expression(new byte[] {(byte) varIndex}, new byte[] {}, new boolean[] {true});
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

    private static Node negateNode(Node node) {
        if (node.isLeaf) {
            throw new IllegalStateException("Cannot negate a bare leaf");
        }
        if (node.op == OP_SUB) {
            return new Node(OP_SUB, node.right, node.left);
        }
        if (node.op == OP_ADD) {
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

    private static int minLeaf(Expression e) {
        int min = Integer.MAX_VALUE;
        for (byte v : e.valueOrder) {
            int leaf = v & 0xff;
            if (leaf < min) {
                min = leaf;
            }
        }
        return min;
    }

    public static void main(String[] args) {
        int maxN = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        for (int n = 1; n <= maxN; n++) {
            long t0 = System.nanoTime();
            int[] count = {0};
            new ComSearchGenerator(n).generate(e -> count[0]++);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            long expected = Counter.run(n).longValue();
            System.out.println("n=" + n + ": got " + count[0] + ", expected " + expected
                    + (count[0] == expected ? " [OK]" : " [MISMATCH]")
                    + " in " + ms + "ms");
        }
    }
}
