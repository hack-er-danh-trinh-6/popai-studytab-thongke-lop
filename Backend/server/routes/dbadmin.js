import { Router } from "express";
import rateLimit from "express-rate-limit";
import bcrypt from "bcryptjs";
import { pool, query } from "../db.js";
import {
  describeDbError, escapeLike, isSensitiveColumn, MAX_BULK_DELETE, PASSWORD_COLUMN, parseListQuery,
  READ_ONLY_TABLES, stripSensitive, validateKey, validateValues
} from "../dbAdminDomain.js";

// Trình quản lý dữ liệu (chỉ admin hệ thống, đã qua requireAdmin ở router cha).
// Tên bảng/cột KHÔNG BAO GIỜ lấy trực tiếp từ client: luôn đối chiếu với catalog PostgreSQL rồi mới trích dẫn bằng qi().
const router = Router();

const writeLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 120,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Bạn thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại." }
});

const qi = (identifier) => `"${String(identifier).replaceAll('"', '""')}"`;

async function listTableNames() {
  const result = await query(
    `SELECT c.relname AS name
       FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relkind = 'r'
      ORDER BY c.relname`
  );
  return result.rows.map((row) => row.name);
}

/** Đọc cấu trúc bảng; trả null nếu bảng không tồn tại trong schema public. */
async function loadTable(name, db = { query }) {
  if (typeof name !== "string" || name.length > 100) return null;
  const columns = await db.query(
    `SELECT a.attname AS name, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull AS not_null,
            pg_get_expr(d.adbin, d.adrelid) AS default_expr,
            (a.attgenerated <> '') AS generated, (a.attidentity <> '') AS identity
       FROM pg_attribute a
       JOIN pg_class c ON c.oid = a.attrelid
       JOIN pg_namespace n ON n.oid = c.relnamespace
       LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
      WHERE n.nspname = 'public' AND c.relname = $1 AND c.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped
      ORDER BY a.attnum`,
    [name]
  );
  if (columns.rowCount === 0) return null;
  const keys = await db.query(
    `SELECT a.attname AS name
       FROM pg_index i
       JOIN pg_class c ON c.oid = i.indrelid
       JOIN pg_namespace n ON n.oid = c.relnamespace
       JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey::int2[])
      WHERE i.indisprimary AND n.nspname = 'public' AND c.relname = $1
      ORDER BY array_position(i.indkey::int2[], a.attnum)`,
    [name]
  );
  return {
    name,
    readOnly: READ_ONLY_TABLES.has(name),
    primaryKey: keys.rows.map((row) => row.name),
    columns: columns.rows.map((row) => ({
      name: row.name,
      type: row.type,
      notNull: row.not_null,
      defaultExpr: row.default_expr,
      generated: row.generated,
      identity: row.identity,
      sensitive: isSensitiveColumn(row.name),
      writable: !row.generated && (!isSensitiveColumn(row.name) || row.name === PASSWORD_COLUMN)
    }))
  };
}

async function requireTable(req, res) {
  const table = await loadTable(req.params.table);
  if (!table) {
    res.status(404).json({ message: "Không tìm thấy bảng." });
    return null;
  }
  return table;
}

function requireWritable(table, res) {
  if (table.readOnly) {
    res.status(403).json({ message: "Bảng này chỉ cho phép xem." });
    return false;
  }
  return true;
}

// Mọi cột đọc ra đều ép sang text để giữ nguyên giá trị (ngày giờ, số lớn, JSON) và tránh lệch múi giờ ở driver.
const selectList = (table, prefix = "t.") => table.columns
  .filter((column) => !column.sensitive)
  .map((column) => `${prefix}${qi(column.name)}::text AS ${qi(column.name)}`)
  .join(", ");

const keyWhere = (table, key, startIndex = 1) => {
  const values = [];
  const parts = table.primaryKey.map((name, index) => {
    const column = table.columns.find((item) => item.name === name);
    values.push(key[name]);
    return `t.${qi(name)} = $${startIndex + index}::${column.type}`;
  });
  return { sql: parts.join(" AND "), values };
};

