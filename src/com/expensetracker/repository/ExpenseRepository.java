package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import java.util.List;
import java.util.Optional;

/**
 * The storage layer. The service talks only to this interface, so we can swap
 * the storage (file, memory, a real database later) without touching the service.
 */
public interface ExpenseRepository {

    /** All expenses, in the order they were created. */
    List<Expense> findAll();

    Optional<Expense> findById(long id);

    /**
     * Saves an expense. If its id is 0, it is a new expense and gets a new id.
     * Otherwise the existing expense with that id is replaced.
     * Returns the saved expense (with its id).
     */
    Expense save(Expense expense);

    /** Deletes by id. Returns true if something was deleted. */
    boolean deleteById(long id);
}
