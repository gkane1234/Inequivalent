package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class ComSearchSolverTest {

    @Test
    public void comSearchCountsMatchCounterThroughN4() {
        for (int n = 1; n <= 4; n++) {
            ExpressionList list = new ComSearchGenerator(n).toExpressionList();
            Assert.assertEquals(Counter.run(n).intValue(), list.getNumExpressions());
        }
    }

    @Test
    public void streamingSolveFinds24Game() {
        Solver solver = new Solver(4);
        List<EvaluatedExpression> found = Collections.synchronizedList(new ArrayList<>());
        Solver.SearchStats stats = solver.findSolutionsStreaming(
                new double[] {2, 4, 7, 10}, 24, 50, found::add);
        Assert.assertTrue("expected solutions for 2,4,7,10 → 24", stats.found > 0);
        Assert.assertEquals(found.size(), stats.found);
        Assert.assertTrue(stats.checked >= stats.found);
        Assert.assertTrue(stats.hitPercent() > 0);
    }

    @Test
    public void evalDuringGenerateMatchesExpressionEval() {
        double[] values = {2, 4, 7, 10};
        double factor = Math.pow(10, Solver.ROUNDING);
        AtomicInteger checked = new AtomicInteger();
        new ComSearchGenerator(4).search(values, (raw, materialize) -> {
            checked.incrementAndGet();
            double rounded = Double.isNaN(raw) || Double.isInfinite(raw)
                    ? raw
                    : Math.round(raw * factor) / factor;
            Expression expr = materialize.get();
            double fromExpr = expr.evaluateWithValues(values, Solver.ROUNDING);
            if (Double.isNaN(rounded) && Double.isNaN(fromExpr)) {
                return;
            }
            Assert.assertTrue(
                    "value mismatch for " + expr,
                    Solver.equal(rounded, fromExpr));
        }, 1);
        Assert.assertEquals(Counter.run(4).intValue(), checked.get());
    }

    @Test
    public void parallelSearchMatchesSerialCounts() {
        double[] values = {1, 2, 3, 4, 5};
        double goal = 10;
        Solver serial = new Solver(5, 1);
        Solver parallel = new Solver(5, Math.max(2, Runtime.getRuntime().availableProcessors()));
        Solver.SearchStats serialStats = serial.findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, e -> {});
        Solver.SearchStats parallelStats = parallel.findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, e -> {});
        Assert.assertEquals(Counter.run(5).longValue(), serialStats.checked);
        Assert.assertEquals(serialStats.checked, parallelStats.checked);
        Assert.assertEquals(serialStats.found, parallelStats.found);
    }

    @Test
    public void stopKeepsPartialResults() {
        Solver solver = new Solver(5);
        AtomicInteger found = new AtomicInteger();
        Thread stopper = new Thread(() -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            solver.requestStop();
        });
        stopper.start();
        Solver.SearchStats stats = solver.findSolutionsStreaming(
                new double[] {1, 2, 3, 4, 5}, 10, Integer.MAX_VALUE,
                e -> found.incrementAndGet());
        try {
            stopper.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Assert.assertTrue(stats.checked >= 0);
        Assert.assertEquals(found.get(), stats.found);
    }

    @Test
    public void largeNStreamsWithoutMaterializingChildLists() {
        // n=9 used to OOM by collecting full child expression lists.
        Solver solver = new Solver(9, 1);
        Solver.SearchStats stats = solver.findSolutionsStreaming(
                new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 100, 1, e -> {});
        Assert.assertEquals(1, stats.found);
        Assert.assertTrue(stats.checked >= 1);
    }

    @Test
    public void dedupeOffKeepsIndexOrderCopies() {
        List<String> lines = displays(solveHits(new double[] {3, 3, 7, 7}, 24, false));
        Assert.assertTrue(lines.contains("(3+3/7)*7"));
        Assert.assertTrue(lines.contains("(3/7+3)*7"));
        List<String> zeros = displays(solveHits(new double[] {3, 3}, 0, false));
        Assert.assertTrue(Collections.frequency(zeros, "3-3") > 1);
    }

    @Test
    public void dedupeOnCollapsesRepeatedNumbers() {
        double[] values = {3, 3, 7, 7};
        List<String> off = displays(solveHits(values, 24, false));
        List<EvaluatedExpression> onHits = solveHits(values, 24, true);
        List<String> on = displays(onHits);
        Assert.assertEquals(1, Collections.frequency(on, "(3+3/7)*7"));
        Assert.assertFalse(on.contains("(3/7+3)*7"));
        Assert.assertTrue(on.size() < off.size());
        Assert.assertEquals(new HashSet<String>(on).size(), on.size());
        for (EvaluatedExpression hit : onHits) {
            Assert.assertTrue(Solver.equal(24, hit.evaluateWithValues(values, Solver.ROUNDING)));
        }

        List<String> fiftyOff = displays(solveHits(values, 50, false));
        List<String> fiftyOn = displays(solveHits(values, 50, true));
        Assert.assertTrue(Collections.frequency(fiftyOff, "3/3+7*7") > 1);
        Assert.assertEquals(1, Collections.frequency(fiftyOn, "3/3+7*7"));

        List<String> zeros = displays(solveHits(new double[] {3, 3}, 0, true));
        Assert.assertEquals(1, Collections.frequency(zeros, "3-3"));
    }

    @Test
    public void dedupeOnDoesNotMergeDistinctInputs() {
        double[] values = {1, 2, 3, 4};
        Solver.SearchStats off = new Solver(4).findSolutionsStreaming(
                values, 10, Integer.MAX_VALUE, e -> {}, null, false);
        Solver.SearchStats on = new Solver(4).findSolutionsStreaming(
                values, 10, Integer.MAX_VALUE, e -> {}, null, true);
        Assert.assertEquals(off.found, on.found);
        Assert.assertEquals(off.checked, on.checked);

        Set<String> canonical = new HashSet<String>();
        int[] count = {0};
        new ComSearchGenerator(4).search(values, (raw, materialize) -> {
            count[0]++;
            Expression original = materialize.get();
            Expression reordered = CommutativeOrder.reorder(original, values);
            double before = original.evaluateWithValues(values, Solver.ROUNDING);
            double after = reordered.evaluateWithValues(values, Solver.ROUNDING);
            if (Double.isNaN(before) || Double.isNaN(after)) {
                Assert.assertTrue(Double.isNaN(before) && Double.isNaN(after));
            } else {
                Assert.assertTrue(Solver.equal(before, after));
            }
            canonical.add(new EvaluatedExpression(reordered, values, 0).display());
        }, 1);
        Assert.assertEquals(Counter.run(4).intValue(), count[0]);
        Assert.assertEquals(count[0], canonical.size());
    }

    @Test
    public void dedupeCountsUniqueHitsTowardTheCap() {
        Solver.SearchStats stats = new Solver(4).findSolutionsStreaming(
                new double[] {3, 3, 7, 7}, 24, 1, e -> {}, null, true);
        Assert.assertEquals(1, stats.found);
        Assert.assertTrue(stats.checked < Counter.run(4).longValue());
    }

    @Test
    public void parallelDedupeMatchesSerial() {
        double[] values = {3, 3, 7, 7};
        List<String> serial = displays(solveHits(values, 24, true));
        List<String> parallel = new ArrayList<String>();
        Solver solver = new Solver(4, 4);
        solver.findSolutionsStreaming(values, 24, Integer.MAX_VALUE, e -> parallel.add(e.display()), null, true);
        Assert.assertEquals(serial.size(), parallel.size());
        Assert.assertEquals(new HashSet<String>(serial), new HashSet<String>(parallel));
    }

    private static List<EvaluatedExpression> solveHits(double[] values, double goal, boolean dedupe) {
        List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        new Solver(values.length).findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, hits::add, null, dedupe);
        return hits;
    }

    private static List<String> displays(List<EvaluatedExpression> hits) {
        List<String> lines = new ArrayList<String>();
        for (EvaluatedExpression hit : hits) {
            lines.add(hit.display());
        }
        return lines;
    }

}
