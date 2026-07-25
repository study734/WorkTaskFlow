# Member1 Agent Safety

These rules apply only to the independent `WorkTaskFlow-member1` clone.

## Fixed Workflow

- Work only in this clone and only on the local `member1` branch.
- `origin` is the personal backup repository `study734/WorkTaskFlow`.
- `upstream` is the team repository `HO-0219/WorkTaskFlow`.
- Fetching `upstream/main` is read-only and may be used to inspect team changes.
- Integrate `upstream/main` into local `member1` only after checking that the worktree is clean.
- Push intermediate team-code backups only to `origin/member1`.
- Push personal configuration only from `.personal/` to `origin/personal-config`.
- Never merge `personal-config` into `member1`.
- Push to `upstream/member1` only after the user explicitly approves the final team push.
- Creating a pull request from `upstream/member1` to `upstream/main` requires separate explicit approval.

## Start Gate

- Verify the current directory is `WorkTaskFlow-member1`.
- Verify the outer repository branch is `member1`.
- Verify the outer repository remotes match the repositories above.
- Inspect existing changes and preserve everything outside the requested scope.
- If any check fails, stop and report it instead of repairing or bypassing it automatically.

## Safety

- Never access or modify the sibling `WorkTaskFlow` directory.
- Never push directly to `upstream/main`.
- Never force-push or delete a remote branch.
- Never bypass the disabled `upstream` push URL without explicit final-push approval.
- Do not merge a pull request, change repository settings, or delete branches without explicit approval.
- Do not modify files outside the user's requested scope.
- Do not commit the root `AGENTS.md`, `.personal/`, secrets, credentials, `.env`, or generated artifacts to the outer team-code repository.
- The personal fork is public. Never store secrets, private credentials, personal data, or confidential team material in `personal-config`.

## Validation

- Before a backup or team push, report the changed files and relevant diff.
- Run validation appropriate to the change.
- For a full MVP checkpoint, run the backend tests and frontend production build.
- Report failures and remaining gaps; do not hide or bypass them.
