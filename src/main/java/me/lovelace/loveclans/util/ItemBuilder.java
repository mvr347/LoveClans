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
    public static final String HEAD_BARRIER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWZkMjQwMDAwMmFkOWZiYmJkMDA2Njk0MWViNWIxYTM4NGFiOWIwZTQ4YTE3OGVlOTZlNGQxMjlhNTIwODY1NCJ9fX0=";
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

    public static final String HEAD_NO_PLAYERS_EMPTY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRkMmViMGM2ZjhhOTU0M2VmNWZkNzI1MjVjYzJmYWIzNTY2M2NkNzA5MTM1ZTQzYjhlMjU3ZGMwYjc1ODk0OCJ9fX0=";
    public static final String HEAD_SPIRIT_STAR = HEAD_SPIRIT;
    public static final String HEAD_ACTIVE_BUFFS = HEAD_EXPERIENCE;

    // Diplomacy relation icons
    public static final String HEAD_RELATION_HOSTILE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzY2NmI3OGFmMmNkMDQ4NDNmZDBiNWZmYmNmM2Q0ZWJiYThhNjg1NjhiMmViNjA4ZDQ1OTlkNTg5NjBkZjc3MyJ9fX0=";
    public static final String HEAD_RELATION_NEUTRAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q4NzhiOGYwNTJhNWU0OWExYmY5MDk4MTc3NWIxYWM2ZGVlYmFhMzhjMThjODc3YjVhOTUxNTc1NjQxOWY3OSJ9fX0=";
    public static final String HEAD_RELATION_FRIENDLY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWI3NmI0ZWU5ODg1NzIyOTdjYmQ4NzQ2ODNiZWU5NmFlM2M1NWNlOTRjMDA0ZTUxYWRjODJjZWUxNmNkMGIwYyJ9fX0=";

    // Rank management icons (Recruit / Clansman / Guardian)
    public static final String HEAD_RANK_RECRUIT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmIwZmQ2MGQ3MjI0MTlhNzg1N2Y3Mjg2YzIzZjcyYzIxOTMzMDM4ZmM0MTQwNDljZGU2NTZmNGU4MzI4ZTU0NiJ9fX0=";
    public static final String HEAD_RANK_CLANSMAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGFlZjZhZTE2OWNjNGYzZTM5Yzc2ZDA5NzE5OTNiOTJhYmU1MTBkOTZjOWJiNDk3YzFhYjlhMmI0YzJkYThhMSJ9fX0=";
    public static final String HEAD_RANK_GUARDIAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWFiNTRjMWNlOTQyYzIzMjFkNmRiYjgzMjQ1ZWJmM2ZmZDY5NmJmOWQxZjAyNDY3MzY0NzFmNjdmNzJiYTI1MCJ9fX0=";

    // Rank permission icons (Build / Invite Members / Kick Players)
    public static final String HEAD_PERMISSION_BUILD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2UzNGM5ZWVhY2QxZjMzNWRjMTA3NzdmMzBlMGQzYmNkMzM4ZmU4OTY1ZDM2NWQ2NDU2YjU2OGE1ZjQ5NmQyOSJ9fX0=";
    public static final String HEAD_PERMISSION_INVITE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=";
    public static final String HEAD_PERMISSION_KICK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ4YTk5ZGIyYzM3ZWM3MWQ3MTk5Y2Q1MjYzOTk4MWE3NTEzY2U5Y2NhOTYyNmEzOTM2Zjk2NWIxMzExOTMifX19";

    // Wool-textured heads for clan tag color picker (15 named colors)
    public static final String HEAD_WOOL_WHITE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDA4ZGY2MGM1MTA3NGVlZjI1NDRmZjM4Y2VhZDllMTY2NzVhZTQyNTE5MTYxMDUxODBlMWY4Y2UxOTdhYjNiYyJ9fX0=";
    public static final String HEAD_WOOL_GRAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTUyODhkZGM5MTFhNzVmNzdjM2E1ZDMzNjM2NWE4ZjhiMTM5ZmE1MzkzMGI0YjZlZTEzOTg3NWM4MGNlMzY2YyJ9fX0=";
    public static final String HEAD_WOOL_DARK_GRAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWRmMjFmNTMyMTIyNTY2YWY4OTNkYTI3ODgwYTFiNjA5NWMzNTcxMmYyOWEzNzhjZmVjYzdmZTJiMTMyOGFiNCJ9fX0=";
    public static final String HEAD_WOOL_BLACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2ZhNGRkYTZkMTlhMWZlMmQ5ODhkNjVkZWM1MzQyOTUwNTMwODE2NmM5MDY3YjY4YTQ3NzBjYTVjNDM2Y2Y5NCJ9fX0=";
    public static final String HEAD_WOOL_RED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjk1M2IxMmEwOTQ2YjYyOWI0YzA4ODlkNDFmZDI2ZWQyNmZiNzI5ZDRkNTE0YjU5NzI3MTI0YzM3YmI3MGQ4ZCJ9fX0=";
    public static final String HEAD_WOOL_DARK_RED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTkxMjdjYjdiZDNhOTg5ZDcyYzJlNWM0MjZlMWNjMTQ0NmI1ZTZkZTc0MTRkNDI3ODNmMmZlNmJhZGIxNzdkNCJ9fX0=";
    public static final String HEAD_WOOL_ORANGE_GOLD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWUzMDQ4MGVkMjgzNGY1YjdjYzgxZGNiMWRjN2U0NzY2NzkyNDMwYzQ5ZGFkNGRiY2QxMGE5M2M1ZTE0NTdiYyJ9fX0=";
    public static final String HEAD_WOOL_YELLOW = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGQ5MDUyNjlhY2NhYjI0YjExOTI0ZWJhOGJkOTI5OTFiOGQ4NWNlNDI3NjAyN2ExNjM2YzkzMWI2ZDA2Yzg5ZSJ9fX0=";
    public static final String HEAD_WOOL_LIME = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzc0NzJkNjA4ODIxZjQ1YTg4MDUzNzZlYzBjNmZmY2I3ODExNzgyOWVhNWY5NjAwNDFjMmEwOWQxMGUwNGNiNCJ9fX0=";
    public static final String HEAD_WOOL_DARK_GREEN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmRlZDQxYjU4NTZiZjhiNWVmMDY2MGEyZmY2ZTA3ODA5NmQ5OTg1NzAxNjZiNzc2ZmQ2YjFiYTRjZDA5MDhmNCJ9fX0=";
    public static final String HEAD_WOOL_LIGHT_BLUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTYzZTY2NDZmMWMwZDQxZmQzYmY1NTg0YTFjZTA0NGY1YzQ2ZDU5ODI1OGRiNDYyMTYxMTc4NTlmNTdhZjE5NyJ9fX0=";
    public static final String HEAD_WOOL_CYAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWZhZGY3NDFhYjc2Y2QzNjIwYWQxNjEzMDAyMDJkN2I1OWEzMzA1MWU1OTY3ZTRiNjE5NGJhYzQwYmIyODBmZiJ9fX0=";
    public static final String HEAD_WOOL_BLUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFhZjQ2ZmViZDQ1YzBmNGQ4MWU4ZmExYjY2YjI3NWQ4OWUyNzJiMmFkNTVjOTc4NTUzYTk5YzczM2UxZmYifX19";
    public static final String HEAD_WOOL_DARK_BLUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2YzZTQwNjI5MTE3NGQyNGNkZjBmOTUzZjhhMTc0YTgyYmIzNDg5ZGNlOGY2NzlhNDQzZWYxYWFlMDE2OTA2MSJ9fX0=";
    public static final String HEAD_WOOL_PINK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjk3MDEyZWQ2YTkyYjA1ZWEwZjE5NDk1MDc0ODU0NGUwNzViYWEyODc4MWNhMzczZDFiMjdlMjhjMjY5NTNjIn19fQ==";
    public static final String HEAD_WOOL_PURPLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmE5NGNiMjVkZTYyOGNhMzU5YjJmNmVhNWE4ODY4Y2JlMjY1OTVlZWRiMmJmZmI3NTA5NjdhZDFlZTE4NTAifX19";

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
