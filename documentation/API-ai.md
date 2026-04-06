# AI Analysis API

REST endpoints exposed by the `GeminiController` for AI-powered experiment analysis via Google Gemini.

**Base path:** `/api/ai`

---

## POST `/api/ai/analyze`

Analyzes one or more experiments by building a structured prompt from their measurement data and
querying the Google Gemini API. Returns the AI-generated analysis text.

**Requires:** `GEMINI_API_KEY` environment variable to be set on the backend.

### Request Body

```json
{
  "experimentIds": [1, 2],
  "userQuestion": "Why did the low control fail in both runs? Is there a lot-number trend?"
}
```

| Field           | Type        | Required | Description                                          |
|-----------------|-------------|----------|------------------------------------------------------|
| `experimentIds` | `Long[]`    | Yes      | IDs of experiments to analyze (at least one)         |
| `userQuestion`  | `String`    | Yes      | Free-text question for the AI analyst                |

### Response `200 OK`

```json
{
  "analysis": "Based on the experiment data provided, the low control failed in both runs..."
}
```

| Field      | Type     | Description                              |
|------------|----------|------------------------------------------|
| `analysis` | `String` | AI-generated analysis text from Gemini   |

### Error Responses

| Status | Condition                                                       |
|--------|-----------------------------------------------------------------|
| `400`  | `experimentIds` is empty or null                                |
| `404`  | One or more experiment IDs do not exist                         |
| `502`  | Gemini API call failed or returned an unparseable response      |

### Example `400` — Empty experiment list

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "At least one experiment ID is required.",
  "timestamp": "2026-04-06T10:30:00"
}
```

### Example `502` — Gemini API failure

```json
{
  "status": 502,
  "error": "Bad Gateway",
  "message": "AI service error: Failed to parse Gemini API response",
  "timestamp": "2026-04-06T10:30:00"
}
```

---

## Prompt Structure

The service builds a three-section prompt sent to Gemini:

```
[SYSTEM_CONTEXT]
You are a senior Biotech Analyst for EliSmart. Analyze the following N ELISA experiment(s)
run under the "<Protocol>" protocol.
Protocol Limits: Max %CV: X% | Max %Error: Y%.

[EXPERIMENT_DATA]
Exp 1 "Run 001" (2026-04-05): Status OK. Reagent Lots: [Capture Ab=A1]. Calibration (8 pairs): Avg %CV=1.4% | Avg %Rec=100.5%. Controls (2 pairs): Avg %CV=1.4% | Avg %Rec=101.0%.
...

[USER_QUERY]
<userQuestion>
```
