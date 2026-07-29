package dashboard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;

public class MainFrame extends JFrame {

    private static final int UI_REFRESH_MS = 100;

    private static final int MAX_DRAIN_PER_TICK = 200;

    private static final int MIN_MA_WINDOW = 1;
    private static final int MAX_MA_WINDOW = 200;

    private static final int SIMULATED_WORK_MS = 3000;

    private static final int PROGRESS_STEPS = 50;

    private static final int MAX_RETAINED_QUOTES = 5000;

    private final JButton startButton = new FlatButton("Start Data Feed", Theme.BTN_START);
    private final JButton stopButton = new FlatButton("Stop Data Feed", Theme.BTN_STOP);
    private final JButton calculateButton = new FlatButton("Calculate Indicators", Theme.BTN_CALC);
    private final JSlider speedSlider = new JSlider(100, 2000, 1000);
    private final JTextField maWindowField = new JTextField("5", 5);

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[] { "Timestamp", "Symbol", "Price", "Direction" }, 0);
    private final JTable priceTable = new JTable(tableModel);
    private final ChartPanel chart = new ChartPanel();
    private final JLabel statusLabel = new JLabel("Idle");
    private final JPanel statusPanel = new JPanel(new BorderLayout());
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    private final ArrayBlockingQueue<StockQuote> queue = new ArrayBlockingQueue<>(1000);

    private final List<StockQuote> history =
            Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Double> lastPriceBySymbol = new HashMap<>();

    private QuoteProducer producer;
    private Thread producerThread;

    private final Timer uiTimer = new Timer(UI_REFRESH_MS, e -> drainQueueIntoTable());

    public MainFrame() {
        super("Real-Time Financial Market Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BACKGROUND);

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(buildHeader(), BorderLayout.NORTH);
        topArea.add(buildControlPanel(), BorderLayout.CENTER);

        add(topArea, BorderLayout.NORTH);
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

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBackground(Theme.HEADER_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Real-Time Financial Market Dashboard");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.HEADER_TEXT);

        JLabel subtitle = new JLabel("Live simulated quotes with moving-average indicators");
        subtitle.setFont(Theme.FONT_SUBTITLE);
        subtitle.setForeground(Theme.HEADER_SUBTEXT);

        panel.add(title);
        panel.add(subtitle);
        return panel;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID));

        stopButton.setEnabled(false);

        speedSlider.setPreferredSize(new Dimension(160, 40));
        speedSlider.setMajorTickSpacing(500);
        speedSlider.setPaintTicks(true);
        speedSlider.setBackground(Theme.BACKGROUND);

        maWindowField.setFont(Theme.FONT_LABEL);
        maWindowField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.GRID),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));

        panel.add(startButton);
        panel.add(stopButton);
        panel.add(calculateButton);
        panel.add(fieldLabel("Speed (delay ms)"));
        panel.add(speedSlider);
        panel.add(fieldLabel("MA window"));
        panel.add(maWindowField);
        return panel;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_LABEL);
        label.setForeground(Theme.TEXT);
        return label;
    }

    private JSplitPane buildCenterSplit() {
        styleTable();

        JScrollPane tableScroll = new JScrollPane(priceTable);
        tableScroll.setBorder(titledBorder("Market Data"));
        tableScroll.getViewport().setBackground(Theme.SURFACE);

        chart.setBorder(titledBorder("Price / Indicator"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, chart);
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        split.setBackground(Theme.BACKGROUND);
        return split;
    }

    private void styleTable() {
        priceTable.setFont(Theme.FONT_TABLE);
        priceTable.setRowHeight(24);
        priceTable.setBackground(Theme.SURFACE);
        priceTable.setForeground(Theme.TEXT);
        priceTable.setGridColor(Theme.GRID);
        priceTable.setSelectionBackground(Theme.SELECTION_BG);
        priceTable.setSelectionForeground(Theme.TEXT);
        priceTable.setShowVerticalLines(false);

        priceTable.getTableHeader().setFont(Theme.FONT_BUTTON);
        priceTable.getTableHeader().setBackground(Theme.TABLE_HEADER_BG);
        priceTable.getTableHeader().setForeground(Theme.TABLE_HEADER_FG);
        priceTable.getTableHeader().setReorderingAllowed(false);

        priceTable.getColumnModel().getColumn(2).setCellRenderer(new PriceCellRenderer());

        priceTable.removeColumn(priceTable.getColumnModel().getColumn(3));

        priceTable.setAutoCreateColumnsFromModel(false);
    }

    private Border titledBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Theme.GRID), title),
                BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    private JPanel buildStatusBar() {
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));

        statusLabel.setFont(Theme.FONT_STATUS);

        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 18));
        progressBar.setForeground(Theme.BTN_CALC);
        progressBar.setVisible(false);

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.EAST);

        showIdleStatus();
        return statusPanel;
    }

    private void showIdleStatus() {
        applyStatus("Idle", Theme.STATUS_IDLE_BG, Theme.STATUS_IDLE_FG);
    }

    private void showActiveStatus(String text) {
        applyStatus(text, Theme.STATUS_ACTIVE_BG, Theme.STATUS_ACTIVE_FG);
    }

    private void showErrorStatus(String text) {
        applyStatus(text, Theme.STATUS_ERROR_BG, Theme.STATUS_ERROR_FG);
    }

    private void restoreFeedStatus() {
        if (uiTimer.isRunning()) {
            showActiveStatus("Data feed active");
        } else {
            showIdleStatus();
        }
    }

    private void applyStatus(String text, Color background, Color foreground) {
        statusLabel.setText(text);
        statusLabel.setForeground(foreground);
        statusPanel.setBackground(background);
        progressBar.setBackground(background);
    }

    private void startFeed() {
        queue.clear();

        producer = new QuoteProducer(queue, speedSlider.getValue());
        producerThread = new Thread(producer, "quote-producer");
        producerThread.setDaemon(true);
        producerThread.start();

        uiTimer.start();

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        showActiveStatus("Data feed active");
    }

    private void stopFeed() {
        stopProducerThread();

        uiTimer.stop();
        drainQueueIntoTable();

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        showIdleStatus();
    }

    private void stopProducerThread() {
        if (producer != null) {
            producer.stop();
            producerThread.interrupt();
        }
    }

    private void shutdown() {
        stopProducerThread();
        uiTimer.stop();
    }

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
                    String.format("%.2f", quote.getPrice()),
                    directionFor(quote)
            });
        }

        trimToRetentionLimit();
        scrollToLastRow();
        updateStatusWithDropCount();
    }

    private void trimToRetentionLimit() {
        int excessRows = tableModel.getRowCount() - MAX_RETAINED_QUOTES;
        for (int i = 0; i < excessRows; i++) {
            tableModel.removeRow(0);
        }

        synchronized (history) {
            int excessQuotes = history.size() - MAX_RETAINED_QUOTES;
            if (excessQuotes > 0) {
                history.subList(0, excessQuotes).clear();
            }
        }
    }

    private Integer directionFor(StockQuote quote) {
        Double previous = lastPriceBySymbol.put(quote.getSymbol(), quote.getPrice());

        if (previous == null) {
            return 0;
        }
        return Double.compare(quote.getPrice(), previous);
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
        showActiveStatus(dropped == 0
                ? "Data feed active"
                : "Data feed active — dropped: " + dropped);
    }

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

        List<StockQuote> snapshot = snapshotHistory();

        if (snapshot.size() < window) {
            showInputWarning("Not enough data yet for a window of " + window + ".\n"
                    + "That needs at least " + window + " quotes, but only " + snapshot.size()
                    + " have arrived so far.\nStart the data feed and wait a few seconds.");
            return;
        }

        runCalculationInBackground(snapshot, window);
    }

    private List<StockQuote> snapshotHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    private void showInputWarning(String message) {
        showErrorStatus("Invalid MA window — calculation not started");

        JOptionPane.showMessageDialog(this, message,
                "Check the MA window value", JOptionPane.WARNING_MESSAGE);
    }

    private void runCalculationInBackground(List<StockQuote> snapshot, int window) {
        restoreFeedStatus();
        calculateButton.setEnabled(false);
        chart.showMessage("Calculating moving average (window " + window + ")...");

        progressBar.setValue(0);
        progressBar.setVisible(true);

        SwingWorker<List<Double>, Void> worker = new SwingWorker<List<Double>, Void>() {

            @Override
            protected List<Double> doInBackground() throws Exception {
                for (int step = 1; step <= PROGRESS_STEPS; step++) {
                    Thread.sleep(SIMULATED_WORK_MS / PROGRESS_STEPS);
                    setProgress(step * 100 / PROGRESS_STEPS);
                }
                return movingAverage(snapshot, window);
            }

            @Override
            protected void done() {
                try {
                    showResult(get(), snapshot, window);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    chart.showMessage("Calculation was interrupted.");
                    showErrorStatus("Calculation interrupted");
                } catch (ExecutionException ex) {
                    chart.showMessage("Calculation failed: " + describeCause(ex));
                    showErrorStatus("Calculation failed");
                } finally {
                    calculateButton.setEnabled(true);
                    progressBar.setVisible(false);
                }
            }
        };

        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progressBar.setValue((Integer) event.getNewValue());
            }
        });

        worker.execute();
    }

    private static String describeCause(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause == null) {
            return "unknown error";
        }
        return (cause.getMessage() == null)
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }

    private static List<Double> movingAverage(List<StockQuote> quotes, int window) {
        List<Double> averages = new ArrayList<>();
        double sum = 0;

        for (int i = 0; i < quotes.size(); i++) {
            sum += quotes.get(i).getPrice();

            if (i >= window) {
                sum -= quotes.get(i - window).getPrice();
            }
            if (i >= window - 1) {
                averages.add(sum / window);
            }
        }
        return averages;
    }

    private void showResult(List<Double> averages, List<StockQuote> snapshot, int window) {
        List<Double> prices = new ArrayList<>();
        for (StockQuote quote : snapshot) {
            prices.add(quote.getPrice());
        }

        chart.setData(prices, averages, window);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("Button.disabledText", Theme.BTN_DISABLED_TEXT);

            new MainFrame().setVisible(true);
        });
    }
}
