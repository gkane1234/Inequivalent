package com.github.gkane1234;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class UnorderedPartitionTest {

    @Test
    public void partitionCountsMatchBellMinusOne() {
        // B_n - 1 (exclude the single-block partition)
        Assert.assertEquals(1, countPartitions(2));   // B2=2
        Assert.assertEquals(4, countPartitions(3));   // B3=5
        Assert.assertEquals(14, countPartitions(4));  // B4=15
        Assert.assertEquals(51, countPartitions(5));  // B5=52
        Assert.assertEquals(202, countPartitions(6)); // B6=203
    }

    @Test
    public void largeNDoesNotStackOverflowOnFirstPartitions() {
        int n = 88;
        int[] leaves = new int[n];
        for (int i = 0; i < n; i++) {
            leaves[i] = i;
        }
        int[] seen = {0};
        try {
            UnorderedPartition.forEach(leaves, p -> {
                seen[0]++;
                if (seen[0] >= 20) {
                    throw new RuntimeException("stop");
                }
            });
        } catch (RuntimeException e) {
            Assert.assertEquals("stop", e.getMessage());
        }
        Assert.assertEquals(20, seen[0]);
    }

    @Test
    public void searchN88EmitsCandidatesWithoutStackOverflow() {
        double[] values = new double[88];
        for (int i = 0; i < 88; i++) {
            values[i] = i + 1;
        }
        int[] seen = {0};
        try {
            new ComSearchGenerator(88).search(values, (raw, mat) -> {
                seen[0]++;
                if (seen[0] >= 100) {
                    throw new RuntimeException("stop");
                }
            }, 1);
        } catch (RuntimeException e) {
            Assert.assertEquals("stop", e.getMessage());
        }
        Assert.assertTrue(seen[0] >= 100);
    }

    private static int countPartitions(int n) {
        List<List<int[]>> all = new ArrayList<>();
        UnorderedPartition.forEach(n, all::add);
        return all.size();
    }
}
