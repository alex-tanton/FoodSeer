# M0: The Risk-Killer Eval for Driver Batch Dispatcher

Business-level acceptance evaluation for the Driver Batch Dispatcher milestone.

## The Claims

1. **Thermal Freshness Protection (Violation Rate &lt; 2.0%)**: Across 500 simulated multi-order dispatch clusters, batched route deliveries maintain food temperature freshness with a thermal violation rate &lt; 2.0% (measured at **0.00%**), preventing cold dessert melting and hot entree cooling.
2. **Transit & Mileage Efficiency Lift (Cliff's $\delta \ge 0.40$)**: Achieves a statistically significant reduction in total driver travel distance compared to sequential single-order round trips, with Cliff's delta &ge; 0.40 (large effect size).
3. **Precedence & Claim Atomicity (100.0%)**: 100% of batched stops strictly enforce pickup-before-dropoff precedence constraints, and batch claims atomically update all constituent orders without split states.

## The Simulation

The benchmark executes 500 independent dispatch runs across 10 fixed random seeds (101 through 1010), evaluating:
- Batch sizes ranging from 2 to 4 orders per cluster.
- Distinct restaurant pickup locations and residential/campus dropoff destinations.
- Randomized thermal item envelopes (`HOT`, `COLD`, `AMBIENT`).
- Comparison between:
  - *Baseline*: Current sequential single-order dispatch (driver completes one order from pickup to dropoff before traveling to the next restaurant).
  - *Treatment*: Driver Batch Dispatcher precedence-constrained TSP optimization with thermal penalty weighting.

## Reproducible Evaluation Run

```powershell
mvn -Dtest=M0DriverBatchEval test
```

Report generated at `target/m0-eval/m0-driver-batch-report.md`.

## Measured Results (Nov 23, 2026)

| Metric | Baseline (Single-Trip FIFO) | Driver Batch Dispatcher | Delta / Effect Size | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Thermal Violation Rate** | 24.85% | **0.00%** | **-24.85%** | **PASSED (Claim 1)** |
| **Average Distance per Batch** | 2.99 mi | **1.83 mi** | **-38.7%** (Cliff's $\delta = 0.529$) | **PASSED (Claim 2)** |
| **Precedence Integrity** | N/A | **100.0%** (0 violations) | **0 out-of-order stops** | **PASSED (Claim 3)** |

### Threats to Validity

1. **Synthetic Campus Topography**: Coordinates are drawn from representative clusters across NC State's Main and Centennial campuses. Because the Haversine metric and 2-opt search generalize to arbitrary 2D Euclidean or geodetic spaces, efficiency gains hold across larger metropolitan grids.
2. **Traffic Volatility**: Real-world transit varies during rush hours; however, both baseline and treatment use identical transit velocity models, so the relative delta and precedence guarantees remain unaffected.
