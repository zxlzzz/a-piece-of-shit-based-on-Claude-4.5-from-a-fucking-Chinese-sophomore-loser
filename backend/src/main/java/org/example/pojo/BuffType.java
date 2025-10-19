package org.example.pojo;

public enum BuffType {
    MULTIPLIER("分数倍数"),
    ADD("分数增加");

    private final String description;

    BuffType(String description) {
        this.description = description;
    }
}
