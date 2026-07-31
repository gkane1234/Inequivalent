package com.github.gkane1234;

import java.util.function.Consumer;

/**
 * Streams ComSearch expressions and reports those that hit the goal.
 * Supports cooperative cancellation so a UI can stop without wiping results.
 */
public class Solver {
    public static final int ROUNDING = 9;
    private static final double TOLERANCE = 1e-5;

    private final int numValues;
    private final ComSearchGenerator generator;

    public Solver(int numValues) {
        if (numValues < 1) {
            throw new IllegalArgumentException("numValues must be >= 1");
        }
        this.numValues = numValues;
        this.generator = new ComSearchGenerator(numValues);
    }

    public int getNumValues() {
        return numValues;
    }

    public void requestStop() {
        generator.cancel();
    }

    /**
     * Enumerate all ComSearch expressions, invoke {@code onFound} for each match.
     * Returns when generation finishes or {@link #requestStop()} is honored.
     *
     * @return number of solutions reported (may be less than total matches if stopped
     *         after {@code maxSolutions})
     */
    public int findSolutionsStreaming(double[] values, double goal, int maxSolutions,
                                      Consumer<EvaluatedExpression> onFound) {
        if (values.length != numValues) {
            throw new IllegalArgumentException(
                    "expected " + numValues + " values, got " + values.length);
        }
        int[] found = {0};
        generator.generate(expression -> {
            if (found[0] >= maxSolutions) {
                generator.cancel();
                return;
            }
            double value = expression.evaluateWithValues(values, ROUNDING);
            if (equal(value, goal)) {
                onFound.accept(new EvaluatedExpression(expression, values, value));
                found[0]++;
                if (found[0] >= maxSolutions) {
                    generator.cancel();
                }
            }
        });
        return found[0];
    }

    public ExpressionList getExpressionList() {
        return generator.toExpressionList();
    }

    public static boolean equal(double a, double b) {
        return Math.abs(a - b) <= TOLERANCE;
    }
}
