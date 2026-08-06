package me.lovelace.loveclans.command;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import me.lovelace.loveclans.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Единая административная команда плагина: {@code /loveclansadmin <subcommand>}
 * (алиасы {@code /clansadmin}, {@code /ca}).
 * <p>
 * Раньше все эти действия были собраны под {@code /clan admin <action> ...} внутри
 * {@link ClanCommand}, плюс отдельно жили {@code /clan reload} и {@code /clan artifact} там же -
 * а сама {@code /loveclansadmin}/{@code /clansadmin}/{@code /ca} лишь переупаковывала свои
 * аргументы под {@code /clan admin ...} через хак в начале {@code ClanCommand#onCommand} и не
 * знала о reload вовсе. Здесь вся админ-логика собрана в одном месте по образцу принятого в
 * экосистеме Love* единого родительского admin-класса (см. {@code LoveChatAdminCommand} в
 * LoveChat) - старые пути в {@link ClanCommand} остаются рабочими, но лишь перенаправляют сюда
 * короткой подсказкой, а не пропадают молча.
 */
public final class ClansAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "createnpc", "removenpc", "disband", "war", "diplo", "exp", "points", "artifact", "help"
    );
    private static final List<String> AMOUNT_ACTIONS = List.of("add", "remove", "set");
    private static final List<String> WAR_ACTIONS = List.of("start", "end");
    private static final List<String> RELATIONS = List.of("ally", "enemy", "neutral");

    private final LoveClansPlugin plugin;

    public ClansAdminCommand(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!Permissions.has(sender, Permissions.ADMIN)) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> reload(sender);
                case "createnpc" -> createNpc(sender, args);
                case "removenpc" -> removeNpc(sender, args);
                case "disband" -> disband(sender, args);
                case "war" -> war(sender, args);
                case "diplo" -> diplo(sender, args);
                case "exp" -> expOrPoints(sender, args, true);
                case "points" -> expOrPoints(sender, args, false);
                case "artifact" -> artifact(sender, args);
                default -> sendHelp(sender);
            }
        } catch (IllegalStateException exception) {
            plugin.sendOperationError(sender, exception);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getMessages().reload();
        plugin.getMessages().send(sender, "general.reloaded");
    }

    private void createNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.players-only");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("contracts")) {
            plugin.getMessages().send(sender, "admin.npc.unknown-type");
            return;
        }
        double distance = plugin.getConfig().getDouble("clans.contracts.npc-bind-distance", 6.0);
        var npc = plugin.getCitizensIntegration().lookedAtNpc(player, distance);
        Integer npcId = npc == null ? null : plugin.getCitizensIntegration().npcId(npc);
        if (npcId == null) {
            plugin.getMessages().send(player, "admin.npc.not-looking-at-npc");
            return;
        }
        plugin.getConfig().set("clans.contracts.npc-id", npcId);
        plugin.saveConfig();
        plugin.getMessages().send(player, "admin.npc.bound", Map.of("id", String.valueOf(npcId)));
    }

    private void removeNpc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "clan.help.admin-npc");
            return;
        }
        int targetId;
        try {
            targetId = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            plugin.getMessages().send(sender, "general.invalid-number");
            return;
        }
        int currentId = plugin.getConfig().getInt("clans.contracts.npc-id", -1);
        if (currentId != targetId) {
            plugin.getMessages().send(sender, "admin.npc.not-bound");
            return;
        }
        plugin.getConfig().set("clans.contracts.npc-id", -1);
        plugin.saveConfig();
        plugin.getMessages().send(sender, "admin.npc.unbound", Map.of("id", String.valueOf(targetId)));
    }

    private void disband(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "clan.help.admin-disband");
            return;
        }
        Optional<Clan> clanOpt = plugin.getClanManager().getClanByTag(args[1]);
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(sender, "clan.not-found");
            return;
        }
        Clan clan = clanOpt.get();
        plugin.getClanManager().disbandClanAsync(clan, null) // null actorId bypasses permission check
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(sender, "admin.disbanded", Map.of("tag", clan.tag(), "color", clan.tagColor()))))
                .exceptionally(ex -> {
                    plugin.runSync(() -> plugin.sendOperationError(sender, ex));
                    return null;
                });
    }

    private void war(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getMessages().send(sender, "clan.help.admin-war");
            return;
        }
        String subAction = args[1].toLowerCase(Locale.ROOT);
        Optional<Clan> clan1Opt = plugin.getClanManager().getClanByTag(args[2]);
        Optional<Clan> clan2Opt = plugin.getClanManager().getClanByTag(args[3]);
        if (clan1Opt.isEmpty() || clan2Opt.isEmpty()) {
            plugin.getMessages().send(sender, "clan.not-found");
            return;
        }
        Clan clan1 = clan1Opt.get();
        Clan clan2 = clan2Opt.get();

        if (subAction.equals("start")) {
            plugin.getWarManager().startWarAsync(clan1, clan2, null)
                    .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(sender, "admin.war-started", Map.of("clan1", clan1.tag(), "color1", clan1.tagColor(), "clan2", clan2.tag(), "color2", clan2.tagColor()))))
                    .exceptionally(ex -> {
                        plugin.runSync(() -> plugin.sendOperationError(sender, ex));
                        return null;
                    });
        } else if (subAction.equals("end")) {
            plugin.getWarManager().peaceAsync(clan1, clan2)
                    .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(sender, "admin.war-ended", Map.of("clan1", clan1.tag(), "color1", clan1.tagColor(), "clan2", clan2.tag(), "color2", clan2.tagColor()))))
                    .exceptionally(ex -> {
                        plugin.runSync(() -> plugin.sendOperationError(sender, ex));
                        return null;
                    });
        } else {
            plugin.getMessages().send(sender, "clan.help.admin-war");
        }
    }

    private void diplo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getMessages().send(sender, "clan.help.admin-diplo");
            return;
        }
        Optional<Clan> clan1Opt = plugin.getClanManager().getClanByTag(args[1]);
        Optional<Clan> clan2Opt = plugin.getClanManager().getClanByTag(args[2]);
        if (clan1Opt.isEmpty() || clan2Opt.isEmpty()) {
            plugin.getMessages().send(sender, "clan.not-found");
            return;
        }
        Clan clan1 = clan1Opt.get();
        Clan clan2 = clan2Opt.get();

        DiplomacyRelation relation;
        try {
            relation = DiplomacyRelation.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getMessages().send(sender, "general.invalid-relation");
            return;
        }

        plugin.getClanManager().setDiplomacyAsync(clan1, clan2, relation, null) // null actorId
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(sender, "admin.diplo-updated", Map.of("clan1", clan1.tag(), "color1", clan1.tagColor(), "clan2", clan2.tag(), "color2", clan2.tagColor(), "relation", plugin.getMessages().relationName(relation)))))
                .exceptionally(ex -> {
                    plugin.runSync(() -> plugin.sendOperationError(sender, ex));
                    return null;
                });
    }

    private void expOrPoints(CommandSender sender, String[] args, boolean isExp) {
        String helpKey = isExp ? "clan.help.admin-exp" : "clan.help.admin-points";
        if (args.length < 4) {
            plugin.getMessages().send(sender, helpKey);
            return;
        }
        String subAction = args[1].toLowerCase(Locale.ROOT); // add, remove, set
        if (!AMOUNT_ACTIONS.contains(subAction)) {
            plugin.getMessages().send(sender, helpKey);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessages().send(sender, "general.invalid-number");
            return;
        }

        Optional<Clan> clanOpt = plugin.getClanManager().getClanByTag(args[2]);
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(sender, "clan.not-found");
            return;
        }
        Clan clan = clanOpt.get();

        if (isExp) {
            switch (subAction) {
                case "add" -> plugin.getClanManager().addExperienceAsync(clan, amount);
                case "remove" -> {
                    clan.removeExperience(amount);
                    plugin.getClanManager().updateClanAsync(clan);
                }
                case "set" -> {
                    clan.setExperience(amount);
                    plugin.getClanManager().updateClanAsync(clan);
                }
            }
            plugin.getMessages().send(sender, "admin.exp-updated", Map.of("tag", clan.tag(), "color", clan.tagColor()));
        } else {
            switch (subAction) {
                case "add" -> clan.addUpgradePoints((int) amount);
                case "remove" -> clan.removeUpgradePoints((int) amount);
                case "set" -> clan.setUpgradePoints((int) amount);
            }
            plugin.getClanManager().updateClanAsync(clan);
            plugin.getMessages().send(sender, "admin.points-updated", Map.of("tag", clan.tag(), "color", clan.tagColor()));
        }
    }

    private void artifact(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.players-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "clan.help.artifact");
            return;
        }
        ArtifactType type;
        try {
            type = ArtifactType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("general.error", exception);
        }
        player.getInventory().addItem(plugin.getArtifactManager().createArtifact(type));
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessages().send(sender, "clan.help.admin-header");
        plugin.getMessages().send(sender, "clan.help.admin-reload");
        plugin.getMessages().send(sender, "clan.help.admin-npc");
        plugin.getMessages().send(sender, "clan.help.admin-disband");
        plugin.getMessages().send(sender, "clan.help.admin-war");
        plugin.getMessages().send(sender, "clan.help.admin-diplo");
        plugin.getMessages().send(sender, "clan.help.admin-exp");
        plugin.getMessages().send(sender, "clan.help.admin-points");
        plugin.getMessages().send(sender, "clan.help.artifact");
        plugin.getMessages().send(sender, "clan.help.admin-footer");
    }

    @Nullable
    @Override
    @SuppressWarnings("NullableProblems")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!Permissions.has(sender, Permissions.ADMIN)) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }

        List<String> clanTags = plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList());
        String action = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            List<String> completions = switch (action) {
                case "removenpc" -> List.of();
                case "createnpc" -> List.of("contracts");
                case "disband", "diplo" -> clanTags;
                case "war" -> WAR_ACTIONS;
                case "exp", "points" -> AMOUNT_ACTIONS;
                case "artifact" -> Arrays.stream(ArtifactType.values())
                        .map(type -> type.name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toList());
                default -> List.of();
            };
            return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
        }

        if (args.length == 3) {
            List<String> completions = switch (action) {
                case "diplo", "war" -> clanTags;
                case "exp", "points" -> clanTags;
                default -> List.of();
            };
            return StringUtil.copyPartialMatches(args[2], completions, new ArrayList<>());
        }

        if (args.length == 4 && action.equals("diplo")) {
            return StringUtil.copyPartialMatches(args[3], RELATIONS, new ArrayList<>());
        }
        if (args.length == 4 && action.equals("war")) {
            return StringUtil.copyPartialMatches(args[3], clanTags, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