async function bindValue(column, value) {
  if (value !== null && column.name === PASSWORD_COLUMN) {
    if (value.length < 8 || value.length > 72) {
      const error = new Error("Mật khẩu phải từ 8 đến 72 ký tự.");
      error.status = 400;
      throw error;
    }
    return bcrypt.hash(value, 12);
  }
  return value;
}

const audit = (db, tableName, action, key, before, after) => db.query(
  `INSERT INTO pop_admin_audit_log (table_name, action, row_key, before_data, after_data) VALUES ($1, $2, $3, $4, $5)`,
  [tableName, action, JSON.stringify(key), before ? JSON.stringify(stripSensitive(before)) : null, after ? JSON.stringify(stripSensitive(after)) : null]
);

const fail = (res, error, next) => {
  const known = describeDbError(error);
  if (known) return res.status(known.status).json({ message: known.message });
  if (error?.status === 400) return res.status(400).json({ message: error.message });
  return next(error);
};

router.get("/tables", async (_req, res, next) => {
  try {
    const names = await listTableNames();
    const counts = await Promise.all(names.map((name) => query(`SELECT COUNT(*)::int AS n FROM ${qi(name)}`)));
    return res.json(names.map((name, index) => ({ name, rowCount: counts[index].rows[0].n, readOnly: READ_ONLY_TABLES.has(name) })));
  } catch (error) {
    return next(error);
  }
});

router.get("/tables/:table", async (req, res, next) => {
  try {
    const table = await requireTable(req, res);
    if (!table) return;
    const referenced = await query(
      `SELECT c.conrelid::regclass::text AS table_name, c.confdeltype AS on_delete
         FROM pg_constraint c WHERE c.contype = 'f' AND c.confrelid = $1::regclass`,
      [`public.${qi(table.name)}`]
    );
    return res.json({
      ...table,
      referencedBy: referenced.rows.map((row) => ({ table: row.table_name.replaceAll('"', ""), cascade: row.on_delete === "c" }))
    });
  } catch (error) {
    return next(error);
  }
});

router.get("/tables/:table/rows", async (req, res, next) => {
  try {
    const table = await requireTable(req, res);
    if (!table) return;
    const visible = table.columns.filter((column) => !column.sensitive);
    const options = parseListQuery(req.query, visible.map((column) => column.name));
    const values = [];
    let where = "";
    if (options.search) {
      values.push(`%${escapeLike(options.search)}%`);
      where = `WHERE ${visible.map((column) => `t.${qi(column.name)}::text ILIKE $1`).join(" OR ")}`;
    }
    // Sắp xếp theo cột chọn (hoặc khóa chính), luôn thêm khóa chính để phân trang ổn định.
    // Dùng t."cột" để tham chiếu cột gốc, không phải bản ép text trong SELECT.
    const dirSql = options.dir === "asc" ? "ASC" : "DESC";
    const orderColumns = [...(options.sort ? [options.sort] : []), ...table.primaryKey.filter((name) => name !== options.sort)];
    const order = orderColumns.length
      ? `ORDER BY ${orderColumns.map((name, i) => `t.${qi(name)} ${i === 0 ? dirSql : "ASC"} NULLS LAST`).join(", ")}`
      : "";
    const [count, rows] = await Promise.all([
      query(`SELECT COUNT(*)::int AS n FROM ${qi(table.name)} t ${where}`, values),
      query(
        `SELECT ${selectList(table)} FROM ${qi(table.name)} t ${where} ${order} LIMIT ${options.pageSize} OFFSET ${options.offset}`,
        values
      )
    ]);
    return res.json({ total: count.rows[0].n, page: options.page, pageSize: options.pageSize, rows: rows.rows });
  } catch (error) {
    return fail(res, error, next);
  }
});

