"use strict";

// Trình quản lý dữ liệu: xem, tìm, thêm, sửa, xóa dòng của mọi bảng. Dùng h()/api()/toast() từ app.js.
const DB_MAX_CELL = 90;

function dbState() {
  if (!state.db) {
    state.db = {
      tables: null, tableFilter: "", current: null, schema: null,
      rows: [], total: 0, page: 1, pageSize: 50, sort: null, dir: "desc", search: "",
      selected: new Map(), loading: false, error: null, modal: null
    };
  }
  return state.db;
}

let dbRoot = null;
let dbSearchTimer = null;
let dbLoadSeq = 0;

const dbKeyOf = (schema, row) => Object.fromEntries(schema.primaryKey.map((name) => [name, row[name]]));
const dbKeyString = (schema, row) => JSON.stringify(schema.primaryKey.map((name) => row[name]));
const dbHasKey = (schema) => schema && schema.primaryKey.length > 0;
const dbCanWrite = (schema) => schema && !schema.readOnly;
const dbIsBool = (type) => type === "boolean";
const dbIsJson = (type) => type === "json" || type === "jsonb";
const dbIsNumber = (type) => /^(smallint|integer|bigint|numeric|real|double precision)/.test(type);
const dbIsLongText = (column, value) => dbIsJson(column.type) || (column.type === "text" && (value ?? "").length > 80);

async function loadDbTables(selectName) {
  const db = dbState();
  try {
    db.tables = await api("/db/tables");
    const keep = selectName ?? db.current;
    if (keep && db.tables.some((table) => table.name === keep)) {
      if (db.current !== keep || !db.schema) await selectDbTable(keep);
    }
    paintDb();
  } catch (error) {
    if (handleFatal(error)) return;
    db.error = error.message;
    paintDb();
  }
}

async function selectDbTable(name) {
  const db = dbState();
  db.current = name; db.schema = null; db.rows = []; db.total = 0; db.page = 1;
  db.sort = null; db.dir = "desc"; db.search = ""; db.selected = new Map(); db.error = null;
  paintDb();
  try {
    db.schema = await api(`/db/tables/${encodeURIComponent(name)}`);
  } catch (error) {
    if (handleFatal(error)) return;
    db.error = error.message; paintDb(); return;
  }
  await loadDbRows();
}

async function loadDbRows() {
  const db = dbState();
  if (!db.current) return;
  const seq = ++dbLoadSeq;
  db.loading = true; paintDb();
  const params = new URLSearchParams({ page: db.page, pageSize: db.pageSize, dir: db.dir });
  if (db.sort) params.set("sort", db.sort);
  if (db.search) params.set("q", db.search);
  try {
    const data = await api(`/db/tables/${encodeURIComponent(db.current)}/rows?${params}`);
    if (seq !== dbLoadSeq) return;
    db.rows = data.rows; db.total = data.total; db.error = null;
    const last = Math.max(1, Math.ceil(db.total / db.pageSize));
    if (db.page > last) { db.page = last; db.loading = false; return loadDbRows(); }
  } catch (error) {
    if (seq !== dbLoadSeq) return;
    if (handleFatal(error)) return;
    db.error = error.message;
  }
  db.loading = false;
  paintDb();
}

// ---------- Hiển thị ----------
function renderDbView() {
  dbRoot = h("div", { class: "layout" });
  const db = dbState();
  if (!db.tables) loadDbTables();
  paintDb();
  return dbRoot;
}

function paintDb() {
  if (!dbRoot || state.view !== "db") return;
  const db = dbState();
  const focused = document.activeElement;
  const keepSearch = focused && focused.id === "db-search";
  const caret = keepSearch ? focused.selectionStart : 0;
  dbRoot.replaceChildren(
    h("div", { class: "side" }, dbTableList()),
    h("div", { id: "db-main" }, dbMain()),
    db.modal ? (db.modal.node ??= dbModalNode()) : null
  );
  if (keepSearch) {
    const input = document.getElementById("db-search");
    if (input) { input.focus(); input.setSelectionRange(caret, caret); }
  }
}

