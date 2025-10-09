package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionResultDTO {
    private int questionId;
    private String questionText;
    private Map<String, Integer> optionCounts;
}
