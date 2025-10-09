package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pojo.RoomStatus;


import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomDTO {
    private String roomCode;
    private int maxPlayers;
    private int currentPlayers;
    private RoomStatus status;
    private List<PlayerDTO> players;
    private LocalDateTime questionStartTime;
    private Integer timeLimit;
    private Integer currentIndex;
    private QuestionDTO currentQuestion;
    private Integer questionCount;
}
