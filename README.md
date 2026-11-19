# llm_project

This API sends the supplied context to the configured AI provider and returns the
generated text directly. It has no database, saved summaries, or conversation history.
Every request must include all the context needed to generate its response.

Run with Java 21 or later using `./mvnw spring-boot:run` (Windows: `./mvnw.cmd spring-boot:run`).
Configure the provider with `AI_OLLAMA_BASE_URL`, `AI_OLLAMA_CHAT_MODEL`, and optionally
`AI_OLLAMA_TEMPERATURE`. The defaults are in `src/main/resources/application.properties`.

Send requests to `POST http://localhost:8090/choose-your-fate/ai/ask`
with `Content-Type: application/json`:

```json
{
  "requestType": "CHARACTER_RECAP",
  "context": "Arin is an elven ranger who crossed the forest and rescued a lost traveler."
}
```

Use `CHARACTER_RECAP` for a character recap, `PATH_SUMMARY` for a journey summary, or
`HOUSING_CAPACITY` to estimate housing capacity (see below).
`context` is required and must contain character details or journey history.
`characterId` is optional caller metadata; if provided, it must be positive.
No data is retrieved using the ID. No authentication token or API version header is required.

The response has this shape:

```json
{"response": "You are Arin, an elven ranger..."}
```

If the AI provider cannot be reached, times out, or fails to generate a response,
the API returns HTTP `503 Service Unavailable`
with `Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "AI unavailable",
  "status": 503,
  "detail": "AI generation is currently unavailable. Please try again later.",
  "instance": "/choose-your-fate/ai/ask",
  "code": "AI_UNAVAILABLE"
}
```

An empty AI response is also treated as unavailable. Provider error details are
not included in the API response.

Swagger UI is available at `http://localhost:8090/swagger-ui/index.html`.

For Docker, first build the JAR with `./mvnw.cmd package`, then run
`docker compose -f docker-compose-llm_project.yml --profile prod up --build`.
The container uses `http://host.docker.internal:11434` for Ollama by default;
set `AI_OLLAMA_BASE_URL` to use a different provider address.

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
