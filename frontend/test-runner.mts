import { globSync } from "node:fs";

const files = globSync("tests/**/*.test.{ts,mts,mjs}").sort();

if (files.length === 0) {
  console.error("No test files matched tests/**/*.test.{ts,mts,mjs}");
  process.exit(1);
}

for (const file of files) {
  await import(`./${file}`);
}
