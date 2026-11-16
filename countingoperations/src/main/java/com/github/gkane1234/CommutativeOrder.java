package com.github.gkane1234;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rewrites a hit so sums and products are ordered by the display text of their
 * parts. Subtraction and division keep operand order. Used only when the
 * repeated-number dedupe option is on; the search itself is unchanged.
 */
public final class CommutativeOrder {
    private CommutativeOrder() {}

    public static Expression reorder(Expression expression, double[] values) {
        Node root = reorder(parse(expression), values);
        return toExpression(root);
    }

    private static Node reorder(Node node, double[] values) {
        if (node.leaf) {
            node.key = display(node, values);
            return node;
        }
        node.left = reorder(node.left, values);
        node.right = reorder(node.right, values);
        if (node.op != ComSearchGenerator.OP_ADD && node.op != ComSearchGenerator.OP_MUL) {
            node.key = display(node, values);
            return node;
        }
        List<Node> parts = new ArrayList<Node>();
        flatten(node.left, node.op, parts);
        flatten(node.right, node.op, parts);
        parts.sort(Comparator.comparing(part -> part.key));
        Node folded = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            folded = Node.internal(node.op, folded, parts.get(i));
        }
        folded.key = display(folded, values);
        return folded;
    }

    private static void flatten(Node node, int op, List<Node> out) {
        if (!node.leaf && node.op == op) {
            flatten(node.left, op, out);
            flatten(node.right, op, out);
        } else {
            out.add(node);
        }
    }

    private static String display(Node node, double[] values) {
        return new EvaluatedExpression(toExpression(node), values, 0).display();
    }

    private static Node parse(Expression expression) {
        Node[] stack = new Node[expression.order.length];
        int sp = 0;
        int valuesPointer = 0;
        int operationsPointer = 0;
        for (boolean isNumber : expression.order) {
            if (isNumber) {
                int varIndex = expression.valueOrder[valuesPointer++] & 0xff;
                stack[sp++] = Node.leaf(varIndex);
            } else {
                Node right = stack[--sp];
                Node left = stack[--sp];
                int op = expression.operations[operationsPointer++] & 0xff;
                stack[sp++] = Node.internal(op, left, right);
            }
        }
        if (sp != 1) {
            throw new IllegalStateException("Invalid expression");
        }
        return stack[0];
    }

    private static Expression toExpression(Node root) {
        int leaves = countLeaves(root);
        int internals = countInternals(root);
        byte[] valueOrder = new byte[leaves];
        byte[] operations = new byte[internals];
        boolean[] order = new boolean[leaves + internals];
        int[] valuesPointer = {0};
        int[] operationsPointer = {0};
        int[] index = {0};
        write(root, valueOrder, operations, order, valuesPointer, operationsPointer, index);
        return new Expression(valueOrder, operations, order);
    }

    private static void write(Node node, byte[] valueOrder, byte[] operations, boolean[] order,
                              int[] valuesPointer, int[] operationsPointer, int[] index) {
        if (node.leaf) {
            valueOrder[valuesPointer[0]++] = (byte) node.varIndex;
            order[index[0]++] = true;
            return;
        }
        write(node.left, valueOrder, operations, order, valuesPointer, operationsPointer, index);
        write(node.right, valueOrder, operations, order, valuesPointer, operationsPointer, index);
        operations[operationsPointer[0]++] = (byte) node.op;
        order[index[0]++] = false;
    }

    private static int countLeaves(Node node) {
        return node.leaf ? 1 : countLeaves(node.left) + countLeaves(node.right);
    }

    private static int countInternals(Node node) {
        return node.leaf ? 0 : 1 + countInternals(node.left) + countInternals(node.right);
    }

    private static final class Node {
        final boolean leaf;
        final int varIndex;
        final int op;
        Node left;
        Node right;
        String key;

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