function dbTableList() {
  const db = dbState();
  const filter = db.tableFilter.trim().toLowerCase();
  const tables = (db.tables ?? []).filter((table) => !filter || table.name.toLowerCase().includes(filter));
  return h("section", { class: "card" },
    h("h2", { text: "Bảng dữ liệu" }),
    h("p", { class: "muted small", text: "Chọn một bảng để xem, thêm, sửa hoặc xóa dòng. Mọi thay đổi đều được ghi vào nhật ký." }),
    h("input", {
      type: "text", placeholder: "Lọc tên bảng…", "aria-label": "Lọc tên bảng", value: db.tableFilter,
      oninput: (event) => { db.tableFilter = event.target.value; const pos = event.target.selectionStart; paintDb(); const el = dbRoot.querySelector(".db-filter"); if (el) { el.focus(); el.setSelectionRange(pos, pos); } },
      class: "db-filter"
    }),
    h("div", { class: "class-list" },
      !db.tables ? h("p", { class: "empty small", text: "Đang tải…" }) : null,
      db.tables && tables.length === 0 ? h("p", { class: "empty small", text: "Không có bảng phù hợp." }) : null,
      tables.map((table) => h("button", {
        type: "button", class: `class-btn${db.current === table.name ? " active" : ""}`,
        onclick: () => { if (db.current !== table.name) selectDbTable(table.name); }
      },
      h("span", { class: "count", text: String(table.rowCount) }),
      table.name,
      table.readOnly ? h("span", { class: "sub", text: "Chỉ xem" }) : null))));
}

function dbMain() {
  const db = dbState();
  if (!db.current) {
    return h("section", { class: "card" }, h("p", { class: "empty", text: db.error ?? "Chọn một bảng ở cột bên trái để bắt đầu." }));
  }
  const schema = db.schema;
  const canWrite = dbCanWrite(schema);
  const hasKey = dbHasKey(schema);
  const selectedCount = db.selected.size;

  const search = h("input", {
    type: "text", id: "db-search", placeholder: "Tìm trong mọi cột…", "aria-label": "Tìm kiếm", value: db.search,
    oninput: (event) => {
      db.search = event.target.value.trim();
      clearTimeout(dbSearchTimer);
      dbSearchTimer = setTimeout(() => { db.page = 1; loadDbRows(); }, 300);
    }
  });
  const sizes = h("select", {
    "aria-label": "Số dòng mỗi trang",
    onchange: (event) => { db.pageSize = Number(event.target.value); db.page = 1; loadDbRows(); }
  }, [25, 50, 100, 200].map((n) => h("option", { value: n, selected: n === db.pageSize ? true : undefined, text: `${n} dòng/trang` })));
  sizes.value = String(db.pageSize);

  return h("section", { class: "card" },
    h("div", { class: "head" },
      h("div", {},
        h("h2", { text: db.current }),
        h("p", { class: "muted small", text: schema
          ? `${db.total} dòng · ${schema.columns.length} cột · khóa chính: ${schema.primaryKey.join(", ") || "không có"}`
          : "Đang tải cấu trúc…" })),
      h("div", {},
        schema?.readOnly ? h("span", { class: "pill warn", text: "Chỉ xem" }) : null,
        schema && !schema.readOnly && !hasKey ? h("span", { class: "pill warn", text: "Không có khóa chính: chỉ thêm được" }) : null)),
    h("div", { class: "db-toolbar" },
      h("div", { class: "grow" }, search),
      sizes,
      h("button", { type: "button", class: "ghost", text: "Làm mới", onclick: () => { loadDbTables(); loadDbRows(); } }),
      canWrite ? h("button", { type: "button", text: "+ Thêm dòng", disabled: !schema, onclick: () => openDbModal({ mode: "insert" }) }) : null,
      canWrite && hasKey ? h("button", {
        type: "button", class: "danger", text: `Xóa đã chọn (${selectedCount})`, disabled: selectedCount === 0,
        onclick: () => openDbModal({ mode: "delete", rows: [...db.selected.values()] })
      }) : null),
    db.error ? h("div", { class: "error", text: db.error }) : null,
    schema ? dbTable(schema, canWrite && hasKey) : h("p", { class: "empty small", text: "Đang tải…" }),
    schema ? dbPager() : null);
}

