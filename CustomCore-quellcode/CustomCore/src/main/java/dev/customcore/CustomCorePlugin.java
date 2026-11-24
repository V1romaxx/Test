package dev.customcore;

import dev.customcore.ability.AbilityRegistry;
import dev.customcore.ability.BuiltInAbilities;
import dev.customcore.boss.BossListener;
import dev.customcore.boss.BossManager;
import dev.customcore.boss.BuiltInBossAttacks;
import dev.customcore.command.BossCommand;
import dev.customcore.command.WeaponCommand;
import dev.customcore.util.CooldownManager;
import dev.customcore.weapon.WeaponListener;
import dev.customcore.weapon.WeaponManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hauptklasse. Andere Plugins erreichen die API über {@link #get()}:
 * abilities().register(...), weapons().registerCode(...), bosses().registerAttackType(...)
 */
public final class CustomCorePlugin extends JavaPlugin {

    private static CustomCorePlugin instance;

    private CooldownManager cooldowns;
    private AbilityRegistry abilities;
    private WeaponManager weapons;
    private BossManager bosses;

    public static CustomCorePlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        for (String resource : new String[]{"weapons.yml", "bosses.yml"}) {
            if (!new java.io.File(getDataFolder(), resource).exists()) {
                saveResource(resource, false);
            }
        }

        cooldowns = new CooldownManager();
        abilities = new AbilityRegistry();
        BuiltInAbilities.registerAll(abilities);

        weapons = new WeaponManager(this);
        weapons.load();

        bosses = new BossManager(this);
        BuiltInBossAttacks.registerAll(bosses);
        bosses.load();

        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new BossListener(this), this);

        WeaponCommand weaponCommand = new WeaponCommand(this);
        getCommand("weapon").setExecutor(weaponCommand);
        getCommand("weapon").setTabCompleter(weaponCommand);
        BossCommand bossCommand = new BossCommand(this);
        getCommand("boss").setExecutor(bossCommand);
        getCommand("boss").setTabCompleter(bossCommand);

        bosses.start();
        getLogger().info("CustomCore aktiviert.");
    }

    @Override
    public void onDisable() {
        if (bosses != null) {
            bosses.shutdown();
        }
    }

    public void reloadAll() {
        weapons.load();
        bosses.reload();
    }

    public CooldownManager cooldowns() {
        return cooldowns;
    }

    public AbilityRegistry abilities() {
        return abilities;
    }

    public WeaponManager weapons() {
        return weapons;
    }

    public BossManager bosses() {
        return bosses;
    }
}
