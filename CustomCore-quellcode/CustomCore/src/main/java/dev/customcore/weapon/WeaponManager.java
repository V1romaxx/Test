package dev.customcore.weapon;

import dev.customcore.CustomCorePlugin;
import dev.customcore.ability.Ability;
import dev.customcore.ability.AbilityContext;
import dev.customcore.util.Cfg;
import dev.customcore.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class WeaponManager {

    private final CustomCorePlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, WeaponDefinition> weapons = new LinkedHashMap<>();
    /** Per Code registrierte Waffen überleben /weapon reload. */
    private final Map<String, WeaponDefinition> codeWeapons = new LinkedHashMap<>();
    /** Spieler, deren Abilities gerade laufen (verhindert Endlos-Schleifen durch Ability-Schaden). */
    private final Set<UUID> guarded = new HashSet<>();

    public WeaponManager(CustomCorePlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "weapon_id");
    }

    public void load() {
        weapons.clear();
        weapons.putAll(codeWeapons);
        File file = new File(plugin.getDataFolder(), "weapons.yml");
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("weapons");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                try {
                    WeaponDefinition def = parse(id, root.getConfigurationSection(id));
                    weapons.put(def.id(), def);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Waffe '" + id + "' konnte nicht geladen werden: " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info(weapons.size() + " Waffen geladen.");
    }

    private WeaponDefinition parse(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "IRON_SWORD"));
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("Ungültiges Material");
        }
        ConfigurationSection hit = s.getConfigurationSection("on-hit");
        ConfigurationSection click = s.getConfigurationSection("on-right-click");
        return new WeaponDefinition(
                id.toLowerCase(Locale.ROOT),
                material,
                s.getString("name", id),
                s.getStringList("lore"),
                s.getString("item-model"),
                s.getDouble("damage", 0),
                s.getDouble("attack-speed", 1.6),
                s.getBoolean("unbreakable", true),
                s.getBoolean("glow", false),
                abilities(hit),
                Cfg.secondsToMs(hit, "cooldown"),
                abilities(click),
                Cfg.secondsToMs(click, "cooldown"));
    }

    private List<Ability> abilities(ConfigurationSection section) {
        List<Ability> list = new ArrayList<>();
        for (ConfigurationSection a : Cfg.sections(section, "abilities")) {
            Ability ability = plugin.abilities().create(a);
            if (ability != null) {
                list.add(ability);
            }
        }
        return list;
    }

    /** API: Waffe direkt aus Code registrieren. */
    public void registerCode(WeaponDefinition def) {
        codeWeapons.put(def.id(), def);
        weapons.put(def.id(), def);
    }

    public Collection<String> ids() {
        return weapons.keySet();
    }

    public WeaponDefinition get(String id) {
        return id == null ? null : weapons.get(id.toLowerCase(Locale.ROOT));
    }

    public ItemStack create(String id) {
        WeaponDefinition def = get(id);
        if (def == null) {
            return null;
        }
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(def.name()));
        if (!def.lore().isEmpty()) {
            meta.lore(def.lore().stream().map(Text::item).toList());
        }
        meta.setUnbreakable(def.unbreakable());
        if (def.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (def.itemModel() != null) {
            NamespacedKey model = NamespacedKey.fromString(def.itemModel());
            if (model != null) {
                meta.setItemModel(model);
            }
        }
        if (def.damage() > 0) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, "weapon_damage"), def.damage() - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "weapon_speed"), def.attackSpeed() - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        return item;
    }

    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public WeaponDefinition definitionOf(ItemStack item) {
        return get(idOf(item));
    }

    public boolean isGuarded(Player player) {
        return guarded.contains(player.getUniqueId());
    }

    public void execute(Player player, List<Ability> abilities, AbilityContext ctx) {
        boolean added = guarded.add(player.getUniqueId());
        try {
            for (Ability ability : abilities) {
                try {
                    ability.execute(ctx);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Fehler in einer Waffen-Ability", ex);
                }
            }
        } finally {
            if (added) {
                guarded.remove(player.getUniqueId());
            }
        }
    }
}
