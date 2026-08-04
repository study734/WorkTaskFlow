# Member1 Personal Workflow

These instructions apply to this checkout and belong only on the personal
`member1-work` branch.

## Repository Boundaries

- `origin` is the working fork `study734/WorkTaskFlow`.
- `upstream` is the team repository `HO-0219/WorkTaskFlow`.
- Keep intermediate work and personal agent guidance on `member1-work`; push it
  only to `origin/member1-work`.
- Rebuild `member1` from the latest `upstream/main` for a curated final PR.
- Never push directly to `upstream`.
- Use an explicit repository for personal issues:
  `gh issue ... --repo study734/WorkTaskFlow`.
- Reference personal issues as `study734/WorkTaskFlow#<number>` or by full URL;
  do not use an unqualified `Closes #<number>` in the team PR.
- Preserve unrelated worktree changes. Never force-push or delete remote
  branches without explicit approval.

## Repository Map and Commands

- `backend/`: Java 21 Spring Boot API. Run tests there with
  `.\mvnw.cmd test` on Windows.
- `frontend/`: React, TypeScript, and Vite. Install locked dependencies with
  `npm ci`; verify production output with `npm run build`.
- `docs/`: product, API, data, QA, and deployment contracts.
- `DevFlow.md`: phase gates. `devLog/`: work and validation evidence when the
  repository workflow requires it.
- Read the nearest relevant documentation before changing a contract. Use only
  commands the repository actually provides.

## Working Loop

- Establish the goal, relevant context, constraints, and observable done
  criteria from the prompt and repository. For a clear, small task, infer these
  and execute without requiring a formal brief or extra approval.
- Ask only when an unresolved choice would materially change scope, behavior,
  risk, permissions, or external state.
- Inspect Git state before editing and keep unrelated changes untouched.
- Plan first for complex, ambiguous, high-risk, cross-module, or long-running
  work. Use an ExecPlan following `.agent/PLANS.md` only when the task benefits
  from a durable multi-step plan; small clear changes need no plan.
- Once scope and risk are clear, execute autonomously. Do not require the user
  to supervise implementation steps.
- Make the smallest coherent change. Avoid speculative features, abstractions,
  automation, permissions, and unrelated cleanup.
- Give the agent access to the tools needed to verify its result, but keep
  sandbox and approval scope tight.
- Run the narrowest relevant checks first. Run broader backend, frontend, or
  integration gates only when the changed surface warrants them.
- Confirm observable behavior when practical, then review the final diff for
  scope drift, regressions, secrets, missing tests, and documentation impact.
- Report commands actually run and their results. Do not claim unrun checks or
  treat compilation alone as behavioral proof.

## Personal Files

- Keep `AGENTS.md` and personal guidance on `member1-work` only.
- Put unversioned machine-local artifacts in `.git/info/exclude`; do not change
  shared `.gitignore` to hide them.
- Do not use `skip-worktree` or `assume-unchanged` for shared tracked files.

## Final Submission

- Fetch `upstream/main` and rebuild `member1` from that exact base.
- Transfer only curated project changes from `member1-work`. Exclude
  `AGENTS.md`, `.agent/`, `.agents/`, `.codex/`, `.agent-local/`, and `.local/`.
- Create clean feature-level commits; do not reuse mixed WIP commits.
- Before pushing, verify:
  - `.gitignore` is unchanged from `upstream/main`.
  - `git diff --name-only upstream/main...member1` contains no personal paths.
  - `git log --oneline upstream/main..member1` contains only curated commits.
  - `git diff --check` passes.
  - Relevant tests pass and the final diff receives a separate review.
- Push `member1` to `origin`, then open `study734:member1` to
  `HO-0219:main`. Never push the submission branch to `upstream`.
