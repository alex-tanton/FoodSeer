#!/usr/bin/env bash
# Starts FoodSeer with the surplus rescue demo: backend on an in-memory H2
# database (no MySQL), the "demo" profile seeding staff, customers, order
# history and a live STEAK surplus listing, and the React frontend.
#
#   scripts/rescue-demo.sh
#
# Needs Java 21, Maven and Node. Every demo account's password is rescue123:
# demo_staff (staff), maya, leo, ana, vic, tara, ben (customers).
# Data lives in memory, so every run starts from the same clean demo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/food-seer-backend"
FRONTEND="$ROOT/food-seer-frontend"

# Maven needs JDK 21; newer JDKs break the project's Mockito/Byte Buddy version.
if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  export JAVA_HOME
fi

for template in main test; do
  dir="$BACKEND/src/$template/resources"
  if [[ ! -f "$dir/application.properties" ]]; then
    cp "$dir/application.properties.template" "$dir/application.properties"
  fi
done

export SPRING_PROFILES_ACTIVE=demo
export SPRING_DATASOURCE_URL='jdbc:h2:mem:foodseer-demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_DATASOURCE_USERNAME=sa
export SPRING_DATASOURCE_PASSWORD=
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
export APP_JWT_SECRET="${APP_JWT_SECRET:-$(openssl rand -hex 32)}"

if [[ ! -d "$FRONTEND/node_modules" ]]; then
  (cd "$FRONTEND" && npm install --no-audit --no-fund)
fi

cleanup() {
  [[ -n "${BACKEND_PID:-}" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "${FRONTEND_PID:-}" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# H2 is a test-scoped dependency, so run on the test classpath.
(cd "$BACKEND" && mvn -q spring-boot:run -Dspring-boot.run.useTestClasspath=true) &
BACKEND_PID=$!

echo "Waiting for the backend on http://localhost:8080 ..."
until curl -s -o /dev/null http://localhost:8080/auth/login -X OPTIONS; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "The backend stopped. See the Maven output above." >&2
    exit 1
  fi
  sleep 2
done

(cd "$FRONTEND" && BROWSER=none npm start) &
FRONTEND_PID=$!

cat <<'EOF'

Rescue demo is starting on http://localhost:3000 (password for every account: rescue123)
  1. Log in as demo_staff -> Rescue Matching -> Preview matches for STEAK -> Send rescue alerts
  2. Log in as maya -> Rescue Deals -> Reserve 1 portion of STEAK
  3. Log in as demo_staff -> Rescue Matching: 1 meal rescued, revenue and savings
Press Ctrl+C to stop.
EOF
wait
