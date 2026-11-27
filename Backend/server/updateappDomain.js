// Logic thuần cho thông báo cập nhật app (tách khỏi route để kiểm thử không cần Express/DB).
export const DEFAULT_APP_HUB_URL = "https://apphub-pop.onrender.com";
// Liên kết do server quyết định, không nhận từ client để trang này không thể dùng gửi link tuỳ ý tới học sinh.
export const appHubUrl = () => {
  const configured = (process.env.APP_HUB_URL ?? "").trim();
  return /^https:\/\/[^\s]+$/.test(configured) && configured.length <= 300 ? configured : DEFAULT_APP_HUB_URL;
};
export const VERSION_PATTERN = /^[0-9A-Za-z][0-9A-Za-z.\-_+ ]{0,29}$/;

export function buildUpdateMessage(version) {
  const label = version ? ` ${version}` : "";
  return {
    title: "Có bản cập nhật POP AI mới",
    body: `POP AI${label} đã sẵn sàng. Nhấn vào đây để mở App Hub và cập nhật phiên bản mới nhất.`
  };
}

export function buildUpdateMessageWithLink(version, linkUrl) {
  const label = version ? ` ${version}` : "";
  return {
    title: "Có bản cập nhật POP AI mới",
    body: `POP AI${label} đã sẵn sàng. Nhấn vào đây để tải phiên bản mới nhất.`,
    linkUrl
  };
}

export function validateVersion(version) {
  if (typeof version !== "string" || version.trim().length === 0) {
    return { ok: false, message: "Vui lòng nhập số phiên bản." };
  }
  const v = version.trim();
  if (!VERSION_PATTERN.test(v)) {
    return { ok: false, message: "Phiên bản chỉ gồm chữ, số, dấu chấm, gạch và tối đa 30 ký tự (ví dụ 1.4.0)." };
  }
  return { ok: true, version: v };
}

export function validateVersionCode(code) {
  if (code === undefined || code === null || code === "") {
    return { ok: true, versionCode: null };
  }
  const n = Number(code);
  if (!Number.isInteger(n) || n < 1 || n > 2000000000) {
    return { ok: false, message: "Mã phiên bản phải là số nguyên dương (ví dụ 2)." };
  }
  return { ok: true, versionCode: n };
}

export function buildAppVersionRow(version, versionCode, fileName, fileUrl, fileSize, uploadedBy) {
  return {
    version,
    versionCode,
    apkFileName: fileName,
    apkUrl: fileUrl,
    apkSize: fileSize,
    isActive: true,
    uploadedBy
  };
}
