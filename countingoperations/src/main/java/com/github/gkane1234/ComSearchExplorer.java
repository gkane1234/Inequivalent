package com.github.gkane1234;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
    private final JTextArea outputArea;
    private final JLabel statusLabel;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton resetButton;
    private final JButton randomizeButton;
    private final Random random = new Random();

    private Solver solver;
    private SwingWorker<Void, String> worker;
    private final AtomicInteger solutionCount = new AtomicInteger();
    private final AtomicLong checkedCount = new AtomicLong();
    private final AtomicBoolean userStopped = new AtomicBoolean(false);

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

        add(controls, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setPreferredSize(new Dimension(640, 360));
        scroll.setBorder(BorderFactory.createTitledBorder("Solutions (live)"));
        add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready. Set count/range, Randomize or type values, then Start.");
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
        solver = new Solver(n);
        solutionCount.set(0);
        checkedCount.set(0);
        userStopped.set(false);
        setRunning(true);
        final long startedAtNanos = System.nanoTime();
        statusLabel.setText(formatStatus(0, 0, 0)
                + "  Enumerating n=" + n + " (hits appear live; Stop anytime)…");
        appendLine("--- start: values=" + java.util.Arrays.toString(values)
                + " goal=" + goal + " (streaming enumeration; Stop keeps results) ---");

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
                            int k = solutionCount.incrementAndGet();
                            publish("SOL:" + k + ". " + solution.display()
                                    + " = " + format(solution.getValue()));
                        },
                        stats -> {
                            solutionCount.set((int) Math.min(stats.found, Integer.MAX_VALUE));
                            checkedCount.set(stats.checked);
                            publish("STAT");
                        });
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
