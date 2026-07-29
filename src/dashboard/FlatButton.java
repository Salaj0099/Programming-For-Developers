package dashboard;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;

/**
 * A JButton that paints its own flat, rounded, coloured background.
 *
 * The stock Windows look-and-feel paints its own button skin and ignores
 * setBackground(), so the only reliable way to get a custom colour is to
 * switch that skin off and draw the shape ourselves.
 */
public class FlatButton extends JButton {

    private static final int CORNER_RADIUS = 8;

    /** The button's normal colour; hover and pressed states are derived from it. */
    private final Color baseColour;

    public FlatButton(String text, Color baseColour) {
        super(text);
        this.baseColour = baseColour;

        setContentAreaFilled(false); // stop the look-and-feel filling the background
        setBorderPainted(false);     // ...and drawing its own border
        setOpaque(false);            // ...and stop Swing blanking our rounded shape
        setFocusPainted(false);      // no dotted focus rectangle
        setForeground(Theme.BTN_TEXT);
        setFont(Theme.FONT_BUTTON);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16)); // inner padding
        setRolloverEnabled(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(currentFill());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS);
        } finally {
            g2.dispose();
        }

        super.paintComponent(g); // draws the label text on top of our shape
    }

    /** Picks the fill colour for the button's current state. */
    private Color currentFill() {
        if (!isEnabled()) {
            return Theme.BTN_DISABLED;
        }
        if (getModel().isPressed()) {
            return baseColour.darker();
        }
        if (getModel().isRollover()) {
            return lighten(baseColour);
        }
        return baseColour;
    }

    /** Moves a colour part of the way towards white. */
    private static Color lighten(Color colour) {
        return new Color(
                Math.min(255, colour.getRed() + 28),
                Math.min(255, colour.getGreen() + 28),
                Math.min(255, colour.getBlue() + 28));
    }
}
