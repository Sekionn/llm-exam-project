# llm_project

This API sends a request type plus context to a local LLM (Ollama) and returns the generated text.
It has no database or history; every request must include all the context it needs.

Run with Java 21 using `./mvnw.cmd spring-boot:run`. Configure Ollama with
`AI_OLLAMA_BASE_URL`, `AI_OLLAMA_CHAT_MODEL`, and optionally `AI_OLLAMA_TEMPERATURE`.
The defaults are in `src/main/resources/application.properties`.

Send a housing request to `POST http://localhost:8090/ai/ask` with `Content-Type: application/json`:

```json
{
  "requestType": "HOUSING_CAPACITY",
  "context": "housingType: apartment\nnumberOfLevels: 4\nnumberOfFlats: 20\nheightMeters: unknown",
  "useRag": true
}
```

The response has this shape:

```json
{"response": "{\"capacity\": 40, \"confidence\": \"high\", \"reasoning\": \"...\"}"}
```

If the AI provider cannot be reached, times out, or fails to generate a response,
the API returns HTTP `503 Service Unavailable` with `Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "AI unavailable",
  "status": 503,
  "detail": "AI generation is currently unavailable. Please try again later.",
  "instance": "/ai/ask",
  "code": "AI_UNAVAILABLE"
}
```

An empty AI response is also treated as unavailable. Provider error details are not included in the API response.
Swagger UI is available at `http://localhost:8090/swagger-ui/index.html`.

For Docker, first build the JAR with `./mvnw.cmd package`, then run
`docker compose -f docker-compose-llm_project.yml --profile prod up --build`.
The container uses `http://host.docker.internal:11434` for Ollama by default;
set `AI_OLLAMA_BASE_URL` to use a different provider address.

## Adding a new request type

1. Add a value to `AiRequestType`.
2. Add a case to `resolveSystemPrompt` with its system prompt, using the 4T's.
3. Add a case to `buildUserMessage`.

## Housing capacity (Smart City)

`requestType: "HOUSING_CAPACITY"` estimates how many residents one housing record can hold.
`context` is plain text with one `key: value` line per field (missing values are `unknown`); the fields are
described in `src/main/resources/rag/housing-data-context.md`.

```json
{
  "requestType": "HOUSING_CAPACITY",
  "context": "housingType: apartment\nnumberOfLevels: 4\nnumberOfFlats: unknown\nheightMeters: unknown"
}
```

The model's answer (in `response`) is a JSON object:
`{"capacity": 8, "confidence": "low", "reasoning": "..."}`.

**RAG:** the housing data description is split into sections, embedded with the local embedding model and
the most similar sections are added to the prompt. Set `"useRag": false` in the request to switch retrieval
off for one call and compare answers; the default is `app.ai.rag-enabled`.
If retrieval fails, the request is still answered without background material (a warning is logged).

**Models** (local only): `ollama pull qwen3:4b` and `ollama pull nomic-embed-text`.
Settings: `AI_OLLAMA_CHAT_MODEL`, `AI_OLLAMA_EMBEDDING_MODEL`, `AI_RAG_ENABLED`, `AI_RAG_TOP_K`, `SERVER_PORT`
(default port is now 8090, matching this README and docker-compose).

### Example: the effect of RAG on a housing capacity estimate

The same request is sent twice. The only difference is the `useRag` flag.
Model: `qwen3:4b` (local, via Ollama), embeddings: `nomic-embed-text`, temperature 0.3.

**Request (with RAG)**

```json
{
  "requestType": "HOUSING_CAPACITY",
  "context": "housingType: apartment\nnumberOfLevels: 4\nnumberOfFlats: 20\nheightMeters: unknown",
  "useRag": true
}
```

**Response (with RAG)**

```json
{
  "response": "{\"capacity\": 40, \"confidence\": \"high\", \"reasoning\": \"20 flats × 2 persons per flat = 40 residents.\"}"
}
```

The log shows `Retrieved 3 background chunks for HOUSING_CAPACITY`. The estimate follows the rule from
`src/main/resources/rag/housing-data-context.md` (2 persons per flat when `numberOfFlats` is known).

