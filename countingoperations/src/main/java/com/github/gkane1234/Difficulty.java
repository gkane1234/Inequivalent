package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Difficulty of one solved line, and of a list of numbers with a goal.
 *
 * <p>The editable rules are the methods in the score section below. Comment out
 * a return and write another to try a different rule. {@link #PARAMETERS} holds
 * the numeric factors: the operation and side weights, the weight on a starting
 * number, and the weight on the goal.
 *
 * <p>A step charges the two numbers it uses. A starting number is multiplied by
 * {@code startingNumberFactor}. A number made by an earlier step is not. The
 * line adds {@code goalFactor} times the cost of the goal. The line is then
 * floored, the ideas are combined, and the repeated-number result is applied.
 * Lines that share an {@link IdentityDedupe} key are one idea, and the idea
 * keeps its cheapest line. No solved line leaves the difficulty undefined.
 */
public final class Difficulty {
    /** The score used by the methods that do not take a {@link Parameters}. */
    public static final Parameters PARAMETERS = new Parameters();

    private static final double LOG_2 = Math.log(2.0);
    private static final double UNIT_TOLERANCE = 1e-9;

    private Difficulty() {}

    /** Raw step-cost total. Infinite when a step divides by zero. */
    public static double solutionCost(Expression expression, double[] values) {
        return solutionCost(expression, values, PARAMETERS);
    }

    public static double solutionCost(Expression expression, double[] values, Parameters parameters) {
        Parameters cost = parameters == null ? PARAMETERS : parameters;
        return walk(reorder(expression, values, cost), values, false, cost).cost;
    }

    /** One solved line, with the cost of each step. */
    public static LineTrace trace(Expression expression, double[] values) {
        return trace(expression, values, Double.NaN, PARAMETERS);
    }

    public static LineTrace trace(Expression expression, double[] values, double goal) {
        return trace(expression, values, goal, PARAMETERS);
    }

    public static LineTrace trace(Expression expression, double[] values, double goal, Parameters parameters) {
        Parameters cost = parameters == null ? PARAMETERS : parameters;
        Expression ordered = reorder(expression, values, cost);
        Walked walked = walk(ordered, values, true, cost);
        String text = new EvaluatedExpression(ordered, values, 0).display();
        double goalPart = walked.cost == Double.POSITIVE_INFINITY ? 0.0 : goalCost(goal, cost);
        double difficulty = walked.cost == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY
                : floorCost(lineCost(walked.cost, goalPart));
        return new LineTrace(walked.steps, walked.cost, goalPart, difficulty, text);
    }

    /**
     * The cheapest line of each identity key, with that line's steps.
     * The rating matches {@link #rate}.
     */
    public static DealTrace explain(Iterable<? extends Expression> lines, double[] values, double goal) {
        return explain(lines, values, goal, PARAMETERS);
    }

    public static DealTrace explain(Iterable<? extends Expression> lines, double[] values, double goal,
            Parameters parameters) {
        Parameters cost = parameters == null ? PARAMETERS : parameters;
        Map<String, IdeaBuilder> best = new LinkedHashMap<String, IdeaBuilder>();
        int count = 0;
        for (Expression line : lines) {
            count++;
            String key = IdentityDedupe.signature(line, values);
            double difficulty = solutionDifficulty(line, values, goal, cost);
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
            ideas.add(new IdeaTrace(trace(idea.line, values, goal, cost), idea.lines));
        }
        ideas.sort((a, b) -> {
            int byScore = Double.compare(a.line.difficulty, b.line.difficulty);
            if (byScore != 0) {
                return byScore;
            }
            return a.line.text.compareTo(b.line.text);
        });
        if (count == 0) {
            return new DealTrace(ideas, new Rating(Double.NaN, 0, 0), Double.NaN, 1.0);
        }
        double[] ideaScores = new double[ideas.size()];
        for (int i = 0; i < ideas.size(); i++) {
            ideaScores[i] = ideas.get(i).line.difficulty;
        }
        double parallel = combineIdeas(ideaScores);
        double repeat = repeatedNumbers(values);
        double difficulty = parallel;
        if (Double.isFinite(difficulty)) {
            difficulty = finish(difficulty, repeat);
        }
        return new DealTrace(ideas, new Rating(difficulty, ideas.size(), count), parallel, repeat);
    }

    /** How the deal score is assembled, and what each step in each idea costs. */
    public static String formatExplain(DealTrace deal) {
        StringBuilder text = new StringBuilder();
        if (deal.rating.ideas <= 0 || Double.isNaN(deal.rating.difficulty)) {
            text.append("No solved line, so these numbers have no difficulty.\n");
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
                text.append("steps ").append(num(idea.line.cost));
                text.append(", goal ").append(num(idea.line.goal));
                text.append(", score ").append(num(idea.line.difficulty)).append('\n');
            }
            for (Step step : idea.line.steps) {
                text.append(formatStep(step));
            }
        }
        return text.toString();
    }

    /** Floored line cost, including the goal term. */
    public static double solutionDifficulty(Expression expression, double[] values, double goal) {
        return solutionDifficulty(expression, values, goal, PARAMETERS);
    }

    public static double solutionDifficulty(Expression expression, double[] values, double goal,
            Parameters parameters) {
        Parameters cost = parameters == null ? PARAMETERS : parameters;
        double steps = solutionCost(expression, values, cost);
        if (steps == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        return floorCost(lineCost(steps, goalCost(goal, cost)));
    }

    /**
     * Parallel sum of the cheapest line of each identity key.
     * {@link Double#NaN} when {@code lines} is empty.
     */
    public static Rating rate(Iterable<? extends Expression> lines, double[] values, double goal) {
        return rate(lines, values, goal, PARAMETERS);
    }

    public static Rating rate(Iterable<? extends Expression> lines, double[] values, double goal,
            Parameters parameters) {
        Parameters cost = parameters == null ? PARAMETERS : parameters;
        Map<String, Double> best = new HashMap<String, Double>();
        int count = 0;
        for (Expression line : lines) {
            count++;
            String key = IdentityDedupe.signature(line, values);
            double difficulty = solutionDifficulty(line, values, goal, cost);
            Double previous = best.get(key);
            if (previous == null || difficulty < previous) {
                best.put(key, difficulty);
            }
        }
        if (count == 0) {
            return new Rating(Double.NaN, 0, 0);
        }
        double[] ideaScores = new double[best.size()];
        int ideaIndex = 0;
        for (double difficulty : best.values()) {
            ideaScores[ideaIndex++] = difficulty;
        }
        double difficulty = combineIdeas(ideaScores);
        double repeated = repeatedNumbers(values);
        if (Double.isFinite(difficulty)) {
            difficulty = finish(difficulty, repeated);
        }
        return new Rating(difficulty, best.size(), count);
    }

    public static String format(Rating rating) {
        if (rating == null || rating.ideas <= 0 || Double.isNaN(rating.difficulty)) {
            return "";
        }
        String ideas = rating.ideas == 1 ? "1 idea" : rating.ideas + " ideas";
        return String.format(Locale.US, "Difficulty %.2f from %s. Lower is easier.", rating.difficulty, ideas);
    }

    private static Walked walk(Expression expression, double[] values, boolean record, Parameters parameters) {
        Parameters cost = parameters == null ? PARAMETERS : parameters;
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
                double leftScale = cost.sideFactor(op, true);
                double rightScale = cost.sideFactor(op, false);
                double leftWeight = left.computed ? 1.0 : cost.startingNumberFactor;
                double rightWeight = right.computed ? 1.0 : cost.startingNumberFactor;
                double leftValue = valueCost(left.value);
                double rightValue = valueCost(right.value);
                double leftCost = leftWeight * leftScale * leftValue;
                double rightCost = rightWeight * rightScale * rightValue;
                double stepTotal = combineOperands(leftCost, rightCost);
                total += stepTotal;
                String shown = show(left) + symbol(op) + show(right);
                if (record) {
                    steps.add(new Step(steps.size() + 1, symbol(op), shown, result.text(),
                            operandPart("left", left.value, !left.computed, leftWeight, leftScale, leftValue, leftCost),
                            operandPart("right", right.value, !right.computed, rightWeight, rightScale, rightValue, rightCost),
                            operandPart("result", result, false, 0.0, 0.0, 0.0, 0.0),
                            stepTotal, cost.operationCost(op)));
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
        return "Ideas combine to " + num(deal.parallel)
                + "\nRepeated numbers " + num(deal.repeat)
                + "\nDifficulty " + num(deal.rating.difficulty);
    }

    /** True when two input numbers are the same pile in {@link #repeatedNumbers}. */
    private static boolean sameNumber(double left, double right) {
        return Double.isFinite(left) && Double.isFinite(right) && Math.abs(left - right) <= UNIT_TOLERANCE;
    }

    /** The reported difficulty: {@link #combineIdeas} combined with {@link #repeatedNumbers}. */
    static double finish(double ideas, double repeated) {
        return ideas * repeated;
    }

    /** Public name for {@link #repeatedNumbers}. */
    public static double repeatFactor(double[] values) {
        return repeatedNumbers(values);
    }

    private static String formatStep(Step step) {
        StringBuilder text = new StringBuilder();
        text.append("  ").append(step.index).append(". ").append(step.expression);
        text.append(" = ").append(step.resultText);
        text.append("    step ").append(num(step.cost)).append('\n');
        if (Math.abs(step.operationCost - 1.0) > 1e-12) {
            text.append("    operation × ").append(num(step.operationCost)).append('\n');
        }
        text.append(formatPart(step.left));
        text.append(formatPart(step.right));
        return text.toString();
    }

    private static String formatPart(Part part) {
        String kind = "result".equals(part.role) ? "this step" : (part.familiar ? "number" : "made earlier");
        String weight = weightLabel(part);
        return String.format(Locale.US, "     %-6s %-8s %-12s %-11s %s = %s%n",
                part.role, part.valueText, kind, weight, part.formula, num(part.cost));
    }

    private static String weightLabel(Part part) {
        if (part.familiar && Math.abs(part.roleWeight - 0.5) < 1e-9) {
            return "half weight";
        }
        if (!part.familiar && Math.abs(part.roleWeight - 1.0) < 1e-9) {
            return "full weight";
        }
        return trim(part.roleWeight) + " weight";
    }

    private static String show(Item item) {
        return item.computed ? "(" + item.text + ")" : item.text;
    }

    private static char symbol(int op) {
        return Operation.getOperations()[op].getName();
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


    private static Part operandPart(String role, Rat value, boolean starting, double weight,
            double scale, double costOfValue, double cost) {
        String formula = "result".equals(role)
                ? "later"
                : trim(weight) + "*" + trim(scale) + "*" + trim(costOfValue);
        return new Part(role, value.text(), starting, value.p == 0L,
                value.p, value.q, cost, weight, formula);
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

    
    /*
     * Editable score. Comment out a return and write another to try a different rule.
     * Numeric weights live on Parameters. With the returns below, the score is
     *
     * F(p/q) = 0 when p = 0, otherwise
     *          log2(1+|p|) · (q ≠ 1 ? k·log2(q) : 1)
     * negativeFactor returns 1, so a negative number costs the same as |p|.
     * step   = w(a)·S(op, first)·F(a) + w(b)·S(op, second)·F(b)
     * d      = max(1, Σ steps + goalFactor·F(goal))
     * D      = repeatedNumbers · 1/(1/d1 + 1/d2 + …)
     *
     * w is startingNumberFactor for a card and 1 for a number made earlier.
     * S is that operation's factor times that side's factor.
     * d1, d2, … are the cheapest line of each idea.
     * repeatedNumbers is 2 for a value seen once and 0.5·(1+1/k) for a value seen k times,
     * summed over those piles.
     */

    /** Cost of the numerator, p. */
    static double numeratorCost(Rat value) {
        return Math.log1p(Math.abs(value.p)) / LOG_2;
        // return Math.abs(value.p);
    }

    
    /**
     * Cost of the denominator, q. 
     */
    static double denominatorCost(Rat value) {
        return 4* Math.log(Math.abs(value.q)) / LOG_2;
        // return 1.0;
    }

    /**
     * Extra factor when p is below 0. valueCost multiplies the magnitude by this.
     * A return of 1 leaves a negative number the same as its absolute value.
     */
    static double signFactor(Rat value) {
        double positiveFactor = 1.0;
        double negativeFactor = 1.0;
        return value.p < 0L ? negativeFactor : positiveFactor;
        // return 2.0;
    }


    /**
     * F, the cost of one number. A zero costs 0. Otherwise this is the magnitude,
     * times {@link #negativeFactor} when p is negative, times {@link #fractionFactor}
     * when q is not 1. Steps, the goal term, and nothing else call this.
     */
    static double valueCost(Rat value) {
        if (value == null || value.p == 0L) {
            return 0.0;
        }
        return signFactor(value)*(numeratorCost(value)+denominatorCost(value));
    }

    /**
     * One step from its two operand costs. Each operand cost is already
     * w·S·F. The result of the step is not included. It is charged when a later
     * step uses it, and the goal is charged by {@link #goalTerm}.
     */
    static double combineOperands(double firstCost, double secondCost) {
        return firstCost + secondCost;
    }

    /** goalFactor times F(goal). Added once per line */
    private static double goalCost(double goal, Parameters parameters) {
        return parameters.goalFactor * valueCost(Rat.fromDouble(goal));
    }

    /** The line before the floor: the sum of {@link #combineOperands} plus {@link #goalCost}. */
    static double lineCost(double stepsCost, double goalCost) {
        return stepsCost + goalCost;
    }

    /**
     * The line score. A cost of 0 would make the reciprocal sum infinite, so a free
     * line is one unit. Comment out the max line to let a line cost less than 1.
     */
    static double floorCost(double cost) {
        return Math.max(1.0, cost);
        // return cost;
    }

    /**
     * One score from the cheapest line of each idea. The reciprocal sum makes another
     * idea lower the score. The result is the idea score passed to {@link #finish}.
     */
    static double combineIdeas(double[] difficulties) {
        double reciprocal = 0.0;
        for (double difficulty : difficulties) {
            reciprocal += 1.0 / difficulty;
        }
        if (reciprocal == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0 / reciprocal;
    }

    /**
     * The hand's repeated-number scale, from the input numbers only. A value seen
     * once weighs 2. A value seen k times weighs 0.5·(1+1/k). The result is summed
     * across those piles and passed to {@link #finish}.
     */
    static double repeatedNumbers(double[] values) {
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
            if (i < n && sameNumber(sorted[i], sorted[i - 1])) {
                count++;
            } else {
                weight += count == 1 ? 1.0 : 0.5 * (1.0 + 1.0 / count);
                count = 1;
            }
        }
        return weight;
    }

    /**
     * Numeric factors. A fresh instance matches the standard score.
     * The functions that use them are {@code magnitude}, {@code negativeFactor},
     * {@code fractionFactor}, {@code valueCost}, {@code combineOperands}, {@code lineCost},
     * {@code floorCost}, {@code combineIdeas}, {@code repeatedNumbers}, and {@code finish}.
     */
    public static final class Parameters {
        /** Weight on an original input number. */
        public double startingNumberFactor = 0;
        /** Weight on the goal, charged once per line. */
        public double goalFactor = 0;
        /** Multiplier for each addition. */
        public double addCost = 1.0;
        /** Multiplier for each subtraction. */
        public double subtractCost = 1.25;
        /** Multiplier for each multiplication. */
        public double multiplyCost = 1.5;
        /** Multiplier for each division. */
        public double divideCost = 1.5;
        /** Multiplier on the first argument of a subtraction. */
        public double subtractLeft = 1.0;
        /** Multiplier on the second argument of a subtraction. */
        public double subtractRight = 1.25;
        /** Multiplier on the first argument of a division. */
        public double divideLeft = 1.0;
        /** Multiplier on the second argument of a division. */
        public double divideRight = 1.25;

        double operationCost(int op) {
            if (op == ComSearchGenerator.OP_ADD) {
                return addCost;
            }
            if (op == ComSearchGenerator.OP_SUB) {
                return subtractCost;
            }
            if (op == ComSearchGenerator.OP_MUL) {
                return multiplyCost;
            }
            if (op == ComSearchGenerator.OP_DIV) {
                return divideCost;
            }
            return 1.0;
        }

        double sideFactor(int op, boolean left) {
            return operationCost(op) * argumentWeight(op, left);
        }

        double argumentWeight(int op, boolean left) {
            if (op == ComSearchGenerator.OP_SUB) {
                return left ? subtractLeft : subtractRight;
            }
            if (op == ComSearchGenerator.OP_DIV) {
                return left ? divideLeft : divideRight;
            }
            return 1.0;
        }
    }

    public static final class Part {
        public final String role;
        public final String valueText;
        public final boolean familiar;
        public final boolean zero;
        public final long numerator;
        public final long denominator;
        public final double cost;
        public final double roleWeight;
        public final String formula;

        Part(String role, String valueText, boolean familiar, boolean zero,
                long numerator, long denominator, double cost,
                double roleWeight, String formula) {
            this.role = role;
            this.valueText = valueText;
            this.familiar = familiar;
            this.zero = zero;
            this.numerator = numerator;
            this.denominator = denominator;
            this.cost = cost;
            this.roleWeight = roleWeight;
            this.formula = formula;
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
        public final double operationCost;

        Step(int index, char symbol, String expression, String resultText,
                Part left, Part right, Part result, double cost, double operationCost) {
            this.index = index;
            this.symbol = symbol;
            this.expression = expression;
            this.resultText = resultText;
            this.left = left;
            this.right = right;
            this.result = result;
            this.cost = cost;
            this.operationCost = operationCost;
        }
    }

    public static final class LineTrace {
        public final List<Step> steps;
        /** Sum of the step costs, before the goal term and the floor. */
        public final double cost;
        public final double goal;
        public final double difficulty;
        public final String text;

        LineTrace(List<Step> steps, double cost, double goal, double difficulty, String text) {
            this.steps = steps;
            this.cost = cost;
            this.goal = goal;
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
        final double parallel;
        final double repeat;

        DealTrace(List<IdeaTrace> ideas, Rating rating, double parallel, double repeat) {
            this.ideas = ideas;
            this.rating = rating;
            this.parallel = parallel;
            this.repeat = repeat;
        }
    }

    static final class Walked {
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

    static final class IdeaBuilder {
        Expression line;
        double difficulty;
        int lines;

        IdeaBuilder() {}
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

    static final class Item {
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

    /**
     * Cheapest +/− or ×/÷ order inside each layer. The value of a layer does not
     * change, so optimizing a layer cannot spoil the layer above it.
     */
    private static Expression reorder(Expression expression, double[] values, Parameters parameters) {
        return optimize(parseNode(expression), values, parameters).nodeToExpression();
    }

    private static Ordered optimize(Node node, double[] values, Parameters parameters) {
        if (node.leaf) {
            Ordered ordered = new Ordered();
            ordered.node = node;
            ordered.value = Rat.fromDouble(values[node.varIndex]);
            ordered.weight = parameters.startingNumberFactor;
            return ordered;
        }
        boolean additive = node.op == ComSearchGenerator.OP_ADD || node.op == ComSearchGenerator.OP_SUB;
        List<Ordered> parts = new ArrayList<Ordered>();
        List<Boolean> negative = new ArrayList<Boolean>();
        if (additive) {
            collectSum(node, false, parts, negative, values, parameters);
        } else {
            collectProduct(node, false, parts, negative, values, parameters);
        }
        return assemble(parts, negative, additive, parameters);
    }

    private static void collectSum(Node node, boolean negated, List<Ordered> parts, List<Boolean> negative,
            double[] values, Parameters parameters) {
        if (node.leaf || node.op == ComSearchGenerator.OP_MUL || node.op == ComSearchGenerator.OP_DIV) {
            parts.add(optimize(node, values, parameters));
            negative.add(Boolean.valueOf(negated));
            return;
        }
        if (node.op == ComSearchGenerator.OP_ADD) {
            collectSum(node.left, negated, parts, negative, values, parameters);
            collectSum(node.right, negated, parts, negative, values, parameters);
            return;
        }
        collectSum(node.left, negated, parts, negative, values, parameters);
        collectSum(node.right, !negated, parts, negative, values, parameters);
    }

    private static void collectProduct(Node node, boolean inverted, List<Ordered> parts, List<Boolean> negative,
            double[] values, Parameters parameters) {
        if (node.leaf || node.op == ComSearchGenerator.OP_ADD || node.op == ComSearchGenerator.OP_SUB) {
            parts.add(optimize(node, values, parameters));
            negative.add(Boolean.valueOf(inverted));
            return;
        }
        if (node.op == ComSearchGenerator.OP_MUL) {
            collectProduct(node.left, inverted, parts, negative, values, parameters);
            collectProduct(node.right, inverted, parts, negative, values, parameters);
            return;
        }
        collectProduct(node.left, inverted, parts, negative, values, parameters);
        collectProduct(node.right, !inverted, parts, negative, values, parameters);
    }

    private static Ordered assemble(List<Ordered> parts, List<Boolean> negative, boolean additive,
            Parameters parameters) {
        int k = parts.size();
        if (k == 1) {
            return parts.get(0);
        }
        int primary = additive ? ComSearchGenerator.OP_ADD : ComSearchGenerator.OP_MUL;
        int secondary = additive ? ComSearchGenerator.OP_SUB : ComSearchGenerator.OP_DIV;
        @SuppressWarnings("unchecked")
        List<Built>[] bySize = new List[k + 1];
        for (int i = 0; i <= k; i++) {
            bySize[i] = new ArrayList<Built>();
        }
        for (int i = 0; i < k; i++) {
            Built single = new Built();
            single.used = 1 << i;
            single.sign = 0;
            single.cost = 0.0;
            single.value = parts.get(i).value;
            single.node = parts.get(i).node;
            single.weight = parts.get(i).weight;
            bySize[1].add(single);
        }
        for (int size = 2; size <= k; size++) {
            List<Built> found = new ArrayList<Built>();
            for (int leftSize = 1; leftSize < size; leftSize++) {
                for (int a = 0; a < bySize[leftSize].size(); a++) {
                    Built left = bySize[leftSize].get(a);
                    for (int b = 0; b < bySize[size - leftSize].size(); b++) {
                        Built right = bySize[size - leftSize].get(b);
                        if ((left.used & right.used) != 0) {
                            continue;
                        }
                        consider(found, left, right, primary, parameters);
                        consider(found, left, right, secondary, parameters);
                    }
                }
            }
            Map<Long, Built> best = new HashMap<Long, Built>();
            for (int i = 0; i < found.size(); i++) {
                Built candidate = found.get(i);
                long key = (((long) candidate.used) << k) | candidate.sign;
                Built previous = best.get(key);
                if (previous == null || candidate.cost < previous.cost) {
                    best.put(key, candidate);
                }
            }
            bySize[size].addAll(best.values());
        }
        int required = 0;
        for (int i = 0; i < k; i++) {
            if (negative.get(i).booleanValue()) {
                required |= 1 << i;
            }
        }
        Built chosen = null;
        for (int i = 0; i < bySize[k].size(); i++) {
            Built candidate = bySize[k].get(i);
            if (candidate.sign == required && (chosen == null || candidate.cost < chosen.cost)) {
                chosen = candidate;
            }
        }
        Ordered ordered = new Ordered();
        if (chosen == null) {
            ordered.node = parts.get(0).node;
            ordered.value = parts.get(0).value;
            ordered.weight = 1.0;
            return ordered;
        }
        ordered.node = chosen.node;
        ordered.value = chosen.value;
        ordered.weight = 1.0;
        return ordered;
    }

    private static void consider(List<Built> found, Built left, Built right, int op, Parameters parameters) {
        Rat value = combineValues(left.value, right.value, op);
        if (value == null) {
            return;
        }
        Built built = new Built();
        built.used = left.used | right.used;
        built.sign = left.sign | (op == ComSearchGenerator.OP_SUB || op == ComSearchGenerator.OP_DIV
                ? (right.sign ^ right.used)
                : right.sign);
        double step = combineOperands(operandCharge(left, op, true, parameters),
                operandCharge(right, op, false, parameters));
        built.cost = left.cost + right.cost + step;
        built.value = value;
        built.node = Node.internal(op, left.node, right.node);
        built.weight = 1.0;
        found.add(built);
    }

    private static double operandCharge(Built part, int op, boolean leftSide, Parameters parameters) {
        double weight = Integer.bitCount(part.used) == 1 ? part.weight : 1.0;
        return weight * parameters.sideFactor(op, leftSide) * valueCost(part.value);
    }

    private static Rat combineValues(Rat left, Rat right, int op) {
        if (left == null || right == null) {
            return null;
        }
        if (op == ComSearchGenerator.OP_ADD) {
            return left.add(right);
        }
        if (op == ComSearchGenerator.OP_SUB) {
            return left.subtract(right);
        }
        if (op == ComSearchGenerator.OP_MUL) {
            return left.multiply(right);
        }
        return left.divide(right);
    }

    private static Node parseNode(Expression expression) {
        Node[] stack = new Node[expression.order.length];
        int sp = 0;
        int valuesPointer = 0;
        int operationsPointer = 0;
        for (boolean isNumber : expression.order) {
            if (isNumber) {
                stack[sp++] = Node.leaf(expression.valueOrder[valuesPointer++] & 0xff);
            } else {
                Node right = stack[--sp];
                Node left = stack[--sp];
                stack[sp++] = Node.internal(expression.operations[operationsPointer++] & 0xff, left, right);
            }
        }
        return stack[0];
    }

    static final class Ordered {
        Node node;
        Rat value;
        double weight;

        Expression nodeToExpression() {
            return node.toExpression();
        }
    }

    static final class Built {
        int used;
        int sign;
        double cost;
        double weight;
        Rat value;
        Node node;
    }

    static final class Node {
        final boolean leaf;
        final int varIndex;
        final int op;
        final Node left;
        final Node right;

        private Node(boolean leaf, int varIndex, int op, Node left, Node right) {
            this.leaf = leaf;
            this.varIndex = varIndex;
            this.op = op;
            this.left = left;
            this.right = right;
        }

        static Node leaf(int varIndex) {
            return new Node(true, varIndex, -1, null, null);
        }

        static Node internal(int op, Node left, Node right) {
            return new Node(false, -1, op, left, right);
        }

        Expression toExpression() {
            if (leaf) {
                return new Expression(new byte[] {(byte) varIndex}, new byte[0], new boolean[] {true});
            }
            return Expression.combineWithOp(left.toExpression(), right.toExpression(), (byte) op);
        }
    }

    static final class Rat {
        final long p;
        final long q;

        private Rat(long p, long q) {
            this.p = p;
            this.q = q;
        }

        String text() {
            if (q == 1L) {
                return Long.toString(p);
            }
            return p + "/" + q;
        }

        static Rat fromDouble(double number) {
            if (!Double.isFinite(number)) {
                return null;
            }
            double rounded = Math.rint(number);
            if (Math.abs(number - rounded) <= UNIT_TOLERANCE && Math.abs(rounded) < 1e15) {
                return new Rat((long) rounded, 1L);
            }
            return continuedFraction(number);
        }

        Rat add(Rat other) {
            return reduced(p * other.q + other.p * q, q * other.q);
        }

        Rat subtract(Rat other) {
            return reduced(p * other.q - other.p * q, q * other.q);
        }

        Rat multiply(Rat other) {
            return reduced(p * other.p, q * other.q);
        }

        Rat divide(Rat other) {
            if (other.p == 0L) {
                return null;
            }
            return reduced(p * other.q, q * other.p);
        }

        private static Rat reduced(long numerator, long denominator) {
            if (denominator == 0L) {
                return null;
            }
            if (denominator < 0L) {
                numerator = -numerator;
                denominator = -denominator;
            }
            long divisor = gcd(numerator, denominator);
            return new Rat(numerator / divisor, denominator / divisor);
        }

        private static long gcd(long a, long b) {
            a = Math.abs(a);
            b = Math.abs(b);
            while (b != 0L) {
                long next = a % b;
                a = b;
                b = next;
            }
            return a;
        }

        private static Rat continuedFraction(double number) {
            boolean negative = number < 0;
            double rest = Math.abs(number);
            long n0 = 0L;
            long d0 = 1L;
            long n1 = 1L;
            long d1 = 0L;
            for (int i = 0; i < 40; i++) {
                long whole = (long) Math.floor(rest + 1e-12);
                long nextN = n1 * whole + n0;
                long nextD = d1 * whole + d0;
                n0 = n1;
                d0 = d1;
                n1 = nextN;
                d1 = nextD;
                if (d1 != 0L && Math.abs((double) n1 / (double) d1 - Math.abs(number)) <= UNIT_TOLERANCE) {
                    break;
                }
                double frac = rest - whole;
                if (frac < 1e-12) {
                    break;
                }
                rest = 1.0 / frac;
            }
            long numerator = negative ? -n1 : n1;
            return reduced(numerator, d1 == 0L ? 1L : d1);
        }
    }
}
