package org.example.pojo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class Buff {
    private String id;  // ✅ 添加唯一标识（用于移除特定 Buff）
    private String type;  // Buff 类型（如 "SCORE_DOUBLE", "SCORE_MINUS"）
    private Integer duration;
    private Map<String, Object> params;
}
