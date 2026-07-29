# Codex Execution Plans (ExecPlans)

This document defines the requirements for an execution plan ("ExecPlan"): a
living design document that a coding agent can follow to deliver a working
feature or system change.

Treat the reader as a complete beginner to this repository. The reader has only
the current working tree and the single ExecPlan. They have no memory of prior
conversations, plans, or unstated decisions.

Repository instructions in `AGENTS.md` continue to apply. An ExecPlan must
repeat every task-relevant repository constraint that a future implementer
needs, including the personal-branch and final-submission boundaries.

## How to use ExecPlans and PLANS.md

Use an ExecPlan for complex features, significant refactors, migrations,
multi-module changes, work with important unknowns, or work likely to span
multiple hours or sessions. Smaller and well-understood changes do not require
one.

When authoring an ExecPlan, read this entire file and follow it closely. Start
from the skeleton below, investigate the repository, and fill in the plan until
it is self-contained and executable.

When implementing an ExecPlan, proceed milestone by milestone without asking
the user to choose routine next steps. Keep every section current. At each
stopping point, record what is complete, what remains, and the evidence gathered.
Resolve ordinary implementation ambiguity autonomously. Escalate only when a
choice would materially change the approved goal, scope, risk, or external
impact. When the current task authorization and repository rules permit
commits, commit coherent, validated milestones rather than mixing unrelated
work.

When a design changes, update the ExecPlan and record both the decision and its
reason. It must always be possible for another agent or a human to resume the
work using only the working tree and the ExecPlan.

For difficult requirements or important unknowns, add explicit prototyping
milestones. Use small, disposable, observable experiments to establish
feasibility before committing to the full implementation.

Unless the user chooses another location, store task-specific plans under
`.agent/exec-plans/` with a short descriptive filename such as
`.agent/exec-plans/report-download.md`.

## Non-negotiable requirements

Every ExecPlan must satisfy all of the following:

- It is fully self-contained in its current form.
- It remains a living document throughout implementation.
- It enables a novice to implement the change end to end.
- It delivers demonstrably working behavior, not merely code that compiles.
- It defines every non-obvious term in plain language.
- It names the relevant repository-relative files, modules, and commands.
- It states observable acceptance criteria and expected evidence.
- It preserves unrelated worktree changes and follows `AGENTS.md`.

Begin with purpose and user-visible value. Explain what someone can do after the
change that they could not do before and how they can observe that result. Then
describe the exact edits, commands, tests, and expected outcomes needed to reach
it.

Do not rely on earlier conversation or undocumented assumptions. Repeat every
assumption the plan depends on. If external documentation or a prior checked-in
plan is relevant, summarize the required knowledge in this plan. A checked-in
source may also be referenced, but the reader must not need to reconstruct
critical context from external links.

## Formatting

When an ExecPlan appears inside a chat response, put the whole plan in one
fenced code block labeled `md`. Do not nest triple-backtick fences inside it;
indent commands, transcripts, diffs, and code samples instead.

When the ExecPlan is saved as a Markdown file whose complete content is the
plan, omit the outer fence.

Use Markdown headings with two blank lines after each heading. Write in plain
prose and prefer sentences over large tables or long enumerations. Checklists
are mandatory in `Progress` and should generally be avoided elsewhere unless
they make the plan clearer.

## Writing guidelines

Self-containment and plain language are paramount. Define repository-specific
terms immediately and say where they appear in this codebase. Do not write
"according to the architecture document" or "as discussed earlier" in place of
the explanation a new reader needs.

Anchor the plan in observable outcomes. State what the user can do after the
implementation, the commands to run, and what successful output looks like.
Describe acceptance as behavior a human can verify. For internal changes,
identify a test or scenario that fails before the change and passes afterward.

Specify repository context precisely. Use full repository-relative paths. Name
functions, classes, modules, database migrations, routes, and UI surfaces where
relevant. For every command, state the working directory and the expected
result. If results depend on environment or services, state those assumptions
and reasonable alternatives.

Be safe and idempotent. Prefer steps that can be repeated without damage or
drift. Explain how to retry a partially failed operation. For destructive
changes or migrations, provide backups, compatibility measures, or a rollback
path. Never discard unrelated local changes.

