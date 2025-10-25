package org.example.pojo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PlayerGameState implements Serializable {
    private static final long serialVersionUID = 1L;
    private String playerId;
    private String name;
    private Integer totalScore;
    private List<Buff> activeBuffs;
    private Map<String, Object> customData;
}
