package me.lovelace.loveclans.service;

import java.util.Arrays;
import java.util.List;

/**
 * Pure rules for GUI feedback sounds: which {@code sounds.gui-*} key a menu event maps to and when a sound must
 * stay silent. No Bukkit here, so it is unit-tested without a server; the Bukkit side (reading the clicked item,
 * playing the sound) lives in {@code GuiManager} and {@link SoundService}.
 */
public final class GuiSounds {
    private GuiSounds() {
    }

    /** What happened in a menu; every kind has exactly one {@code sounds.<key>} entry in config.yml. */
    public enum Kind {
        CLICK("gui-click"),
        OPEN("gui-open"),
        BACK("gui-back"),
        CLOSE("gui-close"),
        TOGGLE("gui-toggle"),
        SUCCESS("gui-success"),
        DENIED("gui-denied"),
        ERROR("gui-error");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** A GUI sound heard within this many ticks suppresses the "menu opened" sound (navigation already made noise). */
    public static final int OPEN_QUIET_TICKS = 20;

    /** Config key (without the {@code sounds.} prefix) of a kind. */
    public static String keyFor(Kind kind) {
        return kind.key();
    }

    /** Every GUI sound key, in declaration order. */
    public static List<String> allKeys() {
        return Arrays.stream(Kind.values()).map(Kind::key).toList();
    }

    /**
     * Sound kind of a click on one menu slot, or {@code null} when the slot must stay silent.
     *
     * @param passive   the slot is empty or holds frame glass
     * @param texture   texture id of the clicked head ({@link #textureId}), null for a non-head item
     * @param backNamed the item is named like the "back" button (the back texture is also used for "demote")
     * @param closeNamed the item is named like the "close" button
     */
    public static Kind classify(boolean passive, String texture, String backTexture, String closeTexture,
                                String inactiveTexture, boolean backNamed, boolean closeNamed) {
        if (passive) return null;
        if (texture != null) {
            if (texture.equals(backTexture) && backNamed) return Kind.BACK;
            if (texture.equals(closeTexture) && closeNamed) return Kind.CLOSE;
            if (texture.equals(inactiveTexture)) return Kind.DENIED;
        }
        return Kind.CLICK;
    }

    /** Last path segment of a skin URL (the hash). The scheme differs between http and https builds, the hash does not. */
    public static String textureId(String skinUrl) {
        if (skinUrl == null || skinUrl.isBlank()) return null;
        String trimmed = skinUrl.endsWith("/") ? skinUrl.substring(0, skinUrl.length() - 1) : skinUrl;
        String id = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return id.isEmpty() ? null : id;
    }

    /** The generic click sound is dropped when a specific result sound (success/denied/error/toggle) already played this tick. */
    public static boolean suppressClick(Integer lastSpecificTick, int nowTick) {
        return lastSpecificTick != null && lastSpecificTick == nowTick;
    }

    /** The "menu opened" sound is dropped right after another GUI sound, so navigation and refreshes stay quiet. */
    public static boolean suppressOpen(Integer lastAnyTick, int nowTick) {
        return lastAnyTick != null && nowTick - lastAnyTick < OPEN_QUIET_TICKS;
    }

    /** Kinds that report an action's outcome (they replace the click sound of the same tick). */
    public static boolean isSpecific(Kind kind) {
        return kind == Kind.SUCCESS || kind == Kind.DENIED || kind == Kind.ERROR || kind == Kind.TOGGLE;
    }
}
