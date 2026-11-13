import test from "node:test";
import assert from "node:assert/strict";

import { calculateNotificationPanelStyle } from "../../src/components/NotificationBell.tsx";

test("positions an upward notification panel from the trigger top edge", () => {
  const style = calculateNotificationPanelStyle({
    placement: "up",
    triggerRect: {
      bottom: 720,
      left: 18,
      right: 62,
      top: 676,
    },
    viewportHeight: 768,
    viewportWidth: 1366,
  });

  assert.equal(style.position, "fixed");
  assert.equal(style.left, "18px");
  assert.equal(style.bottom, "100px");
  assert.equal(style.top, undefined);
});

test("positions a downward notification panel below the trigger", () => {
  const style = calculateNotificationPanelStyle({
    placement: "down",
    triggerRect: {
      bottom: 58,
      left: 330,
      right: 374,
      top: 14,
    },
    viewportHeight: 720,
    viewportWidth: 390,
  });

  assert.equal(style.position, "fixed");
  assert.equal(style.left, "16px");
  assert.equal(style.top, "66px");
  assert.equal(style.bottom, undefined);
});

test("keeps the notification panel inside a narrow viewport", () => {
  const style = calculateNotificationPanelStyle({
    placement: "up",
    triggerRect: {
      bottom: 650,
      left: -4,
      right: 40,
      top: 606,
    },
    viewportHeight: 700,
    viewportWidth: 320,
  });

  assert.equal(style.left, "16px");
  assert.equal(style.width, "min(360px, calc(100vw - 32px))");
});
