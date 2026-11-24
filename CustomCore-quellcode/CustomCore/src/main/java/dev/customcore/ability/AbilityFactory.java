package dev.customcore.ability;

import org.bukkit.configuration.ConfigurationSection;

/** Baut aus einem YAML-Abschnitt eine Ability. */
@FunctionalInterface
public interface AbilityFactory {
    Ability create(ConfigurationSection cfg);
}
