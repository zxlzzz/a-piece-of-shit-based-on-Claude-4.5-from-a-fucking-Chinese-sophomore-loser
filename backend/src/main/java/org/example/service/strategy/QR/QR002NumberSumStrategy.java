package org.example.service.strategy.QR;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.GameContext;
import org.example.pojo.PlayerGameState;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class QR002NumberSumStrategy extends BaseRepeatableStrategy {
    private static final String KEY = "QR002_choices";

    public QR002NumberSumStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public int getTotalRounds() {
        return 3;
    }

    @Override
    public QuestionDetailDTO calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        for (Map.Entry<String, String> e : submissions.entrySet()) {
            PlayerGameState s = context.getPlayerStates().get(e.getKey());
            if (s == null) continue;
            if (s.getCustomData() == null) s.setCustomData(new HashMap<>());
            
            @SuppressWarnings("unchecked")
            List<Integer> choices = (List<Integer>) s.getCustomData().computeIfAbsent(KEY, k -> new ArrayList<>());
            choices.add(Integer.parseInt(e.getValue()));
        }

        Map<String, Integer> baseScores = currentRound < 3 
            ? submissions.keySet().stream().collect(Collectors.toMap(id -> id, id -> 0))
            : calcFinal(context);
        
        Map<String, Integer> finalScores = new HashMap<>(baseScores);

        List<PlayerSubmissionDTO> playerSubs = buildPlayerSubmissions(context, submissions, baseScores, finalScores);
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        return QuestionDetailDTO.builder()
            .questionIndex(context.getCurrentQuestionIndex())
            .questionText(context.getCurrentQuestion().getText() + " (第" + currentRound + "/3轮)")
            .optionText(getOptionText(context.getCurrentQuestion()))
            .questionType(context.getCurrentQuestion().getType())
            .playerSubmissions(playerSubs)
            .choiceCounts(choiceCounts)
            .build();
    }

    private Map<String, Integer> calcFinal(GameContext context) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, List<Integer>> all = new HashMap<>();

        for (Map.Entry<String, PlayerGameState> e : context.getPlayerStates().entrySet()) {
            if (e.getValue().getCustomData() == null) continue;
            @SuppressWarnings("unchecked")
            List<Integer> choices = (List<Integer>) e.getValue().getCustomData().get(KEY);
            if (choices != null && choices.size() == 3) {
                all.put(e.getKey(), new ArrayList<>(choices));
            }
        }

        if (all.isEmpty()) return scores;

        Map<String, Integer> sums = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : all.entrySet()) {
            sums.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).sum());
        }

        int min = sums.values().stream().min(Integer::compareTo).orElse(0);
        for (Map.Entry<String, Integer> e : sums.entrySet()) {
            scores.put(e.getKey(), e.getValue() == min ? e.getValue() + 5 : e.getValue());
        }

        Map<Integer, Long> freq = all.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.groupingBy(n -> n, Collectors.counting()));

        long max = freq.values().stream().max(Long::compareTo).orElse(0L);
        List<Integer> modes = freq.entrySet().stream()
            .filter(e -> e.getValue() == max)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (modes.size() == 1) {
            int mode = modes.get(0);
            for (Map.Entry<String, List<Integer>> e : all.entrySet()) {
                long cnt = e.getValue().stream().filter(n -> n == mode).count();
                if (cnt > 0) {
                    scores.put(e.getKey(), scores.get(e.getKey()) - mode * (int) cnt);
                }
            }
        }

        return scores;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(Map<String, String> submissions, int currentRound) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR002";
    }
}
