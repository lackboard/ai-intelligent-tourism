package com.learn.aiintelligenttourism.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemorySignalExtractorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private MemorySignalExtractor extractor;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        extractor = new MemorySignalExtractor(chatClientBuilder);
    }

    @Test
    void testPreFilter_skipsLLMCall() {
        // When user sends very short meaningless messages
        extractor.isItineraryAcceptedMessage("。");
        extractor.isItineraryRejectedMessage("哈");
        
        // Then chat client should not be called at all
        verify(chatClient, never()).prompt();
    }

    @Test
    void testPreFilter_allowsValidShortMessages() {
        // Given mock response
        MemorySignalExtractor.ExtractedSignals mockSignals = new MemorySignalExtractor.ExtractedSignals(
                true, false, false, "偏轻松", null, null, List.of(), null, null
        );
        when(chatClient.prompt().system(any(String.class)).user("好").options(any()).call().entity(MemorySignalExtractor.ExtractedSignals.class))
                .thenReturn(mockSignals);
        clearInvocations(chatClient);

        // When user sends valid short message
        boolean isAccepted = extractor.isItineraryAcceptedMessage("好");
        
        // Then chat client should be called
        assertThat(isAccepted).isTrue();
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void testExtractProfileFacts_cachesResult() {
        // Given mock response
        MemorySignalExtractor.ExtractedSignals mockSignals = new MemorySignalExtractor.ExtractedSignals(
                false, false, false, null, "偏经济型", "市中心", List.of("美食", "文化"), "高铁", "成都"
        );

        when(chatClient.prompt().system(any(String.class)).user("去成都，想吃美食看文化，住市中心，坐高铁，穷游")
                .options(any()).call().entity(MemorySignalExtractor.ExtractedSignals.class))
                .thenReturn(mockSignals);
        clearInvocations(chatClient);

        String message = "去成都，想吃美食看文化，住市中心，坐高铁，穷游";

        // Call extraction once
        List<ProfileMemoryFact> facts1 = extractor.extractProfileFacts(message);
        assertThat(facts1).hasSize(4); // budgetRange, hotelPreference, interestTags, transportPreference
        assertThat(facts1.stream().anyMatch(f -> f.key().equals("budget_range") && f.value().equals("偏经济型"))).isTrue();

        // Call another method with the SAME message
        String destination = extractor.extractDestination(message);
        assertThat(destination).isEqualTo("成都");

        // Then verify LLM was only called ONCE due to caching
        verify(chatClient, times(1)).prompt();
    }
}
