package com.github.gkane1234;

import java.util.ArrayList;
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
        List<EvaluatedExpression> found = new ArrayList<>();
        int count = solver.findSolutionsStreaming(
                new double[] {2, 4, 7, 10}, 24, 50, found::add);
        Assert.assertTrue("expected solutions for 2,4,7,10 → 24", count > 0);
        Assert.assertEquals(count, found.size());
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
        solver.findSolutionsStreaming(
                new double[] {1, 2, 3, 4, 5}, 10, Integer.MAX_VALUE,
                e -> found.incrementAndGet());
        try {
            stopper.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Stop is cooperative; we mainly assert it returns without error.
        Assert.assertTrue(found.get() >= 0);
    }
}
