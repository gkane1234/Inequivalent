package com.github.gkane1234;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class IdentityDedupeTest {
    private static final int ADD = ComSearchGenerator.OP_ADD;
    private static final int SUB = ComSearchGenerator.OP_SUB;
    private static final int MUL = ComSearchGenerator.OP_MUL;
    private static final int DIV = ComSearchGenerator.OP_DIV;

    @Test
    public void parkingAOneIsOneIdea() {
        double[] values = {8, 9, 1, 7};
        String parked = IdentityDedupe.signature(rpn(
                new int[] {0, 1, 2, 3},
                new int[] {ADD, DIV, ADD},
                bits(true, true, false, true, false, true, false)), values);
        String multiplied = IdentityDedupe.signature(rpn(
                new int[] {2, 0, 1, 3},
                new int[] {ADD, MUL, ADD},
                bits(true, true, true, false, false, true, false)), values);
        String dividedEarly = IdentityDedupe.signature(rpn(
                new int[] {3, 0, 1, 2},
                new int[] {ADD, ADD, DIV},
                bits(true, true, false, true, false, true, false)), values);
        String dividedLeaf = IdentityDedupe.signature(rpn(
                new int[] {3, 0, 1, 2},
                new int[] {DIV, ADD, ADD},
                bits(true, true, true, true, false, false, false)), values);
        String usedAsAddend = IdentityDedupe.signature(rpn(
                new int[] {2, 1, 3, 0},
                new int[] {ADD, SUB, MUL},
                bits(true, true, false, true, false, true, false)), values);

        Assert.assertEquals("(7+8+9) || 1", parked);
        Assert.assertEquals(parked, multiplied);
        Assert.assertEquals(parked, dividedEarly);
        Assert.assertEquals(parked, dividedLeaf);
        Assert.assertNotEquals(parked, usedAsAddend);
    }

    @Test
    public void zeroAndOneMarksMoveIndependently() {
        double[] values = {5, 1, 0};
        String timesThenAdd = IdentityDedupe.signature(rpn(
                new int[] {0, 1, 2},
                new int[] {MUL, ADD},
                bits(true, true, false, true, false)), values);
        String addThenTimes = IdentityDedupe.signature(rpn(
                new int[] {0, 2, 1},
                new int[] {ADD, MUL},
                bits(true, true, false, true, false)), values);
        String oneThenFive = IdentityDedupe.signature(rpn(
                new int[] {1, 2, 0},
                new int[] {ADD, MUL},
                bits(true, true, false, true, false)), values);

        Assert.assertEquals("5 || 0 || 1", timesThenAdd);
        Assert.assertEquals(timesThenAdd, addThenTimes);
        Assert.assertEquals(timesThenAdd, oneThenFive);
    }

    @Test
    public void twoOnesAreNotTheSameAsSubtractingThem() {
        double[] values = {8, 3, 1, 1};
        String bothFactors = IdentityDedupe.signature(rpn(
                new int[] {0, 1, 2, 3},
                new int[] {MUL, MUL, MUL},
                bits(true, true, false, true, false, true, false)), values);
        String splitFactors = IdentityDedupe.signature(rpn(
                new int[] {0, 2, 1, 3},
                new int[] {MUL, MUL, MUL},
                bits(true, true, false, true, true, false, false)), values);
        String cancelled = IdentityDedupe.signature(rpn(
                new int[] {0, 1, 2, 3},
                new int[] {MUL, SUB, ADD},
                bits(true, true, false, true, true, false, false)), values);

        Assert.assertEquals("(3*8) || 1 || 1", bothFactors);
        Assert.assertEquals(bothFactors, splitFactors);
        Assert.assertEquals("(3*8) || (1-1)", cancelled);
        Assert.assertNotEquals(bothFactors, cancelled);
    }

    @Test
    public void aComputedUnitKeepsItsSubtree() {
        double[] values = {8, 3, 3, 3};
        String quotientOnTheRight = IdentityDedupe.signature(rpn(
                new int[] {0, 1, 2, 3},
                new int[] {MUL, DIV, MUL},
                bits(true, true, false, true, true, false, false)), values);
        String quotientInTheMiddle = IdentityDedupe.signature(rpn(
                new int[] {0, 2, 3, 1},
                new int[] {DIV, MUL, MUL},
                bits(true, true, true, false, false, true, false)), values);
        String difference = IdentityDedupe.signature(rpn(
                new int[] {0, 1, 2, 3},
                new int[] {MUL, SUB, ADD},
                bits(true, true, false, true, true, false, false)), values);
        String cancelledAcross = IdentityDedupe.signature(rpn(
                new int[] {1, 2, 0, 3},
                new int[] {MUL, MUL, DIV},
                bits(true, true, false, true, false, true, false)), values);

        Assert.assertEquals("(3*8) || (3/3)", quotientOnTheRight);
        Assert.assertEquals(quotientOnTheRight, quotientInTheMiddle);
        Assert.assertEquals("(3*8) || (3-3)", difference);
        Assert.assertNotEquals(quotientOnTheRight, difference);
        Assert.assertNotEquals(quotientOnTheRight, cancelledAcross);
    }

    @Test
    public void nineEightSevenOneHasTwoIdeas() {
        Map<String, List<String>> groups = group(new double[] {9, 8, 7, 1}, 24);
        Assert.assertEquals(2, groups.size());
        int[] sizes = sizes(groups);
        Assert.assertEquals(14, sizes[0]);
        Assert.assertEquals(1, sizes[1]);
        String big = null;
        String small = null;
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() == 14) {
                big = entry.getKey();
            } else {
                small = entry.getKey();
            }
        }
        Assert.assertEquals("(7+8+9) || 1", big);
        Assert.assertFalse(small.contains("||"));
    }

    @Test
    public void sixesStayTwoIdeas() {
        Map<String, List<String>> groups = group(new double[] {6, 6, 6, 6}, 24);
        Assert.assertEquals(2, groups.size());
        for (List<String> lines : groups.values()) {
            Assert.assertEquals(1, lines.size());
        }
    }

    @Test
    public void distinctIdeasWithoutAUnitStaySplit() {
        Map<String, List<String>> groups = group(new double[] {2, 4, 7, 10}, 24);
        int lines = 0;
        for (List<String> group : groups.values()) {
            lines += group.size();
        }
        Assert.assertEquals(lines, groups.size());
        Assert.assertTrue(groups.size() >= 4);
    }

    private static Map<String, List<String>> group(double[] values, double goal) {
        List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        new Solver(values.length).findSolutionsStreaming(
                values, goal, Integer.MAX_VALUE, hits::add, null, true);
        Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
        for (EvaluatedExpression hit : hits) {
            String key = IdentityDedupe.signature(hit, values);
            List<String> lines = groups.get(key);
            if (lines == null) {
                lines = new ArrayList<String>();
                groups.put(key, lines);
            }
            lines.add(hit.display());
        }
        return groups;
    }

    private static int[] sizes(Map<String, List<String>> groups) {
        int[] sizes = new int[groups.size()];
        int i = 0;
        for (List<String> lines : groups.values()) {
            sizes[i++] = lines.size();
        }
        java.util.Arrays.sort(sizes);
        for (int left = 0, right = sizes.length - 1; left < right; left++, right--) {
            int tmp = sizes[left];
            sizes[left] = sizes[right];
            sizes[right] = tmp;
        }
        return sizes;
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
