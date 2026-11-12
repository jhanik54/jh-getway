# Accessibility Review — JH GetWay

Date: 2026-11-12 · Scope: main UI (`MainActivity.kt`), string resources, contrast, touch targets.
Method: static review only. No device/TalkBack run yet (see Limitations).

## Findings & fixes applied

### Icons
- Every `Icon` now either carries a meaningful `contentDescription` or an explicit `null` when purely decorative next to a visible label (e.g. provider key glyph, MoreScreen row icons, navigation glyphs use the tab label).
- Action icons are distinguishable: "Open settings", "Copy gateway key", "Delete <provider name>", "Copy diagnostics", "Active route".
- Decorative-but-stateful glyphs keep descriptions only when they carry status that is not otherwise visible (route health checkmark).

### Contrast (WCAG 2.1, computed)
| Pair | Ratio | Result |
|---|---|---|
| Navy #111827 on White / #F4F6FA | 17.7 / 16.4 | AAA (normal text) |
| White on Navy / #29344B paused card | 17.7 / 12.45 | AAA |
| White on #4F67E8 button | 4.73 | AA normal text |
| Muted #667085 on White | 4.97 | AA normal text |
| Blue #4F67E8 on White | 4.73 | AA normal text |
| Red → now #B91C1C on White (errors, delete) | 6.47 | AA normal text (was #D64545 = 4.38, borderline) |
| Orange → now #C2410C (Cooldown) | 5.18 | AA normal text (was #E58B18 = 2.61, failed) |
| Green → now #15803D (Active/healthy) | 5.02 | AA normal text (was #16A36A = 3.24, large-text only) |
| White 72% on Navy (card subtitles) | 9.71 | AAA |

### Touch targets
- `IconButton`, `Button`, `OutlinedButton`, `Switch`, `NavigationBarItem`: ≥ 48 dp interactive area.
- Chain reorder (↑ / ↓) glyphs live inside `IconButton` — 48 dp ✓.

### Other
- Dynamic type: all text uses Material 3 `typography` styles (sp-based), respects system font scale.
- Screens are scrollable (`LazyColumn`, settings/diagnostics dialogs) — no fixed-height clips at large font scales.
- All interactive elements are keyboard/Switch-access compatible via standard Material components.

## Known limitations / follow-ups

1. **FilterChips are ~32 dp high** — below the 48 dp touch-target guideline. They duplicate larger adjacent controls (buttons/switches) and are supplementary selectors; accepted for this release. Revisit with `Modifier.heightIn(min = 48.dp)` if users report mis-taps.
2. **Color is not the only signal**: status also uses text labels (Active/Cooldown/Disabled/Healthy/Unhealthy) — not color-only ✓.
3. **English/Bangla coverage is not total**: static UI strings are extracted (`values/strings.xml`, `values-bn/strings.xml`, 158 keys). Interpolated strings, runtime status messages, toasts, the diagnostics report, and gateway log lines remain English. Bangla UI may mix languages.
4. **Dark theme** applies only primary-color customization over Material 3 dark/light schemes; most surfaces are hardcoded light brand colors, so the app looks near-identical in both modes. Not an accessibility defect, but a fidelity gap.
5. **TalkBack sweep on device pending** — semantics audit done statically only; no spoken-order verification, no IME/focus-trap test on real hardware.
6. Chain tier arrows (↑/↓) are text glyphs; consider Material `ArrowUpward`/`ArrowDownward` icons with descriptions.
7. `startActivityForResult` deprecated call path (from earlier phase) still used for the lock screen; behavior verified only via code review.
