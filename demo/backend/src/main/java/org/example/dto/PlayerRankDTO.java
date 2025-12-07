package org.example.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayerRankDTO {
    private String playerId;
    private String playerName;
    private Integer totalScore;
    private Integer rank;
    private Boolean passed;
}
