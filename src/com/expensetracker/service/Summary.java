package com.expensetracker.service;

import com.expensetracker.model.Expense;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Totals for a set of expenses.
 * highest is null when there are no expenses. byCategory is sorted, biggest first.
 */
public record Summary(BigDecimal total, int count, BigDecimal average,
                      Expense highest, Map<String, BigDecimal> byCategory) {
}
