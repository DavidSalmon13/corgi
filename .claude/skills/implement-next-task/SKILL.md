---
name: implement-next-task
description: Implements the next open task(s) from the current spec's tasks.md, in order — one or several per run — testing, ticking and committing each, and stops once only 20% of the context window is left. Use when the user says "implement the next task", "continue the spec", "do the next tasks", or invokes /implement-next-task.
---

# Implement next task

Each run picks up where the last one stopped: take the first open task in the current spec's `tasks.md`, implement it, verify it, tick it, commit it, then move to the next one. Keep going until the spec is done, a task is blocked, or the context budget runs out.

## Important guidelines

- **Context budget is the hard stop.** Run the budget check before every task and at checkpoints during long ones. Stop when it says `verdict=stop`, which means 20% or less of the window is left.
- **Order matters.** Take open tasks top to bottom. Sub-tasks (`T3.1`) come right after their parent. Skip a task only if it is blocked, and say why.
- **One task, one commit.** Never batch several tasks into one commit, and never tick a task whose checks fail.
- **Follow the project rules.** Read `CLAUDE.md`. Use only this project's `.claude/` skills and agents. Money is integer cents, the ledger is append-only, and webhooks are idempotent.
- **Always use AskUserQuestion** when you are blocked on a decision only the user can make. Put assumptions you can make yourself in `DECISIONS.md` (dated, a few sentences each), not in a question.

## Context budget check

```bash
bash "$(git rev-parse --show-toplevel)/.claude/skills/implement-next-task/scripts/context-budget.sh"
# → used=61234 window=200000 used_pct=30.6 left_pct=69.4 verdict=continue
```

The script reads the session transcript, so the numbers are measured, not guessed. The window defaults to 200k and switches to 1M once usage passes 200k. Set `CONTEXT_WINDOW=1000000` to force a 1M window, or `STOP_AT_LEFT_PCT=<n>` to change the threshold. If it prints `verdict=unknown`, estimate conservatively and stop after the current task.

**When to run it:**

1. At the start, before taking the first task.
2. After each task is committed, before taking the next one.
3. During a long task (a screen, a service with many tests), after each major step.

**Look ahead.** If `left_pct` is under about 35% and the next task is large (a whole screen, a new service, a scaffold), stop now rather than start something you cannot finish.

## Process

### Step 1: Find the spec and the next task

1. Branch → spec: grep `^> Branch: <current branch>` in `playbook/products/*/epics/*/specs/*/tasks.md`. Strip any `--t<id>` suffix first. Fall back to suffix-matching `<spec-slug>` from `feat/<epic>/<spec>`. No match → AskUserQuestion.
2. Run `git status`. Uncommitted changes are probably a task the last run stopped in the middle of. Read the diff, find which open task it belongs to, and finish that task first.
3. The next task is the first line matching `- [~] T<id>` (in progress: a run stopped partway through it) or, if there is none, the first `- [ ] T<id>` (open). Lines marked `[x]` are done and `[-]` are cancelled; skip both.
4. No open tasks → say the spec is done, suggest the **qa** skill and then the **pr** skill, and stop.

### Step 2: Load just enough context

Read what this task needs, and no more:

- `spec.md`: the acceptance criteria the task cites (AC numbers) and the sections it touches.
- `standards.md` in the spec folder, plus `playbook/standards/` files that apply (always `global/git-workflow.md` and `global/tech-stack.md`).
- `DECISIONS.md` entries on the same area.
- For screen tasks: the named design file in `design/plainly/` is the UI reference. Rebuild it faithfully.
- The existing code the task extends. Read the neighbouring files rather than the whole repo. For a broad search, use the **code-explorer** agent so the file dumps stay out of this context.

### Step 3: Implement

- **Logic, money, ledger, API:** test first. Write the failing tests named in the task (use the **testing** skill's conventions and `// covers ACn, Tn` comments), confirm they fail for the right reason, then implement until they pass. The **tdd-guide** agent can do this for a large task.
- **Screens:** build from the design file. Use plain language with no unexplained insurance jargon. Show "how we got this" for every amount, and check dark mode and 390 px width.
- **Scaffolding:** match the stack in `tech-stack.md`. If the `protect-config` hook blocks a new config file (tsconfig, eslint and so on), ask the user before setting `PLAYBOOK_ALLOW_CONFIG=1`.
- **Verification tasks** that name a skill ("testing skill", "qa skill"): run that project skill instead of doing the work by hand.
- Stay inside the task's scope. If you spot other work, note it in the final summary rather than doing it.

### Step 4: Verify

Run the checks for what changed: build, lint, typecheck and the tests for the touched module. `./gradlew test` covers `api/`, and the `web/` package scripts cover `web/`. Use the **build-error-resolver** agent if the build breaks and the fix isn't obvious. Don't tick the task until its checks are green.

### Step 5: Tick and commit

1. Change the task's `- [ ]` to `- [x]` in `tasks.md`.
2. Log any decision or assumption in `DECISIONS.md`.
3. Commit the code, the ticked `tasks.md` and any `DECISIONS.md` change together, in the repo's style:

   ```
   feat(<epic-slug>): <what changed, in plain words>

   - <key point> (T<id>)
   - <tests that prove it>

   Co-Authored-By: <attribution line from the session>
   ```

   Never use `--no-verify`. If a hook fails, fix the cause.

### Step 6: Loop or stop

Run the budget check again.

- `verdict=continue` and another open task → go back to Step 1.3.
- `verdict=stop`, the look-ahead says stop, or a task is blocked → stop and go to Step 7.

**Stopping in the middle of a task:** get to a state that compiles, leave the task unticked and the changes uncommitted, and write down exactly what is left. The next run sees the changes in `git status` and finishes the task.

### Step 7: Report

End with a short summary:

```
Done this run: T3 Scaffold web/ · T3.1 Design tokens   (2 commits)
Stopped because: context 81% used (19% left)  |  spec complete  |  T8 blocked: <reason>
In progress: T7, tests written, LedgerQueryService left to do (uncommitted)
Next up: T8 policy.IssuePolicyService
Open: 15 of 23 tasks
```

If the budget stopped the run, tell the user to start a fresh session (or `/clear`) and run `/implement-next-task` again.
