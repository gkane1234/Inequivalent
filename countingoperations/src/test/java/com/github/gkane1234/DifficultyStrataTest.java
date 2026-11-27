package com.github.gkane1234;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DifficultyStrataTest {
    private static final double TOLERANCE = 1e-6;

    @Test
    public void percentileInterpolates() {
        List<Double> values = Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0);
        Assert.assertEquals(10.0, DifficultyStrata.percentile(values, 0.0), TOLERANCE);
        Assert.assertEquals(50.0, DifficultyStrata.percentile(values, 1.0), TOLERANCE);
        Assert.assertEquals(30.0, DifficultyStrata.percentile(values, 0.5), TOLERANCE);
        Assert.assertEquals(34.0, DifficultyStrata.percentile(values, 0.6), TOLERANCE);
    }

    @Test
    public void bandsFollowInclusiveCutoffs() {
        DifficultyStrata.Bounds bounds = new DifficultyStrata.Bounds(10, 20, 30);
        Assert.assertEquals(DifficultyStrata.Band.EASY, bounds.bandFor(10));
        Assert.assertEquals(DifficultyStrata.Band.MEDIUM, bounds.bandFor(10.0001));
        Assert.assertEquals(DifficultyStrata.Band.MEDIUM, bounds.bandFor(20));
        Assert.assertEquals(DifficultyStrata.Band.HARD, bounds.bandFor(20.0001));
        Assert.assertEquals(DifficultyStrata.Band.HARD, bounds.bandFor(30));
        Assert.assertEquals(DifficultyStrata.Band.HARDEST, bounds.bandFor(30.0001));
        Assert.assertNull(bounds.bandFor(Double.NaN));
        Assert.assertNull(bounds.bandFor(0));
    }

    @Test
    public void defaultSweepMatchesStoredBounds() {
        DifficultyStrata.Result result = DifficultyStrata.compute();
        Assert.assertEquals(1820, result.boards);
        Assert.assertEquals(1362, result.solvable);
        Assert.assertEquals(DifficultyStrata.DEFAULT_BOUNDS.easyMax, result.bounds.easyMax, 1e-4);
        Assert.assertEquals(DifficultyStrata.DEFAULT_BOUNDS.mediumMax, result.bounds.mediumMax, 1e-4);
        Assert.assertEquals(DifficultyStrata.DEFAULT_BOUNDS.hardMax, result.bounds.hardMax, 1e-4);
    }

    @Test
    public void game24FindsEachBand() {
        for (DifficultyStrata.Band band : DifficultyStrata.Band.values()) {
            Game24.Puzzle puzzle = Game24.find(band, DifficultyStrata.DEFAULT_BOUNDS, new java.util.Random(1), 2000, null);
            Assert.assertEquals(band, puzzle.band);
            Assert.assertTrue(DifficultyStrata.DEFAULT_BOUNDS.matches(puzzle.rating.difficulty, band));
            Assert.assertFalse(puzzle.solutions.isEmpty());
        }
    }
}
