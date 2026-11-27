package com.github.gkane1234;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Classic 24-game deals and banded puzzle search using {@link DifficultyStrata}.
 */
public final class Game24 {
    public static final double GOAL = 24.0;
    public static final int COUNT = 4;
    public static final int DEAL_MIN = 1;
    public static final int DEAL_MAX = 9;

    private Game24() {}

    public static final class Puzzle {
        public final double[] values;
        public final List<EvaluatedExpression> solutions;
        public final DifficultyStrata.Band band;
        public final Difficulty.Rating rating;
        public final int attempts;

        Puzzle(double[] values, List<EvaluatedExpression> solutions,
                DifficultyStrata.Band band, Difficulty.Rating rating, int attempts) {
            this.values = values;
            this.solutions = solutions;
            this.band = band;
            this.rating = rating;
            this.attempts = attempts;
        }
    }

    public static double[] deal(Random random) {
        double[] values = new double[COUNT];
        for (int i = 0; i < COUNT; i++) {
            values[i] = DEAL_MIN + random.nextInt(DEAL_MAX - DEAL_MIN + 1);
        }
        return values;
    }

    public static Puzzle find(DifficultyStrata.Band band, DifficultyStrata.Bounds bounds, Random random) {
        return find(band, bounds, random, 400, null);
    }

    public static Puzzle find(DifficultyStrata.Band band, DifficultyStrata.Bounds bounds, Random random,
            int maxAttempts, Consumer<double[]> onAttempt) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds required");
        }
        Solver solver = new Solver(COUNT);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            double[] values = deal(random);
            if (onAttempt != null) {
                onAttempt.accept(values);
            }
            List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
            solver.findSolutionsStreaming(values, GOAL, Integer.MAX_VALUE, hits::add, null, true);
            if (hits.isEmpty()) {
                continue;
            }
            Difficulty.Rating rating = Difficulty.rate(hits, values);
            if (bounds.matches(rating.difficulty, band)) {
                return new Puzzle(values, hits, band, rating, attempt);
            }
        }
        throw new IllegalStateException(
                "No " + band.id + " 24-game puzzle found in " + maxAttempts + " deals");
    }
}
