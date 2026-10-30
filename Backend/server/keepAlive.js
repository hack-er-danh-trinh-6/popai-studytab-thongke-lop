// Chống Render free tier spin down: tự gọi /healthz định kỳ.
// Render tự đặt RENDER_EXTERNAL_URL (vd https://pop-ai.onrender.com). Có thể ghi đè bằng KEEP_ALIVE_URL.
// Tắt bằng KEEP_ALIVE=off. Chu kỳ mặc định 10 phút (Render ngủ sau 15 phút không có request).
// Lưu ý: chỉ giữ cho server đang chạy không ngủ; nếu đã ngủ thì cần ping ngoài (UptimeRobot) để đánh thức.
const DEFAULT_INTERVAL_MS = 10 * 60 * 1000;
const MIN_INTERVAL_MS = 60 * 1000;
const TIMEOUT_MS = 15 * 1000;

export function resolveKeepAliveTarget(env = process.env) {
  if (env.KEEP_ALIVE === "off") return null;
  const base = (env.KEEP_ALIVE_URL || env.RENDER_EXTERNAL_URL || "").trim();
  if (!/^https?:\/\//i.test(base)) return null;
  return base.replace(/\/+$/, "") + "/healthz";
}

export function resolveKeepAliveInterval(env = process.env) {
  const value = Number(env.KEEP_ALIVE_INTERVAL_MS);
  if (!Number.isFinite(value) || value <= 0) return DEFAULT_INTERVAL_MS;
  return Math.max(value, MIN_INTERVAL_MS);
}

export function startKeepAlive({ env = process.env, fetchImpl = globalThis.fetch } = {}) {
  const target = resolveKeepAliveTarget(env);
  if (!target) {
    console.log("Keep-alive disabled (no RENDER_EXTERNAL_URL/KEEP_ALIVE_URL or KEEP_ALIVE=off)");
    return () => {};
  }
  const interval = resolveKeepAliveInterval(env);
  const ping = async () => {
    try {
      const res = await fetchImpl(target, { signal: AbortSignal.timeout(TIMEOUT_MS) });
      console.log("keep_alive_ping", { status: res.status });
    } catch (error) {
      console.error("keep_alive_failed", { code: error?.name ?? "network_error" });
    }
  };
  const timer = setInterval(ping, interval);
  timer.unref();
  console.log(`Keep-alive started: ${target} every ${Math.round(interval / 1000)}s`);
  return () => clearInterval(timer);
}
