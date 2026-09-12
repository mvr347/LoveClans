package me.lovelace.loveclans.textures;

import me.lovelace.loveclans.util.HeadsConfig;

/**
 * Централизованное хранилище base64 текстур голов (skull textures), используемых в GUI LoveClans.
 * <p>
 * Эти константы раньше были продублированы как приватные литералы прямо в отдельных GUI-классах
 * (например, {@code ClanCreateMenu}) и/или инлайнились по месту вызова {@code ItemBuilder.head(...)}
 * в нескольких меню одновременно - так одна и та же текстура существовала в нескольких копиях без
 * единой точки правды. Основной набор именованных head-констант плагина по-прежнему живёт в
 * {@code util.ItemBuilder} - трогать его не нужно; здесь собираются только текстуры, которые были
 * найдены как незакреплённые/дублирующиеся литералы по коду.
 */
public final class HeadTextures {

    private HeadTextures() {
        // Утилитарный класс-константа, инстанцирование не предполагается
    }

    /**
     * Иконка переименования (поле «название») - используется в меню создания клана,
     * настройках клана и настройках частной территории.
     */
    public static final String HEAD_NAME =
            HeadsConfig.get("name", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTY3ZDgxM2FlN2ZmZTViZTk1MWE0ZjQxZjJhYTYxOWE1ZTM4OTRlODVlYTVkNDk4NmY4NDk0OWM2M2Q3NjcyZSJ9fX0=");

    /**
     * Иконка поля «тег клана» - используется в меню создания и настройках клана.
     */
    public static final String HEAD_TAG =
            HeadsConfig.get("tag", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzFiYzJiY2ZiMmJkMzc1OWU2YjFlODZmYzdhNzk1ODVlMTEyN2RkMzU3ZmMyMDI4OTNmOWRlMjQxYmM5ZTUzMCJ9fX0=");

    /**
     * Иконка статуса «клан открыт» - используется в меню создания и настройках клана.
     */
    public static final String HEAD_OPEN =
            HeadsConfig.get("open", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTQ4YmI0ZTQ0MzVjMmMyMWQ3ZjYxODNiMzhhMmI3MzcyNjUzZjM1NDBiZTAyMjU5ZGQ0N2JmNTI0OTJkZTY2OSJ9fX0=");

    /**
     * Иконка статуса «клан закрыт» - используется в меню создания и настройках клана.
     */
    public static final String HEAD_CLOSED =
            HeadsConfig.get("closed", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmJmNDZiZjM5ZGZjNzE4ZTdlYTMxZGI0MzQ3N2ZjNmI3ZGNhNTg4ZmUwYTc4OTFkNDgxYzVkZGE5ZTE2ZjUyMCJ9fX0=");

    /**
     * Иконка смены цвета тега клана - настройки клана.
     */
    public static final String HEAD_COLOR =
            HeadsConfig.get("color", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGIyNDllODhhZmEzMGZjODM3YjgyMTczYTMwNDgzNDU4ZDRlOWEzM2M3ZWMyNWU1NTEzODdlOGU1NGEwMThhZSJ9fX0=");

    /**
     * Иконка управления правами рангов - настройки клана.
     */
    public static final String HEAD_ROLES =
            HeadsConfig.get("roles", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVmODcyZTMxYTQzZWU4YTY1Y2FjY2Y3M2I5NDJjOTdmMmNmODJjYzdjYmRhN2M5NzUyODc0MDliYzhlMjQxNCJ9fX0=");
}
