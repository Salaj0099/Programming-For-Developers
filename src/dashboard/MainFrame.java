package dashboard;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class MainFrame extends JFrame {

    /** How often the EDT drains the queue into the table, in milliseconds. */
    private static final int UI_REFRESH_MS = 100;

    /** Maximum quotes moved from queue to table in one drain, to keep the EDT responsive. */
    private static final int MAX_DRAIN_PER_TICK = 200;

    /** Allowed range for the "MA window" input field. */
    private static final int MIN_MA_WINDOW = 1;
    private static final int MAX_MA_WINDOW = 200;

    /** Artificial delay so the background calculation is long enough to see. */
    private static final int SIMULATED_WORK_MS = 3000;

    /** How many slices the simulated work is split into, for progress reporting. */
    private static final int PROGRESS_STEPS = 50;

    private final JButton startButton = new JButton("Start Data Feed");
    private final JButton stopButton = new JButton("Stop Data Feed");
    private final JButton calculateButton = new JButton("Calculate Indicators");
    private final JSlider speedSlider = new JSlider(100, 2000, 1000);
    private final JTextField maWindowField = new JTextField("5", 5);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[] { "Timestamp", "Symbol", "Price" }, 0);
    private final JTable priceTable = new JTable(tableModel);
    private final ChartPanel chart = new ChartPanel();
    private final JLabel statusLabel = new JLabel("Idle");
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    /** Shared hand-off point between the producer thread and the EDT. */
    private final ArrayBlockingQueue<StockQuote> queue = new ArrayBlockingQueue<>(1000);

    /**
     * Every quote that has reached the table, kept for calculations.
     * Written by the EDT, read by the SwingWorker thread, so it must be
     * synchronised. Iterating it still requires locking on the list itself.
     */
    private final List<StockQuote> history =
            Collections.synchronizedList(new ArrayList<>());

    /** Null while the feed is stopped. */
    private QuoteProducer producer;
    private Thread producerThread;

    /** Fires on the EDT; moves quotes from the queue into the table. */
    private final Timer uiTimer = new Timer(UI_REFRESH_MS, e -> drainQueueIntoTable());

    public MainFrame() {
        super("Real-Time Financial Market Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.NORTH);
        add(buildCenterSplit(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        startButton.addActionListener(e -> startFeed());
        stopButton.addActionListener(e -> stopFeed());
        calculateButton.addActionListener(e -> calculateIndicators());
        speedSlider.addChangeListener(e -> {
            if (producer != null) {
                producer.setDelayMs(speedSlider.getValue());
            }
        });
    }

    // ---------------------------------------------------------------- layout

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));

        stopButton.setEnabled(false);

        speedSlider.setPreferredSize(new Dimension(160, 40));
        speedSlider.setMajorTickSpacing(500);
        speedSlider.setPaintTicks(true);

        panel.add(startButton);
        panel.add(stopButton);
        panel.add(calculateButton);
        panel.add(new JLabel("Speed (delay ms)"));
        panel.add(speedSlider);
        panel.add(new JLabel("MA window"));
        panel.add(maWindowField);
        return panel;
    }

    private JSplitPane buildCenterSplit() {
        JScrollPane tableScroll = new JScrollPane(priceTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Market Data"));

        chart.setBorder(BorderFactory.createTitledBorder("Price / Indicator"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, chart);
        split.setResizeWeight(0.5);
        return split;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        progressBar.setStringPainted(true);   // show "42%" inside the bar
        progressBar.setPreferredSize(new Dimension(200, 18));
        progressBar.setVisible(false);        // only shown during a calculation

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.EAST);
        return panel;
    }

    // ------------------------------------------------------------ feed control

    /** Runs on the EDT (button click). */
    private void startFeed() {
        producer = new QuoteProducer(queue, speedSlider.getValue());
        producerThread = new Thread(producer, "quote-producer");
        producerThread.setDaemon(true);
        producerThread.start();

        uiTimer.start();

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Data feed active");
    }

    /** Runs on the EDT (button click). */
    private void stopFeed() {
        if (producer != null) {
            producer.stop();       // flips the volatile flag; thread exits its loop
            producerThread.interrupt(); // wakes it early if it is mid-sleep
        }

        uiTimer.stop();
        drainQueueIntoTable();     // show anything produced just before stopping

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("Idle");
    }

    // -------------------------------------------------------------- consumer

    /** Always runs on the EDT: either from the Swing Timer or from stopFeed(). */
    private void drainQueueIntoTable() {
        List<StockQuote> batch = new ArrayList<>();
        queue.drainTo(batch, MAX_DRAIN_PER_TICK);

        if (batch.isEmpty()) {
            return;
        }

        history.addAll(batch);

        for (StockQuote quote : batch) {
            tableModel.addRow(new Object[] {
                    quote.getFormattedTime(),
                    quote.getSymbol(),
                    String.format("%.2f", quote.getPrice())
            });
        }

        scrollToLastRow();
        updateStatusWithDropCount();
    }

    private void scrollToLastRow() {
        int lastRow = priceTable.getRowCount() - 1;
        if (lastRow >= 0) {
            Rectangle cell = priceTable.getCellRect(lastRow, 0, true);
            priceTable.scrollRectToVisible(cell);
        }
    }

    private void updateStatusWithDropCount() {
        if (producer == null) {
            return;
        }
        int dropped = producer.getDroppedCount();
        statusLabel.setText(dropped == 0
                ? "Data feed active"
                : "Data feed active — dropped: " + dropped);
    }

    // ------------------------------------------------------------ indicators

    /** Runs on the EDT (button click). Validates input, then hands work to a worker thread. */
    private void calculateIndicators() {
        String typed = maWindowField.getText().trim();

        if (typed.isEmpty()) {
            showInputWarning("Please enter a moving-average window size before calculating.\n"
                    + "Any whole number from " + MIN_MA_WINDOW + " to " + MAX_MA_WINDOW + " will do.");
            return;
        }

        int window;
        try {
            window = Integer.parseInt(typed);
        } catch (NumberFormatException ex) {
            showInputWarning("\"" + typed + "\" is not a whole number.\n"
                    + "Please enter a value between " + MIN_MA_WINDOW + " and " + MAX_MA_WINDOW
                    + " — for example 5.");
            return;
        }

        if (window < MIN_MA_WINDOW || window > MAX_MA_WINDOW) {
            showInputWarning("The moving-average window must be between " + MIN_MA_WINDOW
                    + " and " + MAX_MA_WINDOW + ".\nYou entered " + window + ".");
            return;
        }

        // Take the snapshot on the EDT so the size we validate is the size we use.
        List<StockQuote> snapshot = snapshotHistory();

        if (snapshot.size() < window) {
            showInputWarning("Not enough data yet for a window of " + window + ".\n"
                    + "That needs at least " + window + " quotes, but only " + snapshot.size()
                    + " have arrived so far.\nStart the data feed and wait a few seconds.");
            return;
        }

        runCalculationInBackground(snapshot, window);
    }

    /** Copies the shared history so the worker thread reads a private, unchanging list. */
    private List<StockQuote> snapshotHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    private void showInputWarning(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Check the MA window value", JOptionPane.WARNING_MESSAGE);
    }

    private void runCalculationInBackground(List<StockQuote> snapshot, int window) {
        calculateButton.setEnabled(false);
        chart.showMessage("Calculating moving average (window " + window + ")...");

        progressBar.setValue(0);
        progressBar.setVisible(true);

        SwingWorker<List<Double>, Void> worker = new SwingWorker<List<Double>, Void>() {

            /** Runs on a background thread. Must not touch any Swing component. */
            @Override
            protected List<Double> doInBackground() throws Exception {
                // Heavy work simulated in slices, so progress can be reported as we go.
                for (int step = 1; step <= PROGRESS_STEPS; step++) {
                    Thread.sleep(SIMULATED_WORK_MS / PROGRESS_STEPS);
                    setProgress(step * 100 / PROGRESS_STEPS); // fires a property change
                }
                return movingAverage(snapshot, window);
            }

            /** Runs on the EDT once doInBackground() returns. Safe to touch Swing here. */
            @Override
            protected void done() {
                try {
                    showResult(get(), snapshot, window);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    chart.showMessage("Calculation was interrupted.");
                } catch (ExecutionException ex) {
                    chart.showMessage("Calculation failed: " + ex.getCause().getMessage());
                } finally {
                    calculateButton.setEnabled(true);
                    progressBar.setVisible(false);
                }
            }
        };

        // Swing delivers these events on the EDT, so it is safe to touch the bar here.
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progressBar.setValue((Integer) event.getNewValue());
            }
        });

        worker.execute();
    }

    /**
     * Sliding-window moving average. Each output value is the mean of the
     * previous {@code window} prices, so the series is shorter than the input.
     */
    private static List<Double> movingAverage(List<StockQuote> quotes, int window) {
        List<Double> averages = new ArrayList<>();
        double sum = 0;

        for (int i = 0; i < quotes.size(); i++) {
            sum += quotes.get(i).getPrice();

            if (i >= window) {
                sum -= quotes.get(i - window).getPrice(); // drop the value leaving the window
            }
            if (i >= window - 1) {
                averages.add(sum / window);
            }
        }
        return averages;
    }

    /** Runs on the EDT, called from done(). */
    private void showResult(List<Double> averages, List<StockQuote> snapshot, int window) {
        List<Double> prices = new ArrayList<>();
        for (StockQuote quote : snapshot) {
            prices.add(quote.getPrice());
        }

        chart.setData(prices, averages, window);
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
