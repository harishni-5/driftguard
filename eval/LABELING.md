# How groups are labeled

Written on 2026-11-08, after labeling the first 48 groups (`pattern-drift-labels.json`) and before labeling the held-out set (`pattern-drift-heldout.json`). The first set was labeled without written rules, so it's slightly inconsistent in places; it's kept as it was rather than relabeled after seeing a model's answers.

Each group gets one label. The question is always: **would a careful reviewer ask for these to become one function?**

| Label | When |
|---|---|
| **SAME_JOB** | They do the same job, and one shared function (maybe with a parameter for a name, table, bucket or URL) would replace them without making anything harder to read. This includes copies whose behavior has drifted apart in ways nobody would choose on purpose, such as one copy swallowing errors while another throws. That divergence is the drift. |
| **INTENTIONAL_VARIANTS** | Same shape on purpose: implementations of one interface or base class, an adapter per external API, framework overrides, a public wrapper around an internal method, or a typed per-table data-access layer whose functions behave identically. |
| **DIFFERENT_JOBS** | They do different things and only share vocabulary or a common pattern, such as a UI handler and the database function it calls, or "save a post" vs "rename the user". |
| **UNCLEAR** | Mixed groups (some members are the same job, others aren't) or cases where reasonable reviewers would split. Left out of the score. |

Rules of thumb:
- Different file, different component or different agent class doesn't make a copy intentional. Ask whether the code would be designed that way on purpose.
- Small size doesn't excuse a copy, but it does lower its value. A 3-line duplicate is still SAME_JOB if it's the same job.
- A note goes with every label, saying what decided it.
