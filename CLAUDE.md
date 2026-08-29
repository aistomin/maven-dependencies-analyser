# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`AGENTS.md` is a symlink to this file, so the same guidance applies to any AI coding agent
working here. Edit `CLAUDE.md`; never replace the symlink with a copy.

## Rule number one: review the real diff before every commit message

**This repository is public.** Anything committed here is published to the world and stays in the
git history even if a later commit removes it.

Never suggest a commit message from memory of what you changed. The user edits files manually,
other tools touch the tree, and a build can leave artifacts behind. Before proposing any commit
message, look at what is *actually* there:

```bash
git status --short          # includes untracked files, which diffs do not show
git diff HEAD               # staged + unstaged changes to tracked files
```

Then read every untracked file that would be committed — `git diff` never shows them.

Review that output as a reviewer, not as the author, and confirm two things:

1. **The change is correct** and does what the ticket asked. Changes the Maven build cannot
   verify (docs, config, CI, tooling) must still be read line by line before committing.
2. **Nothing is in the diff that does not belong in a public repository.** Credentials, tokens,
   API keys, `.env` files, private keys, personal or third-party data, absolute local paths,
   scratch and temp files, editor/OS junk (`.idea/`, `*.iml`, `.DS_Store`), build output
   (`target/`, `*.class`, `*.jar`), and anything else unrelated to the ticket.

If anything looks wrong or out of place, stop and raise it with the user. Do not commit it and
sort it out afterwards — a public push cannot be taken back.

## Project

`maven-dependencies-analyser` is a Maven plugin (single Maven module, JDK 21, `packaging:
maven-plugin`, goal prefix `maven-dependencies-analyser`) with a single goal, `check`, that
parses a project's `pom.xml` and fails or warns the build when the parent, dependencies, or
plugins have newer versions in Maven Central. It is published to Maven Central as
`com.github.aistomin:maven-dependencies-analyser`. Requires JDK 21+ and Maven 3.8.3+.

## Commands

```bash
# Full build — run this before submitting any PR (per README contribution guidelines)
mvn clean install package javadoc:javadoc

# Tests only
mvn test

# Single test class / single test method
mvn test -Dtest=MdaMojoTest
mvn test -Dtest=MdaMojoTest#testWarning
```

Notes on the build:
- There is no separate lint command: Checkstyle, PMD, duplicate-finder, and JaCoCo's coverage
  check are all bound to the `verify` phase and **fail the build** on violations.
- Checkstyle (`conf/checkstyle.xml`) is strict: mandatory Javadoc on all methods, fields, and
  types (with `@since` tags on types), 80-character lines, `final` method parameters and
  local variables, and naming rules. It scans both `src/main/java` and `src/test/java`
  (`includeTestSourceDirectory` in the `maven-checkstyle-plugin` config), so tests are held
  to exactly the same bar as production code. Match the existing files' Javadoc/comment
  style exactly or the build breaks.
- The Apache 2.0 license header on every `.java` file is enforced by the `RegexpHeader`
  module, which pins all 15 lines verbatim. **The copyright line is
  `Copyright (c) 2019 Andrej Istomin` and the year is frozen at 2019** — the year of first
  publication. Never turn it into a range, never bump it to the current year, and never add
  automation that does: the year carries no legal weight (copyright arises without a notice
  at all under the Berne Convention), Apache's own boilerplate uses a single year, and a
  constant header is one that can never start failing on its own. Changing the year breaks
  the build. `LICENSE` carries the same notice and is kept in sync by hand.
- JaCoCo enforces **80% line coverage per package** (`jacoco:check`); new code needs tests.
- The plugin runs itself on this repo during `verify` (dogfooding, at `WARNING` level).
- Tests are JUnit 5 (Jupiter). Both the tests and the dogfooding step query the real Maven
  Central over the network, so the full build needs network access.
  `src/test/resources/error_pom.xml` intentionally contains outdated dependencies;
  `sample_pom.xml`, `parentless_pom.xml`, and `sections_pom.xml` cover the other parsing
  cases.

## Architecture

All production code is one package, `com.github.aistomin.maven.dependencies.analyser`,
four files:

- **`MdaMojo`** — the plugin entry point (`@Mojo(name = "check", defaultPhase = VERIFY)`).
  Parameters: `level` (`ERROR` fails the build, `WARNING` only logs — see `FailureLevel`),
  `enabled`, `skip` (wins over both, meant for the command line), and `pom` (path to the
  pom file, used by tests to point at fixture poms); all of them are settable as
  `-Dmda.<parameter>`. It
  collects parent + dependencies + plugins, asks the repo for newer versions of each, and
  routes every failure through `throwError()`, which either throws `MojoFailureException` or
  logs, depending on `level`.
