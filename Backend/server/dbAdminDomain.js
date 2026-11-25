// Hàm thuần cho trình quản lý dữ liệu của trang sysadmin: kiểm tra tham số, quy tắc bảo vệ cột/bảng và dịch lỗi PostgreSQL.
import { escapeLike } from "./statsDomain.js";

export { escapeLike };

export const PAGE_SIZES = [25, 50, 100, 200];
export const MAX_BULK_DELETE = 200;

// Bảng chỉ xem: không cho thêm/sửa/xóa từ giao diện.
export const READ_ONLY_TABLES = new Set(["pop_schema_migrations", "pop_admin_audit_log"]);

// Cột mật khẩu: nhập mật khẩu thường, server băm bcrypt trước khi lưu. Không bao giờ trả giá trị ra ngoài.
export const PASSWORD_COLUMN = "password_hash";
// Mọi cột "bí mật" (hash mật khẩu/token): luôn bị ẩn khi đọc; chỉ PASSWORD_COLUMN được ghi.
export const isSensitiveColumn = (name) => /(^|_)(password|secret|token)(_|$)|hash$/i.test(name);
export const isWritableColumn = (name) => !isSensitiveColumn(name) || name === PASSWORD_COLUMN;

const TEXT_TYPES = new Set(["text", "character varying", "character", "name", "citext"]);
export const isTextType = (formatType) => TEXT_TYPES.has(String(formatType).replace(/\(.*\)/, "").trim());

export function parseListQuery(query, columnNames) {
  const page = Math.max(1, Number.parseInt(query.page, 10) || 1);
  const requested = Number.parseInt(query.pageSize, 10);
  const pageSize = PAGE_SIZES.includes(requested) ? requested : 50;
  const sort = columnNames.includes(query.sort) ? query.sort : null;
  const dir = String(query.dir).toLowerCase() === "asc" ? "asc" : "desc";
  const search = typeof query.q === "string" ? query.q.trim().slice(0, 100) : "";
  return { page, pageSize, offset: (page - 1) * pageSize, sort, dir, search };
}

/**
 * Chuẩn hóa giá trị người dùng nhập cho một cột. Trả về chuỗi hoặc null (để PostgreSQL tự ép kiểu bằng CAST).
 * Cột không phải chữ mà để trống được hiểu là NULL.
 */
export function normalizeInput(raw, formatType) {
  if (raw === null || raw === undefined) return null;
  if (typeof raw === "number" || typeof raw === "boolean") return String(raw);
  if (typeof raw !== "string") throw new TypeError("invalid_value");
  if (raw.length > 20000) throw new TypeError("too_long");
  if (raw.trim() === "" && !isTextType(formatType)) return null;
  return raw;
}

/** Kiểm tra object {cột: giá trị} gửi lên: chỉ cột có trong bảng, ghi được, không trùng/đặc biệt. */
export function validateValues(values, columns, { allowEmpty = false } = {}) {
  if (!values || typeof values !== "object" || Array.isArray(values)) return { error: "Dữ liệu gửi lên không hợp lệ." };
  const byName = new Map(columns.map((column) => [column.name, column]));
  const entries = [];
  for (const [name, raw] of Object.entries(values)) {
    const column = byName.get(name);
    if (!column) return { error: `Bảng không có cột "${name}".` };
    if (!isWritableColumn(name)) return { error: `Cột "${name}" được bảo vệ, không thể chỉnh sửa.` };
    if (column.generated) return { error: `Cột "${name}" là cột tự tính, không thể chỉnh sửa.` };
    try {
      entries.push({ column, value: normalizeInput(raw, column.type) });
    } catch (error) {
      return { error: error.message === "too_long" ? `Giá trị cột "${name}" quá dài.` : `Giá trị cột "${name}" không hợp lệ.` };
    }
  }
  if (!allowEmpty && entries.length === 0) return { error: "Chưa có giá trị nào để lưu." };
  return { entries };
}

/** Khóa chính gửi lên phải có đủ và chỉ gồm đúng các cột khóa chính. */
export function validateKey(key, primaryKey) {
  if (primaryKey.length === 0) return { error: "Bảng không có khóa chính nên không thể sửa/xóa từng dòng." };
  if (!key || typeof key !== "object" || Array.isArray(key)) return { error: "Khóa dòng không hợp lệ." };
  const names = Object.keys(key);
  if (names.length !== primaryKey.length || !primaryKey.every((name) => typeof key[name] === "string" && key[name] !== "" && key[name].length < 500)) {
    return { error: "Khóa dòng không hợp lệ." };
  }
  return { key };
}

/** Bỏ cột bí mật khỏi bản ghi trước khi ghi nhật ký hoặc trả về. */
export function stripSensitive(row) {
  return Object.fromEntries(Object.entries(row ?? {}).filter(([name]) => !isSensitiveColumn(name)));
}

/** Dịch lỗi PostgreSQL thành thông báo tiếng Việt dễ hiểu. Trả null nếu không phải lỗi dữ liệu do người dùng nhập. */
export function describeDbError(error) {
  const code = String(error?.code ?? "");
  const where = error?.column ? ` (cột "${error.column}")` : error?.constraint ? ` (ràng buộc "${error.constraint}")` : "";
  const detail = error?.detail ? ` ${error.detail}` : "";
  if (code === "23505") return { status: 409, message: `Giá trị bị trùng với dòng đã có${where}.${detail}`.trim() };
  if (code === "23503") {
    return { status: 409, message: `Vi phạm liên kết giữa các bảng${where}: dòng này đang được dùng ở bảng khác hoặc giá trị tham chiếu không tồn tại.${detail}`.trim() };
  }
  if (code === "23502") return { status: 400, message: `Cột bắt buộc không được để trống${where}.` };
  if (code === "23514") return { status: 400, message: `Giá trị không thỏa điều kiện kiểm tra${where}.` };
  if (code === "22P02" || code === "22007" || code === "22008" || code === "22003" || code === "22001" || code === "22023" || code === "22P05") {
    return { status: 400, message: `Giá trị không đúng định dạng hoặc vượt giới hạn của cột: ${error.message}` };
  }
  if (code === "57014") return { status: 408, message: "Truy vấn mất quá nhiều thời gian và đã bị hủy." };
  return null;
}
