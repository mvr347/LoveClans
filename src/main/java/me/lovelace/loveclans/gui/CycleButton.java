package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The filter/sort switch shared by every list screen ("members menu" style): a head whose lore lists all options,
 * the current one marked {@code ▶}, followed by the LMB/RMB hint. LMB steps forward, RMB steps back.
 */
public final class CycleButton {
    private CycleButton() {}

    /** Next (or previous) index on a ring of {@code size} options. */
    public static int stepIndex(int current, int size, boolean forward) {
        if (size <= 0) return 0;
        int next = (current + (forward ? 1 : -1)) % size;
        return next < 0 ? next + size : next;
    }

    /** Next/previous option, skipping the ones {@code allowed} rejects; the current one if nothing else is allowed. */
    public static <E extends Enum<E>> E step(E current, E[] options, boolean forward, Predicate<E> allowed) {
        int index = current.ordinal();
        for (int i = 0; i < options.length; i++) {
            index = stepIndex(index, options.length, forward);
            if (allowed.test(options[index])) return options[index];
        }
        return current;
    }

    public static <E extends Enum<E>> E step(E current, E[] options, boolean forward) {
        return step(current, options, forward, option -> true);
    }

    /**
     * Builds the switch. {@code labelPrefix + option.name().toLowerCase()} is the lang key of every option label;
     * options rejected by {@code shown} are left out of the list.
     */
    public static <E extends Enum<E>> ItemStack build(LoveClansPlugin plugin, Player player, String headTexture,
                                                      String nameKey, String labelPrefix, E[] options, E current,
                                                      Predicate<E> shown) {
        List<Component> lore = new ArrayList<>();
        for (E option : options) {
            if (!shown.test(option)) continue;
            String label = plugin.getMessages().raw(labelPrefix + option.name().toLowerCase(java.util.Locale.ROOT));
            lore.add(plugin.getMessages().component(option == current ? "gui.cycle.option-current" : "gui.cycle.option-other",
                    Map.of("name", label), player));
        }
        lore.add(Component.empty());
        lore.add(plugin.getMessages().component("gui.cycle.hint", player));
        return ItemBuilder.head(headTexture)
                .name(plugin.getMessages().component(nameKey, player))
                .lore(lore)
                .build();
    }

    public static <E extends Enum<E>> ItemStack build(LoveClansPlugin plugin, Player player, String headTexture,
                                                      String nameKey, String labelPrefix, E[] options, E current) {
        return build(plugin, player, headTexture, nameKey, labelPrefix, options, current, option -> true);
    }
}
