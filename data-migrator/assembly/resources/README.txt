This is a distribution of

       Camunda 7 to 8 Data Migrator v${project.version}

visit
       https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/README.md

==================

Usage

Before running the migrator, some setup steps are required for both Camunda 7 and Camunda 8. Please refer to the [project README](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/data-migrator/README.md) for details.
To run the Data Migrator:

1. Adjust `./configuration/application.yml`  
   - The `c7` config should point to your Camunda 7 database  
   - The `camunda.client` section should point to your Camunda 8 cluster for runtime migration  
   - The `c8` RDBMS config is only required when running with `--history` (for history migration)

2. Execute the `start` script:  
   - `start.bat` for Windows  
   - `start.sh` for Linux/macOS

Options

- `--help` – Show this help message
- `--runtime` – Migrate runtime data
- `--history` – Migrate history data
- `--identity` – Migrate identity data only
- `--list-skipped` – List previously skipped data
- `--list-migrated` – List migrated entities (C7 ID -> C8 Key)
- `--retry-skipped` – Only retry the migration of previously skipped data
- `--drop-schema` – If migration was successful, drop the migrator schema on shutdown
- `--force` – Force the dropping of the migrator schema in all cases, to be used in combination with --drop-schema (warning: this can lead to data loss)

==================

Contents:

  /
      Contains two start scripts: one for Windows (`.bat`) and one for Linux/macOS (`.sh`).

  internal/
      Contains the Java application.

  configuration/
      Contains all configuration resources, including `application.yml`.

  userlib/
      Add custom database drivers, plugins, or other extensions here.

  logs/
      Created during run time and contains the log outputs

==================

      Camunda 7 to 8 Data Migrator v${project.version}

=================
