package dashboard;

import java.awt.Color;
import java.awt.Font;

/**
 * Every colour and font used by the dashboard, in one place.
 * Nothing here has any behaviour — it is purely the visual palette.
 */
public final class Theme {

    private Theme() { } // utility class: never instantiated

    // ---- surfaces -------------------------------------------------------
    public static final Color BACKGROUND = new Color(0xF4, 0xF6, 0xF9); // page behind panels
    public static final Color SURFACE = Color.WHITE;                    // tables, chart
    public static final Color HEADER_BG = new Color(0x1E, 0x2A, 0x44);  // deep navy banner
    public static final Color HEADER_TEXT = Color.WHITE;
    public static final Color HEADER_SUBTEXT = new Color(0xA8, 0xB4, 0xCC);

    // ---- buttons --------------------------------------------------------
    public static final Color BTN_START = new Color(0x2E, 0x7D, 0x32);  // green
    public static final Color BTN_STOP = new Color(0xC6, 0x28, 0x28);   // red
    public static final Color BTN_CALC = new Color(0x1F, 0x3A, 0x93);   // navy
    public static final Color BTN_DISABLED = new Color(0xC9, 0xCF, 0xD8);
    public static final Color BTN_TEXT = Color.WHITE;
    /** Label colour on a disabled button; dark enough to stay readable on BTN_DISABLED. */
    public static final Color BTN_DISABLED_TEXT = new Color(0x5A, 0x62, 0x70);

    // ---- status bar states ---------------------------------------------
    public static final Color STATUS_IDLE_BG = new Color(0xDD, 0xE1, 0xE7);
    public static final Color STATUS_IDLE_FG = new Color(0x33, 0x3A, 0x45);
    public static final Color STATUS_ACTIVE_BG = new Color(0x1B, 0x7F, 0x3B);
    public static final Color STATUS_ACTIVE_FG = Color.WHITE;
    public static final Color STATUS_ERROR_BG = new Color(0xC6, 0x28, 0x28);
    public static final Color STATUS_ERROR_FG = Color.WHITE;

    // ---- table ----------------------------------------------------------
    public static final Color TABLE_HEADER_BG = new Color(0xE8, 0xEC, 0xF2);
    public static final Color TABLE_HEADER_FG = new Color(0x1E, 0x2A, 0x44);
    public static final Color GRID = new Color(0xDC, 0xE1, 0xE8);
    public static final Color SELECTION_BG = new Color(0xD6, 0xE2, 0xF7);
    public static final Color TEXT = new Color(0x21, 0x25, 0x2B);

    /** Price went up: soft green fill, strong green text. */
    public static final Color RISE_BG = new Color(0xE3, 0xF6, 0xE9);
    public static final Color RISE_FG = new Color(0x1B, 0x7F, 0x3B);

    /** Price went down: soft red fill, strong red text. */
    public static final Color FALL_BG = new Color(0xFC, 0xE6, 0xE6);
    public static final Color FALL_FG = new Color(0xB3, 0x26, 0x1E);

    // ---- chart lines (chosen by the user) -------------------------------
    public static final Color CHART_PRICE = new Color(0x1F, 0x3A, 0x93);  // navy
    public static final Color CHART_MA = new Color(0xE8, 0xA3, 0x3D);     // amber
    public static final Color CHART_AXIS = new Color(0x8C, 0x95, 0xA3);
    public static final Color CHART_GRID = new Color(0xEC, 0xEF, 0xF3);

    // ---- fonts ----------------------------------------------------------
    // Java quietly substitutes a default face if "Segoe UI" is unavailable.
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_LABEL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_STATUS = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_TABLE = new Font("Segoe UI", Font.PLAIN, 13);
}
