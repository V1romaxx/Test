package dev.customcore.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownManager {

    private final Map<String, Long> until = new HashMap<>();

    private static String key(UUID id, String name) {
        return id + ":" + name;
    }

    /** Verbleibende Zeit in ms (0 = bereit). */
    public long remainingMs(UUID id, String name) {
        Long end = until.get(key(id, name));
        if (end == null) {
            return 0;
        }
        long rest = end - System.currentTimeMillis();
        return Math.max(rest, 0);
    }

    /** Prüft den Cooldown und startet ihn bei Erfolg. */
    public boolean tryUse(UUID id, String name, long cooldownMs) {
        if (remainingMs(id, name) > 0) {
            return false;
        }
        if (cooldownMs > 0) {
            until.put(key(id, name), System.currentTimeMillis() + cooldownMs);
        }
        return true;
    }

    public void clear(UUID id) {
        String prefix = id + ":";
        until.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
