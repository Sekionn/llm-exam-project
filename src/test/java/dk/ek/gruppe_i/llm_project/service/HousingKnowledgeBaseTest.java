package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Pure unit tests: chunking and similarity need no Ollama and no Spring context. */
class HousingKnowledgeBaseTest {

    @Test
    void splitsMarkdownIntoOneChunkPerSection() {
        List<String> chunks = HousingKnowledgeBase.split("# Title\nintro\n\n## One\na\n\n## Two\nb\n");

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).startsWith("# Title"));
        assertTrue(chunks.get(1).startsWith("## One"));
        assertTrue(chunks.get(2).startsWith("## Two"));
    }

    @Test
    void splitIgnoresBlankInput() {
        assertTrue(HousingKnowledgeBase.split("  \n\n").isEmpty());
    }

    @Test
    void cosineIsOneForSameDirectionAndZeroForOrthogonal() {
        assertEquals(1.0, HousingKnowledgeBase.cosine(new float[]{1, 2}, new float[]{2, 4}), 1e-9);
        assertEquals(0.0, HousingKnowledgeBase.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-9);
    }

    @Test
    void cosineOfZeroVectorIsZero() {
        assertEquals(0.0, HousingKnowledgeBase.cosine(new float[]{0, 0}, new float[]{1, 1}), 1e-9);
    }

    @Test
    void retrievalEmbedsAllMatchingFilesAndCachesTheirChunks() throws Exception {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(text -> new float[]{1, 0}).toList();
        });
        when(model.embed(anyString())).thenReturn(new float[]{1, 0});
        AiDeploymentProperties properties = new AiDeploymentProperties();
        properties.setRagTopK(Integer.MAX_VALUE);
        HousingKnowledgeBase knowledgeBase = new HousingKnowledgeBase(model, properties);

        List<String> retrieved = knowledgeBase.retrieveApartmentRules("apartment building, flats known");
        var sources = new PathMatchingResourcePatternResolver().getResources(HousingKnowledgeBase.APARTMENT_PATTERN);
        assertTrue(sources.length >= 4, "expected rules, reference, profile and examples files");
        int expectedChunks = 0;
        for (var source : sources) {
            try (var stream = source.getInputStream()) {
                List<String> fileChunks = HousingKnowledgeBase.split(StreamUtils.copyToString(stream, StandardCharsets.UTF_8));
                assertTrue(retrieved.containsAll(fileChunks), "missing material from " + source.getFilename());
                expectedChunks += fileChunks.size();
            }
        }
        assertEquals(expectedChunks, retrieved.size());

        knowledgeBase.retrieveApartmentRules("apartment building, levels known");
        verify(model, times(1)).embed(anyList());
        verify(model, times(2)).embed(anyString());
    }
}
