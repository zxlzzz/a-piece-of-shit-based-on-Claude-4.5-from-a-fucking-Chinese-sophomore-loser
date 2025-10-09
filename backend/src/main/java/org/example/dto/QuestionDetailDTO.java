package org.example.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class QuestionDetailDTO {
    private Integer questionIndex;
    private String questionText;
    private String optionText;
    private String questionType;
    private List<PlayerSubmissionDTO> playerSubmissions;
    private Map<String, Integer> choiceCounts;
}
