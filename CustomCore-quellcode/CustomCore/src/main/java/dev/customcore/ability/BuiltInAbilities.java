package dev.customcore.ability;

import dev.customcore.CustomCorePlugin;
import dev.customcore.util.Fx;
import dev.customcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BuiltInAbilities {

    /** Spieler, deren Fledermaus-Teleport gerade läuft (verhindert Doppelnutzung). */
    private static final Set<UUID> BAT_ACTIVE = new HashSet<>();

    private BuiltInAbilities() {
    }

    private static LivingEntity pick(AbilityContext c, String who) {
        return "self".equalsIgnoreCase(who) ? c.user() : c.target();
    }

    /** Ziel-Ort: getroffenes Lebewesen, sonst anvisierter Block. */
    private static Location focus(AbilityContext c) {
        if (c.target() != null) {
            return c.target().getLocation();
        }
        Block block = c.user().getTargetBlockExact(40);
        return block != null ? block.getLocation().add(0.5, 1, 0.5) : null;
    }

    public static void registerAll(AbilityRegistry r) {

        r.register("ignite", cfg -> {
            int ticks = (int) (cfg.getDouble("seconds", 3) * 20);
            String who = cfg.getString("target", "target");
            return c -> {
                LivingEntity e = pick(c, who);
                if (e != null) {
                    e.setFireTicks(ticks);
                }
            };
        });

        r.register("damage", cfg -> {
            double amount = cfg.getDouble("amount", 4);
            String who = cfg.getString("target", "target");
            return c -> {
                LivingEntity e = pick(c, who);
                if (e != null) {
                    e.damage(amount, c.user());
                }
            };
        });

        r.register("potion", cfg -> {
            String name = cfg.getString("effect", "slowness").toLowerCase(Locale.ROOT);
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(name));
            if (type == null) {
                throw new IllegalArgumentException("Unbekannter Effekt: " + name);
            }
            int ticks = (int) (cfg.getDouble("seconds", 5) * 20);
            int amp = cfg.getInt("amplifier", 0);
            double radius = cfg.getDouble("radius", 0);
            String who = cfg.getString("target", "target");
            return c -> {
                PotionEffect effect = new PotionEffect(type, ticks, amp);
                if (radius > 0) {
                    Location center = "self".equalsIgnoreCase(who) || c.target() == null
                            ? c.user().getLocation() : c.target().getLocation();
                    for (LivingEntity e : center.getNearbyLivingEntities(radius)) {
                        if (!e.equals(c.user()) && !(e instanceof ArmorStand)) {
                            e.addPotionEffect(effect);
                        }
                    }
                } else {
                    LivingEntity e = pick(c, who);
                    if (e != null) {
                        e.addPotionEffect(effect);
                    }
                }
            };
        });

        r.register("lifesteal", cfg -> {
            double percent = cfg.getDouble("percent", 0.25);
            return c -> {
                Player u = c.user();
                AttributeInstance max = u.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : 20;
                u.setHealth(Math.min(cap, u.getHealth() + c.damage() * percent));
                u.getWorld().spawnParticle(Particle.HEART, u.getLocation().add(0, 1.5, 0), 3, 0.3, 0.2, 0.3, 0);
            };
        });

        r.register("knockback", cfg -> {
            double strength = cfg.getDouble("strength", 1.2);
            double up = cfg.getDouble("up", 0.4);
            return c -> {
                if (c.target() != null) {
                    Fx.knock(c.user(), c.target(), strength, up);
                }
            };
        });

        r.register("lightning", cfg -> {
            double damage = cfg.getDouble("damage", 6);
            double radius = cfg.getDouble("radius", 2.5);
            return c -> {
                Location loc = focus(c);
                if (loc == null) {
                    return;
                }
                loc.getWorld().strikeLightningEffect(loc);
                for (LivingEntity e : loc.getNearbyLivingEntities(radius)) {
                    if (!e.equals(c.user()) && !(e instanceof ArmorStand)) {
                        e.damage(damage, c.user());
                    }
                }
            };
        });

        r.register("explosion", cfg -> {
            float power = (float) cfg.getDouble("power", 2);
            boolean fire = cfg.getBoolean("fire", false);
            boolean breakBlocks = cfg.getBoolean("break-blocks", false);
            return c -> {
                Location loc = focus(c);
                if (loc != null) {
                    loc.getWorld().createExplosion(loc, power, fire, breakBlocks, c.user());
                }
            };
        });

        r.register("shockwave", cfg -> {
            double radius = cfg.getDouble("radius", 5);
            double damage = cfg.getDouble("damage", 6);
            double knockback = cfg.getDouble("knockback", 1.2);
            double up = cfg.getDouble("up", 0.5);
            Particle particle = Fx.particle(cfg.getString("particle", "CLOUD"), Particle.CLOUD);
            return c -> {
                Player u = c.user();
                Location loc = u.getLocation();
                Fx.ring(loc, radius * 0.5, particle);
                Fx.ring(loc, radius, particle);
                for (LivingEntity e : loc.getNearbyLivingEntities(radius)) {
                    if (e.equals(u) || e instanceof ArmorStand) {
                        continue;
                    }
                    if (damage > 0) {
                        e.damage(damage, u);
                    }
                    Fx.knock(u, e, knockback, up);
                }
            };
        });

        r.register("dash", cfg -> {
            double strength = cfg.getDouble("strength", 1.6);
            return c -> {
                Vector v = c.user().getLocation().getDirection().multiply(strength);
                v.setY(Math.max(v.getY(), 0.2));
                c.user().setVelocity(v);
            };
        });

        r.register("fireball", cfg -> {
            double speed = cfg.getDouble("speed", 1.5);
            float yield = (float) cfg.getDouble("yield", 0);
            boolean fire = cfg.getBoolean("fire", false);
            return c -> {
                Vector dir = c.user().getEyeLocation().getDirection().multiply(speed);
                Fireball fb = c.user().launchProjectile(Fireball.class, dir);
                fb.setYield(yield);
                fb.setIsIncendiary(fire);
            };
        });

        r.register("heal", cfg -> {
            double amount = cfg.getDouble("amount", 4);
            double percent = cfg.getDouble("percent", 0);
            return c -> {
                Player u = c.user();
                AttributeInstance max = u.getAttribute(Attribute.MAX_HEALTH);
                double cap = max != null ? max.getValue() : 20;
                double heal = percent > 0 ? cap * percent / 100.0 : amount;
                u.setHealth(Math.min(cap, u.getHealth() + heal));
                u.getWorld().spawnParticle(Particle.HEART, u.getLocation().add(0, 1.5, 0), 5, 0.4, 0.3, 0.4, 0);
            };
        });

        r.register("particle", cfg -> {
            Particle particle = Fx.particle(cfg.getString("particle", "CLOUD"), Particle.CLOUD);
            int count = cfg.getInt("count", 15);
            double spread = cfg.getDouble("spread", 0.4);
            double speed = cfg.getDouble("speed", 0.02);
            String at = cfg.getString("at", "target");
            return c -> {
                Location loc = "self".equalsIgnoreCase(at) || c.target() == null
                        ? c.user().getLocation().add(0, 1, 0)
                        : c.target().getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(particle, loc, count, spread, spread, spread, speed);
            };
        });

        r.register("sound", cfg -> {
            String sound = cfg.getString("sound", "entity.experience_orb.pickup");
            float volume = (float) cfg.getDouble("volume", 1.0);
            float pitch = (float) cfg.getDouble("pitch", 1.0);
            return c -> Fx.sound(c.user().getLocation(), sound, volume, pitch);
        });

        // Spawnt eine Fledermaus, macht den Spieler unsichtbar und teleportiert ihn nach X Sekunden
        // zur Position der Fledermaus (die dann verschwindet).
        r.register("bat_teleport", cfg -> {
            int duration = (int) (cfg.getDouble("seconds", 5) * 20);
            boolean invisible = cfg.getBoolean("invisible", true);
            return c -> {
                Player player = c.user();
                if (!BAT_ACTIVE.add(player.getUniqueId())) {
                    return;
                }
                Location start = player.getLocation().add(0, 1, 0);
                Bat bat = player.getWorld().spawn(start, Bat.class, b -> {
                    b.setInvulnerable(true);
                    b.setPersistent(false);
                    b.addScoreboardTag("cc_bat");
                });
                if (invisible) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                            duration + 20, 0, false, false));
                }
                Fx.sound(start, "entity.bat.takeoff", 1.2f, 1.0f);
                start.getWorld().spawnParticle(Particle.CLOUD, start, 20, 0.4, 0.5, 0.4, 0.02);

                new BukkitRunnable() {
                    private int ticks = 0;
                    private Location last = start.clone();

                    @Override
                    public void run() {
                        ticks++;
                        if (bat.isValid()) {
                            last = bat.getLocation();
                        }
                        boolean playerGone = !player.isOnline() || player.isDead();
                        if (!playerGone && ticks < duration) {
                            return;
                        }
                        cancel();
                        bat.remove();
                        BAT_ACTIVE.remove(player.getUniqueId());
                        if (playerGone) {
                            return;
                        }
                        if (invisible) {
                            player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        }
                        Location dest = last.clone();
                        dest.setYaw(player.getLocation().getYaw());
                        dest.setPitch(player.getLocation().getPitch());
                        dest.getWorld().spawnParticle(Particle.CLOUD, dest, 25, 0.4, 0.5, 0.4, 0.03);
                        player.teleport(dest);
                        Fx.sound(dest, "entity.enderman.teleport", 1.0f, 1.3f);
                    }
                }.runTaskTimer(CustomCorePlugin.get(), 1L, 1L);
            };
        });

        r.register("message", cfg -> {
            String text = cfg.getString("text", "");
            return c -> c.user().sendMessage(Text.mm(text));
        });

        r.register("command", cfg -> {
            List<String> commands = new ArrayList<>(cfg.getStringList("commands"));
            if (cfg.isString("command")) {
                commands.add(cfg.getString("command"));
            }
            return c -> {
                String targetName = c.target() != null ? c.target().getName() : "";
                for (String command : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            command.replace("%player%", c.user().getName()).replace("%target%", targetName));
                }
            };
        });
    }
}
