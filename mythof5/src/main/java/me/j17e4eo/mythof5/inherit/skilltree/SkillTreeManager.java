package me.j17e4eo.mythof5.inherit.skilltree;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkill;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks upgrade points and selections for goblin skills.
 */
public class SkillTreeManager {

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final Map<UUID, PlayerSkillData> data = new HashMap<>();
    private final Map<String, List<SkillUpgrade>> upgrades = new LinkedHashMap<>();
    private File file;
    private YamlConfiguration config;

    public SkillTreeManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        registerDefaults();
    }

    private void registerDefaults() {
        register("rush_strike", new SkillUpgrade("range", "강화 A: 폭발 돌파", "돌진 각도를 넓히고 피해량을 증가시킨다.", 1),
                new SkillUpgrade("bleed", "강화 B: 출혈 단검", "중첩되는 출혈을 부여하여 장기전을 유리하게 만든다.", 1));
        register("pursuit_mark", new SkillUpgrade("sprint", "강화 A: 폭주 추격", "표식이 유지되는 동안 이동 속도 보너스가 크게 증가한다.", 1),
                new SkillUpgrade("rupture", "강화 B: 약점 노출", "표식이 새겨진 적이 받는 피해가 상승한다.", 1));
        register("vision_twist", new SkillUpgrade("dread", "강화 A: 공포 왜곡", "시야 왜곡이 적을 짧게 기절시킨다.", 1),
                new SkillUpgrade("chill", "강화 B: 냉기 분출", "주변 물과 용암을 얼려 임시 장벽을 만든다.", 1));
        register("veil_break", new SkillUpgrade("flare", "강화 A: 파수꾼의 비명", "은신한 대상이 드러날 때 순간 피해를 입는다.", 1),
                new SkillUpgrade("mark", "강화 B: 추적자", "드러난 대상에게 추적 표식이 자동 부여된다.", 1));
        register("ember_boost", new SkillUpgrade("wildfire", "강화 A: 폭발 버프", "버프 범위가 넓어지고 주변 적에게 화상 피해를 입힌다.", 1),
                new SkillUpgrade("ward", "강화 B: 화염 수호", "강화된 아군에게 잠시 보호막을 부여한다.", 1));
        register("ember_recovery", new SkillUpgrade("purify", "강화 A: 정화", "추가 상태 이상을 제거하고 재생량이 증가한다.", 1),
                new SkillUpgrade("ember_wall", "강화 B: 불씨 장막", "회복 범위에 주기적인 화염 장막을 깐다.", 1));
        register("weapon_overdrive", new SkillUpgrade("forge_heat", "강화 A: 단조 가열", "무기 강화 시간이 증가하고 화염 피해가 추가된다.", 1),
                new SkillUpgrade("impact", "강화 B: 충격 파동", "강화된 무기가 추가 충격파 피해를 발생시킨다.", 1));
        register("legendary_summon", new SkillUpgrade("eternal", "강화 A: 지속 신화", "전설 무기의 지속 시간이 늘어나고 치명타가 강화된다.", 1),
                new SkillUpgrade("eruption", "강화 B: 불꽃 해방", "소환 시 화염 폭발이 일어나 적을 밀쳐낸다.", 1));
    }

    private void register(String skillKey, SkillUpgrade a, SkillUpgrade b) {
        List<SkillUpgrade> options = new ArrayList<>();
        options.add(a);
        options.add(b);
        upgrades.put(skillKey.toLowerCase(Locale.ROOT), Collections.unmodifiableList(options));
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "skills.yml");
        if (!file.exists()) {
            config = new YamlConfiguration();
            return;
        }
        config = YamlConfiguration.loadConfiguration(file);
        data.clear();
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }
        for (String key : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = playersSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                int points = section.getInt("points", 0);
                Map<String, String> selections = new HashMap<>();
                ConfigurationSection selectSection = section.getConfigurationSection("selections");
                if (selectSection != null) {
                    for (String skillKey : selectSection.getKeys(false)) {
                        selections.put(skillKey, selectSection.getString(skillKey));
                    }
                }
                data.put(uuid, new PlayerSkillData(points, selections));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in skills.yml: " + key);
            }
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("players", null);
        for (Map.Entry<UUID, PlayerSkillData> entry : data.entrySet()) {
            String key = entry.getKey().toString();
            PlayerSkillData profile = entry.getValue();
            config.set("players." + key + ".points", profile.getPoints());
            for (Map.Entry<String, String> selection : profile.getSelections().entrySet()) {
                config.set("players." + key + ".selections." + selection.getKey(), selection.getValue());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save skills.yml: " + e.getMessage());
        }
    }

    public int getAvailablePoints(UUID uuid) {
        return data.computeIfAbsent(uuid, ignored -> new PlayerSkillData(0, new HashMap<>())).getPoints();
    }

    public void addPoints(UUID uuid, int amount, String reason) {
        if (amount <= 0) {
            return;
        }
        PlayerSkillData profile = data.computeIfAbsent(uuid, ignored -> new PlayerSkillData(0, new HashMap<>()));
        profile.addPoints(amount);
        save();
        Player player = Bukkit.getPlayer(uuid);
        String reasonText = (reason == null || reason.isBlank())
                ? messages.format("goblin.skilltree.reason.unknown")
                : reason;
        if (player != null) {
            player.sendMessage(messages.format("goblin.skilltree.gain", Map.of(
                    "points", String.valueOf(amount),
                    "reason", reasonText
            )));
        }
        chronicleManager.logEvent(ChronicleEventType.SKILL,
                messages.format("chronicle.skill.point", Map.of(
                        "player", player != null ? player.getName() : uuid.toString(),
                        "points", String.valueOf(amount),
                        "reason", reasonText
                )), player != null ? List.of(player) : List.of());
    }

    public Optional<SkillUpgrade> findUpgrade(String skillKey, String upgradeId) {
        List<SkillUpgrade> options = upgrades.get(skillKey.toLowerCase(Locale.ROOT));
        if (options == null) {
            return Optional.empty();
        }
        for (SkillUpgrade upgrade : options) {
            if (upgrade.getId().equalsIgnoreCase(upgradeId)) {
                return Optional.of(upgrade);
            }
        }
        return Optional.empty();
    }

    public List<SkillUpgrade> getUpgrades(String skillKey) {
        return upgrades.getOrDefault(skillKey.toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    public String getSelectedUpgrade(UUID uuid, String skillKey) {
        PlayerSkillData profile = data.get(uuid);
        if (profile == null) {
            return null;
        }
        return profile.getSelections().get(skillKey.toLowerCase(Locale.ROOT));
    }

    public boolean selectUpgrade(Player player, GoblinSkill skill, SkillUpgrade upgrade) {
        UUID uuid = player.getUniqueId();
        PlayerSkillData profile = data.computeIfAbsent(uuid, ignored -> new PlayerSkillData(0, new HashMap<>()));
        String key = skill.getKey();
        if (profile.getSelections().containsKey(key)) {
            player.sendMessage(messages.format("goblin.skilltree.already"));
            return false;
        }
        if (profile.getPoints() < upgrade.getCost()) {
            player.sendMessage(messages.format("goblin.skilltree.not_enough"));
            return false;
        }
        profile.addPoints(-upgrade.getCost());
        profile.getSelections().put(key, upgrade.getId());
        save();
        chronicleManager.logEvent(ChronicleEventType.SKILL_UPGRADE,
                messages.format("chronicle.skill.upgrade", Map.of(
                        "player", player.getName(),
                        "skill", skill.getDisplayName(),
                        "upgrade", upgrade.getName()
                )), List.of(player));
        player.sendMessage(messages.format("goblin.skilltree.unlocked", Map.of(
                "skill", skill.getDisplayName(),
                "upgrade", upgrade.getName()
        )));
        return true;
    }

    public List<String> describeTree(Player player, GoblinAspect aspect) {
        UUID uuid = player.getUniqueId();
        PlayerSkillData profile = data.computeIfAbsent(uuid, ignored -> new PlayerSkillData(0, new HashMap<>()));
        List<String> lines = new ArrayList<>();
        lines.add(messages.format("goblin.skilltree.header", Map.of(
                "points", String.valueOf(profile.getPoints()))));
        for (GoblinSkill skill : aspect.getSkills()) {
            if (skill.getCategory() == null || skill.getCategory().name().equals("PASSIVE")) {
                continue;
            }
            lines.add(messages.format("goblin.skilltree.skill", Map.of(
                    "name", skill.getDisplayName(),
                    "key", skill.getKey())));
            String selected = profile.getSelections().get(skill.getKey());
            if (selected != null) {
                Optional<SkillUpgrade> upgrade = findUpgrade(skill.getKey(), selected);
                upgrade.ifPresent(value -> lines.add("  - " + value.getName() + " : " + value.getDescription()));
            } else {
                lines.add(messages.format("goblin.skilltree.none"));
                for (SkillUpgrade option : getUpgrades(skill.getKey())) {
                    lines.add(messages.format("goblin.skilltree.option", Map.of(
                            "id", option.getId(),
                            "name", option.getName(),
                            "desc", option.getDescription(),
                            "cost", String.valueOf(option.getCost())
                    )));
                }
            }
        }
        return lines;
    }

    private static final class PlayerSkillData {
        private int points;
        private final Map<String, String> selections;

        private PlayerSkillData(int points, Map<String, String> selections) {
            this.points = points;
            this.selections = selections;
        }

        private int getPoints() {
            return points;
        }

        private Map<String, String> getSelections() {
            return selections;
        }

        private void addPoints(int delta) {
            this.points = Math.max(0, this.points + delta);
        }
    }
}
