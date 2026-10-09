# 1. No prompt caching for review digests (for now)

Date: 2026-10-09
Status: Accepted

## Context
- What a digest request looks like (prefix layout, token counts from your logs)
- How often the same movie is regenerated today (the metrics)
- The pricing and the three caching rules that matter here

## Decision
We don't use prompt caching for review digests.

## Consequences
- Each regeneration pays full input price (~$0.012 for 3k input tokens)
- No risk of paying write premiums that are never read

## Revisit when
- app_ai_digest_regenerate shows the same movie regenerated more than 2x/hour regularly, or
- the prompt gains a large shared prefix (e.g. few-shot examples)
Then: 1-hour TTL, breakpoint after the stable part, app reviews oldest-first.