---
name: copilot-review-request
description: How to request a Copilot PR review — REST API for initial request; web UI required for re-requests
type: feedback
---

## Initial review request (first time on a PR)

Use the REST API:

```bash
gh api repos/SpineEventEngine/REPO/pulls/NUMBER/requested_reviewers \
  -X POST \
  -f 'reviewers[]=Copilot'
```

`REPO` is the current repository name — derive it from `gh repo view --json name -q .name`.
The reviewer login is `Copilot` (capital C).

## Re-requests (after Copilot has already reviewed)

**No programmatic path works.** The REST endpoint silently no-ops, the GraphQL
`requestReviews` mutation rejects Bot node IDs, and `@copilot review` comments
are unreliable. The web UI uses an internal GitHub API not available publicly.

**Tell the user to re-request via the web UI** — "Re-request review" button next
to Copilot in the Reviewers sidebar of the PR.

**Why:** GitHub only exposes re-request flows for human reviewers via public API.
Copilot is a GitHub App/Bot; re-requesting it after an existing review requires
the internal UI path.

**How to apply:** On the first request for a PR, use the REST API above. On any
subsequent request for the same PR, tell the user to click "Re-request review"
in the web UI.
