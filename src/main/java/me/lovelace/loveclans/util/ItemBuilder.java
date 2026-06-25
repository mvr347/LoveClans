package me.lovelace.loveclans.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ItemBuilder {
    // Custom Head Textures
    public static final String HEAD_BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=";
    public static final String HEAD_BARRIER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==";
    public static final String HEAD_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGViMmQ3NTRhYmJjNWQwMDRiODBmZTA4YjUzMTg2ZjY5ZGM4ZTllZjZmOGY3ZmQwNzIxNGE4Yzg0OTZlYjA4NSJ9fX0=";
    public static final String HEAD_MEMBERS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFhZTgwMzg0YTAwYjZmOWY2NGRkODMwN2E5MDY3NjU0NGM5N2E3OTI5NzE2NWVhNzEzMjYyYzdkODgzMzg0NyJ9fX0=";
    public static final String HEAD_INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjgwZDMyOTVkM2Q5YWJkNjI3NzZhYmNiOGRhNzU2ZjI5OGE1NDVmZWU5NDk4YzRmNjlhMWMyYzc4NTI0YzgyNCJ9fX0=";
    public static final String HEAD_DESC = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRkMmViMGM2ZjhhOTU0M2VmNWZkNzI1MjVjYzJmYWIzNTY2M2NkNzA5MTM1ZTQzYjhlMjU3ZGMwYjc1ODk0OCJ9fX0=";
    public static final String HEAD_EXPAND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzA0MGZlODM2YTZjMmZiZDJjN2E5YzhlYzZiZTUxNzRmZGRmMWFjMjBmNTVlMzY2MTU2ZmE1ZjcxMmUxMCJ9fX0=";
    public static final String HEAD_MSG = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDc1N2U5NzJjNzYzMTQ5NDc2YmM1NTZjZmJhZDM2MTRjMWQ0OTk1NGYxM2Y4ODkzZWI5ZDEwNTg0NTc1M2YyZSJ9fX0=";
    public static final String HEAD_QUEST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZkN2I3MjA0YmVhMWZmMGRmNzkzMGJkNDU1OWEzMGI1ZWE1NTllNTYyNDI5NDY5NGU4NjdiZjdiNWFlMDM2MSJ9fX0=";
    public static final String HEAD_INVITE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=";
    public static final String HEAD_DELETE_YES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMwZjQ1MzdkMjE0ZDM4NjY2ZTYzMDRlOWM4NTFjZDZmN2U0MWEwZWI3YzI1MDQ5YzlkMjJjOGM1ZjY1NDVkZiJ9fX0=";
    public static final String HEAD_DELETE_NO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19";
    public static final String HEAD_DISBAND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGEzNWU3NjQ5MmQ0MmQ3MmNmNTZkZmM0YjdkNTM0N2Q0OGNjODg5NDBkZmQ2YTlkNjIzNWJjNzE4ZjRjNmJhYiJ9fX0=";
    public static final String HEAD_TERRITORIES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUwMTU0NzBlMjg2ZTRlZDc3YTAzODc2Y2JiZmQ3YjNkMzU4YTYwNjA2YjQ0NmQyYzRiYzhkOGU5YzM3M2VlOSJ9fX0=";
    public static final String HEAD_PRIVATES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNkMDJjZGMwNzViYjFjYzVmNmZlM2M3NzExYWU0OTc3ZTM4YjkxMGQ1MGVkNjAyM2RmNzM5MTNlNWU3ZmNmZiJ9fX0=";
    public static final String HEAD_EXPERIENCE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDhiODFkODkzNWZjMTAxZTM1ZjNlNTRmNTM3YjYxMjgyMmYxOGZhZjc1MmE5MDAzN2NjNDIxNWE1ZGM4ODFlZiJ9fX0=";
    public static final String HEAD_DAILY_QUESTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MxYjJmNTkyY2ZjOGQzNzJkY2Y1ZmQ0NGVlZDY5ZGRkYzY0NjAxZDc4NDZkNzI2MTlmNzA1MTFkODA0M2E4OSJ9fX0=";
    public static final String HEAD_WEEKLY_QUESTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjJjZGEwNWVhNWQ5Y2NhNzAyMDdhOGIyOWZkZTFjOTY5NmM3NTI3YzYyZTU5OWUzZGEyZDI3NWYyMGUwYmE5YiJ9fX0=";
    public static final String HEAD_MONTHLY_QUESTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmY1NjhkZDgwODIwNjU3NTU0ZjliNDVkYjAzMWQyMmNkOWI3ZDE2MWMxOTgyNzczYjFiMzU4NTRjOTkzOTgyNCJ9fX0=";
    public static final String HEAD_COMPLETED_QUESTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjdkYTA3ZmMwZTRhZTEwMWNkM2Q2YjYwODBiMDRhYTc1MzgxZWY4MDc0ZTAyZWQ1Mzk3NTk0ZjQ5YjcxY2IwYyJ9fX0=";
    public static final String HEAD_EXP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUwMTU0NzBlMjg2ZTRlZDc3YTAzODc2Y2JiZmQ3YjNkMzU4YTYwNjA2YjQ0NmQyYzRiYzhkOGU5YzM3M2VlOSJ9fX0=";
    public static final String HEAD_MAP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk5MjRiY2ZkN2VjNmVhNDM3NDM4Yjc1YjcyZWU0MWEyYjU3MTA0ZGJhMjVlY2FmYTYxMThlNjhlMWI4MWM5ZCJ9fX0=";
    public static final String HEAD_PREVIOUS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMWYyZjhjY2ZlYTA0MzIzZDkxMWIzNTIxYTdhZmQ3MTc5YTA3YWU3OSJ9fX0=";
    public static final String HEAD_NEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTliZjMyOTJlMTI2YTEwNWI1NGViYTcxM2FhMWIxNTJkNTQxYTFkODkzODgyOWM1NjM2NTZkMjMyNjZiZCJ9fX0=";

    // Universal "inactive/disabled" button icon
    public static final String HEAD_INACTIVE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzYxODczMWUwNjMzNzlhZWJmODJmMWQ2NGM0MTljOTBkN2YwYzE2NDhjNTQ4ZTliNjE1MWIxYmFiYTY2ZDcyMyJ9fX0=";

    // Main menu icons
    public static final String HEAD_SPIRIT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTdjNjZmNWE0YjQwODAwNWIzMzZkYTY2NzZlOGY2YTJhNjdlZWEzMTVmYjdlOTEzNjBhY2MwNDc4MDJmYTMyMCJ9fX0=";
    public static final String HEAD_MAIN_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjUxZWI3MjdiZDg5NmFkZDU1ZjZkNjc4M2NlY2Q3OTNkOTgyNTAwZjRkOTQ3NjE0M2ZlMDhlMjFmZjdlM2Y1ZSJ9fX0=";
    public static final String HEAD_MAIN_APPLICATIONS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjYyYzA4ODA1YmQ5Yzk1N2RhMzQ1MDU1NGEwOWU5OTQwNDJmNTQ2OTVkYjg1NWMxYzJjYjQ3ZWY0NDJlMWJmNiJ9fX0=";
    public static final String HEAD_CLOSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19";
    public static final String HEAD_DIPLOMACY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY3MjA4NWZmNWRmMGVlODUyMTM2ZTJkNDFhNzY5MjI5MjkxZDY2NmIyOGU5ZmIwNGQ2YzkyZjE1OGU0MmJiIn19fQ==";
    public static final String HEAD_LEAVE_CLAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWZkMjQwMDAwMmFkOWZiYmJkMDA2Njk0MWViNWIxYTM4NGFiOWIwZTQ4YTE3OGVlOTZlNGQxMjlhNTIwODY1NCJ9fX0=";

    // Settings / territories
    public static final String HEAD_LEVEL_INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDYxNzhhZDUxZmQ1MmIxOWQwYTM4ODg3MTBiZDkyMDY4ZTkzMzI1MmFhYzZiMTNjNzZlN2U2ZWE1ZDMyMjYifX19";
    public static final String HEAD_CAPITAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmVlZjdlNTZjZGU3NDA3NzJkZmI3NmRkZDJmNTg0YmU4OTA3Yjg1OTc2NjhlNDAyNjM0OTg2NDY5MjMwYWE0OSJ9fX0=";
    public static final String HEAD_MORE_TERRITORIES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjFiNjM1YmM5MmRkMjAwMTFkNmU3ZDAxMjU3ZTZlYTczN2I0OWZiN2RlOTQxZDNiYmQxODc3MTA0ZmM1ZWIxZSJ9fX0=";

    // Spirit menu / abilities
    public static final String HEAD_SPIRIT_ABILITIES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiZGFhNzU1MDk5ZWRkN2VmYTFmMTI4ODJjN2E1MWI1ODE1ZGI1MmUwYjE2NGFlZjZkZjlhMWY1M2VjYTIzIn19fQ==";
    public static final String HEAD_SPIRIT_HISTORY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzk0YTNkNWQ5MmQ1YTYwNjQ2NzAzYmU5NWNiYzRmMjdiZmMyNDUwNjc1MGU5ZGIyYWJlMzRhZTI3MjIxOWMwMyJ9fX0=";
    public static final String HEAD_ABILITY_PHOENIX = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjgyNDYzNDc2OTdiZWM3NGUwYjU4MmZjM2E5ZTNlYzZhOWRmMTE1ZTFlYmQ3ZDM2NjRhYmRhZmZkYTM5YTdmYiJ9fX0=";
    public static final String HEAD_ABILITY_BERSERKER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDE4MWJmNDQxNjhjOWMyZmU2YmRhNzExNDBhYzEzMjk2NWZlOGVhZmVmZjI4MjllZDc3MTM2YWFjZWFmODIifX19";
    public static final String HEAD_ABILITY_SANCTUARY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWEzZTFhMDUwZmZkYWUyY2YzNDJmZTM4YzYxZWJhYzdlMDZhYzU4Y2UxYTYwMDNmZTIzZWE4YWU0YjU4YzYwOCJ9fX0=";

    // Placeholders pending final art from the spec; reuse existing textures until replaced
    public static final String HEAD_NO_PLAYERS_EMPTY = HEAD_INACTIVE;
    public static final String HEAD_SPIRIT_STAR = HEAD_SPIRIT;
    public static final String HEAD_ACTIVE_BUFFS = HEAD_EXPERIENCE;

    private final ItemStack itemStack;
    private final List<Component> lore = new ArrayList<>();

    private ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }
    
    public static ItemBuilder of(ItemStack itemStack) {
        ItemBuilder builder = new ItemBuilder(itemStack.getType());
        builder.itemStack.setItemMeta(itemStack.getItemMeta());
        builder.itemStack.setAmount(itemStack.getAmount());
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
            builder.lore.addAll(itemStack.getItemMeta().lore());
        }
        return builder;
    }

    public static ItemBuilder head(String base64) {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD);
        builder.mutate(meta -> {
            if (meta instanceof SkullMeta skullMeta) {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new ProfileProperty("textures", base64));
                skullMeta.setPlayerProfile(profile);
            }
        });
        return builder;
    }

    public ItemBuilder name(Component name) {
        mutate(meta -> meta.displayName(name));
        return this;
    }

    public ItemBuilder lore(Component line) {
        lore.add(line);
        return this;
    }

    public ItemBuilder lore(Collection<Component> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        mutate(meta -> meta.addItemFlags(flags));
        return this;
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }
    
    public ItemBuilder glow(boolean glow) {
        if (glow) {
            mutate(meta -> {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            });
        }
        return this;
    }

    public ItemBuilder mutate(Consumer<ItemMeta> consumer) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            mutate(meta -> meta.lore(lore));
        }
        return itemStack;
    }
}
