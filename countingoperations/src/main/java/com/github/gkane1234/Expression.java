package com.github.gkane1234;
import java.util.*;
import java.io.Serializable;
 /**
    This class is used to represent an expression in Reverse Polish Notation (RPN) as a list of values and operations and an order.
    
    To evaluate the expression with specific values use the method evaluate_with_values.
*/
public class Expression implements Serializable{
    private static final long serialVersionUID = 1L;
   
    public byte[] valueOrder;
    public byte[] operations;
    public boolean[] order;
    /**
        Constructor for the Expression class.
        @param value_order: the order that the values are evaluated in the expression.
        @param operations: the operations of the expression.
        @param order: the order of the expression. 

        For example: 
        The expression ((a+b)*c) is represented as value_order = [0,1,2] and operations = [0,2] and order = [true,true,false,true,false]
        To evaluate this expression with values a=3, b=4.7, c=5 the method evaluate_with_values would be used with values = [3,4.7,5].
    */
    public Expression(byte[] valueOrder,byte[] operations,boolean[] order) {
        int numValues = 0;
        for (int i=0;i<order.length;i++) {
            if (order[i]) {
                numValues++;
            }
        }
        if (numValues!=valueOrder.length) {
            throw new IllegalArgumentException("Invalid expression: " + Arrays.toString(order));
        }
        this.valueOrder=valueOrder;
        this.operations=operations;
        this.order=order;
    }

    public Expression(Expression expression) {
        this.valueOrder=expression.valueOrder;
        this.operations=expression.operations;
        this.order=expression.order;
    }


    /**
    Changes the value order of the expression.
    These values represent the order of the numbers in the expression.
    @param valueOrder: the new value order of the expression.
    */
    public Expression changeValueOrder(byte[] valueOrder) {
        
        byte[] newValues = new byte[valueOrder.length];
        for (byte i=0;i<valueOrder.length;i++) {
            newValues[i]=valueOrder[this.valueOrder[i]];
        }

        return new Expression(newValues,this.operations,this.order);
    }
    /**
        Evaluates the expression with specific values.
        @param values: the values of the expression.
        @param rounding: the number of decimal places to round the result to.
    */
    public double evaluateWithValues(double[] values, int rounding) {
        return evaluateWithValues(values, rounding, null, null);
    }

    /**
     * Evaluate without allocating remap/stack when scratch buffers are provided.
     * {@code remapScratch} length &gt;= valueOrder.length; {@code stackScratch} length &gt;= order.length.
     */
    public double evaluateWithValues(double[] values, int rounding,
                                     double[] remapScratch, double[] stackScratch) {
        double[] remapped = remapScratch != null && remapScratch.length >= valueOrder.length
                ? remapScratch
                : new double[valueOrder.length];
        for (int i = 0; i < valueOrder.length; i++) {
            remapped[i] = values[valueOrder[i] & 0xff];
        }
        return evaluateRpn(remapped, rounding, stackScratch);
    }

    @Override
    public String toString() {
        return Expression.convertToParenthetical(this);
    
    }

    /**
    Evaluates the expression with specific values.
    @param values: the values of the expression.
    @param rounding: the number of decimal places to round the result to.
    */
    private double evaluateRpn(double[] values, int rounding, double[] stackScratch) {
        double[] stack = stackScratch != null && stackScratch.length >= order.length
                ? stackScratch
                : new double[order.length];
        int sp = 0;
        int valuesPointer = 0;
        int operationsPointer = 0;
        Operation[] ops = Operation.getOperations();

        for (boolean isNumber : order) {
            if (isNumber) {
                stack[sp++] = values[valuesPointer++];
            } else {
                if (sp < 2) {
                    throw new IllegalStateException("Invalid expression: " + this.toString());
                }
                double b = stack[--sp];
                double a = stack[--sp];
                double result = ops[operations[operationsPointer++]].apply(a, b);
                if (Double.isNaN(result)) {
                    return result;
                }
                stack[sp++] = result;
            }
        }
        double nonRounded = stack[--sp];
        double factor = Math.pow(10, rounding);
        return Math.round(nonRounded * factor) / factor;
    }

    public boolean equals(Expression expression) {
        return Arrays.equals(this.valueOrder, expression.valueOrder) && Arrays.equals(this.operations, expression.operations) && Arrays.equals(this.order, expression.order);
    }
    /**
     * Pretty-print with only the parentheses required by operator precedence
     * (so {@code (a+b)*c} stays parenthesized, but {@code (a+b)+c} becomes {@code a+b+c}).
     */
    public static String convertToParenthetical(Expression expression) {
        return formatInfix(expression, i -> String.valueOf(expression.valueOrder[i] & 0xff));
    }

    /**
     * Same precedence rules as {@link #convertToParenthetical}, but leaf text comes
     * from {@code leafText} (e.g. concrete values for {@link EvaluatedExpression#display()}).
     */
    public static String formatInfix(Expression expression,
                                    java.util.function.IntFunction<String> leafText) {
        ArrayStack<InfixFrag> stack = new ArrayStack<>(expression.order.length);
        int valuesPointer = 0;
        int operationsPointer = 0;
        for (boolean isNumber : expression.order) {
            if (isNumber) {
                if (valuesPointer >= expression.valueOrder.length) {
                    throw new IllegalStateException(
                            "Invalid expression: " + Arrays.toString(expression.order));
                }
                stack.push(InfixFrag.leaf(leafText.apply(valuesPointer++)));
            } else {
                if (operationsPointer >= expression.operations.length) {
                    throw new IllegalStateException(
                            "Invalid expression: " + Arrays.toString(expression.order));
                }
                byte op = expression.operations[operationsPointer++];
                InfixFrag b = stack.pop();
                InfixFrag a = stack.pop();
                stack.push(InfixFrag.combine(a, b, op));
            }
        }
        return stack.pop().text;
    }

