package dashboard;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

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
import javax.swing.table.DefaultTableModel;

public class MainFrame extends JFrame {

    private final JButton startButton = new JButton("Start Data Feed");
    private final JButton stopButton = new JButton("Stop Data Feed");
    private final JButton calculateButton = new JButton("Calculate Indicators");
    private final JSlider speedSlider = new JSlider(100, 2000, 1000);
    private final JTextField maWindowField = new JTextField("5", 5);
    private final JTable priceTable = new JTable(
            new DefaultTableModel(new Object[] { "Timestamp", "Symbol", "Price" }, 0));
    private final JPanel chartPanel = new JPanel();
    private final JLabel statusLabel = new JLabel("Idle");

    public MainFrame() {
        super("Real-Time Financial Market Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.NORTH);
        add(buildCenterSplit(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
