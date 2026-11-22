# References for Issue a policy

## Related Specs

_None._ This is the first spec in any epic; the pre-flight overlap check had nothing to search.

## Upstream Meeting

None. Scope comes from `ASSIGNMENT.md`, `DECISIONS.md` and the epic's product brief and tech plan.

## Visuals

Design-canvas components in `design/plainly/` (see `design/plainly/README.md`). Rebuild them in React; don't reuse the markup. Copy, tokens and states come from these files.

| File | Screen | States this spec builds |
| --- | --- | --- |
| `design/plainly/SignIn.dc.html` | Sign-in with role switcher (demo) | — |
| `design/plainly/B1-Policies.dc.html` | B1 Policies | populated (All / Owes money chips), empty |
| `design/plainly/B2-NewPolicy.dc.html` | B2 New customer and policy | valid, field errors |
| `design/plainly/B3-Workspace.dc.html` | B3 Policy workspace | balance due (issue lines only; no action buttons) |
| `design/plainly/P1-Home.dc.html` | P1 Home: Your cover | amount due (without the Pay button) |

Design tokens (light and dark) are the CSS variables on `.pl` / `.pl.dark` at the top of each file.

## Similar Implementations

None. The repo is greenfield.
