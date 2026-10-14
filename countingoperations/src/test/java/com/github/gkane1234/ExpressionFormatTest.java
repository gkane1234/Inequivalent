package com.github.gkane1234;

import org.junit.Assert;
import org.junit.Test;

public class ExpressionFormatTest {

    @Test
    public void omitsRedundantParensForLeftAssocAdd() {
        // (0+1)+2
        Expression e = Expression.combineWithOp(
                Expression.combineWithOp(leaf(0), leaf(1), (byte) 0),
                leaf(2), (byte) 0);
        Assert.assertEquals("0+1+2", e.toString());
    }

    @Test
    public void keepsParensWhenPrecedenceRequires() {
        // (0+1)*2
        Expression e = Expression.combineWithOp(
                Expression.combineWithOp(leaf(0), leaf(1), (byte) 0),
                leaf(2), (byte) 2);
        Assert.assertEquals("(0+1)*2", e.toString());
    }

    @Test
    public void keepsParensForRightNestedSubtraction() {
        // 0-(1-2)
        Expression e = Expression.combineWithOp(
                leaf(0),
                Expression.combineWithOp(leaf(1), leaf(2), (byte) 1),
                (byte) 1);
        Assert.assertEquals("0-(1-2)", e.toString());
    }

    @Test
    public void displayUsesValuesWithoutParenWall() {
        Expression e = Expression.combineWithOp(
                Expression.combineWithOp(leaf(0), leaf(1), (byte) 2),
                leaf(2), (byte) 0);
        EvaluatedExpression ev = new EvaluatedExpression(e, new double[] {2, 4, 7}, 15);
        Assert.assertEquals("2*4+7", ev.display());
    }

    private static Expression leaf(int i) {
        return new Expression(new byte[] {(byte) i}, new byte[] {}, new boolean[] {true});
    }
}
