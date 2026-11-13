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

Use `CHARACTER_RECAP` for a character recap or `PATH_SUMMARY` for a journey summary.
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
