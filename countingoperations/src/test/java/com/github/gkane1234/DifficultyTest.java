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
    public void productLayerDividesBeforeMultiplyingWhenThatIsCheaper() {
        Expression nine = new Expression(new byte[] {0}, new byte[0], new boolean[] {true});
        Expression thirteen = new Expression(new byte[] {1}, new byte[0], new boolean[] {true});
        Expression twelve = new Expression(new byte[] {2}, new byte[0], new boolean[] {true});
        Expression eleven = new Expression(new byte[] {3}, new byte[0], new boolean[] {true});
        Expression sum = Expression.combineWithOp(nine, thirteen, (byte) ADD);
        Expression product = Expression.combineWithOp(sum, twelve, (byte) MUL);
        Expression line = Expression.combineWithOp(product, eleven, (byte) DIV);
        double[] values = {9, 13, 12, 11};
        Difficulty.LineTrace trace = Difficulty.trace(line, values, 24);
        boolean dividedToTwo = false;
        for (int i = 0; i < trace.steps.size(); i++) {
            if ("2".equals(trace.steps.get(i).resultText)) {
                dividedToTwo = true;
            }
        }
        Assert.assertTrue(dividedToTwo);
        Assert.assertTrue(trace.text.indexOf("/11") < trace.text.indexOf("*12")
                || trace.text.contains(")*12") || trace.text.startsWith("12*"));
    }

    @Test
    public void oneStepChargesTheTwoOperands() {
        double eight = 0.5 * log2(1 + 8);
        double three = 0.5 * log2(1 + 3);
        Assert.assertEquals(eight + three, cost(new double[] {8, 3}, MUL), TOLERANCE);
        Assert.assertEquals(0.0, cost(new double[] {0, 0}, ADD), TOLERANCE);
        Assert.assertTrue(cost(new double[] {8, 3}, MUL) < cost(new double[] {53, 27}, SUB));
    }

    @Test
    public void parametersPriceOperationsArgumentsAndIntermediates() {
        Expression product = rpn(new int[] {0, 1}, new int[] {MUL}, bits(true, true, false));
        double[] eightThree = {8, 3};
        double productCost = Difficulty.solutionCost(product, eightThree);
        Difficulty.Parameters times = new Difficulty.Parameters();
        times.multiplyCost = 5;
        Assert.assertEquals(productCost * 5, Difficulty.solutionCost(product, eightThree, times), 1e-9);

        Expression difference = rpn(new int[] {0, 1}, new int[] {SUB}, bits(true, true, false));
        double[] eightMinusThree = {8, 3};
        Difficulty.LineTrace plain = Difficulty.trace(difference, eightMinusThree);
        Difficulty.Parameters heavyRight = new Difficulty.Parameters();
        heavyRight.subtractRight = 2;
        Assert.assertEquals(plain.cost + plain.steps.get(0).right.cost,
                Difficulty.solutionCost(difference, eightMinusThree, heavyRight), 1e-9);

        Expression quotient = rpn(new int[] {0, 1}, new int[] {DIV}, bits(true, true, false));
        double[] sevenOverThree = {7, 3};
        Difficulty.Parameters divisor = new Difficulty.Parameters();
        divisor.divideRight = 3;
        Difficulty.LineTrace division = Difficulty.trace(quotient, sevenOverThree);
        Assert.assertEquals(division.cost + 2 * division.steps.get(0).right.cost,
                Difficulty.solutionCost(quotient, sevenOverThree, divisor), 1e-9);
        Assert.assertEquals(0.0, division.steps.get(0).result.cost, 0.0);
    }

    @Test
    public void traceSplitsAStepAndAReusedValue() {
        Expression product = rpn(new int[] {0, 1}, new int[] {MUL}, bits(true, true, false));
        double[] eightThree = {8, 3};
        Difficulty.LineTrace once = Difficulty.trace(product, eightThree);
        Assert.assertEquals(1, once.steps.size());
        Difficulty.Step step = once.steps.get(0);
        Assert.assertEquals('*', step.symbol);
        Assert.assertTrue(step.left.familiar);
        Assert.assertTrue(step.right.familiar);
        Assert.assertFalse(step.result.familiar);
        Assert.assertEquals(step.left.cost + step.right.cost, step.cost, 0.0);
        Assert.assertEquals(0.0, step.result.cost, 0.0);
        Assert.assertEquals(Difficulty.solutionCost(product, eightThree), step.cost, TOLERANCE);

        Expression reused = rpn(new int[] {0, 1, 2}, new int[] {MUL, MUL},
                bits(true, true, false, true, false));
        double[] eightThreeThree = {8, 3, 3};
        Difficulty.LineTrace twice = Difficulty.trace(reused, eightThreeThree);
        Assert.assertEquals(2, twice.steps.size());
        Assert.assertTrue(twice.steps.get(1).left.familiar != twice.steps.get(1).right.familiar);
        double sum = 0.0;
        for (Difficulty.Step traced : twice.steps) {
            sum += traced.cost;
        }
        Assert.assertEquals(Difficulty.solutionCost(reused, eightThreeThree), sum, 0.0);
    }

    @Test
    public void explainMatchesTheDealRating() {
        double[] values = {5, 5, 5, 1};
        List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        new Solver(4, 1).findSolutionsStreaming(
                values, 24, Integer.MAX_VALUE, hits::add, null, true);
        Difficulty.DealTrace deal = Difficulty.explain(hits, values, 24);
        Difficulty.Rating rating = Difficulty.rate(hits, values, 24);
        Assert.assertEquals(rating.difficulty, deal.rating.difficulty, 1e-9);
        Assert.assertEquals(rating.ideas, deal.rating.ideas);
        Assert.assertEquals(rating.lines, deal.rating.lines);
        String text = Difficulty.formatExplain(deal);
        Assert.assertTrue(text.contains("Ideas combine"));
        Assert.assertTrue(text.contains("goal"));
        for (Difficulty.IdeaTrace idea : deal.ideas) {
            double sum = 0.0;
            for (Difficulty.Step step : idea.line.steps) {
                sum += step.cost;
            }
            Assert.assertEquals(idea.line.cost, sum, 1e-9);
        }
    }

    @Test
    public void floorAndParallelSum() {
        Expression zero = rpn(new int[] {0, 1}, new int[] {ADD}, bits(true, true, false));
        double[] zeros = {0, 0};
        Assert.assertEquals(0.0, Difficulty.solutionCost(zero, zeros), TOLERANCE);
        Difficulty.Rating one = Difficulty.rate(Arrays.asList(zero), zeros, 0);
        Assert.assertEquals(0.75, one.difficulty, TOLERANCE);
        Assert.assertEquals(1, one.ideas);

        Expression product = rpn(new int[] {0, 1}, new int[] {MUL}, bits(true, true, false));
        Difficulty.Rating both = Difficulty.rate(Arrays.asList(zero, product), zeros, 0);
        Assert.assertEquals(0.375, both.difficulty, TOLERANCE);
        Assert.assertEquals(2, both.ideas);

        Expression sixPlusOne = rpn(new int[] {0, 1}, new int[] {ADD}, bits(true, true, false));
        double[] sixOne = {6, 1};
        Difficulty.Rating repeated = Difficulty.rate(Arrays.asList(sixPlusOne, sixPlusOne), sixOne, 0);
        Assert.assertEquals(
                Difficulty.solutionDifficulty(sixPlusOne, sixOne, 0) * 4.0, repeated.difficulty, TOLERANCE);
        Assert.assertEquals(1, repeated.ideas);
        Assert.assertEquals(2, repeated.lines);

        Assert.assertTrue(Double.isNaN(Difficulty.rate(new ArrayList<Expression>(), new double[0], 0).difficulty));
    }

    @Test
    public void numbersUseOneIdeaAndStayOrdered() {
        Difficulty.Rating spareOne = rateNumbers(new double[] {9, 8, 7, 1}, 24);
        Difficulty.Rating sixes = rateNumbers(new double[] {6, 6, 6, 6}, 24);
        Difficulty.Rating ordinary = rateNumbers(new double[] {2, 4, 7, 10}, 24);
        Difficulty.Rating fraction = rateNumbers(new double[] {5, 5, 5, 1}, 24);

        Assert.assertEquals(2, spareOne.ideas);
        Assert.assertEquals(15, spareOne.lines);
        Assert.assertTrue(sixes.difficulty < spareOne.difficulty);
        Assert.assertTrue(spareOne.difficulty > 0.0);

        Assert.assertEquals(2, sixes.ideas);
        Assert.assertEquals(2, sixes.lines);
        Assert.assertEquals(4, ordinary.ideas);
        Assert.assertEquals(ordinary.lines, ordinary.ideas);
        Assert.assertTrue(sixes.difficulty < ordinary.difficulty);
        Assert.assertEquals(1, fraction.ideas);
        Assert.assertTrue(sixes.difficulty < fraction.difficulty);

        Difficulty.Rating triple = rateNumbers(new double[] {3, 3, 3, 8}, 24);
        Difficulty.Rating pairs = rateNumbers(new double[] {7, 7, 3, 3}, 24);
        Assert.assertTrue(triple.difficulty < pairs.difficulty);
    }

    @Test
    public void repeatWeightsMatchThePiles() {
        Assert.assertEquals(8.0, Difficulty.repeatFactor(new double[] {9, 8, 7, 1}), TOLERANCE);
        Assert.assertEquals(5.0 / 8.0, Difficulty.repeatFactor(new double[] {6, 6, 6, 6}), TOLERANCE);
        Assert.assertEquals(8.0 / 3.0, Difficulty.repeatFactor(new double[] {8, 3, 3, 3}), TOLERANCE);
        Assert.assertEquals(1.5, Difficulty.repeatFactor(new double[] {7, 3, 7, 3}), TOLERANCE);
        Assert.assertEquals(2.0, Difficulty.repeatFactor(new double[] {1}), TOLERANCE);
    }

    private static Difficulty.Rating rateNumbers(double[] values, double goal) {
        List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        new Solver(values.length).findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, hits::add, null, true);
        return Difficulty.rate(hits, values, goal);
    }

    private static double cost(double[] values, int op) {
        return cost(values, new int[] {0, 1}, new int[] {op}, bits(true, true, false));
    }

    private static double cost(double[] values, int[] vars, int[] ops, boolean[] order) {
        return Difficulty.solutionCost(rpn(vars, ops, order), values);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
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
