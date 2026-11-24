package dev.customcore.boss;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

public record BossDefinition(
        String id,
        EntityType type,
        String name,
        double health,
        double damage,
        double speed,
        double knockbackResistance,
        boolean fireImmune,
        BossBar.Color barColor,
        BossBar.Overlay barOverlay,
        double barRange,
        double targetRange,
        long attackDelayTicks,
        Map<String, String> equipment,
        List<AttackSlot> attacks,
        List<Phase> phases,
        List<Drop> drops,
        boolean clearDrops,
        int exp,
        String deathMessage
) {

    public record AttackSlot(String type, BossAttack attack, long cooldownTicks,
                             double minHp, double maxHp, double range) {
    }

    public record Phase(double at, String message, String sound,
                        double speedMultiplier, double damageMultiplier) {
    }

    /** Entweder weapon (Waffen-ID) oder material gesetzt. */
    public record Drop(String weapon, Material material, int amount, double chance) {
    }
}
