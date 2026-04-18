# EliSmart LIMS — User Guide

> **Audience:** Laboratory scientists and technicians who use EliSmart LIMS day-to-day.
> No programming knowledge is required to follow this guide.

---

## Table of Contents

1. [What is EliSmart LIMS?](#1-what-is-elismart-lims)
2. [Roles and Permissions](#2-roles-and-permissions)
3. [Logging In](#3-logging-in)
4. [The Dashboard — Your Starting Point](#4-the-dashboard--your-starting-point)
5. [One-Time Lab Setup](#5-one-time-lab-setup)
   - 5.1 [Adding Reagents to the Catalog](#51-adding-reagents-to-the-catalog)
   - 5.2 [Defining a Protocol](#52-defining-a-protocol)
6. [Running an Experiment](#6-running-an-experiment)
   - 6.1 [Creating the Experiment Record](#61-creating-the-experiment-record)
   - 6.2 [Entering Measurement Data Manually](#62-entering-measurement-data-manually)
   - 6.3 [Importing Data from a Plate Reader (CSV)](#63-importing-data-from-a-plate-reader-csv)
7. [Understanding Automated Validation](#7-understanding-automated-validation)
8. [Reviewing Experiment Results](#8-reviewing-experiment-results)
9. [Editing an Experiment Record](#9-editing-an-experiment-record)
10. [Searching and Filtering Experiments](#10-searching-and-filtering-experiments)
11. [Comparing Experiments Side by Side](#11-comparing-experiments-side-by-side)
12. [AI-Powered Analysis](#12-ai-powered-analysis)
13. [Exporting Data and Reports](#13-exporting-data-and-reports)
14. [Reagent Batch Management and Expiry Alerts](#14-reagent-batch-management-and-expiry-alerts)
15. [User Management (Administrators Only)](#15-user-management-administrators-only)
16. [Status Reference](#16-status-reference)
17. [EliSmart and Your Daily Work](#17-elismart-and-your-daily-work)

---

## 1. What is EliSmart LIMS?

EliSmart LIMS (Laboratory Information Management System) is a web-based platform that centralises all the information produced during your laboratory assay runs. It stores your experimental data, performs quality calculations automatically, and keeps a complete, tamper-evident history of every change — so you never have to worry about traceability or paper trails again.

At the heart of the system are three concepts you will work with every day:

| Concept | What it means |
|---|---|
| **Reagent** | A kit component or consumable you register once in the system catalog. Every physical lot you receive gets its own batch record. |
| **Protocol** | A recipe for a specific assay type — it captures the calibration curve model, the number of calibrator and control replicates, and the acceptance criteria (maximum allowed %CV and %Recovery error). |
| **Experiment** | A single run of a protocol in the lab. It links the measurement signals from that day to the reagent lots actually used, and it holds the automatically calculated quality metrics. |

---

## 2. Roles and Permissions

Every user account has one of three roles. Your role determines what you can create, edit, or approve.

| Role | What you can do |
|---|---|
| **Analyst** | Create and edit experiments; enter or import measurement data; export results. |
| **Reviewer** | Everything an Analyst can do, plus approve or reject experiments (transition them to the final OK or KO status). |
| **Administrator** | Everything a Reviewer can do, plus manage user accounts, create and edit Protocols, manage the Reagent Catalog, and delete records. |

If you are unsure of your role, open the sidebar — your username and role are displayed at the bottom.

---

## 3. Logging In

Open the EliSmart LIMS URL in your browser. You will be presented with a login form. Enter your **username** and **password** provided by your administrator, then click **Log in**.

After a successful login you are taken directly to the **Dashboard**.

> **Session security:** Your session remains active as long as your browser window is open. To log out explicitly, click the **Logout** button in the left sidebar at any time.

---

## 4. The Dashboard — Your Starting Point

The Dashboard is the first page you see after logging in. It gives you an instant summary of the lab's current state:

- **Total protocols** defined in the system.
- **Total experiments** recorded.
- **Experiments validated this month** — how many passed (OK) and how many failed (KO).
- **Reagent expiry alerts** — any kit lot expiring within the next 90 days is listed here with a yellow or red highlight.

From the Dashboard you can navigate to every section of the application using the clearly labelled buttons in the centre of the page.

---

## 5. One-Time Lab Setup

The following two steps are usually performed once by an Administrator when a new assay type is introduced in the lab. Day-to-day users can skip ahead to [Section 6](#6-running-an-experiment).

### 5.1 Adding Reagents to the Catalog

Before a protocol can require a reagent, that reagent must exist in the **Reagent Catalog**.

1. From the Dashboard, click **Add Reagent**.
2. Enter the reagent **name** and the **manufacturer** name.
3. Optionally add a description.
4. Click **Save**.

The reagent is now available for use in protocols and for batch registration. You only register a reagent type once; physical kit lots (with lot numbers and expiry dates) are registered separately each time a new kit arrives (see [Section 14](#14-reagent-batch-management-and-expiry-alerts)).

### 5.2 Defining a Protocol

A protocol captures the full acceptance criteria for an assay type. It must be set up before any experiment can be recorded against it.

1. From the Dashboard, click **New Protocol**.
2. Fill in the following fields:

   | Field | What to enter |
   |---|---|
   | **Name** | A descriptive name, e.g. "ELISA Human IL-6 Serum". |
   | **Curve type** | The mathematical model used to fit the calibration curve. Common choices are **4PL** (four-parameter logistic, standard for most ELISAs) and **Linear** (for simpler assays). Hover over the info icon for a brief description of each. |
   | **Number of calibrators** | How many concentration levels appear in your standard curve. |
   | **Number of controls** | How many control wells you run per plate. |
   | **Max %CV allowed** | The upper limit for the coefficient of variation across replicate signals. Pairs exceeding this threshold are flagged automatically. |
   | **Max %Recovery error allowed** | The maximum acceptable deviation from the nominal concentration, expressed as a percentage. |

3. In the **Required Reagents** section, add each kit component that must be documented when this protocol is run. Click **Add reagent row**, select a reagent from the dropdown (or type its name), then specify the required quantity. Repeat for each component.
4. Click **Create Protocol**.

> **Note:** Protocol reagent requirements cannot be changed after creation. If your assay procedure changes significantly, create a new protocol version with a distinct name.

---

## 6. Running an Experiment

This is the most frequent operation in EliSmart LIMS. Each time you run an assay on the bench, you create one Experiment record.

### 6.1 Creating the Experiment Record

1. From the Dashboard, click **New Experiment**.
2. Select the **Protocol** that matches the assay you ran. The system will immediately load the required reagents and the expected number of measurement pairs for that protocol.
3. Enter an **experiment name** (e.g. "Run 2024-11-15 Batch A") and the **date and time** the assay was performed.
4. For each required reagent shown, enter or select the **lot number** of the kit actually used. If the lot has been registered before, you can select it from the dropdown; if it is a new delivery, click **Register new batch** and fill in the lot number, supplier, and expiry date.

> **Note on status:** when creating an experiment the only valid status is **Pending**. The statuses **OK**, **KO**, and **Validation Error** are set automatically by the system after you save measurement data — they are never shown as choices in the creation form.

### 6.2 Entering Measurement Data Manually

After the header is filled in, a table of **measurement pairs** appears — one row for each calibrator, control, and sample well, depending on the protocol.

For each row:
- Enter **Signal 1** and **Signal 2** (the duplicate absorbance readings for that well).
- Optionally enter the **nominal concentration** for calibrators and controls.

As you type, the system instantly calculates the **%CV** (precision) for that pair and shows it in green (acceptable) or red (above the protocol limit). This gives you immediate feedback while you are still at the bench.

When all values are entered, click **Save Experiment**.

### 6.3 Importing Data from a Plate Reader (CSV)

If your plate reader software can export results as a CSV file (Tecan Magellan, BioTek Gen5, and Molecular Devices SoftMax Pro are natively supported), you can skip manual entry entirely.

1. After filling in the experiment header and reagent lots (steps 1–4 above), select the **Import from CSV** tab.
2. Upload your CSV file.
3. A well-mapping interface will appear. For each well, select a **pair type** (Calibrator, Control, or Sample) and enter the nominal concentration for calibration points. You can assign multiple wells to the same pair type in a single step using the **bulk-assign** control — select several wells at once, choose their pair type and nominal concentration, and click **Apply to selected** to set all of them together.
4. Click **Import**. The system parses the file, maps the signals to measurement pairs, and calculates all quality metrics automatically.
5. Review the imported pairs and click **Save Experiment**.

> **Tip:** If your CSV format is not recognised automatically, contact your administrator to configure a custom column mapping.

> **Signal validation:** the system rejects any CSV row that contains a **negative signal value** (e.g. `-0.050`). Optical density readings must be zero or positive. If one or more rows contain invalid values, the import fails and an error message identifies every offending row and value before any data is saved. Correct the CSV file and re-upload.

---

## 7. Understanding Automated Validation

When you save an experiment with the status set to **Completed**, EliSmart LIMS runs an automated validation pass. You do not need to trigger this manually — it happens in the background when the data is saved.

The validation engine performs the following steps:

1. **Curve fitting** — It fits a calibration curve to your Calibrator pairs using the model defined in the protocol (4PL, Linear, etc.).
2. **Back-calculation** — For each Control and Sample pair, the system calculates the concentration from the fitted curve and computes **%Recovery** (how close the back-calculated concentration is to the nominal value).
3. **Outlier detection** — Pairs with a %CV above the protocol limit are automatically flagged as statistical outliers and excluded from the final pass/fail assessment.
4. **Accept/Reject decision** — Each remaining pair is compared against the protocol limits. If all pairs pass, the experiment status is set to **OK**. If any pair fails, the status is set to **KO**.

The status **Validation Error** appears only if the system could not complete the calculation — for example, if there are no calibration points to build the curve. In that case, check your data and re-save.

---

## 8. Reviewing Experiment Results

Click on any experiment name in the search results (or use the **Go to Details** button after saving) to open the **Experiment Details** page.

This page shows:

- **Experiment header** — name, date, protocol, operator, and current status.
- **Status badge** — a colour-coded icon tells you at a glance whether the run passed or failed.
- **Reagent lots used** — the full traceability record of every kit component.
- **Protocol details** (expandable) — the acceptance criteria that were applied.
- **Results table** — each measurement pair is listed with its raw signals, calculated mean, %CV, and %Recovery. Each row is colour-coded:
  - **Green** — the pair passed all criteria.
  - **Yellow** — the pair is borderline or flagged as an outlier.
  - **Red** — the pair failed one or more criteria.

> **Understanding the colour coding:** A green row means both %CV and %Recovery are within the protocol limits. A red row means at least one value is out of range. Outlier pairs are shown with a strikethrough or a distinct style and were excluded from the pass/fail decision.

### Calibration Curve Quality Metrics

For experiments using nonlinear curve models (4PL, 5PL, 3PL), additional goodness-of-fit
statistics are displayed in the **Protocol details** expander and in the curve parameters
section:

| Metric | What it means |
|---|---|
| **R²** | Coefficient of determination. How well the fitted curve explains the calibration signal data. Values ≥ 0.99 are generally acceptable; lower values indicate a poor fit. |
| **RMSE** | Root-mean-square error in signal units. The average absolute deviation between observed calibrator signals and the model's predicted signals. Compare this to the total signal range — an RMSE below 1–5% of range is typically good. |
| **df** | Degrees of freedom (number of calibrators minus number of model parameters). Shown for context; CI calculations require df ≥ 1. |

### EC₅₀ Confidence Interval (4PL only)

For experiments fitted with the 4PL model, the **95% confidence interval on EC₅₀** (the
inflection point of the sigmoid, stored as parameter C) is shown alongside the C value:

```
EC₅₀ = 0.32 ng/mL  [95% CI: 0.27 – 0.38]
```

**How to read it:** the true EC₅₀ lies within this range with 95% probability under the
weighted regression model. A **narrow CI** (less than a 2-fold concentration range) indicates
a well-defined sigmoid midpoint — the calibration data adequately constrained the inflection
point. A **wide CI** (spanning an order of magnitude or more) means the sigmoid midpoint is
poorly defined — you may need more calibration points near the EC₅₀ region or a broader
concentration range.

If the CI is shown as `—` (not available), the covariance matrix was singular: the calibration
data did not provide enough curvature information to compute a reliable standard error for C.
This does not invalidate the fit, but the uncertainty on C is not quantifiable from these data.

---

## 9. Editing an Experiment Record

Experiments open in **View mode** by default — nothing can be accidentally changed while you are reading the results.

To make changes:

1. Click the **Edit** button (pencil icon). The page switches to Edit mode, indicated by a green banner.
2. You can update:
   - The experiment name and date.
   - The reagent lot records.
   - Individual signal values (which will trigger a recalculation of %CV and %Recovery).
3. When you are done, click **Save**. A confirmation dialog will appear — review your changes and confirm.

**Changing the status (Reviewers and Administrators only):** If you need to manually override the validation outcome — for example, to accept an experiment where an outlier was incorrectly flagged, or to reject a run for procedural reasons — you can change the status in Edit mode. When moving an experiment to **OK** or **KO**, the system will require you to enter a written **justification**. This is mandatory for full audit trail compliance and cannot be skipped.

> **Marking a pair as an outlier:** In the results table, you can flag or un-flag individual measurement pairs as outliers using the toggle in each row. A justification is required here as well. Flagged pairs are excluded from subsequent validation calculations.

---

## 10. Searching and Filtering Experiments

From the Dashboard, click **Search Experiments** to open the search page. You can filter by:

- **Name** — partial text match.
- **Status** — All, OK, KO, or Validation Error.
- **Date** — an exact date, or a date range (from / to).

Click **Search** to run the query. Results are displayed in a paginated table showing name, protocol, date, and status.

Each row has a **Details** button to open the full record, and a **checkbox** to select experiments for comparison or batch export.

### My Experiments Filter

At the top of the search page you will find a **"My experiments"** toggle. When enabled
(the default for Analysts and Reviewers), the results are restricted to experiments you
created. Disable the toggle to see all experiments accessible under your role.

> **Administrators** see all experiments by default — their toggle is off when the page loads.

This filter helps keep your personal work front-and-centre in busy multi-user labs without
hiding the full experiment database when you need to review other team members' runs.

---

## 11. Comparing Experiments Side by Side

When you need to investigate trends, confirm run-to-run consistency, or justify why one run behaved differently from another, the **Compare** feature lets you view up to four experiments at the same time.

1. On the Search Experiments page, tick the checkboxes next to 2–4 experiments you want to compare.
2. Click **Compare Selected**.
3. The comparison page shows four panels that can be locked independently:

   - **Reagent Lots** — highlights any differences in lot numbers between runs, making it easy to identify reagent-related variability.
   - **Calibration Pairs** — tabular view of all calibrator signals, with per-column %CV flagging.
   - **Control Pairs** — same view for control wells.
   - **Calibration Curve** — an interactive chart overlaying the fitted curves from all selected experiments. You can toggle between linear and logarithmic X-axis scales.

4. At the bottom of the page, you can launch an **AI analysis** of all selected experiments at once (see [Section 12](#12-ai-powered-analysis)).

---

## 12. AI-Powered Analysis

EliSmart LIMS includes an AI assistant (powered by Google Gemini) that can interpret your experimental data and provide scientific commentary.

You can access the AI analysis in two ways:

- **From Experiment Details** — click the **Analyse with AI** button on a single experiment.
- **From Compare** — after selecting multiple experiments, use the AI panel at the bottom of the comparison page.

Type a question or a request in natural language, for example:

> *"Are the control recovery values consistent across the three runs? Could the variation be reagent-related?"*
>
> *"Summarise the calibration curve fit quality for this experiment."*

The AI receives the full experiment data (signals, calculated metrics, protocol limits, and reagent lot information) and responds with a structured scientific interpretation formatted with headings, bullet points, and tables where appropriate. The result is automatically saved and will still be visible if you navigate away and come back.

> **Language:** AI responses are generated in **Italian** by default, matching the interface language used in EliSmart LIMS. If you need a response in another language, include that request explicitly in your question (e.g. *"Answer in English."*).

> **Important:** AI-generated insights are provided as decision support. They do not replace the scientist's judgement or constitute an official regulatory record. Always review the raw data yourself before drawing conclusions.

---

## 13. Exporting Data and Reports

EliSmart LIMS can produce two types of export from any validated experiment:

### PDF — Certificate of Analysis

A formatted, print-ready document containing:
- Experiment metadata (name, date, operator, protocol).
- The reagent lots used.
- The calibration curve plot.
- The complete results table with colour-coded QC status.
- A signature block.

To generate a PDF, open an experiment and click **Export PDF**.

### Excel — Raw Data Export

A spreadsheet (.xlsx) containing all raw signals, calculated metrics, and the protocol acceptance limits on a summary sheet. Useful for further statistical analysis in tools you already use.

To export a single experiment, open it and click **Export Excel**.
To export multiple experiments at once, select them on the Search page and click **Export Batch Excel**.

---

## 14. Reagent Batch Management and Expiry Alerts

Every time a new kit lot arrives in the lab, register it in EliSmart LIMS before it is used in an experiment. This creates the lot-to-experiment traceability chain that is required for GLP compliance.

1. From the Dashboard, click **Search Reagents**.
2. Find the reagent you received and open its record.
3. Click **Register New Batch** and enter the **lot number**, **supplier**, and **expiry date**.

The system monitors all registered lots. Any lot expiring within the next 90 days appears in the **Dashboard expiry alerts** panel, sorted by urgency. This ensures that expired reagents are never inadvertently used in an experiment without notice.

---

## 15. User Management (Administrators Only)

The **User Management** page is accessible from the Dashboard sidebar and is visible only to users with the Administrator role.

From this page, an Administrator can:

- View all registered user accounts with their current role and enabled/disabled state.
- **Change a user's role** — promote an Analyst to Reviewer, or assign Administrator rights.
- **Enable or disable a user account** — useful when a team member leaves the lab or is temporarily on leave, without permanently deleting their activity history.

To create a new user account, use the **Register** option (also accessible via the login page for Administrators). Choose the appropriate role before saving.

> **Security note:** User credentials are never visible after creation, not even to Administrators. If a user forgets their password, the Administrator must disable the account and create a new one.

---

## 16. Status Reference

| Status | Meaning |
|---|---|
| **Pending** | The experiment record has been created but data entry is not yet complete. |
| **Completed** | All measurement data has been entered. The automated validation will run. |
| **OK** | Automated validation passed. All measurement pairs are within the protocol acceptance criteria. |
| **KO** | Automated validation failed. One or more measurement pairs are outside the protocol limits. |
| **Validation Error** | The system could not complete the validation calculation (e.g. missing calibration points). The data should be checked and the experiment re-saved. |

---

## 17. EliSmart and Your Daily Work

Science generates data. Every plate read, every kit lot, every decision made in the lab produces a piece of information that matters — not just for today's result, but for the investigation you might need to run six months from now when a discrepant result appears, or for the audit that arrives with little warning.

Traditionally, this information lives in a patchwork of spreadsheets, paper worksheets, and laboratory notebooks — each one maintained by a different person, each one formatted slightly differently, each one a potential source of transcription error or version confusion.

EliSmart LIMS was built to remove that friction.

When you open the system in the morning, the Dashboard tells you immediately if any kit is about to expire. When you finish a run and type in the signals, %CV is calculated before you finish the row — catching precision problems before the plate is cold. When the calibration curve is fitted automatically and %Recovery is back-calculated without you opening a single spreadsheet, the calculation is done the same way every time, with no formula drift, no copy-paste errors, no manual transcription. When a run fails, the reason is captured and stored alongside the data, attributed to the person who made the decision.

The result is a laboratory where the question *"what happened on that run three months ago?"* takes seconds instead of hours to answer. Where the audit trail is not a separate document to prepare — it is simply a side-effect of doing your normal work. Where comparing two lots of the same kit to understand a variability trend is a matter of clicking two checkboxes and reading a chart.

Our hope is that EliSmart LIMS gives you back the time you currently spend maintaining records, so that you can spend it on the work that only a scientist can do.
