package com.github.gkane1234;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
 * The 24-game panel deals by difficulty strata computed from the 1–13 board sweep.
 */
public class ComSearchExplorer extends JFrame {
    private final JSpinner countSpinner;
    private final JTextField minRangeField;
    private final JTextField maxRangeField;
    private final JPanel valuesPanel;
    private JTextField[] valueFields;
    private final JTextField goalField;
    private final JCheckBox dedupeRepeatedBox;
    private final JTextArea outputArea;
    private final JLabel statusLabel;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton resetButton;
    private final JButton randomizeButton;
    private final Random random = new Random();

    private final JTextField easyPctField;
    private final JTextField mediumPctField;
    private final JTextField hardPctField;
    private final JTextField easyMaxField;
    private final JTextField mediumMaxField;
    private final JTextField hardMaxField;
    private final JButton computeStrataButton;
    private final JButton[] bandButtons;
    private final JButton reveal24Button;
    private final JLabel strataStatusLabel;

    private Solver solver;
    private SwingWorker<?, ?> worker;
    private final AtomicInteger solutionCount = new AtomicInteger();
    private final AtomicLong checkedCount = new AtomicLong();
    private final AtomicBoolean userStopped = new AtomicBoolean(false);
    private Game24.Puzzle last24Puzzle;

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
        dedupeRepeatedBox = new JCheckBox("Dedupe repeated numbers", true);
        top.add(dedupeRepeatedBox);
        controls.add(top, BorderLayout.NORTH);

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
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(resetButton);
        controls.add(buttons, BorderLayout.SOUTH);

