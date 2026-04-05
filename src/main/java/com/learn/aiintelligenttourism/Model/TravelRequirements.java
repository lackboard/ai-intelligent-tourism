package com.learn.aiintelligenttourism.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TravelRequirements(
        String destination,
        String travelDate,
        String budget,
        String preference
) {
    public boolean isMissingCriticalInfo() {
        return destination == null || destination.isBlank();
    }
}

