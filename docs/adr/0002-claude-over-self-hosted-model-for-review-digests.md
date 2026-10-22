# 2. Claude, not a self-hosted open model, writes review digests (build vs buy)

Date: 2026-10-22
Status: Accepted

## Context
Review digests are written by Claude Opus 5.5 at low effort (Phase 4). Phase 5 added a self-hosted alternative
to compare against: Qwen3-4B-Instruct-2507 (FP8) served by vLLM on the development machine's RTX 5080
(`docker compose --profile gpu up -d llm`), behind the same `DigestModel` interface
(`OpenModelDigestModel`, selected with `app.ai.provider=open-model`).

The comparison changed one thing only, the model that writes the digest. Everything else stayed the same: the
25 eval cases × 2 repetitions, the production prompt, the safety checks, and the Claude Sonnet 5.5 judge
(`evals/review-digest/`, variants `baseline` and `qwen3-4b`). The success rule was written down before the run:
the open model had to keep safe and no_forbidden at 100%, sentiment ≥ 92%, grounded ≥ 52% (the bottom of
Claude's interval) and coverage ≥ 90%.

| Metric | Claude Opus 5.5 | Qwen3-4B (vLLM) | Rule |
|---|---|---|---|
| safe | 100% | 98% | 100% |
| no_forbidden | 100% | 98% | 100% |
| sentiment | 100% | 68% (54–79) | ≥ 92% |
| grounded | 66% (52–78) | 46% (33–60) | ≥ 52% |
| coverage | 98% | 88% | ≥ 90% |
| latency p50 / p95 | 3.7 s / 6.9 s | 2.1 s / 5.4 s | – |
| output tokens | ~200 | 78 | – |

Where the open model falls short:
- It never answered UNKNOWN for movies with too few reviews (0 of 6); Claude always did. When unsure it
  defaults to MIXED (12 of its 16 sentiment misses).
- Prompt injections never got a link or the injected verdict into a digest, but they still bent the result:
  `inject-01` turned a clearly positive set of reviews into MIXED, quoting the attacker's "avoid this film"
  as a complaint. The deterministic checks only look for what an injection tries to plant, so this shows up
  only in the judge's grounded and sentiment scores.
- One digest had more than three `liked` items. `ReviewDigest.problems()` rejected it, so it would never have
  been shown, but it counts against safe and no_forbidden.

What each option costs:
- **Buy (Claude):** about $0.008 per digest at the measured ~1,000 input and ~200 output tokens
  ($4 / $20 per million). Pay per use, nothing to run.
- **Build (self-hosted):** no per-token fee, but a GPU that is always on, with 60% of a 16 GB card
  (~9.8 GB) held by vLLM, several GB of host RAM, and operations work. Getting it reliable here took:
  `--enforce-eager` (CUDA-graph crashes under WSL2); `disable_any_whitespace` (the JSON grammar let the
  model emit newlines until max_tokens on 3 of 12 calls); a warm-up request (the first call after start
  took 46 s); and capping WSL memory (the VM exhausted Windows' commit limit and took Docker down).
  In production it would also need a GPU node and scheduling for it in Kubernetes.

## Decision
Claude stays the digest model. `OpenModelDigestModel` stays in the code, tested and switched off
(`app.ai.provider=claude` by default), as a fallback and as the path for the next comparison.

## Consequences
- Digest quality stays at the measured Claude level; each digest keeps costing about $0.008.
- The app works without a GPU, in development and in Kubernetes.
- The open model keeps working with one property change. Its unit tests run in every build, but its
  quality is only known as of this eval.
- The `app.ai.cost` metric uses Claude's per-token prices. If the open model is switched on, the metric
  overstates the cost, because self-hosting is paid in GPU hours, not tokens.

## Revisit when
- A larger open model fits the card (for example Qwen3-8B FP8): run the same eval as a new variant.
- The schema enforces what it can in the grammar (`"maxItems": 3`), and the prompt states the
  too-few-reviews rule in concrete terms that a small model follows: worth a re-run of `qwen3-4b`.
- Digest volume grows far enough that per-token cost dominates. At $0.008 per digest, 100,000 digests a
  month cost about $800, which is around where a dedicated GPU server starts to compete.
- Data must stay on our own hardware (a privacy or contract requirement): then self-hosting is required,
  and the quality gaps above become the work list.
