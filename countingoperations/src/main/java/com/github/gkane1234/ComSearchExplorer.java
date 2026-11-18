package com.github.gkane1234;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * Small Swing explorer for live ComSearch solving.
 * Start streams matches as they are found; Stop keeps the output; Reset clears it.
 */
public class ComSearchExplorer extends JFrame {
    private final JSpinner countSpinner;
    private final JTextField minRangeField;
    private final JTextField maxRangeField;
    private final JPanel valuesPanel;
    private JTextField[] valueFields;
    private final JTextField goalField;
    private final JCheckBox dedupeRepeatedBox;
    private final JTextField minSolutionsField;
    private final JTextField maxSolutionsField;
    private final JComboBox<String> sortBox;
    private final JTextArea outputArea;
    private final JLabel statusLabel;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton resetButton;
    private final JButton randomizeButton;
    private final JButton rangeButton;
    private final JButton explainButton;

    


    private final Random random = new Random();

    private Solver solver;
    private final List<Solver> liveSolvers = java.util.Collections.synchronizedList(new ArrayList<Solver>());
    private SwingWorker<?, ?> worker;
    private boolean running;
    private final AtomicInteger solutionCount = new AtomicInteger();
    private final AtomicLong checkedCount = new AtomicLong();
    private final AtomicBoolean userStopped = new AtomicBoolean(false);
    private List<RangeSurvey.Row> surveyRows = new ArrayList<RangeSurvey.Row>();
    private boolean showingSurvey;
    private int surveyScanned;
    private long surveyTotal;
    private long surveyElapsedMs;
    private boolean surveyStopped;
    private int surveyN;
    private int surveyMin;
    private int surveyMax;
    private double surveyGoal;
    private boolean surveyDedupe;

