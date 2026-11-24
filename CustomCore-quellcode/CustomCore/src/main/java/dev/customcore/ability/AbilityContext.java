package dev.customcore.ability;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * @param user   der Spieler mit der Waffe
 * @param target getroffenes bzw. anvisiertes Lebewesen (kann null sein)
 * @param item   die Waffe
 * @param damage zugefügter Schaden (nur bei Hit-Effekten, sonst 0)
 * @param origin Ort des Geschehens
 */
public record AbilityContext(Player user, LivingEntity target, ItemStack item, double damage, Location origin) {
}
