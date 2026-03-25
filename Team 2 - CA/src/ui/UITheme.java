package ui;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;

// centralized visual theme — colors, fonts, and component styling helpers
// all methods are static, no instantiation needed
public class UITheme {

    // core palette
    public static final Color DARK_HEADER  = new Color(44, 62, 80);
    public static final Color LOGIN_BG     = new Color(23, 32, 42);
    public static final Color PRIMARY      = new Color(52, 152, 219);
    public static final Color SUCCESS      = new Color(39, 174, 96);
    public static final Color DANGER       = new Color(192, 57, 43);
    public static final Color SECONDARY    = new Color(108, 122, 137);
    public static final Color LIGHT_BG     = new Color(245, 246, 250);
    public static final Color HEADER_TEXT  = Color.WHITE;
    public static final Color SUBTEXT      = new Color(149, 165, 166);
    public static final Color FIELD_BORDER = new Color(189, 195, 199);
    public static final Color ROW_ALT      = new Color(248, 249, 250);

    // fonts
    public static final Font FONT_TITLE  = new Font("Arial", Font.BOLD, 16);
    public static final Font FONT_BODY   = new Font("Arial", Font.PLAIN, 13);
    public static final Font FONT_SMALL  = new Font("Arial", Font.PLAIN, 11);
    public static final Font FONT_BOLD   = new Font("Arial", Font.BOLD, 13);
    public static final Font FONT_BUTTON = new Font("Arial", Font.BOLD, 12);
    public static final Font FONT_LABEL  = new Font("Arial", Font.BOLD, 10);

    private UITheme() {}

    // apply flat style to an existing button with the given background color
    public static void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_BUTTON);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        // darken slightly on hover
        Color hover = darken(bg, 0.85f);
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(hover); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
    }

    // convenience factories
    public static JButton primaryBtn(String text) {
        JButton btn = new JButton(text); styleButton(btn, PRIMARY); return btn;
    }

    public static JButton successBtn(String text) {
        JButton btn = new JButton(text); styleButton(btn, SUCCESS); return btn;
    }

    public static JButton dangerBtn(String text) {
        JButton btn = new JButton(text); styleButton(btn, DANGER); return btn;
    }

    public static JButton secondaryBtn(String text) {
        JButton btn = new JButton(text); styleButton(btn, SECONDARY); return btn;
    }

    // style a table: row height, subtle horizontal lines, no vertical lines, bold dark header, alternating rows
    public static void styleTable(JTable table) {
        table.setRowHeight(26);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(235, 237, 240));
        table.setFont(FONT_BODY);
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(new Color(210, 228, 252));
        table.setSelectionForeground(Color.BLACK);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(DARK_HEADER);
        header.setForeground(Color.WHITE);
        header.setOpaque(true);
        header.setReorderingAllowed(false);

        // default alternating renderer — will be overridden in screens with custom renderers
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });
    }

    // styled dark header panel with title on the left
    public static JPanel createHeaderPanel(String title) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel label = new JLabel(title);
        label.setFont(FONT_TITLE);
        label.setForeground(HEADER_TEXT);
        header.add(label, BorderLayout.WEST);
        return header;
    }

    // overload — right-side control (e.g. search panel)
    public static JPanel createHeaderPanel(String title, JComponent rightControl) {
        JPanel header = createHeaderPanel(title);
        header.add(rightControl, BorderLayout.EAST);
        return header;
    }

    // clean border + height on a text/password field
    public static void styleTextField(JTextField field) {
        field.setFont(FONT_BODY);
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 34));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(FIELD_BORDER),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
    }

    // set the frame / panel content background to the app light gray
    public static void applyFrameBackground(JFrame frame) {
        frame.getContentPane().setBackground(LIGHT_BG);
    }

    // darken a color by multiplying each channel by factor
    private static Color darken(Color c, float factor) {
        return new Color(
            Math.max(0, (int)(c.getRed()   * factor)),
            Math.max(0, (int)(c.getGreen() * factor)),
            Math.max(0, (int)(c.getBlue()  * factor))
        );
    }
}