    public ComSearchExplorer() {
        super("ComSearch Explorer");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Count:"));
        // No small cap — any practical n (memory/time will limit you first).
        countSpinner = new JSpinner(new SpinnerNumberModel(4, 1, Integer.MAX_VALUE, 1));
        ((JSpinner.DefaultEditor) countSpinner.getEditor()).getTextField().setColumns(4);
        countSpinner.addChangeListener(e -> rebuildValueFields(false));
        top.add(countSpinner);

        top.add(new JLabel("Range:"));
        minRangeField = new JTextField("1", 4);
        maxRangeField = new JTextField("13", 4);
        top.add(minRangeField);
        top.add(new JLabel("–"));
        top.add(maxRangeField);

        randomizeButton = new JButton("Randomize");
        randomizeButton.addActionListener(e -> randomizeValues());
        top.add(randomizeButton);

        top.add(new JLabel("Goal:"));
        goalField = new JTextField("24", 8);
        top.add(goalField);
        dedupeRepeatedBox = new JCheckBox("Dedupe repeated numbers");
        top.add(dedupeRepeatedBox);

        JPanel survey = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        survey.setBorder(BorderFactory.createTitledBorder("Range survey"));
        survey.add(new JLabel("Solutions:"));
        minSolutionsField = new JTextField("1", 4);
        maxSolutionsField = new JTextField("", 4);
        minSolutionsField.setToolTipText("Minimum solution count to keep. Blank counts as 0.");
        maxSolutionsField.setToolTipText("Maximum solution count to keep. Blank means no maximum.");
        survey.add(minSolutionsField);
        survey.add(new JLabel("–"));
        survey.add(maxSolutionsField);
        survey.add(new JLabel("Sort:"));
        sortBox = new JComboBox<String>(new String[] {"Solution count", "Difficulty"});
        sortBox.setToolTipText("Solution count lists the most solutions first. Difficulty lists the higher scores first. Lower is easier.");
        survey.add(sortBox);
        rangeButton = new JButton("Search range");
        rangeButton.setToolTipText("Count solutions for every nondecreasing set of values in the range.");
        rangeButton.addActionListener(e -> startRangeSearch());
        survey.add(rangeButton);
        sortBox.addActionListener(e -> {
            if (surveyIdle()) {
                renderSurvey();
            }
        });
        FocusAdapter refreshSurvey = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (surveyIdle()) {
                    renderSurvey();
                }
            }
        };
        minSolutionsField.addFocusListener(refreshSurvey);
        maxSolutionsField.addFocusListener(refreshSurvey);
        minSolutionsField.addActionListener(e -> {
            if (surveyIdle()) {
                renderSurvey();
            }
        });
        maxSolutionsField.addActionListener(e -> {
            if (surveyIdle()) {
                renderSurvey();
            }
        });

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(survey, BorderLayout.SOUTH);
        controls.add(north, BorderLayout.NORTH);

        valuesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        valuesPanel.setBorder(BorderFactory.createTitledBorder("Values"));
        JScrollPane valuesScroll = new JScrollPane(valuesPanel);
        valuesScroll.setPreferredSize(new Dimension(640, 90));
        valuesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        valuesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        controls.add(valuesScroll, BorderLayout.CENTER);
        rebuildValueFields(true);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        resetButton = new JButton("Reset");
        stopButton.setEnabled(false);
        startButton.addActionListener(e -> startSearch());
        stopButton.addActionListener(e -> stopSearch());
        resetButton.addActionListener(e -> resetOutput());
        explainButton = new JButton("Explain difficulty");
        explainButton.setToolTipText("Solve the numbers and goal above, then show each step's cost and the parallel sum.");
        explainButton.addActionListener(e -> explainDifficulty());
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(resetButton);
        buttons.add(explainButton);
        controls.add(buttons, BorderLayout.SOUTH);

        add(controls, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setPreferredSize(new Dimension(640, 360));
        scroll.setBorder(BorderFactory.createTitledBorder("Results"));
        JLabel runExplanation = new JLabel("<html><body style='width:760px'>"
                + "Start walks inequivalent expressions for the values above and checks them against the goal. "
                + "Explain difficulty solves that same deal and shows the cost of each step, then the parallel sum. "
                + "Nothing is stored ahead of time, and Stop keeps the lines already printed. "
                + "Leave Dedupe repeated numbers off to keep each hit in the index order the search already uses. "
                + "Turn it on and each hit reorders its sums and products by the text of those parts, "
                + "then a set keeps one copy of each line. The expressions that get checked do not change."
                + "<br><br>Search range counts solutions for every nondecreasing set of that many integers "
                + "in the range (1–10 with 4 numbers is 1 1 1 1, then 1 1 1 2, through 10 10 10 10). "
                + "The solution window keeps a count range, and Sort orders that list. "
                + "Solution count puts the most solutions first. Difficulty is the parallel sum of the ideas "
                + "in the solved lines, and that sort puts the higher scores first. Lower is easier. "
                + "A set with no solved line has no score. Dedupe changes which lines are counted. "
                + "After a survey, edit the window or the sort to rearrange the same results."
                + "</body></html>");
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(runExplanation, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready. Start solves the values above. Search range lists every set in the range.");
        add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void rebuildValueFields(boolean randomize) {
        int n = (Integer) countSpinner.getValue();
        valuesPanel.removeAll();
        valueFields = new JTextField[n];
        for (int i = 0; i < n; i++) {
            valueFields[i] = new JTextField("1", 5);
            valuesPanel.add(new JLabel("v" + i + ":"));
            valuesPanel.add(valueFields[i]);
        }
        if (randomize) {
            fillRandomValues();
        }
        valuesPanel.revalidate();
        valuesPanel.repaint();
    }

    private void randomizeValues() {
        try {
            fillRandomValues();
            statusLabel.setText("Randomized " + valueFields.length + " values in ["
                    + minRangeField.getText().trim() + ", " + maxRangeField.getText().trim() + "].");
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage());
        }
    }

    private void fillRandomValues() {
        int min = Integer.parseInt(minRangeField.getText().trim());
        int max = Integer.parseInt(maxRangeField.getText().trim());
        if (min > max) {
            throw new IllegalArgumentException("Range min must be ≤ max.");
        }
        long span = (long) max - (long) min + 1L;
        for (JTextField field : valueFields) {
            int value = min + (int) (random.nextDouble() * span);
            if (value > max) {
                value = max;
            }
            field.setText(String.valueOf(value));
        }
    }

    private double[] readValues() {
        double[] values = new double[valueFields.length];
        for (int i = 0; i < valueFields.length; i++) {
            values[i] = Double.parseDouble(valueFields[i].getText().trim());
        }
        return values;
    }

    private void startSearch() {
        final double[] values;
        final double goal;
        try {
            values = readValues();
            goal = Double.parseDouble(goalField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid number in values/goal.");
            return;
        }

        if (worker != null && !worker.isDone()) {
            statusLabel.setText("Already running — Stop first, or wait.");
            return;
        }

        int n = values.length;
        final boolean dedupeRepeated = dedupeRepeatedBox.isSelected();
        showingSurvey = false;
        solver = new Solver(n);
        solutionCount.set(0);
        checkedCount.set(0);
        userStopped.set(false);
        setRunning(true);
        final long startedAtNanos = System.nanoTime();
        statusLabel.setText(formatStatus(0, 0, 0)
                + "  Enumerating n=" + n + " (hits appear live; Stop anytime)…");
        appendLine("--- start: values=" + java.util.Arrays.toString(values)
                + " goal=" + goal + " ("
                + (dedupeRepeated
                    ? "dedupe on: sums and products ordered by text, one copy of each line"
                    : "dedupe off: index order, every labeled hit")
                + "; Stop keeps results) ---");

        final List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        worker = new SwingWorker<Void, String>() {
            private Solver.SearchStats lastStats = new Solver.SearchStats(0, 0);
            private long elapsedMs;

            @Override
            protected Void doInBackground() {
                lastStats = solver.findSolutionsStreaming(
                        values, goal, Integer.MAX_VALUE,
                        solution -> {
                            if (userStopped.get()) {
                                return;
                            }
                            hits.add(solution);
                            int k = solutionCount.incrementAndGet();
                            publish("SOL:" + k + ". " + solution.display()
                                    + " = " + format(solution.getValue()));
                        },
                        stats -> {
                            solutionCount.set((int) Math.min(stats.found, Integer.MAX_VALUE));
                            checkedCount.set(stats.checked);
                            publish("STAT");
                        },
                        dedupeRepeated);
                elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                long liveMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                for (String line : chunks) {
                    if ("STAT".equals(line)) {
                        statusLabel.setText(formatStatus(solutionCount.get(), checkedCount.get(), liveMs)
                                + "  Searching…");
                    } else if (line.startsWith("SOL:")) {
                        appendLine(line.substring(4));
                        statusLabel.setText(formatStatus(solutionCount.get(), checkedCount.get(), liveMs)
                                + "  Searching…");
                    }
                }
            }

            @Override
            protected void done() {
                setRunning(false);
                try {
                    get(); // surface background errors (e.g. unexpected failures)
                } catch (java.util.concurrent.CancellationException ignored) {
                    // Stop button
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = "Error: " + cause.getClass().getSimpleName()
                            + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
                    statusLabel.setText(msg);
                    appendLine("--- " + msg + " ---");
                    return;
                }
                long found = solutionCount.get();
                long checked = checkedCount.get();
                if (lastStats != null && lastStats.checked > checked) {
                    found = lastStats.found;
                    checked = lastStats.checked;
                    solutionCount.set((int) Math.min(found, Integer.MAX_VALUE));
                    checkedCount.set(checked);
                }
                if (elapsedMs <= 0) {
                    elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                }
                String prefix = userStopped.get() ? "Stopped. " : "Done. ";
                String end = prefix + formatStatus(found, checked, elapsedMs);
                if (!userStopped.get()) {
                    String difficulty = Difficulty.format(Difficulty.rate(hits, values));
                    if (!difficulty.isEmpty()) {
                        end = end + "  " + difficulty;
                    }
                }
                statusLabel.setText(end);
                appendLine("--- " + end + " ---");
            }
        };
        worker.execute();
    }

    private static String formatStatus(long found, long checked, long elapsedMs) {
        double pct = checked == 0 ? 0.0 : 100.0 * found / checked;
        return String.format("%d hit / %d checked (%.4f%%) in %s",
                found, checked, pct, formatDuration(elapsedMs));
    }

    private static String formatDuration(long elapsedMs) {
        if (elapsedMs < 1000) {
            return elapsedMs + "ms";
        }
        if (elapsedMs < 60_000) {
            return String.format("%.2fs", elapsedMs / 1000.0);
        }
        long seconds = elapsedMs / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return minutes + "m " + seconds + "s";
    }

    private void explainDifficulty() {
        final double[] values;
        final double goal;
        try {
            values = readValues();
            goal = Double.parseDouble(goalField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid number in values/goal.");
            return;
        }
        if (worker != null && !worker.isDone()) {
            statusLabel.setText("Already running — Stop first, or wait.");
            return;
        }

        final boolean dedupeRepeated = dedupeRepeatedBox.isSelected();
        showingSurvey = false;
        solver = new Solver(values.length);
        userStopped.set(false);
        setRunning(true);
        outputArea.setText("");
        final long startedAtNanos = System.nanoTime();
        statusLabel.setText("Solving before the difficulty breakdown…");
        final List<EvaluatedExpression> hits = new ArrayList<EvaluatedExpression>();
        worker = new SwingWorker<Difficulty.DealTrace, Solver.SearchStats>() {
            @Override
            protected Difficulty.DealTrace doInBackground() {
                solver.findSolutionsStreaming(
                        values, goal, Integer.MAX_VALUE, hits::add,
                        stats -> publish(stats), dedupeRepeated);
                if (userStopped.get() || solver.isCancelled()) {
                    return null;
                }
                return Difficulty.explain(hits, values);
            }

            @Override
            protected void process(java.util.List<Solver.SearchStats> chunks) {
                Solver.SearchStats stats = chunks.get(chunks.size() - 1);
                long liveMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                statusLabel.setText(formatStatus(stats.found, stats.checked, liveMs) + "  Solving…");
            }

            @Override
            protected void done() {
                setRunning(false);
                final Difficulty.DealTrace deal;
                try {
                    deal = get();
                } catch (java.util.concurrent.CancellationException ignored) {
                    statusLabel.setText("Stopped before every line was checked, so the difficulty is not shown.");
                    outputArea.setText("The score uses every solved line. Stop left this search unfinished.\n");
                    return;
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = "Error: " + cause.getClass().getSimpleName()
                            + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
                    statusLabel.setText(msg);
                    outputArea.setText(msg + "\n");
                    return;
                }
                if (deal == null) {
                    statusLabel.setText("Stopped before every line was checked, so the difficulty is not shown.");
                    outputArea.setText("The score uses every solved line. Stop left this search unfinished.\n");
                    return;
                }
                StringBuilder text = new StringBuilder();
                text.append("values");
                for (double value : values) {
                    text.append(' ').append(format(value));
                }
                text.append("  goal ").append(format(goal)).append('\n');
                text.append(Difficulty.formatExplain(deal));
                outputArea.setText(text.toString());
                outputArea.setCaretPosition(0);
                String summary = Difficulty.format(deal.rating);
                statusLabel.setText(summary.isEmpty() ? "No solved line." : summary);
            }
        };
        worker.execute();
    }

    private void startRangeSearch() {
        final int n = (Integer) countSpinner.getValue();
        final int min;
        final int max;
        final double goal;
        final int minSolutions;
        final Integer maxSolutions;
        try {
            min = Integer.parseInt(minRangeField.getText().trim());
            max = Integer.parseInt(maxRangeField.getText().trim());
            goal = Double.parseDouble(goalField.getText().trim());
            minSolutions = parseMinSolutions();
            maxSolutions = parseMaxSolutions();
            if (maxSolutions != null && maxSolutions < minSolutions) {
                throw new IllegalArgumentException("Solution-count min must be ≤ max.");
            }
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid number in range, goal, or solution counts.");
            return;
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage());
            return;
        }
        if (min > max) {
            statusLabel.setText("Range min must be ≤ max.");
            return;
        }
        if (worker != null && !worker.isDone()) {
            statusLabel.setText("Already running — Stop first, or wait.");
            return;
        }

        final boolean dedupeRepeated = dedupeRepeatedBox.isSelected();
        long span = (long) max - (long) min + 1L;
        final long total = span > Integer.MAX_VALUE
                ? -1L
                : RangeSurvey.multisetCount(n, (int) span);
        surveyRows = new ArrayList<RangeSurvey.Row>();
        surveyScanned = 0;
        surveyTotal = total;
        surveyStopped = false;
        surveyN = n;
        surveyMin = min;
        surveyMax = max;
        surveyGoal = goal;
        surveyDedupe = dedupeRepeated;
        showingSurvey = true;
        userStopped.set(false);
        solver = new Solver(n, 1);
        synchronized (liveSolvers) {
            liveSolvers.clear();
        }
        setRunning(true);
        final long startedAtNanos = System.nanoTime();
        outputArea.setText("Counting sets…\n");
        statusLabel.setText(scanStatus(0, total, 0) + "  Counting…");

        final List<RangeSurvey.Row> rows = surveyRows;
        worker = new SwingWorker<Integer, Integer>() {
            private long elapsedMs;

            @Override
            protected Integer doInBackground() {
                int completed = RangeSurvey.search(
                        solver, min, max, goal, dedupeRepeated,
                        userStopped::get, liveSolvers, rows, scanned -> publish(scanned));
                surveyScanned = completed;
                elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                return completed;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int completed = chunks.get(chunks.size() - 1);
                long liveMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                statusLabel.setText(scanStatus(completed, total, liveMs) + "  Counting…");
            }

            @Override
            protected void done() {
                surveyStopped = userStopped.get();
                if (elapsedMs <= 0) {
                    elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                }
                surveyElapsedMs = elapsedMs;
                setRunning(false);
                try {
                    get();
                } catch (java.util.concurrent.CancellationException ignored) {
                    surveyStopped = true;
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = "Error: " + cause.getClass().getSimpleName()
                            + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
                    statusLabel.setText(msg);
                    appendLine("--- " + msg + " ---");
                    return;
                }
                renderSurvey();
            }
        };
        worker.execute();
    }

    private void renderSurvey() {
        if (!showingSurvey) {
            return;
        }
        final int minSolutions;
        final Integer maxSolutions;
        try {
            minSolutions = parseMinSolutions();
            maxSolutions = parseMaxSolutions();
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage());
            return;
        }
        RangeSurvey.Sort sort = sortBox.getSelectedIndex() == 1
                ? RangeSurvey.Sort.DIFFICULTY
                : RangeSurvey.Sort.SOLUTION_COUNT;
        final List<RangeSurvey.Row> kept;
        try {
            kept = RangeSurvey.select(surveyRows, minSolutions, maxSolutions, sort);
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage());
            return;
        }
        String sortName = sort == RangeSurvey.Sort.DIFFICULTY ? "difficulty" : "solution count";
        StringBuilder text = new StringBuilder();
        text.append("--- n=").append(surveyN)
                .append(" range ").append(surveyMin).append("–").append(surveyMax)
                .append(" goal=").append(format(surveyGoal))
                .append(surveyDedupe ? " dedupe on" : " dedupe off")
                .append(", solutions ").append(solutionWindow(minSolutions, maxSolutions))
                .append(", sorted by ").append(sortName)
                .append(surveyStopped ? ", stopped early" : "")
                .append(" ---\n");
        for (RangeSurvey.Row row : kept) {
            text.append(String.format("%4d  %8s  %s%n", row.solutions(), row.difficultyText(), row.valuesText()));
        }
        if (kept.isEmpty()) {
            text.append("No sets in that solution-count range.\n");
        }
        outputArea.setText(text.toString());
        outputArea.setCaretPosition(0);
        String prefix = surveyStopped ? "Stopped. " : "Done. ";
        statusLabel.setText(prefix + kept.size() + " sets kept, "
                + scanStatus(surveyScanned, surveyTotal, surveyElapsedMs));
    }

    private static String solutionWindow(int minSolutions, Integer maxSolutions) {
        if (maxSolutions == null) {
            return minSolutions + " or more";
        }
        return minSolutions + "–" + maxSolutions;
    }

    private static String scanStatus(int completed, long total, long elapsedMs) {
        String ofTotal = total < 0 ? "?" : String.valueOf(total);
        return completed + " / " + ofTotal + " sets in " + formatDuration(elapsedMs);
    }

    private int parseMinSolutions() {
        return parseSolutionBound(minSolutionsField.getText(), 0, "Solution-count min");
    }

    private Integer parseMaxSolutions() {
        String text = maxSolutionsField.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        return parseSolutionBound(text, Integer.MIN_VALUE, "Solution-count max");
    }

    private static int parseSolutionBound(String text, int blankValue, String label) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return blankValue;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 0) {
                throw new IllegalArgumentException(label + " must be ≥ 0.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number in " + label.toLowerCase() + ".");
        }
    }

    private boolean surveyIdle() {
        return showingSurvey && !running && (worker == null || worker.isDone());
    }

    private void stopSearch() {
        userStopped.set(true);
        if (solver != null) {
            solver.requestStop();
        }
        synchronized (liveSolvers) {
            for (Solver live : liveSolvers) {
                live.requestStop();
            }
        }
        if (worker != null) {
            worker.cancel(true);
        }
        stopButton.setEnabled(false);
        statusLabel.setText("Stopping… (keeping current output)");
    }

    private void resetOutput() {
        if (worker != null && !worker.isDone()) {
            stopSearch();
        }
        outputArea.setText("");
        solutionCount.set(0);
        checkedCount.set(0);
        userStopped.set(false);
        showingSurvey = false;
        surveyRows = new ArrayList<RangeSurvey.Row>();
        surveyScanned = 0;
        setRunning(false);
        statusLabel.setText("Cleared. Enter numbers and Start, or Search range.");
    }

    private void setRunning(boolean running) {
        this.running = running;
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        resetButton.setEnabled(true);
        randomizeButton.setEnabled(!running);
        rangeButton.setEnabled(!running);
        explainButton.setEnabled(!running);
        countSpinner.setEnabled(!running);
        minRangeField.setEnabled(!running);
        maxRangeField.setEnabled(!running);
        goalField.setEnabled(!running);
        dedupeRepeatedBox.setEnabled(!running);
        minSolutionsField.setEnabled(!running);
        maxSolutionsField.setEnabled(!running);
        sortBox.setEnabled(!running);
        if (valueFields != null) {
            for (JTextField field : valueFields) {
                field.setEnabled(!running);
            }
        }
    }

    private void appendLine(String line) {
        outputArea.append(line);
        outputArea.append("\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private static String format(double value) {
        if (Math.abs(value - Math.round(value)) < 1e-9) {
            return String.valueOf(Math.round(value));
        }
        return String.valueOf(value);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ComSearchExplorer().setVisible(true));
    }
}
