package ui;

import model.AccountStatus;
import model.User;
import repository.*;
import service.ReportService;
import service.StockService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
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

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("From:"));
        controls.add(fromSpinner);
        controls.add(new JLabel("To:"));
        controls.add(toSpinner);
        controls.add(generateBtn);

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
        JTable table = new JTable(turnoverModel);
        UITheme.styleTable(table);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(4).setMinWidth(130);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
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

        JButton refreshBtn = UITheme.secondaryBtn("Refresh");
        refreshBtn.addActionListener(e -> refreshStock());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(refreshBtn);
        controls.add(new JLabel("  Stock values are current as of now."));

        String[] cols = {"Item", "Qty", "Unit Price (inc VAT)", "Stock Value (£)", "Status"};
        stockModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(stockModel);
        UITheme.styleTable(table);
        table.getColumnModel().getColumn(1).setMaxWidth(60);

        // highlight low-stock rows light red
        Color LOW_STOCK_COLOR = new Color(255, 210, 210);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
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
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
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

        JTable table = new JTable(debtModel);
        UITheme.styleTable(table);

        // colour status cell by account state
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
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

        debtFooter = new JLabel("  Press 'Generate Report' to load current debt figures.");
        debtFooter.setFont(UITheme.FONT_BOLD);
        debtFooter.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
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