**Request (without RAG)**: identical, but with `"useRag": false`

```json
{
  "requestType": "HOUSING_CAPACITY",
  "context": "housingType: apartment\nnumberOfLevels: 4\nnumberOfFlats: 20\nheightMeters: unknown",
  "useRag": false
}
```

**Response (without RAG)**

```json
{
  "response": "{\"capacity\": 80, \"confidence\": \"low\", \"reasoning\": \"Assuming a maximum of 4 residents per flat, the building with 20 flats has a capacity of 80. Confidence is low due to the absence of data on typical occupancy rates.\"}"
}
```

Without background material the model has no project rule to apply, so it makes up its own assumption
(4 residents per flat) and reports low confidence.

| | `capacity` | `confidence` | Assumption used |
|---|---|---|---|
| RAG on | 40 | high | 2 persons per flat (from the knowledge file) |
| RAG off | 80 | low | 4 residents per flat (the model's own guess) |

**Notes**

- Retrieval makes the answer follow the project's own rules. It does not make it more accurate by itself:
  the 2-persons-per-flat rule is a project assumption, not an official statistic, and there is no ground truth
  for real capacity.
- This is a single run for each setting. With temperature 0.3 the answers can vary between runs, so repeat the
  request a few times before drawing conclusions.
- The model's JSON is returned as a string inside `response`; the Smart City client parses it.

## Apartment capacity: `POST /ai/housing-capacity`

Estimates the **typical number of residents** (not the legal maximum) of apartment buildings. Built from real OpenStreetMap data (see `housing_eda.ipynb`).

Request (1 to 50 buildings; leave an unknown field out, unknown is never 0):

```json
{
  "useRag": true,
  "buildings": [
    { "id": "way/4393716", "footprintM2": 630, "numberOfLevels": 5 },
    { "id": "way/1",       "footprintM2": 300, "heightMeters": 15 },
    { "id": "way/2",       "numberOfFlats": 30 }
  ]
}
```

Response, one result per building in request order:

```json
{ "results": [ {
  "id": "way/4393716", "tier": "B", "situation": "levels known, flats unknown, footprint known",
  "capacity": 63, "modelCapacity": 63, "confidence": "medium", "reasoning": "630 m2 x 5 x 0.8 = 2520 m2 / 40 = 63",
  "cappedByCeiling": false, "ceiling": 126, "baselineCapacity": 63,
  "baselineBasis": "footprint 630 m2 x 5 floors x 0.8 = 2520 m2 usable; 2520 / 40 m2 per person = 63.0",
  "ragUsed": true, "error": null } ] }
```

How it works:

1. The server decides the situation (tier A to E) from the known fields and writes the model input itself, so every caller sends the same format.
2. With RAG, the knowledge base (`rag/apartment-*.md`) is searched with the **words** of the situation (for example `apartment building, levels unknown, flats unknown, footprint known`), not with numbers. The best chunks (rule, reference figures, data profile, examples) go in the prompt.
3. The answer is read as JSON (`<think>` blocks, code fences and extra text are removed; one retry if it cannot be read).
4. The backend ceiling is applied: usable area / 20 m2 per person. A higher model answer is limited and marked `cappedByCeiling`.
5. `baselineCapacity` is the documented rule from the notebook, for comparison. It is a rule, not ground truth.

A building that fails (invalid values, unreadable answer, model down) gets an `error` in its own result; the others are still answered.

Files from the notebook's `rag_export/` go in `src/main/resources/rag/`: `apartment-rules.md`, `apartment-reference.md`, `apartment-profile.md`, `apartment-examples.md` and `baseline-parameters.properties`. Re-run the notebook and copy them again when the data changes.

Compare with and without RAG on the held-out buildings (needs the app and Ollama running):

```
python tools/compare_rag.py rag_export/eval_set.csv --limit 40
```

Fill `capacity_label` in `eval_set.csv` by hand for a real comparison; without labels the script only compares with the baseline rule.
