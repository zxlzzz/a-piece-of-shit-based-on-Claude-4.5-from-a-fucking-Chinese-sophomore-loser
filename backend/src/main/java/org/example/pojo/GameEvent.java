package org.example.pojo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class GameEvent {
    private String type;
    private String targetPlayerId;
    private String sourcePlayerId;
    private String description;
    private Map<String, Object> data;
}
