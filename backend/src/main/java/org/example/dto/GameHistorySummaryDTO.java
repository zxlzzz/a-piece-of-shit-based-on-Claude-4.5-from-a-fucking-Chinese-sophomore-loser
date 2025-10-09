package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameHistorySummaryDTO {
    private Long gameId;
    private String roomCode;
    private LocalDateTime endTime;
    private Integer myScore;
    private Integer myRank;
    private Integer questionCount;
    private Integer playerCount;
}
