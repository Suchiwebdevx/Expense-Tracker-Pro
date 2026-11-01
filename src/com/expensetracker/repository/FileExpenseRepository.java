package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Stores expenses in a plain text file, one expense per line, fields separated by a tab:
 * id TAB date TAB category TAB amount TAB title
 *
 * Every change is written to a temporary file first and then moved over the real file,
 * so a crash in the middle of saving cannot leave a half-written data file.
 */
public class FileExpenseRepository implements ExpenseRepository {

    private static final Logger LOG = Logger.getLogger(FileExpenseRepository.class.getName());

    private final Path file;
    private Map<Long, Expense> data = new LinkedHashMap<>();
    private long nextId = 1;

    public FileExpenseRepository(Path file) throws IOException {
        this.file = file;
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(file)) {
            load();
        }
    }

    private void load() throws IOException {
        int lineNo = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            try {
                String[] p = line.split("\t", 5);
                if (p.length != 5) {
                    throw new IllegalArgumentException("expected 5 fields");
                }
                Expense e = new Expense(Long.parseLong(p[0]), unescape(p[4]),
                        new BigDecimal(p[3]), p[2], LocalDate.parse(p[1]));
                data.put(e.getId(), e);
                nextId = Math.max(nextId, e.getId() + 1);
            } catch (RuntimeException ex) {
                LOG.warning("Skipping bad line " + lineNo + " in " + file + ": " + ex.getMessage());
            }
        }
    }

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
        long newNextId = nextId;
        if (expense.getId() == 0) {
            toSave = expense.withId(nextId);
            newNextId = nextId + 1;
        } else if (!data.containsKey(expense.getId())) {
            throw new IllegalArgumentException("No expense with id " + expense.getId());
        }
        // Change a copy first. Only if writing the file works do we keep the change.
        Map<Long, Expense> copy = new LinkedHashMap<>(data);
        copy.put(toSave.getId(), toSave);
        persist(copy);
        data = copy;
        nextId = newNextId;
        return toSave;
    }

    @Override
    public synchronized boolean deleteById(long id) {
        if (!data.containsKey(id)) {
            return false;
        }
        Map<Long, Expense> copy = new LinkedHashMap<>(data);
        copy.remove(id);
        persist(copy);
        data = copy;
        return true;
    }

    private void persist(Map<Long, Expense> map) {
        StringBuilder sb = new StringBuilder();
        for (Expense e : map.values()) {
            sb.append(e.getId()).append('\t')
              .append(e.getDate()).append('\t')
              .append(e.getCategory()).append('\t')
              .append(e.getAmount().toPlainString()).append('\t')
              .append(escape(e.getTitle())).append('\n');
        }
        try {
            Path tmp = Files.createTempFile(file.toAbsolutePath().getParent(), "expenses", ".tmp");
            Files.write(tmp, sb.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save expenses to " + file, ex);
        }
    }

    // Titles may contain tabs or new lines, which would break the file format, so we escape them.
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 't': sb.append('\t'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
