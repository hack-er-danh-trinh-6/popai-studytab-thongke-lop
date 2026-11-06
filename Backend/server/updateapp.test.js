import assert from "node:assert/strict";
import test from "node:test";

import { appHubUrl, buildUpdateMessage, DEFAULT_APP_HUB_URL, VERSION_PATTERN } from "./updateappDomain.js";

test("thông báo cập nhật có/không số phiên bản", () => {
  assert.match(buildUpdateMessage("1.4.0").body, /POP AI 1\.4\.0 đã sẵn sàng/);
  assert.match(buildUpdateMessage("").body, /POP AI đã sẵn sàng/);
  assert.ok(buildUpdateMessage("").title.length <= 80);
  assert.ok(buildUpdateMessage("x".repeat(30)).body.length <= 500);
});

test("liên kết App Hub mặc định và chỉ chấp nhận https", () => {
  delete process.env.APP_HUB_URL;
  assert.equal(appHubUrl(), DEFAULT_APP_HUB_URL);
  assert.equal(DEFAULT_APP_HUB_URL, "https://apphub-pop.onrender.com");
  process.env.APP_HUB_URL = "http://evil.example";
  assert.equal(appHubUrl(), DEFAULT_APP_HUB_URL);
  process.env.APP_HUB_URL = "https://hub.example.com/pop";
  assert.equal(appHubUrl(), "https://hub.example.com/pop");
  delete process.env.APP_HUB_URL;
});

test("số phiên bản hợp lệ", () => {
  for (const ok of ["1.4.0", "2", "v1.0-beta", "1.0.0+12"]) assert.ok(VERSION_PATTERN.test(ok), ok);
  for (const bad of ["", "<b>", "1.0\n", "x".repeat(31), "-1"]) assert.ok(!VERSION_PATTERN.test(bad), bad);
});
