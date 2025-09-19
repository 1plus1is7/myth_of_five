package me.j17e4eo.mythof5.balance;

import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkill;
import me.j17e4eo.mythof5.relic.RelicType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Aggregates combat telemetry for balance analysis. The data can be inspected
 * via commands or exported as CSV for offline tooling.
 */
public class BalanceTable {

    private int hunterWins;
    private int inheritorWins;
    private final Map<String, SkillUsage> skillUsage = new HashMap<>();
    private final Map<String, Integer> relicCounts = new HashMap<>();

    public synchronized void recordBattle(boolean hunterWon) {
        if (hunterWon) {
            hunterWins++;
        } else {
            inheritorWins++;
        }
    }

    public synchronized void recordSkillUsage(GoblinAspect aspect, GoblinSkill skill,
                                              String upgrade, boolean inheritor) {
        String key = aspect.getKey() + ":" + skill.getKey();
        SkillUsage usage = skillUsage.computeIfAbsent(key, ignored -> new SkillUsage(aspect, skill));
        usage.total++;
        if (inheritor) {
            usage.inheritorUses++;
        } else {
            usage.sharedUses++;
        }
        if (upgrade != null && !upgrade.isBlank()) {
            usage.upgrades.merge(upgrade.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
    }

    public synchronized void recordRelicEquipped(RelicType type) {
        relicCounts.merge(type.getKey(), 1, Integer::sum);
    }

    public synchronized List<String> formatSummary() {
        List<String> lines = new ArrayList<>();
        int totalBattles = hunterWins + inheritorWins;
        double hunterRate = totalBattles > 0 ? (hunterWins * 100.0D / totalBattles) : 0.0D;
        lines.add(String.format(Locale.KOREA, "[전투] 헌터 승률 %.1f%% (%d승 / %d패)", hunterRate, hunterWins, inheritorWins));
        lines.add("[전투] 기록된 스킬 사용 " + skillUsage.size() + "종");
        lines.add("[전투] 수집된 유물 장착 " + relicCounts.values().stream().mapToInt(Integer::intValue).sum() + "회");
        return lines;
    }

    public synchronized List<String> buildReport() {
        List<String> lines = new ArrayList<>(formatSummary());
        lines.add("---- 스킬 사용 통계 ----");
        List<SkillUsage> usageList = new ArrayList<>(skillUsage.values());
        usageList.sort((a, b) -> Integer.compare(b.total, a.total));
        for (SkillUsage usage : usageList) {
            StringJoiner joiner = new StringJoiner(", ");
            for (Map.Entry<String, Integer> entry : usage.upgrades.entrySet()) {
                joiner.add(entry.getKey() + " x" + entry.getValue());
            }
            String upgradesText = joiner.length() > 0 ? joiner.toString() : "강화 선택 없음";
            lines.add(String.format(Locale.KOREA,
                    "• %s.%s - 총 %d회 (계승자 %d / 공유 %d) [%s]",
                    usage.aspect.getKey(), usage.skill.getKey(), usage.total,
                    usage.inheritorUses, usage.sharedUses, upgradesText));
        }
        lines.add("---- 유물 장착률 ----");
        relicCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> lines.add(String.format(Locale.KOREA,
                        "• %s - %d회",
                        entry.getKey(), entry.getValue())));
        return lines;
    }

    public synchronized File exportCsv(File folder) throws IOException {
        folder.mkdirs();
        File file = new File(folder, "balance-report.csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("category,key,subkey,value");
            writer.printf(Locale.ROOT, "battle,wins,hunter,%d%n", hunterWins);
            writer.printf(Locale.ROOT, "battle,wins,inheritor,%d%n", inheritorWins);
            for (SkillUsage usage : skillUsage.values()) {
                String baseKey = usage.aspect.getKey() + ":" + usage.skill.getKey();
                writer.printf(Locale.ROOT, "skill,%s,total,%d%n", baseKey, usage.total);
                writer.printf(Locale.ROOT, "skill,%s,inheritor,%d%n", baseKey, usage.inheritorUses);
                writer.printf(Locale.ROOT, "skill,%s,shared,%d%n", baseKey, usage.sharedUses);
                for (Map.Entry<String, Integer> entry : usage.upgrades.entrySet()) {
                    writer.printf(Locale.ROOT, "skill,%s,upgrade_%s,%d%n",
                            baseKey, entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<String, Integer> entry : relicCounts.entrySet()) {
                writer.printf(Locale.ROOT, "relic,%s,equipped,%d%n", entry.getKey(), entry.getValue());
            }
        }
        return file;
    }

    public synchronized void reset() {
        hunterWins = 0;
        inheritorWins = 0;
        skillUsage.clear();
        relicCounts.clear();
    }

    private static final class SkillUsage {
        private final GoblinAspect aspect;
        private final GoblinSkill skill;
        private int total;
        private int inheritorUses;
        private int sharedUses;
        private final Map<String, Integer> upgrades = new HashMap<>();

        private SkillUsage(GoblinAspect aspect, GoblinSkill skill) {
            this.aspect = aspect;
            this.skill = skill;
        }
    }
}
