const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "..");
const out = path.join(root, "dist");
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, "src"), { recursive: true });
for (const file of ["index.html"]) fs.copyFileSync(path.join(root, file), path.join(out, file));
for (const file of ["app.js", "styles.css"]) fs.copyFileSync(path.join(root, "src", file), path.join(out, "src", file));
console.log("Offline website packaged in dist/ (no runtime CDN dependencies).");