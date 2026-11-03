# Expense Tracker Pro

A full-stack expense tracker. A **Java backend** (REST API, validation, file storage) serves a **web page** (HTML, CSS, JavaScript). It needs **no libraries and no internet**, only a JDK.

Capstone project for the YuvaIntern Full Stack Developer Fresher internship, by Suchi Upadhyay.

## Features

- Add, edit and delete expenses (title, amount, category, date)
- Totals, average, highest expense, and a bar for each category
- Filter by category and by month
- Export the shown expenses to a CSV file (opens in Excel)
- Data is saved in a file, so it is still there after a restart
- Clear error messages for every wrong input, shown next to the field
- Works on desktop and phone

## HOW IT IS BUILT

```
Browser (index.html, style.css, app.js)
        |  fetch() with JSON
        v
 web layer         ApiServer, Json          reads requests, writes JSON, security headers
        |
 service layer     ExpenseService           all rules: validation, totals, filters, CSV
        |
 storage layer     ExpenseRepository        interface
                   FileExpenseRepository    saves to data/expenses.txt
                   InMemoryExpenseRepository  used by tests
```

Each layer only talks to the layer below it, so each can be tested and changed on its own.

## Run it in Eclipse

1. **File -> Import -> General -> Existing Projects into Workspace**, choose the `ExpenseTrackerPro` folder, click **Finish**.
2. In Package Explorer open `src -> com.expensetracker -> Main.java`.
3. Right-click `Main.java` -> **Run As -> Java Application**.
4. The Console shows `Expense Tracker Pro is running.` Open **http://localhost:8080** in your browser.
5. Stop it with the red square button in the Console.

Your data is saved in `data/expenses.txt` inside the project folder.

## Run the tests

Right-click the project -> **Run As -> JUnit Test**. There are 51 tests in 4 classes:

| Class | What it tests |
|---|---|
| ExpenseServiceTest | validation rules, add/update/delete, filters, totals, CSV |
| JsonTest | the JSON reader and writer |
| FileExpenseRepositoryTest | saving, reloading, bad lines in the file |
| ApiServerTest | the real server over HTTP (status codes, errors, security headers) |

## Run without Eclipse

```
javac -d out $(find src/com -name "*.java" ! -name "*Test.java")
cp -r src/web out/web
java -cp out com.expensetracker.Main
```

Settings (environment variables): `PORT` (default 8080), `HOST` (default 127.0.0.1, only this computer), `EXPENSE_DATA_DIR` (default `data`).

## REST API

| Method | Address | What it does |
|---|---|---|
| GET | /api/health | `{"status":"UP"}` for monitoring |
| GET | /api/categories | list of categories |
| GET | /api/expenses?category=Food&month=2026-10 | list, newest first (filters optional) |
| POST | /api/expenses | create (201) |
| GET | /api/expenses/{id} | one expense |
| PUT | /api/expenses/{id} | update |
| DELETE | /api/expenses/{id} | delete (204) |
| GET | /api/summary?month=2026-10 | total, count, average, highest, per category |
| GET | /api/export.csv?category=Food&month=2026-10 | CSV download |

Example:

```
POST /api/expenses      Content-Type: application/json
{"title":"Tea","amount":"20.50","category":"Food","date":"2026-10-31"}
```

Errors: `400` wrong input (with a message for each field), `404` not found, `405` wrong method, `413` body over 16 KB, `415` not JSON, `500` unexpected error.

## Rules for an expense

| Field | Rule |
|---|---|
| title | required, 1 to 100 characters, no control characters |
| amount | greater than 0, at most 10,000,000, at most 2 decimal places |
| category | Food, Travel, Shopping, Bills, Health, Entertainment or Other |
| date | a real date (YYYY-MM-DD), from 2000-01-01 up to today |

## Security

- The page shows data with `textContent`, so a title like `<script>...` is shown as text and never runs.
- All input is checked again on the server, because the browser can be bypassed.
- Money uses `BigDecimal`, so totals are exact (0.10 + 0.20 = 0.30).
- CSV export stops "formula injection" (cells starting with `= + - @`).
- Request body limit 16 KB, only the 4 web files can be downloaded, the server listens on localhost by default.
- Headers: Content-Security-Policy, X-Content-Type-Options, X-Frame-Options, Referrer-Policy.
- The data file is written to a temp file and then moved, so a crash cannot leave a half-written file.

## How to extend it

- **New category:** add the name to `CATEGORIES` in `ExpenseService`.
- **New field (for example "note"):** add it to `Expense`, `validate()` in the service, `Json`, the file format in `FileExpenseRepository`, and the form in `index.html` and `app.js`.
- **Real database:** write a class `JdbcExpenseRepository implements ExpenseRepository`. The service and web layers do not change.
- **Users and login:** add a `userId` to `Expense` and a login endpoint; add a session check at the top of `ApiServer.api()`.

## Known limitations

- One data file, no users or login; it is meant for one person on one computer.
- The whole file is rewritten on every change. That is fast for thousands of expenses but not for millions; use a database then.
- The `month` filter field needs a browser that supports `<input type="month">` (Chrome, Edge). In others, type `2026-10`.
- The project is not deployed online; GitHub Pages can only host static pages, so a Java backend needs a server host.

## Project structure

```
ExpenseTrackerPro/
  src/com/expensetracker/Main.java
  src/com/expensetracker/model/Expense.java
  src/com/expensetracker/repository/   ExpenseRepository, FileExpenseRepository, InMemoryExpenseRepository (+ test)
  src/com/expensetracker/service/      ExpenseService, Summary, ValidationException, NotFoundException (+ test)
  src/com/expensetracker/web/          ApiServer, Json (+ 2 tests)
  src/web/                             index.html, style.css, app.js
```
