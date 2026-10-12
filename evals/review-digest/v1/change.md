Tell the model to attribute each point to the number of reviewers who made it.

Baseline: grounded 66% (52-78). Failures were almost all crowd words for single opinions
("most", "several", "viewers agree") and merged points from different reviews.
Success rule (decided before running): grounded >= ~80% and no other metric loses more than one case-run.