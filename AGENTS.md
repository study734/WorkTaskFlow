# Member1 Personal Workflow

These instructions apply to this checkout and are versioned only on the
`member1-work` branch in `study734/WorkTaskFlow`.

## Repository Roles

- `origin` is the read-only team repository `HO-0219/WorkTaskFlow`.
- `personal` is the working fork `study734/WorkTaskFlow`.
- `member1-work` contains intermediate commits and personal agent guidance.
- `member1` is rebuilt from the latest `origin/main` for a curated final PR.

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

## Final Submission

- Fetch `origin/main` and rebuild `member1` from that exact base.
- Transfer the final project state from `member1-work` while excluding
  `AGENTS.md`, `.agents/`, `.codex/`, `.agent-local/`, and `.local/`.
- Create clean feature-level commits; do not reuse mixed WIP commits.
- Verify the final diff and commit list before pushing `member1` to `personal`.
- Submit `study734:member1` to `HO-0219:main` with a pull request.
- Never push directly to `origin`.

## Required Final Checks

- `.gitignore` is unchanged from `origin/main`.
- No personal path appears in `git diff origin/main...member1 --name-only`.
- `git log origin/main..member1` contains only curated submission commits.
- Relevant tests pass and the pull request diff receives a separate review.
