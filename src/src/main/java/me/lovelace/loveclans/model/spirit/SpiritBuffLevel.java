package me.lovelace.loveclans.model.spirit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public enum SpiritBuffLevel {
    LEVEL_1(1, "Пробуждение", "+5% скорости передвижения"),
    LEVEL_2(2, "Единство", "+7% к получению опыта"),
    LEVEL_3(3, "Защита", "-8% урона от мобов"),
    LEVEL_4(4, "Благословение", "+8% скорости ломания блоков"), // Haste equivalent via potion or attribute if available
    LEVEL_5(5, "Сила Крови", "+12% урона по мобам"),
    LEVEL_6(6, "Гармония", "Регенерация +0.5 сердца каждые 6 секунд"),
    LEVEL_7(7, "Дух Воина", "+15% урона по игрокам других кланов (только на главной территории)"),
    LEVEL_8(8, "Хранитель Земли", "-15% урона от взрывов и -10% урона от падения"),
    LEVEL_9(9, "Легенда", "+20% ко всем предыдущим баффам"),
    LEVEL_10(10, "Бессмертный Дух", "Уникальная способность");

    private final int level;
    private final String name;
    private final String description;

    SpiritBuffLevel(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Component getDisplayName() {
        return MiniMessage.miniMessage().deserialize("<gold>" + name + "</gold>");
    }

    public static SpiritBuffLevel getByLevel(int level) {
        for (SpiritBuffLevel buffLevel : values()) {
            if (buffLevel.getLevel() == level) {
                return buffLevel;
            }
        }
        return null;
    }
}
