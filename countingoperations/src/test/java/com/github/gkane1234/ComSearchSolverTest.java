package com.github.gkane1234;

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
    public void solverFinds24GameWithComSearch() {
        // load=false → generate with ComSearch; do not require on-disk expression files
        Solver solver = new Solver(4, false, false, null, false, true);
        Assert.assertEquals(1170, solver.solverSet.getNumExpressions());

        SolutionList solutions = solver.findAllSolutions(
                new double[] {2, 4, 7, 10}, 24, 50);
        Assert.assertTrue(
                "expected at least one solution for 2,4,7,10 → 24, got "
                        + solutions.getNumSolutions(),
                solutions.getNumSolutions() > 0);
    }

    @Test
    public void streamingSolveFinds24Game() {
        Solver solver = new Solver(4, false, false, null, false, true);
        SolutionList solutions = solver.findAllSolutionsStreaming(
                new double[] {2, 4, 7, 10}, 24, 50);
        Assert.assertTrue(solutions.getNumSolutions() > 0);
    }
}
