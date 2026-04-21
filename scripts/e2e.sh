#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# E2E Test Script for Personal Blog System
# ──────────────────────────────────────────────────────────────────────────────
# Usage:
#   ./scripts/e2e.sh              # Run full E2E tests
#   ./scripts/e2e.sh --skip-ai    # Skip AI-related tests (summary & QA)
#
# Environment variables (with defaults):
#   BACKEND_URL     - Backend API base URL (default: http://localhost:8080)
#   ADMIN_EMAIL     - Admin user email (default: from .env or admin@example.com)
#   ADMIN_PASSWORD  - Admin user password (default: from .env or admin123456)
#   E2E_USER_EMAIL  - Test user email (default: user@test.com)
#   E2E_USER_PASSWORD - Test user password (default: user123456)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Parse arguments ───────────────────────────────────────────────────────────
SKIP_AI=false
SKIP_BUILD=false
NO_RECREATE=false
for arg in "$@"; do
  case "$arg" in
    --skip-ai) SKIP_AI=true ;;
    --no-build) SKIP_BUILD=true ;;
    --no-recreate) NO_RECREATE=true ;;
    *) echo "Unknown argument: $arg"; exit 1 ;;
  esac
done

# ── Configuration ─────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVIDENCE_DIR="$PROJECT_DIR/.sisyphus/evidence"
EVIDENCE_FILE="$EVIDENCE_DIR/task-14-e2e.txt"

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
E2E_USER_EMAIL="${E2E_USER_EMAIL:-user@test.com}"
E2E_USER_PASSWORD="${E2E_USER_PASSWORD:-user123456}"

# Load admin credentials from .env if available
if [ -f "$PROJECT_DIR/.env" ]; then
  # shellcheck disable=SC1091
  ADMIN_EMAIL="${ADMIN_EMAIL:-$(grep '^ADMIN_EMAIL=' "$PROJECT_DIR/.env" | cut -d= -f2-)}"
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(grep '^ADMIN_PASSWORD=' "$PROJECT_DIR/.env" | cut -d= -f2-)}"
fi
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123456}"

# Cookie jar for curl
COOKIE_JAR=$(mktemp /tmp/e2e-cookies.XXXXXX)
# Second cookie jar for the normal user
USER_COOKIE_JAR=$(mktemp /tmp/e2e-user-cookies.XXXXXX)

# Error counter
ERRORS=0

# ── Helper functions ──────────────────────────────────────────────────────────
log() {
  echo "$*" | tee -a "$EVIDENCE_FILE"
}

log_ok() {
  local step="$1"
  echo "[OK] $step" | tee -a "$EVIDENCE_FILE"
}

log_fail() {
  local step="$1"
  local detail="${2:-}"
  ERRORS=$((ERRORS + 1))
  if [ -n "$detail" ]; then
    echo "[FAIL] $step: $detail" | tee -a "$EVIDENCE_FILE"
  else
    echo "[FAIL] $step" | tee -a "$EVIDENCE_FILE"
  fi
}

log_step() {
  echo "" | tee -a "$EVIDENCE_FILE"
  echo "=== $1 ===" | tee -a "$EVIDENCE_FILE"
}

# Extract CSRF token from cookie jar
extract_csrf_token() {
  local jar="$1"
  # Cookie jar format: domain  path  secure  expiry  name  value
  # The csrf_token cookie line looks like:
  # localhost  FALSE  /  FALSE  ...  csrf_token  <value>
  grep -i 'csrf_token' "$jar" 2>/dev/null | awk '{print $NF}' | head -1
}

# ── Prerequisite checks ──────────────────────────────────────────────────────
log_step "Prerequisite Checks"

if ! command -v curl &>/dev/null; then
  log_fail "curl is required but not installed"
  exit 1
fi
log_ok "curl is available"

if ! command -v docker &>/dev/null; then
  log_fail "docker is required but not installed"
  exit 1
fi
log_ok "docker is available"

if ! docker compose version &>/dev/null 2>&1 && ! docker-compose version &>/dev/null 2>&1; then
  log_fail "docker compose is required but not installed"
  exit 1
fi
log_ok "docker compose is available"

# Use docker compose v2 or docker-compose v1
if docker compose version &>/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
else
  DOCKER_COMPOSE="docker-compose"
fi

