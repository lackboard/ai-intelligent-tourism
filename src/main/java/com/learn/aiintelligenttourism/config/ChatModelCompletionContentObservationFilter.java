package com.learn.aiintelligenttourism.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatModelCompletionContentObservationFilter implements ObservationFilter {

    @Override
    @NonNull
    public Observation.Context map(@NonNull Observation.Context context) {
        appendLangfuseIdentity(context);

        if (context instanceof ChatModelObservationContext chatContext) {
            List<String> prompts = processPrompts(chatContext);
            List<String> completions = processCompletions(chatContext);
            chatContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", ObservabilityHelper.concatenateStrings(prompts)));
            chatContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.completion", ObservabilityHelper.concatenateStrings(completions)));
            chatContext.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", ObservabilityHelper.concatenateStrings(prompts)));
            chatContext.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output", ObservabilityHelper.concatenateStrings(completions)));
        } else if (context instanceof VectorStoreObservationContext vectorContext) {
            if (vectorContext.getQueryRequest() != null && StringUtils.hasText(vectorContext.getQueryRequest().getQuery())) {
                vectorContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", vectorContext.getQueryRequest().getQuery()));
                vectorContext.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.input", vectorContext.getQueryRequest().getQuery()));
            }
            if (!CollectionUtils.isEmpty(vectorContext.getQueryResponse())) {
                List<String> docs = new ArrayList<>();
                for (Document doc : vectorContext.getQueryResponse()) {
                    if (StringUtils.hasText(doc.getText())) {
                        docs.add(doc.getText());
                    }
                }
                vectorContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.completion", ObservabilityHelper.concatenateStrings(docs)));
                vectorContext.addHighCardinalityKeyValue(KeyValue.of("langfuse.observation.output", ObservabilityHelper.concatenateStrings(docs)));
            }
        } else if (context instanceof EmbeddingModelObservationContext embeddingContext) {
            if (embeddingContext.getRequest() != null && !CollectionUtils.isEmpty(embeddingContext.getRequest().getInstructions())) {
                embeddingContext.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", ObservabilityHelper.concatenateStrings(embeddingContext.getRequest().getInstructions())));
                embeddingContext.addHighCardinalityKeyValue(KeyValue.of("embedding.input", ObservabilityHelper.concatenateStrings(embeddingContext.getRequest().getInstructions())));
            }
        }
        
        return context;
    }

    private void appendLangfuseIdentity(Observation.Context context) {
        String userId = LangfuseTraceContextHolder.getUserId();
        if (StringUtils.hasText(userId)) {
            context.addLowCardinalityKeyValue(KeyValue.of("langfuse.user.id", userId));
        }

        String sessionId = LangfuseTraceContextHolder.getSessionId();
        if (StringUtils.hasText(sessionId)) {
            context.addLowCardinalityKeyValue(KeyValue.of("langfuse.session.id", sessionId));
        }
    }

    private List<String> processPrompts(ChatModelObservationContext context) {
        List<String> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
            for (Message message : context.getRequest().getInstructions()) {
                if (StringUtils.hasText(message.getText())) {
                    result.add(message.getText());
                }
            }
        }
        return result;
    }

    private List<String> processCompletions(ChatModelObservationContext context) {
        List<String> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(context.getResponse().getResults())) {
            context.getResponse().getResults().forEach(generation -> {
                String outputText = generation.getOutput().getText();
                if (StringUtils.hasText(outputText)) {
                    result.add(outputText);
                }
            });
        }
        return result;
    }
}