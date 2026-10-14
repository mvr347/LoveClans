package me.lovelace.loveclans.listener;

import java.util.UUID;

/** Pure choice of the action bar text shown when a player enters a clan territory. */
public final class TerritoryEnterText {

    public enum Kind {
        OWN("territory.enter.own"),
        ENEMY("territory.enter.enemy"),
        FOREIGN("territory.enter.foreign");

        private final String langKey;

        Kind(String langKey) {
            this.langKey = langKey;
        }

        public String langKey() {
            return langKey;
        }
    }

    private TerritoryEnterText() {
    }

    /**
     * @param viewerClanId clan of the entering player, or null when the player has no clan
     * @param ownerClanId  clan that owns the territory
     * @param atWar        whether the viewer's clan and the owner are at war (PREPARING or ACTIVE)
     */
    public static Kind choose(UUID viewerClanId, UUID ownerClanId, boolean atWar) {
        if (viewerClanId != null && viewerClanId.equals(ownerClanId)) {
            return Kind.OWN;
        }
        if (viewerClanId != null && atWar) {
            return Kind.ENEMY;
        }
        return Kind.FOREIGN;
    }
}
