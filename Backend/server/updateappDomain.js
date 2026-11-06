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
