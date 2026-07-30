package me.lovelace.loveclans.integration;

import dev.lovelace.lovecore.api.social.ProfileOracle;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.DiplomacyRelation;

import java.util.Optional;
import java.util.UUID;

/**
 * Реализация {@link ProfileOracle} поверх {@code ClanManager}.
 *
 * <p>Регистрируется в {@code ServicesManager} с приоритетом выше {@code Normal} и вытесняет
 * рефлексивную реализацию из {@code lovecore-plugin}.</p>
 */
public final class LoveClansProfileOracle implements ProfileOracle {

    private final LoveClansPlugin plugin;

    public LoveClansProfileOracle(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<UUID> clanId(UUID playerId) {
        return clanOf(playerId).map(Clan::id);
    }

    @Override
    public Optional<String> clanTag(UUID playerId) {
        return clanOf(playerId).map(Clan::tag);
    }

    @Override
    public Optional<String> clanName(UUID playerId) {
        return clanOf(playerId).map(Clan::name);
    }

    @Override
    public boolean areEnemies(UUID first, UUID second) {
        Optional<Clan> a = clanOf(first);
        Optional<Clan> b = clanOf(second);
        if (a.isEmpty() || b.isEmpty() || a.get().id().equals(b.get().id())) {
            return false;
        }
        // Идущая война — вражда независимо от объявленных отношений: кланы могут официально
        // числиться нейтральными и при этом воевать за территорию.
        if (plugin.getWarManager().areAtWar(a.get().id(), b.get().id())) {
            return true;
        }
        // Вражда в LoveClans односторонняя — клан мог объявить врагом, не будучи объявленным
        // в ответ, — поэтому проверяем обе стороны и наружу отдаём симметричный ответ.
        return a.get().relationTo(b.get().id()) == DiplomacyRelation.ENEMY
                || b.get().relationTo(a.get().id()) == DiplomacyRelation.ENEMY;
    }

    @Override
    public boolean areClanmates(UUID first, UUID second) {
        Optional<Clan> a = clanOf(first);
        Optional<Clan> b = clanOf(second);
        return a.isPresent() && b.isPresent() && a.get().id().equals(b.get().id());
    }

    private Optional<Clan> clanOf(UUID playerId) {
        return plugin.getClanManager().getPlayerClan(playerId);
    }
}
