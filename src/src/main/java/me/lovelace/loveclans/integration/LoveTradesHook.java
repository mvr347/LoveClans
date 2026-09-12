package me.lovelace.loveclans.integration;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveTrades.LoveTrades;
import me.lovelace.loveTrades.api.ClanIntegration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Регистрирует LoveClans как поставщика клановых отношений для LoveTrades: враждующие
 * (ENEMY, либо в состоянии войны) кланы не могут торговать друг с другом, а союзные (ALLY)
 * получают бонус к ресурсам при обмене. Сама логика запрета/бонуса уже реализована в
 * LoveTrades (TradeManager) - этот класс лишь отвечает на два вопроса "враги?"/"союзники?".
 */
public final class LoveTradesHook {
    private final LoveClansPlugin plugin;

    public LoveTradesHook(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Plugin loveTradesPlugin = Bukkit.getPluginManager().getPlugin("LoveTrades");
        if (loveTradesPlugin instanceof LoveTrades loveTrades) {
            loveTrades.setClanIntegration(new Integration());
        }
    }

    private Optional<Clan> clanOf(Player player) {
        return plugin.getClanManager().getPlayerClan(player.getUniqueId());
    }

    private final class Integration extends ClanIntegration {
        @Override
        public boolean isEnemy(Player a, Player b) {
            Optional<Clan> clanAOpt = clanOf(a);
            Optional<Clan> clanBOpt = clanOf(b);
            if (clanAOpt.isEmpty() || clanBOpt.isEmpty()) {
                return false;
            }
            Clan clanA = clanAOpt.get();
            Clan clanB = clanBOpt.get();
            if (clanA.id().equals(clanB.id())) {
                return false;
            }
            if (plugin.getWarManager().areAtWar(clanA.id(), clanB.id()) || plugin.getSiegeManager().areInSiege(clanA.id(), clanB.id())
                    || plugin.getRaidManager().areInRaid(clanA.id(), clanB.id())) {
                return true;
            }
            // isEnemy() is LoveTrades' only "block this trade" hook, so embargo (§5.2) and blockade
            // (§5.3) piggyback on it too, even though neither actually changes the ALLY/NEUTRAL/ENEMY
            // relation - a blockaded clan can't trade with anyone outside its own clan, and an
            // embargo blocks trade specifically between the two embargoed clans.
            if (plugin.getDiplomacyManager().isBlockaded(clanA.id()) || plugin.getDiplomacyManager().isBlockaded(clanB.id())) {
                return true;
            }
            if (plugin.getDiplomacyManager().isEmbargoed(clanA.id(), clanB.id())) {
                return true;
            }
            // Enemy declarations are unilateral in this clan model (see ClanManager#setDiplomacyAsync -
            // only the declaring clan's relation is set), so check both directions.
            return clanA.relationTo(clanB.id()) == DiplomacyRelation.ENEMY
                    || clanB.relationTo(clanA.id()) == DiplomacyRelation.ENEMY;
        }

        @Override
        public boolean isAlly(Player a, Player b) {
            Optional<Clan> clanAOpt = clanOf(a);
            Optional<Clan> clanBOpt = clanOf(b);
            if (clanAOpt.isEmpty() || clanBOpt.isEmpty()) {
                return false;
            }
            Clan clanA = clanAOpt.get();
            Clan clanB = clanBOpt.get();
            if (clanA.id().equals(clanB.id())) {
                return false;
            }
            // Alliances are always mutual once accepted (ClanManager#acceptAllianceAsync sets
            // ALLY on both sides), so checking one direction is enough.
            return clanA.relationTo(clanB.id()) == DiplomacyRelation.ALLY;
        }
    }
}
