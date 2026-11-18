package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DifficultyTest {
    private static final int ADD = ComSearchGenerator.OP_ADD;
    private static final int SUB = ComSearchGenerator.OP_SUB;
    private static final int MUL = ComSearchGenerator.OP_MUL;
    private static final int DIV = ComSearchGenerator.OP_DIV;
    private static final double TOLERANCE = 1e-6;

    @Test
    public void singleStepsFollowTheExamples() {
        Assert.assertEquals(1.0397207708, cost(new double[] {0, 7}, DIV), TOLERANCE);
        Assert.assertEquals(1.4451858789, cost(new double[] {2, 1}, DIV), TOLERANCE);
        Assert.assertEquals(2.3592494356, cost(new double[] {6, 1}, ADD), TOLERANCE);
        Assert.assertEquals(3.4011973817, cost(new double[] {8, 3}, MUL), TOLERANCE);
        Assert.assertEquals(5.3085127114, cost(new double[] {53, 27}, SUB), TOLERANCE);
        Assert.assertEquals(5.5191194439, cost(new double[] {7, 3}, DIV), TOLERANCE);
        Assert.assertEquals(5.8334237736, cost(new double[] {17, 19}, MUL), TOLERANCE);
        Assert.assertTrue(cost(new double[] {0, 7}, DIV) < cost(new double[] {2, 1}, DIV));
        Assert.assertTrue(cost(new double[] {8, 3}, MUL) < cost(new double[] {53, 27}, SUB));
        Assert.assertTrue(cost(new double[] {53, 27}, SUB) < cost(new double[] {7, 3}, DIV));
        Assert.assertTrue(cost(new double[] {7, 3}, DIV) < cost(new double[] {17, 19}, MUL));
    }

    @Test
    public void solutionTotalsMatchTheSketch() {
        Assert.assertEquals(10.2004381855, cost(new double[] {10, 7, 2, 4},
                new int[] {0, 1, 2, 3}, new int[] {SUB, MUL, MUL},
                bits(true, true, false, true, false, true, false)), TOLERANCE);
        Assert.assertEquals(13.7653407155, cost(new double[] {6, 6, 6, 6},
                new int[] {0, 1, 2, 3}, new int[] {ADD, ADD, ADD},
                bits(true, true, false, true, false, true, false)), TOLERANCE);
        Assert.assertEquals(14.7650591157, cost(new double[] {6, 6, 6, 6},
                new int[] {0, 1, 2, 3}, new int[] {MUL, ADD, SUB},
                bits(true, true, false, true, true, false, false)), TOLERANCE);
        Assert.assertEquals(26.6060643390, cost(new double[] {5, 5, 5, 1},
                new int[] {1, 3, 0, 2}, new int[] {DIV, SUB, MUL},
                bits(true, true, true, false, false, true, false)), TOLERANCE);
        Assert.assertEquals(21.4089386377, cost(new double[] {1, 3, 4, 6},
                new int[] {3, 0, 1, 2}, new int[] {DIV, SUB, DIV},
                bits(true, true, true, true, false, false, false)), TOLERANCE);
        Assert.assertEquals(20.5146373744, cost(new double[] {3, 3, 8, 8},
                new int[] {3, 1, 2, 0}, new int[] {DIV, SUB, DIV},
                bits(true, true, true, true, false, false, false)), TOLERANCE);
        Assert.assertEquals(24.0, eval(new double[] {5, 5, 5, 1},
                new int[] {1, 3, 0, 2}, new int[] {DIV, SUB, MUL},
                bits(true, true, true, false, false, true, false)), 1e-6);
        Assert.assertEquals(24.0, eval(new double[] {1, 3, 4, 6},
                new int[] {3, 0, 1, 2}, new int[] {DIV, SUB, DIV},
                bits(true, true, true, true, false, false, false)), 1e-6);
        Assert.assertEquals(24.0, eval(new double[] {3, 3, 8, 8},
                new int[] {3, 1, 2, 0}, new int[] {DIV, SUB, DIV},
                bits(true, true, true, true, false, false, false)), 1e-6);
    }

    @Test
    public void floorAndParallelSum() {
        Expression zero = rpn(new int[] {0, 1}, new int[] {ADD}, bits(true, true, false));
        double[] zeros = {0, 0};
        Assert.assertEquals(0.0, Difficulty.solutionCost(zero, zeros), TOLERANCE);
        Difficulty.Rating one = Difficulty.rate(Arrays.asList(zero), zeros);
        Assert.assertEquals(1.5, one.difficulty, TOLERANCE);
        Assert.assertEquals(1, one.ideas);

        Expression product = rpn(new int[] {0, 1}, new int[] {MUL}, bits(true, true, false));
        Difficulty.Rating both = Difficulty.rate(Arrays.asList(zero, product), zeros);
        Assert.assertEquals(0.75, both.difficulty, TOLERANCE);
        Assert.assertEquals(2, both.ideas);

        Expression sixPlusOne = rpn(new int[] {0, 1}, new int[] {ADD}, bits(true, true, false));
        double[] sixOne = {6, 1};
        Difficulty.Rating repeated = Difficulty.rate(Arrays.asList(sixPlusOne, sixPlusOne), sixOne);
        Assert.assertEquals(
                Difficulty.solutionDifficulty(sixPlusOne, sixOne) * 2.0, repeated.difficulty, TOLERANCE);
        Assert.assertEquals(1, repeated.ideas);
        Assert.assertEquals(2, repeated.lines);

        Assert.assertTrue(Double.isNaN(Difficulty.rate(new ArrayList<Expression>(), new double[0]).difficulty));
    }

    @Test
    public void dealsUseOneIdeaAndStayOrdered() {
        Difficulty.Rating spareOne = rateDeal(new double[] {9, 8, 7, 1}, 24);
        Difficulty.Rating sixes = rateDeal(new double[] {6, 6, 6, 6}, 24);
        Difficulty.Rating ordinary = rateDeal(new double[] {2, 4, 7, 10}, 24);
        Difficulty.Rating fraction = rateDeal(new double[] {5, 5, 5, 1}, 24);

        Assert.assertEquals(2, spareOne.ideas);
        Assert.assertEquals(15, spareOne.lines);
        Assert.assertTrue(sixes.difficulty < spareOne.difficulty);
        Assert.assertTrue(spareOne.difficulty > 16.0);
        Assert.assertTrue(spareOne.difficulty < 32.0);

        Assert.assertEquals(2, sixes.ideas);
        Assert.assertEquals(2, sixes.lines);
        Assert.assertEquals(4, ordinary.ideas);
        Assert.assertEquals(ordinary.lines, ordinary.ideas);
        Assert.assertTrue(sixes.difficulty < ordinary.difficulty);
        Assert.assertEquals(1, fraction.ideas);
        Assert.assertEquals(26.6060643390 * 7.0 / 3.0, fraction.difficulty, 0.05);
        Assert.assertTrue(sixes.difficulty < fraction.difficulty);

        Difficulty.Rating triple = rateDeal(new double[] {3, 3, 3, 8}, 24);
        Difficulty.Rating pairs = rateDeal(new double[] {7, 7, 3, 3}, 24);
        Assert.assertTrue(triple.difficulty < pairs.difficulty);
    }

    @Test
    public void repeatWeightsMatchThePiles() {
        Assert.assertEquals(4.0, Difficulty.repeatFactor(new double[] {9, 8, 7, 1}), TOLERANCE);
        Assert.assertEquals(5.0 / 4.0, Difficulty.repeatFactor(new double[] {6, 6, 6, 6}), TOLERANCE);
        Assert.assertEquals(1.0 + 4.0 / 3.0, Difficulty.repeatFactor(new double[] {8, 3, 3, 3}), TOLERANCE);
        Assert.assertEquals(1.5 + 1.5, Difficulty.repeatFactor(new double[] {7, 3, 7, 3}), TOLERANCE);
        Assert.assertEquals(1.0, Difficulty.repeatFactor(new double[] {1}), TOLERANCE);
    }

    private static Difficulty.Rating rateDeal(double[] values, double goal) {
        List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        new Solver(values.length).findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, hits::add, null, true);
        return Difficulty.rate(hits, values);
    }

    private static double cost(double[] values, int op) {
        return cost(values, new int[] {0, 1}, new int[] {op}, bits(true, true, false));
    }

    private static double cost(double[] values, int[] vars, int[] ops, boolean[] order) {
        return Difficulty.solutionCost(rpn(vars, ops, order), values);
    }

    private static double eval(double[] values, int[] vars, int[] ops, boolean[] order) {
        return rpn(vars, ops, order).evaluateWithValues(values, Solver.ROUNDING);
    }

    private static Expression rpn(int[] vars, int[] ops, boolean[] order) {
        byte[] valueOrder = new byte[vars.length];
        byte[] operations = new byte[ops.length];
        for (int i = 0; i < vars.length; i++) {
            valueOrder[i] = (byte) vars[i];
        }
        for (int i = 0; i < ops.length; i++) {
            operations[i] = (byte) ops[i];
        }
        return new Expression(valueOrder, operations, order);
    }

    private static boolean[] bits(boolean... values) {
        return values;
    }
}
