# Label review guide (#149 AC-2)

> Written on 21 November 2026 (#234, [spec 234](../../../../specs/234-ai-triage-end-to-end.md) D6). The labels of the evaluation cases are proposals written with the evaluation package and, for the holdout, by a separate agent from the rubric only ([spec 227](../../../../specs/227-critical-ceiling-holdout.md) D2). None has been reviewed by another person. #149 AC-2 asks for that review. Applying it is later work (#149): each change is recorded in the [evaluation README](../README.md) with the case, the old value, the new value and the link to the review.

## What to review

[`labels.md`](labels.md) has one line per case, 217 in all: 72 holdout, 97 test and 48 development cases. The lines are grouped:

1. **Urgent references** (minimum `High` or `Critical`). They come first because a model that under-rates one of them fails the gate.
2. **Expected abstentions**: texts that say nothing a team could act on.
3. **The rest** (minimum `Low` or `Medium`).

Within each group the lines are sorted by split (holdout, test, dev) and then by ID. The table shows each case's text and its current labels only. It has **no model output**: do not look at the run folders while you review, so the model's answers do not steer your labels.

## Rubric

- **Category:** one of `Health`, `Safety`, `LostPerson`, `Infrastructure` or `Other` ([`features.md` §4.2](../../../../Product/features.md#42-teams-and-permissions); since 15 November 2026 a theft is `Safety`, and `Infrastructure` is something broken or damaged on the site that needs a team now).
- **Minimum priority:** the **lowest** priority that is still acceptable, on the scale of [`features.md` §5.3](../../../../Product/features.md#53-priority-scale-ai-3): `Critical` (a life or someone's physical safety is at risk now), `High` (someone is hurt or at risk and needs a team within minutes, but no life is in danger), `Medium` (needs a team, but nobody is in danger), `Low` (an inconvenience that can wait). A model answer higher than the minimum passes. A lower one fails. A `Low` or `Medium` case rated `Critical` also fails (the ceiling check).
- **Abstain:** `yes` only when the text gives nothing to triage. An abstention has no category and no minimum priority.
- Judge only what the text says, as an operator reading it would. Tags such as `urgent`, `slang` or `injection` describe the slice the case belongs to. They are not labels to review.

## How to answer, in the pull request that adds this guide

1. For each label you would change, add a **review comment on that case's line** of `labels.md` with the field, the new value and why. For example: *minimum priority `High` → `Critical`: "não responde" means unconscious, which the scale puts at Critical.* One comment per case.
2. In the text of your review, say plainly that **you reviewed the labels**, and which cases you covered: all of them, or which groups or splits. Cases you did not cover stay marked as not reviewed. An approval without that sentence does not count as a label review.
3. When you are unsure, say so in the comment. An unclear comment is not applied; it is listed for follow-up.
