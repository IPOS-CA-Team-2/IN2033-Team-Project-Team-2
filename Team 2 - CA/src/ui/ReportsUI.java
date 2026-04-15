package ui;

import model.AccountStatus;
import model.User;
import repository.*;
import service.ReportService;
import service.StockService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;


// manager-only reports screen — three tabs: turnover, stock availability, aggregated debt
// each tab pulls data from ReportService and displays it in a styled table with summary footer
public class ReportsUI extends JPanel {

    private final ReportService reportService;
    private final JTabbedPane   tabs;

    // turnover tab components
    private JSpinner   fromSpinner, toSpinner;
    private JLabel     turnoverSummary;
    private DefaultTableModel turnoverModel;

    // stock tab components
    private DefaultTableModel stockModel;
    private JLabel            stockFooter;

    // debt tab components
    private DefaultTableModel debtModel;
    private JLabel            debtFooter;

    private JTable turnoverTable;
    private JTable stockTable;
    private JTable debtTable;

    public ReportsUI(User user, int initialTab) {
        SaleRepository          saleRepo  = new SaleRepositoryImpl();
        CustomerRepository      custRepo  = new CustomerRepositoryImpl();
        StockService            stockSvc  = new StockService(new StockRepositoryImpl());
        WholesaleOrderRepository orderRepo = new WholesaleOrderRepositoryImpl();
        this.reportService = new ReportService(saleRepo, custRepo, stockSvc, orderRepo);

        setLayout(new BorderLayout());
        setOpaque(false);

        add(UITheme.createHeaderPanel("Management Reports"), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.setFont(UITheme.FONT_BOLD);
        tabs.addTab("Sales / Turnover",      buildTurnoverTab());
        tabs.addTab("Stock Availability",    buildStockTab());
        tabs.addTab("Aggregated Debt",       buildDebtTab());
        tabs.setSelectedIndex(Math.min(initialTab, 2));
        add(tabs, BorderLayout.CENTER);

        // load stock on open since it needs no input
        refreshStock();
    }

    // ---- turnover tab ----

    private JPanel buildTurnoverTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // date range controls
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyAgo = cal.getTime();

        fromSpinner = makeDateSpinner(thirtyAgo);
        toSpinner   = makeDateSpinner(today);

        JButton generateBtn = UITheme.primaryBtn("Generate Report");
        generateBtn.addActionListener(e -> generateTurnover());

        JButton printBtn = UITheme.secondaryBtn("Print / Save PDF");
        printBtn.addActionListener(e -> printTable(turnoverTable, "Sales / Turnover Report"));


        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("From:"));
        controls.add(fromSpinner);
        controls.add(new JLabel("To:"));
        controls.add(toSpinner);
        controls.add(generateBtn);

        controls.add(printBtn);


        turnoverSummary = new JLabel(" ");
        turnoverSummary.setFont(UITheme.FONT_BODY);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(controls, BorderLayout.NORTH);
        top.add(turnoverSummary, BorderLayout.SOUTH);

