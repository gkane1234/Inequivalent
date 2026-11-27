package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Difficulty bands for the 24 game.
 *
 * <p>Enumerate every nondecreasing {@code count}-tuple with values in
 * {@code [min, max]}, rate the boards that hit the goal, then cut the sorted
 * difficulties at the given percentiles. Default cutoffs are the bottom 60%
 * (easy), 60–85% (medium), 85–95% (hard), and 95%+ (hardest). Re-run after the
 * difficulty equation changes; the web app only needs the resulting numbers.
 */
public final class DifficultyStrata {
    public static final double DEFAULT_EASY_PERCENTILE = 0.60;
    public static final double DEFAULT_MEDIUM_PERCENTILE = 0.85;
    public static final double DEFAULT_HARD_PERCENTILE = 0.95;
    public static final int DEFAULT_MIN = 1;
    public static final int DEFAULT_MAX = 13;
    public static final int DEFAULT_COUNT = 4;
    public static final double DEFAULT_GOAL = 24.0;

    /** Bounds from the 1–13 four-number sweep with the current difficulty equation. */
    public static final Bounds DEFAULT_BOUNDS = new Bounds(27.413335, 48.099017, 61.534586);

    private DifficultyStrata() {}

    public enum Band {
        EASY("easy"),
        MEDIUM("medium"),
        HARD("hard"),
        HARDEST("hardest");

        public final String id;

        Band(String id) {
            this.id = id;
        }

        public static Band fromId(String id) {
            for (Band band : values()) {
                if (band.id.equals(id)) {
                    return band;
                }
            }
            throw new IllegalArgumentException("Unknown difficulty " + id);
        }
    }

    public static final class Bounds {
        /** Inclusive upper end of easy (p60). */
        public final double easyMax;
        /** Inclusive upper end of medium (p85). */
        public final double mediumMax;
        /** Inclusive upper end of hard (p95). Hardest is above this. */
        public final double hardMax;

        public Bounds(double easyMax, double mediumMax, double hardMax) {
            if (!(easyMax <= mediumMax) || !(mediumMax <= hardMax)) {
                throw new IllegalArgumentException(
                        "Need easyMax ≤ mediumMax ≤ hardMax, got "
                                + easyMax + ", " + mediumMax + ", " + hardMax);
            }
            this.easyMax = easyMax;
            this.mediumMax = mediumMax;
            this.hardMax = hardMax;
        }

        public Band bandFor(double difficulty) {
            if (!Double.isFinite(difficulty) || !(difficulty > 0)) {
                return null;
            }
            if (difficulty <= easyMax) {
                return Band.EASY;
            }
            if (difficulty <= mediumMax) {
                return Band.MEDIUM;
            }
            if (difficulty <= hardMax) {
                return Band.HARD;
            }
            return Band.HARDEST;
        }

