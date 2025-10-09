package org.example.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreUtils {
    public static Map<String, List<String>> groupByChoice(Map<String, String> submissions) {
        Map<String, List<String>> grouped = new HashMap<>();
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            grouped.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
        }
        return grouped;
    };
    public static void awardScores(Map<String, Integer> result, List<String> players,int score) {
        for (String player : players) {
            result.put(player, score);
        }
    }
}
