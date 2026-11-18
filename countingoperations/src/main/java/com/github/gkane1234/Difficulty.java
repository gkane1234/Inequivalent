package com.github.gkane1234;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Difficulty of one solved line, and of a deal.
 *
 * <p>A line is the sum of its step costs, and at least 1. Lines that share an
 * {@link IdentityDedupe} key are one idea, and the idea keeps its cheapest
 * line. The deal is the parallel sum of those ideas, so another idea always
 * makes the deal easier than the easiest idea alone. No solved line leaves
 * the deal undefined.
 *
 * <p>A number is a reduced fraction {@code p/q}. Its size is {@code ln(1+|p|)},
 * and 0 has size 0. A denominator adds {@code λ ln(q)} at full weight. Dealt
 * cards and the result of a step use half weight on the log-size. A value
 * produced by an earlier step uses full weight when it is used again. The
 * step costs are the unit. There is no anchor.
 *
 * <p>Repeated dealt numbers scale the finished deal. A value seen once weighs
 * 1. A value seen {@code k} times weighs {@code 1 + 1/k}. The scale is that
 * sum divided by how many cards were dealt, so a hand of all different
 * numbers is unchanged.
 */
public final class Difficulty {
    public static final double LAMBDA = 2.5;
    /** Weight on the log-size of a dealt card or of a step's result. */
    public static final double FAMILIAR_WEIGHT = 0.5;
    public static final double MIN_SOLUTION_DIFFICULTY = 1.0;

    private static final double LOG_2 = Math.log(2.0);
    private static final double UNIT_TOLERANCE = 1e-9;

    private Difficulty() {}

    /** Raw step-cost total. Infinite when a step divides by zero. */
    public static double solutionCost(Expression expression, double[] values) {
        Item[] stack = new Item[expression.order.length];
        int sp = 0;
        int valuesPointer = 0;
        int operationsPointer = 0;
        double total = 0.0;
        for (boolean isNumber : expression.order) {
            if (isNumber) {
                int varIndex = expression.valueOrder[valuesPointer++] & 0xff;
                stack[sp++] = Item.leaf(values[varIndex]);
            } else {
                Item right = stack[--sp];
                Item left = stack[--sp];
                int op = expression.operations[operationsPointer++] & 0xff;
                if (left.value == null || right.value == null) {
                    return Double.POSITIVE_INFINITY;
                }
                Rat result = apply(op, left.value, right.value);
                if (result == null) {
                    return Double.POSITIVE_INFINITY;
                }
                total += numberCost(left.value, !left.computed)
                        + numberCost(right.value, !right.computed)
                        + numberCost(result, true);
                stack[sp++] = Item.computed(result);
            }
        }
        if (sp != 1) {
            throw new IllegalStateException("Invalid expression");
        }
        return total;
    }

    /** {@code max(1, solutionCost)}. */
    public static double solutionDifficulty(Expression expression, double[] values) {
        return Math.max(MIN_SOLUTION_DIFFICULTY, solutionCost(expression, values));
    }

    /**
     * Parallel sum of the cheapest line of each identity key.
     * {@link Double#NaN} when {@code lines} is empty.
     */
    public static Rating rate(Iterable<? extends Expression> lines, double[] values) {
        Map<String, Double> best = new HashMap<String, Double>();
        int count = 0;
        for (Expression line : lines) {
            count++;
            String key = IdentityDedupe.signature(line, values);
            double difficulty = solutionDifficulty(line, values);
            Double previous = best.get(key);
            if (previous == null || difficulty < previous) {
                best.put(key, difficulty);
            }
        }
        if (count == 0) {
            return new Rating(Double.NaN, 0, 0);
        }
        double reciprocal = 0.0;
        for (double difficulty : best.values()) {
            reciprocal += 1.0 / difficulty;
        }
        double deal = reciprocal == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / reciprocal;
        if (Double.isFinite(deal)) {
            deal *= repeatFactor(values);
        }
        return new Rating(deal, best.size(), count);
    }

    /**
     * How much repeated cards ease the deal. Four different numbers give 1.
     * Four of a kind give {@code (5/4) / 4}. A triple and a singleton give
     * {@code (1 + 4/3) / 4}. Two pairs give {@code (3/2 + 3/2) / 4}.
     */
    public static double repeatFactor(double[] values) {
        int n = values.length;
        if (n == 0) {
            return 1.0;
        }
        double[] sorted = new double[n];
        System.arraycopy(values, 0, sorted, 0, n);
        Arrays.sort(sorted);
        double weight = 0.0;
        int count = 1;
        for (int i = 1; i <= n; i++) {
            if (i < n && sameCard(sorted[i], sorted[i - 1])) {
                count++;
            } else {
                weight += count == 1 ? 1.0 : 1.0 + 1.0 / count;
                count = 1;
            }
        }
        return weight / n;
    }

    private static boolean sameCard(double left, double right) {
        return Double.isFinite(left) && Double.isFinite(right) && Math.abs(left - right) <= UNIT_TOLERANCE;
    }

