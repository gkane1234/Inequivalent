package com.github.gkane1234;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ComSearch generator with optional eval-during-generate for solving.
 *
 * <p>Additive {@code &} keeps the min-leaf child on the positive side (2^{g-1} masks).
 * Multiplicative {@code &} uses every nonempty numerator (2^g-1). For every emitted
 * expression that contains {@code -}, the algebraic opposite is also emitted.
 *
 * <p>{@link #search} evaluates while combining and only builds {@link Expression}
 * objects when the consumer asks (hits). Root partitions stream into a bounded
 * worker pool — they are never collected into a giant list (that OOMs for large n).
 */
public class ComSearchGenerator {
    public static final byte OP_ADD = 0;
    public static final byte OP_SUB = 1;
    public static final byte OP_MUL = 2;
    public static final byte OP_DIV = 3;

    private final int n;
    private volatile boolean cancelled;
    private VExpr[] leafNodes;

    /** Per-worker scratch so search misses allocate no candidate objects. */
    private final ThreadLocal<ScratchMaterializer> scratch =
            ThreadLocal.withInitial(ScratchMaterializer::new);
    private final ThreadLocal<ScratchMaterializer> scratchOpposite =
            ThreadLocal.withInitial(ScratchMaterializer::new);

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
        leafNodes = null;
        int[] leaves = identityLeaves();
        if (n == 1) {
            out.accept(leaf(0));
            return;
        }

        Consumer<VExpr> withOpposite = node -> {
            if (cancelled) {
                return;
            }
            out.accept(node.toExpression());
            if (!cancelled && node.hasSub) {
                out.accept(negate(node).toExpression());
            }
        };

        genAdd(leaves, withOpposite);
        if (!cancelled) {
            genMul(leaves, withOpposite);
        }
    }

    /**
     * Consumer for search candidates. {@code materialize} is only valid during the
     * call (and must be used synchronously if at all — i.e. on hits).
     */
    @FunctionalInterface
    public interface CandidateConsumer {
        void accept(double rawValue, Supplier<Expression> materialize);
    }

    public void search(double[] values, CandidateConsumer onCandidate) {
        search(values, onCandidate,
                Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
    }

    public void search(double[] values, CandidateConsumer onCandidate, int parallelism) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        if (values == null || values.length != n) {
            throw new IllegalArgumentException(
                    "expected " + n + " values, got "
                            + (values == null ? 0 : values.length));
        }
        cancelled = false;
        leafNodes = new VExpr[n];
        for (int i = 0; i < n; i++) {
            leafNodes[i] = VExpr.leaf(i, values[i]);
        }
        int[] leaves = identityLeaves();
        if (n == 1) {
            VExpr one = leafNodes[0];
            onCandidate.accept(one.value, one::toExpression);
            return;
        }

        SearchSink sink = (value, hasSub, mat) -> {
            if (cancelled) {
                return;
            }
            onCandidate.accept(value, mat);
            if (!cancelled && hasSub) {
                ScratchMaterializer opp = scratchOpposite.get();
                opp.asOppositeOf(mat);
                onCandidate.accept(-value, opp);
            }
        };

        if (parallelism <= 1) {
            streamRootJobs(leaves, sink, null);
            return;
        }

        int workers = parallelism;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        Semaphore inflight = new Semaphore(workers * 2);
        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicBoolean producerDone = new AtomicBoolean(false);
        Object doneMonitor = new Object();
        try {
            streamRootJobs(leaves, sink, job -> {
                if (cancelled) {
                    return;
                }
                try {
                    inflight.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    return;
                }
                inFlight.incrementAndGet();
                pool.execute(() -> {
                    try {
                        if (!cancelled) {
                            runRootJobSearch(job.additive, job.partition, sink);
                        }
                    } finally {
                        inflight.release();
                        if (inFlight.decrementAndGet() == 0 && producerDone.get()) {
                            synchronized (doneMonitor) {
                                doneMonitor.notifyAll();
                            }
                        }
                    }
                });
            });

            producerDone.set(true);
            synchronized (doneMonitor) {
                while (inFlight.get() > 0 && !cancelled) {
                    try {
                        doneMonitor.wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancelled = true;
                        break;
                    }
                }
            }
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @FunctionalInterface
    private interface SearchSink {
        void accept(double value, boolean hasSub, Supplier<Expression> materialize);
    }

    private static final class RootJob {
        final boolean additive;
        final List<int[]> partition;

        RootJob(boolean additive, List<int[]> partition) {
            this.additive = additive;
            this.partition = partition;
        }
    }

    private void streamRootJobs(int[] leaves, SearchSink sink, Consumer<RootJob> parallelSubmit) {
        BooleanSupplier cancelledFn = () -> cancelled;
        UnorderedPartition.forEach(leaves, partition -> {
            if (cancelled) {
                return;
            }
            RootJob job = new RootJob(true, partition);
            if (parallelSubmit != null) {
                parallelSubmit.accept(job);
            } else {
                runRootJobSearch(true, partition, sink);
            }
        }, cancelledFn);
        if (!cancelled) {
            UnorderedPartition.forEach(leaves, partition -> {
                if (cancelled) {
                    return;
                }
                RootJob job = new RootJob(false, partition);
                if (parallelSubmit != null) {
                    parallelSubmit.accept(job);
                } else {
                    runRootJobSearch(false, partition, sink);
                }
            }, cancelledFn);
        }
    }

    private void runRootJobSearch(boolean additive, List<int[]> partition, SearchSink sink) {
        if (cancelled) {
            return;
        }
        VExpr[] chosen = new VExpr[partition.size()];
        chooseChildren(0, partition, additive, chosen, () -> {
            if (additive) {
                combineAdditiveToSink(chosen, sink);
            } else {
                combineMultiplicativeToSink(chosen, sink);
            }
        });
    }

    private void runRootJob(boolean additive, List<int[]> partition, Consumer<VExpr> out) {
        if (cancelled) {
            return;
        }
        VExpr[] chosen = new VExpr[partition.size()];
        chooseChildren(0, partition, additive, chosen, () -> {
            if (additive) {
                combineAdditive(chosen, out);
            } else {
                combineMultiplicative(chosen, out);
            }
        });
    }

    private void chooseChildren(int index, List<int[]> partition, boolean additive,
                                VExpr[] chosen, Runnable onComplete) {
        if (cancelled) {
            return;
        }
        if (index == partition.size()) {
            onComplete.run();
            return;
        }
        int[] block = partition.get(index);
        Consumer<VExpr> onChild = child -> {
            if (cancelled) {
                return;
            }
            chosen[index] = child;
            chooseChildren(index + 1, partition, additive, chosen, onComplete);
        };
        if (additive) {
            genMul(block, onChild);
        } else {
            genAdd(block, onChild);
        }
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

    private int[] identityLeaves() {
        int[] leaves = new int[n];
        for (int i = 0; i < n; i++) {
            leaves[i] = i;
        }
        return leaves;
    }

    private void genAdd(int[] leaves, Consumer<VExpr> out) {
        if (cancelled) {
            return;
        }
        if (leaves.length == 1) {
            out.accept(leafV(leaves[0]));
            return;
        }
        UnorderedPartition.forEach(leaves, partition -> runRootJob(true, partition, out),
                () -> cancelled);
    }

    private void genMul(int[] leaves, Consumer<VExpr> out) {
        if (cancelled) {
            return;
        }
        if (leaves.length == 1) {
            out.accept(leafV(leaves[0]));
            return;
        }
        UnorderedPartition.forEach(leaves, partition -> runRootJob(false, partition, out),
                () -> cancelled);
    }

    private void combineAdditiveToSink(VExpr[] children, SearchSink sink) {
        if (cancelled) {
            return;
        }
        int g = children.length;
        int minIndex = indexOfMinLeaf(children);
        VExpr[] pos = new VExpr[g];
        VExpr[] neg = new VExpr[g];
        boolean childHasSub = anyHasSub(children);
        long freeLimit = 1L << (g - 1);
        ScratchMaterializer mat = scratch.get();
        for (long freeMask = 0; freeMask < freeLimit; freeMask++) {
            if (cancelled) {
                return;
            }
            int posN = 0;
            int negN = 0;
            pos[posN++] = children[minIndex];
            int bit = 0;
            for (int b = 0; b < g; b++) {
                if (b == minIndex) {
                    continue;
                }
                if (((freeMask >> bit++) & 1L) != 0) {
                    pos[posN++] = children[b];
                } else {
                    neg[negN++] = children[b];
                }
            }
            sortByMinLeaf(pos, posN);
            sortByMinLeaf(neg, negN);

            double value = foldValue(pos, posN, OP_ADD);
            if (negN > 0) {
                value -= foldValue(neg, negN, OP_ADD);
            }
            mat.setAdditive(children, minIndex, freeMask);
            sink.accept(value, childHasSub || negN > 0, mat);
        }
    }

    private void combineMultiplicativeToSink(VExpr[] children, SearchSink sink) {
        if (cancelled) {
            return;
        }
        int g = children.length;
        VExpr[] numer = new VExpr[g];
        VExpr[] denom = new VExpr[g];
        boolean childHasSub = anyHasSub(children);
        long maskLimit = 1L << g;
        ScratchMaterializer mat = scratch.get();
        for (long mask = 1; mask < maskLimit; mask++) {
            if (cancelled) {
                return;
            }
            int numerN = 0;
            int denomN = 0;
            for (int b = 0; b < g; b++) {
                if (((mask >> b) & 1L) != 0) {
                    numer[numerN++] = children[b];
                } else {
                    denom[denomN++] = children[b];
                }
            }
            sortByMinLeaf(numer, numerN);
            sortByMinLeaf(denom, denomN);

            double value = foldValue(numer, numerN, OP_MUL);
            if (denomN > 0) {
                double d = foldValue(denom, denomN, OP_MUL);
                value = d != 0 ? value / d : Double.NaN;
            }
            mat.setMultiplicative(children, mask);
            sink.accept(value, childHasSub, mat);
        }
    }

    private Expression buildAdditiveExpression(VExpr[] children, int minIndex, long freeMask) {
        int g = children.length;
        VExpr[] pos = new VExpr[g];
        VExpr[] neg = new VExpr[g];
        int posN = 0;
        int negN = 0;
        pos[posN++] = children[minIndex];
        int bit = 0;
        for (int b = 0; b < g; b++) {
            if (b == minIndex) {
                continue;
            }
            if (((freeMask >> bit++) & 1L) != 0) {
                pos[posN++] = children[b];
            } else {
                neg[negN++] = children[b];
            }
        }
        sortByMinLeaf(pos, posN);
        sortByMinLeaf(neg, negN);
        VExpr left = foldForward(pos, posN, OP_ADD);
        VExpr result = negN == 0
                ? left
                : VExpr.combine(left, foldForward(neg, negN, OP_ADD), OP_SUB);
        return result.toExpression();
    }

    private Expression buildMultiplicativeExpression(VExpr[] children, long mask) {
        int g = children.length;
        VExpr[] numer = new VExpr[g];
        VExpr[] denom = new VExpr[g];
        int numerN = 0;
        int denomN = 0;
        for (int b = 0; b < g; b++) {
            if (((mask >> b) & 1L) != 0) {
                numer[numerN++] = children[b];
            } else {
                denom[denomN++] = children[b];
            }
        }
        sortByMinLeaf(numer, numerN);
        sortByMinLeaf(denom, denomN);
        VExpr left = foldForward(numer, numerN, OP_MUL);
        VExpr result = denomN == 0
                ? left
                : VExpr.combine(left, foldForward(denom, denomN, OP_MUL), OP_DIV);
        return result.toExpression();
    }

    private void combineAdditive(VExpr[] children, Consumer<VExpr> out) {
        if (cancelled) {
            return;
        }
        int g = children.length;
        int minIndex = indexOfMinLeaf(children);
        VExpr[] pos = new VExpr[g];
        VExpr[] neg = new VExpr[g];
        long freeLimit = 1L << (g - 1);
        for (long freeMask = 0; freeMask < freeLimit; freeMask++) {
            if (cancelled) {
                return;
            }
            int posN = 0;
            int negN = 0;
            pos[posN++] = children[minIndex];
            int bit = 0;
            for (int b = 0; b < g; b++) {
                if (b == minIndex) {
                    continue;
                }
                if (((freeMask >> bit++) & 1L) != 0) {
                    pos[posN++] = children[b];
                } else {
                    neg[negN++] = children[b];
                }
            }
            sortByMinLeaf(pos, posN);
            sortByMinLeaf(neg, negN);
            VExpr left = foldForward(pos, posN, OP_ADD);
            if (negN == 0) {
                out.accept(left);
            } else {
                out.accept(VExpr.combine(left, foldForward(neg, negN, OP_ADD), OP_SUB));
            }
        }
    }

    private void combineMultiplicative(VExpr[] children, Consumer<VExpr> out) {
        if (cancelled) {
            return;
        }
        int g = children.length;
        VExpr[] numer = new VExpr[g];
        VExpr[] denom = new VExpr[g];
        long maskLimit = 1L << g;
        for (long mask = 1; mask < maskLimit; mask++) {
            if (cancelled) {
                return;
            }
            int numerN = 0;
            int denomN = 0;
            for (int b = 0; b < g; b++) {
                if (((mask >> b) & 1L) != 0) {
                    numer[numerN++] = children[b];
                } else {
                    denom[denomN++] = children[b];
                }
            }
            sortByMinLeaf(numer, numerN);
            sortByMinLeaf(denom, denomN);
            VExpr left = foldForward(numer, numerN, OP_MUL);
            if (denomN == 0) {
                out.accept(left);
            } else {
                out.accept(VExpr.combine(left, foldForward(denom, denomN, OP_MUL), OP_DIV));
            }
        }
    }

    private final class ScratchMaterializer implements Supplier<Expression> {
        private VExpr[] children;
        private int minIndex;
        private long mask;
        private boolean additive;
        private boolean opposite;
        private Supplier<Expression> base;

        void setAdditive(VExpr[] children, int minIndex, long mask) {
            this.children = children;
            this.minIndex = minIndex;
            this.mask = mask;
            this.additive = true;
            this.opposite = false;
            this.base = null;
        }

        void setMultiplicative(VExpr[] children, long mask) {
            this.children = children;
            this.mask = mask;
            this.additive = false;
            this.opposite = false;
            this.base = null;
        }

        void asOppositeOf(Supplier<Expression> base) {
            this.base = base;
            this.opposite = true;
        }

        @Override
        public Expression get() {
            Expression expr;
            if (base != null) {
                expr = base.get();
            } else if (additive) {
                expr = buildAdditiveExpression(children, minIndex, mask);
            } else {
                expr = buildMultiplicativeExpression(children, mask);
            }
            if (opposite) {
                return negate(toVExpr(expr)).toExpression();
            }
            return expr;
        }
    }

    private VExpr leafV(int varIndex) {
        if (leafNodes != null) {
            return leafNodes[varIndex];
        }
        return VExpr.leaf(varIndex, 0.0);
    }

    static Expression leaf(int varIndex) {
        return new Expression(new byte[] {(byte) varIndex}, new byte[] {}, new boolean[] {true});
    }

    private static int indexOfMinLeaf(VExpr[] children) {
        int minIndex = 0;
        int min = children[0].minLeaf;
        for (int b = 1; b < children.length; b++) {
            if (children[b].minLeaf < min) {
                min = children[b].minLeaf;
                minIndex = b;
            }
        }
        return minIndex;
    }

    private static boolean anyHasSub(VExpr[] children) {
        for (VExpr child : children) {
            if (child.hasSub) {
                return true;
            }
        }
        return false;
    }

    private static double foldValue(VExpr[] parts, int len, byte op) {
        double acc = parts[0].value;
        for (int i = 1; i < len; i++) {
            acc = VExpr.apply(op, acc, parts[i].value);
        }
        return acc;
    }

    private static VExpr foldForward(VExpr[] parts, int len, byte forwardOp) {
        VExpr acc = parts[0];
        for (int i = 1; i < len; i++) {
            acc = VExpr.combine(acc, parts[i], forwardOp);
        }
        return acc;
    }

    private static void sortByMinLeaf(VExpr[] arr, int len) {
        for (int i = 1; i < len; i++) {
            VExpr key = arr[i];
            int keyMin = key.minLeaf;
            int j = i - 1;
            while (j >= 0 && arr[j].minLeaf > keyMin) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    static VExpr negate(VExpr node) {
        return negateNode(node);
    }

    private static VExpr toVExpr(Expression e) {
        VExpr[] stack = new VExpr[e.order.length];
        int sp = 0;
        int vp = 0;
        int op = 0;
        for (boolean isNum : e.order) {
            if (isNum) {
                stack[sp++] = VExpr.leaf(e.valueOrder[vp++] & 0xff, 0.0);
            } else {
                VExpr b = stack[--sp];
                VExpr a = stack[--sp];
                stack[sp++] = VExpr.combine(a, b, e.operations[op++]);
            }
        }
        return stack[0];
    }

    static final class VExpr {
        final boolean isLeaf;
        final int var;
        final byte op;
        final VExpr left;
        final VExpr right;
        final int minLeaf;
        final boolean hasSub;
        final double value;

        private VExpr(int var, double value) {
            this.isLeaf = true;
            this.var = var;
            this.op = -1;
            this.left = null;
            this.right = null;
            this.minLeaf = var;
            this.hasSub = false;
            this.value = value;
        }

        private VExpr(byte op, VExpr left, VExpr right, double value) {
            this.isLeaf = false;
            this.var = -1;
            this.op = op;
            this.left = left;
            this.right = right;
            this.minLeaf = Math.min(left.minLeaf, right.minLeaf);
            this.hasSub = op == OP_SUB || left.hasSub || right.hasSub;
            this.value = value;
        }

        static VExpr leaf(int var, double value) {
            return new VExpr(var, value);
        }

        static VExpr combine(VExpr left, VExpr right, byte op) {
            return new VExpr(op, left, right, apply(op, left.value, right.value));
        }

        Expression toExpression() {
            if (isLeaf) {
                return ComSearchGenerator.leaf(var);
            }
            return Expression.combineWithOp(left.toExpression(), right.toExpression(), op);
        }

        static double apply(byte op, double a, double b) {
            switch (op) {
                case OP_ADD:
                    return a + b;
                case OP_SUB:
                    return a - b;
                case OP_MUL:
                    return a * b;
                case OP_DIV:
                    return b != 0 ? a / b : Double.NaN;
                default:
                    throw new IllegalArgumentException("unknown op " + op);
            }
        }
    }

    private static boolean canAbsorbNegation(VExpr node) {
        if (node.isLeaf) {
            return false;
        }
        if (node.op == OP_SUB) {
            return true;
        }
        return canAbsorbNegation(node.left) || canAbsorbNegation(node.right);
    }

    private static VExpr negateNode(VExpr node) {
        if (node.isLeaf) {
            throw new IllegalStateException("Cannot negate a bare leaf");
        }
        if (node.op == OP_SUB) {
            return VExpr.combine(node.right, node.left, OP_SUB);
        }
        if (node.op == OP_ADD) {
            if (canAbsorbNegation(node.right)) {
                return VExpr.combine(negateNode(node.right), node.left, OP_SUB);
            }
            if (canAbsorbNegation(node.left)) {
                return VExpr.combine(negateNode(node.left), node.right, OP_SUB);
            }
            throw new IllegalStateException("Cannot negate sum without a subtraction inside");
        }
        if (canAbsorbNegation(node.left)) {
            return VExpr.combine(negateNode(node.left), node.right, node.op);
        }
        if (canAbsorbNegation(node.right)) {
            return VExpr.combine(node.left, negateNode(node.right), node.op);
        }
        throw new IllegalStateException("Cannot negate expression");
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
