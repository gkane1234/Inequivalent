package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Groups solved lines that differ only by where a 0 or a 1 is parked.
 *
 * <p>A value that is 0 or 1, whether it was dealt or computed, keeps the
 * subtree that produced it. Multiplying or dividing by 1, and adding or
 * subtracting 0, drop that attachment. Using the 0 or 1 for anything else
 * bakes the subtree into the core. The signature is that core plus the
 * multiset of dropped subtrees. Sums and products are flattened and ordered
 * so the attachment point is the only thing this pass removes.
 */
public final class IdentityDedupe {
    /** Close enough to 0 or 1 for a computed unit such as {@code 3/3}. */
    static final double UNIT_TOLERANCE = 1e-9;

    private IdentityDedupe() {}

    /**
     * Canonical key for one solved line. Two lines are the same idea when
     * the keys match.
     */
    public static String signature(Expression expression, double[] values) {
        Part root = evaluate(expression, values);
        String core = root.core == null ? canon(root.tree, values) : canon(root.core, values);
        if (root.stripped.isEmpty()) {
            return core;
        }
        List<String> dropped = new ArrayList<String>(root.stripped.size());
        for (Node node : root.stripped) {
            dropped.add(canon(node, values));
        }
        Collections.sort(dropped);
        StringBuilder key = new StringBuilder(core);
        for (String piece : dropped) {
            key.append(" || ").append(piece);
        }
        return key.toString();
    }

    private static Part evaluate(Expression expression, double[] values) {
        Part[] stack = new Part[expression.order.length];
        int sp = 0;
        int valuesPointer = 0;
        int operationsPointer = 0;
        for (boolean isNumber : expression.order) {
            if (isNumber) {
                int varIndex = expression.valueOrder[valuesPointer++] & 0xff;
                stack[sp++] = leaf(varIndex, values[varIndex]);
            } else {
                Part right = stack[--sp];
                Part left = stack[--sp];
                int op = expression.operations[operationsPointer++] & 0xff;
                stack[sp++] = combine(op, left, right);
            }
        }
        if (sp != 1) {
            throw new IllegalStateException("Invalid expression");
        }
        return stack[0];
    }

    private static Part leaf(int varIndex, double value) {
        Part part = new Part();
        part.value = value;
        part.tree = Node.leaf(varIndex);
        part.stripped = new ArrayList<Node>();
        if (isUnit(value)) {
            part.core = null;
        } else {
            part.core = part.tree;
        }
        return part;
    }

    private static Part combine(int op, Part left, Part right) {
        if (isIdentityOperand(op, right.value, false)) {
            return strip(left, right);
        }
        if (isIdentityOperand(op, left.value, true)) {
            return strip(right, left);
        }
        return bake(op, left, right);
    }

    /**
     * Drop {@code identity} and keep {@code survivor}. A survivor that is
     * itself 0 or 1 stays marked, so {@code 1+0} can still be multiplied
     * by a later 1.
     */
    private static Part strip(Part survivor, Part identity) {
        Part out = new Part();
        out.value = survivor.value;
        out.core = survivor.core;
        out.tree = survivor.tree;
        out.stripped = new ArrayList<Node>(survivor.stripped.size() + identity.stripped.size() + 1);
        out.stripped.addAll(survivor.stripped);
        out.stripped.add(identity.tree);
        out.stripped.addAll(identity.stripped);
        return out;
    }

    private static Part bake(int op, Part left, Part right) {
        Part out = new Part();
        out.value = apply(op, left.value, right.value);
        Node core = Node.internal(op, material(left), material(right));
        out.stripped = new ArrayList<Node>(left.stripped.size() + right.stripped.size());
        out.stripped.addAll(left.stripped);
        out.stripped.addAll(right.stripped);
        if (isUnit(out.value)) {
            out.tree = core;
            out.core = null;
        } else {
            out.tree = core;
            out.core = core;
        }
        return out;
    }

    private static Node material(Part part) {
        return part.core == null ? part.tree : part.core;
    }

    private static boolean isIdentityOperand(int op, double value, boolean onLeft) {
        if (onLeft) {
            return (op == ComSearchGenerator.OP_MUL && isOne(value))
                    || (op == ComSearchGenerator.OP_ADD && isZero(value));
        }
        return (op == ComSearchGenerator.OP_MUL && isOne(value))
                || (op == ComSearchGenerator.OP_DIV && isOne(value))
                || ((op == ComSearchGenerator.OP_ADD || op == ComSearchGenerator.OP_SUB) && isZero(value));
    }

    private static boolean isUnit(double value) {
        return isZero(value) || isOne(value);
    }

    private static boolean isZero(double value) {
        return !Double.isNaN(value) && Math.abs(value) <= UNIT_TOLERANCE;
    }

    private static boolean isOne(double value) {
        return !Double.isNaN(value) && Math.abs(value - 1.0) <= UNIT_TOLERANCE;
    }

    private static double apply(int op, double a, double b) {
        if (op == ComSearchGenerator.OP_ADD) {
            return a + b;
        }
        if (op == ComSearchGenerator.OP_SUB) {
            return a - b;
        }
        if (op == ComSearchGenerator.OP_MUL) {
            return a * b;
        }
        if (op == ComSearchGenerator.OP_DIV) {
            return b == 0.0 ? Double.NaN : a / b;
        }
        throw new IllegalArgumentException("unknown op " + op);
    }

    private static String canon(Node node, double[] values) {
        if (node.leaf) {
            return formatValue(values[node.varIndex]);
        }
        if (node.op == ComSearchGenerator.OP_ADD || node.op == ComSearchGenerator.OP_MUL) {
            List<Node> parts = new ArrayList<Node>();
            flatten(node, node.op, parts);
            List<String> keys = new ArrayList<String>(parts.size());
            for (Node part : parts) {
                keys.add(canon(part, values));
            }
            Collections.sort(keys);
            return "(" + join(keys, node.op == ComSearchGenerator.OP_ADD ? "+" : "*") + ")";
        }
        return "(" + canon(node.left, values) + symbol(node.op) + canon(node.right, values) + ")";
    }

    private static void flatten(Node node, int op, List<Node> out) {
        if (!node.leaf && node.op == op) {
            flatten(node.left, op, out);
            flatten(node.right, op, out);
        } else {
            out.add(node);
        }
    }

    private static String join(List<String> keys, String symbol) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(symbol);
            }
            text.append(keys.get(i));
        }
        return text.toString();
    }

    private static String symbol(int op) {
        if (op == ComSearchGenerator.OP_ADD) {
            return "+";
        }
        if (op == ComSearchGenerator.OP_SUB) {
            return "-";
        }
        if (op == ComSearchGenerator.OP_MUL) {
            return "*";
        }
        return "/";
    }

    private static String formatValue(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) <= UNIT_TOLERANCE && Math.abs(rounded) < 1e15) {
            return Long.toString((long) rounded);
        }
        return Double.toString(value);
    }

    private static final class Part {
        double value;
        Node tree;
        Node core;
        List<Node> stripped;
    }

    private static final class Node {
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
    }
}
