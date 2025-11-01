package org.example.entity;

import lombok.Getter;

// ✅ 应该改成枚举
@Getter
public enum QuestionType {
    CHOICE("CHOICE"),
    BID("BID");

    private final String value;
    QuestionType(String value) {
        this.value = value;
    }
}