    private static final class InfixFrag {
        final String text;
        /** 0 = atom, 1 = +/-, 2 = * or /. */
        final int precedence;

        private InfixFrag(String text, int precedence) {
            this.text = text;
            this.precedence = precedence;
        }

        static InfixFrag leaf(String text) {
            return new InfixFrag(text, 0);
        }

        static InfixFrag combine(InfixFrag left, InfixFrag right, byte op) {
            int prec = precedenceOf(op);
            char symbol = Operation.getOperations()[op].getName();
            String a = needsParens(left, prec, true) ? "(" + left.text + ")" : left.text;
            String b = needsParens(right, prec, false) ? "(" + right.text + ")" : right.text;
            return new InfixFrag(a + symbol + b, prec);
        }

        private static int precedenceOf(byte op) {
            // + - lower than * /
            return (op == 0 || op == 1) ? 1 : 2;
        }

        /**
         * Left-associative ops: left child at same precedence needs no parens;
         * right child at same (or lower) precedence does, so {@code a-(b-c)} stays clear.
         */
        private static boolean needsParens(InfixFrag child, int parentPrec, boolean isLeft) {
            if (child.precedence == 0) {
                return false;
            }
            if (child.precedence < parentPrec) {
                return true;
            }
            if (child.precedence > parentPrec) {
                return false;
            }
            return !isLeft;
        }
    }

    /**
        Creates an expression from a parenthetical string.
        @param expression: the parenthetical string to convert.
    */ 
    public static Expression createExpressionFromString(String expression) {
        
        //TODO: implement this method
        throw new UnsupportedOperationException("Not implemented");
    }
    /**
        Creates all possible expressions made by combining two expressions.
        @param expression1: the first expression to combine.
        @param expression2: the second expression to combine.

        Avoids returning expressions that are the same up to commutativity.
    */
    public static Expression[] createCombinedExpressions(Expression expression1, Expression expression2) {
        
        int index=0;
        Expression[] toReturn = new Expression[Operation.getNumOperationOrderings()];
        for (byte opCode=0;opCode<Operation.getOperations().length;opCode++) {
            if (!Operation.getOperations()[opCode].isCommutative()) {
                toReturn[index++]=combineExpressions(expression1, expression2, opCode);
                toReturn[index++]=combineExpressions(expression2, expression1, opCode);
            } else {
                toReturn[index++]=combineExpressions(expression1, expression2, opCode);
            }
        }
        return toReturn;
    }

    /**
     *Combines two expressions.
     *@param expr1: the first expression to combine.
     *@param expr2: the second expression to combine.
     *@param opCode: the operation to combine the expressions.
     */

    private static Expression combineExpressions(Expression expr1, Expression expr2, byte opCode) {
        return combineWithOp(expr1, expr2, opCode);
    }

    /**
     * Combines two expressions with a single binary operation (RPN append).
     * Op codes: 0 +, 1 -, 2 *, 3 /.
     */
    public static Expression combineWithOp(Expression expr1, Expression expr2, byte opCode) {
        byte[] newValueOrder = combine(expr1.valueOrder, expr2.valueOrder);
        byte[] newOperations = combineWithExtraSpot(expr1.operations, expr2.operations);
        newOperations[newOperations.length - 1] = opCode;
        boolean[] newOrder = combineWithExtraSpot(expr1.order, expr2.order);
        newOrder[newOrder.length - 1] = false;
        return new Expression(newValueOrder, newOperations, newOrder);
    }
    /**
        Combines two byte arrays.
        @param arr1: the first byte array to combine.
        @param arr2: the second byte array to combine.
    */
    public static byte[] combine(byte[] arr1, byte[] arr2) {
        
        byte[] newArr = new byte[arr1.length + arr2.length];
        

        System.arraycopy(arr1, 0, newArr, 0, arr1.length);
        System.arraycopy(arr2, 0, newArr, arr1.length, arr2.length);
        
        return newArr;
    }

    // Overloaded method for combining byte arrays
    /**
        Combines two byte arrays with an extra spot.
        @param arr1: the first byte array to combine.
        @param arr2: the second byte array to combine.

        Used in combining expressions.
    */
    public static byte[] combineWithExtraSpot(byte[] arr1, byte[] arr2) {
        
        byte[] newArr = new byte[arr1.length + arr2.length+1];
        
        System.arraycopy(arr1, 0, newArr, 0, arr1.length);
        System.arraycopy(arr2, 0, newArr, arr1.length, arr2.length);
        
        return newArr;
    }
    /**
        Combines two boolean arrays with an extra spot.
        @param arr1: the first boolean array to combine.
        @param arr2: the second boolean array to combine.

        Used in combining expressions.
    */
    public static boolean[] combineWithExtraSpot(boolean[] arr1, boolean[] arr2) {
        
        boolean[] newArr = new boolean[arr1.length + arr2.length+1];
        System.arraycopy(arr1, 0, newArr, 0, arr1.length);
        System.arraycopy(arr2, 0, newArr, arr1.length, arr2.length);
        return newArr;
    }

}

