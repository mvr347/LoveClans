package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.quest.QuestObjective;
import me.lovelace.loveclans.model.quest.objective.BountyTurnInObjective;
import me.lovelace.loveclans.model.quest.objective.BreedAnimalObjective;
import me.lovelace.loveclans.model.quest.objective.CraftItemObjective;
import me.lovelace.loveclans.model.quest.objective.EnchantItemObjective;
import me.lovelace.loveclans.model.quest.objective.FishItemObjective;
import me.lovelace.loveclans.model.quest.objective.GatherItemObjective;
import me.lovelace.loveclans.model.quest.objective.KillMobObjective;
import me.lovelace.loveclans.model.quest.objective.MineAnyOreObjective;
import me.lovelace.loveclans.model.quest.objective.MineBlockObjective;
import me.lovelace.loveclans.model.quest.objective.PvPKillObjective;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Optional;

/**
 * Builds a {@link QuestObjective} from a contract pool entry's {@code objective} config section
 * (§1.1/§1.4) - every contract's mechanics live entirely in config.yml, this just dispatches on
 * the configured {@code type} to the matching objective record. Progress-format strings are
 * shared per objective type and live in lang.yml under {@code contract.progress.<type>}, not
 * duplicated across all 60 pool entries.
 */
final class ContractObjectiveFactory {
    private ContractObjectiveFactory() {
    }

    static Optional<QuestObjective> build(LoveClansPlugin plugin, String contractId, ConfigurationSection objectiveSection) {
        if (objectiveSection == null) {
            plugin.getLogger().warning("Contract '" + contractId + "' has no 'objective' section - skipped.");
            return Optional.empty();
        }
        String type = objectiveSection.getString("type", "").toUpperCase(Locale.ROOT);
        int target = Math.max(1, objectiveSection.getInt("target", 1));
        try {
            return switch (type) {
                case "MINE_ANY_ORE" -> Optional.of(new MineAnyOreObjective(target, format(plugin, "mine-any-ore")));
                case "MINE_BLOCK" -> Optional.of(new MineBlockObjective(material(objectiveSection, "block-type"), target, format(plugin, "mine-block")));
                case "KILL_MOB" -> Optional.of(new KillMobObjective(entityType(objectiveSection, "mob-type"), target, format(plugin, "kill-mob")));
                case "BOUNTY_TURN_IN" -> Optional.of(new BountyTurnInObjective(target, format(plugin, "bounty-turn-in")));
                case "GATHER_ITEM" -> Optional.of(new GatherItemObjective(material(objectiveSection, "item-type"), target, format(plugin, "gather-item")));
                case "CRAFT_ITEM" -> Optional.of(new CraftItemObjective(material(objectiveSection, "item-type"), target, format(plugin, "craft-item")));
                case "FISH_ITEM" -> Optional.of(new FishItemObjective(material(objectiveSection, "item-type"), target, format(plugin, "fish-item")));
                case "PVP_KILL" -> Optional.of(new PvPKillObjective(target, format(plugin, "pvp-kill")));
                case "BREED_ANIMAL" -> Optional.of(new BreedAnimalObjective(entityType(objectiveSection, "animal-type"), target, format(plugin, "breed-animal")));
                case "ENCHANT_ITEM" -> Optional.of(new EnchantItemObjective(enchantment(objectiveSection), target, format(plugin, "enchant-item")));
                default -> {
                    plugin.getLogger().warning("Contract '" + contractId + "' has unknown objective type '" + type + "' - skipped.");
                    yield Optional.empty();
                }
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            plugin.getLogger().warning("Contract '" + contractId + "' has an invalid objective config: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static String format(LoveClansPlugin plugin, String typeKey) {
        return plugin.getMessages().raw("contract.progress." + typeKey);
    }

    private static Material material(ConfigurationSection section, String key) {
        return Material.valueOf(section.getString(key, "").toUpperCase(Locale.ROOT));
    }

    private static EntityType entityType(ConfigurationSection section, String key) {
        return EntityType.valueOf(section.getString(key, "").toUpperCase(Locale.ROOT));
    }

    private static Enchantment enchantment(ConfigurationSection section) {
        String key = section.getString("enchantment-type", "").toLowerCase(Locale.ROOT);
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment == null) {
            throw new IllegalArgumentException("unknown enchantment '" + key + "'");
        }
        return enchantment;
    }
}
