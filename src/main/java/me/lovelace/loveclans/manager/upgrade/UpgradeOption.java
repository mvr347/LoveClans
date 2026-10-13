package me.lovelace.loveclans.manager.upgrade;

import me.lovelace.loveclans.model.ClanUpgrade;

/** One concrete choice inside an offer: an ordinary upgrade or the one-time direction pick. */
public enum UpgradeOption {
    MEMBERS(ClanUpgrade.MEMBERS),
    CLAIM_SIZE(ClanUpgrade.CLAIM_SIZE),
    EXPERIENCE(ClanUpgrade.EXPERIENCE),
    CHEST(ClanUpgrade.CHEST),
    TAX(ClanUpgrade.TAX);

    private final ClanUpgrade upgrade;

    UpgradeOption(ClanUpgrade upgrade) {
        this.upgrade = upgrade;
    }

    public ClanUpgrade upgrade() {
        return upgrade;
    }

    public static UpgradeOption of(ClanUpgrade upgrade) {
        for (UpgradeOption option : values()) {
            if (option.upgrade == upgrade) {
                return option;
            }
        }
        throw new IllegalArgumentException("No option for " + upgrade);
    }
}
