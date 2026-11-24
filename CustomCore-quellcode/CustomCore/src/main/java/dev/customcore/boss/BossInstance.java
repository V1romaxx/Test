package dev.customcore.boss;

import dev.customcore.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Ein lebender Boss in der Welt. */
public final class BossInstance {

    private final BossDefinition def;
    private final LivingEntity entity;
    private final BossBar bar;
    private final long[] cooldownUntil;
    private final boolean[] phasesDone;
    private long lastAttackTick;

    public BossInstance(BossDefinition def, LivingEntity entity) {
        this.def = def;
        this.entity = entity;
        this.bar = BossBar.bossBar(Text.mm(def.name()), 1f, def.barColor(), def.barOverlay());
        this.cooldownUntil = new long[def.attacks().size()];
        this.phasesDone = new boolean[def.phases().size()];
    }

    public BossDefinition def() {
        return def;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BossBar bar() {
        return bar;
    }

    public double healthPercent() {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : Math.max(entity.getHealth(), 1);
        return entity.getHealth() / cap * 100.0;
    }

    public void updateBar() {
        bar.progress((float) Math.max(0.0, Math.min(1.0, healthPercent() / 100.0)));
    }

    public void hideBar() {
        for (Player player : entity.getWorld().getPlayers()) {
            player.hideBossBar(bar);
        }
    }

    long cooldownUntil(int index) {
        return cooldownUntil[index];
    }

    void setCooldownUntil(int index, long tick) {
        cooldownUntil[index] = tick;
    }

    long lastAttackTick() {
        return lastAttackTick;
    }

    void setLastAttackTick(long tick) {
        this.lastAttackTick = tick;
    }

    /** true, wenn die Phase bisher noch nicht ausgelöst wurde. */
    boolean markPhase(int index) {
        if (phasesDone[index]) {
            return false;
        }
        phasesDone[index] = true;
        return true;
    }
}
