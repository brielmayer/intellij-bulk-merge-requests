#!/usr/bin/env bash
#
# Sets up the whole test environment:
#   1. waits for the GitLab container to become ready
#   2. creates a personal access token for root
#   3. creates the group and five projects
#   4. generates the Maven composite and pushes it
#
# Idempotent: running it again reuses what already exists.
#
# Requires Git Bash (Windows), docker, git and curl.
set -euo pipefail

cd "$(dirname "$0")"

GITLAB_URL="http://localhost:8929"
CONTAINER="bulk-mr-gitlab"
GROUP="bulk-mr-demo"
TOKEN_FILE=".token"
WORKSPACE="workspace"
PROJECTS=(service-a service-b service-c service-d service-e)

# Docker on Windows/Git Bash mangles arguments that look like paths.
export MSYS_NO_PATHCONV=1

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m    %s\033[0m\n' "$*"; }

# ------------------------------------------------------------------ 1. wait --
wait_for_gitlab() {
  log "Waiting for GitLab at $GITLAB_URL (first boot takes 3-6 minutes)"
  # Not /-/readiness: that endpoint is restricted to the monitoring IP whitelist and
  # answers 404 from outside the container. The sign-in page is the honest signal that
  # Rails is actually serving requests.
  for i in $(seq 1 120); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$GITLAB_URL/users/sign_in" || true)
    if [ "$code" = "200" ] || [ "$code" = "302" ]; then
      echo "    ready after ~$((i * 5))s"
      return 0
    fi
    printf '.'
    sleep 5
  done
  echo
  echo "GitLab did not become ready. Check: docker compose logs -f gitlab" >&2
  exit 1
}

# ----------------------------------------------------------------- 2. token --
create_token() {
  if [ -s "$TOKEN_FILE" ]; then
    TOKEN=$(cat "$TOKEN_FILE")
    if curl -sf -H "PRIVATE-TOKEN: $TOKEN" "$GITLAB_URL/api/v4/user" >/dev/null; then
      log "Reusing the token in $TOKEN_FILE"
      return 0
    fi
    warn "Token in $TOKEN_FILE is no longer valid, creating a new one"
  fi

  log "Creating a personal access token for root (takes ~30s)"
  TOKEN=$(docker exec "$CONTAINER" gitlab-rails runner "
    user = User.find_by_username('root')
    user.personal_access_tokens.where(name: 'bulk-mr-testenv').delete_all
    token = user.personal_access_tokens.create!(
      scopes: ['api', 'write_repository'],
      name: 'bulk-mr-testenv',
      expires_at: 300.days.from_now
    )
    puts token.token
  " 2>/dev/null | tr -d '\r' | tail -n 1)

  if [ -z "$TOKEN" ]; then
    echo "Could not create a token. Check: docker compose logs gitlab" >&2
    exit 1
  fi
  printf '%s' "$TOKEN" > "$TOKEN_FILE"
  echo "    written to testenv/$TOKEN_FILE"
}

api() {
  local method=$1 path=$2
  shift 2
  curl -sf -X "$method" -H "PRIVATE-TOKEN: $TOKEN" "$GITLAB_URL/api/v4$path" "$@"
}

# -------------------------------------------------------------- 3. projects --
create_projects() {
  log "Creating group '$GROUP' and ${#PROJECTS[@]} projects"

  group_id=$(api GET "/groups/$GROUP" 2>/dev/null | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1 || true)
  if [ -z "$group_id" ]; then
    group_id=$(api POST "/groups" \
      -d "name=$GROUP" -d "path=$GROUP" -d "visibility=private" \
      | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
    echo "    group created (id $group_id)"
  else
    echo "    group already exists (id $group_id)"
  fi

  for name in "${PROJECTS[@]}"; do
    if api GET "/projects/$GROUP%2F$name" >/dev/null 2>&1; then
      echo "    $name already exists"
    else
      api POST "/projects" \
        -d "name=$name" -d "path=$name" -d "namespace_id=$group_id" \
        -d "default_branch=main" -d "initialize_with_readme=false" >/dev/null
      echo "    $name created"
    fi
  done
}

# ------------------------------------------------------------- 4. workspace --
# Every module is its own Git repository so one IDE window shows five VCS roots -
# that is the case the plugin exists for.
write_module_pom() {
  local dir=$1 name=$2
  cat > "$dir/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>ch.brielmayer.demo</groupId>
    <artifactId>bulk-mr-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>$name</artifactId>
  <name>$name</name>
</project>
EOF
}

write_module_source() {
  local dir=$1 name=$2
  local class
  class=$(echo "$name" | sed -E 's/(^|-)([a-z])/\U\2/g')
  mkdir -p "$dir/src/main/java/ch/brielmayer/demo"
  cat > "$dir/src/main/java/ch/brielmayer/demo/$class.java" <<EOF
package ch.brielmayer.demo;

public final class $class {

    private $class() {
    }

    public static String describe() {
        return "$name";
    }
}
EOF
}

init_repo() {
  local dir=$1 name=$2 branch=$3 push_branch=$4

  if [ -d "$dir/.git" ]; then
    echo "    $name already initialised"
    return 0
  fi

  git -C "$dir" init -q -b main
  git -C "$dir" add -A
  git -C "$dir" -c user.email=dev@example.com -c user.name="Test Dev" \
    commit -q -m "chore: initial commit"

  git -C "$dir" remote add origin "$GITLAB_URL/$GROUP/$name.git"
  git -C "$dir" -c credential.helper= \
    push -q "http://root:$TOKEN@localhost:8929/$GROUP/$name.git" main

  # A feature branch to open the merge request from.
  git -C "$dir" checkout -q -b "$branch"
  printf '\n// change on %s\n' "$branch" >> "$dir/src/main/java/ch/brielmayer/demo/"*.java
  git -C "$dir" add -A
  git -C "$dir" -c user.email=dev@example.com -c user.name="Test Dev" \
    commit -q -m "feat: change for $branch"

  if [ "$push_branch" = "yes" ]; then
    git -C "$dir" -c credential.helper= \
      push -q "http://root:$TOKEN@localhost:8929/$GROUP/$name.git" "$branch"
    echo "    $name on $branch (pushed)"
  else
    echo "    $name on $branch (deliberately NOT pushed - this repo must fail)"
  fi
}

create_workspace() {
  log "Generating the Maven composite in testenv/$WORKSPACE"
  mkdir -p "$WORKSPACE"

  cat > "$WORKSPACE/pom.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>ch.brielmayer.demo</groupId>
  <artifactId>bulk-mr-demo</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Bulk MR Demo (composite)</name>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <modules>
    <module>service-a</module>
    <module>service-b</module>
    <module>service-c</module>
    <module>service-d</module>
    <module>service-e</module>
  </modules>
</project>
EOF

  # Pre-register the VCS roots so the IDE does not have to ask.
  mkdir -p "$WORKSPACE/.idea"
  {
    echo '<?xml version="1.0" encoding="UTF-8"?>'
    echo '<project version="4">'
    echo '  <component name="VcsDirectoryMappings">'
    for name in "${PROJECTS[@]}"; do
      echo "    <mapping directory=\"\$PROJECT_DIR\$/$name\" vcs=\"Git\" />"
    done
    echo '  </component>'
    echo '</project>'
  } > "$WORKSPACE/.idea/vcs.xml"

  local i=0
  for name in "${PROJECTS[@]}"; do
    i=$((i + 1))
    local dir="$WORKSPACE/$name"
    mkdir -p "$dir"
    write_module_pom "$dir" "$name"
    write_module_source "$dir" "$name"

    # service-e keeps its branch local so the batch has one guaranteed failure.
    if [ "$name" = "service-e" ]; then
      init_repo "$dir" "$name" "feature/BMR-$i-local-only" "no"
    else
      init_repo "$dir" "$name" "feature/BMR-$i" "yes"
    fi
  done
}

# ------------------------------------------------------ 5. one existing MR ----
# service-d already has an open merge request, so the batch hits a 409 there.
create_conflicting_mr() {
  log "Opening a merge request on service-d up front (to test the conflict path)"
  local existing
  existing=$(api GET "/projects/$GROUP%2Fservice-d/merge_requests?state=opened" || echo "[]")
  if [ "$existing" != "[]" ]; then
    echo "    already open"
    return 0
  fi
  api POST "/projects/$GROUP%2Fservice-d/merge_requests" \
    -d "source_branch=feature/BMR-4" \
    -d "target_branch=main" \
    -d "title=Already open" >/dev/null && echo "    created" || warn "could not create it"
}

wait_for_gitlab
create_token
create_projects
create_workspace
create_conflicting_mr

cat <<EOF

------------------------------------------------------------------------
Test environment ready.

  GitLab    $GITLAB_URL
  Login     root / Bulk-MR-Testenv-2026
  Token     $(cat "$TOKEN_FILE")     (also in testenv/$TOKEN_FILE)
  Workspace $(pwd)/$WORKSPACE

Next:
  1. ./gradlew runIde
  2. In the sandbox IDE: File | Open -> testenv/workspace
  3. Settings | Tools | Bulk Merge Requests -> add host
       Host      localhost:8929
       Provider  GitLab
       Token     the token above
  4. Git | Bulk Merge Requests...

Expected result: 3 created, service-d conflicts (merge request already
exists), service-e fails (source branch was never pushed).
------------------------------------------------------------------------
EOF
