---
name: kdoc-no-issue-references
description: Don't cite tracker issue numbers in KDoc/source doc comments unless essential.
metadata:
  type: feedback
  since: 2026-06-30
---

Avoid referencing tracker issues (e.g. `#440`) in KDoc, Javadoc, and inline source
doc comments unless the reference is essential to understanding the code. The issue
number belongs in the commit message, the PR description, and the `.agents/tasks/`
file — not in long-lived documentation.

**Why:** Issue numbers are transient context; embedded in API/source docs they
become noise and go stale as trackers change. Requested by the user during the
#440 fix, where two test KDocs cited the issue.

**How to apply:** When writing or reviewing doc comments, describe the behavior or
scenario directly instead of pointing at an issue. Keep issue references to the
commit, the PR, and task files.
