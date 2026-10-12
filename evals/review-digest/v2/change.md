Number the reviews and ask the model to check counts against the ids (v1's wording + ids).

v1 (counting without ids) didn't move grounded (66% -> 60%): the model switched from vague crowd words
to exact counts, then miscounted. Hypothesis: it needs something concrete to count with.
Success rule (unchanged): grounded >= ~80% and no other metric loses more than one case-run.