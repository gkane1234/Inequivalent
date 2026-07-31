package com.github.gkane1234;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.FileNotFoundException;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import java.util.Arrays;
import javax.swing.SwingWorker;
/**
    Solver class for finding solutions for a given goal using a set of values.

    It is a SwingWorker in order to allow for progress updates when used in the applet.
    By default, expression sets are built with {@link ComSearchGenerator} (no truncator dedupe).
*/
public class Solver extends SwingWorker<Void,String>{
    private static final int NUM_TRUNCATORS = 20;
    private static final int ROUNDING =9;
    private static final double TOLERANCE = 1e-5;

    private static final int MAX_SOLUTIONS = 200;
    ExpressionList solverSet;
    private int numValues;
    private boolean verbose;

    private final CountingOperationsApplet applet;

    private String status;
    /**
        Constructor for the Solver class.
        @param numValues: an <code>int</code> representing the number of values to use.
        @param verbose: a <code>boolean</code> representing whether to print verbose output.
        @param load: a <code>boolean</code> representing whether to load the expression list from a file.
        @param applet: a <code>CountingOperationsApplet</code> representing the applet to update, null if not in applet.
        @param compressed: a <code>boolean</code> representing whether to use compressed expression lists.
    */
    Solver(int numValues,boolean verbose, boolean load, CountingOperationsApplet applet, boolean compressed){
        this(numValues, verbose, load, applet, compressed, true);
    }

    /**
        @param useComSearch: if true, generate with {@link ComSearchGenerator}; otherwise {@link ExpressionDynamic}.
    */
    Solver(int numValues, boolean verbose, boolean load, CountingOperationsApplet applet,
           boolean compressed, boolean useComSearch) {
        this.verbose = verbose;
        this.applet = applet;
        if (load) {
            try {
                if (verbose) {
                    broadcast("Loading expression list...");
                }
                if (compressed) {
                    solverSet = CompressedExpressionList.loadCompressed(numValues);
                } else {
                    solverSet = ExpressionList.loadCompressed(numValues);
                }
            } catch (FileNotFoundException e) {
                if (verbose) {
                    broadcast("File not found, creating instead...");
                }
                solverSet = buildExpressionList(numValues, verbose, compressed, useComSearch);
                saveExpressionList(solverSet, compressed, verbose);
            }
        } else {
            solverSet = buildExpressionList(numValues, verbose, compressed, useComSearch);
        }
        if (verbose) {
            broadcast("Loaded "+solverSet.getNumExpressions()+" expressions.");
        }
        this.numValues=numValues;
    }

    private ExpressionList buildExpressionList(int numValues, boolean verbose,
                                               boolean compressed, boolean useComSearch) {
        if (useComSearch) {
            if (verbose) {
                broadcast("Generating expressions with ComSearch...");
            }
            return new ComSearchGenerator(numValues).toExpressionList();
        }
        return new ExpressionDynamic(numValues, ROUNDING, NUM_TRUNCATORS, null, verbose, compressed, true)
                .getExpressionList();
    }

    private void saveExpressionList(ExpressionList list, boolean compressed, boolean verbose) {
        if (compressed) {
            CompressedExpressionList compressedExpressionList =
                    ExpressionCompression.compressExpressionList(list);
            CompressedExpressionList.saveCompressed(compressedExpressionList, verbose);
            solverSet = compressedExpressionList;
        } else {
            ExpressionList.saveCompressed(list, verbose);
        }
    }
    @Override
    protected Void doInBackground() {
        publish(status);
        return null;

    }
    @Override
    public void process(List<String> chunks) {
        for (String chunk : chunks) {
            applet.onSolverUpdate(chunk);
        }
    }
    public int getNumValues() {
        return numValues;
    }
    /**
        Finds all solutions for a given goal using a set of values.
        @param values: an <code>double[]</code> representing the values to use.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @return a <code>SolutionSet</code> representing the solutions found.
    */
    public SolutionList findAllSolutions(double[] values, double goal,int maxSolutions) {
        return ExpressionList.findSolutions(solverSet, values, goal, Solver.ROUNDING, maxSolutions, verbose);
    }

