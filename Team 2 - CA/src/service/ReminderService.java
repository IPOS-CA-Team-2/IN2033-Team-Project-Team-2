package service;

import model.*;
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

    // builds the reminder letter text per appendix 6 layout
    // payment due date is always set to current date + 7 days per brief spec
    private String formatLetter(Customer customer, Reminder.Type type, LocalDate date) {
        StringBuilder sb = new StringBuilder();
        String line = "─".repeat(60);
        LocalDate paymentDue = date.plusDays(7);

        // sender block
        sb.append(PharmacyConfig.getName()).append("\n");
        sb.append(PharmacyConfig.getAddress()).append("\n");
        sb.append(PharmacyConfig.getCity()).append(" ").append(PharmacyConfig.getPostcode()).append("\n");
        sb.append(PharmacyConfig.getPhone()).append("\n");
        if (PharmacyConfig.getFax() != null && !PharmacyConfig.getFax().isBlank()) {
            sb.append(PharmacyConfig.getFax()).append("\n");
        }
        sb.append("\n");

        // date and recipient
        sb.append(date.format(DATE_FMT)).append("\n\n");
        sb.append(customer.getName()).append("\n");
        if (customer.getAddress() != null && !customer.getAddress().isBlank()) {
            sb.append(customer.getAddress()).append("\n");
        }
        sb.append("\n");

        // subject line
        String ordinal = type == Reminder.Type.FIRST ? "First" : "Second";
        sb.append(line).append("\n");
        sb.append("Re: Account ").append(customer.getAccountNumber())
          .append(" — ").append(ordinal).append(" Payment Reminder\n");
        sb.append(line).append("\n\n");

        sb.append("Dear ").append(customer.getName()).append(",\n\n");

        if (type == Reminder.Type.FIRST) {
            sb.append("We write to inform you that your account (").append(customer.getAccountNumber())
              .append(") has an outstanding balance of £")
              .append(String.format("%.2f", customer.getCurrentBalance())).append(".\n\n");
            sb.append("As this balance has not been settled by the agreed date, your account has ")
              .append("been suspended. Please remit the full amount by ")
              .append(paymentDue.format(DATE_FMT))
              .append(" to restore normal account access.\n\n");
            sb.append("If payment has already been made, please disregard this notice.\n\n");
        } else {
            sb.append("We write to draw your urgent attention to the outstanding balance of £")
              .append(String.format("%.2f", customer.getCurrentBalance()))
              .append(" on your account (").append(customer.getAccountNumber()).append(").\n\n");
            sb.append("Despite our previous reminder, this balance remains unpaid. ")
              .append("Please remit payment in full by ")
              .append(paymentDue.format(DATE_FMT))
              .append(". Failure to do so will result in your account being referred for further action.\n\n");
            sb.append("We strongly urge you to contact us immediately to arrange payment.\n\n");
        }

        sb.append("Yours sincerely,\n\n");
        sb.append(PharmacyConfig.getName()).append("\n");
        sb.append(line);

        return sb.toString();
    }
}
