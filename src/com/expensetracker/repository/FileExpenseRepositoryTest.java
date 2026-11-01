package com.expensetracker.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.expensetracker.model.Expense;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileExpenseRepositoryTest {

    private Path dir;
    private Path file;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("etp-test");
        file = dir.resolve("data").resolve("expenses.txt"); // the "data" folder does not exist yet
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static Expense newExpense(String title, String amount) {
        return new Expense(0, title, new BigDecimal(amount), "Food", LocalDate.parse("2026-10-31"));
    }

    @Test
    void savedExpensesSurviveARestart() throws IOException {
        FileExpenseRepository repo = new FileExpenseRepository(file);
        Expense a = repo.save(newExpense("Tea", "200.00"));
        Expense b = repo.save(newExpense("Bus", "20.50"));
        assertEquals(1, a.getId());
        assertEquals(2, b.getId());

        FileExpenseRepository reloaded = new FileExpenseRepository(file); // like restarting the program
        assertEquals(List.of(a, b), reloaded.findAll());
        assertEquals(a, reloaded.findById(1).get());
    }

    @Test
    void idsKeepGrowingAfterARestartAndAfterADelete() throws IOException {
        FileExpenseRepository repo = new FileExpenseRepository(file);
        repo.save(newExpense("one", "1"));
        Expense two = repo.save(newExpense("two", "2"));
        assertTrue(repo.deleteById(two.getId()));

        FileExpenseRepository reloaded = new FileExpenseRepository(file);
        assertEquals(1, reloaded.findAll().size());
        assertEquals(2, reloaded.save(newExpense("three", "3")).getId()); // 2 is free again after reload
    }

    @Test
    void updateReplacesTheExpense() throws IOException {
        FileExpenseRepository repo = new FileExpenseRepository(file);
        Expense a = repo.save(newExpense("Tea", "10"));
        repo.save(new Expense(a.getId(), "Coffee", new BigDecimal("12.00"), "Food", a.getDate()));
        assertEquals("Coffee", new FileExpenseRepository(file).findById(a.getId()).get().getTitle());
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void saveOfUnknownIdFails() throws IOException {
        FileExpenseRepository repo = new FileExpenseRepository(file);
        assertThrows(IllegalArgumentException.class,
                () -> repo.save(new Expense(5, "x", BigDecimal.ONE, "Food", LocalDate.parse("2026-10-31"))));
    }

    @Test
    void deleteOfUnknownIdReturnsFalse() throws IOException {
        assertFalse(new FileExpenseRepository(file).deleteById(42));
    }

    @Test
    void tabsNewLinesBackslashesAndUnicodeInTitlesSurvive() throws IOException {
        String tricky = "a\tb\nc\\d ₹ é";
        FileExpenseRepository repo = new FileExpenseRepository(file);
        repo.save(newExpense(tricky, "5"));
        assertEquals(tricky, new FileExpenseRepository(file).findAll().get(0).getTitle());
        assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size()); // still one line in the file
    }

    @Test
    void badLinesAreSkippedInsteadOfCrashing() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "1\t2026-10-31\tFood\t10.00\tTea\nthis is garbage\n\nx\ty\n3\t2026-10-31\tFood\t5.00\tBus\n");
        FileExpenseRepository repo = new FileExpenseRepository(file);
        assertEquals(2, repo.findAll().size());
        assertEquals(4, repo.save(newExpense("next", "1")).getId());
    }
}
