package me.lovelace.clans.model;

public enum ClanPermission {
    BUILD("clan.permission.build"),
    INVITE("clan.permission.invite"),
    KICK("clan.permission.kick"),
    DIPLOMACY("clan.permission.diplomacy"),
    UPGRADE("clan.permission.upgrade"),
    CLAIM("clan.permission.claim"),
    SETTINGS("clan.permission.settings");

    private final String key;

    ClanPermission(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return key;
    }
}