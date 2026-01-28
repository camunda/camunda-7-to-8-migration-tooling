# Configuration Guide for Data Migrator

## Batch Size Configuration

The Data Migrator supports batch INSERT operations to reduce database interactions and improve migration performance.

### Configuration Property

**Property:** `camunda.migrator.batch-size`  
**Type:** Integer  
**Default:** 100  
**Range:** 1 - Integer.MAX_VALUE

### What It Does

The batch size controls how many INSERT operations are collected in memory before being flushed to the database as a single batch operation. This reduces:
- Number of database round trips
- Transaction overhead
- Overall migration time

### How It Works

1. **Buffering**: INSERT operations are collected in an in-memory buffer
2. **Auto-flush**: When the buffer reaches `batch-size`, it automatically flushes to the database
3. **Final flush**: Any remaining records are flushed at the end of migration
4. **Cache**: Buffered records are cached in memory to support lookups before database commit

### Configuration Examples

#### Application Properties

```properties
# Default batch size (100 records)
camunda.migrator.batch-size=100

# Larger batch for better performance (more memory)
camunda.migrator.batch-size=500

# Smaller batch for memory-constrained environments
camunda.migrator.batch-size=50
```

#### Application YAML

```yaml
camunda:
  migrator:
    batch-size: 100
```

### Tuning Guidelines

#### Increase Batch Size When:
- You have plenty of memory available
- Network latency to database is high
- You want to optimize for throughput over memory

#### Decrease Batch Size When:
- Running in memory-constrained environments
- Database transaction timeout is limited
- You want to reduce the impact of failed batches

### Transaction Safety

#### Runtime Migration
- If a batch INSERT fails, all C8 process instances created in that batch are automatically cancelled (rolled back)
- Migration continues with the next batch
- Failed batch keys are logged for troubleshooting

#### History Migration
- Batch INSERT failures are logged but don't stop the migration
- Database transaction management handles rollback of failed inserts
- Migration continues processing subsequent records

### Performance Considerations

**Batch Size vs Memory:**
- Larger batches use more memory (records buffered + cache)
- Memory usage = batch-size × average record size × 2 (buffer + cache)

**Batch Size vs Transaction Timeout:**
- Larger batches take longer to commit
- Ensure database transaction timeout > (batch-size × avg insert time)

**Recommended Starting Values:**
- Development/Testing: 50-100
- Production (small dataset): 100-200
- Production (large dataset): 200-500

### Monitoring

Enable INFO logging to see batch flush operations:

```properties
logging.level.io.camunda.migration.data.impl.clients.DbClient=INFO
```

Log messages include:
- `Flushing batch of X records to database` - Normal batch flush
- `Batch insert failed` - Batch operation failed
- `Rolling back X process instances` - Rollback in progress

### Troubleshooting

**Problem:** Transaction timeout errors  
**Solution:** Reduce batch-size

**Problem:** Slow migration performance  
**Solution:** Increase batch-size (if memory allows)

**Problem:** Out of memory errors  
**Solution:** Reduce batch-size

**Problem:** Frequent batch insert failures  
**Solution:** Check database connectivity and transaction timeout settings

### Related Configuration

This setting works in conjunction with:
- `camunda.migrator.page-size` - Controls how many records are fetched from C7 at once
- Database connection pool settings
- Database transaction timeout settings

### Examples

#### High-throughput Setup (Large Server)
```properties
camunda.migrator.batch-size=500
camunda.migrator.page-size=500
```

#### Memory-constrained Setup (Small Server)
```properties
camunda.migrator.batch-size=50
camunda.migrator.page-size=100
```

#### Balanced Setup (Default)
```properties
camunda.migrator.batch-size=100
camunda.migrator.page-size=100
```
