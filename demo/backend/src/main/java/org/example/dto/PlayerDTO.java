package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDTO {

    private String playerId;

    private String name;

    @Builder.Default
    private Integer score = 0;

    @Builder.Default
    private Boolean ready = false;
}
