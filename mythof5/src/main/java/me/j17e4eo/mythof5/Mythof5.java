package me.j17e4eo.mythof5;

import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.command.GoblinCommand;
import me.j17e4eo.mythof5.command.MythAdminCommand;
import me.j17e4eo.mythof5.command.RelicCommand;
import me.j17e4eo.mythof5.command.SquadCommand;
import me.j17e4eo.mythof5.command.gui.AdminGuiManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.HunterListener;
import me.j17e4eo.mythof5.hunter.HunterManager;
import me.j17e4eo.mythof5.hunter.ParadoxManager;
import me.j17e4eo.mythof5.hunter.command.HunterCommand;
import me.j17e4eo.mythof5.hunter.data.ArtifactGrade;
import me.j17e4eo.mythof5.hunter.math.SealMath;
import me.j17e4eo.mythof5.hunter.seal.SealManager;
import me.j17e4eo.mythof5.hunter.seal.command.SealCommand;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.InheritManager;
import me.j17e4eo.mythof5.inherit.GoblinWeaponManager;
import me.j17e4eo.mythof5.listener.BossListener;
import me.j17e4eo.mythof5.listener.PlayerListener;
import me.j17e4eo.mythof5.listener.SquadListener;
import me.j17e4eo.mythof5.omens.OmenManager;
import me.j17e4eo.mythof5.relic.RelicManager;
import me.j17e4eo.mythof5.relic.LoreFragmentManager;
import me.j17e4eo.mythof5.rift.RiftCommand;
import me.j17e4eo.mythof5.rift.RiftGameplayListener;
import me.j17e4eo.mythof5.rift.RiftManager;
import me.j17e4eo.mythof5.rift.RiftProtectionListener;
import me.j17e4eo.mythof5.squad.SquadManager;
import me.j17e4eo.mythof5.inherit.skilltree.SkillTreeManager;
import me.j17e4eo.mythof5.meta.MetaEventManager;
import me.j17e4eo.mythof5.weapon.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Mythof5 extends JavaPlugin {

    private BossManager bossManager;
    private InheritManager inheritManager;
    private SquadManager squadManager;
    private Messages messages;
    private ChronicleManager chronicleManager;
    private RelicManager relicManager;
    private AspectManager aspectManager;
    private OmenManager omenManager;
    private BalanceTable balanceTable;
    private SkillTreeManager skillTreeManager;
    private MetaEventManager metaEventManager;
    private LoreFragmentManager loreFragmentManager;
    private HunterManager hunterManager;
    private ParadoxManager paradoxManager;
    private SealManager sealManager;
    private AdminGuiManager adminGuiManager;
    private GoblinWeaponManager goblinWeaponManager;
    private WeaponManager weaponManager;
    private RiftManager riftManager;
    private boolean doubleJumpEnabled;
    private double doubleJumpVerticalVelocity;
    private double doubleJumpForwardMultiplier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerConfigDefaults();
        loadDoubleJumpSettings();

        messages = new Messages(this);
        chronicleManager = new ChronicleManager(this, messages);
        chronicleManager.load();
        balanceTable = new BalanceTable();
        skillTreeManager = new SkillTreeManager(this, messages, chronicleManager);
        skillTreeManager.load();
        metaEventManager = new MetaEventManager(this, messages);
        relicManager = new RelicManager(this, messages, chronicleManager, balanceTable, skillTreeManager, metaEventManager);
        relicManager.load();
        loreFragmentManager = new LoreFragmentManager(this, messages, relicManager);
        loreFragmentManager.setMetaEventManager(metaEventManager);
        metaEventManager.setLoreFragmentManager(loreFragmentManager);
        omenManager = new OmenManager(this, messages, chronicleManager);

        aspectManager = new AspectManager(this, messages, chronicleManager, relicManager, omenManager,
                skillTreeManager, balanceTable, metaEventManager);
        aspectManager.load();

        goblinWeaponManager = new GoblinWeaponManager(this, messages, aspectManager);
        aspectManager.setWeaponManager(goblinWeaponManager);

        weaponManager = new WeaponManager(this);

        inheritManager = new InheritManager(this, messages, aspectManager);
        inheritManager.load();

        bossManager = new BossManager(this, inheritManager, aspectManager, messages);
        squadManager = new SquadManager(this, messages);
        squadManager.load();

        riftManager = new RiftManager(this);
        riftManager.load();

        double fatigueWindowHours = getConfig().getDouble("hunter.release.fatigue_window_hours", 1.0D);
        long fatigueWindowMillis = (long) (fatigueWindowHours * 60D * 60D * 1000D);
        SealMath sealMath = new SealMath(
                getConfig().getDouble("hunter.release.low", 0.0052D),
                getConfig().getDouble("hunter.release.base", 0.1835D),
                getConfig().getDouble("hunter.release.slope", 0.012D),
                getConfig().getDouble("hunter.release.cap", 0.45D),
                fatigueWindowMillis,
                getConfig().getDouble("hunter.release.fatigue_min", 0.7D),
                getConfig().getDouble("hunter.release.fatigue_max", 1.2D)
        );
        Map<ArtifactGrade, Double> gaugeOverrides = new EnumMap<>(ArtifactGrade.class);
        ConfigurationSection gaugeSection = getConfig().getConfigurationSection("hunter.gauge.gain");
        for (ArtifactGrade grade : ArtifactGrade.values()) {
            double value = grade.getDefaultGaugeGain();
            if (gaugeSection != null && gaugeSection.isDouble(grade.name())) {
                value = gaugeSection.getDouble(grade.name());
            }
            gaugeOverrides.put(grade, value);
        }
        double sealPatchValue = getConfig().getDouble("hunter.seal.patch_value", 20.0D);
        double deathDecay = getConfig().getDouble("hunter.seal.death_decay", 10.0D);
        double witnessRadius = getConfig().getDouble("hunter.event.witness_radius", 64.0D);
        long broadcastCooldownMillis = (long) (getConfig().getDouble("hunter.event.broadcast_cooldown", 600D) * 1000L);
        ConfigurationSection thresholdSection = getConfig().getConfigurationSection("hunter.omens.thresholds");
        int longThreshold = thresholdSection != null ? thresholdSection.getInt("long", 2) : 2;
        int mediumThreshold = thresholdSection != null ? thresholdSection.getInt("medium", 4) : 4;
        int lateThreshold = thresholdSection != null ? thresholdSection.getInt("late", 5) : 5;

        hunterManager = new HunterManager(this, messages, chronicleManager, sealMath,
                sealPatchValue, deathDecay, witnessRadius, broadcastCooldownMillis,
                longThreshold, mediumThreshold, lateThreshold, gaugeOverrides);
        hunterManager.load();
        hunterManager.setAspectManager(aspectManager);
        hunterManager.setInheritManager(inheritManager);
        aspectManager.setHunterManager(hunterManager);

        sealManager = new SealManager(this, messages, hunterManager, aspectManager);
        aspectManager.setSealManager(sealManager);
        inheritManager.setHunterManager(hunterManager);
        inheritManager.setSealManager(sealManager);

        long ritualWindow = getConfig().getLong("hunter.paradox.ritual_window", 600L);
        double failureScale = getConfig().getDouble("hunter.paradox.failure_scale", 1.5D);
        paradoxManager = new ParadoxManager(this, messages, chronicleManager, hunterManager, ritualWindow, failureScale);
        hunterManager.setParadoxManager(paradoxManager);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BossListener(this, bossManager), this);
        PlayerListener playerListener = new PlayerListener(bossManager, inheritManager, aspectManager,
                doubleJumpEnabled, doubleJumpVerticalVelocity, doubleJumpForwardMultiplier);
        pluginManager.registerEvents(playerListener, this);
        pluginManager.registerEvents(inheritManager, this);
        pluginManager.registerEvents(goblinWeaponManager, this);
        pluginManager.registerEvents(weaponManager, this);
        pluginManager.registerEvents(aspectManager, this);
        pluginManager.registerEvents(relicManager, this);
        pluginManager.registerEvents(loreFragmentManager, this);
        pluginManager.registerEvents(new SquadListener(squadManager, getConfig().getBoolean("squad.friendly_fire", false), messages), this);
        pluginManager.registerEvents(new HunterListener(hunterManager), this);
        pluginManager.registerEvents(sealManager, this);
        pluginManager.registerEvents(new RiftGameplayListener(riftManager), this);
        pluginManager.registerEvents(new RiftProtectionListener(riftManager), this);

        adminGuiManager = new AdminGuiManager(this, bossManager, aspectManager, relicManager,
                chronicleManager, omenManager, balanceTable, messages);
        pluginManager.registerEvents(adminGuiManager, this);

        registerCommands();

        inheritManager.reapplyToOnlinePlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            aspectManager.handlePlayerJoin(player);
            hunterManager.handleJoin(player);
            sealManager.handleJoin(player);
            relicManager.handleJoin(player);
        }
        bossManager.initializeBossBars();
        playerListener.initializeExistingPlayers();
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.shutdown();
        }
        if (inheritManager != null) {
            inheritManager.save();
        }
        if (aspectManager != null) {
            aspectManager.save();
        }
        if (relicManager != null) {
            relicManager.save();
        }
        if (skillTreeManager != null) {
            skillTreeManager.save();
        }
        if (chronicleManager != null) {
            chronicleManager.save();
        }
        if (squadManager != null) {
            squadManager.save();
            squadManager.saveInvites();
        }
        if (omenManager != null) {
            omenManager.shutdown();
        }
        if (hunterManager != null) {
            hunterManager.save();
            hunterManager.shutdown();
        }
        if (sealManager != null) {
            sealManager.shutdown();
        }
        if (riftManager != null) {
            riftManager.shutdown();
        }

        if (doubleJumpEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
        }
    }

    private void registerCommands() {
        PluginCommand mythCommand = Objects.requireNonNull(getCommand("myth"), "Command myth not defined in plugin.yml");
        MythAdminCommand mythAdminCommand = new MythAdminCommand(this, bossManager, inheritManager, aspectManager,
                relicManager, chronicleManager, omenManager, balanceTable, messages, adminGuiManager);
        mythCommand.setExecutor(mythAdminCommand);
        mythCommand.setTabCompleter(mythAdminCommand);

        PluginCommand squadCommand = Objects.requireNonNull(getCommand("squad"), "Command squad not defined in plugin.yml");
        SquadCommand squadExecutor = new SquadCommand(squadManager, messages);
        squadCommand.setExecutor(squadExecutor);
        squadCommand.setTabCompleter(squadExecutor);

        PluginCommand goblinCommand = Objects.requireNonNull(getCommand("goblin"), "Command goblin not defined in plugin.yml");
        GoblinCommand goblinExecutor = new GoblinCommand(aspectManager, messages, skillTreeManager);
        goblinCommand.setExecutor(goblinExecutor);
        goblinCommand.setTabCompleter(goblinExecutor);

        PluginCommand relicCommand = Objects.requireNonNull(getCommand("relic"), "Command relic not defined in plugin.yml");
        RelicCommand relicExecutor = new RelicCommand(relicManager, messages, loreFragmentManager);
        relicCommand.setExecutor(relicExecutor);
        relicCommand.setTabCompleter(relicExecutor);

        PluginCommand hunterCommand = Objects.requireNonNull(getCommand("hunter"), "Command hunter not defined in plugin.yml");
        HunterCommand hunterExecutor = new HunterCommand(this, hunterManager, messages);
        hunterCommand.setExecutor(hunterExecutor);
        hunterCommand.setTabCompleter(hunterExecutor);

        PluginCommand sealCommand = Objects.requireNonNull(getCommand("seal"), "Command seal not defined in plugin.yml");
        SealCommand sealExecutor = new SealCommand(sealManager, messages);
        sealCommand.setExecutor(sealExecutor);
        sealCommand.setTabCompleter(sealExecutor);

        PluginCommand riftCommand = Objects.requireNonNull(getCommand("rift"), "Command rift not defined in plugin.yml");
        RiftCommand riftExecutor = new RiftCommand(riftManager);
        riftCommand.setExecutor(riftExecutor);
        riftCommand.setTabCompleter(riftExecutor);
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public InheritManager getInheritManager() {
        return inheritManager;
    }

    public SquadManager getSquadManager() {
        return squadManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public ChronicleManager getChronicleManager() {
        return chronicleManager;
    }

    public RelicManager getRelicManager() {
        return relicManager;
    }

    public AspectManager getAspectManager() {
        return aspectManager;
    }

    public OmenManager getOmenManager() {
        return omenManager;
    }

    public BalanceTable getBalanceTable() {
        return balanceTable;
    }

    public HunterManager getHunterManager() {
        return hunterManager;
    }

    public RiftManager getRiftManager() {
        return riftManager;
    }

    public void broadcast(String message) {
        Component component = Component.text().append(Component.text("「", NamedTextColor.GOLD))
                .append(Component.text(message, NamedTextColor.GOLD))
                .append(Component.text("」", NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(component);
    }

    private void registerConfigDefaults() {
        FileConfiguration config = getConfig();
        config.addDefault("boss.hp_default", 10000D);
        config.addDefault("boss.armor_default", 50D);
        config.addDefault("boss.name", "태초의 도깨비");
        config.addDefault("inherit.power_key", "dokkaebi.core");
        config.addDefault("inherit.buffs", List.of(
                "generic.max_health:set:40",
                "generic.movement_speed:add:0.03",
                "generic.attack_damage:add:2"
        ));
        config.addDefault("inherit.announce", true);
        config.addDefault("inherit.transformation.default.scale_multiplier", 2.0D);
        config.addDefault("inherit.transformation.default.attack_bonus", 6.0D);
        config.addDefault("inherit.transformation.default.speed_bonus", 0.0D);
        config.addDefault("inherit.transformation.aspects.power.scale_multiplier", 2.4D);
        config.addDefault("inherit.transformation.aspects.power.attack_bonus", 9.0D);
        config.addDefault("inherit.transformation.aspects.power.speed_bonus", 0.02D);
        config.addDefault("inherit.transformation.aspects.speed.scale_multiplier", 1.8D);
        config.addDefault("inherit.transformation.aspects.speed.attack_bonus", 4.0D);
        config.addDefault("inherit.transformation.aspects.speed.speed_bonus", 0.08D);
        config.addDefault("inherit.transformation.aspects.mischief.scale_multiplier", 1.9D);
        config.addDefault("inherit.transformation.aspects.mischief.attack_bonus", 5.0D);
        config.addDefault("inherit.transformation.aspects.mischief.speed_bonus", 0.05D);
        config.addDefault("inherit.transformation.aspects.flame.scale_multiplier", 2.1D);
        config.addDefault("inherit.transformation.aspects.flame.attack_bonus", 5.5D);
        config.addDefault("inherit.transformation.aspects.flame.speed_bonus", 0.04D);
        config.addDefault("inherit.transformation.aspects.forge.scale_multiplier", 2.35D);
        config.addDefault("inherit.transformation.aspects.forge.attack_bonus", 8.0D);
        config.addDefault("inherit.transformation.aspects.forge.speed_bonus", -0.01D);
        config.addDefault("goblin.triggers.power.boss_keywords", List.of("태초의 도깨비"));
        config.addDefault("goblin.triggers.speed.trace_items", List.of("SNOWBALL", "GOAT_HORN"));
        config.addDefault("goblin.triggers.mischief.contract_items", List.of("WRITTEN_BOOK", "WRITABLE_BOOK"));
        config.addDefault("goblin.triggers.mischief.contract_keywords", List.of("계약"));
        config.addDefault("goblin.triggers.flame.ritual_blocks", List.of("CAMPFIRE", "SOUL_CAMPFIRE"));
        config.addDefault("goblin.triggers.flame.catalyst", "BLAZE_POWDER");
        config.addDefault("goblin.triggers.forge.catalyst", "NETHERITE_INGOT");
        config.addDefault("goblin.triggers.forge.fuel", "BLAZE_ROD");
        config.addDefault("goblin.triggers.forge.station_blocks", List.of("SMITHING_TABLE", "ANVIL"));
        config.addDefault("goblin.triggers.forge.radius", 4);
        config.addDefault("squad.max_members", 5);
        config.addDefault("squad.friendly_fire", false);
        config.addDefault("movement.double_jump.enabled", true);
        config.addDefault("movement.double_jump.vertical_velocity", 0.9D);
        config.addDefault("movement.double_jump.forward_multiplier", 0.6D);
        config.addDefault("relic.fragments.drop_chance", 0.01D);
        config.addDefault("hunter.release.low", 0.0052D);
        config.addDefault("hunter.release.base", 0.1835D);
        config.addDefault("hunter.release.slope", 0.012D);
        config.addDefault("hunter.release.cap", 0.45D);
        config.addDefault("hunter.release.fatigue_window_hours", 1.0D);
        config.addDefault("hunter.release.fatigue_min", 0.7D);
        config.addDefault("hunter.release.fatigue_max", 1.2D);
        config.addDefault("hunter.gauge.gain.C", 7.0D);
        config.addDefault("hunter.gauge.gain.B", 9.0D);
        config.addDefault("hunter.gauge.gain.A", 11.0D);
        config.addDefault("hunter.gauge.gain.S", 12.0D);
        config.addDefault("hunter.seal.patch_value", 20.0D);
        config.addDefault("hunter.seal.death_decay", 10.0D);
        config.addDefault("hunter.event.witness_radius", 64.0D);
        config.addDefault("hunter.event.broadcast_cooldown", 600D);
        config.addDefault("hunter.omens.thresholds.long", 2);
        config.addDefault("hunter.omens.thresholds.medium", 4);
        config.addDefault("hunter.omens.thresholds.late", 5);
        config.addDefault("hunter.paradox.ritual_window", 600L);
        config.addDefault("hunter.paradox.failure_scale", 1.5D);
        config.addDefault("seal.enabled", true);
        config.addDefault("seal.allow_unseal", true);
        config.addDefault("seal.allow_upgrade", false);
        config.addDefault("seal.auto_popup", false);
        config.addDefault("seal.gui_rows", 3);
        config.addDefault("seal.weapon_whitelist", List.of("DIAMOND_SWORD", "NETHERITE_SWORD", "TRIDENT", "BOW", "CROSSBOW", "NETHERITE_AXE", "DIAMOND_AXE"));
        config.addDefault("seal.name_format", "{aspect} 봉인된 무기 (T{tier})");
        config.addDefault("seal.lore_lines", List.of("Aspect: {aspect}", "Tier: {tier}"));
        config.addDefault("drop.flame.amount", 1);
        config.addDefault("drop.flame.always", true);
        config.addDefault("drop.flame.announce", true);
        config.addDefault("drop.flame.protect_seconds", 8);
        config.addDefault("drop.flame.default_tier", 1);
        config.addDefault("aspect.force.tier.1.damage_multiplier", 1.15D);
        config.addDefault("aspect.force.tier.1.knockback", 0.45D);
        config.addDefault("aspect.speed.tier.1.speed_level", 0);
        config.addDefault("aspect.speed.tier.1.speed_duration", 120);
        config.addDefault("aspect.speed.tier.1.attack_speed", 0.1D);
        config.addDefault("aspect.mischief.tier.1.confusion_chance", 0.2D);
        config.addDefault("aspect.mischief.tier.1.confusion_seconds", 4);
        config.addDefault("aspect.mischief.tier.1.vanish_seconds", 2);
        config.addDefault("aspect.flame.tier.1.fire_seconds", 4);
        config.addDefault("aspect.flame.tier.1.ignite_chance", 0.8D);
        config.addDefault("aspect.flame.tier.1.bonus_damage", 1.0D);
        config.addDefault("aspect.flame.tier.1.glow_seconds", 2);
        config.addDefault("aspect.forge.tier.1.durability_reduction", 0.5D);
        config.addDefault("aspect.forge.tier.1.crit_chance", 0.15D);
        config.addDefault("aspect.forge.tier.1.crit_multiplier", 1.5D);
        config.addDefault("upgrade.cost.same_flames", 3);
        config.addDefault("upgrade.max_tier", 5);
        config.addDefault("upgrade.success_chance", 0.85D);
        config.options().copyDefaults(true);
        saveConfig();
    }

    private void loadDoubleJumpSettings() {
        FileConfiguration config = getConfig();
        doubleJumpEnabled = config.getBoolean("movement.double_jump.enabled", true);
        doubleJumpVerticalVelocity = config.getDouble("movement.double_jump.vertical_velocity", 0.9D);
        if (doubleJumpVerticalVelocity <= 0) {
            getLogger().warning("[Config] movement.double_jump.vertical_velocity 값은 0보다 커야 합니다. 기본값 0.9를 사용합니다.");
            doubleJumpVerticalVelocity = 0.9D;
        }

        doubleJumpForwardMultiplier = config.getDouble("movement.double_jump.forward_multiplier", 0.6D);
        if (doubleJumpForwardMultiplier < 0) {
            getLogger().warning("[Config] movement.double_jump.forward_multiplier 값은 음수가 될 수 없습니다. 기본값 0.6을 사용합니다.");
            doubleJumpForwardMultiplier = 0.6D;
        }
    }
}
