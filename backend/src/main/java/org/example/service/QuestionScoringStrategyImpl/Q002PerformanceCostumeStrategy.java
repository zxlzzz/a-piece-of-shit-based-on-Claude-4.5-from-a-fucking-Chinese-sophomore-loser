package org.example.service.QuestionScoringStrategyImpl;

import java.util.HashMap;
import java.util.Map;

public class Q002PerformanceCostumeStrategy extends BaseQuestionStrategy{
    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions){
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String c1 = players[0].getValue();
        String c2 = players[1].getValue();

        boolean hasGuard = c1.equals("A") || c1.equals("C") || c2.equals("A") || c2.equals("C");
        boolean hasPrince = c1.equals("B") || c2.equals("B");
        boolean complete = hasGuard && hasPrince;

        int v1 = c1.equals("A") ? 7 : c1.equals("B") ? 5 : 3;
        int v2 = c2.equals("A") ? 7 : c2.equals("B") ? 5 : 3;

        scores.put(players[0].getKey(), complete ? v1 : -v1);
        scores.put(players[1].getKey(), complete ? v2 : -v2);

        return scores;
    }
    @Override
    public String getQuestionIdentifier(){
        return "Q002";
    }
}