function dbCell(column, value) {
  if (value === null || value === undefined) return h("span", { class: "db-null", text: "NULL" });
  const text = String(value);
  const short = text.length > DB_MAX_CELL ? `${text.slice(0, DB_MAX_CELL - 1)}…` : text;
  return h("span", { class: "db-val", title: text.length > DB_MAX_CELL ? text.slice(0, 1000) : undefined, text: short });
}

function dbTable(schema, selectable) {
  const db = dbState();
  const visible = schema.columns.filter((column) => !column.sensitive);
  const allKeys = db.rows.map((row) => dbKeyString(schema, row));
  const allSelected = selectable && db.rows.length > 0 && allKeys.every((key) => db.selected.has(key));
  const sortHeader = (column) => {
    const active = db.sort === column.name;
    return h("th", { "aria-sort": active ? (db.dir === "asc" ? "ascending" : "descending") : "none" },
      h("button", {
        type: "button", class: "th-sort", title: column.type,
        onclick: () => {
          if (db.sort === column.name) db.dir = db.dir === "asc" ? "desc" : "asc";
          else { db.sort = column.name; db.dir = "asc"; }
          db.page = 1; loadDbRows();
        }
      }, column.name, active ? (db.dir === "asc" ? " ▲" : " ▼") : "",
      schema.primaryKey.includes(column.name) ? h("span", { class: "db-pk", text: " 🔑" }) : null));
  };
  const writable = dbCanWrite(schema) && dbHasKey(schema);
  const head = h("tr", {},
    selectable ? h("th", { class: "db-check" }, h("input", {
      type: "checkbox", "aria-label": "Chọn tất cả dòng trong trang", checked: allSelected,
      onchange: (event) => {
        for (const row of db.rows) {
          const key = dbKeyString(schema, row);
          if (event.target.checked) db.selected.set(key, row); else db.selected.delete(key);
        }
        paintDb();
      }
    })) : null,
    visible.map(sortHeader),
    h("th", { text: "Thao tác" }));

  const body = db.rows.map((row) => {
    const key = dbHasKey(schema) ? dbKeyString(schema, row) : null;
    return h("tr", { class: key && db.selected.has(key) ? "db-selected" : "" },
      selectable ? h("td", { class: "db-check" }, h("input", {
        type: "checkbox", "aria-label": "Chọn dòng", checked: db.selected.has(key),
        onchange: (event) => { if (event.target.checked) db.selected.set(key, row); else db.selected.delete(key); paintDb(); }
      })) : null,
      visible.map((column) => h("td", { class: dbIsNumber(column.type) ? "num" : "" }, dbCell(column, row[column.name]))),
      h("td", { class: "db-actions" },
        h("button", { type: "button", class: "ghost small-btn", text: writable ? "Sửa" : "Xem", onclick: () => openDbModal({ mode: writable ? "edit" : "view", row }) }),
        dbCanWrite(schema) ? h("button", { type: "button", class: "ghost small-btn", text: "Nhân bản", onclick: () => openDbModal({ mode: "insert", row }) }) : null,
        writable ? h("button", { type: "button", class: "danger small-btn", text: "Xóa", onclick: () => openDbModal({ mode: "delete", rows: [row] }) }) : null));
  });
  return h("div", { class: `table-wrap${db.loading ? " is-loading" : ""}` },
    h("table", { class: "data" }, h("thead", {}, head),
      h("tbody", {}, body.length ? body : h("tr", {}, h("td", { colspan: visible.length + 2, class: "empty", text: db.loading ? "Đang tải…" : "Không có dòng nào." })))));
}

