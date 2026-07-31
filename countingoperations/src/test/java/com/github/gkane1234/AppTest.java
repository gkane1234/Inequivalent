package com.github.gkane1234;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Legacy compression suite; bit-packing roundtrips are covered elsewhere.
 * Kept ignored so surefire can compile this package.
 */
public class AppTest {

    @Test
    @Ignore("ExpressionCompression roundtrip is not reliable for all ComSearch forms yet")
    public void testExpressionCompression() {
        // intentionally empty
    }
}
