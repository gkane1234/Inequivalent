package com.github.gkane1234;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 */
public final class Difficulty {
    public static final double LAMBDA = 1.5;
    /** Weight on the log-size of a dealt card or of a step's result. */
    public static final double FAMILIAR_WEIGHT = 0.5;
    public static final double MIN_SOLUTION_DIFFICULTY = 1.0;

    private static final double LOG_2 = Math.log(2.0);
    private static final double UNIT_TOLERANCE = 1e-9;

    private Difficulty() {}

    /** Raw step-cost total. Infinite when a step divides by zero. */
    public static double solutionCost(Expression expression, double[] values) {
        return walk(expression, values, false).cost;
    }

    /** One solved line, with the cost of each step. */
    public static LineTrace trace(Expression expression, double[] values) {
        Walked walked = walk(expression, values, true);
        String text = new EvaluatedExpression(expression, values, 0).display();
        double difficulty = walked.cost == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY
                : Math.max(MIN_SOLUTION_DIFFICULTY, walked.cost);
        return new LineTrace(walked.steps, walked.cost, difficulty, text);
    }

    /**
     * The cheapest line of each identity key, with that line's steps.
     * The rating matches {@link #rate}.
     */
    public static DealTrace explain(Iterable<? extends Expression> lines, double[] values) {
        Map<String, IdeaBuilder> best = new LinkedHashMap<String, IdeaBuilder>();
        int count = 0;
        for (Expression line : lines) {
            count++;
            String key = IdentityDedupe.signature(line, values);
            double difficulty = solutionDifficulty(line, values);
            IdeaBuilder idea = best.get(key);
            if (idea == null) {
                idea = new IdeaBuilder();
                idea.line = line;
                idea.difficulty = difficulty;
                idea.lines = 1;
                best.put(key, idea);
            } else {
                idea.lines++;
                if (difficulty < idea.difficulty) {
                    idea.line = line;
                    idea.difficulty = difficulty;
                }
            }
        }
        List<IdeaTrace> ideas = new ArrayList<IdeaTrace>();
        for (IdeaBuilder idea : best.values()) {
            ideas.add(new IdeaTrace(trace(idea.line, values), idea.lines));
        }
        ideas.sort((a, b) -> {
            int byScore = Double.compare(a.line.difficulty, b.line.difficulty);
            if (byScore != 0) {
                return byScore;
            }
            return a.line.text.compareTo(b.line.text);
        });
        if (count == 0) {
            return new DealTrace(ideas, new Rating(Double.NaN, 0, 0));
        }
        double reciprocal = 0.0;
        for (IdeaTrace idea : ideas) {
            reciprocal += 1.0 / idea.line.difficulty;
        }
        double deal = reciprocal == 0.0 ? Double.POSITIVE_INFINITY : 1.0 / reciprocal;
        return new DealTrace(ideas, new Rating(deal, ideas.size(), count));
    }

    /** How the deal score is assembled, and what each step in each idea costs. */
    public static String formatExplain(DealTrace deal) {
        StringBuilder text = new StringBuilder();
        if (deal.rating.ideas <= 0 || Double.isNaN(deal.rating.difficulty)) {
            text.append("No solved line, so this deal has no difficulty.\n");
            return text.toString();
        }
        text.append(format(deal.rating)).append('\n');
        text.append(parallelSum(deal)).append('\n');
        for (int i = 0; i < deal.ideas.size(); i++) {
            IdeaTrace idea = deal.ideas.get(i);
            text.append('\n');
            text.append("Idea ").append(i + 1).append(" of ").append(deal.ideas.size());
            text.append(". Score ").append(num(idea.line.difficulty));
            text.append(" from ").append(idea.lines).append(idea.lines == 1 ? " line." : " lines.");
            text.append(" The cheapest line is shown.\n");
            text.append(idea.line.text).append('\n');
            if (idea.line.cost == Double.POSITIVE_INFINITY) {
                text.append("A step divides by zero, so this line has no finite cost.\n");
            } else {
                text.append("line cost ").append(num(idea.line.cost));
                text.append(", score max(1, cost) = ").append(num(idea.line.difficulty)).append('\n');
            }
            for (Step step : idea.line.steps) {
                text.append(formatStep(step));
            }
        }
        return text.toString();
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
        return new Rating(deal, best.size(), count);
    }

