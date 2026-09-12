package me.lovelace.loveclans.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Преобразует заранее размеченные построчно (через \n) описания способностей духа
 * (SpiritAbility) в список компонентов лора предмета.
 */
public final class AbilityLoreFormatter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private AbilityLoreFormatter() {
    }

    public static List<Component> format(String rawDescription) {
        List<Component> result = new ArrayList<>();
        for (String line : rawDescription.split("\n")) {
            result.add(line.isEmpty() ? Component.empty() : MINI_MESSAGE.deserialize(line));
        }
        return result;
    }
}
