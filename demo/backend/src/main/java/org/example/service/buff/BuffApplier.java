package org.example.service.buff;

import lombok.extern.slf4j.Slf4j;
import org.example.pojo.GameContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Buff 应用器（简化版 - demo 不需要 Buff 系统）
 */
@Component
@Slf4j
public class BuffApplier {

    /**
     * 应用 Buff（demo 版本直接返回原分数，不修改）
     */
    public Map<String, Integer> applyBuffs(GameContext context, Map<String, Integer> baseScores) {
        // demo 不需要 Buff 系统，直接返回基础分数
        return new HashMap<>(baseScores);
    }

    /**
     * 减少 Buff 持续时间（demo 版本空实现）
     */
    public void decreaseBuffDuration(GameContext context) {
        // demo 不需要 Buff 系统，空实现
    }
}
