export const ADMINBOT_SCHOOL = "THPT Tây Đô";
export const ADMINBOT_CLASS = "11TN2";
export const ADMINBOT_TEACHER = "Phan Quốc Huy";
export const ADMINBOT_TEACHER_EMAIL = "teacher@adminbot.invalid";
export const ADMINBOT_INTERVAL_MS = 5 * 60 * 1000;

export const ADMINBOT_ROSTER = Object.freeze([
  "Vũ Huy", "Ngọc Ngân", "Anh Hào", "Trường Vi", "Danh Trình", "Nhã Đan", "Huỳnh Anh", "Thùy Linh",
  "Huỳnh Giao", "Lê Thịnh", "Lâm Thịnh", "Tấn Lộc", "Thái Tường", "Văn An", "Khánh Băng", "Ngọc Tuyền",
  "Gia Hân", "Tỉnh Ngọc", "Minh Thuận", "Cẩm Ly", "Ngọc Diệu", "Ngọc Thắm", "Duyên Anh", "Minh Huy",
  "Mỹ Kim", "Ngọc Trâm", "Thanh Lĩnh", "Văn Hiếu", "Thanh Quỳnh", "Cẩm Nhung", "Thủy Tiên", "Nhựt Trang",
  "Hồng Vân", "Nguyên Phú", "Trúc Vy", "Thế Anh", "Minh Hiếu", "Duy Khang"
]);

export function adminbotStudentEmail(index) {
  if (!Number.isInteger(index) || index < 0 || index >= ADMINBOT_ROSTER.length) {
    throw new RangeError("Student index is outside the Adminbot roster.");
  }
  return `student${String(index + 1).padStart(2, "0")}@adminbot.invalid`;
}
