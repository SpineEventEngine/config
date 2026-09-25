---
name: design-over-detekt-thresholds
description: Choose file and class boundaries on design grounds; suppress detekt's size rules (e.g. TooManyFunctions) instead of splitting cohesive code to satisfy them.
metadata:
  type: feedback
  since: 2026-09-25
---

Decide where code lives — which file, which class — by cohesion and responsibility,
not by detekt's size thresholds. When the cohesive design makes a file or class exceed
the threshold of a rule such as `TooManyFunctions`, suppress that rule there with
a reason, rather than moving code into a file that exists only to keep the count down.

**Why:** A plan for #768 proposed a new `CustomSbom.kt` "for detekt `TooManyFunctions`".
The user rejected it: "Do not sacrifice the design decisions over detekt's settings. If
having all the code under `PublicationSbom.kt` is a better design, just suppress the
`TooManyFunctions`."

**How to apply:** When a plan or edit splits a file, state the design reason for the
split. If the only reason is a detekt threshold, keep the code together and add
`@file:Suppress("TooManyFunctions")` (or the matching rule) with a short justification.
A split that has its own design merit, such as separating a public DSL from
`internal` machinery, is still fine.
