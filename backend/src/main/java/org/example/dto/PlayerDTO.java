package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 玩家DTO - 用于前后端数据传输
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDTO {

    /**
     * 玩家唯一标识（统一命名为 playerId）
     */
    private String playerId;

    /**
     * 玩家昵称
     */
    private String name;

    /**
     * 当前得分（使用 Integer 而非 int，避免 null 问题）
     */
    @Builder.Default
    private Integer score = 0;

    /**
     * 是否已准备
     */
    @Builder.Default
    private Boolean ready = false;
}