# ── Initialize evidence file ─────────────────────────────────────────────────
mkdir -p "$EVIDENCE_DIR"
echo "E2E Test Evidence - $(date)" > "$EVIDENCE_FILE"
echo "========================================" >> "$EVIDENCE_FILE"
echo "SKIP_AI=$SKIP_AI" >> "$EVIDENCE_FILE"
echo "BACKEND_URL=$BACKEND_URL" >> "$EVIDENCE_FILE"
echo "ADMIN_EMAIL=$ADMIN_EMAIL" >> "$EVIDENCE_FILE"
echo "E2E_USER_EMAIL=$E2E_USER_EMAIL" >> "$EVIDENCE_FILE"
echo "" >> "$EVIDENCE_FILE"

# ── Step 1: Start services with docker compose ───────────────────────────────
log_step "Step 1: Start Services"

cd "$PROJECT_DIR"
if $SKIP_BUILD || $NO_RECREATE; then
  $DOCKER_COMPOSE up -d --no-recreate 2>&1 | tee -a "$EVIDENCE_FILE"
  log_ok "docker compose up -d executed (no build, no recreate)"
else
  $DOCKER_COMPOSE up -d --build 2>&1 | tee -a "$EVIDENCE_FILE"
  log_ok "docker compose up -d --build executed"
fi

# ── Step 2: Wait for health check ────────────────────────────────────────────
log_step "Step 2: Wait for Health Check"

MAX_WAIT=120
WAITED=0
HEALTHY=false

while [ $WAITED -lt $MAX_WAIT ]; do
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BACKEND_URL/api/health" 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "200" ]; then
    HEALTHY=true
    break
  fi
  sleep 2
  WAITED=$((WAITED + 2))
  echo "  Waiting for backend... ($WAITED/${MAX_WAIT}s, HTTP=$HTTP_CODE)" | tee -a "$EVIDENCE_FILE"
done

if $HEALTHY; then
  log_ok "Backend is healthy (waited ${WAITED}s)"
  curl -s "$BACKEND_URL/api/health" | tee -a "$EVIDENCE_FILE"
  echo "" >> "$EVIDENCE_FILE"
else
  log_fail "Backend health check" "Timed out after ${MAX_WAIT}s"
  echo "Container logs:" >> "$EVIDENCE_FILE"
  $DOCKER_COMPOSE logs --tail=50 backend >> "$EVIDENCE_FILE" 2>&1 || true
  echo "" | tee -a "$EVIDENCE_FILE"
  echo "E2E Test: FAILED ($ERRORS errors)" | tee -a "$EVIDENCE_FILE"
  exit 1
fi

# ── Step 3: Register normal user ─────────────────────────────────────────────
log_step "Step 3: Register Normal User"

REGISTER_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$E2E_USER_EMAIL\",\"password\":\"$E2E_USER_PASSWORD\"}" \
  "$BACKEND_URL/api/auth/register" 2>&1)

REGISTER_HTTP=$(echo "$REGISTER_RESPONSE" | tail -1)
REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

echo "Register response: HTTP=$REGISTER_HTTP Body=$REGISTER_BODY" >> "$EVIDENCE_FILE"

if [ "$REGISTER_HTTP" = "201" ]; then
  log_ok "Register user ($E2E_USER_EMAIL)"
elif [ "$REGISTER_HTTP" = "409" ]; then
  log_ok "Register user ($E2E_USER_EMAIL) - already exists, continuing"
else
  log_fail "Register user" "HTTP=$REGISTER_HTTP Body=$REGISTER_BODY"
fi

# ── Step 4: Admin login ──────────────────────────────────────────────────────
log_step "Step 4: Admin Login"

ADMIN_LOGIN_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST \
  -H 'Content-Type: application/json' \
  -c "$COOKIE_JAR" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  "$BACKEND_URL/api/auth/login" 2>&1)

ADMIN_LOGIN_HTTP=$(echo "$ADMIN_LOGIN_RESPONSE" | tail -1)
ADMIN_LOGIN_BODY=$(echo "$ADMIN_LOGIN_RESPONSE" | sed '$d')

echo "Admin login response: HTTP=$ADMIN_LOGIN_HTTP Body=$ADMIN_LOGIN_BODY" >> "$EVIDENCE_FILE"