function dbPager() {
  const db = dbState();
  const pages = Math.max(1, Math.ceil(db.total / db.pageSize));
  const from = db.total === 0 ? 0 : (db.page - 1) * db.pageSize + 1;
  const to = Math.min(db.total, db.page * db.pageSize);
  return h("div", { class: "pager" },
    h("span", { class: "muted small", text: `${from}–${to} / ${db.total} dòng · trang ${db.page}/${pages}` }),
    h("div", { class: "actions" },
      h("button", { type: "button", class: "ghost", text: "‹ Trước", disabled: db.page <= 1 || db.loading, onclick: () => { db.page -= 1; loadDbRows(); } }),
      h("button", { type: "button", class: "ghost", text: "Sau ›", disabled: db.page >= pages || db.loading, onclick: () => { db.page += 1; loadDbRows(); } })));
}

// ---------- Hộp thoại thêm / sửa / xóa ----------
function openDbModal(modal) {
  const db = dbState();
  db.modal = { ...modal, busy: false, error: null, fields: [], node: null };
  paintDb();
  const first = dbRoot.querySelector(".modal input:not([disabled]):not([type=checkbox]), .modal textarea:not([disabled])");
  if (first) first.focus();
}

document.addEventListener("keydown", (event) => {
  const modal = state.db?.modal;
  if (event.key === "Escape" && modal && !modal.busy) closeDbModal();
});

function closeDbModal() {
  dbState().modal = null;
  paintDb();
}

function dbField(schema, column, modal) {
  const source = modal.row ? modal.row[column.name] : undefined;
  const isEdit = modal.mode === "edit";
  const isView = modal.mode === "view";
  const isInsert = modal.mode === "insert";
  const isKey = schema.primaryKey.includes(column.name);
  const isPassword = column.sensitive; // chỉ cột password_hash tới được đây
  // Nhân bản: bỏ giá trị khóa chính để DB tự sinh / người dùng nhập khóa mới.
  const autoUuid = isInsert && modal.row && isKey && column.type === "uuid" && !column.defaultExpr;
  const initial = autoUuid ? crypto.randomUUID() : isInsert && modal.row && isKey ? undefined : source;
  const hasDefault = Boolean(column.defaultExpr) || column.identity;
  const disabled = isView || column.generated;

  let input;
  const common = { id: `f-${column.name}`, disabled };
  if (dbIsBool(column.type)) {
    input = h("select", common, h("option", { value: "", text: "—" }), h("option", { value: "true", text: "true" }), h("option", { value: "false", text: "false" }));
    input.value = initial ?? "";
  } else if (isPassword) {
    input = h("input", { ...common, type: "password", autocomplete: "new-password", placeholder: isEdit ? "Để trống = giữ nguyên mật khẩu" : "Nhập mật khẩu (8–72 ký tự)" });
  } else if (dbIsLongText(column, initial)) {
    input = h("textarea", { ...common, rows: 5, spellcheck: "false" });
    input.value = initial ?? "";
  } else {
    input = h("input", { ...common, type: "text", autocomplete: "off", spellcheck: "false", placeholder: dbPlaceholder(column) });
    input.value = initial ?? "";
  }

  const nullBox = !isPassword && !column.notNull && !disabled
    ? h("input", { type: "checkbox", id: `n-${column.name}`, checked: initial === null || (initial === undefined && !hasDefault && isInsert) })
    : null;
  const defBox = isInsert && hasDefault && !isPassword
    ? h("input", { type: "checkbox", id: `d-${column.name}`, checked: initial === undefined })
    : null;

  const sync = () => {
    const useDefault = defBox?.checked;
    const useNull = nullBox?.checked && !useDefault;
    input.disabled = disabled || Boolean(useDefault) || Boolean(useNull);
    if (nullBox) nullBox.disabled = Boolean(useDefault);
  };
  nullBox?.addEventListener("change", () => { sync(); if (!nullBox.checked) input.focus(); });
  defBox?.addEventListener("change", () => { sync(); if (!defBox.checked && !(nullBox?.checked)) input.focus(); });
  input.addEventListener("input", () => { if (nullBox?.checked) { nullBox.checked = false; sync(); } });
  sync();

  const uuidButton = column.type === "uuid" && isInsert && !hasDefault
    ? h("button", { type: "button", class: "ghost small-btn", text: "Tạo UUID", onclick: () => { input.value = crypto.randomUUID(); if (nullBox) nullBox.checked = false; sync(); } })
    : null;

  const original = initial === undefined ? undefined : initial;
  const read = () => {
    if (defBox?.checked) return { skip: true };
    if (nullBox?.checked) return { value: null };
    return { value: input.value };
  };
  const node = h("div", { class: "db-field" },
    h("label", { for: `f-${column.name}` },
      isPassword ? `${column.name} (mật khẩu, sẽ được mã hóa)` : column.name, " ",
      h("span", { class: "db-type", text: column.type }),
      isKey ? h("span", { class: "pill", text: "khóa chính" }) : null,
      column.notNull && !hasDefault ? h("span", { class: "pill warn", text: "bắt buộc" }) : null,
      column.generated ? h("span", { class: "pill", text: "tự tính" }) : null),
    h("div", { class: "db-input-row" }, input, uuidButton),
    h("div", { class: "db-flags" },
      defBox ? h("label", { class: "check small", for: `d-${column.name}` }, defBox, `Mặc định (${column.defaultExpr ?? "tự tăng"})`) : null,
      nullBox ? h("label", { class: "check small", for: `n-${column.name}` }, nullBox, "NULL") : null));
  return { column, node, read, original, isPassword };
}