        String[] cols = {"Sale ID", "Date", "Customer", "Payment", "Total (£ inc VAT)"};
        turnoverModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        turnoverTable = new JTable(turnoverModel);
        UITheme.styleTable(turnoverTable);
        turnoverTable.getColumnModel().getColumn(0).setMaxWidth(70);
        turnoverTable.getColumnModel().getColumn(4).setMinWidth(130);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(turnoverTable), BorderLayout.CENTER);
        return panel;
    }

    private void printTable(JTable table, String title) {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No data to print. Please generate the report first.",
                    "Nothing to Print", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // build HTML
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>").append(title).append("</title>");
            html.append("<script>window.onload = function() { window.print(); }</script>");
            html.append("<style>");
            html.append("body { font-family: Arial, sans-serif; font-size: 12px; }");
            html.append("h2 { color: #2c3e50; }");
            html.append("table { border-collapse: collapse; width: 100%; }");
            html.append("th { background: #2c3e50; color: white; padding: 6px; text-align: left; }");
            html.append("td { padding: 5px; border-bottom: 1px solid #ddd; }");
            html.append("tr:nth-child(even) { background: #f8f9fa; }");
            html.append("</style></head><body>");
            html.append("<h2>").append(title).append("</h2>");
            html.append("<table><thead><tr>");

            // headers
            for (int c = 0; c < table.getColumnCount(); c++) {
                html.append("<th>").append(table.getColumnName(c)).append("</th>");
            }
            html.append("</tr></thead><tbody>");

            // rows
            for (int r = 0; r < table.getRowCount(); r++) {
                html.append("<tr>");
                for (int c = 0; c < table.getColumnCount(); c++) {
                    Object val = table.getValueAt(r, c);
                    html.append("<td>").append(val != null ? val.toString() : "").append("</td>");
                }
                html.append("</tr>");
            }

            html.append("</tbody></table></body></html>");

            // write to temp file
            java.io.File temp = java.io.File.createTempFile("ipos_report_", ".html");
            temp.deleteOnExit();
            java.nio.file.Files.writeString(temp.toPath(), html.toString());

            // open in browser
            Desktop.getDesktop().browse(temp.toURI());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open report: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void generateTurnover() {
        LocalDate from = toLocalDate((Date) fromSpinner.getValue());
        LocalDate to   = toLocalDate((Date) toSpinner.getValue());

        if (from.isAfter(to)) {
            JOptionPane.showMessageDialog(this, "From date must be before To date.", "Invalid Range", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ReportService.TurnoverReport r = reportService.generateTurnoverReport(from, to);
        turnoverModel.setRowCount(0);

        for (ReportService.SaleRow row : r.rows) {
            turnoverModel.addRow(new Object[]{
                row.saleId, row.date, row.customerLabel,
                row.paymentMethod, String.format("£%.2f", row.total)
            });
        }

        turnoverSummary.setText(String.format(
            "  %d sale(s)  |  Revenue: £%.2f  |  Cash: %d  |  Card: %d  |  Wholesale orders: %d  |  Order total: £%.2f",
            r.saleCount, r.totalRevenue, r.cashCount, r.cardCount, r.orderCount, r.orderTotal
        ));
    }

    // ---- stock tab ----

    private JPanel buildStockTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton refreshBtn   = UITheme.secondaryBtn("Refresh");
        refreshBtn.addActionListener(e -> refreshStock());

        JButton lowStockBtn = UITheme.primaryBtn("Low Stock Report");
        lowStockBtn.addActionListener(e -> showLowStockReport());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(refreshBtn);
        controls.add(lowStockBtn);
        controls.add(new JLabel("  Stock values are current as of now."));

        String[] cols = {"Item", "Qty", "Unit Price (inc VAT)", "Stock Value (£)", "Status"};
        stockModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JButton printBtn = UITheme.secondaryBtn("Print / Save PDF");
        printBtn.addActionListener(e -> printTable(stockTable, "Stock Availability Report"));
        controls.add(printBtn);

        stockTable = new JTable(stockModel);
        UITheme.styleTable(stockTable);
        stockTable.getColumnModel().getColumn(1).setMaxWidth(60);

        // highlight low-stock rows light red
        Color LOW_STOCK_COLOR = new Color(255, 210, 210);
        stockTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    String status = (String) stockModel.getValueAt(row, 4);
                    c.setBackground("LOW STOCK".equals(status) ? LOW_STOCK_COLOR
                            : (row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT));
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        stockFooter = new JLabel("  Total stock value: —");
        stockFooter.setFont(UITheme.FONT_BOLD);
        stockFooter.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(stockTable), BorderLayout.CENTER);
        panel.add(stockFooter, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshStock() {
        if (stockModel == null) return;
        ReportService.StockReport r = reportService.generateStockReport();
        stockModel.setRowCount(0);
        for (ReportService.StockRow row : r.rows) {
            stockModel.addRow(new Object[]{
                row.name,
                row.quantity,
                String.format("£%.2f", row.unitPriceIncVat),
                String.format("£%.2f", row.stockValue),
                row.lowStock ? "LOW STOCK" : "OK"
            });
        }
        stockFooter.setText(String.format("  Total stock value: £%.2f", r.totalStockValue));
    }

    // ---- debt tab ----

    private JPanel buildDebtTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton generateBtn = UITheme.primaryBtn("Generate Report");
        generateBtn.addActionListener(e -> generateDebt());

        JLabel note = new JLabel("  Shows current outstanding balances across all account holders.");
        note.setFont(UITheme.FONT_SMALL);
        note.setForeground(UITheme.SUBTEXT);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(generateBtn);
        controls.add(note);

        String[] cols = {"Account No", "Name", "Status", "Balance (£)", "Credit Limit (£)", "Utilisation %"};
        debtModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        debtTable = new JTable(debtModel);
        UITheme.styleTable(debtTable);

        // colour status cell by account state
        debtTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    String status = value != null ? value.toString() : "";
                    switch (status) {
                        case "IN_DEFAULT": c.setBackground(new Color(255, 200, 200)); break;
                        case "SUSPENDED":  c.setBackground(new Color(255, 235, 180)); break;
                        default:           c.setBackground(new Color(200, 240, 200)); break;
                    }
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        JButton printBtn = UITheme.secondaryBtn("Print / Save PDF");
        printBtn.addActionListener(e -> printTable(debtTable, "Aggregated Debt Report"));
        controls.add(printBtn);

        debtFooter = new JLabel("  Press 'Generate Report' to load current debt figures.");
        debtFooter.setFont(UITheme.FONT_BOLD);
        debtFooter.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(debtTable), BorderLayout.CENTER);
        panel.add(debtFooter, BorderLayout.SOUTH);
        return panel;
    }

    private void generateDebt() {
        ReportService.DebtReport r = reportService.generateDebtReport();
        debtModel.setRowCount(0);

        for (ReportService.DebtRow row : r.rows) {
            debtModel.addRow(new Object[]{
                row.accountNumber,
                row.name,
                row.status.name(),
                String.format("£%.2f", row.balance),
                String.format("£%.2f", row.creditLimit),
                String.format("%.1f%%", row.utilisationPct)
            });
        }

        debtFooter.setText(String.format(
            "  Total outstanding debt: £%.2f  |  %d account holder(s) in arrears  |  Snapshot: %s",
            r.totalDebt, r.debtorCount, r.generatedAt
        ));
    }

    // opens a modal dialog listing every item that is at or below its stock threshold
    private void showLowStockReport() {
        ReportService.LowStockReport report = reportService.generateLowStockReport();

        // build the dialog
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = (owner instanceof Frame)
            ? new JDialog((Frame) owner, "Low Stock Report", true)
            : new JDialog((Dialog) owner, "Low Stock Report", true);
        dialog.setSize(780, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(0, 0));

        // header
        JLabel header = new JLabel(
            "  Items Below Stock Threshold  —  " + report.lowStockCount + " item(s) require attention",
            SwingConstants.LEFT);
        header.setFont(UITheme.FONT_BOLD);
        header.setOpaque(true);
        header.setBackground(UITheme.PRIMARY);
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        dialog.add(header, BorderLayout.NORTH);

        // table
        String[] cols = {"Item Code", "Description", "Package Type", "Unit", "Current Stock (packs)", "Min Threshold (packs)"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        Color LOW_RED = new Color(255, 210, 210);
        JTable table = new JTable(model);
        UITheme.styleTable(table);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? LOW_RED : new Color(255, 230, 230));
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(4).setMaxWidth(170);
        table.getColumnModel().getColumn(5).setMaxWidth(170);

        if (report.rows.isEmpty()) {
            model.addRow(new Object[]{"—", "All items are above their stock thresholds", "", "", "", ""});
        } else {
            for (ReportService.LowStockRow row : report.rows) {
                model.addRow(new Object[]{
                    row.itemCode.isBlank() ? "—" : row.itemCode,
                    row.name,
                    row.packageType.isBlank() ? "—" : row.packageType,
                    row.unit.isBlank()        ? "—" : row.unit,
                    row.quantity,
                    row.threshold
                });
            }
        }

        dialog.add(new JScrollPane(table), BorderLayout.CENTER);

        // footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        footer.setOpaque(false);

        JButton printBtn = UITheme.secondaryBtn("Print / Save PDF");
        printBtn.addActionListener(e -> printTable(table, "Low Stock Report — " + report.generatedAt));

        JButton closeBtn = UITheme.primaryBtn("Close");
        closeBtn.addActionListener(e -> dialog.dispose());

        footer.add(printBtn);
        footer.add(closeBtn);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // ---- helpers ----

    private JSpinner makeDateSpinner(Date initial) {
        SpinnerDateModel model = new SpinnerDateModel(initial, null, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        spinner.setEditor(new JSpinner.DateEditor(spinner, "dd/MM/yyyy"));
        spinner.setPreferredSize(new Dimension(110, 28));
        return spinner;
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
