package dev.customcore.boss;

import dev.customcore.CustomCorePlugin;
import dev.customcore.util.Fx;
import dev.customcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class BuiltInBossAttacks {

    private BuiltInBossAttacks() {
    }

    private static CustomCorePlugin plugin() {
        return CustomCorePlugin.get();
    }

    /** Einschlag nach Verzögerung: Schaden + Rückstoß für Spieler im Radius. */
    private static void delayedImpact(LivingEntity boss, Location center, double radius,
                                      double damage, double knockback, double up, int delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (!boss.isValid()) {
                return;
            }
            Fx.ring(center, radius, Particle.CLOUD);
            Fx.ring(center, radius * 0.6, Particle.CRIT);
            Fx.sound(center, "entity.generic.explode", 1.2f, 0.7f);
            for (Player player : center.getNearbyPlayers(radius)) {
                if (!Fx.targetable(player)) {
                    continue;
                }
                player.damage(damage, boss);
                Fx.knock(boss, player, knockback, up);
            }
        }, delayTicks);
    }

    public static void registerAll(BossManager m) {

        m.registerAttackType("slam", cfg -> {
            double radius = cfg.getDouble("radius", 6);
            double damage = cfg.getDouble("damage", 8);
            double knockback = cfg.getDouble("knockback", 1.2);
            double up = cfg.getDouble("up", 0.6);
            int delay = cfg.getInt("delay-ticks", 20);
            return (boss, targets) -> {
                LivingEntity entity = boss.entity();
                Location center = entity.getLocation().clone();
                Fx.sound(center, "entity.ravager.roar", 1.5f, 0.8f);
                Fx.ring(center, radius, Particle.CRIT);
                delayedImpact(entity, center, radius, damage, knockback, up, delay);
            };
        });

        m.registerAttackType("summon", cfg -> {
            EntityType type = EntityType.valueOf(cfg.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
            int count = cfg.getInt("count", 3);
            double radius = cfg.getDouble("radius", 4);
            int maxAlive = cfg.getInt("max-alive", 8);
            String name = cfg.getString("name", "");
            return (boss, targets) -> {
                LivingEntity entity = boss.entity();
                String tag = "cc_minion_" + entity.getUniqueId();
                long alive = entity.getLocation().getNearbyLivingEntities(48).stream()
                        .filter(e -> e.getScoreboardTags().contains(tag)).count();
                Fx.sound(entity.getLocation(), "entity.evoker.prepare_summon", 1.5f, 0.8f);
                for (int i = 0; i < count && alive + i < maxAlive; i++) {
                    double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                    Location spawn = entity.getLocation().clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    Entity minion = entity.getWorld().spawnEntity(spawn, type);
                    minion.addScoreboardTag(tag);
                    if (!name.isBlank()) {
                        minion.customName(Text.mm(name));
                    }
                    if (minion instanceof Mob mob) {
                        mob.setTarget(targets.get(0));
                    }
                    spawn.getWorld().spawnParticle(Particle.CLOUD, spawn, 15, 0.4, 0.6, 0.4, 0.02);
                }
            };
        });

        m.registerAttackType("leap", cfg -> {
            double strength = cfg.getDouble("strength", 1.4);
            double height = cfg.getDouble("height", 0.8);
            double landRadius = cfg.getDouble("land-radius", 0);
            double landDamage = cfg.getDouble("land-damage", 6);
            int landDelay = cfg.getInt("land-delay-ticks", 14);
            return (boss, targets) -> {
                LivingEntity entity = boss.entity();
                Vector dir = targets.get(0).getLocation().toVector().subtract(entity.getLocation().toVector());
                dir.setY(0);
                if (dir.lengthSquared() < 0.01) {
                    return;
                }
                dir.normalize().multiply(strength).setY(height);
                entity.setVelocity(dir);
                Fx.sound(entity.getLocation(), "entity.ravager.attack", 1.2f, 0.9f);
                if (landRadius > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                        if (entity.isValid()) {
                            delayedImpact(entity, entity.getLocation().clone(), landRadius, landDamage, 1.0, 0.5, 0);
                        }
                    }, landDelay);
                }
            };
        });

        m.registerAttackType("projectile", cfg -> {
            String kind = cfg.getString("projectile", "small_fireball").toLowerCase(Locale.ROOT);
            int count = cfg.getInt("count", 1);
            double spread = cfg.getDouble("spread", 0.1);
            double speed = cfg.getDouble("speed", 1.2);
            float yield = (float) cfg.getDouble("yield", kind.equals("fireball") ? 1.0 : 0.0);
            boolean breakBlocks = cfg.getBoolean("break-blocks", false);
            Class<? extends Projectile> cls = switch (kind) {
                case "fireball" -> Fireball.class;
                case "wither_skull" -> WitherSkull.class;
                case "arrow" -> Arrow.class;
                case "snowball" -> Snowball.class;
                default -> SmallFireball.class;
            };
            return (boss, targets) -> {
                LivingEntity entity = boss.entity();
                Location from = entity.getEyeLocation();
                Vector base = targets.get(0).getEyeLocation().toVector().subtract(from.toVector()).normalize();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < count; i++) {
                    Vector v = base.clone().add(new Vector(
                            rnd.nextDouble(-spread, spread), rnd.nextDouble(-spread, spread), rnd.nextDouble(-spread, spread)))
                            .normalize().multiply(speed);
                    Projectile projectile = entity.launchProjectile(cls, v);
                    if (projectile instanceof Fireball fireball) {
                        fireball.setYield(yield);
                        fireball.setIsIncendiary(false);
                    }
                    if (!breakBlocks) {
                        projectile.addScoreboardTag("cc_noblock");
                    }
                }
                Fx.sound(entity.getLocation(), "entity.blaze.shoot", 1.2f, 1.0f);
            };
        });

        m.registerAttackType("lightning", cfg -> {
            int strikes = cfg.getInt("strikes", 3);
            double damage = cfg.getDouble("damage", 8);
            double radius = cfg.getDouble("radius", 3);
            int delay = cfg.getInt("delay-ticks", 20);
            return (boss, targets) -> {
                for (int i = 0; i < strikes; i++) {
                    Location spot = targets.get(i % targets.size()).getLocation().clone();
                    Fx.ring(spot, radius, Particle.CRIT);
                    Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                        if (!boss.entity().isValid()) {
                            return;
                        }
                        spot.getWorld().strikeLightningEffect(spot);
                        for (Player player : spot.getNearbyPlayers(radius)) {
                            if (Fx.targetable(player)) {
                                player.damage(damage, boss.entity());
                            }
                        }
                    }, delay + i * 6L);
                }
            };
        });

        m.registerAttackType("pull", cfg -> {
            double radius = cfg.getDouble("radius", 12);
            double strength = cfg.getDouble("strength", 1.1);
            double damage = cfg.getDouble("damage", 0);
            return (boss, targets) -> {
                LivingEntity entity = boss.entity();
                Fx.sound(entity.getLocation(), "entity.enderman.teleport", 1.5f, 0.6f);
                Fx.ring(entity.getLocation(), radius, Particle.CLOUD);
                for (Player player : entity.getLocation().getNearbyPlayers(radius)) {
                    if (!Fx.targetable(player)) {
                        continue;
                    }
                    Vector pull = entity.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (pull.lengthSquared() < 0.01) {
                        continue;
                    }
                    pull.normalize().multiply(strength).setY(0.3);
                    player.setVelocity(pull);
                    if (damage > 0) {
                        player.damage(damage, entity);
                    }
                }
            };
        });

        m.registerAttackType("heal", cfg -> {
            double percent = cfg.getDouble("percent", 10);
            return (boss, targets) -> {
                LivingEntity entity = boss.entity();
                AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : entity.getHealth();
                entity.setHealth(Math.min(cap, entity.getHealth() + cap * percent / 100.0));
                entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 2, 0), 12, 0.6, 0.5, 0.6, 0);
                Fx.sound(entity.getLocation(), "entity.generic.drink", 1.2f, 0.8f);
            };
        });

        m.registerAttackType("potion", cfg -> {
            String name = cfg.getString("effect", "slowness").toLowerCase(Locale.ROOT);
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(name));
            if (type == null) {
                throw new IllegalArgumentException("Unbekannter Effekt: " + name);
            }
            int ticks = (int) (cfg.getDouble("seconds", 5) * 20);
            int amp = cfg.getInt("amplifier", 0);
            double radius = cfg.getDouble("radius", 8);
            return (boss, targets) -> {
                Location loc = boss.entity().getLocation();
                Fx.ring(loc, radius, Particle.CLOUD);
                for (Player player : loc.getNearbyPlayers(radius)) {
                    if (Fx.targetable(player)) {
                        player.addPotionEffect(new PotionEffect(type, ticks, amp));
                    }
                }
            };
        });

        m.registerAttackType("command", cfg -> {
            List<String> commands = new ArrayList<>(cfg.getStringList("commands"));
            if (cfg.isString("command")) {
                commands.add(cfg.getString("command"));
            }
            return (boss, targets) -> {
                for (String command : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                            .replace("%boss%", boss.entity().getUniqueId().toString())
                            .replace("%target%", targets.get(0).getName()));
                }
            };
        });

        m.registerAttackType("message", cfg -> {
            String text = cfg.getString("text", "");
            double radius = cfg.getDouble("radius", 40);
            return (boss, targets) -> {
                for (Player player : boss.entity().getLocation().getNearbyPlayers(radius)) {
                    player.sendMessage(Text.mm(text));
                }
            };
        });
    }
}
