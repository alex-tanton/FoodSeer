#!/usr/bin/env bash
# Starts FoodSeer with the Group Dining Harmonizer demo: backend on an
# in-memory H2 database (no MySQL setup needed), with the "demo" profile
# seeding room DEMO26 ("Library Study Group"), multiple allergic members,
# cross-contamination alerts, and the React frontend on http://localhost:3000.
#
#   scripts/group-demo.sh
#
# Needs Java 21, Maven, and Node.
# Passwords for demo accounts: rescue123
#   - host_alex (Host, no restrictions)
#   - leo (Customer, PEANUTS allergy)
#   - maya (Customer, GLUTEN allergy)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT/food-seer-backend"
FRONTEND="$ROOT/food-seer-frontend"

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
export SPRING_DATASOURCE_URL='jdbc:h2:mem:foodseer-group-demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_DATASOURCE_USERNAME=sa
export SPRING_DATASOURCE_PASSWORD=
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
export APP_JWT_SECRET="${APP_JWT_SECRET:-$(openssl rand -hex 32 2>/dev/null || echo 'Zm9vZHNlZXItdGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyaXRlbXM=')}"

if [[ ! -d "$FRONTEND/node_modules" ]]; then
  (cd "$FRONTEND" && npm install --no-audit --no-fund)
fi

cleanup() {
  [[ -n "${BACKEND_PID:-}" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "${FRONTEND_PID:-}" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# H2 runs on the test classpath
(cd "$BACKEND" && mvn -q spring-boot:run -Dspring-boot.run.useTestClasspath=true) &
BACKEND_PID=$!

echo "Waiting for the backend on http://localhost:8080 ..."
until curl -s -o /dev/null http://localhost:8080/auth/login -X OPTIONS; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "The backend stopped. See Maven logs." >&2
    exit 1
  fi
  sleep 2
done

(cd "$FRONTEND" && BROWSER=none npm start) &
FRONTEND_PID=$!

cat <<'EOF'

========================================================================
🍽️ Group Dining Harmonizer Demo Started!
========================================================================
Frontend URL: http://localhost:3000
Demo Accounts (Password: rescue123):
  - host_alex (Room Host)
  - leo (Customer with PEANUT allergy)
  - maya (Customer with GLUTEN allergy)

Demo Walkthrough:
  1. Log in as host_alex -> Click "Group Dining" in top navigation.
  2. Open room "DEMO26" (Library Study Group) directly:
     http://localhost:3000/group-dining/DEMO26
  3. Inspect Harmonized Menu:
     - Notice "GARDEN SALAD" is Universally Safe for all members.
     - Notice "COOKIES" is flagged: ⚠️ Unsafe for Leo [PEANUT].
  4. Inspect Shared Cart & Split Bill:
     - Notice Table Cross-Contamination Alert: Cookies conflict with Leo's allergy.
     - View itemized per-member subtotals, tax split, and total owed.
  5. Click "Submit Group Order" to checkout the consolidated order.
========================================================================
Press Ctrl+C to stop.
EOF
wait