router.post("/tables/:table/rows", writeLimiter, async (req, res, next) => {
  const client = await pool.connect();
  try {
    const table = await requireTable(req, res);
    if (!table || !requireWritable(table, res)) return;
    const parsed = validateValues(req.body?.values, table.columns, { allowEmpty: true });
    if (parsed.error) return res.status(400).json({ message: parsed.error });

    const bound = await Promise.all(parsed.entries.map(async (entry) => bindValue(entry.column, entry.value)));
    const sql = parsed.entries.length === 0
      ? `INSERT INTO ${qi(table.name)} DEFAULT VALUES RETURNING ${selectList(table, "")}`
      : `INSERT INTO ${qi(table.name)} (${parsed.entries.map((e) => qi(e.column.name)).join(", ")})
         VALUES (${parsed.entries.map((e, i) => `$${i + 1}::${e.column.type}`).join(", ")})
         RETURNING ${selectList(table, "")}`;
    await client.query("BEGIN");
    const inserted = await client.query(sql, bound);
    const row = inserted.rows[0];
    await audit(client, table.name, "INSERT", Object.fromEntries(table.primaryKey.map((name) => [name, row[name] ?? null])), null, row);
    await client.query("COMMIT");
    return res.status(201).json({ row });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return fail(res, error, next);
  } finally {
    client.release();
  }
});

router.patch("/tables/:table/rows", writeLimiter, async (req, res, next) => {
  const client = await pool.connect();
  try {
    const table = await requireTable(req, res);
    if (!table || !requireWritable(table, res)) return;
    const key = validateKey(req.body?.key, table.primaryKey);
    if (key.error) return res.status(400).json({ message: key.error });
    const parsed = validateValues(req.body?.values, table.columns);
    if (parsed.error) return res.status(400).json({ message: parsed.error });
    const bound = await Promise.all(parsed.entries.map(async (entry) => bindValue(entry.column, entry.value)));

    const where = keyWhere(table, key.key, parsed.entries.length + 1);
    await client.query("BEGIN");
    const before = await client.query(
      `SELECT ${selectList(table)} FROM ${qi(table.name)} t WHERE ${keyWhere(table, key.key).sql} FOR UPDATE`,
      keyWhere(table, key.key).values
    );
    if (before.rowCount !== 1) {
      await client.query("ROLLBACK");
      return res.status(404).json({ message: "Dòng không còn tồn tại (có thể đã bị xóa)." });
    }
    const updated = await client.query(
      `UPDATE ${qi(table.name)} t SET ${parsed.entries.map((e, i) => `${qi(e.column.name)} = $${i + 1}::${e.column.type}`).join(", ")}
        WHERE ${where.sql} RETURNING ${selectList(table)}`,
      [...bound, ...where.values]
    );
    const row = updated.rows[0];
    await audit(client, table.name, "UPDATE", key.key, before.rows[0], row);
    await client.query("COMMIT");
    return res.json({ row });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return fail(res, error, next);
  } finally {
    client.release();
  }
});

// Xóa một hoặc nhiều dòng trong cùng một giao dịch: lỗi ở dòng nào thì hoàn tác tất cả.
router.post("/tables/:table/rows/delete", writeLimiter, async (req, res, next) => {
  const client = await pool.connect();
  try {
    const table = await requireTable(req, res);
    if (!table || !requireWritable(table, res)) return;
    const keys = req.body?.keys;
    if (!Array.isArray(keys) || keys.length === 0 || keys.length > MAX_BULK_DELETE) {
      return res.status(400).json({ message: `Hãy chọn từ 1 đến ${MAX_BULK_DELETE} dòng để xóa.` });
    }
    const checked = keys.map((key) => validateKey(key, table.primaryKey));
    const bad = checked.find((item) => item.error);
    if (bad) return res.status(400).json({ message: bad.error });

    await client.query("BEGIN");
    let deleted = 0;
    for (const { key } of checked) {
      const where = keyWhere(table, key);
      const removed = await client.query(
        `DELETE FROM ${qi(table.name)} t WHERE ${where.sql} RETURNING ${selectList(table)}`,
        where.values
      );
      if (removed.rowCount === 1) {
        deleted += 1;
        await audit(client, table.name, "DELETE", key, removed.rows[0], null);
      }
    }
    await client.query("COMMIT");
    return res.json({ deleted });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return fail(res, error, next);
  } finally {
    client.release();
  }
});

export default router;
