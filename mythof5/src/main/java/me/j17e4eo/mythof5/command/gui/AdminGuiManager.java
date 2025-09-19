package me.j17e4eo.mythof5.command.gui;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.boss.BossInstance;
import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.omens.OmenManager;
import me.j17e4eo.mythof5.omens.OmenStage;
import me.j17e4eo.mythof5.relic.RelicManager;
import me.j17e4eo.mythof5.relic.RelicType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds and handles the administrator chest GUIs. Every option in
 * {@code /myth admin} can be triggered visually for easier access during live
 * events. All labels are intentionally written in Korean to match the rest of
 * the plugin.
 */
public class AdminGuiManager implements Listener {

    private static final String ACTION_OPEN_MAIN = "open_main";
    private static final String ACTION_OPEN_BOSS = "open_boss";
    private static final String ACTION_OPEN_BOSS_SPAWN = "open_boss_spawn";
    private static final String ACTION_OPEN_BOSS_END = "open_boss_end";
    private static final String ACTION_SPAWN_ENTITY = "spawn_entity";
    private static final String ACTION_SHOW_BOSS_LIST = "show_boss_list";
    private static final String ACTION_END_BOSS = "end_boss";
    private static final String ACTION_OPEN_INHERIT = "open_inherit";
    private static final String ACTION_OPEN_ASPECT = "open_aspect";
    private static final String ACTION_SELECT_ASPECT = "select_aspect";
    private static final String ACTION_OPEN_RELIC = "open_relic";
    private static final String ACTION_SELECT_RELIC_TYPE = "select_relic_type";
    private static final String ACTION_SELECT_PLAYER = "select_player";
    private static final String ACTION_OPEN_OMEN = "open_omen";
    private static final String ACTION_TRIGGER_OMEN = "trigger_omen";
    private static final String ACTION_SHOW_CHRONICLE = "show_chronicle";
    private static final String ACTION_SHOW_BALANCE = "show_balance";
    private static final String ACTION_GIVE_GUIDE = "give_guide";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_PAGE = "page";
    private static final String ACTION_CHOOSE_RELIC = "choose_relic";

    private final Mythof5 plugin;
    private final BossManager bossManager;
    private final AspectManager aspectManager;
    private final RelicManager relicManager;
    private final ChronicleManager chronicleManager;
    private final OmenManager omenManager;
    private final BalanceTable balanceTable;
    private final Messages messages;

    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final ItemStack fillerItem;
    private final List<EntityType> spawnableTypes;
    private final Map<OmenStage, String> omenDisplayNames;

