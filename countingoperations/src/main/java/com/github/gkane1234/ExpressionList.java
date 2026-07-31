package com.github.gkane1234;

/**
 * Simple list of inequivalent expressions produced by {@link ComSearchGenerator}.
 */
public class ExpressionList {
    private final Expression[] expressions;
    private final int numExpressions;
    private final int numValues;

    public ExpressionList(Expression[] expressions, int numExpressions, int numValues) {
        this.expressions = expressions;
        this.numExpressions = numExpressions;
        this.numValues = numValues;
    }

    public int getNumValues() {
        return numValues;
    }

    public int getNumExpressions() {
        return numExpressions;
    }

    public Expression get(int index) {
        return expressions[index];
    }
}
