# Quick Start Guide: CDC Application

## Overview

This guide will help you quickly start the CDC (Change Data Capture) application to monitor database changes and generate CSV files.

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Oracle database (optional - will use mock data if not available)

## Quick Start (3 Steps)

### 1. Build the Project

```bash
mvn clean package -DskipTests
```

This creates the uber-jar with all dependencies: `target/realtime-data-pipeline-1.0.0-SNAPSHOT.jar`

### 2. Configure (Optional)

Edit `.env` file to customize settings:

```bash
# Database connection
DATABASE_HOST=host.docker.internal
DATABASE_PORT=1521
DATABASE_USERNAME=system
DATABASE_PASSWORD=helowin
DATABASE_SID=helowin
DATABASE_SCHEMA=FINANCE_USER
DATABASE_TABLES=TRANS_INFO

# CDC settings
OUTPUT_PATH=./output/cdc
POLL_INTERVAL_SECONDS=10
```

### 3. Run the Application

```bash
./run-standalone-cdc.sh
```

That's it! The application will:
- Connect to the database (or use mock data if unavailable)
- Monitor for changes every 10 seconds
- Generate CSV files in `./output/cdc/`

## Viewing Output

Check the generated CSV files:

```bash
# List all CDC output files
ls -lh output/cdc/

# View the latest file
tail -f output/cdc/cdc_events_*.csv
```

## Stopping the Application

Press `Ctrl+C` to stop the application gracefully.

## Output Format

CSV files contain the following columns:

- `timestamp`: When the change was detected
- `table`: Which table changed
- `operation`: Type of change (INSERT, UPDATE, DELETE)
- `data`: The changed data in JSON format

Example:
```csv
timestamp,table,operation,data
"2026-02-13 10:09:14","trans_info","INSERT","{id=0, data='sample_data_0'}"
```

## Testing with Mock Data

If the database is not available, the application automatically runs in mock data mode, generating sample events for testing.

## Troubleshooting

### Database Connection Issues

If you see "Database connection failed", check:

1. Oracle container is running:
   ```bash
   docker ps | grep oracle
   ```

2. Port 1521 is accessible:
   ```bash
   nc -z localhost 1521
   ```

3. Database credentials in `.env` are correct

### No Output Files

If no CSV files are created:

1. Check the application logs for errors
2. Verify the `OUTPUT_PATH` directory exists and is writable
3. Ensure the application is running (not stopped by errors)

## Advanced Usage

### Custom Poll Interval

Change how often the database is checked:

```bash
export POLL_INTERVAL_SECONDS=5  # Check every 5 seconds
./run-standalone-cdc.sh
```

### Monitor Multiple Tables

```bash
export DATABASE_TABLES="TRANS_INFO,USERS,ORDERS"
./run-standalone-cdc.sh
```

### Custom Output Location

```bash
export OUTPUT_PATH=/data/cdc-output
./run-standalone-cdc.sh
```

## Running as Background Process

To run the CDC application in the background:

```bash
nohup ./run-standalone-cdc.sh > cdc.log 2>&1 &
```

Check the process:
```bash
ps aux | grep StandaloneCDCApp
```

Stop the background process:
```bash
pkill -f StandaloneCDCApp
```

## Integration with Downstream Systems

The generated CSV files can be:

1. **Imported into data warehouses** (Snowflake, BigQuery, Redshift)
2. **Processed by ETL tools** (Apache NiFi, Talend, Informatica)
3. **Analyzed with data tools** (Pandas, R, Excel)
4. **Streamed to message queues** (Kafka, RabbitMQ)

## Next Steps

- Review the full documentation in `CDC_IMPLEMENTATION_SUCCESS.md`
- Explore the spec files in `.kiro/specs/realtime-data-pipeline/`
- Check the implementation code in `src/main/java/com/realtime/pipeline/`

## Support

For issues or questions:
1. Check the logs in the console output
2. Review `CDC_IMPLEMENTATION_SUCCESS.md` for detailed information
3. Examine the source code in `StandaloneCDCApp.java`
