package dashboard;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Decides how one Price cell is drawn: green if that symbol's price rose,
 * red if it fell, plain if it is the symbol's first quote.
 *
 * The direction was worked out when the row was added and stored in a hidden
 * model column, so this renderer only has to read it — never recalculate it.
 */
public class PriceCellRenderer extends DefaultTableCellRenderer {

    /** Hidden column holding -1 (fell), 0 (unchanged/first) or 1 (rose). */
    private static final int DIRECTION_COLUMN = 3;

    public PriceCellRenderer() {
        setHorizontalAlignment(SwingConstants.RIGHT); // numbers read better right-aligned
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {

        // Let the superclass set the text, font and selection defaults first.
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int direction = readDirection(table, row);

        if (isSelected) {
            // While a row is selected, let the selection colours win so the
            // user can still see what they have clicked on.
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else if (direction > 0) {
            setBackground(Theme.RISE_BG);
            setForeground(Theme.RISE_FG);
        } else if (direction < 0) {
            setBackground(Theme.FALL_BG);
            setForeground(Theme.FALL_FG);
        } else {
            // Always reset explicitly: this component is reused for every cell,
            // so leftover colours from a previous cell would otherwise persist.
            setBackground(table.getBackground());
            setForeground(Theme.TEXT);
        }

        return this;
    }

    private int readDirection(JTable table, int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        Object stored = table.getModel().getValueAt(modelRow, DIRECTION_COLUMN);

        return (stored instanceof Integer) ? (Integer) stored : 0;
    }
}
