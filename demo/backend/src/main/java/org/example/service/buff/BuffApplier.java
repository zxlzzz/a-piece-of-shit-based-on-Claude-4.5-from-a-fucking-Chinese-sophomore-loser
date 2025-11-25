package org.example.service.buff;

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.Buff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BuffApplier {

    /**
     * 应用 Buff 到分数
     * @param buff Buff 对象
     * @param score 当前分数
     * @param playerId 玩家ID
     * @return [新分数, 是否生效(1/0)]
     */
    public int[] applyBuff(Buff buff, int score, String playerId) {
        // 检查触发条件
        if (!shouldTrigger(buff, score)) {
            log.debug("Buff {} 未触发 (playerId={}, score={})",
                    buff.getType(), playerId, score);
            return new int[]{score, 0};
        }

        int newScore = calculateScore(buff, score);

        log.info("Buff {} 生效: {} -> {} (playerId={})",
                buff.getType(), score, newScore, playerId);

        return new int[]{newScore, 1};
    }

    /**
     * 检查 Buff 是否应该触发
     */
    private boolean shouldTrigger(Buff buff, int score) {
        // 如果配置了 "仅在得分时触发"
        if (buff.getParams() != null &&
                Boolean.TRUE.equals(buff.getParams().get("triggerOnScore"))) {
            return score > 0;
        }
        return true;
    }

    /**
     * 计算应用 Buff 后的分数
     */
    private int calculateScore(Buff buff, int score) {
        switch (buff.getType()) {
            case MULTIPLIER:
                return (int) (score * buff.getValue());
            case ADD:
                // 允许负值（扣分）
                return score + buff.getValue().intValue();
            default:
                log.warn("未知 Buff 类型: {}", buff.getType());
                return score;
        }
    }
}