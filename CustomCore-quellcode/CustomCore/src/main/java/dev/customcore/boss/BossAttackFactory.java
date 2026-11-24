package dev.customcore.boss;

import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface BossAttackFactory {
    BossAttack create(ConfigurationSection cfg);
}
