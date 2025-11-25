package org.example.pojo;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
public class Buff implements Serializable {
    private static final long serialVersionUID = 1L;
    private Double value;
    @Enumerated(EnumType.STRING)
    private BuffType type;  // Buff 类型（如 "SCORE_DOUBLE", "SCORE_MINUS"）
    private Integer duration;
    private Map<String, Object> params;
}
