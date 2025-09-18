package me.j17e4eo.mythof5.relic;

import java.util.Locale;

/**
 * The smaller tales (설화) used as tactical relics on the server.
 */
public enum RelicType {
    MANGTAE_HALABEOM("mangtae_halabeom", "망태할아범", "단시간 상대를 포획해 묶어둔다."),
    WATER_GOBLIN("water_goblin", "물도깨비", "수중 전투에서 이동과 호흡이 강화된다."),
    GUMIHO_TAIL("gumiho_tail", "구미호 꼬리", "환영 분신을 만들어 교란한다."),
    DOOR_GUARD("door_guard", "문지기 도깨비", "문과 포탈을 봉인하여 추격을 저지한다."),
    DUNGAP_MOUSE("dungap_mouse", "둔갑쥐", "작은 동물로 둔갑해 위기를 모면한다."),
    PACK_ELDER("pack_elder", "등짐 노인", "자원을 빠르게 운반하고 획득 효율을 높인다."),
    TRICK_AND_BIND("trick_and_bind", "속임과 구속의 설화", "환영으로 적을 유인하고 동시에 결박한다.");

    private final String key;
    private final String displayName;
    private final String effect;

    RelicType(String key, String displayName, String effect) {
        this.key = key;
        this.displayName = displayName;
        this.effect = effect;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEffect() {
        return effect;
    }

    public static RelicType fromKey(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (RelicType type : values()) {
            if (type.key.equals(normalized) || type.displayName.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}