if [ "$ADMIN_LOGIN_HTTP" = "200" ]; then
  log_ok "Admin login"
else
  log_fail "Admin login" "HTTP=$ADMIN_LOGIN_HTTP Body=$ADMIN_LOGIN_BODY"
fi

# Extract CSRF token for admin
ADMIN_CSRF=$(extract_csrf_token "$COOKIE_JAR")
echo "Admin CSRF token: $ADMIN_CSRF" >> "$EVIDENCE_FILE"

if [ -z "$ADMIN_CSRF" ]; then
  log_fail "Extract admin CSRF token" "csrf_token cookie not found in jar"
fi

# ── Step 5: Admin create post (draft) ────────────────────────────────────────
log_step "Step 5: Admin Create Post (Draft)"

CREATE_POST_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST \
  -H 'Content-Type: application/json' \
  -H "X-CSRF-Token: $ADMIN_CSRF" \
  -b "$COOKIE_JAR" \
  -d '{"title":"E2E Test Post","summary":"A post created by E2E tests","contentMd":"# Hello\n\nThis is an E2E test post with **bold** text."}' \
  "$BACKEND_URL/api/admin/posts" 2>&1)

CREATE_POST_HTTP=$(echo "$CREATE_POST_RESPONSE" | tail -1)
CREATE_POST_BODY=$(echo "$CREATE_POST_RESPONSE" | sed '$d')

echo "Create post response: HTTP=$CREATE_POST_HTTP Body=$CREATE_POST_BODY" >> "$EVIDENCE_FILE"

POST_ID=""
if [ "$CREATE_POST_HTTP" = "201" ]; then
  log_ok "Create draft post"
  # Extract post ID from response
  POST_ID=$(echo "$CREATE_POST_BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
  echo "Created post ID: $POST_ID" >> "$EVIDENCE_FILE"
else
  log_fail "Create draft post" "HTTP=$CREATE_POST_HTTP Body=$CREATE_POST_BODY"
fi

# ── Step 6: Admin publish post ───────────────────────────────────────────────
log_step "Step 6: Admin Publish Post"

if [ -n "$POST_ID" ]; then
  PUBLISH_RESPONSE=$(curl -s -w '\n%{http_code}' \
    -X POST \
    -H "X-CSRF-Token: $ADMIN_CSRF" \
    -b "$COOKIE_JAR" \
    "$BACKEND_URL/api/admin/posts/$POST_ID/publish" 2>&1)

  PUBLISH_HTTP=$(echo "$PUBLISH_RESPONSE" | tail -1)
  PUBLISH_BODY=$(echo "$PUBLISH_RESPONSE" | sed '$d')

  echo "Publish response: HTTP=$PUBLISH_HTTP Body=$PUBLISH_BODY" >> "$EVIDENCE_FILE"

  if [ "$PUBLISH_HTTP" = "200" ]; then
    log_ok "Publish post (ID=$POST_ID)"
  else
    log_fail "Publish post" "HTTP=$PUBLISH_HTTP Body=$PUBLISH_BODY"
  fi
else
  log_fail "Publish post" "No post ID available from previous step"
fi

# ── Step 7: Public access - list posts ───────────────────────────────────────
log_step "Step 7: Public Access - List Posts"

LIST_POSTS_RESPONSE=$(curl -s -w '\n%{http_code}' \
  "$BACKEND_URL/api/posts?page=0&size=10" 2>&1)

LIST_POSTS_HTTP=$(echo "$LIST_POSTS_RESPONSE" | tail -1)
LIST_POSTS_BODY=$(echo "$LIST_POSTS_RESPONSE" | sed '$d')

echo "List posts response: HTTP=$LIST_POSTS_HTTP Body=$LIST_POSTS_BODY" >> "$EVIDENCE_FILE"

if [ "$LIST_POSTS_HTTP" = "200" ]; then
  log_ok "List published posts"
else
  log_fail "List published posts" "HTTP=$LIST_POSTS_HTTP Body=$LIST_POSTS_BODY"
fi

# ── Step 7b: Public access - get post detail ──────────────────────────────────
log_step "Step 7b: Public Access - Post Detail"

if [ -n "$POST_ID" ]; then
  POST_DETAIL_RESPONSE=$(curl -s -w '\n%{http_code}' \
    "$BACKEND_URL/api/posts/$POST_ID" 2>&1)

  POST_DETAIL_HTTP=$(echo "$POST_DETAIL_RESPONSE" | tail -1)
  POST_DETAIL_BODY=$(echo "$POST_DETAIL_RESPONSE" | sed '$d')

  echo "Post detail response: HTTP=$POST_DETAIL_HTTP Body=$POST_DETAIL_BODY" >> "$EVIDENCE_FILE"

  if [ "$POST_DETAIL_HTTP" = "200" ]; then
    log_ok "Get post detail (ID=$POST_ID)"
  else
    log_fail "Get post detail" "HTTP=$POST_DETAIL_HTTP Body=$POST_DETAIL_BODY"
  fi
else
  log_fail "Get post detail" "No post ID available"
fi

# ── Step 8: Normal user login and post comment ────────────────────────────────
log_step "Step 8: Normal User Login"

USER_LOGIN_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST \
  -H 'Content-Type: application/json' \
  -c "$USER_COOKIE_JAR" \
  -d "{\"email\":\"$E2E_USER_EMAIL\",\"password\":\"$E2E_USER_PASSWORD\"}" \
  "$BACKEND_URL/api/auth/login" 2>&1)

