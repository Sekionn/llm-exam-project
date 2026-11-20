package dk.ek.gruppe_i.llm_project.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void bundledRagSourceExistsAndHasSections() throws Exception {
        String text = StreamUtils.copyToString(
                new ClassPathResource(HousingKnowledgeBase.RESOURCE).getInputStream(), StandardCharsets.UTF_8);

        assertTrue(HousingKnowledgeBase.split(text).size() > 3);
    }
}
