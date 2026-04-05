package com.learn.aiintelligenttourism.memory;

import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L1 工作记忆仍复用现有 ChatMemory。
 * 这里额外提供一个轻量摘要，用于跨模式切换时把最近上下文补给到模型。
 */
@Service
public class WorkingMemoryService {

    public static final String SOURCE_JDBC_CHAT_MEMORY = "jdbc_chat_memory";

    private final JdbcChatMemoryRepository chatMemoryRepository;

    public WorkingMemoryService(JdbcChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    public String summarizeRecentConversation(String threadId, int maxMessages) {
        if (threadId == null || threadId.isBlank()) {
            return "";
        }

        List<Message> messages = findConversationMessages(threadId);
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        int fromIndex = Math.max(0, messages.size() - maxMessages);
        List<Message> recentMessages = messages.subList(fromIndex, messages.size());
        StringBuilder summary = new StringBuilder();
        for (Message message : recentMessages) {
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            summary.append("- ")
                    .append(message.getMessageType().name())
                    .append(": ")
                    .append(trimForPrompt(text, 120))
                    .append('\n');
        }
        return summary.toString().trim();
    }

    /**
     * 测试时直接查看 L1 的原始消息列表，比只看摘要更容易判断窗口裁剪是否符合预期。
     */
    public List<WorkingMemoryMessageView> getRecentMessageViews(String threadId, int maxMessages) {
        List<Message> messages = findConversationMessages(threadId);
        if (messages.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, messages.size() - maxMessages);
        return messages.subList(fromIndex, messages.size()).stream()
                .map(message -> new WorkingMemoryMessageView(
                        message.getMessageType().name(),
                        trimForPrompt(message.getText(), 300)
                ))
                .toList();
    }

    public void clearConversation(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        chatMemoryRepository.deleteByConversationId(threadId.trim());
    }

    public WorkingMemorySnapshot getWorkingMemorySnapshot(String threadId, int maxMessages) {
        List<Message> messages = findConversationMessages(threadId);
        if (messages.isEmpty()) {
            return WorkingMemorySnapshot.empty(SOURCE_JDBC_CHAT_MEMORY);
        }
        return new WorkingMemorySnapshot(
                SOURCE_JDBC_CHAT_MEMORY,
                summarizeRecentConversation(threadId, maxMessages),
                getRecentMessageViews(threadId, maxMessages)
        );
    }

    private List<Message> findConversationMessages(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return List.of();
        }
        List<Message> messages = chatMemoryRepository.findByConversationId(threadId.trim());
        return messages == null ? List.of() : messages;
    }

    private String trimForPrompt(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
