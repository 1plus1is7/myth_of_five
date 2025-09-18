package me.j17e4eo.mythof5.balance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores balancing targets for quick reference via commands.
 */
public class BalanceTable {

    private final Map<String, String> metrics = new LinkedHashMap<>();

    public BalanceTable() {
        metrics.put("계승자 vs 일반", "승률 70% 이상");
        metrics.put("계승자 vs 설화 조합팀", "상황에 따라 50% 이하");
        metrics.put("설화 단독 효과", "소규모 전투 결정적 / 대규모 전투 보조");
        metrics.put("연대기 기록 정확도", "100% (실시간 검증)");
    }

    public List<String> format() {
        return metrics.entrySet().stream()
                .map(entry -> "• " + entry.getKey() + " : " + entry.getValue())
                .toList();
    }
}
