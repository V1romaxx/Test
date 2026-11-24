package dev.customcore.boss;

import dev.customcore.CustomCorePlugin;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

public final class BossListener implements Listener {

    private final CustomCorePlugin plugin;

    public BossListener(CustomCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Bosse aus neu geladenen Chunks wieder aufnehmen. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            plugin.bosses().adopt(entity);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        BossInstance boss = plugin.bosses().get(event.getEntity().getUniqueId());
        if (boss != null) {
            plugin.bosses().handleDeath(boss, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        BossInstance boss = plugin.bosses().get(event.getEntity().getUniqueId());
        if (boss == null) {
            return;
        }
        BossBehavior behavior = plugin.bosses().behavior(boss.def().id());
        if (behavior != null) {
            behavior.onDamaged(boss, event);
        }
    }

    /** Boss-Projektile mit dem Tag cc_noblock zerstören keine Blöcke. */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity().getScoreboardTags().contains("cc_noblock")) {
            event.blockList().clear();
        }
    }
}
