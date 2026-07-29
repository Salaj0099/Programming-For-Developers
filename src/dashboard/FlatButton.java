package dashboard;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;

public class FlatButton extends JButton {

    private static final int CORNER_RADIUS = 8;

    private final Color baseColour;

    public FlatButton(String text, Color baseColour) {
        super(text);
        this.baseColour = baseColour;

        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setFocusPainted(false);
        setForeground(Theme.BTN_TEXT);
        setFont(Theme.FONT_BUTTON);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
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

        super.paintComponent(g);
    }

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

    private static Color lighten(Color colour) {
        return new Color(
                Math.min(255, colour.getRed() + 28),
                Math.min(255, colour.getGreen() + 28),
                Math.min(255, colour.getBlue() + 28));
    }
}
