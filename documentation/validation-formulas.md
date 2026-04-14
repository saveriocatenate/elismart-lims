# EliSmart LIMS — Validation Formulas and Calculation Specifications

**Version:** 1.0  
**Status:** Normative  
**Audience:** Software developers, laboratory scientists, system validators, regulatory auditors

This document is the authoritative specification for every derived metric and calibration model
computed by the EliSmart LIMS validation engine. An auditor or validator must be able to use
this document to independently verify that the software calculates each quantity correctly.

For each formula, both the mathematical expression (in Unicode notation) and the corresponding
Java source code are provided so that the documented specification can be directly compared
against the implementation.

---

## Table of Contents

1. [Signal Processing](#1-signal-processing)
   - 1.1 [Signal Mean](#11-signal-mean)
   - 1.2 [Coefficient of Variation (%CV)](#12-coefficient-of-variation-cv)
   - 1.3 [Percent Recovery (%Recovery)](#13-percent-recovery-recovery)
2. [Calibration Curve Models](#2-calibration-curve-models)
   - 2.1 [Four-Parameter Logistic (4PL)](#21-four-parameter-logistic-4pl)
   - 2.2 [Five-Parameter Logistic (5PL)](#22-five-parameter-logistic-5pl)
   - 2.3 [Three-Parameter Log-Logistic (3PL)](#23-three-parameter-log-logistic-3pl)
   - 2.4 [Linear](#24-linear)
   - 2.5 [Semi-Log Linear](#25-semi-log-linear)
   - 2.6 [Point-to-Point](#26-point-to-point)
3. [Outlier Detection](#3-outlier-detection)
   - 3.1 [Percent CV Threshold](#31-percent-cv-threshold)
   - 3.2 [Grubbs Test (between-pair)](#32-grubbs-test-between-pair)
4. [Validation Engine Logic](#4-validation-engine-logic)
5. [Traceability and Audit](#5-traceability-and-audit)
6. [References](#6-references)

---

## 1. Signal Processing

All `MeasurementPair` entities store two raw replicate signals (`signal1`, `signal2`) captured
from the instrument. All derived values — mean, %CV, and %Recovery — are calculated exclusively
server-side at POST and PUT time. Client-supplied values for these fields are silently ignored.

### 1.1 Signal Mean

**Formula:**

```
Mean = (Signal₁ + Signal₂) / 2
```

**Applicability:** All `MeasurementPair` records where n = 2 replicates (the standard
configuration in EliSmart). The mean represents the best estimate of the true signal for the
replicate pair.

**Implementation class:** `ValidationConstants.calculateSignalMean(double signal1, double signal2)`  
**File:** `src/main/java/it/elismart_lims/service/validation/ValidationConstants.java`

```java
public static double calculateSignalMean(double signal1, double signal2) {
    return (signal1 + signal2) / 2.0;
}
```

**Calculation timing:** Computed at every POST (new pair creation) and every PUT/PATCH (pair
update). The stored `signalMean` column always reflects the current raw signal values.

---

### 1.2 Coefficient of Variation (%CV)

The %CV quantifies within-pair imprecision (repeatability) as a dimensionless percentage of
the mean signal. It is the primary within-pair quality criterion.

**Formula (ISO 5725-2 / CLSI EP15-A3, n = 2):**

```
SD  = |Signal₁ − Signal₂| / √2       (sample standard deviation for n = 2)
%CV = (SD / Mean) × 100
```

Combining the two steps into a single expression:

```
%CV = |Signal₁ − Signal₂| / (Mean × √2) × 100
```

#### Why divide by √2, not by 2?

ISO 5725-2 defines the sample standard deviation as:

```
SD = √( Σ(xi − mean)² / (n − 1) )
```

For exactly two replicates (n = 2), this simplifies to:

```
SD = |Signal₁ − Signal₂| / √2
```

The division by √2 yields an **unbiased estimate of the population standard deviation**.
Many laboratory software packages incorrectly compute the simpler **Percent Range** instead:

```
%Range = |Signal₁ − Signal₂| / Mean × 100
```

The relationship between the two is:

```
%Range = √2 × %CV
```

This means that a %Range of 14.14% corresponds to a %CV of 10.00%. Using %Range instead of
%CV will consistently over-report imprecision by a factor of √2 ≈ 1.414. EliSmart uses the
ISO 5725-2 definition (%CV) throughout. Regulatory submissions and inter-laboratory comparisons
based on %CV will not be reproducible if the instrument software uses %Range — verify the
definition used by any software being compared against EliSmart.

**Special case:** When Mean = 0, %CV is set to 0.0 to avoid division by zero. A zero-signal
measurement pair should be investigated independently.

**Acceptance criterion:** The protocol defines `maxCvAllowed` (e.g., 15.0%). A pair **passes**
the CV criterion if:

```
cvPct ≤ maxCvAllowed
```

**Implementation class:** `ValidationConstants.calculateCvPercent(double signal1, double signal2)`  
**File:** `src/main/java/it/elismart_lims/service/validation/ValidationConstants.java`

```java
public static final double SQRT_2 = Math.sqrt(2.0);

public static double calculateCvPercent(double signal1, double signal2) {
    double mean = calculateSignalMean(signal1, signal2);
    if (mean == 0.0) {
        return 0.0;
    }
    double sd = Math.abs(signal1 - signal2) / SQRT_2;
    return (sd / mean) * 100.0;
}
```

**Standards:** ISO 5725-2:2019 §6.2; CLSI EP15-A3 §4.

---

### 1.3 Percent Recovery (%Recovery)

%Recovery quantifies accuracy — the agreement between the measured concentration (back-calculated
from the calibration curve) and the theoretically known nominal concentration. It is the primary
accuracy criterion for CONTROL and SAMPLE pairs.

**Formula:**

```
%Recovery = (Measured Concentration / Nominal Concentration) × 100
```

Where:

- **Measured Concentration** (`interpolatedConc`) — the concentration value back-interpolated
  from the calibration curve using the pair's `signalMean` as input to the inverse curve
  function (see Section 2 for each curve model's inverse formula).
- **Nominal Concentration** (`concentrationNominal`) — the certified or assigned concentration
  of the calibrator, control, or spiked sample, as entered when the MeasurementPair was created.

**Applicability:**

| Pair Type    | %Recovery calculated? | Rationale                                                                      |
|--------------|-----------------------|--------------------------------------------------------------------------------|
| CALIBRATION  | No                    | Calibrators define the curve; comparing them against their own fit is circular |
| CONTROL      | Yes                   | Known-concentration QC material — the primary accuracy check                  |
| SAMPLE       | Yes                   | Unknown concentration interpolated from the curve; recovery verifies plausibility |

**Acceptance criterion:** The protocol defines `maxErrorAllowed` as a percentage (e.g., 20.0%).
A pair **passes** the recovery criterion if:

```
|%Recovery − 100| ≤ maxErrorAllowed

Equivalently:   (100 − maxErrorAllowed) ≤ %Recovery ≤ (100 + maxErrorAllowed)
Example (maxErrorAllowed = 20):  80% ≤ %Recovery ≤ 120%
```

**Implementation location:** `ValidationEngine.evaluate()`  
**File:** `src/main/java/it/elismart_lims/service/validation/ValidationEngine.java`

```java
double interpolated = curveFittingService.interpolateConcentration(
        protocol.getCurveType(), pair.getSignalMean(), curveParams);

double recovery = (interpolated / pair.getConcentrationNominal()) * 100.0;
pair.setRecoveryPct(recovery);

boolean recoveryPass = Math.abs(recovery - 100.0) <= protocol.getMaxErrorAllowed();
```

**Standards:** ICH Q2(R2) §3.2 "Accuracy"; FDA Guidance for Industry — Bioanalytical Method
Validation (2018), Section IV.A.

---

## 2. Calibration Curve Models

Each protocol specifies a `CurveType` that determines which mathematical model is used to fit
the calibration data. All fitters implement the `CurveFitter` interface and share a common
workflow:

1. **Fit** (`fit(List<CalibrationPoint> points)`) — given CALIBRATION pairs ordered by
   concentration, estimate the model parameters that minimise the residual sum of squares.
2. **Interpolate** (`interpolate(double signal, CurveParameters params)`) — given a signal
   value and the fitted parameters, return the back-calculated concentration using the
   analytical inverse of the forward model.

Fitted parameters are serialised as a JSON object and stored in the `curve_parameters` column
of the `experiment` table. All subsequent %Recovery calculations reuse these stored parameters.

---

### 2.1 Four-Parameter Logistic (4PL)

**CurveType enum constant:** `FOUR_PARAMETER_LOGISTIC`  
**Implementation class:** `FourPLFitter`  
**File:** `src/main/java/it/elismart_lims/service/curve/FourPLFitter.java`

#### Forward model

```
y = D + (A − D) / (1 + (x / C)^B)
```

| Parameter | Key in JSON | Physical meaning                                               |
|-----------|-------------|----------------------------------------------------------------|
| A         | `"A"`       | Bottom asymptote — signal as concentration → 0                |
| B         | `"B"`       | Hill slope (Hill coefficient) — steepness of the sigmoid       |
| C         | `"C"`       | Inflection point (EC₅₀ / IC₅₀) — concentration at half-maximal response |
| D         | `"D"`       | Top asymptote — signal as concentration → ∞                   |

When A < D (typical ELISA increase curve), signal rises from A to D as concentration increases.
When A > D (inhibition assay), signal decreases. The model is symmetric about the inflection
point C.

#### Regression method

Non-linear least squares via the **Levenberg-Marquardt algorithm** (Apache Commons Math 3,
`LevenbergMarquardtOptimizer`). An analytic Jacobian is provided to improve convergence speed
and numerical stability.

**Initial parameter guess (data-driven):**

```
A₀ = min(signal values)          — bottom asymptote estimate
D₀ = max(signal values)          — top asymptote estimate
C₀ = √(xMin × xMax)              — geometric mean of concentration range
B₀ = 1.0                         — neutral starting slope
```

**Minimum calibration points required:** 4 (one per free parameter).

#### Inverse formula (back-interpolation)

```
x = C × ((A − D) / (y − D) − 1)^(1/B)
```

**Validity constraint:** The signal `y` must be strictly between the two asymptotes:

```
min(A, D) < y < max(A, D)
```

Signals at or beyond the asymptotes produce undefined (infinite) concentrations; the software
raises `IllegalArgumentException` in these cases.

```java
// Back-interpolation — FourPLFitter.interpolate()
double innerRatio = (a - d) / (signal - d) - 1.0;
return c * Math.pow(innerRatio, 1.0 / b);
```

**When to use:** Symmetric sigmoidal dose-response curves. The standard model for ELISA
immunoassay calibration. Recommended when the response is symmetric around the inflection point.

**Standards / References:** Findlay & Dillard (2007); EMA Guideline on bioanalytical method
validation (2011), Section 4.1.1.

---

### 2.2 Five-Parameter Logistic (5PL)

**CurveType enum constant:** `FIVE_PARAMETER_LOGISTIC`  
**Implementation class:** `FivePLFitter`  
**File:** `src/main/java/it/elismart_lims/service/curve/FivePLFitter.java`

#### Forward model

```
y = D + (A − D) / (1 + (x / C)^B)^E
```

Parameters A, B, C, D share the same definitions as 4PL (see §2.1), plus:

| Parameter | Key in JSON | Physical meaning                                                    |
|-----------|-------------|---------------------------------------------------------------------|
| E         | `"E"`       | Asymmetry parameter — when E = 1, the model reduces exactly to 4PL |

When E ≠ 1, the curve is asymmetric around the inflection point. E > 1 broadens the upper
portion; E < 1 broadens the lower portion. In practice, most real immunoassay curves exhibit
slight asymmetry that the 4PL cannot capture; the 5PL typically yields lower residuals.

#### Regression method

Levenberg-Marquardt with analytic Jacobian, identical library as 4PL.

**Initial parameter guess:**

```
A₀ = min(signal values)
D₀ = max(signal values)
C₀ = √(xMin × xMax)
B₀ = 1.0
E₀ = 1.0   ← starts the optimizer at the symmetric 4PL solution
```

Starting at E₀ = 1.0 ensures the optimizer begins at a physically meaningful point and
converges to the correct asymmetry.

**Minimum calibration points required:** 5.

#### Inverse formula

```
x = C × (((A − D) / (y − D))^(1/E) − 1)^(1/B)
```

Validity constraint: `min(A, D) < y < max(A, D)` (same as 4PL).

```java
// Back-interpolation — FivePLFitter.interpolate()
double ratio     = (a - d) / (signal - d);              // must be > 0
double innerBase = Math.pow(ratio, 1.0 / e) - 1.0;      // must be > 0
return c * Math.pow(innerBase, 1.0 / b);
```

**When to use:** Asymmetric sigmoidal curves. Preferred over 4PL when goodness-of-fit
diagnostics indicate systematic residuals at the extremes of the concentration range. More
common in real assay data than often assumed.

---

### 2.3 Three-Parameter Log-Logistic (3PL)

**CurveType enum constant:** `LOG_LOGISTIC_3P`  
**Implementation class:** `LogLogistic3PFitter`  
**File:** `src/main/java/it/elismart_lims/service/curve/LogLogistic3PFitter.java`

#### Forward model

The 3PL model is a 4PL model with the bottom asymptote A fixed at zero:

```
y = D / (1 + (x / C)^B)

Equivalently:  y = D × (x/C)^B / (1 + (x/C)^B)
```

This is equivalent to setting A = 0 in the 4PL equation.

| Parameter | Key in JSON | Physical meaning                                    |
|-----------|-------------|-----------------------------------------------------|
| B         | `"B"`       | Hill slope                                          |
| C         | `"C"`       | Inflection point (EC₅₀)                             |
| D         | `"D"`       | Top asymptote (signal at concentration → ∞)         |

By fixing A = 0, the model assumes that the background signal (at zero analyte concentration)
is negligible or has been subtracted. This reduces the number of free parameters from 4 to 3,
which can stabilise the fit when calibrators do not span a low-enough concentration to
independently estimate A.

#### Regression method

Levenberg-Marquardt with analytic Jacobian.

**Initial guess:**

```
B₀ = 1.0
C₀ = √(xMin × xMax)
D₀ = max(signal values) × 1.1    — slightly above maximum to ensure D > observed signals
```

**Minimum calibration points required:** 3.

#### Inverse formula

```
x = C × (y / (D − y))^(1/B)
```

Validity constraint: `0 < y < D`.

```java
// Back-interpolation — LogLogistic3PFitter.interpolate()
return c * Math.pow(signal / (d - signal), 1.0 / b);
```

**When to use:** Assays where the blank/background signal is zero or negligible after
background subtraction. Reduces parameter count and can improve fit stability when
low-concentration calibrators are absent.

---

### 2.4 Linear

**CurveType enum constant:** `LINEAR`  
**Implementation class:** `LinearFitter`  
**File:** `src/main/java/it/elismart_lims/service/curve/LinearFitter.java`

#### Forward model

```
y = m × x + q
```

| Parameter | Key in JSON | Physical meaning    |
|-----------|-------------|---------------------|
| m         | `"m"`       | Slope               |
| q         | `"q"`       | Intercept (y-axis)  |

#### Regression method

Ordinary least squares (OLS) via Apache Commons Math 3 `SimpleRegression`.

**Minimum calibration points required:** 2.

#### Inverse formula

```
x = (y − q) / m
```

The inverse is undefined when m ≈ 0 (flat calibration curve), which indicates a degenerate
assay. The software raises `IllegalArgumentException` when |m| < 10⁻¹².

```java
// LinearFitter.interpolate()
return (signal - q) / m;
```

**When to use:** Assays with a narrow dynamic range where the dose-response relationship is
linear. Rare in ELISA; more common in colorimetric or turbidimetric assays. R² and residual
analysis should confirm linearity before using this model.

---

### 2.5 Semi-Log Linear

**CurveType enum constant:** `SEMI_LOG_LINEAR`  
**Implementation class:** `SemiLogLinearFitter`  
**File:** `src/main/java/it/elismart_lims/service/curve/SemiLogLinearFitter.java`

#### Forward model

```
y = m × ln(x) + q
```

> **Note:** The x-axis transformation uses the **natural logarithm** (ln, base e), not log₁₀.
> Parameters m and q are fitted on the ln(x) scale.

| Parameter | Key in JSON | Physical meaning                             |
|-----------|-------------|----------------------------------------------|
| m         | `"m"`       | Slope on the natural-log concentration axis  |
| q         | `"q"`       | Intercept                                    |

#### Regression method

The concentration values are log-transformed (`Math.log`) before applying OLS regression
via `SimpleRegression`. All concentrations must be strictly positive (ln is undefined at 0).

**Minimum calibration points required:** 2.

#### Inverse formula

```
ln(x) = (y − q) / m   →   x = exp((y − q) / m)
```

```java
// SemiLogLinearFitter.interpolate()
return Math.exp((signal - q) / m);
```

**When to use:** Assays where the signal responds linearly to the logarithm of concentration.
This is a common empirical approximation for the linear portion of a sigmoidal curve. Appropriate
as an alternative to 4PL when only the mid-range of the sigmoid is used and a simpler model
is preferred.

**Constraint:** All calibrator concentrations must be > 0. The fit method raises
`IllegalArgumentException` if a zero or negative concentration is encountered.

---

### 2.6 Point-to-Point

**CurveType enum constant:** `POINT_TO_POINT`  
**Implementation class:** `PointToPointFitter`  
**File:** `src/main/java/it/elismart_lims/service/curve/PointToPointFitter.java`

#### Model

No global mathematical equation is fitted. The calibration table is stored verbatim (sorted
ascending by concentration) and back-interpolation uses **piecewise linear interpolation**
between adjacent calibration points.

#### Parameters stored

The `CurveParameters` map stores the full calibration table:

| Key     | Value                                           |
|---------|-------------------------------------------------|
| `"n"`   | Number of calibration points                    |
| `"x_i"` | Concentration of the i-th point (0-indexed)     |
| `"y_i"` | Signal of the i-th point (0-indexed)            |

Points are sorted ascending by concentration before storage.

**Minimum calibration points required:** 2.

#### Inverse formula (per segment)

For each pair of adjacent calibration points (x_i, y_i) and (x_{i+1}, y_{i+1}) that brackets
the target signal, the back-calculated concentration is:

```
x = x_i + (signal − y_i) × (x_{i+1} − x_i) / (y_{i+1} − y_i)
```

The algorithm locates the bracketing segment based on the signal value. The curve is assumed
to be monotonic over the calibration range.

```java
// PointToPointFitter.interpolate() — per-segment linear interpolation
double dy = ys[i + 1] - ys[i];
return xs[i] + (signal - ys[i]) * (xs[i + 1] - xs[i]) / dy;
```

**Special case (flat segment):** When `|y_{i+1} − y_i| < 10⁻¹²` (numerically flat), the
midpoint concentration `(x_i + x_{i+1}) / 2` is returned to avoid division by zero.

**Limitations:**

- No extrapolation beyond the range of calibrators — signals outside `[min(y), max(y)]`
  raise `IllegalArgumentException`.
- Sensitive to individual outlier calibration points (no smoothing).
- Not differentiable at the knot points.

**When to use:** Last resort when no parametric model provides an acceptable fit. Point-to-point
interpolation is **not recommended** for regulatory submissions (21 CFR Part 11, EMA bioanalytical
guideline) because it does not provide a closed-form equation that can be verified independently.

---

## 3. Outlier Detection

`OutlierDetectionService` identifies `MeasurementPair` records that should be excluded from
the pass/fail evaluation before `ValidationEngine.evaluate()` is called. Two sequential
criteria are applied.

**File:** `src/main/java/it/elismart_lims/service/validation/OutlierDetectionService.java`

---

### 3.1 Percent CV Threshold

**Criterion:** A pair is flagged as a within-pair outlier if its `cvPct` exceeds the
protocol's `maxCvAllowed` threshold.

```
Pair is flagged as outlier  ⟺  cvPct > maxCvAllowed
```

This criterion is **always applied first**, regardless of group size. It directly reflects
within-pair imprecision and is the appropriate test for n = 2 replicates.

```java
// OutlierDetectionService.detectOutliers() — Criterion 1
for (MeasurementPair pair : pairs) {
    if (pair.getCvPct() != null && pair.getCvPct() > protocol.getMaxCvAllowed()) {
        outlierIds.add(pair.getId());
    }
}
```

**Practical note:** With n = 2 replicates, this is the only statistically valid within-pair
outlier criterion. The Grubbs test (§3.2) operates across pairs and is not applicable to
the two raw signals within a single pair.

---

### 3.2 Grubbs Test (between-pair)

The Grubbs test detects a **between-pair** outlier: a pair whose `signalMean` is anomalous
relative to other pairs measured at the same concentration level.

**Test statistic:**

```
G = |x_suspect − mean| / SD
```

Where:

- `x_suspect` — `signalMean` of the candidate pair (the one with the largest absolute
  deviation from the group mean)
- `mean` — arithmetic mean of `signalMean` across all non-flagged pairs in the group
- `SD` — sample standard deviation of `signalMean` across the group:
  ```
  SD = √( Σ(xi − mean)² / (n − 1) )
  ```

The pair with the maximum `|signalMean − mean|` is the candidate. If G exceeds the
critical value for the group size at α = 0.05 (two-sided), that pair is flagged.

**Grouping:** Pairs are grouped by `(pairType, concentrationNominal)`. The test is applied
independently within each group. Only non-flagged pairs (not already caught by the CV criterion)
are included in groups.

**Critical values** at α = 0.05 (two-sided), sourced from Grubbs (1969) and ISO 5725-2 Annex A:

| n   | Critical value |
|-----|----------------|
| 3   | 1.155          |
| 4   | 1.481          |
| 5   | 1.715          |
| 6   | 1.887          |
| 7   | 2.020          |
| 8   | 2.126          |
| 9   | 2.215          |
| 10  | 2.290          |
| >10 | 2.290 (conservative) |

**Applicability constraint:** The test requires **n ≥ 3 pairs per group**. Groups with fewer
than 3 pairs are skipped; the CV criterion is the sole applicable test for those groups.

> **Known limitation for n = 3:** The theoretical maximum of the Grubbs statistic for three
> observations is (n−1)/√n = 2/√3 ≈ 1.1547, which is effectively equal to the α = 0.05
> critical value of 1.155. In practice the Grubbs test cannot flag an outlier in a group of
> exactly 3 pairs. It becomes effective from n = 4 onward.

```java
// OutlierDetectionService.applyGrubbsTest()
double g = Math.abs(candidate.getSignalMean() - mean) / sd;
double critical = GRUBBS_CRITICAL_VALUES.getOrDefault(n, GRUBBS_CRITICAL_VALUES.get(10));
if (g > critical) {
    return candidate.getId();   // flagged
}
```

**Standards / References:** Grubbs (1969); ASTM E178 §6; ISO 5725-2:2019 Annex A.

---

## 4. Validation Engine Logic

The following pseudocode describes the complete validation workflow executed by
`ValidationEngine.evaluate()` and its upstream services.

**File:** `src/main/java/it/elismart_lims/service/validation/ValidationEngine.java`

```
INPUT:
  experiment   — Experiment entity with all MeasurementPairs loaded
  protocol     — Protocol entity (maxCvAllowed, maxErrorAllowed, curveType)
  curveParams  — CurveParameters produced by CurveFittingService.fitCurve()

PRECONDITION CHECK:
  if experiment has no CALIBRATION pairs → throw IllegalArgumentException
     (curve fitting cannot proceed without calibration data)

EVALUATION LOOP:
  allPass ← true
  for each pair in experiment.measurementPairs:

    if pair.pairType == CALIBRATION:
      skip  (calibrators define the curve; not evaluated against acceptance criteria)

    if pair.isOutlier == true:
      skip  (excluded from acceptance evaluation by the outlier detection step)

    // Back-interpolate concentration from signal
    interpolatedConc ← CurveFittingService.interpolateConcentration(
                         protocol.curveType, pair.signalMean, curveParams)

    // Compute %Recovery and persist it
    recoveryPct ← (interpolatedConc / pair.concentrationNominal) × 100
    pair.recoveryPct ← recoveryPct

    // CV acceptance criterion
    cvPass ← (pair.cvPct is null) OR (pair.cvPct ≤ protocol.maxCvAllowed)

    // Recovery acceptance criterion
    recoveryPass ← |recoveryPct − 100| ≤ protocol.maxErrorAllowed

    if NOT (cvPass AND recoveryPass):
      allPass ← false

OUTPUT:
  if allPass:
    experimentStatus ← OK
  else:
    experimentStatus ← KO
```

**Status semantics:**

| Status             | Meaning                                                                         |
|--------------------|---------------------------------------------------------------------------------|
| `OK`               | All evaluated (non-outlier, non-calibration) pairs passed both CV and recovery  |
| `KO`               | At least one evaluated pair failed CV or recovery (or both)                     |
| `VALIDATION_ERROR` | Curve fitting failed, insufficient calibration points, or numerical error       |

**Manual status override:** Changing status from `KO` to `OK` (or any non-programmatic change)
requires a `reason` string that is persisted in `audit_log`. This constitutes the electronic
justification required by 21 CFR Part 11 and EU GMP Annex 11.

---

## 5. Traceability and Audit

All calculated values and status changes are subject to the following audit rules:

| Event                                | Audit trail entry                                                                 |
|--------------------------------------|-----------------------------------------------------------------------------------|
| POST / PUT modifies `signalMean`     | Logged to `audit_log` with old value, new value, `changedBy` = authenticated user |
| POST / PUT modifies `cvPct`          | Logged to `audit_log` with old value, new value                                   |
| Validation run updates `recoveryPct` | Logged to `audit_log` for each modified pair                                      |
| `isOutlier` set by outlier detection | Logged with `changedBy = "SYSTEM:outlier-detection"`, `reason` = statistical rule applied |
| `isOutlier` set manually             | Logged with `changedBy` = authenticated user; `reason` field **mandatory**        |
| Experiment status changed to OK/KO   | Logged with `changedBy = "SYSTEM:validation-engine"`                              |
| Manual status override               | Logged with `changedBy` = authenticated user; `reason` field **mandatory**        |

**Client-supplied derived values are ignored:** `signalMean`, `cvPct`, and `recoveryPct` are
always computed server-side. Any value provided by the client in the request body for these
fields is discarded before persisting the entity.

**Re-fit scenario:** If the calibration curve is re-fitted after an initial validation
(e.g., after removing an outlier calibrator), all affected `recoveryPct` values are
recalculated and the updated values are written to `audit_log` with a reference to the
re-fit operation. The previous values are preserved in the audit trail.

---

## 6. References

1. **ISO 5725-2:2019** — *Accuracy (trueness and precision) of measurement methods and
   results — Part 2: Basic method for the determination of repeatability and reproducibility
   of a standard measurement method.* Geneva: International Organization for Standardization.
   (Defines %CV for duplicate measurements, §6.2.)

2. **CLSI EP15-A3** — *User Verification of Precision and Estimation of Bias; Approved
   Guideline — Third Edition.* Wayne, PA: Clinical and Laboratory Standards Institute, 2014.
   (User-level precision verification protocol for n=2 replicates in clinical laboratories.)

3. **ICH Q2(R2)** — *Validation of Analytical Procedures.* International Council for
   Harmonisation of Technical Requirements for Pharmaceuticals for Human Use, 2022.
   (Defines accuracy as %Recovery for analytical method validation; §3.2.)

4. **Findlay, J.W.A. & Dillard, R.F.** (2007). "Appropriate calibration curve fitting in
   ligand binding assays." *AAPS Journal*, 9(2), E260–E267.
   DOI: 10.1208/aapsj0902029. (Authoritative reference for 4PL and 5PL use in immunoassay
   validation; discusses asymptote constraints and back-interpolation.)

5. **ASTM E178-21** — *Standard Practice for Dealing With Outlying Observations.*
   West Conshohocken, PA: ASTM International, 2021.
   (Tabulates Grubbs critical values; §6 describes the single-outlier test statistic.)

6. **Grubbs, F.E.** (1969). "Procedures for detecting outlying observations in samples."
   *Technometrics*, 11(1), 1–21.
   (Original derivation of the Grubbs test statistic and critical value tables.)

7. **EMA** (2011). *Guideline on Bioanalytical Method Validation.* European Medicines Agency,
   EMEA/CHMP/EWP/192217/2009. (Recommends 4PL/5PL for calibration; §4.1.1 discusses
   acceptable regression models for immunoassay validation.)
