import test from "node:test";
import assert from "node:assert/strict";
import { resolveKeepAliveTarget, resolveKeepAliveInterval } from "./keepAlive.js";

test("keep-alive target", () => {
  assert.equal(resolveKeepAliveTarget({}), null);
  assert.equal(resolveKeepAliveTarget({ RENDER_EXTERNAL_URL: "https://a.onrender.com/" }), "https://a.onrender.com/healthz");
  assert.equal(resolveKeepAliveTarget({ KEEP_ALIVE_URL: "https://b.com", RENDER_EXTERNAL_URL: "https://a.onrender.com" }), "https://b.com/healthz");
  assert.equal(resolveKeepAliveTarget({ RENDER_EXTERNAL_URL: "https://a.onrender.com", KEEP_ALIVE: "off" }), null);
  assert.equal(resolveKeepAliveTarget({ RENDER_EXTERNAL_URL: "not-a-url" }), null);
});

test("keep-alive interval", () => {
  assert.equal(resolveKeepAliveInterval({}), 600000);
  assert.equal(resolveKeepAliveInterval({ KEEP_ALIVE_INTERVAL_MS: "5000" }), 60000);
  assert.equal(resolveKeepAliveInterval({ KEEP_ALIVE_INTERVAL_MS: "300000" }), 300000);
  assert.equal(resolveKeepAliveInterval({ KEEP_ALIVE_INTERVAL_MS: "abc" }), 600000);
});