    public static String format(Rating rating) {
        if (rating == null || rating.ideas <= 0 || Double.isNaN(rating.difficulty)) {
            return "";
        }
        String ideas = rating.ideas == 1 ? "1 idea" : rating.ideas + " ideas";
        return String.format(Locale.US, "Difficulty %.2f from %s. Lower is easier.", rating.difficulty, ideas);
    }

    private static Walked walk(Expression expression, double[] values, boolean record) {
        Item[] stack = new Item[expression.order.length];
        int sp = 0;
        int valuesPointer = 0;
        int operationsPointer = 0;
        double total = 0.0;
        List<Step> steps = record ? new ArrayList<Step>() : null;
        for (boolean isNumber : expression.order) {
            if (isNumber) {
                int varIndex = expression.valueOrder[valuesPointer++] & 0xff;
                stack[sp++] = Item.leaf(values[varIndex]);
            } else {
                Item right = stack[--sp];
                Item left = stack[--sp];
                int op = expression.operations[operationsPointer++] & 0xff;
                if (left.value == null || right.value == null) {
                    return Walked.infinite(steps);
                }
                Rat result = apply(op, left.value, right.value);
                if (result == null) {
                    return Walked.infinite(steps);
                }
                boolean leftFamiliar = !left.computed;
                boolean rightFamiliar = !right.computed;
                double leftCost = numberCost(left.value, leftFamiliar);
                double rightCost = numberCost(right.value, rightFamiliar);
                double resultCost = numberCost(result, true);
                double stepCost = leftCost + rightCost + resultCost;
                total += stepCost;
                String shown = show(left) + symbol(op) + show(right);
                if (record) {
                    steps.add(new Step(steps.size() + 1, symbol(op), shown, result.text(),
                            part("left", left.value, leftFamiliar, leftCost),
                            part("right", right.value, rightFamiliar, rightCost),
                            part("result", result, true, resultCost),
                            stepCost));
                }
                stack[sp++] = Item.computed(result, shown);
            }
        }
        if (sp != 1) {
            throw new IllegalStateException("Invalid expression");
        }
        return new Walked(total, steps == null ? new ArrayList<Step>() : steps);
    }

    private static String parallelSum(DealTrace deal) {
        StringBuilder terms = new StringBuilder();
        StringBuilder parts = new StringBuilder();
        double reciprocal = 0.0;
        for (int i = 0; i < deal.ideas.size(); i++) {
            double score = deal.ideas.get(i).line.difficulty;
            double share = 1.0 / score;
            reciprocal += share;
            if (i > 0) {
                terms.append(" + ");
                parts.append(" + ");
            }
            terms.append("1/").append(num(score));
            parts.append(num(share));
        }
        return "1/D = " + terms + " = " + parts + " = " + num(reciprocal)
                + "\nD = " + num(deal.rating.difficulty);
    }

    private static String formatStep(Step step) {
        StringBuilder text = new StringBuilder();
        text.append("  ").append(step.index).append(". ").append(step.expression);
        text.append(" = ").append(step.resultText);
        text.append("    step ").append(num(step.cost)).append('\n');
        text.append(formatPart(step.left));
        text.append(formatPart(step.right));
        text.append(formatPart(step.result));
        return text.toString();
    }

    private static String formatPart(Part part) {
        String kind = "result".equals(part.role) ? "this step" : (part.familiar ? "card" : "made earlier");
        String weight = part.familiar ? "half weight" : "full weight";
        return String.format(Locale.US, "     %-6s %-8s %-12s %-11s %s = %s%n",
                part.role, part.valueText, kind, weight, formula(part), num(part.cost));
    }

