# Running the auth and URL tests

Only Docker is required on the host. Maven and Java run inside the test image:

```powershell
docker compose -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from tests
```

The command runs:

- auth controller API tests;
- URL controller API tests;
- the real Spring Security filter-chain and CSRF tests;
- PostgreSQL persistence and Flyway tests against a disposable
  `postgres:16-alpine` Testcontainer.

`compose.test.yaml` mounts the Docker socket into the Maven test container so
Testcontainers can start PostgreSQL as a sibling container. On Docker Desktop,
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` lets the tests connect to
PostgreSQL through its published port. The `host-gateway` mapping provides the
same hostname when using Docker Engine on Linux.

The Docker profile currently excludes the unfinished analytics controller and
stale group/user/SQLite suites. It is an auth-and-URL test command, not yet a
complete-project test command.

To remove stopped test containers afterward:

```powershell
docker compose -f compose.test.yaml down
```
