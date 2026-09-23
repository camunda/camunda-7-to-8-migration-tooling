# Final Change Summary

Use this procedure after Step 5 and before the final response. Compare the final project tree with
the clean starting commit recorded in `MIGRATION_REPORT.md`.

## Count changes

1. Read the baseline commit from `MIGRATION_REPORT.md`.
2. Count tracked-file changes with `git diff --numstat --no-ext-diff <baseline> --`.
3. List untracked, non-ignored files with `git ls-files --others --exclude-standard -z`.
4. For each untracked file, use the platform's empty-file path with
   `git diff --no-index --numstat --no-ext-diff <empty-file> -- "<path>"`.
   Exit code 1 means the command found differences.
5. Do not stage or commit files to calculate counts.

| Host OS | Empty-file path |
|---|---|
| Linux or macOS | `/dev/null` |
| Windows | `NUL` |

Count changed file paths. Classify files with the Step 2 inventories. Include each new file in the
matching category. Treat code, tests, application configuration, and scripts as **Code**. Treat
BPMN, DMN, and form files as **BPMN/DMN/forms**. Treat all remaining files as **Other**.

Sum line additions and deletions for each category. Report that sum as total changed lines. List
binary changes separately because Git does not provide line counts for them. Exclude
`MIGRATION_REPORT.md` from all counts, and list it separately as a report output.

If the baseline is missing or unreadable, or the starting tree was dirty, mark all exact change
counts unavailable. State why. Never estimate counts.

## Report the result

Use this table in `MIGRATION_REPORT.md` and the final response:

| Asset type | Files changed | Line additions | Line deletions | Total changed lines |
|---|---:|---:|---:|---:|
| Code | ... | ... | ... | ... |
| BPMN/DMN/forms | ... | ... | ... | ... |
| Other | ... | ... | ... | ... |
| Total | ... | ... | ... | ... |

State whether the migration is complete. Include validation results, binary changes, and every open
item. Present the summary in the host's current conversation. In AWS Transform CLI, use the `atx`
transformation conversation.
