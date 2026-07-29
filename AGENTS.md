# Member1 Personal Workflow

These instructions apply to this checkout and are versioned only on the
`member1-work` branch in `study734/WorkTaskFlow`.

## Repository Roles

- `origin` is the read-only team repository `HO-0219/WorkTaskFlow`.
- `personal` is the working fork `study734/WorkTaskFlow`.
- `member1-work` contains intermediate commits and personal agent guidance.
- `member1` is rebuilt from the latest `origin/main` for a curated final PR.

## Repository Map and Commands

- `backend/` is the Java 21 Spring Boot API. Run backend tests from that
  directory with `.\mvnw.cmd test` on Windows.
- `frontend/` is the React and TypeScript Vite app. Install locked dependencies
  with `npm ci` and verify production output with `npm run build`.
- `docs/` contains product, API, data, QA, and deployment contracts.
- `DevFlow.md` contains phase gates; `devLog/` records work actually performed
  and its validation evidence.
- Read the nearest relevant documentation before changing a contract. Do not
  invent a lint, test, or deployment command that the repository does not
  provide.

## Daily Work

- Work and push intermediate changes only from `member1-work`.
- Create and update issues with an explicit repository:
  `gh issue ... --repo study734/WorkTaskFlow`.
- Reference personal issues as `study734/WorkTaskFlow#<number>` or with their
  full URL. Do not use an unqualified `Closes #<number>` in the team PR.
- Preserve unrelated worktree changes and never force-push or delete remote
  branches without explicit approval.

## Personal Files

- Keep `AGENTS.md` and other personal guidance on `member1-work` only.
- Keep unversioned machine-local artifacts in `.git/info/exclude`.
- Do not modify the shared `.gitignore` to hide personal files.
- Do not use `skip-worktree` or `assume-unchanged` for shared tracked files.

## Working Loop

Before editing or running implementation commands, write a Task Brief with all
four fields:

- **Goal:** State the intended change or result in one sentence.
- **Context:** List the relevant files, directories, documents, examples,
  errors, and current repository state that must be inspected.
- **Constraints:** State the applicable architecture, standards, safety rules,
  branch and repository boundaries, scope limits, and prohibited actions.
- **Done criteria:** Write a Markdown checklist of the observable behavior,
  tests, commands, diff, or documentation state that must be true before
  completion.

Do not begin implementation while any Task Brief field is missing or vague.
Discover repository facts before asking the user. State safe assumptions
explicitly, and ask only about unresolved choices that would materially change
the result.

Each Done criteria checklist must follow these rules:

- Describe outcomes, not implementation activity. Phrases such as
  "implementation complete" are not sufficient.
- Attach validation evidence to each applicable item, such as an exact command,
  test result, observable behavior, or diff inspection.
- Include only checks relevant to the task. Mark an inapplicable standard check
  as `N/A` with a short reason when its omission could otherwise be ambiguous.
- Do not declare completion until every required item is checked.

Use this shape and specialize it for the task:

```md
- [ ] Requested behavior or documentation state is observable.
- [ ] Relevant tests pass: `<exact command and expected result>`.
- [ ] Applicable build or static checks pass: `<exact command and expected result>`.
- [ ] Final diff contains only intended changes.
- [ ] Required documentation or work logs are updated.
- [ ] No secrets, personal files, or unrelated changes are included.
```

For a small task, put the Task Brief and a short two-to-five-item Done criteria
checklist in the first progress update. For multi-step, ambiguous, high-risk, or
cross-module work, use the same four fields as the opening section of an
ExecPlan, connect the checklist to its `Validation and Acceptance` section, and
keep both updated when scope changes. Inspect `git status` and preserve all
unrelated changes before implementation.

After editing, run the narrowest relevant tests first, then the applicable
backend test and frontend build gates. Review the final diff separately for
scope drift, regressions, secrets, missing tests, and documentation impact.
Report the commands run and their results; never claim completion from code
inspection or compilation alone when behavior can be exercised.

## Common Mistake Prevention

- Keep durable repository rules here and repeatable task methods in a skill or
  referenced workflow. Do not overload a one-off prompt with rules that should
  survive the current task.
- Keep one task or ExecPlan focused on one coherent outcome. Split unrelated
  requests instead of allowing a long-running task to accumulate project-wide
  scope.
- Do not skip planning for complex work. Resolve important unknowns with a
  small, observable prototype before committing to a broad implementation.
- Do not give an agent broader filesystem, network, credential, or external
  write access than the current task requires. Escalate only the specific
  blocked action.
- Do not let concurrent agents or live tasks edit the same paths in one
  worktree. Use separate Git worktrees or serialize the work, and reconcile
  changes through reviewed commits.
- Do not automate or schedule a workflow until it succeeds reliably when run
  manually and has clear inputs, outputs, failure handling, and validation.
- Do not supervise routine implementation step by step after scope and risk are
  clear. Let the agent execute the approved loop, but require evidence at
  validation, review, push, PR, and other external-change boundaries.

## ExecPlans

When writing complex features or significant refactors, use an ExecPlan (as
described in `.agent/PLANS.md`) from design through implementation and
validation.

## Final Submission

- Fetch `origin/main` and rebuild `member1` from that exact base.
- Transfer the final project state from `member1-work` while excluding
  `AGENTS.md`, `.agent/`, `.agents/`, `.codex/`, `.agent-local/`, and `.local/`.
- Create clean feature-level commits; do not reuse mixed WIP commits.
- Verify the final diff and commit list before pushing `member1` to `personal`.
- Submit `study734:member1` to `HO-0219:main` with a pull request.
- Never push directly to `origin`.

## Required Final Checks

- `.gitignore` is unchanged from `origin/main`.
- No personal path appears in `git diff origin/main...member1 --name-only`.
- `git log origin/main..member1` contains only curated submission commits.
- Relevant tests pass and the pull request diff receives a separate review.
