package com.expensetracker.service;

import com.expensetracker.model.Expense;
import com.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The business layer: all the rules live here (what is a valid expense, how totals
 * are calculated). It does not know about HTTP or files, so it is easy to test.
 */
public class ExpenseService {

    public static final List<String> CATEGORIES =
            List.of("Food", "Travel", "Shopping", "Bills", "Health", "Entertainment", "Other");

    static final int MAX_TITLE_LENGTH = 100;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

    private final ExpenseRepository repository;
    private final Clock clock;

    public ExpenseService(ExpenseRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** The clock can be replaced in tests, so "today" is the same every time. */
    public ExpenseService(ExpenseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ---------- create, read, update, delete ----------

    public Expense add(String title, String amount, String category, String date) {
        return repository.save(validate(0, title, amount, category, date));
    }

    public Expense get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(id));
    }

    public Expense update(long id, String title, String amount, String category, String date) {
        get(id); // throws NotFoundException if it does not exist
        return repository.save(validate(id, title, amount, category, date));
    }

    public void delete(long id) {
        if (!repository.deleteById(id)) {
            throw new NotFoundException(id);
        }
    }

    /** Lists expenses, newest first. category and month ("2026-10") are optional filters. */
    public List<Expense> list(String category, String month) {
        String cat = blankToNull(category);
        YearMonth ym = parseMonth(month);
        if (cat != null && !CATEGORIES.contains(cat)) {
            throw single("category", "Unknown category");
        }
        List<Expense> result = new ArrayList<>();
        for (Expense e : repository.findAll()) {
            if (cat != null && !e.getCategory().equals(cat)) {
                continue;
            }
            if (ym != null && !YearMonth.from(e.getDate()).equals(ym)) {
                continue;
            }
            result.add(e);
        }
        result.sort(Comparator.comparing(Expense::getDate).thenComparing(Expense::getId).reversed());
        return result;
    }

    // ---------- reports ----------

    public Summary summary(String month) {
        List<Expense> expenses = list(null, month);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        Expense highest = null;
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Expense e : expenses) {
            total = total.add(e.getAmount());
            totals.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
            if (highest == null || e.getAmount().compareTo(highest.getAmount()) > 0) {
                highest = e;
            }
        }
        BigDecimal average = expenses.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> sorted = new LinkedHashMap<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(en -> sorted.put(en.getKey(), en.getValue()));
        return new Summary(total, expenses.size(), average, highest, sorted);
    }

    /** CSV text for the filtered expenses. Opens in Excel. */
    public String exportCsv(String category, String month) {
        StringBuilder sb = new StringBuilder("id,title,amount,category,date\r\n");
        for (Expense e : list(category, month)) {
            sb.append(e.getId()).append(',')
              .append(csvCell(e.getTitle())).append(',')
              .append(e.getAmount().toPlainString()).append(',')
              .append(csvCell(e.getCategory())).append(',')
              .append(e.getDate()).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Makes one CSV cell safe. Text that starts with = + - or @ could run as a formula
     * when the file is opened in Excel (CSV injection), so we put a ' in front of it.
     */
    static String csvCell(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ---------- validation ----------

    private Expense validate(long id, String title, String amountText, String category, String dateText) {
        Map<String, String> errors = new LinkedHashMap<>();

        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            errors.put("title", "Title is required");
        } else if (cleanTitle.length() > MAX_TITLE_LENGTH) {
            errors.put("title", "Title must be " + MAX_TITLE_LENGTH + " characters or fewer");
        } else if (cleanTitle.chars().anyMatch(Character::isISOControl)) {
            errors.put("title", "Title contains invalid characters");
        }

        BigDecimal amount = null;
        String a = amountText == null ? "" : amountText.trim();
        if (a.isEmpty()) {
            errors.put("amount", "Amount is required");
        } else if (a.length() > 20) {
            errors.put("amount", "Amount is not a valid number");
        } else {
            try {
                BigDecimal parsed = new BigDecimal(a);
                if (parsed.signum() <= 0) {
                    errors.put("amount", "Amount must be greater than 0");
                } else if (parsed.compareTo(MAX_AMOUNT) > 0) {
                    errors.put("amount", "Amount cannot be more than 10,000,000");
                } else if (parsed.stripTrailingZeros().scale() > 2) {
                    errors.put("amount", "Amount can have at most 2 decimal places");
                } else {
                    amount = parsed.setScale(2, RoundingMode.UNNECESSARY);
                }
            } catch (NumberFormatException ex) {
                errors.put("amount", "Amount is not a valid number");
            }
        }

        String cleanCategory = category == null ? "" : category.trim();
        if (!CATEGORIES.contains(cleanCategory)) {
            errors.put("category", "Please choose a valid category");
        }

        LocalDate date = null;
        String d = dateText == null ? "" : dateText.trim();
        if (d.isEmpty()) {
            errors.put("date", "Date is required");
        } else {
            try {
                date = LocalDate.parse(d);
                if (date.isBefore(MIN_DATE)) {
                    errors.put("date", "Date cannot be before 2000-01-01");
                } else if (date.isAfter(LocalDate.now(clock))) {
                    errors.put("date", "Date cannot be in the future");
                }
            } catch (DateTimeParseException ex) {
                errors.put("date", "Date must be a real date like 2026-10-31");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Expense(id, cleanTitle, amount, cleanCategory, date);
    }

    private static YearMonth parseMonth(String month) {
        String m = blankToNull(month);
        if (m == null) {
            return null;
        }
        try {
            return YearMonth.parse(m);
        } catch (DateTimeParseException ex) {
            throw single("month", "Month must look like 2026-10");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ValidationException single(String field, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(field, message);
        return new ValidationException(m);
    }
}