    /**
     * Solve by streaming ComSearch expressions (does not use {@link #solverSet}).
     * Useful when you do not want to materialize the full expression list first.
     */
    public SolutionList findAllSolutionsStreaming(double[] values, double goal, int maxSolutions) {
        SolutionList solutions = new SolutionList(values, goal);
        new ComSearchGenerator(numValues).generate(expression -> {
            if (solutions.getNumSolutions() >= maxSolutions) {
                return;
            }
            double value = expression.evaluateWithValues(values, ROUNDING);
            if (equal(value, goal)) {
                solutions.addEvaluatedExpression(new EvaluatedExpression(expression, values, value));
            }
        });
        return solutions;
    }
    /**
        Finds all solutions for a given goal using a set of values.
        @param values: an <code>int[]</code> representing the values to use.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @return a <code>SolutionSet</code> representing the solutions found.
    */
    public SolutionList findAllSolutions(int[] values, double goal,int maxSolutions) {
        double[] doubleValues = new double[values.length];
        for (int i=0;i<values.length;i++) {
            doubleValues[i]=values[i];
        }
        return findAllSolutions(doubleValues,goal,maxSolutions);

    }
    /**
        Finds upto one solution for a given goal using a set of values.
        @param values: an <code>double[]</code> representing the values to use.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @return a <code>Solution</code> representing the first solution found.
    */
    public EvaluatedExpression findFirstSolution(double[] values, double goal) {
        broadcast("Finding first solution for "+goal+" with values "+Arrays.toString(values));
        for (int i=0;i<solverSet.getNumExpressions();i++) {
            if (verbose&&i%100000==0) {
                broadcast("Evaluating expression "+i+" of "+solverSet.getNumExpressions());
            }
            if (Solver.equal(solverSet.get(i).evaluateWithValues(values,Solver.ROUNDING),goal)) {
                return new EvaluatedExpression(solverSet.get(i),values,goal);
            }
        }
        return null;
    }
    /**
        Finds upto one solution for a given goal using a set of values.
        @param values: an <code>int[]</code> representing the values to use.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @return a <code>Solution</code> representing the first solution found.
    */
    public EvaluatedExpression findFirstSolution(int[] values, double goal) {
        double[] doubleValues = new double[values.length];
        for (int i=0;i<values.length;i++) {
            doubleValues[i]=values[i];
        }
        return findFirstSolution(doubleValues, goal);

    }
    /**
        Finds a random set of values that can be used to make a given goal.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @param valueRange: an <code>int[]</code> representing the range of values to use.
        @param solutionRange: an <code>int[]</code> representing the range of solutions to find.
        @return a <code>SolutionList</code> representing the solutions found.
    */
    public SolutionList findSolvableValues(double goal, int[] valueRange, int[] solutionRange) throws Exception {
        final int MAX_ATTEMPTS = 1000;
        broadcast("Finding solvable values for "+goal+" with values in range "+Arrays.toString(valueRange)+" and solutions in range "+Arrays.toString(solutionRange)+" and "+MAX_ATTEMPTS+" attempts");
        Random r = new Random();

        int attempts = 0;

        SolutionList solutions=null;

        while (attempts<MAX_ATTEMPTS) {

            attempts++;
            int[] nextAttempt = r.ints(numValues, valueRange[0], valueRange[1]).toArray();
            if (this.verbose) {

                broadcast("Attempting: "+attempts);
                broadcast(" ");
                broadcast(Arrays.toString(nextAttempt));
            }
    
            solutions = findAllSolutions(nextAttempt, goal,solutionRange[1]+1);
            
            if (solutionRange[0] <= solutions.getNumSolutions() && solutions.getNumSolutions() <= solutionRange[1]) {
                broadcast("Constraints met with "+solutions.getNumSolutions()+" solutions.");
                return solutions;
            }
        }
        broadcast("Constraints not met within "+MAX_ATTEMPTS+" attempts." + solutions.getNumSolutions()+" solutions found.");
        throw new Exception("Constraints not met within "+MAX_ATTEMPTS+" attempts");
    }

