package dev.customcore.weapon;

import dev.customcore.ability.Ability;
import org.bukkit.Material;

import java.util.List;

public record WeaponDefinition(
        String id,
        Material material,
        String name,
        List<String> lore,
        String itemModel,
        double damage,
        double attackSpeed,
        boolean unbreakable,
        boolean glow,
        List<Ability> onHit,
        long hitCooldownMs,
        List<Ability> onRightClick,
        long rightClickCooldownMs
) {
}