Validation is required. Include the relevant tests, linting, builds, startup
commands, and a useful end-to-end scenario. State how to distinguish success
from failure. Compilation alone is not sufficient when behavior can be
demonstrated.

Capture concise evidence. Record the outputs, diffs, screenshots, logs, or
request/response examples that prove each milestone. Keep evidence small and
focused so a future reader can understand it quickly.

## Milestones

Milestones tell the implementation story; `Progress` tracks granular status.
Both must exist.

Each milestone must explain:

- the goal and scope;
- what will exist afterward that did not exist before;
- the edits or investigation required;
- the exact validation commands or observations; and
- the acceptance result that permits moving forward.

Every milestone must be independently verifiable and incrementally advance the
overall goal. If a milestone fails validation, diagnose and repair it before
moving to the next one.

## Living plans and design decisions

The following sections are mandatory and must stay current:

- `Progress`
- `Surprises & Discoveries`
- `Decision Log`
- `Outcomes & Retrospective`

Record unexpected behavior, performance tradeoffs, bugs, constraints, and
useful implementation insights in `Surprises & Discoveries`, with short evidence
where possible.

Record every material design or scope decision in `Decision Log`. If the
implementation changes course, update the affected work, validation, and
recovery instructions throughout the plan rather than adding an isolated note.

At the end of a major milestone and at completion, update
`Outcomes & Retrospective` with what was achieved, what remains, and what was
learned. Compare the result with the original purpose.

## Prototypes and parallel implementations

Use a prototype milestone when it reduces uncertainty. Label it clearly,
describe how to run it, and define the evidence that decides whether to promote
or discard it.

Prefer additive, testable changes before removing an old path. Temporary
parallel implementations are acceptable when they lower migration risk or keep
tests passing. Explain how both paths are validated and how the obsolete path
will be retired safely.

## Skeleton of a good ExecPlan

~~~md
# <Short, action-oriented description>

This ExecPlan is a living document. The sections `Progress`,
`Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must
be kept up to date as work proceeds.

This plan follows `.agent/PLANS.md`.

## Purpose / Big Picture

Explain what someone gains after this change and how they can see it working.
State the user-visible behavior or the observable internal improvement.

## Progress

Use timestamped checkboxes. Every stopping point must reflect the actual state,
including partially completed work.

- [x] (YYYY-MM-DD HH:MMZ) Example completed step.
- [ ] Example incomplete step.
- [ ] Example partial step (completed: X; remaining: Y).

## Surprises & Discoveries

Document unexpected behavior, constraints, bugs, optimizations, or insights,
with concise evidence.

- Observation: ...
  Evidence: ...

## Decision Log

Record every material decision.

- Decision: ...
  Rationale: ...
  Date/Author: ...

## Outcomes & Retrospective

At milestones and completion, summarize outcomes, gaps, and lessons. Compare the
result with the original purpose.

## Context and Orientation

Describe the relevant current state for a reader who knows nothing about the
repository. Name key files and modules with full repository-relative paths.
Define every non-obvious term. State applicable `AGENTS.md` constraints and
known worktree conditions.

## Plan of Work

In prose, describe the sequence of edits and additions. For each change, name
the file and location and explain what will change and why.

## Milestones

Describe each independently verifiable milestone as a narrative: goal, work,
result, and proof.

## Concrete Steps

State exact commands and their working directories. Show short expected
transcripts where useful. Update this section as implementation reveals better
commands or required recovery steps.

## Validation and Acceptance

Describe how to start or exercise the system and what to observe. Give specific
inputs and outputs. Name the tests that fail before the change and pass after it
when applicable. Include a separate diff self-review before completion.

## Idempotence and Recovery

Explain which steps are safe to repeat. For risky or partially failing steps,
provide a safe retry, compatibility, rollback, or cleanup path.

## Artifacts and Notes

Include only the most important transcripts, diffs, screenshots, logs, or
snippets that prove the result.

## Interfaces and Dependencies

Name the required libraries, modules, services, schemas, interfaces, and
function signatures. Explain why each dependency or contract is required.

## Revision note

Whenever the plan changes materially, append a dated note describing what
changed and why. Ensure the change is also reflected throughout every affected
section.
~~~

The completion bar is a self-contained, novice-guiding, outcome-focused plan
that a stateless agent or human can follow from top to bottom to produce and
verify a working result.
