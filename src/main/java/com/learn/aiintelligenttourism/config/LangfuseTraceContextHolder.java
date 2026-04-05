package com.learn.aiintelligenttourism.config;

/**
 * Holds per-request Langfuse identity in thread context so ObservationFilter can enrich spans.
 */
public final class LangfuseTraceContextHolder {

    private static final InheritableThreadLocal<String> USER_ID = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<String> SESSION_ID = new InheritableThreadLocal<>();

    private LangfuseTraceContextHolder() {
    }

    public static void set(String userId, String sessionId) {
        if (userId != null && !userId.isBlank()) {
            USER_ID.set(userId.trim());
        }
        if (sessionId != null && !sessionId.isBlank()) {
            SESSION_ID.set(sessionId.trim());
        }
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
        SESSION_ID.remove();
    }
}

