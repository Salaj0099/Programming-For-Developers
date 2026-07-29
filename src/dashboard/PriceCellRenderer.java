package dashboard;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

public class PriceCellRenderer extends DefaultTableCellRenderer {

    private static final int DIRECTION_COLUMN = 3;

    public PriceCellRenderer() {
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int direction = readDirection(table, row);

        if (isSelected) {
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else if (direction > 0) {
            setBackground(Theme.RISE_BG);
            setForeground(Theme.RISE_FG);
        } else if (direction < 0) {
            setBackground(Theme.FALL_BG);
            setForeground(Theme.FALL_FG);
        } else {
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
