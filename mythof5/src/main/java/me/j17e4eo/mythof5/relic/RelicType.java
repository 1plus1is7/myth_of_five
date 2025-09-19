package me.j17e4eo.mythof5.relic;

import java.util.Locale;

/**
 * The smaller tales (설화) used as tactical relics on the server.
 */
public enum RelicType {
    MANGTAE_HALABEOM("mangtae_halabeom", "망태할아범", "단시간 상대를 포획해 묶어둔다.",
            RelicRarity.RARE, "30% 이하 체력일 때 방패막이 발동", "파워 계열과 시너지"),
    WATER_GOBLIN("water_goblin", "물도깨비", "수중 전투에서 이동과 호흡이 강화된다.",
            RelicRarity.RARE, "물속 이동속도 및 호흡 무한", "속도 계열과 시너지"),
    GUMIHO_TAIL("gumiho_tail", "구미호 꼬리", "환영 분신을 만들어 교란한다.",
            RelicRarity.LEGENDARY, "분신을 소환해 적의 시선을 끈다", "장난 계열과 시너지"),
    DOOR_GUARD("door_guard", "문지기 도깨비", "문과 포탈을 봉인하여 추격을 저지한다.",
            RelicRarity.RARE, "문과 게이트를 잠그는 역장 생성", "수비형 루트"),
    DUNGAP_MOUSE("dungap_mouse", "둔갑쥐", "작은 동물로 둔갑해 위기를 모면한다.",
            RelicRarity.COMMON, "은신과 이동 속도가 소폭 상승", "생존 루트"),
    PACK_ELDER("pack_elder", "등짐 노인", "자원을 빠르게 운반하고 획득 효율을 높인다.",
            RelicRarity.COMMON, "인벤토리 가방 슬롯 9칸 추가", "채집 루트"),
    TRICK_AND_BIND("trick_and_bind", "속임과 구속의 설화", "환영으로 적을 유인하고 동시에 결박한다.",
            RelicRarity.LEGENDARY, "표식 대상에게 속박 사슬 생성", "장난/파워 복합"),
    TWILIGHT_CINDERS("twilight_cinders", "황혼의 잔불", "불씨와 그림자가 어우러진 칼날.",
            RelicRarity.MYTHIC, "저체력 적에게 연소+실명 부여", "화염/장난 하이브리드"),
    STORMCALLER_DRUM("stormcaller_drum", "폭풍을 부르는 북", "파도와 바람의 힘이 깃든 악기.",
            RelicRarity.LEGENDARY, "북을 두드리면 광역 속도/둔화 발동", "속도/수호 하이브리드"),
    FORGE_HEART("forge_heart", "단조의 심장", "대장장이 도깨비의 숨결이 남은 심장.",
            RelicRarity.MYTHIC, "철갑 보호막과 도깨비불 폭발", "화염/대장간 하이브리드");

    private final String key;
    private final String displayName;
    private final String effect;
    private final RelicRarity rarity;
    private final String ability;
    private final String synergy;

    RelicType(String key, String displayName, String effect,
              RelicRarity rarity, String ability, String synergy) {
        this.key = key;
        this.displayName = displayName;
        this.effect = effect;
        this.rarity = rarity;
        this.ability = ability;
        this.synergy = synergy;
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

    public RelicRarity getRarity() {
        return rarity;
    }

    public String getAbility() {
        return ability;
    }

    public String getSynergy() {
        return synergy;
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
