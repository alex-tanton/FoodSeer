#!/usr/bin/env bash
# Starts FoodSeer with the Driver Batch Dispatcher demo: backend on an
# in-memory H2 database (no MySQL setup needed), with the "demo" profile
# seeding driver demo_driver, active placed orders across campus with varied
# thermal profiles (Hot Ramen, Cold Gelato, Salad, Burrito), and the React frontend on http://localhost:3000.
#
#   scripts/driver-demo.sh
#
# Needs Java 21, Maven, and Node.
# Demo Driver Credentials:
#   - Username: demo_driver
#   - Password: password
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
export SPRING_DATASOURCE_URL='jdbc:h2:mem:foodseer-driver-demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
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
⚡ Driver Batch Dispatcher Demo Started!
========================================================================
Frontend URL: http://localhost:3000
Demo Driver Credentials:
  - Username: demo_driver
  - Password: password

Demo Walkthrough:
  1. Navigate to http://localhost:3000/login
  2. Log in as "demo_driver" with password "password".
  3. You will be redirected to the Driver Dashboard.
  4. Notice the top view toggle: "⚡ Batched Routes (Optimized)" vs "📦 Individual Orders".
  5. Inspect Available Batch Routes:
     - View clustered routes with total mileage, duration, and Freshness Score.
     - Notice the pooled payout including the "+$2.50 Batch Efficiency Bonus"!
     - Inspect the turn-by-turn stop sequence balancing hot items (Hot Ramen/Pizza) and cold items (Artisan Gelato).
  6. Click "⚡ Claim Batched Route":
     - The batch transitions to CLAIMED and assigns all constituent orders to demo_driver.
  7. Step through Turn-by-Turn Delivery:
     - Click "Confirm Pickup at [Location]" for each pickup.
     - When all pickups are complete, route status automatically updates to IN_TRANSIT.
     - Click "Confirm Delivery to [Location]" for dropoffs.
     - Upon final dropoff, route marks COMPLETED and the efficiency bonus is added directly to Today's Earnings!
========================================================================
Press Ctrl+C to stop.
EOF
wait
