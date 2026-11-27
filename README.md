# llm_project

The API uses a local LLM (Ollama) to estimate the typical number of residents of apartment buildings.
It has no database or request history. All generation goes through `POST /ai/ask`.

Run with Java 21 or later using `./mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` elsewhere.
Install the configured local models with `ollama pull qwen3:4b` and `ollama pull nomic-embed-text`.
Provider settings are in `src/main/resources/application.properties`; overrides can be set in the project's `.env`:
`AI_OLLAMA_BASE_URL`, `AI_OLLAMA_CHAT_MODEL`, `AI_OLLAMA_EMBEDDING_MODEL`, `AI_OLLAMA_TEMPERATURE`,
`AI_RAG_ENABLED`, `AI_RAG_TOP_K`, and `SERVER_PORT` (default 8090).

## Request

Send JSON to `POST http://localhost:8090/ai/ask`:

```json
{
  "requestType": "HOUSING_CAPACITY",
  "useRag": true,
  "buildings": [
    { "id": "way/4393716", "footprintM2": 630, "numberOfLevels": 5 },
    { "id": "way/1", "footprintM2": 300, "heightMeters": 15 },
    { "id": "way/2", "numberOfFlats": 30 }
  ]
}
```

`requestType` selects the prompt and processing flow in `AiService.handleRequest`.
`buildings` must contain 1 to 50 non-null objects. Unknown fields are omitted or null, never zero.
`id` is optional caller metadata, returned unchanged. Set `useRag` to true or false to override
`app.ai.rag-enabled` for the request. No authentication or API version header is required.
Invalid input returns HTTP 400 before generation starts.

## Response and processing

The API returns structured JSON, with one result per building in request order:

```json
{
  "results": [{
    "id": "way/4393716",
    "tier": "B",
    "situation": "levels known, flats unknown, footprint known",
    "capacity": 63,
    "modelCapacity": 63,
    "confidence": "medium",
    "reasoning": "630 m2 x 5 x 0.8 = 2520 m2 / 40 = 63",
    "cappedByCeiling": false,
    "ceiling": 126,
    "baselineCapacity": 63,
    "baselineBasis": "footprint 630 m2 x 5 floors x 0.8 = 2520 m2 usable; 2520 / 40 m2 per person = 63.0",
    "ragUsed": true,
    "error": null
  }]
}
```

This is an illustrative model response; model output can vary.

1. The server derives a situation (tier A to E) from the known fields and formats the model input.
2. With RAG, it searches `rag/apartment-*.md` using the situation's words, then adds the most relevant sections to the prompt.
3. The model answer is parsed as JSON, removing thinking blocks, code fences and surrounding text. An unreadable answer is retried once.
4. The usable-area ceiling limits high estimates and marks `cappedByCeiling`.
5. A rule-based `baselineCapacity` is returned for comparison. The baseline is a project assumption, not ground truth.

Retrieval failure falls back to generation without background material (`ragUsed: false`).
A provider failure or an answer that remains unreadable produces a per-building `error` with null capacity;
other buildings are still processed. The batch response remains HTTP 200, including when all buildings fail.
Provider connection details are not exposed. The baseline is not substituted for a failed AI answer.

Swagger UI is available at `http://localhost:8090/swagger-ui/index.html`.

## RAG resources and comparison

Files exported by the EDA notebook belong in `src/main/resources/rag/`:
`apartment-rules.md`, `apartment-reference.md`, `apartment-profile.md`, `apartment-examples.md`,
and `baseline-parameters.properties`. Copy fresh exports when the source data changes.
Chunks are embedded lazily and cached in memory; there is no database or disk-based vector store.

Compare the same buildings with and without RAG (requires the app and Ollama):

```text
python tools/compare_rag.py rag_export/eval_set.csv --limit 40
```

The script calls `/ai/ask` with `requestType: HOUSING_CAPACITY` for both settings.
Fill `capacity_label` in the evaluation CSV by hand for a comparison against real labels;
without labels, the script compares with the baseline rule only.

## Docker

Build the JAR with `./mvnw.cmd package`, then run
`docker compose -f docker-compose-llm_project.yml --profile prod up --build`.
The container defaults to `http://host.docker.internal:11434` for Ollama;
set `AI_OLLAMA_BASE_URL` to use another address.

## Adding a request type

Add an enum value to `AiRequestType` and a case to `AiService.handleRequest` that selects
its prompt and processing flow. Each flow's response DTO extends the empty `BaseResponse` class,
which is the return type of the service dispatcher and `/ai/ask` endpoint. Add the concrete response
type to the endpoint's Swagger `oneOf` schemas. Update request validation and tests as needed.
