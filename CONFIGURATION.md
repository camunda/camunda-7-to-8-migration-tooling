# Camunda 7 to 8 Data Migrator - Configuration Guide

## Database Flush Configuration

### Batch Size

The migrator supports batching of database INSERT operations to reduce database interactions and improve performance. This is particularly useful when migrating large datasets.

#### Configuration Property

```properties
camunda.migrator.batch-size=100
```

**Default:** 100

**Description:** Controls how many INSERT operations are collected before flushing them to the database in a single batch operation.

#### How It Works

1. **Buffering**: INSERT operations are collected in memory instead of being immediately written to the database.
2. **Auto-flush**: When the number of buffered operations reaches `batch-size`, they are automatically flushed to the database.
3. **Manual flush**: At the end of migration processes, any remaining buffered operations are flushed.
4. **Rollback handling**: If a batch insert fails during runtime migration, the migrator automatically rolls back any C8 process instances that were created in that batch.

#### Choosing the Right Batch Size

- **Smaller batch size (e.g., 50-100)**: 
  - More frequent database flushes
  - Lower memory usage
  - Lower risk of transaction timeouts
  - Recommended for systems with limited resources or strict transaction timeout limits

- **Larger batch size (e.g., 200-500)**:
  - Fewer database interactions
  - Better performance for large migrations
  - Higher memory usage
  - Increased risk of transaction timeouts
  - Recommended for systems with ample resources and relaxed transaction timeout limits

#### Example Configurations

**Conservative (for production systems with strict timeouts):**
```properties
camunda.migrator.batch-size=50
camunda.migrator.page-size=50
```

**Aggressive (for development or systems with high performance requirements):**
```properties
camunda.migrator.batch-size=500
camunda.migrator.page-size=500
```

**Balanced (default):**
```properties
camunda.migrator.batch-size=100
camunda.migrator.page-size=100
```

## Transaction Safety

### Runtime Migration

During runtime migration, the migrator:
1. Creates process instances in Camunda 8 via API
2. Buffers the C7-to-C8 mapping records
3. Flushes mappings to the database in batches

If a batch insert fails:
- The migrator tracks all C8 process instance keys created in that batch
- Automatically cancels those process instances in Camunda 8
- Ensures data consistency between C7 and C8

### History Migration

History migration directly inserts records into the C8 database. Since these are standard database operations within the same transaction scope, rollback is handled by the database transaction manager.

## Other Configuration Properties

For a complete list of configuration properties, see:
- `MigratorProperties.java` - Main configuration class
- `C7Properties.java` - Camunda 7 specific configuration
- `C8Properties.java` - Camunda 8 specific configuration

## Best Practices

1. **Test with different batch sizes**: Start with the default (100) and adjust based on your migration performance and timeout constraints.
2. **Monitor memory usage**: Larger batch sizes require more memory to buffer operations.
3. **Consider your database**: Different databases may have different optimal batch sizes for bulk inserts.
4. **Transaction timeouts**: Ensure your database transaction timeout is sufficient for the batch size you choose.
5. **Network latency**: If migrating to a remote database, consider network latency when choosing batch size.
