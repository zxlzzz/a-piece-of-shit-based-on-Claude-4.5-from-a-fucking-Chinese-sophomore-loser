package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDTO {
    private String token;
    private Long id;
    private String playerId;
    private String username;
    private String name;

    /**
     * 玩家当前所在的房间代码（如果有）
     * 前端收到此字段后应自动跳转到房间
     */
    private String roomCode;
}
