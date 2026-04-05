package com.learn.aiintelligenttourism.memory.event;

import com.learn.aiintelligenttourism.Model.ItineraryResponse;
import com.learn.aiintelligenttourism.memory.ConversationIdentity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryMemoryEvent implements Serializable {
    private ConversationIdentity identity;
    private String userMessage;
    private ItineraryResponse itinerary;
}