    private static String formula(Part part) {
        if (part.zero) {
            return "0";
        }
        String weight = part.familiar ? "0.5" : "1";
        String size = weight + "*ln(1+" + part.numerator.abs() + ")";
        if (BigInteger.ONE.equals(part.denominator)) {
            return size;
        }
        return size + " + " + trim(LAMBDA) + "*ln(" + part.denominator + ")";
    }

    private static String show(Item item) {
        return item.computed ? "(" + item.text + ")" : item.text;
    }

    private static char symbol(int op) {
        return Operation.getOperations()[op].getName();
    }

    private static Part part(String role, Rat value, boolean familiar, double cost) {
        return new Part(role, value.text(), familiar, value.p.signum() == 0,
                value.p, value.q, cost);
    }

    private static String num(double value) {
        if (value == Double.POSITIVE_INFINITY) {
            return "infinity";
        }
        return String.format(Locale.US, "%.4f", value);
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.4f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
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

    public static final class Part {
        public final String role;
        public final String valueText;
        public final boolean familiar;
        public final boolean zero;
        public final BigInteger numerator;
        public final BigInteger denominator;
        public final double cost;

        Part(String role, String valueText, boolean familiar, boolean zero,
                BigInteger numerator, BigInteger denominator, double cost) {
            this.role = role;
            this.valueText = valueText;
            this.familiar = familiar;
            this.zero = zero;
            this.numerator = numerator;
            this.denominator = denominator;
            this.cost = cost;
        }
    }

    public static final class Step {
        public final int index;
        public final char symbol;
        public final String expression;
        public final String resultText;
        public final Part left;
        public final Part right;
        public final Part result;
        public final double cost;

        Step(int index, char symbol, String expression, String resultText,
                Part left, Part right, Part result, double cost) {
            this.index = index;
            this.symbol = symbol;
            this.expression = expression;
            this.resultText = resultText;
            this.left = left;
            this.right = right;
            this.result = result;
            this.cost = cost;
        }
    }

    public static final class LineTrace {
        public final List<Step> steps;
        public final double cost;
        public final double difficulty;
        public final String text;

        LineTrace(List<Step> steps, double cost, double difficulty, String text) {
            this.steps = steps;
            this.cost = cost;
            this.difficulty = difficulty;
            this.text = text;
        }
    }

    public static final class IdeaTrace {
        public final LineTrace line;
        public final int lines;

        IdeaTrace(LineTrace line, int lines) {
            this.line = line;
            this.lines = lines;
        }
    }

    public static final class DealTrace {
        public final List<IdeaTrace> ideas;
        public final Rating rating;

        DealTrace(List<IdeaTrace> ideas, Rating rating) {
            this.ideas = ideas;
            this.rating = rating;
        }
    }

    private static final class Walked {
        final double cost;
        final List<Step> steps;

        Walked(double cost, List<Step> steps) {
            this.cost = cost;
            this.steps = steps;
        }

        static Walked infinite(List<Step> steps) {
            return new Walked(Double.POSITIVE_INFINITY,
                    steps == null ? new ArrayList<Step>() : steps);
        }
    }

    private static final class IdeaBuilder {
        Expression line;
        double difficulty;
        int lines;
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
        final String text;

        private Item(Rat value, boolean computed, String text) {
            this.value = value;
            this.computed = computed;
            this.text = text;
        }

        static Item leaf(double number) {
            Rat value = Rat.fromDouble(number);
            return new Item(value, false, value == null ? "?" : value.text());
        }

        static Item computed(Rat value, String text) {
            return new Item(value, true, text);
        }
    }

    private static final class Rat {
        final BigInteger p;
        final BigInteger q;

        private Rat(BigInteger p, BigInteger q) {
            this.p = p;
            this.q = q;
        }

        String text() {
            if (BigInteger.ONE.equals(q)) {
                return p.toString();
            }
            return p + "/" + q;
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
