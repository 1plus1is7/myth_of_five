package me.j17e4eo.mythof5.inherit.aspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Describes the five primordial goblin aspects along with their skills and
 * descriptive data. Concrete behaviour is executed by the
 * {@link me.j17e4eo.mythof5.inherit.AspectManager}.
 */
public enum GoblinAspect {

    POWER("power", "힘의 도깨비", "오만, 직선적.", "현신 격파",
            buildPowerSkills(), 0.6D, 40),
    SPEED("speed", "속도의 도깨비", "예민, 집요.", "흔적 수집",
            buildSpeedSkills(), 0.5D, 40),
    MISCHIEF("mischief", "교란의 도깨비", "불가해, 냉정.", "계약",
            buildMischiefSkills(), 0.55D, 40),
    FLAME("flame", "정기의 도깨비", "나눔, 고독.", "불씨 의례",
            buildFlameSkills(), 0.7D, 40),
    FORGE("forge", "대장장이 도깨비", "장인, 창조적.", "특수 제작 의식",
            buildForgeSkills(), 0.5D, 40);

    private static final Map<GoblinAspect, String> PASSIVE_KEYS = new EnumMap<>(GoblinAspect.class);

    private final String key;
    private final String displayName;
    private final String personality;
    private final String acquisition;
    private final List<GoblinSkill> skills;
    private final double sharedPassiveRatio;
    private final int baseMaxHealth;

    GoblinAspect(String key, String displayName, String personality, String acquisition,
                 List<GoblinSkill> skills, double sharedPassiveRatio, int baseMaxHealth) {
        this.key = key;
        this.displayName = displayName;
        this.personality = personality;
        this.acquisition = acquisition;
        this.skills = skills;
        this.sharedPassiveRatio = sharedPassiveRatio;
        this.baseMaxHealth = baseMaxHealth;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPersonality() {
        return personality;
    }

    public String getAcquisition() {
        return acquisition;
    }

    public List<GoblinSkill> getSkills() {
        return skills;
    }

    public double getSharedPassiveRatio() {
        return sharedPassiveRatio;
    }

    public int getBaseMaxHealth() {
        return baseMaxHealth;
    }

    public String getPassiveKey() {
        return PASSIVE_KEYS.computeIfAbsent(this, aspect -> aspect.key + "_passive");
    }

    public static GoblinAspect fromKey(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (GoblinAspect aspect : values()) {
            if (aspect.key.equals(normalized) || aspect.displayName.equalsIgnoreCase(normalized)) {
                return aspect;
            }
        }
        return null;
    }

    private static List<GoblinSkill> buildPowerSkills() {
        List<GoblinSkill> list = new ArrayList<>();
        list.add(new GoblinSkill("rush_strike", "돌진 일격", GoblinSkillCategory.ACTIVE,
                35, 1.8D, 0.6D,
                "전방으로 폭발적으로 돌진하여 경로의 적에게 큰 피해를 입힌다.",
                "돌진 거리가 짧고 피해량이 감소한다."));
        list.add(new GoblinSkill("stagger_guard", "경직 저항", GoblinSkillCategory.PASSIVE,
                0, 1.0D, 0.6D,
                "강한 의지로 경직에 거의 걸리지 않는다.",
                "경직 저항이 일부만 부여된다."));
        return Collections.unmodifiableList(list);
    }

    private static List<GoblinSkill> buildSpeedSkills() {
        List<GoblinSkill> list = new ArrayList<>();
        list.add(new GoblinSkill("pursuit_mark", "추격 표식", GoblinSkillCategory.ACTIVE,
                30, 1.6D, 0.5D,
                "응시한 적에게 표식을 새겨 위치를 추적하며 본인에게 가속을 부여한다.",
                "표식 지속 시간이 짧고 가속 효과가 감소한다."));
        list.add(new GoblinSkill("scent_reader", "기척 감지", GoblinSkillCategory.PASSIVE,
                0, 1.0D, 0.5D,
                "주변의 발자취와 기척을 읽어 위치를 파악한다.",
                "기척 감지 범위와 빈도가 감소한다."));
        return Collections.unmodifiableList(list);
    }

    private static List<GoblinSkill> buildMischiefSkills() {
        List<GoblinSkill> list = new ArrayList<>();
        list.add(new GoblinSkill("vision_twist", "시야 왜곡", GoblinSkillCategory.ACTIVE,
                40, 1.7D, 0.55D,
                "주변 적의 시야를 일그러뜨리고 방향 감각을 잃게 한다.",
                "범위가 좁고 지속 시간이 짧다."));
        list.add(new GoblinSkill("veil_break", "은신 탐지", GoblinSkillCategory.UTILITY,
                25, 1.5D, 0.5D,
                "주변 은신한 존재를 끌어내어 위치를 드러낸다.",
                "감지 범위가 줄어들고 빛남 지속 시간이 짧다."));
        return Collections.unmodifiableList(list);
    }

    private static List<GoblinSkill> buildFlameSkills() {
        List<GoblinSkill> list = new ArrayList<>();
        list.add(new GoblinSkill("ember_boost", "불씨 강화", GoblinSkillCategory.ACTIVE,
                32, 1.6D, 0.7D,
                "주변 아군의 공격과 저항을 함께 끌어올린다.",
                "버프의 지속 시간과 강도가 줄어든다."));
        list.add(new GoblinSkill("ember_recovery", "단기 회복", GoblinSkillCategory.UTILITY,
                24, 1.4D, 0.65D,
                "타오르는 불씨로 짧은 시간 재생과 해제를 부여한다.",
                "회복량과 지속 시간이 줄어든다."));
        return Collections.unmodifiableList(list);
    }

    private static List<GoblinSkill> buildForgeSkills() {
        List<GoblinSkill> list = new ArrayList<>();
        list.add(new GoblinSkill("weapon_overdrive", "무기 강화", GoblinSkillCategory.ACTIVE,
                28, 1.7D, 0.5D,
                "주 무기를 단기간 폭발적인 성능으로 개량한다.",
                "강화 수치와 지속 시간이 감소한다."));
        list.add(new GoblinSkill("legendary_summon", "일시적 전설 무기 소환", GoblinSkillCategory.ACTIVE,
                60, 1.8D, 0.5D,
                "전설급 무기를 소환해 잠시 다룬다.",
                "소환 시간이 줄고 공격 강화가 감소한다."));
        return Collections.unmodifiableList(list);
    }
}
