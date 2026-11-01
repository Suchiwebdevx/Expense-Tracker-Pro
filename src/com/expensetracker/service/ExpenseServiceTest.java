package com.expensetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.expensetracker.model.Expense;
import com.expensetracker.repository.InMemoryExpenseRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests the business rules. "Today" is fixed to 2026-10-31 so the tests never change. */
class ExpenseServiceTest {

    private ExpenseService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC);
        service = new ExpenseService(new InMemoryExpenseRepository(), fixed);
    }

    private ValidationException invalid(String title, String amount, String category, String date) {
        return assertThrows(ValidationException.class, () -> service.add(title, amount, category, date));
    }

    // ---------- adding ----------

    @Test
    void addValidExpenseGetsIdAndTwoDecimals() {
        Expense e = service.add("Tea", "200", "Food", "2026-10-31");
        assertTrue(e.getId() > 0);
        assertEquals(new BigDecimal("200.00"), e.getAmount());
        assertEquals("Food", e.getCategory());
    }

    @Test
    void titleIsTrimmed() {
        Expense e = service.add("  Tea  ", "10", "Food", "2026-10-31");
        assertEquals("Tea", e.getTitle());
    }

    @Test
    void emptyTitleIsRejected() {
        assertTrue(invalid("   ", "10", "Food", "2026-10-31").getFields().containsKey("title"));
        assertTrue(invalid(null, "10", "Food", "2026-10-31").getFields().containsKey("title"));
    }

    @Test
    void tooLongTitleIsRejected() {
        String longTitle = "a".repeat(101);
        assertTrue(invalid(longTitle, "10", "Food", "2026-10-31").getFields().containsKey("title"));
        assertEquals(100, service.add("a".repeat(100), "10", "Food", "2026-10-31").getTitle().length());
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() {
        assertEquals("Amount must be greater than 0", invalid("x", "0", "Food", "2026-10-31").getFields().get("amount"));
        assertEquals("Amount must be greater than 0", invalid("x", "-5", "Food", "2026-10-31").getFields().get("amount"));
    }

    @Test
    void nonNumericAmountIsRejected() {
        assertTrue(invalid("x", "abc", "Food", "2026-10-31").getFields().containsKey("amount"));
        assertTrue(invalid("x", "", "Food", "2026-10-31").getFields().containsKey("amount"));
        assertTrue(invalid("x", null, "Food", "2026-10-31").getFields().containsKey("amount"));
    }

    @Test
    void moreThanTwoDecimalsIsRejectedButTwoIsFine() {
        assertTrue(invalid("x", "10.555", "Food", "2026-10-31").getFields().containsKey("amount"));
        assertEquals(new BigDecimal("10.50"), service.add("x", "10.5", "Food", "2026-10-31").getAmount());
        assertEquals(new BigDecimal("10.50"), service.add("x", "10.500", "Food", "2026-10-31").getAmount());
    }

    @Test
    void hugeAmountIsRejected() {
        assertTrue(invalid("x", "10000000.01", "Food", "2026-10-31").getFields().containsKey("amount"));
        assertTrue(invalid("x", "1E999999999", "Food", "2026-10-31").getFields().containsKey("amount"));
        assertEquals(new BigDecimal("10000000.00"), service.add("x", "10000000", "Food", "2026-10-31").getAmount());
    }

    @Test
    void unknownCategoryIsRejected() {
        assertTrue(invalid("x", "10", "Gold", "2026-10-31").getFields().containsKey("category"));
        assertTrue(invalid("x", "10", null, "2026-10-31").getFields().containsKey("category"));
    }

    @Test
    void invalidAndFutureDatesAreRejected() {
        assertTrue(invalid("x", "10", "Food", "2026-02-30").getFields().containsKey("date"));
        assertTrue(invalid("x", "10", "Food", "31-10-2026").getFields().containsKey("date"));
        assertEquals("Date cannot be in the future", invalid("x", "10", "Food", "2026-11-01").getFields().get("date"));
        assertTrue(invalid("x", "10", "Food", "1999-12-31").getFields().containsKey("date"));
        assertEquals("2026-10-31", service.add("x", "10", "Food", "2026-10-31").getDate().toString());
    }

    @Test
    void allErrorsAreReportedTogether() {
        ValidationException ex = invalid("", "-1", "Nope", "bad");
        assertEquals(4, ex.getFields().size());
    }

    // ---------- update and delete ----------

    @Test
    void updateChangesTheExpenseButKeepsTheId() {
        Expense original = service.add("Tea", "10", "Food", "2026-10-30");
        Expense changed = service.update(original.getId(), "Coffee", "25.5", "Travel", "2026-10-31");
        assertEquals(original.getId(), changed.getId());
        assertEquals("Coffee", service.get(original.getId()).getTitle());
        assertEquals(new BigDecimal("25.50"), service.get(original.getId()).getAmount());
        assertEquals(1, service.list(null, null).size());
    }

    @Test
    void updateWithBadDataKeepsTheOldExpense() {
        Expense original = service.add("Tea", "10", "Food", "2026-10-30");
        assertThrows(ValidationException.class, () -> service.update(original.getId(), "", "10", "Food", "2026-10-30"));
        assertEquals("Tea", service.get(original.getId()).getTitle());
    }

    @Test
    void updateAndDeleteOfMissingExpenseThrowNotFound() {
        assertThrows(NotFoundException.class, () -> service.update(99, "x", "10", "Food", "2026-10-31"));
        assertThrows(NotFoundException.class, () -> service.delete(99));
        assertThrows(NotFoundException.class, () -> service.get(99));
    }

    @Test
    void deleteRemovesTheExpense() {
        Expense e = service.add("Tea", "10", "Food", "2026-10-31");
        service.delete(e.getId());
        assertTrue(service.list(null, null).isEmpty());
        assertThrows(NotFoundException.class, () -> service.delete(e.getId()));
    }

    // ---------- list and filters ----------

    @Test
    void listIsNewestFirstAndFiltersWork() {
        service.add("Old food", "10", "Food", "2026-09-15");
        service.add("Bus", "20", "Travel", "2026-10-20");
        service.add("New food", "30", "Food", "2026-10-31");

        List<Expense> all = service.list(null, null);
        assertEquals("New food", all.get(0).getTitle());
        assertEquals("Old food", all.get(2).getTitle());

        assertEquals(2, service.list("Food", null).size());
        assertEquals(2, service.list(null, "2026-10").size());
        assertEquals(1, service.list("Food", "2026-10").size());
        assertEquals(3, service.list("", "").size());
    }

    @Test
    void sameDayExpensesAreSortedNewestIdFirst() {
        service.add("first", "1", "Food", "2026-10-31");
        service.add("second", "1", "Food", "2026-10-31");
        assertEquals("second", service.list(null, null).get(0).getTitle());
    }

    @Test
    void badFiltersAreRejected() {
        assertThrows(ValidationException.class, () -> service.list("Gold", null));
        assertThrows(ValidationException.class, () -> service.list(null, "October"));
        assertThrows(ValidationException.class, () -> service.summary("2026-13"));
    }

    // ---------- summary ----------

    @Test
    void summaryCalculatesTotalsAverageAndHighest() {
        service.add("tea", "200", "Food", "2026-10-31");
        service.add("groceries", "4000", "Shopping", "2026-10-31");
        service.add("trip", "32000", "Travel", "2026-10-20");

        Summary s = service.summary(null);
        assertEquals(new BigDecimal("36200.00"), s.total());
        assertEquals(3, s.count());
        assertEquals(new BigDecimal("12066.67"), s.average());
        assertEquals("trip", s.highest().getTitle());
        assertEquals(List.of("Travel", "Shopping", "Food"), List.copyOf(s.byCategory().keySet()));
        assertEquals(new BigDecimal("32000.00"), s.byCategory().get("Travel"));
    }

    @Test
    void summaryCanBeLimitedToOneMonth() {
        service.add("old", "100", "Food", "2026-09-01");
        service.add("new", "50", "Food", "2026-10-01");
        assertEquals(new BigDecimal("50.00"), service.summary("2026-10").total());
    }

    @Test
    void summaryOfNothingIsZero() {
        Summary s = service.summary(null);
        assertEquals(new BigDecimal("0.00"), s.total());
        assertEquals(new BigDecimal("0.00"), s.average());
        assertEquals(0, s.count());
        assertNull(s.highest());
        assertTrue(s.byCategory().isEmpty());
    }

    @Test
    void decimalsAddUpExactly() {
        service.add("a", "0.10", "Food", "2026-10-31");
        service.add("b", "0.20", "Food", "2026-10-31");
        assertEquals(new BigDecimal("0.30"), service.summary(null).total());
    }

    // ---------- CSV ----------

    @Test
    void csvHasHeaderAndQuotesSpecialCharacters() {
        service.add("Lunch, with \"friends\"", "100", "Food", "2026-10-31");
        String csv = service.exportCsv(null, null);
        assertTrue(csv.startsWith("id,title,amount,category,date\r\n"));
        assertTrue(csv.contains("\"Lunch, with \"\"friends\"\"\""));
        assertTrue(csv.contains(",100.00,Food,2026-10-31"));
    }

    @Test
    void csvStopsFormulaInjection() {
        service.add("=HYPERLINK(\"http://evil\")", "1", "Food", "2026-10-31");
        service.add("@SUM(A1)", "1", "Food", "2026-10-31");
        String csv = service.exportCsv(null, null);
        assertFalse(csv.contains(",=HYPERLINK"));
        assertTrue(csv.contains("'=HYPERLINK"));
        assertTrue(csv.contains("'@SUM(A1)"));
        assertNotNull(csv);
    }
}
