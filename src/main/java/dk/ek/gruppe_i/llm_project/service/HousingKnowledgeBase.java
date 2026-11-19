package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal RAG: the housing data description (src/main/resources/rag/housing-data-context.md) is split into
 * sections, embedded with the local Ollama embedding model, and the sections most similar to the
 * incoming context are retrieved with cosine similarity. No database, nothing is stored on disk.
 * Chunks are embedded on first use, so the application can start without Ollama running.
 */
@Component
public class HousingKnowledgeBase {

    static final String RESOURCE = "rag/housing-data-context.md";

    private final EmbeddingModel embeddingModel;
    private final AiDeploymentProperties properties;
    private List<Chunk> chunks;

    public HousingKnowledgeBase(EmbeddingModel embeddingModel, AiDeploymentProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /** Returns the most relevant knowledge sections for the query, best match first. */
    public List<String> retrieve(String query) {
        List<Chunk> all = loadChunks();
        float[] queryVector = embeddingModel.embed(query);
        return all.stream()
                .sorted(Comparator.comparingDouble((Chunk chunk) -> cosine(queryVector, chunk.vector())).reversed())
                .limit(Math.max(1, properties.getRagTopK()))
                .map(Chunk::text)
                .toList();
    }

    private synchronized List<Chunk> loadChunks() {
        if (chunks == null) {
            List<String> texts = split(readResource());
            List<float[]> vectors = embeddingModel.embed(texts);
            List<Chunk> loaded = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                loaded.add(new Chunk(texts.get(i), vectors.get(i)));
            }
            chunks = List.copyOf(loaded);
        }
        return chunks;
    }

    private String readResource() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read RAG source " + RESOURCE, ex);
        }
    }

    /** Splits markdown into one chunk per "## " section. Text before the first section is its own chunk. */
    static List<String> split(String markdown) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ") && !current.toString().isBlank()) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Chunk(String text, float[] vector) {
    }
}
