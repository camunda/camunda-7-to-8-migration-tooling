# Final Change Summary

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an option is marked (MAY).

Run this procedure before the final response. The change baseline is the starting commit and
`git status --porcelain` output that Step 2 records in `MIGRATION_REPORT.md`.

If the baseline is missing or unreadable, or its status lists a path other than
`MIGRATION_REPORT.md`, then mark every count unavailable and state why. Never estimate counts.

1. Count tracked changes with `git diff --numstat <starting-commit> --`.
2. List untracked, non-ignored files with `git ls-files --others --exclude-standard -z`. Count each
   one with `git diff --no-index --numstat -- /dev/null "<path>"`. Git reads `/dev/null` in this
   command as an empty file on every platform. Exit code 1 means that the files differ.
3. Never stage or commit files to count them.
4. Exclude `MIGRATION_REPORT.md` from every count. List it as a report output.
5. List binary files separately, because Git reports no line counts for them.
6. Classify each changed path with the Step 2 inventories and the table below.

Present this table in the final response and record it in `MIGRATION_REPORT.md`. Total changed lines
are the line additions plus the line deletions. Beside the table, state whether the migration is
complete and give the validation results.

| Asset type | Includes | Files changed | Line additions | Line deletions | Total changed lines |
|---|---|---:|---:|---:|---:|
| Code | code, tests, application configuration, and scripts | | | | |
| BPMN/DMN/forms | BPMN, DMN, and form files | | | | |
| Other | every other file | | | | |
| Total | | | | | |
