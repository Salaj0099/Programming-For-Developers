package dashboard;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * Draws the price series and its moving average as two overlaid line graphs.
 * Everything is painted by hand with Graphics2D — no charting library.
 */
public class ChartPanel extends JPanel {

    // Space reserved around the plotting area for labels, in pixels.
    private static final int PAD_LEFT = 65;
    private static final int PAD_RIGHT = 20;
    private static final int PAD_TOP = 20;
    private static final int PAD_BOTTOM = 35;

    // Line colours live in Theme so the whole palette sits in one file.
    private static final Color PRICE_COLOUR = Theme.CHART_PRICE; // navy
    private static final Color MA_COLOUR = Theme.CHART_MA;       // amber
    private static final Color AXIS_COLOUR = Theme.CHART_AXIS;

    /** All prices, oldest first. */
    private List<Double> prices = new ArrayList<>();

    /** Moving average values; shorter than prices by (window - 1). */
    private List<Double> movingAverages = new ArrayList<>();

    private int window;

    /** Shown instead of the chart whenever there is nothing to plot. */
    private String message = "No data yet — start the feed, then click Calculate Indicators.";

    public ChartPanel() {
        setBackground(Theme.SURFACE);
        setFont(Theme.FONT_LABEL);
    }

    /** Called on the EDT. Replaces the data and asks Swing for a repaint. */
    public void setData(List<Double> prices, List<Double> movingAverages, int window) {
        this.prices = new ArrayList<>(prices);
        this.movingAverages = new ArrayList<>(movingAverages);
        this.window = window;
        this.message = null;
        repaint();
    }

    /** Called on the EDT. Clears the chart and shows a line of text instead. */
    public void showMessage(String message) {
        this.prices = new ArrayList<>();
        this.movingAverages = new ArrayList<>();
        this.message = message;
        repaint();
    }

    /**
     * Swing calls this whenever the panel needs to appear on screen.
     * Never call it yourself — call repaint() instead.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // lets JPanel paint its background first

        Graphics2D g2 = (Graphics2D) g.create(); // a private copy we may freely modify
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            if (prices.isEmpty()) {
                drawCentredMessage(g2);
                return;
            }
            drawChart(g2);
        } finally {
            g2.dispose(); // release the copy's native resources
        }
    }

    private void drawCentredMessage(Graphics2D g2) {
        String text = (message == null) ? "No data yet." : message;
        FontMetrics fm = g2.getFontMetrics();

        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = getHeight() / 2;

        g2.setColor(Color.DARK_GRAY);
        g2.drawString(text, Math.max(5, x), y);
    }

    private void drawChart(Graphics2D g2) {
        int plotWidth = getWidth() - PAD_LEFT - PAD_RIGHT;
        int plotHeight = getHeight() - PAD_TOP - PAD_BOTTOM;

        if (plotWidth <= 0 || plotHeight <= 0) {
            return; // panel too small to draw anything sensible
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double price : prices) {
            min = Math.min(min, price);
            max = Math.max(max, price);
        }
        if (max - min < 0.01) { // all prices equal: invent a range so the line isn't on the edge
            min -= 0.5;
            max += 0.5;
        }

        drawAxes(g2, plotWidth, plotHeight, min, max);
        drawSeries(g2, prices, 0, PRICE_COLOUR, plotWidth, plotHeight, min, max);
        drawSeries(g2, movingAverages, window - 1, MA_COLOUR, plotWidth, plotHeight, min, max);
        drawLegend(g2);
    }

    /** Converts a position in the series (0 .. count-1) into an x pixel. */
    private int toPixelX(int index, int count, int plotWidth) {
        if (count <= 1) {
            return PAD_LEFT;
        }
        return PAD_LEFT + (int) Math.round((double) index / (count - 1) * plotWidth);
    }

    /** Converts a price into a y pixel. Bigger price = smaller y, because y grows downwards. */
    private int toPixelY(double value, double min, double max, int plotHeight) {
        double fraction = (value - min) / (max - min);
        return PAD_TOP + (int) Math.round((1 - fraction) * plotHeight);
    }

    private void drawAxes(Graphics2D g2, int plotWidth, int plotHeight, double min, double max) {
        g2.setColor(AXIS_COLOUR);
        g2.setStroke(new BasicStroke(1f));

        int left = PAD_LEFT;
        int bottom = PAD_TOP + plotHeight;
        double mid = (min + max) / 2;

        // Faint horizontal guide lines behind the data.
        g2.setColor(Theme.CHART_GRID);
        for (double value : new double[] { max, mid, min }) {
            int y = toPixelY(value, min, max, plotHeight);
            g2.drawLine(left + 1, y, left + plotWidth, y);
        }

        g2.setColor(AXIS_COLOUR);
        g2.drawLine(left, PAD_TOP, left, bottom);                  // vertical axis
        g2.drawLine(left, bottom, left + plotWidth, bottom);       // horizontal axis

        g2.setColor(Color.DARK_GRAY);
        drawPriceLabel(g2, max, min, max, plotHeight);
        drawPriceLabel(g2, mid, min, max, plotHeight);
        drawPriceLabel(g2, min, min, max, plotHeight);

        g2.drawString("oldest  ->  newest  (" + prices.size() + " quotes)",
                left + 5, bottom + 22);
    }

    /** Writes one price value to the left of the vertical axis, with a tick mark. */
    private void drawPriceLabel(Graphics2D g2, double value, double min, double max, int plotHeight) {
        int y = toPixelY(value, min, max, plotHeight);
        String text = String.format("%.2f", value);

        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, PAD_LEFT - 8 - fm.stringWidth(text), y + 4);
        g2.drawLine(PAD_LEFT - 4, y, PAD_LEFT, y);
    }

    /**
     * Joins consecutive values with straight lines.
     * {@code startIndex} shifts the series right, so each moving-average point
     * sits under the price it was calculated from.
     */
    private void drawSeries(Graphics2D g2, List<Double> values, int startIndex,
                            Color colour, int plotWidth, int plotHeight,
                            double min, double max) {
        if (values.size() < 2) {
            return;
        }

        g2.setColor(colour);
        g2.setStroke(new BasicStroke(1.8f));

        int count = prices.size(); // both series share the price axis
        int previousX = toPixelX(startIndex, count, plotWidth);
        int previousY = toPixelY(values.get(0), min, max, plotHeight);

        for (int i = 1; i < values.size(); i++) {
            int x = toPixelX(startIndex + i, count, plotWidth);
            int y = toPixelY(values.get(i), min, max, plotHeight);

            g2.drawLine(previousX, previousY, x, y);
            previousX = x;
            previousY = y;
        }
    }

    private void drawLegend(Graphics2D g2) {
        int x = PAD_LEFT + 10;
        int y = PAD_TOP + 12;

        g2.setStroke(new BasicStroke(1.8f));

        g2.setColor(PRICE_COLOUR);
        g2.drawLine(x, y - 4, x + 20, y - 4);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("Price", x + 26, y);

        g2.setColor(MA_COLOUR);
        g2.drawLine(x, y + 12, x + 20, y + 12);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("Moving average (" + window + ")", x + 26, y + 16);
    }
}
