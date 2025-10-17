package org.example.entity;

// ✅ 应该改成枚举
public enum QuestionType {
    CHOICE("choice"),
    BID("bid");

    private final String value;
    QuestionType(String value) {
        this.value = value;
    }
}
