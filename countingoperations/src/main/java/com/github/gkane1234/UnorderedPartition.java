package com.github.gkane1234;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public class UnorderedPartition {
    /** Partitions {0..n-1} into g &gt;= 2 blocks. */
    public static void forEach(int n, Consumer<List<int[]>> consumer) {
        if (n < 2) {
            return;
        }
        int[] leaves = new int[n];
        for (int i = 0; i < n; i++) {
            leaves[i] = i;
        }
        forEach(leaves, consumer);
    }

    /**
     * Partitions the given leaf ids into g &gt;= 2 blocks.
     * Block contents are values from {@code leaves}, not necessarily 0..n-1.
     */
    public static void forEach(int[] leaves, Consumer<List<int[]>> consumer) {
        if (leaves.length < 2) {
            return;
        }
        List<List<Integer>> blocks = new ArrayList<>();
        place(0, leaves, blocks, consumer);
    }

    private static void place(int i, int[] leaves, List<List<Integer>> blocks, Consumer<List<int[]>> consumer) {
        if (i == leaves.length) {
            if (blocks.size() >= 2) {
                consumer.accept(copy(blocks));
            }
            return;
        }

        int leaf = leaves[i];
        int numBlocks = blocks.size();
        for (int b = 0; b < numBlocks; b++) {
            List<Integer> block = blocks.get(b);
            block.add(leaf);
            place(i + 1, leaves, blocks, consumer);
            block.remove(block.size() - 1);
        }
        List<Integer> toAdd = new ArrayList<>();
        toAdd.add(leaf);
        blocks.add(toAdd);
        place(i + 1, leaves, blocks, consumer);
        blocks.remove(blocks.size() - 1);
    }

    private static List<int[]> copy(List<List<Integer>> out){

        List<int[]> part = new ArrayList<int[]>();
        for (List<Integer> block : out) {
            part.add(block.stream().mapToInt(Integer::intValue).toArray());
        }
        
        return part;

    }

    public static void main(String[] args) {
        UnorderedPartition.forEach(100, p -> System.out.println(
            p.stream().map(java.util.Arrays::toString).toList()));
    }

}
