---
name: copilot-review-request
description: How to programmatically request a Copilot PR re-review — use the REST API, not a comment
type: feedback
---

To request (or re-request) a GitHub Copilot PR review, use the REST API:

```bash
gh api repos/SpineEventEngine/REPO/pulls/NUMBER/requested_reviewers \
  -X POST \
  -f 'reviewers[]=Copilot'
```

`REPO` is the current repository name — derive it from `gh repo view --json name -q .name`.
The reviewer login is `Copilot` (capital C).

**Why:** `@copilot review` in a PR comment does not reliably trigger a re-review.
The REST endpoint above is the only programmatic path that works consistently.

**How to apply:** Any time the user says "request a Copilot review", "ask Copilot for a
review", or the review loop reaches the re-review step — use this API call, not a comment.