    public AdminGuiManager(Mythof5 plugin,
                           BossManager bossManager,
                           AspectManager aspectManager,
                           RelicManager relicManager,
                           ChronicleManager chronicleManager,
                           OmenManager omenManager,
                           BalanceTable balanceTable,
                           Messages messages) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.aspectManager = aspectManager;
        this.relicManager = relicManager;
        this.chronicleManager = chronicleManager;
        this.omenManager = omenManager;
        this.balanceTable = balanceTable;
        this.messages = messages;
        this.actionKey = new NamespacedKey(plugin, "admin_action");
        this.valueKey = new NamespacedKey(plugin, "admin_value");
        this.fillerItem = createFiller();
        this.spawnableTypes = Arrays.stream(EntityType.values())
                .filter(type -> type.isAlive() && type.isSpawnable())
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        this.omenDisplayNames = Map.of(
                OmenStage.STARSHIFT, "성좌 이동",
                OmenStage.GHOST_FIRE, "혼불 폭주",
                OmenStage.SKYBREAK, "천공 파열"
        );
    }

    public void openMainMenu(Player player) {
        Inventory inventory = createInventory(MenuType.MAIN, 27, Component.text("Myth 관리자 도구", NamedTextColor.DARK_AQUA));
        fillWithFiller(inventory);
        inventory.setItem(10, createActionItem(Material.NETHER_STAR,
                Component.text("보스 관리", NamedTextColor.GOLD),
                List.of(Component.text("보스를 소환하거나 정리합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_BOSS, null));
        inventory.setItem(12, createActionItem(Material.TOTEM_OF_UNDYING,
                Component.text("계승자 관리", NamedTextColor.GOLD),
                List.of(Component.text("도깨비 계승자를 지정하거나 해제합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_INHERIT, null));
        inventory.setItem(14, createActionItem(Material.END_CRYSTAL,
                Component.text("설화 관리", NamedTextColor.GOLD),
                List.of(Component.text("설화를 지급하거나 회수합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_RELIC, null));
        inventory.setItem(16, createActionItem(Material.CLOCK,
                Component.text("징조 단계", NamedTextColor.GOLD),
                List.of(Component.text("현재 단계를 강제로 발동합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_OMEN, null));

        inventory.setItem(20, createActionItem(Material.BOOK,
                Component.text("연대기 확인", NamedTextColor.AQUA),
                List.of(Component.text("최근 기록 10개를 채팅으로 보여줍니다.", NamedTextColor.GRAY)),
                ACTION_SHOW_CHRONICLE, null));
        inventory.setItem(21, createActionItem(Material.PAPER,
                Component.text("균형표", NamedTextColor.AQUA),
                List.of(Component.text("현재 밸런스 지표를 확인합니다.", NamedTextColor.GRAY)),
                ACTION_SHOW_BALANCE, null));
        inventory.setItem(22, createActionItem(Material.WRITTEN_BOOK,
                Component.text("가이드북 지급", NamedTextColor.AQUA),
                List.of(Component.text("관리자 가이드를 인벤토리에 넣습니다.", NamedTextColor.GRAY)),
                ACTION_GIVE_GUIDE, null));
        inventory.setItem(24, createActionItem(Material.ARROW,
                Component.text("보스 목록 출력", NamedTextColor.GREEN),
                List.of(Component.text("현재 소환된 보스 정보를 확인합니다.", NamedTextColor.GRAY)),
                ACTION_SHOW_BOSS_LIST, null));
        player.openInventory(inventory);
    }

    public void openBossMenu(Player player) {
        Inventory inventory = createInventory(MenuType.BOSS, 27, Component.text("보스 관리", NamedTextColor.DARK_RED));
        fillWithFiller(inventory);
        inventory.setItem(11, createActionItem(Material.DRAGON_EGG,
                Component.text("보스 소환", NamedTextColor.GOLD),
                List.of(Component.text("현재 위치에 보스를 소환합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_BOSS_SPAWN, null));
        inventory.setItem(13, createActionItem(Material.BOOK,
                Component.text("보스 목록 출력", NamedTextColor.AQUA),
                List.of(Component.text("활성화된 보스를 채팅으로 확인합니다.", NamedTextColor.GRAY)),
                ACTION_SHOW_BOSS_LIST, null));
        inventory.setItem(15, createActionItem(Material.BARRIER,
                Component.text("보스 강제 종료", NamedTextColor.RED),
                List.of(Component.text("선택한 보스를 즉시 제거합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_BOSS_END, null));
        inventory.setItem(26, createActionItem(Material.ARROW,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("메인 메뉴로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public void openBossSpawnMenu(Player player, int requestedPage) {
        int pageSize = 45;
        int maxPage = Math.max(0, (spawnableTypes.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = createInventory(MenuType.BOSS_SPAWN, 54,
                Component.text("보스 소환", NamedTextColor.GOLD), page, AdminAction.NONE, null, null);
        fillWithFiller(inventory);
        int startIndex = page * pageSize;
        for (int slot = 0; slot < pageSize; slot++) {
            int index = startIndex + slot;
            if (index >= spawnableTypes.size()) {
                break;
            }
            EntityType type = spawnableTypes.get(index);
            inventory.setItem(slot, createActionItem(resolveEntityIcon(type),
                    Component.text(entityDisplayName(type), NamedTextColor.GOLD),
                    List.of(Component.text("좌클릭: 현재 위치에 소환", NamedTextColor.GRAY)),
                    ACTION_SPAWN_ENTITY, type.name()));
        }

        if (page > 0) {
            inventory.setItem(45, createActionItem(Material.ARROW,
                    Component.text("이전", NamedTextColor.YELLOW), List.of(),
                    ACTION_PAGE, String.valueOf(page - 1)));
        }
        inventory.setItem(48, createActionItem(Material.PAPER,
                Component.text((page + 1) + " / " + (maxPage + 1), NamedTextColor.WHITE), List.of(),
                null, null));
        inventory.setItem(49, createActionItem(Material.BARRIER,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("보스 관리로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        if (page < maxPage) {
            inventory.setItem(53, createActionItem(Material.ARROW,
                    Component.text("다음", NamedTextColor.YELLOW), List.of(),
                    ACTION_PAGE, String.valueOf(page + 1)));
        }
        player.openInventory(inventory);
    }

    public void openBossEndMenu(Player player) {
        Inventory inventory = createInventory(MenuType.BOSS_END, 27,
                Component.text("보스 강제 종료", NamedTextColor.DARK_RED));
        fillWithFiller(inventory);
        List<BossInstance> bosses = bossManager.getActiveBosses().stream()
                .sorted(Comparator.comparingInt(BossInstance::getId))
                .toList();
        if (bosses.isEmpty()) {
            inventory.setItem(13, createActionItem(Material.GRAY_DYE,
                    Component.text("활성화된 보스 없음", NamedTextColor.GRAY),
                    List.of(Component.text("보스가 존재하지 않습니다.", NamedTextColor.DARK_GRAY)),
                    null, null));
        } else {
            int slot = 10;
            for (BossInstance instance : bosses) {
                LivingEntity entity = instance.getEntity();
                double health = entity.isValid() ? entity.getHealth() : 0.0D;
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + instance.getId(), NamedTextColor.GRAY));
                lore.add(Component.text("종류: " + instance.getEntityType().name(), NamedTextColor.GRAY));
                lore.add(Component.text(String.format(Locale.KOREA, "체력: %.1f / %.1f", health, instance.getMaxHealth()), NamedTextColor.GRAY));
                lore.add(Component.text("위치: " + formatLocation(entity.getLocation()), NamedTextColor.DARK_GRAY));
                inventory.setItem(slot++, createActionItem(Material.NETHER_STAR,
                        Component.text("#" + instance.getId() + " " + instance.getDisplayName(), NamedTextColor.GOLD),
                        lore,
                        ACTION_END_BOSS, String.valueOf(instance.getId())));
                if (slot == 17) {
                    slot = 19;
                }
            }
        }
        inventory.setItem(26, createActionItem(Material.ARROW,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("보스 관리로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public void openInheritMenu(Player player) {
        Inventory inventory = createInventory(MenuType.INHERIT, 27,
                Component.text("계승자 관리", NamedTextColor.GOLD));
        fillWithFiller(inventory);
        inventory.setItem(11, createActionItem(Material.EMERALD,
                Component.text("강제 계승", NamedTextColor.GREEN),
                List.of(Component.text("선택한 플레이어에게 계승자를 지정합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_ASPECT, AdminAction.INHERIT_SET.name()));
        inventory.setItem(15, createActionItem(Material.BARRIER,
                Component.text("계승 해제", NamedTextColor.RED),
                List.of(Component.text("선택한 갈래의 계승자를 해제합니다.", NamedTextColor.GRAY)),
                ACTION_OPEN_ASPECT, AdminAction.INHERIT_CLEAR.name()));
        inventory.setItem(26, createActionItem(Material.ARROW,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("메인 메뉴로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public void openAspectSelectMenu(Player player, AdminAction action) {
        Inventory inventory = createInventory(MenuType.ASPECT_SELECT, 27,
                Component.text("도깨비 갈래 선택", NamedTextColor.GOLD), 0, action, null, null);
        fillWithFiller(inventory);
        int slot = 10;
        for (GoblinAspect aspect : GoblinAspect.values()) {
            List<Component> lore = new ArrayList<>();
            String inheritor = aspectManager.getInheritorName(aspect);
            if (inheritor == null || inheritor.isBlank()) {
                lore.add(Component.text("현재 계승자: 없음", NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("현재 계승자: " + inheritor, NamedTextColor.GRAY));
            }
            lore.add(Component.text("성향: " + aspect.getPersonality(), NamedTextColor.DARK_GRAY));
            inventory.setItem(slot++, createActionItem(resolveAspectIcon(aspect),
                    Component.text(aspect.getDisplayName(), NamedTextColor.GOLD),
                    lore,
                    ACTION_SELECT_ASPECT, aspect.name()));
        }
        inventory.setItem(26, createActionItem(Material.ARROW,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("계승자 관리로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public void openPlayerSelectMenu(Player player, AdminAction action, GoblinAspect aspect, RelicType relicType, int requestedPage) {
        int pageSize = 45;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int maxPage = Math.max(0, (players.size() - 1) / Math.max(1, pageSize));
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = createInventory(MenuType.PLAYER_SELECT, 54,
                Component.text("플레이어 선택", NamedTextColor.GOLD), page, action, aspect, relicType);
        fillWithFiller(inventory);
        int start = page * pageSize;
        if (players.isEmpty()) {
            inventory.setItem(22, createActionItem(Material.GRAY_DYE,
                    Component.text("온라인 플레이어 없음", NamedTextColor.GRAY),
                    List.of(Component.text("대상이 접속 중이어야 합니다.", NamedTextColor.DARK_GRAY)),
                    null, null));
        } else {
            for (int slot = 0; slot < pageSize; slot++) {
                int index = start + slot;
                if (index >= players.size()) {
                    break;
                }
                Player target = players.get(index);
                inventory.setItem(slot, createPlayerItem(target, action));
            }
        }
        if (page > 0) {
            inventory.setItem(45, createActionItem(Material.ARROW,
                    Component.text("이전", NamedTextColor.YELLOW), List.of(),
                    ACTION_PAGE, String.valueOf(page - 1)));
        }
        inventory.setItem(48, createActionItem(Material.PAPER,
                Component.text((page + 1) + " / " + (maxPage + 1), NamedTextColor.WHITE), List.of(),
                null, null));
        inventory.setItem(49, createActionItem(Material.BARRIER,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("이전 메뉴로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        if (page < maxPage) {
            inventory.setItem(53, createActionItem(Material.ARROW,
                    Component.text("다음", NamedTextColor.YELLOW), List.of(),
                    ACTION_PAGE, String.valueOf(page + 1)));
        }
        player.openInventory(inventory);
    }

    public void openRelicMenu(Player player) {
        Inventory inventory = createInventory(MenuType.RELIC, 27,
                Component.text("설화 관리", NamedTextColor.GOLD));
        fillWithFiller(inventory);
        inventory.setItem(11, createActionItem(Material.CHEST,
                Component.text("설화 지급", NamedTextColor.GREEN),
                List.of(Component.text("설화를 선택한 플레이어에게 지급합니다.", NamedTextColor.GRAY)),
                ACTION_SELECT_RELIC_TYPE, AdminAction.RELIC_GIVE.name()));
        inventory.setItem(15, createActionItem(Material.HOPPER,
                Component.text("설화 회수", NamedTextColor.RED),
                List.of(Component.text("플레이어에게서 설화를 제거합니다.", NamedTextColor.GRAY)),
                ACTION_SELECT_RELIC_TYPE, AdminAction.RELIC_REMOVE.name()));
        inventory.setItem(26, createActionItem(Material.ARROW,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("메인 메뉴로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public void openRelicTypeMenu(Player player, AdminAction action) {
        Inventory inventory = createInventory(MenuType.RELIC_TYPE_SELECT, 54,
                Component.text("설화 선택", NamedTextColor.GOLD), 0, action, null, null);
        fillWithFiller(inventory);
        int slot = 10;
        for (RelicType type : RelicType.values()) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(type.getEffect(), NamedTextColor.DARK_GRAY));
            inventory.setItem(slot++, createActionItem(Material.END_CRYSTAL,
                    Component.text(type.getDisplayName(), NamedTextColor.GOLD),
                    lore,
                    ACTION_CHOOSE_RELIC, type.name()));
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        inventory.setItem(49, createActionItem(Material.BARRIER,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("설화 관리로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public void openOmenMenu(Player player) {
        Inventory inventory = createInventory(MenuType.OMEN, 27,
                Component.text("징조 단계", NamedTextColor.LIGHT_PURPLE));
        fillWithFiller(inventory);
        inventory.setItem(10, createActionItem(Material.AMETHYST_SHARD,
                Component.text(omenDisplayNames.get(OmenStage.STARSHIFT), NamedTextColor.GOLD),
                List.of(Component.text("초기 징조를 강제로 일으킵니다.", NamedTextColor.GRAY)),
                ACTION_TRIGGER_OMEN, OmenStage.STARSHIFT.name()));
        inventory.setItem(13, createActionItem(Material.SOUL_TORCH,
                Component.text(omenDisplayNames.get(OmenStage.GHOST_FIRE), NamedTextColor.GOLD),
                List.of(Component.text("중간 단계 징조를 발동합니다.", NamedTextColor.GRAY)),
                ACTION_TRIGGER_OMEN, OmenStage.GHOST_FIRE.name()));
        inventory.setItem(16, createActionItem(Material.LIGHTNING_ROD,
                Component.text(omenDisplayNames.get(OmenStage.SKYBREAK), NamedTextColor.GOLD),
                List.of(Component.text("최후의 징조를 즉시 일으킵니다.", NamedTextColor.GRAY)),
                ACTION_TRIGGER_OMEN, OmenStage.SKYBREAK.name()));
        inventory.setItem(26, createActionItem(Material.ARROW,
                Component.text("뒤로", NamedTextColor.YELLOW),
                List.of(Component.text("메인 메뉴로 돌아갑니다.", NamedTextColor.GRAY)),
                ACTION_BACK, null));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        ItemMeta meta = current.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String action = container.get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        String value = container.get(valueKey, PersistentDataType.STRING);
        switch (action) {
            case ACTION_OPEN_MAIN -> openMainMenu(player);
            case ACTION_OPEN_BOSS -> openBossMenu(player);
            case ACTION_OPEN_BOSS_SPAWN -> openBossSpawnMenu(player, 0);
            case ACTION_OPEN_BOSS_END -> openBossEndMenu(player);
            case ACTION_SPAWN_ENTITY -> {
                if (value != null) {
                    spawnBossFromGui(player, value);
                }
            }
            case ACTION_SHOW_BOSS_LIST -> {
                player.closeInventory();
                bossManager.sendBossList(player);
            }
            case ACTION_END_BOSS -> {
                if (value != null) {
                    tryEndBoss(player, value);
                    openBossEndMenu(player);
                }
            }
            case ACTION_OPEN_INHERIT -> {
                if (value == null) {
                    openInheritMenu(player);
                } else {
                    AdminAction next = parseAdminAction(value, AdminAction.NONE);
                    if (next == AdminAction.INHERIT_SET || next == AdminAction.INHERIT_CLEAR) {
                        openAspectSelectMenu(player, next);
                    }
                }
            }
            case ACTION_OPEN_ASPECT -> {
                AdminAction next = parseAdminAction(value, AdminAction.NONE);
                if (next == AdminAction.INHERIT_SET || next == AdminAction.INHERIT_CLEAR) {
                    openAspectSelectMenu(player, next);
                }
            }
            case ACTION_SELECT_ASPECT -> {
                if (value == null) {
                    return;
                }
                GoblinAspect aspect = GoblinAspect.valueOf(value);
                if (holder.getAction() == AdminAction.INHERIT_CLEAR) {
                    clearInherit(player, aspect);
                    openAspectSelectMenu(player, AdminAction.INHERIT_CLEAR);
                } else if (holder.getAction() == AdminAction.INHERIT_SET) {
                    openPlayerSelectMenu(player, AdminAction.INHERIT_SET, aspect, null, 0);
                }
            }
            case ACTION_SELECT_RELIC_TYPE -> {
                AdminAction relicAction = parseAdminAction(value, AdminAction.NONE);
                if (relicAction != AdminAction.NONE) {
                    openRelicTypeMenu(player, relicAction);
                }
            }
            case ACTION_CHOOSE_RELIC -> {
                if (value != null) {
                    try {
                        RelicType type = RelicType.valueOf(value);
                        openPlayerSelectMenu(player, holder.getAction(), null, type, 0);
                    } catch (IllegalArgumentException ignored) {
                        player.sendMessage(Component.text(messages.format("relic.unknown"), NamedTextColor.RED));
                    }
                }
            }
            case ACTION_SELECT_PLAYER -> {
                if (value == null) {
                    return;
                }
                handlePlayerSelection(player, holder, value);
            }
            case ACTION_OPEN_RELIC -> openRelicMenu(player);
            case ACTION_OPEN_OMEN -> openOmenMenu(player);
            case ACTION_TRIGGER_OMEN -> {
                if (value != null) {
                    triggerOmen(player, value);
                }
            }
            case ACTION_SHOW_CHRONICLE -> {
                player.closeInventory();
                showChronicle(player);
            }
            case ACTION_SHOW_BALANCE -> {
                player.closeInventory();
                showBalance(player);
            }
            case ACTION_GIVE_GUIDE -> {
                player.closeInventory();
                giveGuide(player);
            }
            case ACTION_BACK -> openParent(player, holder);
            case ACTION_PAGE -> {
                if (value != null) {
                    int target = parseInt(value, holder.getPage());
                    openPage(player, holder, target);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void openParent(Player player, AdminMenuHolder holder) {
        switch (holder.getType()) {
            case BOSS -> openMainMenu(player);
            case BOSS_SPAWN, BOSS_END -> openBossMenu(player);
            case INHERIT -> openMainMenu(player);
            case ASPECT_SELECT -> openInheritMenu(player);
            case PLAYER_SELECT -> {
                if (holder.getAction() == AdminAction.INHERIT_SET) {
                    openAspectSelectMenu(player, AdminAction.INHERIT_SET);
                } else if (holder.getAction() == AdminAction.RELIC_GIVE || holder.getAction() == AdminAction.RELIC_REMOVE) {
                    openRelicTypeMenu(player, holder.getAction());
                } else {
                    openMainMenu(player);
                }
            }
            case RELIC -> openMainMenu(player);
            case RELIC_TYPE_SELECT -> openRelicMenu(player);
            case OMEN -> openMainMenu(player);
            default -> openMainMenu(player);
        }
    }

    private void openPage(Player player, AdminMenuHolder holder, int page) {
        switch (holder.getType()) {
            case BOSS_SPAWN -> openBossSpawnMenu(player, page);
            case PLAYER_SELECT -> openPlayerSelectMenu(player, holder.getAction(), holder.getAspect(), holder.getRelicType(), page);
            default -> {
            }
        }
    }

    private void handlePlayerSelection(Player admin, AdminMenuHolder holder, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            admin.sendMessage(Component.text(messages.format("commands.common.player_not_online"), NamedTextColor.RED));
            return;
        }
        switch (holder.getAction()) {
            case INHERIT_SET -> {
                GoblinAspect aspect = Objects.requireNonNull(holder.getAspect(), "Aspect not set for inherit");
                setInherit(admin, aspect, target);
                openAspectSelectMenu(admin, AdminAction.INHERIT_SET);
            }
            case RELIC_GIVE -> {
                RelicType type = Objects.requireNonNull(holder.getRelicType(), "Relic type not set for give");
                giveRelic(admin, target, type);
                openPlayerSelectMenu(admin, AdminAction.RELIC_GIVE, null, type, holder.getPage());
            }
            case RELIC_REMOVE -> {
                RelicType type = Objects.requireNonNull(holder.getRelicType(), "Relic type not set for remove");
                removeRelic(admin, target, type);
                openPlayerSelectMenu(admin, AdminAction.RELIC_REMOVE, null, type, holder.getPage());
            }
            default -> {
            }
        }
    }

    private void spawnBossFromGui(Player player, String entityName) {
        EntityType type;
        try {
            type = EntityType.valueOf(entityName);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text(messages.format("commands.myth.invalid_entity"), NamedTextColor.RED));
            return;
        }
        if (!type.isAlive()) {
            player.sendMessage(Component.text(messages.format("commands.myth.entity_not_living"), NamedTextColor.RED));
            return;
        }
        Location location = player.getLocation();
        String baseName = plugin.getConfig().getString("boss.name", "태초의 도깨비");
        String finalName = baseName;
        int counter = 2;
        while (bossManager.hasActiveBossWithName(finalName)) {
            finalName = baseName + " #" + counter++;
        }
        BossInstance instance = bossManager.spawnBoss(type, finalName, null, null, location);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", String.valueOf(instance.getId()));
        placeholders.put("location", formatLocation(instance.getEntity().getLocation()));
        placeholders.put("type", type.name());
        placeholders.put("name", instance.getDisplayName());
        player.sendMessage(Component.text(messages.format("commands.myth.spawn_success", placeholders), NamedTextColor.GOLD));
    }

    private void tryEndBoss(Player player, String idString) {
        try {
            int id = Integer.parseInt(idString);
            boolean result = bossManager.endBoss(id);
            if (result) {
                player.sendMessage(Component.text(messages.format("commands.myth.end_success"), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(messages.format("commands.myth.end_not_found"), NamedTextColor.RED));
            }
        } catch (NumberFormatException ex) {
            player.sendMessage(Component.text(messages.format("commands.myth.invalid_boss_id"), NamedTextColor.RED));
        }
    }

    private void setInherit(Player admin, GoblinAspect aspect, Player target) {
        aspectManager.setInheritor(aspect, target, true, messages.format("chronicle.inherit.force", Map.of(
                "player", target.getName(),
                "aspect", aspect.getDisplayName()
        )));
        admin.sendMessage(Component.text(messages.format("commands.admin.inherit_success", Map.of(
                "player", target.getName(),
                "aspect", aspect.getDisplayName()
        )), NamedTextColor.GOLD));
    }

    private void clearInherit(Player admin, GoblinAspect aspect) {
        String inheritor = aspectManager.getInheritorName(aspect);
        aspectManager.clearInheritor(aspect, true, inheritor);
        admin.sendMessage(Component.text(messages.format("commands.admin.clear_success", Map.of(
                "aspect", aspect.getDisplayName()
        )), NamedTextColor.GOLD));
    }

    private void giveRelic(Player admin, Player target, RelicType type) {
        boolean granted = relicManager.grantRelic(target, type, true);
        if (granted) {
            admin.sendMessage(Component.text(messages.format("commands.admin.relic_grant", Map.of(
                    "player", target.getName(),
                    "relic", type.getDisplayName()
            )), NamedTextColor.GOLD));
        } else {
            admin.sendMessage(Component.text(messages.format("commands.admin.relic_duplicate"), NamedTextColor.YELLOW));
        }
    }

    private void removeRelic(Player admin, Player target, RelicType type) {
        boolean removed = relicManager.removeRelic(target, type);
        if (removed) {
            admin.sendMessage(Component.text(messages.format("commands.admin.relic_removed", Map.of(
                    "player", target.getName(),
                    "relic", type.getDisplayName()
            )), NamedTextColor.GOLD));
        } else {
            admin.sendMessage(Component.text(messages.format("commands.admin.relic_not_owned"), NamedTextColor.RED));
        }
    }

    private void triggerOmen(Player player, String stageName) {
        if (omenManager == null) {
            player.sendMessage(Component.text("징조 시스템이 비활성화되어 있습니다.", NamedTextColor.RED));
            return;
        }
        try {
            OmenStage stage = OmenStage.valueOf(stageName);
            String display = omenDisplayNames.getOrDefault(stage, stage.name());
            omenManager.trigger(stage, display + " (관리자 발동)");
            player.sendMessage(Component.text("징조 " + display + " 단계를 발동했습니다.", NamedTextColor.GOLD));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text(messages.format("commands.admin.omen_unknown"), NamedTextColor.RED));
        }
    }

    private void showChronicle(Player player) {
        List<String> lines = chronicleManager.formatRecent(10);
        player.sendMessage(Component.text("==== 최근 연대기 ====", NamedTextColor.GOLD));
        for (String line : lines) {
            player.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    private void showBalance(Player player) {
        player.sendMessage(Component.text("==== 균형 지표 ====", NamedTextColor.GOLD));
        for (String line : balanceTable.format()) {
            player.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    private void giveGuide(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.BookMeta bookMeta)) {
            player.sendMessage(Component.text(messages.format("commands.myth.guide.error"), NamedTextColor.RED));
            return;
        }
        bookMeta.setTitle(messages.format("commands.myth.guide.title"));
        bookMeta.setAuthor(messages.format("commands.myth.guide.author"));
        Map<String, String> placeholders = Map.of("command", "/myth");
        List<Component> pages = new ArrayList<>();
        for (String line : messages.formatList("commands.myth.guide.pages", placeholders)) {
            pages.add(Component.text(line, NamedTextColor.BLACK));
        }
        if (!pages.isEmpty()) {
            bookMeta.addPages(pages.toArray(new Component[0]));
        }
        book.setItemMeta(bookMeta);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(book);
        if (overflow.isEmpty()) {
            player.sendMessage(Component.text(messages.format("commands.myth.guide.received"), NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text(messages.format("commands.myth.guide.dropped"), NamedTextColor.YELLOW));
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    private Inventory createInventory(MenuType type, int size, Component title) {
        return createInventory(type, size, title, 0, AdminAction.NONE, null, null);
    }

    private Inventory createInventory(MenuType type, int size, Component title, int page, AdminAction action,
                                      GoblinAspect aspect, RelicType relicType) {
        AdminMenuHolder holder = new AdminMenuHolder(type, page, action, aspect, relicType);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private void fillWithFiller(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillerItem.clone());
        }
    }

    private ItemStack createActionItem(Material material, Component name, List<Component> lore, String action, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        }
        if (value != null) {
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerItem(Player target, AdminAction action) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            meta = skullMeta;
        }
        meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN));
        List<Component> lore = new ArrayList<>();
        Set<GoblinAspect> aspects = aspectManager.getAspects(target.getUniqueId());
        if (!aspects.isEmpty()) {
            String joined = aspects.stream().map(GoblinAspect::getDisplayName).collect(Collectors.joining(", "));
            lore.add(Component.text("보유 갈래: " + joined, NamedTextColor.GRAY));
        }
        lore.add(Component.text("행동: " + describeAction(action), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, ACTION_SELECT_PLAYER);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, target.getName());
        item.setItemMeta(meta);
        return item;
    }

    private String describeAction(AdminAction action) {
        return switch (action) {
            case INHERIT_SET -> "계승 지정";
            case RELIC_GIVE -> "설화 지급";
            case RELIC_REMOVE -> "설화 회수";
            default -> "관리 작업";
        };
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private Material resolveEntityIcon(EntityType type) {
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        if (egg != null) {
            return egg;
        }
        return switch (type) {
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            case WITHER -> Material.WITHER_SKELETON_SKULL;
            case WARDEN -> Material.SCULK_SENSOR;
            default -> Material.NETHER_STAR;
        };
    }

    private Material resolveAspectIcon(GoblinAspect aspect) {
        return switch (aspect) {
            case POWER -> Material.REDSTONE_BLOCK;
            case SPEED -> Material.FEATHER;
            case MISCHIEF -> Material.ENDER_PEARL;
            case FLAME -> Material.BLAZE_POWDER;
            case FORGE -> Material.ANVIL;
        };
    }

    private String entityDisplayName(EntityType type) {
        String key = type.getKey().getKey().replace('_', ' ');
        return "보스 소환: " + key.toUpperCase(Locale.KOREA);
    }

    private AdminAction parseAdminAction(String token, AdminAction fallback) {
        if (token == null) {
            return fallback;
        }
        try {
            return AdminAction.valueOf(token);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private int parseInt(String token, int fallback) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatLocation(Location location) {
        World world = location.getWorld();
        String worldName = world != null ? world.getName() : "unknown";
        return String.format(Locale.KOREA, "%s(%.1f, %.1f, %.1f)", worldName, location.getX(), location.getY(), location.getZ());
    }
}