    /**
        Finds a list of random sets of values that can be used to make a given goal.
        @param numSolutions: an <code>int</code> representing the number of solutions to find.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @param valueRange: an <code>int[]</code> representing the range of values to use.
        @param solutionRange: an <code>int[]</code> representing the range of solutions to find.
        @return a <code>List<SolutionList></code> representing the solutions found.
    */
    public List<SolutionList> findListOfSolvableValues(int numSolutions,double goal, int[] valueRange, int[] solutionRange) throws Exception {
        broadcast("Finding "+numSolutions+" solvable values for "+goal+" with values in range "+Arrays.toString(valueRange)+" and solutions in range "+Arrays.toString(solutionRange));
        List<SolutionList> solvables = new ArrayList<>();


        while(solvables.size()<numSolutions) {
            broadcast("Attempting: "+solvables.size());
            solvables.add(findSolvableValues(goal, valueRange, solutionRange));
        }
        return solvables; 
    }
    /**
        Finds a random set of values that can be used to make a given goal.
        @param numSolutions: an <code>int</code> representing the number of solutions to find.
        @return a <code>List<SolutionSet></code> representing the solutions found.
    */
    public List<SolutionList> findListOfSolvableValues(int numSolutions,double goal) throws Exception {
        final int[] defualtRange = new int[]{1,15};
        final int[] defaultSolutionRange = new int[]{1,100*this.numValues};
        return findListOfSolvableValues(numSolutions,goal,defualtRange,defaultSolutionRange);
    }
    /**
        Finds all possible sets of values that can be used to make a given goal.
        @param range: an <code>int[]</code> representing the range of values to use.
        @param goal: a <code>double</code> representing the goal to find solutions for.
        @param findAllSolutions: a <code>boolean</code> representing whether to find all solutions or just one.
        @return a <code>List<SolutionSet></code> representing the solutions found.
    */
    public List<SolutionList> findAllPossibleSolvableValuesInRange(int[] range,double goal,boolean findAllSolutions) {
        //Finds all possible solvable sets of numbers to make the goal
        // Will do so only returning values in non-descreasing order
        RangeIterator allValuesInRangeIterator = new RangeIterator(range, this.numValues);
        List<SolutionList> solvables = new ArrayList<>();
        SolutionList nextSolutionList;
        int tracker = 0;
        while (allValuesInRangeIterator.hasNext()) {
            int[] values = allValuesInRangeIterator.next();
            if (++tracker%1000==0) {
                System.err.println(Arrays.toString(values));
            }
            if (findAllSolutions) {
                nextSolutionList = findAllSolutions(values, goal,MAX_SOLUTIONS);
            } else {
                EvaluatedExpression firstSolution = findFirstSolution(values, goal);
                nextSolutionList = new SolutionList(values,goal);
                if (firstSolution!=null) {
                    nextSolutionList.addEvaluatedExpression(firstSolution);
                }
            }
            solvables.add(nextSolutionList);
        }

        return solvables;

        




    }
    /**
        Finds the first value in a given range that can/can't be created using a set of values.
        @param values: an <code>double[]</code> representing the values to use.
        @param goalRange: an <code>int[]</code> representing the range of goals to use.
        @param works: a <code>boolean</code> representing whether to find the first value that can be created or the first value that cannot be created.
        @param output: a <code>boolean</code> representing whether to print the value and the solution that creates it.
        @return an <code>int</code> representing the first value in the range that can be created or cannot be created.
    */
    public int findFirstInRange(double[] values,int[] goalRange,boolean works,boolean output) {
        // If works is true: Returns the first value that can be created using the values
        // If works is false: Returns the first value that cannot be created using the values
        TIntHashSet intSet = new TIntHashSet();
        TIntObjectHashMap <EvaluatedExpression> outputs = new TIntObjectHashMap<>();


        EvaluatedExpressionList evaluatedExpressionList = ExpressionList.evaluate(solverSet, values, Solver.ROUNDING);
        for (EvaluatedExpression evaluatedExpression : evaluatedExpressionList.getEvaluatedExpressionList()) {
            double answer = evaluatedExpression.getValue();
            if (Solver.equal(answer, Math.round(answer))) {
                int wholeNumberAnswer = (int)Math.round(answer);
                
                //System.err.println(wholeNumberAnswer);
                if (intSet.add(wholeNumberAnswer)&&output) {
                    outputs.put(wholeNumberAnswer, evaluatedExpression);
                }
            }
            
            
        }
        int i = goalRange[0];
        int delta = goalRange[0]<goalRange[1]? 1: -1;
        while (true){
            boolean inside  = intSet.contains(i);
            i+=delta;
            if (output) {
                broadcast(String.valueOf(i));
                broadcast(" ");
                broadcast(outputs.get(i).toString());

            }
                
            
            if (inside!=works) {
                return i;
            }
        }
    }
    public void broadcast(String message) {
        if (this.applet!=null) {
            publish(message);
        } else {
            System.out.println(message);
        }
    }
    /**
        Checks if two doubles are equal within a given tolerance.
        @param a: a <code>double</code> representing the first value to compare.
        @param b: a <code>double</code> representing the second value to compare.
        @return a <code>boolean</code> representing whether the two values are equal within the given tolerance.
    */
    public static boolean equal(double a, double b) {
        return Math.abs(a-b)<=TOLERANCE;
    }
}
