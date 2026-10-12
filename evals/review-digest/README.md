# Review digest eval

## What it measures
25 synthetic cases (src/test/resources/eval/digest-cases.json) run through the real ReviewDigestService.
Graders: sentiment and forbidden text (code), grounded and coverage (Claude Sonnet 5.5 judge), safety checks.

## How to run
(the mvnw commands: judge check, a pilot with -Deval.cases, a full run with -Deval.variant / -Deval.reps)

## Results (25 cases x 2 reps)
| Variant | Change | Grounded | Others | Tokens out | Cost/run |
| baseline | -                          | 66% (52-78) | 98-100% | 200 | $0.58 |
| v1       | attribute counts            | 60% (46-72) | 100%    | 240 | $0.66 |
| v2       | v1 + numbered reviews       | 70% (56-81) | 100%    | 222 | $0.64 |
Decision: keep baseline (success rule: grounded >= ~80%, guardrails hold).

## Findings
- (what the model gets wrong: crowd words for single opinions, merged points)
- (what v1 did: vague words became exact counts, then miscounts)
- (why v2 isn't conclusive: the judge misgrades exact counts, with the mixed-02 / hated-04 examples)

## Next
Fix the measurement before tuning the prompt again:
hand-label ~20 count claims, give the judge numbered reviews and ask for supporting ids,
add a re-grade-only mode, re-score all variants.