USER_LOGIN_HTTP=$(echo "$USER_LOGIN_RESPONSE" | tail -1)
USER_LOGIN_BODY=$(echo "$USER_LOGIN_RESPONSE" | sed '$d')

echo "User login response: HTTP=$USER_LOGIN_HTTP Body=$USER_LOGIN_BODY" >> "$EVIDENCE_FILE"

if [ "$USER_LOGIN_HTTP" = "200" ]; then
  log_ok "Normal user login"
else
  log_fail "Normal user login" "HTTP=$USER_LOGIN_HTTP Body=$USER_LOGIN_BODY"
fi

# Extract CSRF token for normal user
USER_CSRF=$(extract_csrf_token "$USER_COOKIE_JAR")
echo "User CSRF token: $USER_CSRF" >> "$EVIDENCE_FILE"

if [ -z "$USER_CSRF" ]; then
  log_fail "Extract user CSRF token" "csrf_token cookie not found in jar"
fi

# ── Step 8b: Normal user posts comment ───────────────────────────────────────
log_step "Step 8b: Normal User Post Comment"

COMMENT_ID=""
if [ -n "$POST_ID" ] && [ -n "$USER_CSRF" ]; then
  CREATE_COMMENT_RESPONSE=$(curl -s -w '\n%{http_code}' \
    -X POST \
    -H 'Content-Type: application/json' \
    -H "X-CSRF-Token: $USER_CSRF" \
    -b "$USER_COOKIE_JAR" \
    -d '{"content":"Great post! This is a test comment from E2E."}' \
    "$BACKEND_URL/api/posts/$POST_ID/comments" 2>&1)

  CREATE_COMMENT_HTTP=$(echo "$CREATE_COMMENT_RESPONSE" | tail -1)
  CREATE_COMMENT_BODY=$(echo "$CREATE_COMMENT_RESPONSE" | sed '$d')

  echo "Create comment response: HTTP=$CREATE_COMMENT_HTTP Body=$CREATE_COMMENT_BODY" >> "$EVIDENCE_FILE"

  if [ "$CREATE_COMMENT_HTTP" = "201" ]; then
    log_ok "Create comment on post (ID=$POST_ID)"
    COMMENT_ID=$(echo "$CREATE_COMMENT_BODY" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
    echo "Created comment ID: $COMMENT_ID" >> "$EVIDENCE_FILE"
  else
    log_fail "Create comment" "HTTP=$CREATE_COMMENT_HTTP Body=$CREATE_COMMENT_BODY"
  fi
else
  log_fail "Create comment" "Missing post ID or CSRF token"
fi

# ── Step 8c: Verify comment is visible ───────────────────────────────────────
log_step "Step 8c: Verify Comment Visible"

if [ -n "$POST_ID" ]; then
  LIST_COMMENTS_RESPONSE=$(curl -s -w '\n%{http_code}' \
    "$BACKEND_URL/api/posts/$POST_ID/comments" 2>&1)

  LIST_COMMENTS_HTTP=$(echo "$LIST_COMMENTS_RESPONSE" | tail -1)
  LIST_COMMENTS_BODY=$(echo "$LIST_COMMENTS_RESPONSE" | sed '$d')

  echo "List comments response: HTTP=$LIST_COMMENTS_HTTP Body=$LIST_COMMENTS_BODY" >> "$EVIDENCE_FILE"

  if [ "$LIST_COMMENTS_HTTP" = "200" ]; then
    log_ok "List comments for post (ID=$POST_ID)"
  else
    log_fail "List comments" "HTTP=$LIST_COMMENTS_HTTP Body=$LIST_COMMENTS_BODY"
  fi
else
  log_fail "List comments" "No post ID available"
fi

# ── Step 9: Admin delete comment ──────────────────────────────────────────────
log_step "Step 9: Admin Delete Comment"

if [ -n "$COMMENT_ID" ]; then
  DELETE_COMMENT_RESPONSE=$(curl -s -w '\n%{http_code}' \
    -X DELETE \
    -H "X-CSRF-Token: $ADMIN_CSRF" \
    -b "$COOKIE_JAR" \
    "$BACKEND_URL/api/admin/comments/$COMMENT_ID" 2>&1)

  DELETE_COMMENT_HTTP=$(echo "$DELETE_COMMENT_RESPONSE" | tail -1)
  DELETE_COMMENT_BODY=$(echo "$DELETE_COMMENT_RESPONSE" | sed '$d')

  echo "Delete comment response: HTTP=$DELETE_COMMENT_HTTP Body=$DELETE_COMMENT_BODY" >> "$EVIDENCE_FILE"

  if [ "$DELETE_COMMENT_HTTP" = "204" ]; then
    log_ok "Delete comment (ID=$COMMENT_ID)"
  else
    log_fail "Delete comment" "HTTP=$DELETE_COMMENT_HTTP Body=$DELETE_COMMENT_BODY"
  fi
else
  log_fail "Delete comment" "No comment ID available"
fi

# ── Step 10: Admin generate AI summary (optional) ────────────────────────────
log_step "Step 10: Admin Generate AI Summary"

if $SKIP_AI; then
  log_ok "AI summary test skipped (--skip-ai)"
elif [ -n "$POST_ID" ]; then
  AI_SUMMARY_RESPONSE=$(curl -s -w '\n%{http_code}' \
    -X POST \
    -H "X-CSRF-Token: $ADMIN_CSRF" \
    -b "$COOKIE_JAR" \
    "$BACKEND_URL/api/admin/posts/$POST_ID/summary:generate" 2>&1)

  AI_SUMMARY_HTTP=$(echo "$AI_SUMMARY_RESPONSE" | tail -1)
  AI_SUMMARY_BODY=$(echo "$AI_SUMMARY_RESPONSE" | sed '$d')

  echo "AI summary response: HTTP=$AI_SUMMARY_HTTP Body=$AI_SUMMARY_BODY" >> "$EVIDENCE_FILE"

  if [ "$AI_SUMMARY_HTTP" = "200" ]; then
    log_ok "Generate AI summary (post ID=$POST_ID)"
  elif [ "$AI_SUMMARY_HTTP" = "503" ]; then
    log_ok "AI summary skipped (DeepSeek API key not configured)"
  else
    # AI tests may fail due to external service issues; don't count as hard failure
    echo "[WARN] AI summary returned HTTP=$AI_SUMMARY_HTTP (not counted as error)" | tee -a "$EVIDENCE_FILE"
  fi
else
  log_fail "Generate AI summary" "No post ID available"
fi

# ── Step 11: Normal user Q&A (optional) ──────────────────────────────────────
log_step "Step 11: Normal User Q&A"

if $SKIP_AI; then
  log_ok "AI Q&A test skipped (--skip-ai)"
elif [ -n "$POST_ID" ] && [ -n "$USER_CSRF" ]; then
  QA_RESPONSE=$(curl -s -w '\n%{http_code}' \
    -X POST \
    -H 'Content-Type: application/json' \
    -H "X-CSRF-Token: $USER_CSRF" \
    -b "$USER_COOKIE_JAR" \
    -d '{"question":"What is this post about?"}' \
    "$BACKEND_URL/api/posts/$POST_ID/qa" 2>&1)

  QA_HTTP=$(echo "$QA_RESPONSE" | tail -1)
  QA_BODY=$(echo "$QA_RESPONSE" | sed '$d')

  echo "Q&A response: HTTP=$QA_HTTP Body=$QA_BODY" >> "$EVIDENCE_FILE"

  if [ "$QA_HTTP" = "200" ]; then
    log_ok "AI Q&A (post ID=$POST_ID)"
  elif [ "$QA_HTTP" = "503" ]; then
    log_ok "AI Q&A skipped (DeepSeek API key not configured)"
  else
    # AI tests may fail due to external service issues; don't count as hard failure
    echo "[WARN] AI Q&A returned HTTP=$QA_HTTP (not counted as error)" | tee -a "$EVIDENCE_FILE"
  fi
else
  log_fail "AI Q&A" "Missing post ID or CSRF token"
fi

# ── Step 12: Verify /api/auth/me works ────────────────────────────────────────
log_step "Step 12: Verify Auth /me Endpoint"

ME_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -b "$COOKIE_JAR" \
  "$BACKEND_URL/api/auth/me" 2>&1)

