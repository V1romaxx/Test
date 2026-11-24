package dev.customcore.boss;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;

/**
 * Optionale Code-Hooks für einen Boss (per BossManager#registerBehavior(bossId, ...)).
 * Damit lässt sich Verhalten umsetzen, das sich nicht per YAML beschreiben lässt.
 */
public interface BossBehavior {

    default void onSpawn(BossInstance boss) {
    }

    /** Alle 5 Ticks, solange der Boss geladen ist. */
    default void onTick(BossInstance boss, List<Player> targets) {
    }

    default void onPhase(BossInstance boss, BossDefinition.Phase phase) {
    }

    default void onDamaged(BossInstance boss, EntityDamageEvent event) {
    }

    default void onDeath(BossInstance boss) {
    }
}