- **`MdaBuildFile`** — interface abstracting a build file (`parent()`, `dependencies()`,
  `plugins()`).
- **`MdaPom`** — the pom.xml implementation, parses with `MavenXpp3Reader` and resolves
  `${property}` version references against the pom's `<properties>`. Entries without a
  resolvable version are filtered out: a missing version is logged at `debug` (it is
  inherited from the parent), an unresolvable `${property}` at `warn`.
- Version lookup against Maven Central is delegated to the author's separate library
  `com.github.aistomin:maven-browser` (`MavenCentral`, `MvnArtifactVersion`, etc.) — changes
  to the actual "what's newer" logic usually belong there, not here.

## Releasing

The pom on `master` always carries the next version as `X.Y-SNAPSHOT`, matching the
milestone in development. Releases go through the manual `Release to Maven Central` GitHub
Actions workflow (`.github/workflows/release.yml`, workflow_dispatch, run from `master` —
dry runs included). It takes two issue numbers as inputs — the "Release version X" ticket
of the milestone being released and the one of the next milestone — plus an optional
`dry-run` flag. Versions are derived from the tickets' milestone titles (`Version X.Y`).
The workflow does everything end-to-end: validates the tickets, the milestone, the tag,
and that the pom is at `<version>-SNAPSHOT`; bumps the version in `pom.xml` and
`README.md`; deploys to Maven Central via the `release` Maven profile (sources/javadoc
jars, GPG signing, central-publishing-maven-plugin); pushes the release commit and a
follow-up next-`SNAPSHOT` commit to `master`; creates the `v<version>` GitHub release with
generated notes; and closes the release ticket and the milestone. No version branch is
created (the old `4.0`-style branches are legacy). Don't bump the version in the pom by
hand and don't run the release profile locally.

The self-referencing `com.github.aistomin:maven-dependencies-analyser` plugin in
`pom.xml` (the dogfooding step) is deliberately **not** bumped by the release: the version
being released does not exist in Maven Central while the release build runs. Dependabot
opens that bump as a `build(deps)` PR once the new version is published.

The workflow is not atomic — the deploy to Maven Central is irreversible and happens
before the pushes. If a run dies after the deploy step, finish the release by hand (push
the release commit, create the `v<version>` release, bump the pom to the next
`X.Y-SNAPSHOT`, close the ticket and the milestone) and do **not** re-dispatch the
workflow: Maven Central rejects republishing a version.

## Git conventions

