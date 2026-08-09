# Test environment

A local GitLab instance plus a Maven composite of five independent Git repositories — enough to
exercise the plugin end to end, including the failure paths.

Nothing here is part of the plugin build. `testenv/workspace` and `testenv/.token` are generated and
git-ignored.

## Requirements

- Docker Desktop (the container needs ~4 GB RAM)
- Git Bash — the scripts are bash, not PowerShell
- No Maven needed; the IDE imports the composite with its bundled Maven

## Bring it up

```bash
cd testenv
docker compose up -d      # first boot pulls ~1 GB and takes 3-6 minutes
./setup.sh                # waits for readiness, then seeds everything
```

`setup.sh` is idempotent — rerun it any time, it reuses what already exists.

| | |
|---|---|
| GitLab | <http://localhost:8929> |
| Login | `root` / `Bulk-MR-Testenv-2026` |
| Token | printed at the end, stored in `testenv/.token` |
| SSH | port 2224 |

## What it creates

Group `bulk-mr-demo` with five projects, and locally a Maven aggregator whose five modules are each
**their own Git repository**:

```
testenv/workspace/
├── pom.xml           aggregator (not a Git repo)
├── .idea/vcs.xml     the five VCS roots, pre-registered
├── service-a/  .git  feature/BMR-1  pushed
├── service-b/  .git  feature/BMR-2  pushed
├── service-c/  .git  feature/BMR-3  pushed
├── service-d/  .git  feature/BMR-4  pushed, merge request already open
└── service-e/  .git  feature/BMR-5-local-only  never pushed
```

One IDE window therefore shows five repositories — the case the plugin exists for.

## Run the plugin against it

1. `./gradlew runIde`
2. Sandbox IDE: `File | Open` → `testenv/workspace`
3. `Settings | Tools | Bulk Merge Requests` → **+**
   - Host `localhost:8929` (already offered in the dropdown)
   - Provider `GitLab`
   - Token from `testenv/.token`
4. `Git | Bulk Merge Requests…`

## Expected outcome

The last two repositories are wired to fail on purpose, so a single run shows that one bad
repository does not abort the batch:

| Repository | Expectation |
|---|---|
| service-a, -b, -c | created, link in the notification |
| service-d | fails — a merge request for this branch already exists (HTTP 409) |
| service-e | fails — source branch does not exist on the remote |

Notification should read **Created 3 of 5 Merge Requests**.

`localhost:8929` also covers the plain-HTTP path: `RemoteUrl` only keeps `http://` when the remote
itself used it, everything else (including all SSH remotes) is treated as HTTPS.

## Reset

```bash
./teardown.sh     # container, volumes and workspace
```

To keep GitLab but redo the repositories: `rm -rf workspace && ./setup.sh`.
