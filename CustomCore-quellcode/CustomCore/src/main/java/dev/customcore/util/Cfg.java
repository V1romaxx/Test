package dev.customcore.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Cfg {

    private Cfg() {
    }

    /** Wandelt eine YAML-Liste von Maps (- type: x ...) in ConfigurationSections um. */
    public static List<ConfigurationSection> sections(ConfigurationSection parent, String path) {
        List<ConfigurationSection> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (Map<?, ?> map : parent.getMapList(path)) {
            out.add(new MemoryConfiguration().createSection("s", map));
        }
        return out;
    }

    public static long secondsToMs(ConfigurationSection s, String path) {
        if (s == null) {
            return 0;
        }
        return (long) (s.getDouble(path, 0) * 1000.0);
    }
}
