import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

function readSource(relativePath: string): string {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8");
}

test("root route sends unauthenticated visitors to login without rendering public dashboard mode", () => {
  const source = readSource("src/App.tsx");
  const protectedRouteSource = readSource("src/routes/ProtectedRoute.tsx");

  assert.match(source, /path="\/"/);
  assert.match(source, /<ProtectedRoute>/);
  assert.match(source, /<Navigate to="\/app\/dashboard" replace \/>/);
  assert.match(protectedRouteSource, /if \(!user\) \{\s*return <Navigate to="\/login" replace state=\{\{ from: location \}\} \/>;\s*\}/);
  assert.doesNotMatch(source, /<DashboardPage mode="public" \/>/);
});

test("unknown routes fall back to protected root instead of rendering dashboard", () => {
  const source = readSource("src/App.tsx");

  assert.match(source, /<Route path="\*" element=\{<Navigate to="\/" replace \/>} \/>/);
  assert.doesNotMatch(source, /<Route path="\*" element=\{<DashboardPage/);
});

test("login page has no public dashboard return link", () => {
  const source = readSource("src/pages/LoginPage.tsx");

  assert.doesNotMatch(source, /Voltar ao dashboard público/);
  assert.doesNotMatch(source, /<Link[^>]+to="\/"/);
});

test("dashboard and station API calls require authentication", () => {
  const dashboardSource = readSource("src/pages/DashboardPage.tsx");
  const stationApiSource = readSource("src/services/stationApi.ts");

  assert.match(dashboardSource, /auth:\s*"required"/);
  assert.doesNotMatch(dashboardSource, /auth:\s*"none"/);
  assert.match(stationApiSource, /auth:\s*"required"/);
  assert.doesNotMatch(stationApiSource, /auth:\s*"none"/);
});

test("public dashboard branch and public CSS classes are absent", () => {
  const dashboardSource = readSource("src/pages/DashboardPage.tsx");
  const stylesSource = readSource("src/styles.css");

  assert.doesNotMatch(dashboardSource, /mode:\s*"public"/);
  assert.doesNotMatch(dashboardSource, /mode === "public"/);
  assert.doesNotMatch(stylesSource, /\.public-shell/);
  assert.doesNotMatch(stylesSource, /\.public-header/);
  assert.doesNotMatch(stylesSource, /\.public-main/);
  assert.doesNotMatch(stylesSource, /\.dashboard--public/);
});