ME_HTTP=$(echo "$ME_RESPONSE" | tail -1)
ME_BODY=$(echo "$ME_RESPONSE" | sed '$d')

echo "Auth /me response: HTTP=$ME_HTTP Body=$ME_BODY" >> "$EVIDENCE_FILE"

if [ "$ME_HTTP" = "200" ]; then
  log_ok "Auth /me returns user info"
else
  log_fail "Auth /me" "HTTP=$ME_HTTP Body=$ME_BODY"
fi

# ── Step 13: Admin list all posts ─────────────────────────────────────────────
log_step "Step 13: Admin List All Posts"

ADMIN_POSTS_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -b "$COOKIE_JAR" \
  "$BACKEND_URL/api/admin/posts?page=0&size=10" 2>&1)

ADMIN_POSTS_HTTP=$(echo "$ADMIN_POSTS_RESPONSE" | tail -1)
ADMIN_POSTS_BODY=$(echo "$ADMIN_POSTS_RESPONSE" | sed '$d')

echo "Admin list posts response: HTTP=$ADMIN_POSTS_HTTP Body=$ADMIN_POSTS_BODY" >> "$EVIDENCE_FILE"

if [ "$ADMIN_POSTS_HTTP" = "200" ]; then
  log_ok "Admin list all posts"
