package com.github.gkane1234;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Unordered partitions of a leaf list into g &gt;= 2 blocks.
 *
 * <p>Implemented iteratively (explicit backtracking state) so large n does not
 * blow the JVM call stack. Nested ComSearch recursion used to StackOverflow
 * around n≈88 on default stacks.
 */
public class UnorderedPartition {
    /** Partitions {0..n-1} into g &gt;= 2 blocks. */
    public static void forEach(int n, Consumer<List<int[]>> consumer) {
        forEach(n, consumer, () -> false);
    }

    public static void forEach(int n, Consumer<List<int[]>> consumer, BooleanSupplier cancelled) {
        if (n < 2) {
            return;
        }
        int[] leaves = new int[n];
        for (int i = 0; i < n; i++) {
            leaves[i] = i;
        }
        forEach(leaves, consumer, cancelled);
    }

    /**
     * Partitions the given leaf ids into g &gt;= 2 blocks.
     * Block contents are values from {@code leaves}, not necessarily 0..n-1.
     */
    public static void forEach(int[] leaves, Consumer<List<int[]>> consumer) {
        forEach(leaves, consumer, () -> false);
    }

    public static void forEach(int[] leaves, Consumer<List<int[]>> consumer, BooleanSupplier cancelled) {
        if (leaves.length < 2) {
            return;
        }
        int n = leaves.length;
        List<List<Integer>> blocks = new ArrayList<>();
        // At depth i: next block index to try (0..blocks.size()-1 = existing, blocks.size() = new).
        int[] tryNext = new int[n];
        // How leaf i was placed: >=0 → existing block index; -1 → created a new block.
        int[] placedIn = new int[n];

        int i = 0;
        tryNext[0] = 0;

        while (i >= 0) {
            if (cancelled.getAsBoolean()) {
                return;
            }

            if (i == n) {
                if (blocks.size() >= 2) {
                    consumer.accept(copy(blocks));
                }
                i--;
                if (i >= 0) {
                    undo(blocks, i, placedIn);
                    tryNext[i]++;
                }
                continue;
            }

            int options = blocks.size() + 1; // existing blocks + "create new"
            if (tryNext[i] >= options) {
                // Exhausted choices at this leaf — backtrack.
                tryNext[i] = 0;
                i--;
                if (i >= 0) {
                    undo(blocks, i, placedIn);
                    tryNext[i]++;
                }
                continue;
            }

            int leaf = leaves[i];
            int choice = tryNext[i];
            if (choice < blocks.size()) {
                blocks.get(choice).add(leaf);
                placedIn[i] = choice;
            } else {
                List<Integer> created = new ArrayList<>();
                created.add(leaf);
                blocks.add(created);
                placedIn[i] = -1;
            }

            i++;
            if (i < n) {
                tryNext[i] = 0;
            }
        }
    }

    private static void undo(List<List<Integer>> blocks, int i, int[] placedIn) {
        if (placedIn[i] >= 0) {
            List<Integer> block = blocks.get(placedIn[i]);
            block.remove(block.size() - 1);
        } else {
            blocks.remove(blocks.size() - 1);
        }
    }

    private static List<int[]> copy(List<List<Integer>> out) {
        List<int[]> part = new ArrayList<>(out.size());
        for (int b = 0; b < out.size(); b++) {
            List<Integer> block = out.get(b);
            int[] arr = new int[block.size()];
            for (int i = 0; i < block.size(); i++) {
                arr[i] = block.get(i);
            }
            part.add(arr);
        }
        return part;
    }

    public static void main(String[] args) {
        UnorderedPartition.forEach(5, p -> System.out.println(
            p.stream().map(java.util.Arrays::toString).toList()));
    }
}
