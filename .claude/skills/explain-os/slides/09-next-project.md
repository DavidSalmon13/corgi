---
id: next-project
title: Standing up a new project
tracks: [new-project]
ground: onboarding
---

Playbook already lives in this repo: commands, skills, agents and hooks in
`.claude/`, plus standards and the `playbook/` docs. **Commit all of it.**

**Then pick your path:**

- **Code already exists → `/join-project`.** One pass, one confirmation gate:
  it scans the repos, proposes the customer → products → shared-services
  taxonomy, deep-dives each product in parallel, writes the whole tree,
  generates the indexes, and leaves you a `join-report.md` of what it inferred
  versus what it left `TBD`.
- **Greenfield → `/plan-customer`** → `/plan-ecosystem` (only if there are
  several products) → `/plan-product` → `/plan-epic` → `/shape-spec`.

**Then, in the first week:**

- `/discover-standards` — capture the conventions that already exist in the code
  rather than inventing new ones.
- Fill the `TBD`s that `/join-project` flagged — team, business goals, roadmap.
- Point every new techlead at **`/explain-os`**.