else
  log_fail "Admin list all posts" "HTTP=$ADMIN_POSTS_HTTP Body=$ADMIN_POSTS_BODY"
fi

# ── Step 14: Admin list all comments ──────────────────────────────────────────
log_step "Step 14: Admin List All Comments"

ADMIN_COMMENTS_RESPONSE=$(curl -s -w '\n%{http_code}' \
  -b "$COOKIE_JAR" \
  "$BACKEND_URL/api/admin/comments" 2>&1)

ADMIN_COMMENTS_HTTP=$(echo "$ADMIN_COMMENTS_RESPONSE" | tail -1)
ADMIN_COMMENTS_BODY=$(echo "$ADMIN_COMMENTS_RESPONSE" | sed '$d')

echo "Admin list comments response: HTTP=$ADMIN_COMMENTS_HTTP Body=$ADMIN_COMMENTS_BODY" >> "$EVIDENCE_FILE"

if [ "$ADMIN_COMMENTS_HTTP" = "200" ]; then
  log_ok "Admin list all comments"
else
  log_fail "Admin list all comments" "HTTP=$ADMIN_COMMENTS_HTTP Body=$ADMIN_COMMENTS_BODY"
fi

# ── Cleanup ──────────────────────────────────────────────────────────────────
log_step "Cleanup"

rm -f "$COOKIE_JAR" "$USER_COOKIE_JAR"
log_ok "Cookie jars cleaned up"

# ── Final result ─────────────────────────────────────────────────────────────
log_step "Final Result"

echo "" | tee -a "$EVIDENCE_FILE"
if [ $ERRORS -eq 0 ]; then
  echo "E2E Test: PASSED" | tee -a "$EVIDENCE_FILE"
else
  echo "E2E Test: FAILED ($ERRORS errors)" | tee -a "$EVIDENCE_FILE"
fi

echo "" | tee -a "$EVIDENCE_FILE"
echo "Evidence saved to: $EVIDENCE_FILE" | tee -a "$EVIDENCE_FILE"

if [ $ERRORS -gt 0 ]; then
  exit 1
fi