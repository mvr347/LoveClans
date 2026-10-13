package me.lovelace.loveclans.manager.upgrade;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.history.HistoryType;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanDirection;
import me.lovelace.loveclans.model.ClanPermission;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.ClanUpgrade;
import me.lovelace.loveclans.model.upgrade.UpgradeOffer;
import me.lovelace.loveclans.storage.UpgradeStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * "Pick one of two" upgrade offers and the one-time development direction pick.
 *
 * <p>Invariant: a clan has exactly as many pending ordinary offers as it has upgrade points.
 * {@link #topUp} restores it, and it is cheap, so it runs on level-up, on menu open and at startup
 * rather than at every place that can grant a point. Everything here runs on the main thread, which
 * is what makes a double click (or two members clicking at once) unable to spend one point twice;
 * the database write is fire-and-forget behind an in-memory state that is already final.
 */
public final class UpgradeOfferManager {

    public enum Result {
        OK, NO_PERMISSION, ALREADY_HANDLED, NOT_OLDEST, UNAVAILABLE, NO_POINTS, RESIZE_REFUSED, LOCKED
    }

    /** Unavailable reasons double as lang keys under {@code gui.upgrades.unavailable.}. */
    public static final String REASON_MAXED = "maxed";
    public static final String REASON_UNRECOGNIZED = "unrecognized";
    public static final String REASON_NO_TAX = "no-tax";
    public static final String REASON_NO_CLAIM = "no-claim";
    public static final String REASON_ALREADY_BIG = "already-big";
    public static final String REASON_BLOCKED = "blocked";

    private final LoveClansPlugin plugin;
    private final UpgradeStore store;
    private final Map<UUID, List<UpgradeOffer>> offers = new HashMap<>();
    private final Random random = new Random();

    public UpgradeOfferManager(LoveClansPlugin plugin, UpgradeStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void load() {
        offers.clear();
        store.loadOffers().forEach((clanId, list) -> offers.put(clanId, new ArrayList<>(list)));
    }

    // --- Queries ---

    private List<UpgradeOffer> all(Clan clan) {
        return offers.computeIfAbsent(clan.id(), k -> new ArrayList<>());
    }

    /** Pending ordinary offers, oldest first. */
    public List<UpgradeOffer> pending(Clan clan) {
        List<UpgradeOffer> result = new ArrayList<>();
        for (UpgradeOffer offer : all(clan)) {
            if (offer.pending() && offer.kind() == UpgradeOffer.Kind.NORMAL) {
                result.add(offer);
            }
        }
        return result;
    }

    public Optional<UpgradeOffer> pendingDirection(Clan clan) {
        return all(clan).stream().filter(o -> o.pending() && o.kind() == UpgradeOffer.Kind.DIRECTION).findFirst();
    }

    public int pendingCount(Clan clan) {
        return pending(clan).size();
    }

    public Optional<UpgradeOffer> byId(Clan clan, UUID offerId) {
        return all(clan).stream().filter(o -> o.id().equals(offerId)).findFirst();
    }

    public int maxLevel(ClanUpgrade upgrade) {
        return plugin.getConfig().getInt("upgrades." + upgrade.name() + ".max-level", 1);
    }

    /** @return null when the option can still bring something, otherwise the reason (see REASON_*). */
    public String unavailableReason(Clan clan, UpgradeOption option) {
        ClanUpgrade upgrade = option.upgrade();
        int level = clan.upgradeLevel(upgrade);
        if (level >= maxLevel(upgrade)) {
            return REASON_MAXED;
        }
        switch (option) {
            case MEMBERS -> {
                // The member limit does not apply to an unrecognized clan, so more places would be wasted.
                if (!clan.isRecognized()) return REASON_UNRECOGNIZED;
            }
            case TAX -> {
                if (!plugin.getClanManager().isTaxApplicable(clan)) return REASON_NO_TAX;
            }
            case CHEST -> {
                if (clan.chestRows() >= plugin.getConfig().getInt("limits.max-chest-rows", 5)) return REASON_MAXED;
            }
            case CLAIM_SIZE -> {
                Optional<ClanTerritory> capital = clan.getCapitalTerritory();
                var hook = plugin.getAdvancedClaimsHook();
                if (capital.isEmpty() || hook == null || !hook.enabled()) return REASON_NO_CLAIM;
                var current = hook.currentRadiusOf(capital.get());
                int perLevel = plugin.getConfig().getInt("upgrades.CLAIM_SIZE.blocks-per-level", 1);
                int target = plugin.getConfig().getInt("integration.advanced-claims.claim-radius", 25) + (level + 1) * perLevel;
                if (current.isEmpty()) return REASON_NO_CLAIM;
                // Claims created under the old 35-block radius are already at least as big.
                if (current.getAsInt() >= target) return REASON_ALREADY_BIG;
                if (!hook.canResizeTo(capital.get(), level + 1)) return REASON_BLOCKED;
            }
            default -> { }
        }
        return null;
    }

    /** Current and next value of an option, as plain numbers for the lang templates. */
    public Map<String, String> describe(Clan clan, UpgradeOption option) {
        var cfg = plugin.getConfig();
        int level = clan.upgradeLevel(option.upgrade());
        Map<String, String> values = new HashMap<>();
        switch (option) {
            case MEMBERS -> {
                int per = cfg.getInt("limits.members-per-upgrade", 3);
                int now = plugin.getClanManager().maxMembers(clan);
                values.put("now", String.valueOf(now));
                values.put("after", String.valueOf(now + per));
                values.put("delta", String.valueOf(per));
            }
            case CLAIM_SIZE -> {
                int per = cfg.getInt("upgrades.CLAIM_SIZE.blocks-per-level", 1);
                int base = cfg.getInt("integration.advanced-claims.claim-radius", 25);
                int now = clan.getCapitalTerritory().flatMap(t -> {
                    var r = plugin.getAdvancedClaimsHook().currentRadiusOf(t);
                    return r.isPresent() ? Optional.of(r.getAsInt()) : Optional.<Integer>empty();
                }).orElse(base + level * per);
                int after = Math.max(now, base + (level + 1) * per);
                values.put("now", now * 2 + 1 + "x" + (now * 2 + 1));
                values.put("after", after * 2 + 1 + "x" + (after * 2 + 1));
                values.put("delta", String.valueOf(per));
            }
            case EXPERIENCE -> {
                double per = cfg.getDouble("limits.experience-percent-per-upgrade", 5.0D);
                values.put("now", trim(level * per));
                values.put("after", trim((level + 1) * per));
                values.put("delta", trim(per));
            }
            case CHEST -> {
                values.put("now", String.valueOf(clan.chestRows()));
                values.put("after", String.valueOf(clan.chestRows() + 1));
                values.put("delta", "1");
            }
            case TAX -> {
                double per = cfg.getDouble("limits.tax-discount-percent-per-upgrade", 10.0D);
                values.put("now", trim(Math.min(90.0D, level * per)));
                values.put("after", trim(Math.min(90.0D, (level + 1) * per)));
                values.put("delta", trim(per));
            }
        }
        values.put("level", String.valueOf(level));
        values.put("max", String.valueOf(maxLevel(option.upgrade())));
        return values;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // --- Generation ---

    /**
     * Restores "pending ordinary offers == upgrade points" and creates the direction offer once the
     * clan is high enough. Quietly does nothing when no option is available: the point then simply
     * waits, and the next call (menu open, level-up) tries again.
     *
     * @return how many new offers were created
     */
    public int topUp(Clan clan) {
        if (clan == null) {
            return 0;
        }
        int created = 0;
        List<UpgradeOffer> pending = pending(clan);
        // More offers than points (e.g. an admin removed points): drop the newest extras.
        for (int i = pending.size() - 1; i >= clan.upgradePoints() && i >= 0; i--) {
            UpgradeOffer extra = pending.get(i);
            if (extra.cancel()) {
                store.markHandled(extra);
            }
        }
        int missing = clan.upgradePoints() - Math.min(pending.size(), clan.upgradePoints());
        for (int i = 0; i < missing; i++) {
            List<UpgradeOption> pair = OfferGenerator.pair(option -> unavailableReason(clan, option) == null, random);
            if (pair.isEmpty()) {
                break;
            }
            UpgradeOffer offer = UpgradeOffer.create(clan.id(), UpgradeOffer.Kind.NORMAL,
                    pair.get(0).name(), pair.size() > 1 ? pair.get(1).name() : null);
            all(clan).add(offer);
            store.insertOffer(offer);
            created++;
        }
        if (clan.direction().isEmpty() && pendingDirection(clan).isEmpty()
                && clan.level() >= plugin.getConfig().getInt("direction.tiers.first", 5)) {
            UpgradeOffer offer = UpgradeOffer.create(clan.id(), UpgradeOffer.Kind.DIRECTION,
                    "DIRECTION", null);
            all(clan).add(offer);
            store.insertOffer(offer);
            created++;
        }
        return created;
    }

    public void topUpAll() {
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            topUp(clan);
        }
    }

    /** Level-up: create the offer and tell the owner. */
    public void onLevelUp(Clan clan) {
        if (topUp(clan) > 0) {
            notifyOwner(clan);
        }
    }

    public void notifyOwner(Clan clan) {
        clan.leaderId().map(Bukkit::getPlayer).ifPresent(this::notifyPlayer);
    }

    public void notifyPlayer(Player player) {
        plugin.getMessages().send(player, "gui.upgrades.notify");
        plugin.getSoundService().play(player, "upgrade-offer");
    }

    /** On join: remind a member who may choose about offers waiting for them. */
    public void remindIfPending(Player player, Clan clan) {
        if (clan.hasPermission(player.getUniqueId(), ClanPermission.UPGRADE)
                && (pendingCount(clan) > 0 || pendingDirection(clan).isPresent())) {
            notifyPlayer(player);
        }
    }

    // --- Choosing ---

    /**
     * Resolves an ordinary offer in favour of {@code option}. Must run on the main thread.
     * The oldest pending offer is the only one that can be chosen.
     */
    public Result choose(Clan clan, Player actor, UUID offerId, UpgradeOption option) {
        if (!clan.hasPermission(actor.getUniqueId(), ClanPermission.UPGRADE)) {
            return Result.NO_PERMISSION;
        }
        Optional<UpgradeOffer> found = byId(clan, offerId);
        if (found.isEmpty() || !found.get().pending() || found.get().kind() != UpgradeOffer.Kind.NORMAL) {
            return Result.ALREADY_HANDLED;
        }
        UpgradeOffer offer = found.get();
        List<UpgradeOffer> pending = pending(clan);
        if (pending.isEmpty() || !pending.get(0).id().equals(offer.id())) {
            return Result.NOT_OLDEST;
        }
        if (!offer.offers(option.name())) {
            return Result.ALREADY_HANDLED;
        }
        if (clan.upgradePoints() <= 0) {
            return Result.NO_POINTS;
        }
        if (unavailableReason(clan, option) != null) {
            return Result.UNAVAILABLE;
        }
        ClanUpgrade upgrade = option.upgrade();
        int newLevel = clan.upgradeLevel(upgrade) + 1;
        if (option == UpgradeOption.CLAIM_SIZE) {
            ClanTerritory capital = clan.getCapitalTerritory().orElse(null);
            if (!plugin.getAdvancedClaimsHook().resizeToUpgradeLevel(clan, capital, newLevel)) {
                return Result.RESIZE_REFUSED; // the point is kept
            }
        }
        offer.choose(option.name());
        clan.setUpgradeLevel(upgrade, newLevel);
        clan.removeUpgradePoints(1);
        if (option == UpgradeOption.CHEST) {
            clan.setChestRows(clan.chestRows() + 1);
        }
        store.markHandled(offer);
        var storage = plugin.getClanManager().storage();
        storage.saveUpgradeAsync(clan.id(), upgrade, newLevel);
        storage.updateClanUpgradePoints(clan.id(), clan.upgradePoints());
        if (option == UpgradeOption.CHEST) {
            storage.updateClanChestRows(clan.id(), clan.chestRows());
        }
        if (option == UpgradeOption.CLAIM_SIZE) {
            plugin.getClanManager().reindexAllTerritories();
        }
        plugin.getClanManager().recalculateInfluenceAsync(clan);
        plugin.getHistoryManager().add(clan.id(), HistoryType.UPGRADE_CHOSEN, actor.getUniqueId(),
                Map.of("upgrade", upgrade.name(), "level", String.valueOf(newLevel)));
        plugin.getSoundService().play(actor, "upgrade-chosen");
        // The pair after this one may now be stale (e.g. this option just hit its cap): offers that
        // were already created stay as they are - only an offer with NO usable option gets rebuilt.
        refreshDeadOffers(clan);
        return Result.OK;
    }

    /** An offer whose every option became unavailable would be unclickable forever: replace it. */
    public void refreshDeadOffers(Clan clan) {
        for (UpgradeOffer offer : pending(clan)) {
            boolean alive = false;
            for (String name : new String[]{offer.option1(), offer.option2()}) {
                if (name == null) continue;
                try {
                    if (unavailableReason(clan, UpgradeOption.valueOf(name)) == null) alive = true;
                } catch (IllegalArgumentException ignored) {
                    // an option that was removed from the plugin
                }
            }
            if (!alive && offer.cancel()) {
                store.markHandled(offer);
            }
        }
        topUp(clan);
    }

    /** Chooses the development direction. Irreversible; main thread. */
    public Result chooseDirection(Clan clan, Player actor, ClanDirection direction) {
        if (!clan.hasPermission(actor.getUniqueId(), ClanPermission.UPGRADE)) {
            return Result.NO_PERMISSION;
        }
        if (clan.level() < plugin.getConfig().getInt("direction.tiers.first", 5)) {
            return Result.LOCKED;
        }
        Optional<UpgradeOffer> offer = pendingDirection(clan);
        if (clan.direction().isPresent() || offer.isEmpty()) {
            return Result.ALREADY_HANDLED;
        }
        offer.get().resolve(direction.name());
        clan.setDirection(direction, System.currentTimeMillis());
        store.markHandled(offer.get());
        plugin.getClanManager().storage().updateClanDirection(clan.id(), direction, clan.directionChosenAt());
        plugin.getHistoryManager().add(clan.id(), HistoryType.DIRECTION_CHOSEN, actor.getUniqueId(),
                Map.of("direction", direction.name()));
        plugin.getSoundService().playClan(clan, "upgrade-chosen");
        return Result.OK;
    }
}