Work on a branch named `Issue-<number>` off `master`. Commit messages are
[Conventional Commits](https://www.conventionalcommits.org/) with the issue number as the
scope, e.g. `docs(#507): define ai workflow in claude.md with agents.md symlink`. Older
history uses other styles (`Issue #456. Sentence.`, topical scopes like `fix(checkstyle):`,
`fix/...` branch names) — that is legacy; do not imitate it. `./cleanup_branches.sh` deletes
local branches whose remote is gone. Run the full Maven build and only commit when it passes.

## Working agreements

These are binding rules for how work happens in this repository, not suggestions.

### Creating a GitHub issue

Repository: https://github.com/aistomin/maven-dependencies-analyser.

Every issue must have all three of:

1. **A how-to-contribute link at the end of the body**, verbatim:

   ```markdown
   Please read [how to contribute](https://github.com/aistomin/maven-dependencies-analyser?tab=readme-ov-file#how-to-contribute)
   ```

2. **Assignee `aistomin`.**
3. **The highest-numbered open milestone.** Never hardcode it — it moves with every
   release, so resolve it at creation time:

   ```bash
   gh api repos/aistomin/maven-dependencies-analyser/milestones \
     --jq '[.[] | select(.state=="open")] | sort_by(.number) | last | .title'
   ```

Body shape: a short problem statement, then the proposal or acceptance criteria when the
issue is more than one line, and always the contribute link last.

Show the user the drafted title and body and get approval before calling `gh issue create`.

```bash
gh issue create --title "<title>" --body "<body>" --assignee aistomin --milestone "<resolved milestone>"
```

Afterwards, verify the issue actually carries the assignee and milestone
(`gh issue view <n> --json assignees,milestone`) — a bad `--milestone` string fails silently
in some `gh` versions.

### Opening a new milestone

When the user asks to open a milestone for version `X.Y`, always ask them for the
description and the due date — never assume either — then show the full draft (milestone
plus release issue) and get approval before creating anything. On the go:

1. Create the milestone titled `Version X.Y` with the agreed description and due date
   (`gh api repos/aistomin/maven-dependencies-analyser/milestones -f title=...
   -f description=... -f due_on=...`).
2. Create its release issue in that milestone: title `Release version X.Y`, assignee
   `aistomin`. The release issue always goes into the milestone just created — this
   deliberately overrides the "highest-numbered open milestone" rule above. Body template:

   ```markdown
   Let's release version X.Y once all the other issues in this milestone are solved.

   Release steps:
   - [ ] All other issues in the milestone "Version X.Y" are closed or moved out.
   - [ ] The next milestone and its release ticket exist.
   - [ ] Run the "Release to Maven Central" workflow (optionally with a dry run first),
         with this ticket as `release-ticket` and the next milestone's release ticket
         as `next-release-ticket`.

   Please read [how to contribute](https://github.com/aistomin/maven-dependencies-analyser?tab=readme-ov-file#how-to-contribute)
   ```

Only when the user explicitly asks for it, migrate issues from the milestone that is
about to be released: move all still-open issues of the old milestone to the new one,
**except the old milestone's own release ticket** — it must stay, so the release workflow
can close it together with the milestone. Closed issues stay where they are.

### Solving a ticket

The user gives a ticket number or a ticket link. Work through these steps in order.

Steps 1–4 are local, reversible, and need no approval — just do them. **Steps 5–8 each stop
and wait for the user to explicitly say go.** "Go" for one step is not go for the next:
approving the implementation is not approval to commit, approving the commit is not approval
to push.

1. **Read the ticket.** Always resolve the number against this repository, whatever form the
   user gave it in — a bare number, `#507`, or a full URL:

   ```bash
   gh issue view <number> --json number,title,body,milestone,assignees,labels,state,comments
   ```

2. **Sync master and clean up stale branches.** Merged branches from previous tickets are
   pruned here rather than after a merge — there is no need to tidy the local clone at the
   end of a ticket.

   ```bash
   git checkout master && git pull && ./cleanup_branches.sh
   ```

3. **Branch.**

   ```bash
   git checkout -b Issue-<number>
   ```

4. **Grill the user on the ticket.** Issues here range from one-line dependency bumps to
   genuinely ambiguous work. Ask about anything the ticket leaves open — scope, acceptance
   criteria, API shape, what the tests should assert. Use the `grill-me` skill when the
   issue is non-trivial or underspecified; for something obviously trivial, one or two
   clarifying questions (or none) are enough. Do not skip straight to code because the fix
   looks obvious.

5. **Propose the changes — do not make them yet.** Describe what will change, in which
   files, and which tests need to follow. Edit files only after an explicit go.

   The verification command is the full Maven build:

   ```bash
   mvn clean install package javadoc:javadoc
   ```

   It needs JDK 21 and network access (the tests and the dogfooding step call the real
   Maven Central APIs); mention in the proposal if either is missing. Remember the build
   gates: strict Checkstyle, PMD, and duplicate-finder at `verify`, and JaCoCo's 80% line
   coverage per package — new code without tests fails the build.

   Once implemented: run the full build and report the real result.

6. **Review the real diff, then suggest the commit message.** Always, immediately after the
   changes are in — the user may reject the implementation instead, which sends you back to
   step 5. Apply rule number one first: `git status --short`, `git diff HEAD`, read any
   untracked files, and check the result for both correctness and anything that must not
   reach a public repository. Never suggest a message describing changes you have not just
   re-read. Never commit before an explicit go. Format:

   ```
   type(#<number>): imperative, lower-case summary, no trailing period

   Optional body explaining *why*, wrapped at ~72 chars.

   Closes #<number>
   ```

   The header carries the ticket number in the conventional-commit scope. The
   `Closes #<number>` footer is what makes GitHub close the ticket automatically once the PR
   is merged into `master`. Never add AI attribution (`Co-Authored-By: Claude`,
   `Generated with Claude Code`) to commits or PR bodies.

7. **Suggest pushing.** After the commit exists, offer `git push -u origin Issue-<number>`.
   Never push before an explicit go.

8. **Suggest the pull request.** Offer the PR title (same conventional format as the commit
   header) and body. Create it only on an explicit go:

   ```bash
   gh pr create --title "type(#<number>): summary" --body "<body>"
   ```

   Repeat `Closes #<number>` in the PR body — that closes the ticket regardless of whether
   the PR is merged, squashed, or rebased, whereas the commit footer alone only survives a
   merge commit.
