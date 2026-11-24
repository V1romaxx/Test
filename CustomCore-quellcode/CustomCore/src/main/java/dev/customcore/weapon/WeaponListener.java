package dev.customcore.weapon;

import dev.customcore.CustomCorePlugin;
import dev.customcore.ability.AbilityContext;
import dev.customcore.util.Text;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class WeaponListener implements Listener {

    private final CustomCorePlugin plugin;

    public WeaponListener(CustomCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (event.getCause() != DamageCause.ENTITY_ATTACK) {
            return;
        }
        WeaponManager weapons = plugin.weapons();
        if (weapons.isGuarded(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponDefinition def = weapons.definitionOf(item);
        if (def == null || def.onHit().isEmpty()) {
            return;
        }
        if (!plugin.cooldowns().tryUse(player.getUniqueId(), def.id() + ":hit", def.hitCooldownMs())) {
            return;
        }
        AbilityContext ctx = new AbilityContext(player, victim, item, event.getFinalDamage(), victim.getLocation());
        weapons.execute(player, def.onHit(), ctx);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        WeaponManager weapons = plugin.weapons();
        ItemStack item = event.getItem();
        WeaponDefinition def = weapons.definitionOf(item);
        if (def == null || def.onRightClick().isEmpty()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        String key = def.id() + ":click";
        long rest = plugin.cooldowns().remainingMs(player.getUniqueId(), key);
        if (rest > 0) {
            player.sendActionBar(Text.mm("<red>Cooldown: <white>"
                    + String.format(Locale.ROOT, "%.1f", rest / 1000.0) + "s"));
            return;
        }
        plugin.cooldowns().tryUse(player.getUniqueId(), key, def.rightClickCooldownMs());

        Entity looked = player.getTargetEntity(30);
        LivingEntity target = looked instanceof LivingEntity living ? living : null;
        AbilityContext ctx = new AbilityContext(player, target, item, 0, player.getLocation());
        weapons.execute(player, def.onRightClick(), ctx);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldowns().clear(event.getPlayer().getUniqueId());
    }
}
