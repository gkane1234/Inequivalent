package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class RangeSurveyTest {

    @Test
    public void difficultyTextIsTheParallelScore() {
        Assert.assertEquals("", row(0, Double.NaN, 1).difficultyText());
        Assert.assertEquals("1.50", row(2, 1.5, 1, 2).difficultyText());
        Assert.assertEquals("26.61", row(1, 26.606, 5, 5, 5, 1).difficultyText());
    }

    @Test
    public void multisetCountMatchesStarsAndBars() {
        Assert.assertEquals(3L, RangeSurvey.multisetCount(2, 2));
        Assert.assertEquals(715L, RangeSurvey.multisetCount(4, 10));
        Assert.assertEquals(5L, RangeSurvey.multisetCount(4, 2));
    }

    @Test
    public void twoNumberZerosRespectDedupeAndSort() {
        List<RangeSurvey.Row> rows = survey(2, 1, 2, 0, false);
        Assert.assertEquals(Arrays.asList("1 1", "1 2", "2 2"), texts(rows));
        Assert.assertEquals(Arrays.asList(2, 0, 2), counts(rows));

        List<RangeSurvey.Row> deduped = survey(2, 1, 2, 0, true);
        Assert.assertEquals(Arrays.asList(1, 0, 1), counts(deduped));

        List<RangeSurvey.Row> byCount = RangeSurvey.select(rows, 1, null, RangeSurvey.Sort.SOLUTION_COUNT);
        Assert.assertEquals(Arrays.asList("1 1", "2 2"), texts(byCount));
        Assert.assertEquals(Arrays.asList(2, 2), counts(byCount));

        List<RangeSurvey.Row> window = RangeSurvey.select(rows, 2, 2, RangeSurvey.Sort.SOLUTION_COUNT);
        Assert.assertEquals(2, window.size());
        List<RangeSurvey.Row> none = RangeSurvey.select(rows, 3, 5, RangeSurvey.Sort.SOLUTION_COUNT);
        Assert.assertTrue(none.isEmpty());
    }

    @Test
    public void tooManySolutionsAreNotRated() {
        List<RangeSurvey.Row> rows = new ArrayList<RangeSurvey.Row>();
        RangeSurvey.search(new Solver(2, 1), 1, 2, 0, false, () -> false, null, 1, rows, null);
        Assert.assertEquals(Arrays.asList("1 1", "1 2", "2 2"), texts(rows));
        Assert.assertEquals(Arrays.asList(2, 0, 2), counts(rows));
        Assert.assertTrue(rows.get(0).overLimit());
        Assert.assertFalse(rows.get(1).overLimit());
        Assert.assertTrue(rows.get(2).overLimit());
        Assert.assertTrue(Double.isNaN(rows.get(0).difficulty()));
        Assert.assertTrue(Double.isNaN(rows.get(2).difficulty()));
        List<RangeSurvey.Row> kept = RangeSurvey.select(rows, 0, 100, RangeSurvey.Sort.SOLUTION_COUNT);
        Assert.assertEquals(Arrays.asList("1 2"), texts(kept));
    }

    @Test
    public void sortByDifficultyPutsHigherScoresFirst() {
        List<RangeSurvey.Row> rows = Arrays.asList(
                row(8, 2.0, 1, 1, 1, 8),
                row(0, Double.NaN, 9, 9, 9, 9),
                row(2, 10.0, 1, 2, 3, 4),
                row(3, 4.5, 1, 1, 2, 2),
                row(9, 1.25, 1, 1, 1, 9),
                row(1, 26.0, 5, 5, 5, 1));
        List<RangeSurvey.Row> byDifficulty = RangeSurvey.select(rows, 0, null, RangeSurvey.Sort.DIFFICULTY);
        Assert.assertEquals(Arrays.asList(26.0, 10.0, 4.5, 2.0, 1.25, Double.NaN), difficulties(byDifficulty));

        List<RangeSurvey.Row> byCount = RangeSurvey.select(rows, 1, 8, RangeSurvey.Sort.SOLUTION_COUNT);
        Assert.assertEquals(Arrays.asList(8, 3, 2, 1), counts(byCount));
    }

    @Test
    public void fourNumberSurveyMatchesADirectSolve() {
        double[] values = {3, 3, 7, 7};
        int direct = RangeSurvey.countSolutions(new Solver(4, 1), values, 24, true);
        int raw = RangeSurvey.countSolutions(new Solver(4, 1), values, 24, false);
        Assert.assertTrue(direct > 0);
        Assert.assertTrue(direct < raw);

        List<RangeSurvey.Row> rows = survey(4, 3, 7, 24, true);
        RangeSurvey.Row match = null;
        for (RangeSurvey.Row row : rows) {
            if ("3 3 7 7".equals(row.valuesText())) {
                match = row;
            }
            String[] parts = row.valuesText().split(" ");
            int previous = 0;
            for (String part : parts) {
                int value = Integer.parseInt(part);
                Assert.assertTrue(value >= previous);
                Assert.assertTrue(value >= 3 && value <= 7);
                previous = value;
            }
        }
        Assert.assertNotNull(match);
        Assert.assertEquals(direct, match.solutions());
        List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        new Solver(4, 1).findSolutionsStreaming(values, 24, Integer.MAX_VALUE, hits::add, null, true);
        Assert.assertEquals(Difficulty.rate(hits, values, 24).difficulty, match.difficulty(), 1e-9);
        Assert.assertEquals(RangeSurvey.multisetCount(4, 5), rows.size());
    }

    private static List<RangeSurvey.Row> survey(int n, int min, int max, double goal, boolean dedupe) {
        List<RangeSurvey.Row> rows = new ArrayList<RangeSurvey.Row>();
        int completed = RangeSurvey.search(new Solver(n, 1), min, max, goal, dedupe, () -> false, rows, null);
        Assert.assertEquals(rows.size(), completed);
        return rows;
    }

    private static RangeSurvey.Row row(int solutions, double difficulty, int... values) {
        return new RangeSurvey.Row(values, solutions, difficulty);
    }

    private static List<Double> difficulties(List<RangeSurvey.Row> rows) {
        List<Double> difficulties = new ArrayList<Double>();
        for (RangeSurvey.Row row : rows) {
            difficulties.add(row.difficulty());
        }
        return difficulties;
    }

    private static List<String> texts(List<RangeSurvey.Row> rows) {
        List<String> texts = new ArrayList<String>();
        for (RangeSurvey.Row row : rows) {
            texts.add(row.valuesText());
        }
        return texts;
    }

    private static List<Integer> counts(List<RangeSurvey.Row> rows) {
        List<Integer> counts = new ArrayList<Integer>();
        for (RangeSurvey.Row row : rows) {
            counts.add(row.solutions());
        }
        return counts;
    }
}
