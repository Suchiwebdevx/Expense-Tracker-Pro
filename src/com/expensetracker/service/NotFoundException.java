package com.expensetracker.service;

/** Thrown when an expense with the given id does not exist. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(long id) {
        super("Expense " + id + " not found");
    }
}