function dbPlaceholder(column) {
  if (column.type.startsWith("timestamp")) return "YYYY-MM-DD HH:MM:SS+07";
  if (column.type === "date") return "YYYY-MM-DD";
  if (column.type === "uuid") return "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx";
  return "";
}

function dbModalNode() {
  const db = dbState();
  const modal = db.modal;
  const schema = db.schema;
  const backdrop = h("div", { class: "modal-backdrop", onmousedown: (event) => { if (event.target === event.currentTarget && !modal.busy) closeDbModal(); } });
  const error = h("div", { class: "error", hidden: !modal.error, text: modal.error ?? "" });
  const onKey = (event) => { if (event.key === "Escape" && !modal.busy) closeDbModal(); };

  if (modal.mode === "delete") {
    const cascade = (schema.referencedBy ?? []).filter((ref) => ref.cascade).map((ref) => ref.table);
    const restrict = (schema.referencedBy ?? []).filter((ref) => !ref.cascade).map((ref) => ref.table);
    const confirm = h("input", { type: "checkbox", id: "confirm-del", onchange: (event) => { submit.disabled = !event.target.checked; } });
    const submit = h("button", { type: "button", class: "danger-solid", text: `Xóa ${modal.rows.length} dòng`, disabled: true,
      onclick: async () => {
        modal.busy = true; submit.disabled = true; error.hidden = true;
        try {
          const result = await api(`/db/tables/${encodeURIComponent(db.current)}/rows/delete`, {
            method: "POST", body: { keys: modal.rows.map((row) => dbKeyOf(schema, row)) }
          });
          toast(`Đã xóa ${result.deleted} dòng.`);
          for (const row of modal.rows) db.selected.delete(dbKeyString(schema, row));
          db.modal = null;
          await loadDbRows(); loadDbTables();
        } catch (e) {
          if (handleFatal(e)) return;
          modal.busy = false; modal.error = e.message; error.textContent = e.message; error.hidden = false; submit.disabled = !confirm.checked;
        }
      } });
    const preview = modal.rows.slice(0, 5).map((row) => h("li", { class: "small", text: schema.primaryKey.map((name) => `${name} = ${row[name]}`).join(", ") }));
    backdrop.append(h("div", { class: "modal card", role: "dialog", "aria-modal": "true", "aria-label": "Xác nhận xóa", onkeydown: onKey },
      h("h2", { text: `Xóa ${modal.rows.length} dòng khỏi ${db.current}?` }),
      h("ul", { class: "plain" }, preview, modal.rows.length > 5 ? h("li", { class: "small muted", text: `… và ${modal.rows.length - 5} dòng khác` }) : null),
      cascade.length ? h("div", { class: "hint", text: `Dữ liệu liên quan ở các bảng sau (nếu có) sẽ bị xóa theo: ${[...new Set(cascade)].join(", ")}.` }) : null,
      restrict.length ? h("div", { class: "hint", text: `Nếu dòng đang được các bảng ${[...new Set(restrict)].join(", ")} tham chiếu, thao tác sẽ bị từ chối.` }) : null,
      h("label", { class: "check", for: "confirm-del" }, confirm, "Tôi hiểu thao tác này không thể hoàn tác."),
      error,
      h("div", { class: "actions" }, submit, h("button", { type: "button", class: "ghost", text: "Hủy", onclick: closeDbModal }))));
    return backdrop;
  }

  const fields = schema.columns.filter((column) => !column.sensitive || column.writable).map((column) => dbField(schema, column, modal));
  modal.fields = fields;
  const isEdit = modal.mode === "edit";
  const isView = modal.mode === "view";
  const title = isView ? `Xem dòng · ${db.current}` : isEdit ? `Sửa dòng · ${db.current}` : `Thêm dòng · ${db.current}`;
  const submit = h("button", { type: "submit", text: isEdit ? "Lưu thay đổi" : "Thêm dòng" });
  const form = h("form", {
    onsubmit: async (event) => {
      event.preventDefault();
      const values = {};
      for (const field of fields) {
        if (field.column.generated) continue;
        const result = field.read();
        if (result.skip) continue;
        if (field.isPassword) {
          if (result.value === "" || result.value === null) continue;
          values[field.column.name] = result.value; continue;
        }
        if (isEdit) {
          const same = (field.original ?? null) === (result.value ?? null) || (field.original === null && result.value === "" && !/^(text|character)/.test(field.column.type));
          if (same) continue;
        }
        values[field.column.name] = result.value;
      }
      if (isEdit && Object.keys(values).length === 0) { toast("Chưa có thay đổi nào để lưu."); return; }
      modal.busy = true; submit.disabled = true; error.hidden = true;
      try {
        const path = `/db/tables/${encodeURIComponent(db.current)}/rows`;
        if (isEdit) await api(path, { method: "PATCH", body: { key: dbKeyOf(schema, modal.row), values } });
        else await api(path, { method: "POST", body: { values } });
        toast(isEdit ? "Đã lưu thay đổi." : "Đã thêm dòng mới.");
        db.modal = null;
        await loadDbRows(); loadDbTables();
      } catch (e) {
        if (handleFatal(e)) return;
        modal.busy = false; modal.error = e.message; error.textContent = e.message; error.hidden = false; submit.disabled = false;
        error.scrollIntoView({ block: "nearest" });
      }
    }
  },
  h("div", { class: "db-fields" }, fields.map((field) => field.node)),
  error,
  h("div", { class: "actions" }, isView ? null : submit, h("button", { type: "button", class: "ghost", text: isView ? "Đóng" : "Hủy", onclick: closeDbModal })));
  backdrop.append(h("div", { class: "modal card", role: "dialog", "aria-modal": "true", "aria-label": title, onkeydown: onKey },
    h("h2", { text: title }), form));
  return backdrop;
}
