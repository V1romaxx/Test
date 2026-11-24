package dev.customcore.ability;

import dev.customcore.CustomCorePlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AbilityRegistry {

    private final Map<String, AbilityFactory> factories = new HashMap<>();

    /** Eigene Ability-Typen registrieren, z.B. register("meteor", cfg -> ctx -> {...}). */
    public void register(String type, AbilityFactory factory) {
        factories.put(type.toLowerCase(Locale.ROOT), factory);
    }

    public Set<String> types() {
        return factories.keySet();
    }

    /** Liefert null, wenn der Typ unbekannt ist oder die Config fehlerhaft ist. */
    public Ability create(ConfigurationSection cfg) {
        String type = cfg.getString("type", "").toLowerCase(Locale.ROOT);
        AbilityFactory factory = factories.get(type);
        if (factory == null) {
            CustomCorePlugin.get().getLogger().warning("Unbekannter Ability-Typ: '" + type + "'");
            return null;
        }
        Ability base;
        try {
            base = factory.create(cfg);
        } catch (Exception ex) {
            CustomCorePlugin.get().getLogger().warning("Ability '" + type + "' fehlerhaft: " + ex.getMessage());
            return null;
        }
        double chance = cfg.getDouble("chance", 1.0);
        if (chance >= 1.0) {
            return base;
        }
        return ctx -> {
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                base.execute(ctx);
            }
        };
    }
}
