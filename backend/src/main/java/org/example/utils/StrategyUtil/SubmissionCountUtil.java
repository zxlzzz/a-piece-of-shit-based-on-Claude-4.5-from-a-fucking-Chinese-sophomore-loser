package org.example.utils.StrategyUtil;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 部分题需要
 * 传入 id:选项，返回选项:几人选择（long类型）
 */
public class SubmissionCountUtil {
    public static Map<String, Long> countChoices(Map<String, String> submissions){
        return submissions.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
