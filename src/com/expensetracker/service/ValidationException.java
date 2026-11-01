package com.expensetracker.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thrown when the input is not valid. Holds one message per bad field. */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fields;

    public ValidationException(Map<String, String> fields) {
        super("Validation failed: " + fields);
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
