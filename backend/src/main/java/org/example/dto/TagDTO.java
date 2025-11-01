package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 标签DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagDTO implements Serializable {

    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签分类（mechanism/strategy）
     */
    private String category;

    /**
     * 显示颜色
     */
    private String color;
}
