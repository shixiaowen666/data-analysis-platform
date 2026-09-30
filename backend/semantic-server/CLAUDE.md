# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build
mvn clean compile

# Package
mvn clean package -DskipTests

# Run (Spring Boot)
mvn spring-boot:run

# Run single test class
mvn test -Dtest=QueryV2ValidationTest

# Run a main-class regression (no Spring context)
mvn exec:java -Dexec.mainClass="com.wm.semantic.queryv2.QueryV2ParityRegressionMain" -Dexec.classpathScope=test
```

## Architecture Overview

This is a semantic query server — it receives query requests with dimension/indicator IDs, selects the best-matching data sources (tables or models), generates SQL via a two-layer builder, executes it, and returns unified results.

### Core Request Flow

```
Controller (GetDataController)
  → SqlGenerationServiceImpl (orchestrator)
    → TableSelectionServiceImpl (data source selection)
    → SqlGeneratorService → SegmentSqlBuilder → BaseSqlBuilder (SQL generation)
    → JDBC execution + result assembly
```

### Data Source Selection (`TableSelectionServiceImpl`)

Three-phase process:
1. **Dimension stage** — find candidates (tables/models) that can cover all requested `dimensionIds`
2. **Indicator stage** — batch-load indicators for candidates, filter to valid ones
3. **Greedy algorithm** — select the minimal set of data sources covering all `indicatorIds`

Two data source types:
- **Type 1 (single table)**: direct query, no joins
- **Type 2 (model)**: fact table + dimension tables joined via `olap_fact_dim_mapping_pro`

### SQL Generation (Two-Layer Structure)

- **Inner layer** (`BaseSqlBuilder`): produces `SELECT tX.src AS field_key FROM db.tb tX [JOIN ...]` — renames physical columns to business `field_key` aliases
- **Outer layer** (`SegmentSqlBuilder`): wraps inner as `SELECT agg(f.field_key) AS field_key FROM (inner) f WHERE ... GROUP BY ...`
- **Multi-source** (`UnionSqlBuilder`): UNION ALL across segments
- **Final wrapper** (`OuterSqlWrapper`): re-aggregates across UNION results

All `WHERE`/`HAVING` references use `mainsrc.field_key` — they never touch physical table aliases.

### Key Packages

| Package | Role |
|---|---|
| `controller` | REST endpoints, param validation, response wrapping |
| `service` / `service.impl` | Business orchestration, main flow control |
| `support.sql.builder` | SQL construction (BaseSqlBuilder, SegmentSqlBuilder, UnionSqlBuilder, OuterSqlWrapper) |
| `support.sql.model` | `SqlBuildContext` — the standard intermediate object between service and builders |
| `support.sql` | `TimeRangeConverter` for dialect-aware time range conditions |
| `mapper` | MyBatis-Plus mappers (extends `BaseMapper`) |
| `entity` | DO classes with `@TableName`, database entities |
| `dto` | Request/response and intermediate transfer objects |
| `common.config` | `ExtraDataSourceConfig` — dbName → Druid DataSource routing map |
| `common.exception` | `BizException` + `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| `common.response` | `ApiResponse<T>` — unified `{code, message, data}` wrapper |

### Key Database Tables

- `olap_basic_pro` — dimension/indicator metadata (field definitions)
- `olap_table_pro` / `olap_table_plus` — physical table info and engine dialect
- `olap_src_table_field_mapping` — field_key, src_field, etl summary per table
- `olap_fact_dim_mapping_pro` — model fact-dimension join relationships
- `olap_basic_pro_dimension` / `olap_basic_pro_indicator` — dimension/indicator classifications

### Configuration

- `application.yml` — main config (DB, MyBatis-Plus, extra datasources under `app.extra-datasources`)
- `bootstrap.yml` — Nacos config (disabled by default, enable for service discovery)
- `logback-spring.xml` — 4 log files: console, app, error, SQL

### Guiding Documents

- **AGENTS.md** — technology stack, directory structure, naming conventions, coding rules
- **LOGIC-ARCHITECTURE.md** — business logic rules: data source selection algorithm, SQL generation semantics, filter classification (dimension→WHERE, indicator→HAVING), error scenarios

Key constraints from these docs:
- Java 8, Spring Boot 2.7.x, single Maven module (no submodule splitting)
- DO suffix for entities with `@TableName`, `@Resource` for DI, `@Slf4j` for logging
- SQL generation must go through `support/sql` builders — never concatenate SQL in services
- Dimension filters go to `WHERE`, indicator filters go to `HAVING`
- Default indicator aggregation is `sum`