        public boolean matches(double difficulty, Band band) {
            return bandFor(difficulty) == band;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "easy≤%.6f medium≤%.6f hard≤%.6f hardest>%.6f",
                    easyMax, mediumMax, hardMax, hardMax);
        }
    }

    public static final class Result {
        public final Bounds bounds;
        public final int boards;
        public final int solvable;
        public final double easyPercentile;
        public final double mediumPercentile;
        public final double hardPercentile;
        public final double minDifficulty;
        public final double maxDifficulty;

        Result(Bounds bounds, int boards, int solvable,
                double easyPercentile, double mediumPercentile, double hardPercentile,
                double minDifficulty, double maxDifficulty) {
            this.bounds = bounds;
            this.boards = boards;
            this.solvable = solvable;
            this.easyPercentile = easyPercentile;
            this.mediumPercentile = mediumPercentile;
            this.hardPercentile = hardPercentile;
            this.minDifficulty = minDifficulty;
            this.maxDifficulty = maxDifficulty;
        }
    }

    /**
     * Linear interpolation percentile on a sorted ascending list.
     * {@code percentile} is in {@code [0, 1]}.
     */
    public static double percentile(List<Double> sortedAscending, double percentile) {
        if (sortedAscending == null || sortedAscending.isEmpty()) {
            return Double.NaN;
        }
        if (percentile <= 0) {
            return sortedAscending.get(0);
        }
        if (percentile >= 1) {
            return sortedAscending.get(sortedAscending.size() - 1);
        }
        double pos = percentile * (sortedAscending.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return sortedAscending.get(lo);
        }
        double left = sortedAscending.get(lo);
        double right = sortedAscending.get(hi);
        return left + (pos - lo) * (right - left);
    }

    public static Result compute() {
        return compute(DEFAULT_MIN, DEFAULT_MAX, DEFAULT_COUNT, DEFAULT_GOAL,
                DEFAULT_EASY_PERCENTILE, DEFAULT_MEDIUM_PERCENTILE, DEFAULT_HARD_PERCENTILE, null);
    }

    public static Result compute(int min, int max, int count, double goal,
            double easyPercentile, double mediumPercentile, double hardPercentile,
            IntConsumer onBoard) {
        if (min > max) {
            throw new IllegalArgumentException("Range min must be ≤ max.");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be ≥ 1");
        }
        if (!(easyPercentile >= 0) || !(mediumPercentile >= easyPercentile)
                || !(hardPercentile >= mediumPercentile) || !(hardPercentile <= 1)) {
            throw new IllegalArgumentException(
                    "Need 0 ≤ easy ≤ medium ≤ hard ≤ 1 for percentiles.");
        }

        List<Double> difficulties = new ArrayList<Double>();
        Solver solver = new Solver(count);
        int[] tuple = new int[count];
        int[] boards = {0};
        walk(min, max, count, 0, min, tuple, goal, solver, difficulties, onBoard, boards);
        int solvable = difficulties.size();
        if (solvable == 0) {
            throw new IllegalStateException("No solvable boards in [" + min + ", " + max + "]");
        }
        Collections.sort(difficulties);
        Bounds bounds = new Bounds(
                percentile(difficulties, easyPercentile),
                percentile(difficulties, mediumPercentile),
                percentile(difficulties, hardPercentile));
        return new Result(bounds, boards[0], solvable, easyPercentile, mediumPercentile, hardPercentile,
                difficulties.get(0), difficulties.get(difficulties.size() - 1));
    }

    private static void walk(int min, int max, int count, int index, int start, int[] tuple,
            double goal, Solver solver, List<Double> difficulties, IntConsumer onBoard, int[] boards) {
        if (index == count) {
            boards[0]++;
            if (onBoard != null) {
                onBoard.accept(boards[0]);
            }
            double[] values = new double[count];
            for (int i = 0; i < count; i++) {
                values[i] = tuple[i];
            }
            List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
            solver.findSolutionsStreaming(values, goal, Integer.MAX_VALUE, hits::add, null, true);
            if (!hits.isEmpty()) {
                double difficulty = Difficulty.rate(hits, values).difficulty;
                if (Double.isFinite(difficulty)) {
                    difficulties.add(difficulty);
                }
            }
            return;
        }
        for (int value = start; value <= max; value++) {
            tuple[index] = value;
            walk(min, max, count, index + 1, value, tuple, goal, solver, difficulties, onBoard, boards);
        }
    }

    /** Prints the numerical bounds the web app should hard-code. */
    public static void main(String[] args) {
        int min = DEFAULT_MIN;
        int max = DEFAULT_MAX;
        double easy = DEFAULT_EASY_PERCENTILE;
        double medium = DEFAULT_MEDIUM_PERCENTILE;
        double hard = DEFAULT_HARD_PERCENTILE;
        for (int i = 0; i < args.length; i++) {
            if ("--min".equals(args[i]) && i + 1 < args.length) {
                min = Integer.parseInt(args[++i]);
            } else if ("--max".equals(args[i]) && i + 1 < args.length) {
                max = Integer.parseInt(args[++i]);
            } else if ("--easy".equals(args[i]) && i + 1 < args.length) {
                easy = Double.parseDouble(args[++i]);
            } else if ("--medium".equals(args[i]) && i + 1 < args.length) {
                medium = Double.parseDouble(args[++i]);
            } else if ("--hard".equals(args[i]) && i + 1 < args.length) {
                hard = Double.parseDouble(args[++i]);
            }
        }
        System.out.println("Scanning nondecreasing 4-tuples in [" + min + ", " + max + "] → 24…");
        Result result = compute(min, max, DEFAULT_COUNT, DEFAULT_GOAL, easy, medium, hard, boards -> {
            if (boards % 200 == 0) {
                System.out.print(".");
                System.out.flush();
            }
        });
        System.out.println();
        System.out.printf(Locale.US, "boards=%d solvable=%d  difficulty [%.6f, %.6f]%n",
                result.boards, result.solvable, result.minDifficulty, result.maxDifficulty);
        System.out.printf(Locale.US, "percentiles easy=%.0f%% medium=%.0f%% hard=%.0f%%%n",
                result.easyPercentile * 100, result.mediumPercentile * 100, result.hardPercentile * 100);
        System.out.println(result.bounds);
        System.out.printf(Locale.US,
                "web DIFFICULTY_BOUNDS: { easyMax: %.6f, mediumMax: %.6f, hardMax: %.6f }%n",
                result.bounds.easyMax, result.bounds.mediumMax, result.bounds.hardMax);
    }
}
