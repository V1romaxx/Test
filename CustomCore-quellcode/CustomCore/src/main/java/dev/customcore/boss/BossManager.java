package dev.customcore.boss;

import dev.customcore.CustomCorePlugin;
import dev.customcore.util.Cfg;
import dev.customcore.util.Fx;
import dev.customcore.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class BossManager {

    private static final long PERIOD = 5L;

    private final CustomCorePlugin plugin;
    private final NamespacedKey bossKey;
    private final Map<String, BossDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, BossDefinition> codeDefinitions = new LinkedHashMap<>();
    private final Map<String, BossAttackFactory> attackTypes = new HashMap<>();
    private final Map<String, BossBehavior> behaviors = new HashMap<>();
    private final Map<UUID, BossInstance> active = new HashMap<>();
    private BukkitTask task;
    private long tickCount;

    public BossManager(CustomCorePlugin plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "boss_id");
    }

    // ------------------------------------------------------------------ API

    /** Neuen Attacken-Typ registrieren (danach bosses().reload() aufrufen, falls bosses.yml ihn nutzt). */
    public void registerAttackType(String type, BossAttackFactory factory) {
        attackTypes.put(type.toLowerCase(Locale.ROOT), factory);
    }

    /** Code-Hooks für einen Boss. */
    public void registerBehavior(String bossId, BossBehavior behavior) {
        behaviors.put(bossId.toLowerCase(Locale.ROOT), behavior);
    }

    /** Boss komplett im Code definieren. */
    public void registerDefinition(BossDefinition def) {
        codeDefinitions.put(def.id(), def);
        definitions.put(def.id(), def);
    }

    public BossBehavior behavior(String bossId) {
        return behaviors.get(bossId);
    }

    public Collection<String> ids() {
        return definitions.keySet();
    }

    public int activeCount() {
        return active.size();
    }

    public BossInstance get(UUID entityId) {
        return active.get(entityId);
    }

    // ------------------------------------------------------------ Lifecycle

    public void load() {
        definitions.clear();
        definitions.putAll(codeDefinitions);
        File file = new File(plugin.getDataFolder(), "bosses.yml");
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("bosses");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                try {
                    BossDefinition def = parse(id, root.getConfigurationSection(id));
                    definitions.put(def.id(), def);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Boss '" + id + "' konnte nicht geladen werden: " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info(definitions.size() + " Boss-Definitionen geladen.");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
        adoptAll();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (BossInstance boss : active.values()) {
            boss.hideBar();
        }
        active.clear();
    }

    public void reload() {
        for (BossInstance boss : active.values()) {
            boss.hideBar();
        }
        active.clear();
        load();
        adoptAll();
    }

    private void adoptAll() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                adopt(entity);
            }
        }
    }

    /** Nimmt einen bereits existierenden Boss (z.B. nach Chunk-Load oder Neustart) wieder auf. */
    public void adopt(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        String id = living.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        if (id == null || active.containsKey(living.getUniqueId())) {
            return;
        }
        BossDefinition def = definitions.get(id);
        if (def == null) {
            return;
        }
        BossInstance boss = new BossInstance(def, living);
        active.put(living.getUniqueId(), boss);
        // bereits erreichte Phasen still wiederherstellen
        double hp = boss.healthPercent();
        for (int i = 0; i < def.phases().size(); i++) {
            if (hp <= def.phases().get(i).at() && boss.markPhase(i)) {
                applyPhase(boss, def.phases().get(i), false);
            }
        }
    }

    // ---------------------------------------------------------------- Spawn

    public BossInstance spawn(String id, Location loc) {
        BossDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            return null;
        }
        Entity raw = loc.getWorld().spawnEntity(loc, def.type());
        if (!(raw instanceof LivingEntity entity)) {
            raw.remove();
            return null;
        }
        entity.customName(Text.mm(def.name()));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.addScoreboardTag("cc_boss");

        setAttr(entity, Attribute.MAX_HEALTH, def.health());
        entity.setHealth(Math.min(def.health(), entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
        if (def.damage() > 0) {
            setAttr(entity, Attribute.ATTACK_DAMAGE, def.damage());
        }
        if (def.speed() > 0) {
            setAttr(entity, Attribute.MOVEMENT_SPEED, def.speed());
        }
        if (def.knockbackResistance() > 0) {
            setAttr(entity, Attribute.KNOCKBACK_RESISTANCE, def.knockbackResistance());
        }
        if (def.fireImmune()) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        applyEquipment(entity, def);
        entity.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, def.id());

        BossInstance boss = new BossInstance(def, entity);
        active.put(entity.getUniqueId(), boss);
        BossBehavior behavior = behaviors.get(def.id());
        if (behavior != null) {
            behavior.onSpawn(boss);
        }
        return boss;
    }

    private void applyEquipment(LivingEntity entity, BossDefinition def) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) {
            return;
        }
        for (Map.Entry<String, String> entry : def.equipment().entrySet()) {
            EquipmentSlot slot = switch (entry.getKey()) {
                case "head", "helmet" -> EquipmentSlot.HEAD;
                case "chest", "chestplate" -> EquipmentSlot.CHEST;
                case "legs", "leggings" -> EquipmentSlot.LEGS;
                case "feet", "boots" -> EquipmentSlot.FEET;
                case "mainhand" -> EquipmentSlot.HAND;
                case "offhand" -> EquipmentSlot.OFF_HAND;
                default -> null;
            };
            ItemStack item = equipmentItem(entry.getValue());
            if (slot == null || item == null) {
                plugin.getLogger().warning("Boss " + def.id() + ": ungültiges Equipment '" + entry.getKey()
                        + ": " + entry.getValue() + "'");
                continue;
            }
            eq.setItem(slot, item);
            eq.setDropChance(slot, 0f);
        }
    }

    private ItemStack equipmentItem(String value) {
        if (value == null) {
            return null;
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("weapon:")) {
            return plugin.weapons().create(value.substring(7));
        }
        Material material = Material.matchMaterial(value);
        return material != null && !material.isAir() ? new ItemStack(material) : null;
    }

    private static void setAttr(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public int killAll() {
        int count = active.size();
        for (BossInstance boss : new ArrayList<>(active.values())) {
            boss.hideBar();
            boss.entity().remove();
        }
        active.clear();
        return count;
    }

    // ----------------------------------------------------------------- Tick

    private void tick() {
        tickCount += PERIOD;
        for (BossInstance boss : new ArrayList<>(active.values())) {
            LivingEntity entity = boss.entity();
            if (!entity.isValid()) {
                // gestorben (wird im Death-Event behandelt) oder Chunk entladen
                boss.hideBar();
                active.remove(entity.getUniqueId());
                continue;
            }
            try {
                tickBoss(boss);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Fehler im Boss-Tick (" + boss.def().id() + ")", ex);
            }
        }
    }

    private void tickBoss(BossInstance boss) {
        BossDefinition def = boss.def();
        LivingEntity entity = boss.entity();

        // Bossbar
        boss.updateBar();
        double barRangeSq = def.barRange() * def.barRange();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= barRangeSq) {
                player.showBossBar(boss.bar());
            } else {
                player.hideBossBar(boss.bar());
            }
        }

        // Phasen
        double hp = boss.healthPercent();
        for (int i = 0; i < def.phases().size(); i++) {
            BossDefinition.Phase phase = def.phases().get(i);
            if (hp <= phase.at() && boss.markPhase(i)) {
                applyPhase(boss, phase, true);
            }
        }

        // Ziele
        List<Player> targets = new ArrayList<>(entity.getLocation().getNearbyPlayers(def.targetRange())
                .stream().filter(Fx::targetable).toList());
        targets.sort(Comparator.comparingDouble((Player p) -> p.getLocation().distanceSquared(entity.getLocation())));

        BossBehavior behavior = behaviors.get(def.id());
        if (behavior != null) {
            behavior.onTick(boss, targets);
        }
        if (targets.isEmpty()) {
            return;
        }
        if (entity instanceof Mob mob && mob.getTarget() == null) {
            mob.setTarget(targets.get(0));
        }

        // Attacke wählen
        if (tickCount - boss.lastAttackTick() < def.attackDelayTicks()) {
            return;
        }
        double nearest = Math.sqrt(targets.get(0).getLocation().distanceSquared(entity.getLocation()));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < def.attacks().size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, ThreadLocalRandom.current());
        for (int index : order) {
            BossDefinition.AttackSlot slot = def.attacks().get(index);
            if (hp > slot.maxHp() || hp < slot.minHp()) {
                continue;
            }
            if (boss.cooldownUntil(index) > tickCount) {
                continue;
            }
            if (slot.range() > 0 && nearest > slot.range()) {
                continue;
            }
            boss.setCooldownUntil(index, tickCount + slot.cooldownTicks());
            boss.setLastAttackTick(tickCount);
            slot.attack().execute(boss, targets);
            break;
        }
    }

    private void applyPhase(BossInstance boss, BossDefinition.Phase phase, boolean announce) {
        BossDefinition def = boss.def();
        LivingEntity entity = boss.entity();
        if (def.speed() > 0) {
            setAttr(entity, Attribute.MOVEMENT_SPEED, def.speed() * phase.speedMultiplier());
        }
        if (def.damage() > 0) {
            setAttr(entity, Attribute.ATTACK_DAMAGE, def.damage() * phase.damageMultiplier());
        }
        if (!announce) {
            return;
        }
        if (phase.message() != null && !phase.message().isBlank()) {
            for (Player player : entity.getLocation().getNearbyPlayers(def.barRange())) {
                player.sendMessage(Text.mm(phase.message()));
            }
        }
        Fx.sound(entity.getLocation(), phase.sound(), 2f, 0.8f);
        BossBehavior behavior = behaviors.get(def.id());
        if (behavior != null) {
            behavior.onPhase(boss, phase);
        }
    }

    // ---------------------------------------------------------------- Death

    public void handleDeath(BossInstance boss, EntityDeathEvent event) {
        BossDefinition def = boss.def();
        active.remove(boss.entity().getUniqueId());
        boss.hideBar();

        if (def.clearDrops()) {
            event.getDrops().clear();
        }
        event.setDroppedExp(def.exp());
        for (BossDefinition.Drop drop : def.drops()) {
            if (ThreadLocalRandom.current().nextDouble() > drop.chance()) {
                continue;
            }
            ItemStack item = drop.weapon() != null
                    ? plugin.weapons().create(drop.weapon())
                    : (drop.material() != null ? new ItemStack(drop.material(), Math.max(1, drop.amount())) : null);
            if (item != null) {
                event.getDrops().add(item);
            }
        }
        if (def.deathMessage() != null && !def.deathMessage().isBlank()) {
            for (Player player : boss.entity().getLocation().getNearbyPlayers(def.barRange())) {
                player.sendMessage(Text.mm(def.deathMessage()));
            }
        }
        BossBehavior behavior = behaviors.get(def.id());
        if (behavior != null) {
            behavior.onDeath(boss);
        }
    }

    // ---------------------------------------------------------------- Parse

    private BossDefinition parse(String id, ConfigurationSection s) {
        EntityType type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
        Class<?> cls = type.getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            throw new IllegalArgumentException(type + " ist kein Lebewesen");
        }

        List<BossDefinition.AttackSlot> attacks = new ArrayList<>();
        for (ConfigurationSection a : Cfg.sections(s, "attacks")) {
            String attackType = a.getString("type", "").toLowerCase(Locale.ROOT);
            BossAttackFactory factory = attackTypes.get(attackType);
            if (factory == null) {
                plugin.getLogger().warning("Boss " + id + ": unbekannte Attacke '" + attackType + "'");
                continue;
            }
            try {
                attacks.add(new BossDefinition.AttackSlot(attackType, factory.create(a),
                        (long) (a.getDouble("cooldown", 10) * 20),
                        a.getDouble("min-health-percent", 0),
                        a.getDouble("max-health-percent", 100),
                        a.getDouble("range", 0)));
            } catch (Exception ex) {
                plugin.getLogger().warning("Boss " + id + ": Attacke '" + attackType + "' fehlerhaft: " + ex.getMessage());
            }
        }

        List<BossDefinition.Phase> phases = new ArrayList<>();
        for (ConfigurationSection p : Cfg.sections(s, "phases")) {
            phases.add(new BossDefinition.Phase(p.getDouble("at", 50), p.getString("message", ""),
                    p.getString("sound", ""), p.getDouble("speed-multiplier", 1.0),
                    p.getDouble("damage-multiplier", 1.0)));
        }
        phases.sort(Comparator.comparingDouble(BossDefinition.Phase::at).reversed());

        List<BossDefinition.Drop> drops = new ArrayList<>();
        for (ConfigurationSection d : Cfg.sections(s, "drops")) {
            Material material = d.isString("item") ? Material.matchMaterial(d.getString("item")) : null;
            drops.add(new BossDefinition.Drop(d.getString("weapon"), material, d.getInt("amount", 1),
                    d.getDouble("chance", 1.0)));
        }

        Map<String, String> equipment = new HashMap<>();
        ConfigurationSection eq = s.getConfigurationSection("equipment");
        if (eq != null) {
            for (String key : eq.getKeys(false)) {
                equipment.put(key.toLowerCase(Locale.ROOT), eq.getString(key));
            }
        }

        return new BossDefinition(
                id.toLowerCase(Locale.ROOT), type, s.getString("name", id),
                s.getDouble("health", 200), s.getDouble("damage", 0), s.getDouble("speed", 0),
                s.getDouble("knockback-resistance", 0), s.getBoolean("fire-immune", false),
                BossBar.Color.valueOf(s.getString("bar-color", "RED").toUpperCase(Locale.ROOT)),
                BossBar.Overlay.valueOf(s.getString("bar-overlay", "PROGRESS").toUpperCase(Locale.ROOT)),
                s.getDouble("bar-range", 40), s.getDouble("target-range", 24),
                (long) (s.getDouble("attack-delay", 3) * 20),
                equipment, attacks, phases, drops,
                s.getBoolean("clear-drops", true), s.getInt("exp", 0), s.getString("death-message", ""));
    }
}
