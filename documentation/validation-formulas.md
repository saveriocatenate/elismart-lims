# EliSmart LIMS — Validation Formulas and Calculation Specifications

**Version:** 2.0  
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
   - 2.0 [Weighted Least Squares (WLS) — default for nonlinear fitters](#20-weighted-least-squares-wls--default-for-nonlinear-fitters)
   - 2.0b [Data-Driven Initial Parameter Guess (B₀)](#20b-data-driven-initial-parameter-guess-b)
   - 2.1 [Four-Parameter Logistic (4PL)](#21-four-parameter-logistic-4pl)
   - 2.2 [Five-Parameter Logistic (5PL)](#22-five-parameter-logistic-5pl)
   - 2.3 [Three-Parameter Log-Logistic (3PL)](#23-three-parameter-log-logistic-3pl)
   - 2.4 [Linear](#24-linear)
   - 2.5 [Semi-Log Linear](#25-semi-log-linear)
   - 2.6 [Point-to-Point](#26-point-to-point)
3. [Outlier Detection](#3-outlier-detection)
   - 3.1 [Percent CV Threshold](#31-percent-cv-threshold)
   - 3.2 [Grubbs Test (between-pair) — iterative](#32-grubbs-test-between-pair--iterative)
4. [Validation Engine Logic](#4-validation-engine-logic)
5. [Numerical Safeguards](#5-numerical-safeguards)
   - 5.1 [NaN / Infinity Guard](#51-nan--infinity-guard)
   - 5.2 [Zero-Concentration Points in Non-Linear Fitting](#52-zero-concentration-points-in-non-linear-fitting)
   - 5.3 [Zero or Negative Nominal Concentration — %Recovery Skip Rule](#53-zero-or-negative-nominal-concentration--recovery-skip-rule)
   - 5.4 [Grubbs Degenerate Distribution Guard](#54-grubbs-degenerate-distribution-guard)
6. [Curve Fitting Convergence Metadata and Goodness-of-Fit](#6-curve-fitting-convergence-metadata-and-goodness-of-fit)
   - 6.1 [Convergence and Weighted RMS](#61-convergence-and-weighted-rms)
   - 6.2 [Goodness-of-Fit: R², Unweighted RMSE, and Degrees of Freedom](#62-goodness-of-fit-r-unweighted-rmse-and-degrees-of-freedom)
   - 6.3 [95% Confidence Interval on EC₅₀ (4PL parameter C)](#63-95-confidence-interval-on-ec-4pl-parameter-c)
7. [Traceability and Audit](#7-traceability-and-audit)
8. [References](#8-references)

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

### 2.0 Weighted Least Squares (WLS) — default for nonlinear fitters

All three nonlinear fitters (4PL, 5PL, 3PL) use **Weighted Least Squares** rather than ordinary
least squares. This is the regulatory-recommended approach for immunoassays, where signal variance
increases proportionally with signal magnitude (heteroscedastic noise).

#### Rationale

In a typical ELISA, the absolute instrument variability (pipetting noise, reader noise) scales
with the signal level. Fitting with uniform weights (OLS) over-penalises residuals at high-signal
calibrators, which distorts the fitted curve across the entire concentration range. WLS
down-weights high-signal points so that low-concentration calibrators — where analytical
sensitivity matters most — are fitted with the same relative influence as high-signal points.

#### Weight formula

```
wᵢ = 1 / signalᵢ²
```

Where `signalᵢ` is the `signalMean` of the i-th calibration point. This is the 1/y² weighting
scheme recommended in Findlay & Dillard (2007) and adopted in most ELISA analysis software.

**Fallback:** when `signalᵢ ≤ 0`, the weight is set to `1.0` to avoid division by zero or
negative weights. This prevents a blank calibrator (zero signal) from receiving infinite weight.

**Normalisation:** weights are not normalised before being passed to the Levenberg-Marquardt
optimizer — `DiagonalMatrix(weights)` is passed directly as the weight matrix.

```java
// CurveFitter.computeWeights() — used by FourPLFitter, FivePLFitter, LogLogistic3PFitter
static double[] computeWeights(List<CalibrationPoint> points) {
    double[] w = new double[points.size()];
    for (int i = 0; i < points.size(); i++) {
        double s = points.get(i).signal();
        w[i] = (s > 0) ? 1.0 / (s * s) : 1.0;
    }
    return w;
}
```

**Not applied to:** `LinearFitter`, `SemiLogLinearFitter` (OLS via `SimpleRegression`),
`PointToPointFitter` (non-parametric).

**Standards / References:** Findlay & Dillard (2007); EMA Guideline on Bioanalytical Method
Validation (2011), §4.1.1 — recommends weighted regression for calibration models when the
variance is not constant across the calibration range.

---

### 2.0b Data-Driven Initial Parameter Guess (B₀)

The Hill slope initial guess (B₀) for all nonlinear fitters (4PL, 5PL, 3PL) is computed from
the calibration data rather than set to a fixed constant of 1.0.

#### Formula

The Hill slope is estimated from the 10th-percentile and 90th-percentile concentration values
that bracket the linear region of the sigmoid:

```
B₀ = |ln(81) / ln(x₉₀ / x₁₀)|
```

Where `x₁₀` is the concentration at 10% of the signal range above the minimum, and `x₉₀` is
the concentration at 90% of the signal range. The factor `ln(81) = ln(9²) = 2 × ln(9)` is
derived from the 4PL inverse: when y = 10% above floor and y = 90% of span, the ratio
`(A − D)/(y − D) − 1` evaluates to 9 at both ends, and their ratio gives slope directly.

**Clamp:** the computed estimate is clamped to `[0.1, 10.0]` to prevent degenerate starting
points. If the formula produces NaN or Infinity (e.g., too few distinct concentration levels),
the fallback value `1.0` is used.

```java
// CurveFitter.estimateHillSlope() — called by FourPLFitter, FivePLFitter, LogLogistic3PFitter
double b0 = Math.abs(Math.log(81.0) / Math.log(x90 / x10));
if (Double.isNaN(b0) || Double.isInfinite(b0)) return 1.0;
b0 = Math.max(0.1, Math.min(b0, 10.0));
```

**Effect:** a data-driven B₀ closer to the true slope reduces the number of Levenberg-Marquardt
iterations to convergence and lowers the probability of the optimizer finding a local minimum
for shallow or steep sigmoid curves.

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

**Weighted Least Squares** via the **Levenberg-Marquardt algorithm** (Apache Commons Math 3,
`LevenbergMarquardtOptimizer`) with 1/y² weights (see §2.0). An analytic Jacobian is provided
to improve convergence speed and numerical stability.

**Initial parameter guess (data-driven):**

```
A₀ = min(signal values)                          — bottom asymptote estimate
D₀ = max(signal values)                          — top asymptote estimate
C₀ = √(xMin × xMax)                              — geometric mean of concentration range
B₀ = estimateHillSlope(xData, yData)             — data-driven slope (see §2.0b; clamp [0.1, 10])
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

**Weighted Least Squares** via Levenberg-Marquardt with analytic Jacobian and 1/y² weights
(see §2.0), identical library as 4PL.

**Initial parameter guess:**

```
A₀ = min(signal values)
D₀ = max(signal values)
C₀ = √(xMin × xMax)
B₀ = estimateHillSlope(xData, yData)    — data-driven (see §2.0b)
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

**Weighted Least Squares** via Levenberg-Marquardt with analytic Jacobian and 1/y² weights
(see §2.0).

**Initial guess:**

```
B₀ = estimateHillSlope(xData, yData)    — data-driven (see §2.0b)
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

### 3.2 Grubbs Test (between-pair) — iterative

The Grubbs test detects **between-pair** outliers: pairs whose `signalMean` is anomalous
relative to other pairs measured at the same concentration level.

#### Algorithm — iterative Grubbs

The test is applied **iteratively** within each group (one outlier removed per pass) until
no further outlier is detected or the active group shrinks below the minimum size (n = 3).

**Iteration pseudocode:**

```
activeGroup ← copy of group
do:
    candidate ← pair with maximum |signalMean − mean(activeGroup)|
    G          ← |signalMean_candidate − mean| / SD
    if G > critical(|activeGroup|):
        flag candidate as outlier
        remove candidate from activeGroup
        iteration++
    else:
        stop
while flagged AND |activeGroup| ≥ 3
```

At each pass, `mean` and `SD` are **recomputed from the current active group** (excluding
previously flagged pairs). The critical value is also re-looked-up for the updated group size.
This ensures that masking effects — where one extreme outlier suppresses the detectability
of a second — are resolved across passes.

**Test statistic (per pass):**

```
G = |x_suspect − mean| / SD
```

Where:

- `x_suspect` — `signalMean` of the candidate pair (maximum absolute deviation from the group mean)
- `mean` — arithmetic mean of `signalMean` across the current active group
- `SD` — sample standard deviation of `signalMean` across the current active group:
  ```
  SD = √( Σ(xi − mean)² / (n − 1) )
  ```

**Grouping:** Pairs are grouped by `(pairType, concentrationNominal)`. The test is applied
independently within each group. Only pairs not already flagged by the %CV criterion are
included.

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
than 3 pairs are skipped; the CV criterion is the sole applicable test for those groups. The
iterative loop also stops when the active group would drop below 3 after removing an outlier.

> **Known limitation for n = 3:** The theoretical maximum of the Grubbs statistic for three
> observations is (n−1)/√n = 2/√3 ≈ 1.1547, which is effectively equal to the α = 0.05
> critical value of 1.155. In practice the Grubbs test cannot flag an outlier in a group of
> exactly 3 pairs. It becomes effective from n = 4 onward.

```java
// OutlierDetectionService.applyGrubbsTestIterative() — simplified
do {
    flaggedId = runSingleGrubbsPass(activeGroup, groupKey, iteration);
    if (flaggedId != null) {
        outlierIds.add(flaggedId);
        activeGroup.removeIf(p -> p.getId().equals(flaggedId));
        iteration++;
    }
} while (flaggedId != null && activeGroup.size() >= GRUBBS_MIN_GROUP_SIZE);
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

---

## 5. Numerical Safeguards

The rules in this section apply to every arithmetic operation inside fitter classes
(`FourPLFitter`, `FivePLFitter`, `LogLogistic3PFitter`, etc.) and the validation engine.
They prevent IEEE 754 exceptional values (`NaN`, `Infinity`, `-Infinity`) from propagating
into persisted columns or audit log entries.

---

### 5.1 NaN / Infinity Guard

Every `Math.pow()`, division, or `Math.log()` call whose result will be persisted or used
as the input to another arithmetic operation is followed by an explicit guard:

```java
if (Double.isNaN(result) || Double.isInfinite(result))
    throw new IllegalArgumentException(
        "<operation> produced NaN/Infinite: input_a=<val>, input_b=<val>");
```

**Where applied:**
- Back-interpolation (`interpolate()`) in all fitter classes (4PL, 5PL, 3PL, Semi-log,
  Point-to-Point).
- Any intermediate ratio, exponentiation, or logarithm whose output feeds into the final
  concentration estimate.

**Error message contract:** the exception message must include the input values so the
cause is diagnosable from logs alone, without a debugger or reproducing the dataset.

**Implementation examples:**
- `FourPLFitter.interpolate()` — line ~248
- `FivePLFitter.interpolate()` — line ~277
- `LogLogistic3PFitter.interpolate()` — line ~214
- `SemiLogLinearFitter.interpolate()` — line ~88
- `PointToPointFitter.interpolate()` — line ~113

---

### 5.2 Zero-Concentration Points in Non-Linear Fitting

Non-linear sigmoidal models (4PL, 5PL, 3PL) use a log-transform of concentration internally
during Levenberg-Marquardt optimisation. A calibration point with `concentrationNominal = 0`
would require evaluating `Math.log(0) = -Infinity`, which corrupts the Jacobian and halts
convergence.

**Rule:** before entering the optimisation loop, the fitter silently filters out any
calibration point whose concentration is `≤ 0`:

```java
List<CalibrationPoint> validPoints = points.stream()
    .filter(p -> p.concentration() > 0)
    .toList();
if (validPoints.isEmpty()) {
    throw new IllegalArgumentException("No calibration points with concentration > 0 ...");
}
```

A **WARN**-level log entry is produced for each excluded zero-concentration point so that
analysts can investigate whether the blank was intentionally included or entered in error.

**Fitters affected:** `FourPLFitter`, `FivePLFitter`, `LogLogistic3PFitter`.
**Not affected:** `LinearFitter`, `SemiLogLinearFitter` (no log-transform of x in fitting),
`PointToPointFitter` (non-parametric, no optimisation loop).

---

### 5.3 Zero or Negative Nominal Concentration — %Recovery Skip Rule

`ValidationEngine.evaluate()` computes %Recovery as:

```
%Recovery = (interpolatedConc / concentrationNominal) × 100
```

If `concentrationNominal` is `null` or `≤ 0`, this division is either undefined or
physically meaningless (a blank well with nominal concentration 0 is not a quality control).

**Rule:**

```java
if (pair.getConcentrationNominal() == null || pair.getConcentrationNominal() <= 0) {
    log.warn("Pair id={} has nominal concentration <= 0, recovery check skipped", pair.getId());
    pair.setRecoveryPct(null);
    // pair is not penalised for recovery — recoveryPass = true by default
}
```

The pair still participates in the **%CV** evaluation. It is only exempted from the
recovery criterion. The `PairValidationResult.calculatedRecovery` field is `null` for
these pairs and the `recoveryPass` flag is `true`.

**Practical cases:**
- A blank well (zero-standard) included in the CONTROL or SAMPLE group for background
  subtraction purposes.
- A SAMPLE pair where the operator left `concentrationNominal` empty.

---

### 5.4 Grubbs Degenerate Distribution Guard

The Grubbs between-pair outlier test divides by the sample standard deviation of the group:

```
G = |x_suspect − mean| / SD
```

When all `signalMean` values in a group are identical, `SD = 0` and this division is
undefined.

**Rule:** the test uses a near-zero threshold rather than exact equality to account for
floating-point rounding:

```java
if (Math.abs(sd) < 1e-12) {
    // Degenerate: all replicates are identical. No outlier — return empty.
    return Optional.empty();
}
```

This threshold (`1e-12`) is deliberately much smaller than any physically meaningful SD
in a laboratory instrument context (typical OD readings range from 0.001 to 4.0; an SD
of 1e-12 is indistinguishable from machine epsilon at that scale).

**Consequence:** a perfectly reproducible group is treated as having no outlier. If all
pairs in a group read exactly the same signal, the CV criterion (§3.1) will also return
0% — both tests agree the group is clean.

---

## 6. Curve Fitting Convergence Metadata and Goodness-of-Fit

Non-linear fitters (4PL, 5PL, 3PL) append diagnostic metadata keys to the `CurveParameters`
map after a successful fit. These keys use the `_` prefix to distinguish them from model
parameters (e.g. `"A"`, `"B"`, `"C"`).

**File:** `src/main/java/it/elismart_lims/service/curve/CurveParameters.java`

| Key constant | JSON key | Fitters | Meaning |
|---|---|---|---|
| `META_CONVERGENCE` | `_convergence` | 4PL, 5PL, 3PL | `1.0` = converged; `0.0` = did not converge |
| `META_RMS` | `_rms` | 4PL, 5PL, 3PL | Weighted RMS residual from LM optimizer |
| `META_R2` | `_r2` | 4PL, 5PL, 3PL | Unweighted coefficient of determination R² |
| `META_RMSE` | `_rmse` | 4PL, 5PL, 3PL | Unweighted RMSE (signal units) |
| `META_DF` | `_df` | 4PL, 5PL, 3PL | Degrees of freedom (n − p) |
| `META_EC50_LOWER95` | `_ec50_lower95` | 4PL only | 95% CI lower bound on EC₅₀ (parameter C) |
| `META_EC50_UPPER95` | `_ec50_upper95` | 4PL only | 95% CI upper bound on EC₅₀ (parameter C) |

**Linear models** (`LinearFitter`, `SemiLogLinearFitter`) do not include these keys because
OLS regression always produces a unique solution with no iterative convergence.
`PointToPointFitter` does not include them because it stores calibration point arrays, not
a converged parametric fit.

All metadata keys are accessible via `GET /api/experiments/{id}` in the `curveParameters`
field of the JSON response. They are stored for diagnostic and auditing purposes — none of
them feed directly into the pass/fail decision.

---

### 6.1 Convergence and Weighted RMS

**`_convergence`** — the fitter throws `IllegalStateException` (propagated as
`VALIDATION_ERROR`) if the Levenberg-Marquardt optimizer raises a `ConvergenceException`.
In practice `_convergence = 0.0` should never appear in a stored `CurveParameters`; it is
written defensively as a second-level guard read by `ExperimentService`:

```java
double convergenceFlag = curveParams.values()
    .getOrDefault(CurveParameters.META_CONVERGENCE, 1.0);
if (convergenceFlag == 0.0) {
    // experiment status → VALIDATION_ERROR
}
```

**`_rms`** — the WLS residual from the Levenberg-Marquardt final iteration. It reflects the
fit quality on the **weighted** scale (residuals divided by σ = 1/wᵢ). A high value indicates
that the WLS objective was not well-minimised; a low value does not by itself mean a good fit
in signal units (see `_rmse` below for an unweighted assessment).

---

### 6.2 Goodness-of-Fit: R², Unweighted RMSE, and Degrees of Freedom

These three metrics are computed **after** the WLS optimizer converges, using **unweighted**
residuals between the observed calibration signals and the signals predicted by the fitted
model. They quantify how well the model explains the calibration data on the original signal
scale.

**File:** `CurveFitter.computeGoodnessOfFit(List<CalibrationPoint>, double[])` — called by
all three nonlinear fitters after storing the LM result.

#### Degrees of Freedom (df)

```
df = n − p
```

Where `n` = number of valid calibration points, `p` = number of free parameters in the model
(4 for 4PL, 5 for 5PL, 3 for 3PL). Stored as `_df`.

When `df ≤ 0`, R² and RMSE are set to `null` — the system is exactly or over-determined and
the metrics are undefined.

#### Unweighted RMSE

```
RMSE = √( Σ(yᵢ_observed − yᵢ_predicted)² / n )
```

Units are the same as the signal axis (e.g., absorbance units). Stored as `_rmse`.

**Interpretation:** RMSE expresses the average absolute prediction error in signal units. A
value below 1–5% of the total signal range is typically indicative of an acceptable fit. High
RMSE (> 10% of range) should prompt the analyst to inspect the calibration data for outlier
calibrators or reagent degradation.

#### Coefficient of Determination (R²)

```
SS_res = Σ(yᵢ_observed − yᵢ_predicted)²
SS_tot = Σ(yᵢ_observed − ȳ)²             where ȳ = mean(yᵢ_observed)
R²     = 1 − SS_res / SS_tot
```

R² is dimensionless. Stored as `_r2`.

**Interpretation:** R² = 1.0 is a perfect fit; R² < 0 means the model is worse than the
horizontal line at ȳ. For immunoassay calibration, R² ≥ 0.99 is typically acceptable; values
below 0.99 should be investigated.

> **Important:** R² is an unweighted measure. Because the WLS optimizer intentionally
> down-weights high-signal points, a high R² does not guarantee good WLS convergence, and
> vice versa. Both `_rms` (weighted) and `_r2` / `_rmse` (unweighted) should be reviewed
> together.

```java
// CurveFitter.computeGoodnessOfFit() — simplified
double ssRes = IntStream.range(0, n).mapToDouble(i ->
    Math.pow(obs[i] - pred[i], 2)).sum();
double ssTot = IntStream.range(0, n).mapToDouble(i ->
    Math.pow(obs[i] - mean, 2)).sum();
double r2   = (ssTot < 1e-12) ? null : 1.0 - ssRes / ssTot;
double rmse = Math.sqrt(ssRes / n);
```

---

### 6.3 95% Confidence Interval on EC₅₀ (4PL parameter C)

The 95% confidence interval on the EC₅₀ (inflection point C of the 4PL model) is derived
from the asymptotic covariance matrix of the Levenberg-Marquardt solution.

**Applicability:** 4PL fitter only. Requires `df ≥ 1` (at least 5 calibration points for a
4-parameter model). Stored as `_ec50_lower95` and `_ec50_upper95`.

#### Formula

```
SE(C) = √( σ² × [J^T W J]⁻¹ ₍C,C₎ )

CI₉₅ = C ± t(df, 0.025) × SE(C)
```

Where:

- `J` — Jacobian matrix of the 4PL model evaluated at the converged parameter vector
  (n × 4, one row per calibration point, one column per parameter A/B/C/D).
- `W` — diagonal weight matrix (`wᵢ = 1/signalᵢ²`, same as the fitting weights).
- `[J^T W J]⁻¹` — Fisher information matrix inverse (approximate covariance matrix at the
  solution). The `(C, C)` element (index `[2][2]`) is the variance of the C estimate.
- `σ²` — scale factor: `σ² = weighted_rms² × n / df`. This re-scales the Fisher information
  matrix from the normalised LM space back to signal-variance units.
- `t(df, 0.025)` — Student's t critical value at two-tailed α = 0.05 for `df` degrees of
  freedom, computed via `TDistribution(df).inverseCumulativeProbability(0.975)`.

**Failure modes:** if the covariance matrix is singular (near-zero determinant — occurs when
calibration points provide insufficient curvature information), both bounds are set to `null`
and a WARN-level log entry is produced. The experiment proceeds normally; the CI is simply
not available for that run.

```java
// FourPLFitter — EC50 CI (simplified)
double df       = xData.length - 4;
double sigma2   = weightedRms * weightedRms * xData.length / df;
double varC     = sigma2 * covMatrix[2][2];   // (C, C) element
double seC      = Math.sqrt(varC);
double tValue   = new TDistribution(df).inverseCumulativeProbability(0.975);
double lower95  = fitted[2] - tValue * seC;
double upper95  = fitted[2] + tValue * seC;
```

**How to read the CI:** If `_ec50_lower95 = 0.25` and `_ec50_upper95 = 0.40` with units
ng/mL, there is 95% probability that the true EC₅₀ lies between 0.25 and 0.40 ng/mL under
the WLS model assumptions. A narrow CI indicates a well-defined inflection point; a wide CI
(spanning more than an order of magnitude on the concentration axis) indicates insufficient
calibrators around the sigmoid midpoint.

**Standards / References:** Seber & Wild (2003), *Nonlinear Regression* §5.1 (asymptotic
standard errors); Motulsky & Christopoulos (2004), *Fitting Models to Biological Data* §20.

---

## 7. Traceability and Audit

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

## 8. References

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