    public static String format(Rating rating) {
        if (rating == null || rating.ideas <= 0 || Double.isNaN(rating.difficulty)) {
            return "";
        }
        String ideas = rating.ideas == 1 ? "1 idea" : rating.ideas + " ideas";
        return String.format(Locale.US, "Difficulty %.2f from %s. Lower is easier.", rating.difficulty, ideas);
    }

    private static double numberCost(Rat value, boolean familiar) {
        if (value.p.signum() == 0) {
            return 0.0;
        }
        double weight = familiar ? FAMILIAR_WEIGHT : 1.0;
        double size = weight * log1pAbs(value.p);
        double fraction = BigInteger.ONE.equals(value.q) ? 0.0 : LAMBDA * logAbs(value.q);
        return size + fraction;
    }

    private static double log1pAbs(BigInteger value) {
        BigInteger magnitude = value.abs();
        if (magnitude.bitLength() <= 52) {
            return Math.log(1.0 + magnitude.doubleValue());
        }
        return logAbs(magnitude);
    }

    private static double logAbs(BigInteger value) {
        BigInteger magnitude = value.abs();
        int bits = magnitude.bitLength();
        if (bits <= 52) {
            return Math.log(magnitude.doubleValue());
        }
        int shift = bits - 52;
        double mantissa = magnitude.shiftRight(shift).doubleValue();
        return Math.log(mantissa) + shift * LOG_2;
    }

    private static Rat apply(int op, Rat left, Rat right) {
        if (op == ComSearchGenerator.OP_ADD) {
            return left.add(right);
        }
        if (op == ComSearchGenerator.OP_SUB) {
            return left.subtract(right);
        }
        if (op == ComSearchGenerator.OP_MUL) {
            return left.multiply(right);
        }
        if (op == ComSearchGenerator.OP_DIV) {
            return left.divide(right);
        }
        throw new IllegalArgumentException("unknown op " + op);
    }

    public static final class Rating {
        public final double difficulty;
        public final int ideas;
        public final int lines;

        Rating(double difficulty, int ideas, int lines) {
            this.difficulty = difficulty;
            this.ideas = ideas;
            this.lines = lines;
        }
    }

    private static final class Item {
        final Rat value;
        final boolean computed;

        private Item(Rat value, boolean computed) {
            this.value = value;
            this.computed = computed;
        }

        static Item leaf(double number) {
            return new Item(Rat.fromDouble(number), false);
        }

        static Item computed(Rat value) {
            return new Item(value, true);
        }
    }

    private static final class Rat {
        final BigInteger p;
        final BigInteger q;

        private Rat(BigInteger p, BigInteger q) {
            this.p = p;
            this.q = q;
        }

        static Rat fromDouble(double number) {
            if (!Double.isFinite(number)) {
                return null;
            }
            double rounded = Math.rint(number);
            if (Math.abs(number - rounded) <= UNIT_TOLERANCE && Math.abs(rounded) < 1e15) {
                return new Rat(BigInteger.valueOf((long) rounded), BigInteger.ONE);
            }
            return continuedFraction(number);
        }

        Rat add(Rat other) {
            return reduced(p.multiply(other.q).add(other.p.multiply(q)), q.multiply(other.q));
        }

        Rat subtract(Rat other) {
            return reduced(p.multiply(other.q).subtract(other.p.multiply(q)), q.multiply(other.q));
        }

        Rat multiply(Rat other) {
            return reduced(p.multiply(other.p), q.multiply(other.q));
        }

        Rat divide(Rat other) {
            if (other.p.signum() == 0) {
                return null;
            }
            return reduced(p.multiply(other.q), q.multiply(other.p));
        }

        private static Rat reduced(BigInteger numerator, BigInteger denominator) {
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger divisor = numerator.gcd(denominator);
            return new Rat(numerator.divide(divisor), denominator.divide(divisor));
        }

        private static Rat continuedFraction(double number) {
            double sign = number < 0 ? -1.0 : 1.0;
            double rest = Math.abs(number);
            BigInteger n0 = BigInteger.ZERO;
            BigInteger d0 = BigInteger.ONE;
            BigInteger n1 = BigInteger.ONE;
            BigInteger d1 = BigInteger.ZERO;
            for (int i = 0; i < 40; i++) {
                long whole = (long) Math.floor(rest + 1e-12);
                BigInteger nextN = n1.multiply(BigInteger.valueOf(whole)).add(n0);
                BigInteger nextD = d1.multiply(BigInteger.valueOf(whole)).add(d0);
                n0 = n1;
                d0 = d1;
                n1 = nextN;
                d1 = nextD;
                if (d1.signum() != 0 && Math.abs(n1.doubleValue() / d1.doubleValue() - Math.abs(number)) <= UNIT_TOLERANCE) {
                    break;
                }
                double frac = rest - whole;
                if (frac < 1e-12) {
                    break;
                }
                rest = 1.0 / frac;
            }
            BigInteger numerator = sign < 0 ? n1.negate() : n1;
            return reduced(numerator, d1.signum() == 0 ? BigInteger.ONE : d1);
        }
    }
}
