# Team memory index

One line per memory. Scan at the start of every session.
See [README.md](README.md) for the format and routing rules.

## Feedback (validated patterns & corrections)

- [cache-hygiene](feedback/cache-hygiene.md) — Batch edits to shared config files to protect prompt cache hit rates across all sibling repos.
- [copilot-review-request](feedback/copilot-review-request.md) — Use `gh api .../requested_reviewers -X POST -f 'reviewers[]=Copilot'`; `@copilot review` comment is unreliable.

## Project (durable context & rationale)

*(no entries yet)*

## Reference (external systems)

- [cache-warm-window](reference/cache-warm-window.md) — How prompt cache entries are shared between sibling-repo sessions and how to maximise overlap.
- [anthropic-api-caching](reference/anthropic-api-caching.md) — Pattern and pricing for adding prompt caching to any direct Anthropic API call.
