package com.github.gkane1234;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final JPanel valuesPanel;
    private JTextField[] valueFields;
    private final JTextField goalField;
    private final JTextArea outputArea;
    private final JLabel statusLabel;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton resetButton;

    private Solver solver;
    private SwingWorker<Void, String> worker;
    private final AtomicInteger solutionCount = new AtomicInteger();
    private final AtomicBoolean userStopped = new AtomicBoolean(false);

    public ComSearchExplorer() {
        super("ComSearch Explorer");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new BorderLayout(8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Count:"));
        countSpinner = new JSpinner(new SpinnerNumberModel(4, 2, 7, 1));
        countSpinner.addChangeListener(e -> rebuildValueFields());
        top.add(countSpinner);
        top.add(new JLabel("Goal:"));
        goalField = new JTextField("24", 8);
        top.add(goalField);
        controls.add(top, BorderLayout.NORTH);

        valuesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        valuesPanel.setBorder(BorderFactory.createTitledBorder("Values"));
        controls.add(valuesPanel, BorderLayout.CENTER);
        rebuildValueFields();

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

        statusLabel = new JLabel("Ready.");
        add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void rebuildValueFields() {
        int n = (Integer) countSpinner.getValue();
        valuesPanel.removeAll();
        valueFields = new JTextField[n];
        String[] defaults = {"2", "4", "7", "10", "1", "3", "5"};
        for (int i = 0; i < n; i++) {
            valueFields[i] = new JTextField(i < defaults.length ? defaults[i] : "1", 5);
            valuesPanel.add(new JLabel("v" + i + ":"));
            valuesPanel.add(valueFields[i]);
        }
        valuesPanel.revalidate();
        valuesPanel.repaint();
        pack();
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
        userStopped.set(false);
        setRunning(true);
        statusLabel.setText("Searching…");
        appendLine("--- start: values=" + java.util.Arrays.toString(values) + " goal=" + goal + " ---");

        worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                solver.findSolutionsStreaming(values, goal, Integer.MAX_VALUE, solution -> {
                    if (userStopped.get()) {
                        return;
                    }
                    int k = solutionCount.incrementAndGet();
                    publish(k + ". " + solution.display() + " = " + format(solution.getValue()));
                });
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    appendLine(line);
                }
                statusLabel.setText("Found " + solutionCount.get() + " so far…");
            }

            @Override
            protected void done() {
                setRunning(false);
                String end = userStopped.get()
                        ? ("Stopped. Kept " + solutionCount.get() + " solution(s) on screen.")
                        : ("Done. " + solutionCount.get() + " solution(s).");
                statusLabel.setText(end);
                appendLine("--- " + end + " ---");
            }
        };
        worker.execute();
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
        userStopped.set(false);
        setRunning(false);
        statusLabel.setText("Cleared. Enter numbers and Start.");
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        resetButton.setEnabled(true);
        countSpinner.setEnabled(!running);
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
