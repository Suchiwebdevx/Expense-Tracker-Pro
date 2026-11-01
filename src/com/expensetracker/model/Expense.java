package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One expense. The object is immutable: once created it never changes.
 * To "change" an expense we create a new Expense with the same id.
 * Money uses BigDecimal because double can give results like 0.30000000000000004.
 */
public final class Expense {

    private final long id;
    private final String title;
    private final BigDecimal amount;
    private final String category;
    private final LocalDate date;

    public Expense(long id, String title, BigDecimal amount, String category, LocalDate date) {
        this.id = id;
        this.title = Objects.requireNonNull(title);
        this.amount = Objects.requireNonNull(amount);
        this.category = Objects.requireNonNull(category);
        this.date = Objects.requireNonNull(date);
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public LocalDate getDate() { return date; }

    /** Returns a copy of this expense with a different id. */
    public Expense withId(long newId) {
        return new Expense(newId, title, amount, category, date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Expense)) return false;
        Expense e = (Expense) o;
        return id == e.id && title.equals(e.title) && amount.equals(e.amount)
                && category.equals(e.category) && date.equals(e.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, amount, category, date);
    }

    @Override
    public String toString() {
        return "Expense{id=" + id + ", title=" + title + ", amount=" + amount
                + ", category=" + category + ", date=" + date + "}";
    }
}
