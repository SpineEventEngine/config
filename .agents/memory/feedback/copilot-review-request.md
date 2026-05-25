---
name: copilot-review-request
description: How to request or re-request a Copilot PR review programmatically — GraphQL botIds is the only reliable path
type: feedback
---

## Any request (initial or re-request)

Use the GraphQL `requestReviews` mutation with `botIds`:

```bash
gh api graphql -f query='
mutation {
  requestReviews(input: {
    pullRequestId: "PR_NODE_ID",
    botIds: ["BOT_kgDOCnlnWA"]
  }) {
    pullRequest { id number }
  }
}'
```

- `PR_NODE_ID`: get from `gh api repos/OWNER/REPO/pulls/NUMBER --jq '.node_id'`
- `BOT_kgDOCnlnWA`: the fixed node ID for the Copilot PR reviewer bot (stable)

## Why not the REST API

`POST /pulls/{number}/requested_reviewers` with `reviewers[]=Copilot` only
works for the **initial** request. For re-requests it silently no-ops.

`userIds` in the GraphQL mutation also fails — Copilot is a Bot, not a User.

`botIds` is the correct field and works for both initial and re-requests.

**How to apply:** Always use the GraphQL `botIds` mutation above. Do not use
the REST endpoint or `@copilot review` comments.