        JPanel north = new JPanel(new BorderLayout(8, 8));
        north.add(controls, BorderLayout.NORTH);
        north.add(buildStrataPanel(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setPreferredSize(new Dimension(640, 320));
        scroll.setBorder(BorderFactory.createTitledBorder("Solutions (live)"));
        JLabel runExplanation = new JLabel("<html><body style='width:620px'>"
                + "The search walks inequivalent expressions and checks them against the goal. "
                + "Nothing is stored ahead of time, and Stop keeps the lines already printed. "
                + "Dedupe repeated numbers starts on: each hit reorders its sums and products by the text "
                + "of those parts, then a set keeps one copy of each line. Turn it off to keep every labeled hit "
                + "in the index order the search already uses. The expressions that get checked do not change."
                + "</body></html>");
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(runExplanation, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready. Set count/range, Randomize or type values, then Start.");
        add(statusLabel, BorderLayout.SOUTH);

        easyPctField = fieldRef[0];
        mediumPctField = fieldRef[1];
        hardPctField = fieldRef[2];
        easyMaxField = fieldRef[3];
        mediumMaxField = fieldRef[4];
        hardMaxField = fieldRef[5];
        computeStrataButton = buttonRef[0];
        bandButtons = new JButton[] {buttonRef[1], buttonRef[2], buttonRef[3], buttonRef[4]};
        reveal24Button = buttonRef[5];
        strataStatusLabel = labelRef[0];
        applyBounds(DifficultyStrata.DEFAULT_BOUNDS);

        pack();
        setLocationRelativeTo(null);
    }

    // Filled while buildStrataPanel runs (constructor order).
    private final JTextField[] fieldRef = new JTextField[6];
    private final JButton[] buttonRef = new JButton[6];
    private final JLabel[] labelRef = new JLabel[1];

    private JPanel buildStrataPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("24 game difficulty strata"));

        JPanel pctRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        pctRow.add(new JLabel("Percentiles %:"));
        fieldRef[0] = new JTextField(formatPct(DifficultyStrata.DEFAULT_EASY_PERCENTILE), 4);
        fieldRef[1] = new JTextField(formatPct(DifficultyStrata.DEFAULT_MEDIUM_PERCENTILE), 4);
        fieldRef[2] = new JTextField(formatPct(DifficultyStrata.DEFAULT_HARD_PERCENTILE), 4);
        pctRow.add(new JLabel("easy≤"));
        pctRow.add(fieldRef[0]);
        pctRow.add(new JLabel("medium≤"));
        pctRow.add(fieldRef[1]);
        pctRow.add(new JLabel("hard≤"));
        pctRow.add(fieldRef[2]);
        buttonRef[0] = new JButton("Compute from range");
        buttonRef[0].addActionListener(e -> computeStrata());
        pctRow.add(buttonRef[0]);

        JPanel boundRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        boundRow.add(new JLabel("Difficulty bounds:"));
        fieldRef[3] = new JTextField(8);
        fieldRef[4] = new JTextField(8);
        fieldRef[5] = new JTextField(8);
        boundRow.add(new JLabel("easy≤"));
        boundRow.add(fieldRef[3]);
        boundRow.add(new JLabel("medium≤"));
        boundRow.add(fieldRef[4]);
        boundRow.add(new JLabel("hard≤"));
        boundRow.add(fieldRef[5]);
        boundRow.add(new JLabel("(hardest above hard)"));

        JPanel dealRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        DifficultyStrata.Band[] bands = DifficultyStrata.Band.values();
        String[] labels = {"Easy", "Medium", "Hard", "Hardest"};
        for (int i = 0; i < bands.length; i++) {
            final DifficultyStrata.Band band = bands[i];
            buttonRef[i + 1] = new JButton(labels[i]);
            buttonRef[i + 1].addActionListener(e -> deal24(band));
            dealRow.add(buttonRef[i + 1]);
        }
        buttonRef[5] = new JButton("Reveal answers");
        buttonRef[5].setEnabled(false);
        buttonRef[5].addActionListener(e -> reveal24());
        dealRow.add(buttonRef[5]);

        labelRef[0] = new JLabel("<html><body style='width:620px'>"
                + "Compute rates every nondecreasing 4-tuple in the Range fields (default 1–13) that hits 24, "
                + "then cuts that distribution at the percentiles. Edit the numerical bounds to try cuts without "
                + "recomputing. Deal buttons pick four numbers from 1–9 in that band."
                + "</body></html>");

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 2));
        rows.add(pctRow);
        rows.add(boundRow);
        rows.add(dealRow);
        rows.add(labelRef[0]);
        panel.add(rows, BorderLayout.CENTER);
        return panel;
    }

    private static String formatPct(double fraction) {
        return String.format(Locale.US, "%.0f", fraction * 100.0);
    }

    private void applyBounds(DifficultyStrata.Bounds bounds) {
        easyMaxField.setText(formatBound(bounds.easyMax));
        mediumMaxField.setText(formatBound(bounds.mediumMax));
        hardMaxField.setText(formatBound(bounds.hardMax));
    }

    private static String formatBound(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private DifficultyStrata.Bounds readBounds() {
        try {
            return new DifficultyStrata.Bounds(
                    Double.parseDouble(easyMaxField.getText().trim()),
                    Double.parseDouble(mediumMaxField.getText().trim()),
                    Double.parseDouble(hardMaxField.getText().trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Difficulty bounds must be numbers.");
        }
    }

    private void computeStrata() {
        if (worker != null && !worker.isDone()) {
            statusLabel.setText("Stop the current search before computing strata.");
            return;
        }
        final int min;
        final int max;
        final double easyPct;
        final double mediumPct;
        final double hardPct;
        try {
            min = Integer.parseInt(minRangeField.getText().trim());
            max = Integer.parseInt(maxRangeField.getText().trim());
            easyPct = Double.parseDouble(easyPctField.getText().trim()) / 100.0;
            mediumPct = Double.parseDouble(mediumPctField.getText().trim()) / 100.0;
            hardPct = Double.parseDouble(hardPctField.getText().trim()) / 100.0;
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid range or percentile.");
            return;
        }

        setRunning(true);
        computeStrataButton.setEnabled(false);
        statusLabel.setText("Computing difficulty strata over [" + min + ", " + max + "]…");
        worker = new SwingWorker<Void, Integer>() {
            private DifficultyStrata.Result result;
            private String error;

            @Override
            protected Void doInBackground() {
                try {
                    result = DifficultyStrata.compute(min, max, Game24.COUNT, Game24.GOAL,
                            easyPct, mediumPct, hardPct, boards -> {
                                if (boards % 50 == 0) {
                                    publish(boards);
                                }
                            });
                } catch (RuntimeException ex) {
                    error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int boards = chunks.get(chunks.size() - 1);
                statusLabel.setText("Computing difficulty strata… " + boards + " boards");
            }

            @Override
            protected void done() {
                setRunning(false);
                computeStrataButton.setEnabled(true);
                if (error != null) {
                    statusLabel.setText("Strata failed: " + error);
                    return;
                }
                applyBounds(result.bounds);
                String summary = String.format(Locale.US,
                        "Strata ready: %d/%d solvable. %s  (edit bounds to test; web uses these numbers)",
                        result.solvable, result.boards, result.bounds);
                statusLabel.setText(summary);
                strataStatusLabel.setText("<html><body style='width:620px'>" + summary
                        + ". Recompute whenever the difficulty equation changes.</body></html>");
                appendLine("--- " + summary + " ---");
            }
        };
        worker.execute();
    }

    private void deal24(DifficultyStrata.Band band) {
        if (worker != null && !worker.isDone()) {
            statusLabel.setText("Stop the current search before dealing.");
            return;
        }
        final DifficultyStrata.Bounds bounds;
        try {
            bounds = readBounds();
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage());
            return;
        }

        setRunning(true);
        statusLabel.setText("Looking for a " + band.id + " 24-game board…");
        worker = new SwingWorker<Void, double[]>() {
            private Game24.Puzzle puzzle;
            private String error;

            @Override
            protected Void doInBackground() {
                try {
                    puzzle = Game24.find(band, bounds, random, 800, values -> publish(values));
                } catch (RuntimeException ex) {
                    error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                }
                return null;
            }

            @Override
            protected void process(List<double[]> chunks) {
                double[] values = chunks.get(chunks.size() - 1);
                statusLabel.setText("Looking for a " + band.id + " board… "
                        + java.util.Arrays.toString(values));
            }

            @Override
            protected void done() {
                setRunning(false);
                if (error != null) {
                    last24Puzzle = null;
                    reveal24Button.setEnabled(false);
                    statusLabel.setText(error);
                    return;
                }
                last24Puzzle = puzzle;
                reveal24Button.setEnabled(true);
                writeValues(puzzle.values);
                countSpinner.setValue(puzzle.values.length);
                goalField.setText(format(Game24.GOAL));
                dedupeRepeatedBox.setSelected(true);
                String difficulty = Difficulty.format(puzzle.rating);
                String summary = String.format(Locale.US,
                        "%s board %s → 24. %d distinct solutions after dedupe. %s",
                        capitalize(band.id),
                        java.util.Arrays.toString(puzzle.values),
                        puzzle.solutions.size(),
                        difficulty);
                statusLabel.setText(summary);
                appendLine("--- deal " + band.id + ": " + java.util.Arrays.toString(puzzle.values)
                        + " (" + puzzle.attempts + " tries) ---");
            }
        };
        worker.execute();
    }

    private void reveal24() {
        if (last24Puzzle == null) {
            return;
        }
        appendLine("--- answers for " + java.util.Arrays.toString(last24Puzzle.values) + " ---");
        int k = 1;
        for (EvaluatedExpression hit : last24Puzzle.solutions) {
            appendLine(k + ". " + hit.display() + " = " + format(hit.getValue()));
            k++;
        }
        String difficulty = Difficulty.format(last24Puzzle.rating);
        if (!difficulty.isEmpty()) {
            appendLine(difficulty);
        }
    }

    private static String capitalize(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private void writeValues(double[] values) {
        if (valueFields == null || valueFields.length != values.length) {
            countSpinner.setValue(values.length);
            rebuildValueFields(false);
        }
        for (int i = 0; i < values.length; i++) {
            valueFields[i].setText(format(values[i]));
        }
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
                    Difficulty.Rating rating = Difficulty.rate(hits, values);
                    String difficulty = Difficulty.format(rating);
                    if (!difficulty.isEmpty()) {
                        end = end + "  " + difficulty;
                        try {
                            DifficultyStrata.Bounds bounds = readBounds();
                            DifficultyStrata.Band band = bounds.bandFor(rating.difficulty);
                            if (band != null) {
                                end = end + "  Band: " + band.id + ".";
                            }
                        } catch (IllegalArgumentException ignored) {
                            // bounds fields may be mid-edit
                        }
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

    private void stopSearch() {
        userStopped.set(true);
        if (solver != null) {
            solver.requestStop();
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
        last24Puzzle = null;
        reveal24Button.setEnabled(false);
        setRunning(false);
        statusLabel.setText("Cleared. Enter numbers and Start.");
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        resetButton.setEnabled(true);
        randomizeButton.setEnabled(!running);
        countSpinner.setEnabled(!running);
        minRangeField.setEnabled(!running);
        maxRangeField.setEnabled(!running);
        goalField.setEnabled(!running);
        dedupeRepeatedBox.setEnabled(!running);
        computeStrataButton.setEnabled(!running);
        easyPctField.setEnabled(!running);
        mediumPctField.setEnabled(!running);
        hardPctField.setEnabled(!running);
        easyMaxField.setEnabled(!running);
        mediumMaxField.setEnabled(!running);
        hardMaxField.setEnabled(!running);
        for (JButton button : bandButtons) {
            button.setEnabled(!running);
        }
        if (!running) {
            reveal24Button.setEnabled(last24Puzzle != null);
        } else {
            reveal24Button.setEnabled(false);
        }
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
