package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps expenses in memory only. Used by the tests; data is lost when the program stops. */
public class InMemoryExpenseRepository implements ExpenseRepository {

    private final Map<Long, Expense> data = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized List<Expense> findAll() {
        return new ArrayList<>(data.values());
    }

    @Override
    public synchronized Optional<Expense> findById(long id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public synchronized Expense save(Expense expense) {
        Expense toSave = expense;
        if (expense.getId() == 0) {
            toSave = expense.withId(nextId++);
        } else if (!data.containsKey(expense.getId())) {
            throw new IllegalArgumentException("No expense with id " + expense.getId());
        }
        data.put(toSave.getId(), toSave);
        return toSave;
    }

    @Override
    public synchronized boolean deleteById(long id) {
        return data.remove(id) != null;
    }
}
