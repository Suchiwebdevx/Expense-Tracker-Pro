'use strict';

// The web page talks to the Java server through the REST API (fetch).
// Everything shown on screen is set with textContent, never innerHTML,
// so a title like <script>alert(1)</script> is shown as plain text and cannot run.

(function () {
  var form = document.getElementById('expense-form');
  var formTitle = document.getElementById('form-title');
  var submitBtn = document.getElementById('submit-btn');
  var cancelBtn = document.getElementById('cancel-btn');
  var banner = document.getElementById('banner');
  var list = document.getElementById('expense-list');
  var emptyMsg = document.getElementById('empty');
  var filterCategory = document.getElementById('filter-category');
  var filterMonth = document.getElementById('filter-month');
  var exportLink = document.getElementById('export-link');
  var fields = ['title', 'amount', 'category', 'date'];

  var editingId = null; // null = adding, a number = editing that expense
  var bannerTimer = null;

  // ---------- helpers ----------

  function el(id) { return document.getElementById(id); }

  function money(value) { return Number(value).toFixed(2); }

  function todayString() {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + m + '-' + day;
  }

  function showBanner(message, ok) {
    banner.textContent = message;
    banner.className = ok ? 'banner ok' : 'banner';
    banner.hidden = false;
    clearTimeout(bannerTimer);
    if (ok) {
      bannerTimer = setTimeout(function () { banner.hidden = true; }, 3000);
    }
  }

  function hideBanner() { banner.hidden = true; }

  function clearFieldErrors() {
    fields.forEach(function (f) {
      el('err-' + f).textContent = '';
      el(f).classList.remove('invalid');
    });
  }

  function showFieldErrors(errors) {
    var shown = false;
    fields.forEach(function (f) {
      if (errors && errors[f]) {
        el('err-' + f).textContent = errors[f];
        el(f).classList.add('invalid');
        if (!shown) { el(f).focus(); shown = true; }
      }
    });
  }

  // Calls the API. Returns the parsed JSON (or null for 204).
  // Throws an object { status, body } for error replies, or { network: true } if the server is not reachable.
  function api(path, options) {
    return fetch(path, options).then(function (res) {
      if (res.status === 204) { return null; }
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (!res.ok) { throw { status: res.status, body: body }; }
        return body;
      });
    }, function () {
      throw { network: true };
    });
  }

  function handleError(err) {
    if (err && err.network) {
      showBanner('Cannot reach the server. Is the Java program still running?', false);
    } else if (err && err.body && err.body.error) {
      showBanner(err.body.error, false);
    } else {
      showBanner('Something went wrong. Please try again.', false);
    }
  }

  function filterQuery() {
    var parts = [];
    if (filterCategory.value) { parts.push('category=' + encodeURIComponent(filterCategory.value)); }
    if (filterMonth.value) { parts.push('month=' + encodeURIComponent(filterMonth.value)); }
    return parts.length ? '?' + parts.join('&') : '';
  }

  // ---------- drawing ----------

  function renderList(expenses) {
    list.textContent = '';
    emptyMsg.hidden = expenses.length > 0;
    expenses.forEach(function (e) {
      var tr = document.createElement('tr');

      // data-label is used by the phone layout, where each cell shows its column name.
      function cell(label, text, className) {
        var td = document.createElement('td');
        td.textContent = text;
        td.setAttribute('data-label', label);
        if (className) { td.className = className; }
        tr.appendChild(td);
        return td;
      }

      cell('Title', e.title);
      cell('Amount', 'Rs. ' + money(e.amount), 'num');
      cell('Category', e.category);
      cell('Date', e.date, 'num');

      var actions = document.createElement('td');
      actions.className = 'row-actions';
      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'secondary small';
      editBtn.textContent = 'Edit';
      editBtn.setAttribute('aria-label', 'Edit ' + e.title);
      editBtn.addEventListener('click', function () { startEdit(e); });
      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'danger small';
      delBtn.textContent = 'Delete';
      delBtn.setAttribute('aria-label', 'Delete ' + e.title);
      delBtn.addEventListener('click', function () { removeExpense(e); });
      actions.appendChild(editBtn);
      actions.appendChild(delBtn);
      tr.appendChild(actions);

      list.appendChild(tr);
    });
  }

  function renderSummary(s) {
    el('total').textContent = money(s.total);
    el('count').textContent = s.count;
    el('average').textContent = money(s.average);
    el('highest').textContent = s.highest ? s.highest.title + ' (Rs. ' + money(s.highest.amount) + ')' : '-';

    var bars = el('category-bars');
    bars.textContent = '';
    var total = Number(s.total);
    s.byCategory.forEach(function (c) {
      var li = document.createElement('li');
      var name = document.createElement('span');
      name.textContent = c.category;
      var track = document.createElement('div');
      track.className = 'bar-track';
      var fill = document.createElement('div');
      fill.className = 'bar-fill';
      fill.style.width = (total > 0 ? Math.round(Number(c.amount) / total * 100) : 0) + '%';
      track.appendChild(fill);
      var amt = document.createElement('span');
      amt.className = 'amt';
      amt.textContent = 'Rs. ' + money(c.amount);
      li.appendChild(name);
      li.appendChild(track);
      li.appendChild(amt);
      bars.appendChild(li);
    });
  }

  function refresh() {
    var q = filterQuery();
    exportLink.setAttribute('href', '/api/export.csv' + q);
    var summaryQuery = filterMonth.value ? '?month=' + encodeURIComponent(filterMonth.value) : '';
    return Promise.all([api('/api/expenses' + q), api('/api/summary' + summaryQuery)])
      .then(function (results) {
        renderList(results[0]);
        renderSummary(results[1]);
      })
      .catch(handleError);
  }

  // ---------- add, edit, delete ----------

  function resetForm() {
    editingId = null;
    form.reset();
    el('date').value = todayString();
    formTitle.textContent = 'Add an expense';
    submitBtn.textContent = 'Add Expense';
    cancelBtn.hidden = true;
    clearFieldErrors();
  }

  function startEdit(expense) {
    editingId = expense.id;
    el('title').value = expense.title;
    el('amount').value = money(expense.amount);
    el('category').value = expense.category;
    el('date').value = expense.date;
    formTitle.textContent = 'Edit expense';
    submitBtn.textContent = 'Save Changes';
    cancelBtn.hidden = false;
    clearFieldErrors();
    hideBanner();
    el('title').focus();
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function removeExpense(expense) {
    if (!window.confirm('Delete "' + expense.title + '"?')) { return; }
    api('/api/expenses/' + expense.id, { method: 'DELETE' })
      .then(function () {
        if (editingId === expense.id) { resetForm(); }
        showBanner('Expense deleted', true);
        return refresh();
      })
      .catch(handleError);
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    clearFieldErrors();
    hideBanner();

    var payload = {
      title: el('title').value,
      amount: el('amount').value,
      category: el('category').value,
      date: el('date').value
    };
    var isEdit = editingId !== null;
    var path = isEdit ? '/api/expenses/' + editingId : '/api/expenses';

    submitBtn.disabled = true;
    api(path, {
      method: isEdit ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(function () {
      resetForm();
      showBanner(isEdit ? 'Changes saved' : 'Expense added', true);
      return refresh();
    }).catch(function (err) {
      if (err && err.status === 400 && err.body && err.body.fields) {
        showFieldErrors(err.body.fields);
      } else {
        handleError(err);
      }
    }).then(function () {
      submitBtn.disabled = false;
    });
  });

  cancelBtn.addEventListener('click', resetForm);

  // Remove an error message as soon as the user starts fixing the field.
  fields.forEach(function (f) {
    el(f).addEventListener('input', function () {
      el('err-' + f).textContent = '';
      el(f).classList.remove('invalid');
    });
  });

  filterCategory.addEventListener('change', refresh);
  filterMonth.addEventListener('change', refresh);
  el('clear-filters').addEventListener('click', function () {
    filterCategory.value = '';
    filterMonth.value = '';
    refresh();
  });

  // ---------- start ----------

  function fillSelect(select, categories) {
    categories.forEach(function (name) {
      var option = document.createElement('option');
      option.value = name;
      option.textContent = name;
      select.appendChild(option);
    });
  }

  el('date').max = todayString();
  api('/api/categories')
    .then(function (categories) {
      fillSelect(el('category'), categories);
      fillSelect(filterCategory, categories);
      resetForm();
      return refresh();
    })
    .catch(handleError);
})();
