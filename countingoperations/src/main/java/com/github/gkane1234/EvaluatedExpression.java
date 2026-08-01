package com.github.gkane1234;

import java.util.Arrays;
/**
    This class is used to represent a solution to a goal.

    This does not verify that the solution is correct, it is only a way to store a solution.
*/
public class EvaluatedExpression extends Expression{
    private static final int DECIMAL_PLACES = 3;
    private double[] values;
    private double goal;
    /**
        Constructor for the EvaluatedExpression class.
        @param expression: an <code>Expression</code> representing the expression used to get the solution.
        @param values: a <code>double[]</code> representing the values used in the expression.
        @param goal: a <code>double</code> representing the goal of the solution.
    */
    public EvaluatedExpression(Expression expression, double[] values,double goal) {
        super(expression);
        this.values=values;
        this.goal = goal;
    }
    /**
        Displays the EvaluatedExpression as a string.
        @return a <code>String</code> representing the EvaluatedExpression.
    */
    public String display() {
        return Expression.formatInfix(this, i -> {
            double next = this.values[this.valueOrder[i] & 0xff];
            if (next == Math.round(next)) {
                return String.valueOf(Math.round(next));
            }
            return String.format("%." + DECIMAL_PLACES + "f", next);
        });
    }
    /**
        Returns the values used in the expression.
        @return a <code>double[]</code> representing the values used in the expression.
    */
    public double[] getValues() {
        return this.values;
    }
    /**
        Returns the evaluated value of the EvaluatedExpression.
        @return a <code>double</code> representing the value of the EvaluatedExpression.
    */
    public double getValue() {
        return this.goal;
    }

    /**
        Checks if two EvaluatedExpressions are the same.
        @param o: an <code>Object</code> representing the EvaluatedExpression to compare to.
        @return a <code>boolean</code> representing whether the EvaluatedExpressions are the same.
    */
    @Override
    public boolean equals(Object o) {
        if (o instanceof EvaluatedExpression) {
            EvaluatedExpression other = (EvaluatedExpression) o;
            if (Arrays.equals(other.operations, this.operations) && Arrays.equals(other.order, this.order)) {
                for (int i=0;i<this.values.length;i++) { // two equivalent evaluated expressions can have different value orderings if there are repeats in the values.
                    if (this.values[this.valueOrder[i]]!=other.values[other.valueOrder[i]]) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
    /**
        Returns a string representation of the EvaluatedExpression.
        @return a <code>String</code> representing the EvaluatedExpression.
    */
    @Override
    public String toString() {
        return this.display();
    }
    @Override
    public int hashCode() {
        double[] valuesInOrder = new double[this.values.length];
        for(int i =0;i<this.values.length;i++) {
            valuesInOrder[this.valueOrder[i]] = this.values[i];
        }
        return Arrays.hashCode(this.operations)+Arrays.hashCode(this.order)+Arrays.hashCode(valuesInOrder);
    }

}
