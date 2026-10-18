const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const root = path.resolve(__dirname, "..");
test("application JavaScript parses", () => {
  const source = fs.readFileSync(path.join(root, "src/app.js"), "utf8");
  assert.doesNotThrow(() => new vm.Script(source));
});
test("web app has local assets and no runtime CDN dependencies", () => {
  const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
  assert.match(html, /src\/app\.js/);
  assert.match(html, /src\/styles\.css/);
  assert.doesNotMatch(html, /https?:\/\/(?:unpkg\.com|cdn\.jsdelivr\.net|cdnjs\.cloudflare\.com)/i);
});
test("offline package script copies required assets", () => {
  const build = fs.readFileSync(path.join(root, "scripts/build.js"), "utf8");
  assert.match(build, /index\.html/);
  assert.match(build, /app\.js/);
  assert.match(build, /styles\.css/);
});
test("Capacitor targets a local bundled web directory", () => {
  const config = JSON.parse(fs.readFileSync(path.join(root, "capacitor.config.json"), "utf8"));
  assert.equal(config.webDir, "dist");
  assert.equal(config.appId, "io.omnitools.offline");
});