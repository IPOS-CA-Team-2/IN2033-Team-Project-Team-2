package service;

import model.*;
import repository.ConfigRepository;
import repository.CustomerRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// generates payment reminder letters when account timing rules are met
// 1st reminder when account is newly suspended (status_1st_reminder = 'due')
// 2nd reminder when account is heading to in default (status_2nd_reminder = 'due')
public class ReminderService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final CustomerRepository customerRepository;

    public ReminderService(CustomerRepository customerRepository) {
        if (customerRepository == null) throw new IllegalArgumentException("CustomerRepository cannot be null");
        this.customerRepository = customerRepository;
    }

    // scan all customers for due reminders, generate letters, mark them as sent
    // follows the exact pseudocode algorithm from the brief
    public List<Reminder> generateDueReminders() {
        List<Reminder> generated = new ArrayList<>();
        List<Customer> customers = customerRepository.findAll();
        LocalDate today = LocalDate.now();

        for (Customer customer : customers) {

            // --- 1st reminder ---
            // if (status_1stReminder = 'due') → generate, mark sent, schedule 2nd for +15 days
            if ("due".equals(customer.getStatus1stReminder())) {
                generated.add(buildReminder(customer, Reminder.Type.FIRST, today));
                customerRepository.updateStatus(
                    customer.getCustomerId(),
                    customer.getStatus(),
                    "sent",
                    customer.getStatus2ndReminder(),
                    customer.getDate1stReminder(),
                    today.plusDays(15),      // schedule 2nd reminder 15 days from now
                    customer.getStatementDate()
                );
            }

            // --- 2nd reminder ---
            // if (status_2ndReminder = 'due') AND date_2ndReminder <= today → generate
            if ("due".equals(customer.getStatus2ndReminder())) {
                // re-fetch so we see the updated date_2ndReminder if 1st was just processed above
                Customer c = customerRepository.findById(customer.getCustomerId());
                if (c == null) c = customer;

                // null date means 'now' i.e. immediately eligible; otherwise wait until scheduled date
                LocalDate scheduledDate = c.getDate2ndReminder();
                if (scheduledDate == null || !today.isBefore(scheduledDate)) {
                    generated.add(buildReminder(c, Reminder.Type.SECOND, today));
                    customerRepository.updateStatus(
                        c.getCustomerId(),
                        c.getStatus(),
                        c.getStatus1stReminder(),
                        "sent",
                        c.getDate1stReminder(),
                        c.getDate2ndReminder(),
                        c.getStatementDate()
                    );
                }
            }
        }

        return generated;
    }

    // generate a single reminder for a specific customer — for preview or manual resend
    public Reminder generateReminder(Customer customer, Reminder.Type type) {
        return buildReminder(customer, type, LocalDate.now());
    }

    private Reminder buildReminder(Customer customer, Reminder.Type type, LocalDate date) {
        return new Reminder(
            0,
            customer.getCustomerId(),
            customer.getName(),
            customer.getAccountNumber(),
            customer.getAddress(),
            type,
            date,
            customer.getCurrentBalance(),
            formatLetter(customer, type, date)
        );
    }

    // changed this method to use the templates provided/made in the ui

    private String formatLetter(Customer customer, Reminder.Type type, LocalDate date) {
        ConfigRepository configRepo = new ConfigRepository();
        LocalDate paymentDue = date.plusDays(7);
        String line = "─".repeat(60);

        // pick the right template
        String template = type == Reminder.Type.FIRST
                ? configRepo.get("reminder1_template")
                : configRepo.get("reminder2_template");

        // replace all placeholders
        String letter = template
                .replace("{customer_name}", customer.getName())
                .replace("{account_no}",    customer.getAccountNumber())
                .replace("{amount}",        String.format("%.2f", customer.getCurrentBalance()))
                .replace("{pharmacy_name}", configRepo.get("pharmacy_name"))
                .replace("{pharmacy_address}", configRepo.get("pharmacy_address"))
                .replace("{pharmacy_email}", configRepo.get("pharmacy_email"))
                .replace("{pharmacy_phone}", configRepo.get("pharmacy_phone"))
                .replace("{date}",          date.format(DATE_FMT))
                .replace("{payment_due}",   paymentDue.format(DATE_FMT))
                .replace("{line}",          line);

        return letter;
    }
    public List<String[]> generateMonthlyStatements() {
        List<String[]> statements = new ArrayList<>();
        List<Customer> customers = customerRepository.findAll();
        LocalDate today = LocalDate.now();

        for (Customer c : customers) {
            if (c.getCurrentBalance() > 0) {
                statements.add(new String[]{ c.getName(), formatStatement(c, today) });
            }
        }
        return statements;
    }

    private String formatStatement(Customer customer, LocalDate date) {
        ConfigRepository configRepo = new ConfigRepository();
        String line = "─".repeat(60);
        String prevMonth = date.minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        LocalDate dueDate = date.withDayOfMonth(15);
        if (date.getDayOfMonth() > 15) {
            dueDate = date.plusMonths(1).withDayOfMonth(15);
        }

        String address = customer.getAddress() != null && !customer.getAddress().isBlank()
                ? customer.getAddress() + "\n" : "";

        return line + "\n"
                + configRepo.get("pharmacy_name") + "\n"
                + configRepo.get("pharmacy_address") + "\n"
                + configRepo.get("pharmacy_phone") + "\n"
                + configRepo.get("pharmacy_email") + "\n"
                + line + "\n\n"
                + "Date: " + date.format(DATE_FMT) + "\n\n"
                + customer.getName() + "\n"
                + address
                + "\nAccount Number: " + customer.getAccountNumber() + "\n\n"
                + "MONTHLY STATEMENT\n"
                + line + "\n\n"
                + "Dear " + customer.getName() + ",\n\n"
                + "This is your monthly statement for purchases made in " + prevMonth + ".\n\n"
                + "Your outstanding balance is:  £" + String.format("%.2f", customer.getCurrentBalance()) + "\n\n"
                + "Please pay the full amount by " + dueDate.format(DATE_FMT) + ".\n\n"
                + "Payment can be made by credit or debit card in store.\n\n"
                + "Yours sincerely,\n\n"
                + configRepo.get("pharmacy_name") + "\n"
                + line;
    }
}
