package dev.customcore.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/** Kleine Helfer für Effekte, Rückstoß und Ziel-Filter. */
public final class Fx {

    private Fx() {
    }

    public static Particle particle(String name, Particle fallback) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return fallback;
        }
    }

    public static void ring(Location center, double radius, Particle particle) {
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            center.getWorld().spawnParticle(particle,
                    center.getX() + Math.cos(angle) * radius,
                    center.getY() + 0.2,
                    center.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    public static void knock(Entity from, LivingEntity to, double strength, double up) {
        Vector dir = to.getLocation().toVector().subtract(from.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 0);
        } else {
            dir.normalize().multiply(strength);
        }
        dir.setY(up);
        to.setVelocity(dir);
    }

    public static boolean targetable(Player p) {
        GameMode mode = p.getGameMode();
        return !p.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    public static void sound(Location loc, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) {
            return;
        }
        loc.getWorld().playSound(loc, key, volume, pitch);
    }
}
