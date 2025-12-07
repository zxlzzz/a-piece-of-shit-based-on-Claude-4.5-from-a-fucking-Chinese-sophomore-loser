package org.example.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlayerSubmissionDTO {
    private String playerId;
    private String playerName;
    private String choice;
    private Integer baseScore;
    private Integer finalScore;
    private LocalDateTime submittedAt;
}
