# LABDSOF

# Contributing Guidelines

These conventions keep the repository history clear and consistent, and make it easy to see when a change was made and who made it. All branches, commits, pull requests and repository documentation are written in **English**.

## 1. Workflow

The team uses **GitHub Flow**.

1. Every piece of work starts from a **GitHub Issue** (`#<number>`).
2. Create a short-lived branch from `main`.
3. Commit following the conventions below.
4. Open a pull request to `main`.
5. After approval and a green CI run, the PR is **squash-merged** and the branch is deleted.

`main` is always deployable. Direct pushes to `main` are not allowed.

## 2. Branch naming

Format: `<prefix>/<short-description>`

- Lowercase, kebab-case, short and descriptive.

| Prefix | Use for |
|---|---|
| `feature/` | New features or enhancements |
| `bugfix/` | Bug fixes |
| `refactor/` | Code restructuring with no behaviour change |
| `test/` | Adding or updating tests |
| `docs/` | Documentation updates |
| `ci/` | CI/CD pipelines and workflows |
| `chore/` | Dependencies, configuration, Docker, maintenance |

Example: `docs/add-conventions-to-global-report`

## 3. Commit messages

Format:

```
<prefix> (#<issue>): <Title>

[optional body]
```

| Rule | Detail |
|---|---|
| Prefix | Same as the branch prefix, without the slash (`feature`, `bugfix`, `refactor`, `test`, `docs`, `ci`, `chore`) |
| Issue | GitHub Issue number in parentheses, e.g. `(#1)` |
| Title | At most **50 characters**, first letter capitalised, imperative mood ("Add", not "Added"), no trailing full stop |
| Body | Optional. Separate it from the title with a blank line and explain *what* changed and *why* |

Example: `docs (#1): Add conventions chapter to global report`

## 4. Pull requests

| Item | Convention |
|---|---|
| Target | `main` |
| Title | `<Prefix>: <Concise description>` (capitalised prefix), e.g. `Docs: Add conventions to global report` |
| Description | Must reference the issue with `Closes #<issue>` so the issue closes automatically on merge. Include a summary of the changes and how they were tested |
| Merge method | **Squash merge**. The PR title becomes the commit on `main`, and GitHub adds the PR number automatically: `Docs: Add conventions to global report (#12)` |

## 5. Code review

A pull request can only be merged after **two reviews**:

1. **GitHub Copilot**: requested automatically when the PR is opened.
2. **A team member**: 1 approval from someone other than the author.

### As the author
- One issue per PR. Keep it small.
- Review your own diff and wait for CI to pass before asking for a review.
- Address every Copilot comment: fix it, or reply explaining why not.
- Reply to every comment and resolve all conversations.
- Merge the PR yourself (squash) once both reviews are done.

### As the reviewer
- Review within **1 working day**.
- Read Copilot's comments first. Copilot helps, but does not replace your review.
- Be respectful: comment on the code, not the person.
- Check that:
  - it does what the issue asks
  - it works and has tests
  - it is easy to read
  - there are no secrets and no security issues
  - the conventions are followed
- Start blocking comments with **`blocking:`**. Everything else is a suggestion.
- **Approve** when no blocking comments remain; otherwise **Request changes**.

## 6. Branch protection on `main`

- [ ] No direct pushes
- [ ] Copilot code review requested automatically on every PR (repository ruleset)
- [ ] At least **1 approval** from another team member (authors do not approve their own PRs)
- [ ] Stale approvals are dismissed when new commits are pushed
- [ ] All review conversations must be resolved
- [ ] CI pipeline must pass
- [ ] Branch must be up to date with `main` before merging
- [ ] Head branch is deleted automatically after merge

## 7. Pull request template

Save as `.github/pull_request_template.md`:

```markdown
## Summary
<!-- What does this PR change and why? -->

Closes #

## Type
- [ ] feature  - [ ] bugfix  - [ ] refactor  - [ ] test  - [ ] docs  - [ ] ci  - [ ] chore

## How was it tested?
<!-- Unit / integration / manual steps -->

## Checklist (Definition of Done)
- [ ] I reviewed my own diff before requesting a review
- [ ] CI pipeline is green
- [ ] Commits follow the conventions
- [ ] Tests added/updated and passing
- [ ] Documentation updated (if applicable)
- [ ] No secrets committed
```
