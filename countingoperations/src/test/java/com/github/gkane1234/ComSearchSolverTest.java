package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

}
