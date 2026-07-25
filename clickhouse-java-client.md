# ClickHouse Java Client: Important Classes and Functions

This reference covers the official ClickHouse Java `client-v2` API.

## `Client`

Package: `com.clickhouse.client.api.Client`

The main client used to connect to ClickHouse, execute queries, and insert data.
Create one instance and reuse it throughout the application.

### Builder functions

| Function | Purpose |
| --- | --- |
| `new Client.Builder()` | Creates a client builder. |
| `addEndpoint(String endpoint)` | Adds a ClickHouse HTTP endpoint. |
| `setUsername(String username)` | Sets the ClickHouse username. |
| `setPassword(String password)` | Sets the ClickHouse password. |
| `setDefaultDatabase(String database)` | Sets the default database. |
| `compressServerResponse(boolean enabled)` | Enables or disables response compression. |
| `compressClientRequest(boolean enabled)` | Enables or disables request compression. |
| `setMaxConnections(int count)` | Sets the maximum number of pooled connections. |
| `build()` | Builds the client. |

### Client functions

| Function | Purpose |
| --- | --- |
| `ping()` | Checks whether ClickHouse is reachable. |
| `execute(String sql)` | Executes a command such as `CREATE TABLE`. |
| `query(String sql)` | Executes a query. |
| `query(String sql, QuerySettings settings)` | Executes a query with parameters and settings. |
| `queryRecords(String sql, QuerySettings settings)` | Executes a query and returns generic records. |
| `insert(String table, Collection<?> rows)` | Inserts a collection of registered Java objects. |
| `insert(String table, Collection<?> rows, InsertSettings settings)` | Inserts objects with custom insert settings. |
| `getTableSchema(String table)` | Retrieves the schema of a table. |
| `register(Class<?> type, TableSchema schema)` | Registers a Java class for object serialization. |
| `newBinaryFormatReader(QueryResponse response)` | Creates a reader for binary query results. |
| `close()` | Closes connections and releases client resources. |

`execute`, `query`, and `insert` return `CompletableFuture` results.

## `QuerySettings`

Package: `com.clickhouse.client.api.query.QuerySettings`

Contains options for a single query.

| Function | Purpose |
| --- | --- |
| `setQueryParams(Map<String, Object> params)` | Supplies named query parameters. |
| `setSetting(String name, String value)` | Sets a ClickHouse query setting. |
| `setDatabase(String database)` | Overrides the client's default database. |
| `setQueryId(String queryId)` | Assigns an ID to the query. |
| `setFormat(ClickHouseFormat format)` | Sets the response format. |

Example parameter syntax in SQL:

```sql
SELECT count()
FROM redirect_events
WHERE url_id = {urlId:UInt64}
```

## `QueryResponse`

Package: `com.clickhouse.client.api.query.QueryResponse`

Represents a query result. Close it after reading.

| Function | Purpose |
| --- | --- |
| `getReadRows()` | Returns the number of rows read. |
| `getReadBytes()` | Returns the number of bytes read. |
| `getElapsedTime()` | Returns query timing information. |
| `getResponseHeaders()` | Returns supported HTTP response headers. |
| `getServerDisplayName()` | Returns the name of the ClickHouse server. |
| `close()` | Closes the response stream. |

## `ClickHouseBinaryFormatReader`

Package: `com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader`

Reads individual rows and columns from a `QueryResponse`.

| Function | Purpose |
| --- | --- |
| `hasNext()` | Checks whether another row exists. |
| `next()` | Moves to the next row. |
| `getString(...)` | Reads a string column. |
| `getInteger(...)` | Reads a Java `int`. |
| `getLong(...)` | Reads a Java `long`. |
| `getBoolean(...)` | Reads a Boolean value. |
| `getBigInteger(...)` | Reads a large unsigned integer. |
| `getBigDecimal(...)` | Reads a decimal value. |
| `getLocalDate(...)` | Reads a `Date` or `Date32`. |
| `getLocalDateTime(...)` | Reads a date-time value without a zone. |
| `getInstant(...)` | Reads a timestamp as an `Instant`. |
| `getObject(...)` | Reads a column as a general Java object. |
| `getObjectArray(...)` | Reads an array column as `Object[]`. |

Most getters accept either a column name or a column index.

## `GenericRecord`

Package: `com.clickhouse.client.api.data_formats.GenericRecord`

Represents a result row without requiring a custom Java class.

Important functions include:

- `getString(...)`
- `getInteger(...)`
- `getLong(...)`
- `getBoolean(...)`
- `getLocalDate(...)`
- `getLocalDateTime(...)`
- `getInstant(...)`
- `getObject(...)`
- `getObjectArray(...)`

## `InsertSettings`

Package: `com.clickhouse.client.api.insert.InsertSettings`

Contains settings for one insert operation.

It is used to configure the input format and ClickHouse settings associated with
an insert.

## `InsertResponse`

Package: `com.clickhouse.client.api.insert.InsertResponse`

Represents the result of an insert operation. It exposes insert metrics and
response metadata and must be closed after use.

## `TableSchema`

Package: `com.clickhouse.client.api.metadata.TableSchema`

Contains table and column metadata. It is mainly used with:

```java
TableSchema schema = client.getTableSchema("redirect_events");
client.register(RedirectEvent.class, schema);
```

## `ClickHouseFormat`

Package: `com.clickhouse.data.ClickHouseFormat`

Enum containing ClickHouse data formats. Common values include:

- `RowBinary`
- `JSONEachRow`
- `CSV`
- `TSV`
- `Native`

## Exception classes

| Class | Meaning |
| --- | --- |
| `ClickHouseException` | Base ClickHouse client exception. |
| `ServerException` | ClickHouse returned a database error. |
| `ClientException` | The client failed to create or complete the request. |
| `ClientMisconfigurationException` | The client configuration is invalid. |

Because operations are asynchronous, callers may also encounter Java's
`TimeoutException`, `ExecutionException`, and `InterruptedException` when
waiting for a result.
