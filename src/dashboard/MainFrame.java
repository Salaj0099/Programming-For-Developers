package dashboard;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class MainFrame extends JFrame {

    /** How often the EDT drains the queue into the table, in milliseconds. */
    private static final int UI_REFRESH_MS = 100;

    /** Maximum quotes moved from queue to table in one drain, to keep the EDT responsive. */
    private static final int MAX_DRAIN_PER_TICK = 200;

    private final JButton startButton = new JButton("Start Data Feed");
    private final JButton stopButton = new JButton("Stop Data Feed");
    private final JButton calculateButton = new JButton("Calculate Indicators");
    private final JSlider speedSlider = new JSlider(100, 2000, 1000);
    private final JTextField maWindowField = new JTextField("5", 5);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[] { "Timestamp", "Symbol", "Price" }, 0);
    private final JTable priceTable = new JTable(tableModel);
    private final JPanel chartPanel = new JPanel();
    private final JLabel statusLabel = new JLabel("Idle");

    /** Shared hand-off point between the producer thread and the EDT. */
    private final ArrayBlockingQueue<StockQuote> queue = new ArrayBlockingQueue<>(1000);

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

        chartPanel.setBorder(BorderFactory.createTitledBorder("Price / Indicator"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, chartPanel);
        split.setResizeWeight(0.5);
        return split;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        panel.add(statusLabel, BorderLayout.WEST);
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

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
