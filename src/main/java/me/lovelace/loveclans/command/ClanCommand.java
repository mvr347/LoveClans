package me.lovelace.loveclans.command;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.api.events.ClanDiplomacyChangeEvent;
import me.lovelace.loveclans.gui.*;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.model.ClanRank;
import me.lovelace.loveclans.model.ClanTerritory;
import me.lovelace.loveclans.model.DiplomacyRelation;
import me.lovelace.loveclans.model.TerritoryKey;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import me.lovelace.loveclans.model.raid.ClanRaid;
import me.lovelace.loveclans.model.ritual.RitualType;
import me.lovelace.loveclans.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location; // Import Location
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ClanCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_PLAYER_IN_CLAN = List.of(
            "help", "disband", "invite", "accept", "leave", "kick", "promote", "demote",
            "info", "claim", "unclaim", "menu", "members", "territories", "upgrades", "spirit",
            "war", "siege", "raid", "peace", "ally", "enemy", "neutral", "diplo", "letters", "ritual", "vote", "settings", "applications", "list", "home", "chest", "contracts"
    );
    private static final List<String> ROOT_PLAYER_NOT_IN_CLAN = List.of(
            "help", "create", "accept", "list", "info"
    );
    private static final List<String> ROOT_ADMIN = List.of(
            "artifact", "reload", "admin"
    );

    private final LoveClansPlugin plugin;

    public ClanCommand(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!Permissions.has(sender, Permissions.COMMAND)) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }

        String lbl = label.toLowerCase(Locale.ROOT);

        // /clans, /loveclans or /clan list — open clan list
        if (lbl.equals("clans") || lbl.equals("loveclans") || (args.length > 0 && args[0].equalsIgnoreCase("list"))) {
            if (sender instanceof Player player) {
                if (plugin.getClanManager().getAllClans().isEmpty()) {
                    plugin.getMessages().send(player, "clan.list.empty");
                } else {
                    openClanList(player);
                }
            } else {
                plugin.getMessages().send(sender, "general.players-only");
            }
            return true;
        }

        // /diplo or /cd — shorthand diplomacy command
        if (lbl.equals("diplo") || lbl.equals("cd")) {
            if (sender instanceof Player player) {
                try { openDiplomacyFor(player, args.length > 0 ? args[0] : null); }
                catch (IllegalStateException e) { plugin.sendOperationError(player, e); }
            } else {
                plugin.getMessages().send(sender, "general.players-only");
            }
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                Optional<Clan> playerClan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
                if (playerClan.isPresent()) {
                    plugin.getGuiManager().openMain(player, playerClan.get());
                } else {
                    if (plugin.getClanManager().getAllClans().isEmpty()) {
                        plugin.getMessages().send(player, "clan.list.empty");
                    } else {
                        openClanList(player);
                    }
                }
            } else {
                plugin.getMessages().send(sender, "clan.help.console");
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "help" -> {
                    if (sender instanceof Player player) {
                        Optional<Clan> playerClan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
                        if (playerClan.isPresent()) {
                            boolean isLeader = playerClan.get().member(player.getUniqueId())
                                    .map(member -> member.rank() == ClanRank.LEADER)
                                    .orElse(false);
                            plugin.getMessages().send(player, isLeader ? "clan.help.leader" : "clan.help.in-clan");
                        } else {
                            plugin.getMessages().send(player, "clan.help.not-in-clan");
                        }
                    } else {
                        plugin.getMessages().send(sender, "clan.help.console");
                    }
                }
                case "create" -> openCreateGui(requirePlayer(sender));
                case "disband" -> disband(requirePlayer(sender));
                case "invite" -> invite(requirePlayer(sender), args);
                case "accept" -> accept(requirePlayer(sender), args);
                case "leave" -> leave(requirePlayer(sender));
                case "kick" -> kick(requirePlayer(sender), args);
                case "promote" -> rank(requirePlayer(sender), args, ClanRank.GUARDIAN);
                case "demote" -> rank(requirePlayer(sender), args, ClanRank.MEMBER);
                case "info" -> info(sender, args);
                case "claim" -> claim(requirePlayer(sender));
                case "unclaim" -> unclaim(requirePlayer(sender));
                case "menu" -> openMenu(requirePlayer(sender));
                case "members" -> openMembers(requirePlayer(sender));
                case "territories" -> openTerritories(requirePlayer(sender));
                case "upgrades" -> openUpgrades(requirePlayer(sender));
                case "spirit" -> openSpirit(requirePlayer(sender));
                case "list" -> openClanList(requirePlayer(sender));
                case "war" -> war(requirePlayer(sender), args);
                case "siege" -> siege(requirePlayer(sender), args);
                case "raid" -> raid(requirePlayer(sender), args);
                case "peace" -> peace(requirePlayer(sender), args);
                case "ally" -> {
                    if (args.length >= 3 && args[1].equalsIgnoreCase("accept")) {
                        allianceAccept(requirePlayer(sender), args[2]);
                    } else if (args.length >= 3 && args[1].equalsIgnoreCase("decline")) {
                        allianceDecline(requirePlayer(sender), args[2]);
                    } else {
                        diplomacy(requirePlayer(sender), args, DiplomacyRelation.ALLY);
                    }
                }
                case "enemy" -> diplomacy(requirePlayer(sender), args, DiplomacyRelation.ENEMY);
                case "neutral" -> diplomacy(requirePlayer(sender), args, DiplomacyRelation.NEUTRAL);
                case "diplo" -> openDiplomacyFor(requirePlayer(sender), args.length > 1 ? args[1] : null);
                case "letters" -> openLetters(requirePlayer(sender), args);
                case "decline" -> declineInvite(requirePlayer(sender), args);
                case "ritual" -> ritual(requirePlayer(sender), args);
                case "vote" -> vote(requirePlayer(sender), args);
                case "artifact" -> artifact(sender, args);
                case "reload" -> reload(sender);
                case "admin" -> admin(sender, args);
                case "settings" -> openSettings(requirePlayer(sender));
                case "applications" -> {
                    if (args.length >= 3 && args[1].equalsIgnoreCase("accept")) {
                        applicationAccept(requirePlayer(sender), args[2]);
                    } else if (args.length >= 3 && args[1].equalsIgnoreCase("reject")) {
                        applicationReject(requirePlayer(sender), args[2]);
                    } else {
                        openApplications(requirePlayer(sender));
                    }
                }
                case "home" -> home(requirePlayer(sender)); // Added home command handler
                case "chest" -> openChest(requirePlayer(sender));
                case "contracts" -> openContracts(requirePlayer(sender));
                case "confirm" -> confirmPendingChatInput(requirePlayer(sender));
                case "cancel" -> cancelPendingChatInput(requirePlayer(sender));
                default -> plugin.getMessages().send(sender, "general.unknown-command");
            }
        } catch (IllegalStateException exception) {
            plugin.sendOperationError(sender, exception);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (sender instanceof Player player) {
            Optional<Clan> playerClan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
            if (args.length == 1) {
                if (playerClan.isPresent()) {
                    completions.addAll(ROOT_PLAYER_IN_CLAN);
                } else {
                    completions.addAll(ROOT_PLAYER_NOT_IN_CLAN);
                }
                if (Permissions.has(sender, Permissions.ADMIN)) {
                    completions.addAll(ROOT_ADMIN);
                }
                return filter(completions, args[0]);
            }
            if (args.length == 2) {
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "accept", "war", "siege", "raid", "peace", "ally", "enemy", "neutral", "info", "letters" ->
                            completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                    case "ritual" ->
                            completions.addAll(Arrays.stream(RitualType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
                    case "artifact" ->
                            completions.addAll(Arrays.stream(ArtifactType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
                    case "invite", "kick", "promote", "demote", "vote" ->
                            Bukkit.getOnlinePlayers().stream().map(OfflinePlayer::getName).filter(Objects::nonNull).forEach(completions::add);
                    case "admin" -> {
                        if (Permissions.has(sender, Permissions.ADMIN)) {
                            completions.addAll(List.of("exp", "points", "diplo", "war", "disband", "createnpc", "removenpc"));
                        }
                    }
                }
                return filter(completions, args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && Permissions.has(sender, Permissions.ADMIN)) {
                 if (args[1].equalsIgnoreCase("exp") || args[1].equalsIgnoreCase("points")) {
                     completions.addAll(List.of("add", "remove", "set"));
                 } else if (args[1].equalsIgnoreCase("war")) {
                     completions.addAll(List.of("start", "end"));
                 } else if (args[1].equalsIgnoreCase("diplo") || args[1].equalsIgnoreCase("disband")) {
                     completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                 } else if (args[1].equalsIgnoreCase("createnpc")) {
                     completions.add("contracts");
                 }
                 return filter(completions, args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("admin") && Permissions.has(sender, Permissions.ADMIN)) {
                 if (args[1].equalsIgnoreCase("diplo") || args[1].equalsIgnoreCase("war")) {
                     completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                 } else if (!args[1].equalsIgnoreCase("disband")) {
                     completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                 }
                 return filter(completions, args[3]);
            }
            if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("diplo") && Permissions.has(sender, Permissions.ADMIN)) {
                completions.addAll(List.of("ally", "enemy", "neutral"));
                return filter(completions, args[4]);
            }
        } else { // Console sender
            if (args.length == 1) {
                completions.addAll(ROOT_ADMIN); // Only admin commands for console
                completions.add("info"); // Allow console to get clan info
                return filter(completions, args[0]);
            }
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("info")) {
                    completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                }
                if (args[0].equalsIgnoreCase("artifact")) {
                    completions.addAll(Arrays.stream(ArtifactType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
                }
                if (args[0].equalsIgnoreCase("admin")) {
                    completions.addAll(List.of("exp", "points", "diplo", "war", "disband", "createnpc", "removenpc"));
                }
                return filter(completions, args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
                 if (args[1].equalsIgnoreCase("exp") || args[1].equalsIgnoreCase("points")) {
                     completions.addAll(List.of("add", "remove", "set"));
                 } else if (args[1].equalsIgnoreCase("war")) {
                     completions.addAll(List.of("start", "end"));
                 } else if (args[1].equalsIgnoreCase("diplo") || args[1].equalsIgnoreCase("disband")) {
                     completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                 } else if (args[1].equalsIgnoreCase("createnpc")) {
                     completions.add("contracts");
                 }
                 return filter(completions, args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
                 if (args[1].equalsIgnoreCase("diplo") || args[1].equalsIgnoreCase("war")) {
                     completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                 } else if (!args[1].equalsIgnoreCase("disband")) {
                     completions.addAll(plugin.getClanManager().getAllClans().stream().map(Clan::tag).collect(Collectors.toList()));
                 }
                 return filter(completions, args[3]);
            }
            if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("diplo")) {
                completions.addAll(List.of("ally", "enemy", "neutral"));
                return filter(completions, args[4]);
            }
        }
        return List.of();
    }

    // New home method
    private void home(Player player) {
        requirePermission(player, Permissions.HOME); // Assuming a new permission "clans.home"
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        clan.getHomeLocation().ifPresentOrElse(homeLoc -> {
            if (homeLoc.getWorld() == null) {
                plugin.getMessages().send(player, "territory.world-not-found");
                return;
            }
            player.teleportAsync(homeLoc)
                    .thenRun(() -> plugin.getMessages().send(player, "territory.teleported"))
                    .exceptionally(throwable -> {
                        plugin.sendOperationError(player, throwable);
                        return null;
                    });
        }, () -> plugin.getMessages().send(player, "clan.home.not-set")); // New message key
    }

    private void confirmPendingChatInput(Player player) {
        plugin.getChatInputListener(player.getUniqueId()).ifPresent(callback -> callback.accept("подтвердить", false));
    }

    private void cancelPendingChatInput(Player player) {
        plugin.getChatInputListener(player.getUniqueId()).ifPresent(callback -> callback.accept(null, true));
    }

    private void openCreateGui(Player player) {
        requirePermission(player, Permissions.CREATE);
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "clan.already-in-clan");
            return;
        }
        new ClanCreateMenu(plugin, player).open();
    }

    private void openDiplomacyFor(Player player, String targetTag) {
        requirePermission(player, Permissions.DIPLOMACY);
        Optional<Clan> sourceClan = requireClan(player);
        if (sourceClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        if (targetTag == null || targetTag.isBlank()) {
            new ClanDiplomacySelectMenu(plugin, player, sourceClan.get()).open();
            return;
        }
        Clan target = plugin.getClanManager().getClanByTag(targetTag)
                .orElseThrow(() -> new IllegalStateException("war.not-found"));
        new ClanDiplomacyMenu(plugin).open(player, sourceClan.get(), target);
    }

    private void openLetters(Player player, String[] args) {
        requirePermission(player, Permissions.DIPLOMACY);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.letters");
            return;
        }
        Optional<Clan> sourceClan = requireClan(player);
        if (sourceClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan target = plugin.getClanManager().getClanByTag(args[1]).orElseThrow(() -> new IllegalStateException("war.not-found"));
        plugin.getGuiManager().openLetters(player, sourceClan.get(), target);
    }

    private void allianceAccept(Player player, String sourceClanTag) {
        requirePermission(player, Permissions.DIPLOMACY);
        Optional<Clan> acceptorClanOpt = requireClan(player);
        if (acceptorClanOpt.isEmpty()) { plugin.getMessages().send(player, "clan.not-in-clan"); return; }
        Clan acceptorClan = acceptorClanOpt.get();
        Clan sourceClan = plugin.getClanManager().getClanByTag(sourceClanTag)
                .orElseThrow(() -> new IllegalStateException("war.not-found"));
        if (!plugin.getClanManager().hasPendingAllianceFrom(sourceClan.id(), acceptorClan.id())) {
            plugin.getMessages().send(player, "diplomacy.no-pending-request", Map.of("tag", sourceClanTag, "color", sourceClan.tagColor()));
            return;
        }
        plugin.getClanManager().acceptAllianceAsync(acceptorClan, sourceClan, player.getUniqueId())
                .thenRun(() -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "diplomacy.alliance-accepted", Map.of("tag", sourceClanTag, "color", sourceClan.tagColor()));
                    plugin.getClanManager().getOnlineLeader(sourceClan).ifPresent(leader ->
                            plugin.getMessages().send(leader, "diplomacy.alliance-accepted-by",
                                    Map.of("tag", acceptorClan.tag(), "color", acceptorClan.tagColor())));
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void allianceDecline(Player player, String sourceClanTag) {
        requirePermission(player, Permissions.DIPLOMACY);
        Optional<Clan> declinerClanOpt = requireClan(player);
        if (declinerClanOpt.isEmpty()) { plugin.getMessages().send(player, "clan.not-in-clan"); return; }
        Clan declinerClan = declinerClanOpt.get();
        Clan sourceClan = plugin.getClanManager().getClanByTag(sourceClanTag)
                .orElseThrow(() -> new IllegalStateException("war.not-found"));
        plugin.getClanManager().declineAllianceAsync(declinerClan, sourceClan, player.getUniqueId())
                .thenRun(() -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "diplomacy.alliance-declined", Map.of("tag", sourceClanTag, "color", sourceClan.tagColor()));
                    plugin.getClanManager().getOnlineLeader(sourceClan).ifPresent(leader ->
                            plugin.getMessages().send(leader, "diplomacy.alliance-declined-by",
                                    Map.of("tag", declinerClan.tag(), "color", declinerClan.tagColor())));
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void declineInvite(Player player, String[] args) {
        if (args.length < 2) { plugin.getMessages().send(player, "clan.help.accept"); return; }
        String tag = args[1];
        plugin.getClanManager().getClanByTag(tag).ifPresentOrElse(clan -> {
            // Remove invite from memory
            plugin.getClanManager().getPlayerInvites(player.getUniqueId()).stream()
                    .filter(inv -> inv.clanId().equals(clan.id()))
                    .findFirst()
                    .ifPresent(inv -> plugin.getClanManager().removeInvite(player.getUniqueId(), clan.id()));
            plugin.getMessages().send(player, "clan.invite-declined", Map.of("tag", tag, "color", clan.tagColor()));
        }, () -> plugin.getMessages().send(player, "clan.not-found"));
    }

    private void applicationAccept(Player player, String applicantName) {
        requirePermission(player, Permissions.APPLICATIONS);
        Optional<Clan> clanOpt = requireClan(player);
        if (clanOpt.isEmpty()) { plugin.getMessages().send(player, "clan.not-in-clan"); return; }
        Clan clan = clanOpt.get();
        org.bukkit.OfflinePlayer applicant = Bukkit.getOfflinePlayer(applicantName);
        plugin.getClanManager().acceptApplicationAsync(clan, player.getUniqueId(), applicant.getUniqueId())
                .thenAccept(updatedClan -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "gui.applications.accepted", Map.of("player", applicantName));
                    Player online = Bukkit.getPlayer(applicant.getUniqueId());
                    if (online != null) plugin.getMessages().send(online, "clan.joined", Map.of("tag", updatedClan.tag(), "color", updatedClan.tagColor()));
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void applicationReject(Player player, String applicantName) {
        requirePermission(player, Permissions.APPLICATIONS);
        Optional<Clan> clanOpt = requireClan(player);
        if (clanOpt.isEmpty()) { plugin.getMessages().send(player, "clan.not-in-clan"); return; }
        Clan clan = clanOpt.get();
        org.bukkit.OfflinePlayer applicant = Bukkit.getOfflinePlayer(applicantName);
        plugin.getClanManager().rejectApplicationAsync(clan, player.getUniqueId(), applicant.getUniqueId())
                .thenRun(() -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "gui.applications.rejected", Map.of("player", applicantName));
                    Player online = Bukkit.getPlayer(applicant.getUniqueId());
                    if (online != null) plugin.getMessages().send(online, "clan.application-rejected",
                            Map.of("tag", clan.tag(), "color", clan.tagColor()));
                }))
                .exceptionally(t -> { plugin.runSync(() -> plugin.sendOperationError(player, t)); return null; });
    }

    private void disband(Player player) {
        requirePermission(player, Permissions.DISBAND);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        plugin.getClanManager().disbandClanAsync(clan, player.getUniqueId())
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(player, "clan.disbanded")))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void invite(Player player, String[] args) {
        requirePermission(player, Permissions.INVITE);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.invite");
            return;
        }
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessages().send(player, "general.player-not-found", Map.of("player", args[1]));
            return;
        }
        plugin.getClanManager().invitePlayerAsync(clan, player.getUniqueId(), target.getUniqueId())
                .thenAccept(invite -> plugin.runSync(() -> {
                    plugin.getMessages().send(player, "clan.invited", Map.of("player", target.getName()));
                    plugin.getMessages().sendClickableInvite(target, clan.tag(), clan.tagColor());
                }))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void accept(Player player, String[] args) {
        requirePermission(player, Permissions.ACCEPT);
        if (plugin.getClanManager().getPlayerClan(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "clan.already-in-clan");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.accept");
            return;
        }
        plugin.getClanManager().acceptInviteAsync(player.getUniqueId(), args[1])
                .thenAccept(clan -> plugin.runSync(() -> plugin.getMessages().send(player, "clan.joined", Map.of("tag", clan.tag(), "color", clan.tagColor()))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void leave(Player player) {
        requirePermission(player, Permissions.LEAVE);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        plugin.getClanManager().removeMemberAsync(clan, player.getUniqueId(), player.getUniqueId(), false)
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(player, "clan.left")))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void kick(Player player, String[] args) {
        requirePermission(player, Permissions.KICK);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.kick");
            return;
        }
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.getClanManager().removeMemberAsync(clan, player.getUniqueId(), target.getUniqueId(), true)
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(player, "clan.kicked", Map.of("player", displayName(target)))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void rank(Player player, String[] args, ClanRank rank) {
        requirePermission(player, Permissions.RANK);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.rank");
            return;
        }
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.getClanManager().setRankAsync(clan, player.getUniqueId(), target.getUniqueId(), rank)
                .thenAccept(updated -> plugin.runSync(() -> plugin.getMessages().send(player, "clan.rank-changed", Map.of("player", displayName(target), "rank", rank.displayName()))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void info(CommandSender sender, String[] args) {
        Optional<Clan> clan;
        if (args.length >= 2) {
            clan = plugin.getClanManager().getClanByTag(args[1]);
        } else if (sender instanceof Player player) {
            clan = plugin.getClanManager().getPlayerClan(player.getUniqueId());
            if (clan.isEmpty()) {
                plugin.getMessages().send(sender, "clan.info.no-name");
                return;
            }
        } else {
            plugin.getMessages().send(sender, "clan.info.no-name");
            return;
        }

        if (clan.isEmpty()) {
            plugin.getMessages().send(sender, "clan.not-found");
            return;
        }
        Clan value = clan.get();
        plugin.getMessages().send(sender, "clan.info.header", Map.of("tag", value.tag()));
        plugin.getMessages().send(sender, "clan.info.line", Map.of("key", "Название", "value", value.name()));
        plugin.getMessages().send(sender, "clan.info.line", Map.of("key", "Уровень", "value", String.valueOf(value.level())));
        plugin.getMessages().send(sender, "clan.info.line", Map.of("key", "Очки улучшений", "value", String.valueOf(value.upgradePoints())));
        plugin.getMessages().send(sender, "clan.info.line", Map.of("key", "Участники", "value", value.members().size() + "/" + plugin.getClanManager().maxMembers(value)));
        plugin.getMessages().send(sender, "clan.info.line", Map.of("key", "Тип", "value", value.isOpen() ? "Открытый" : "Закрытый"));
    }

    private void claim(Player player) {
        requirePermission(player, Permissions.CLAIM);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        
        // Prevent claiming if at war
        if (plugin.getWarManager().activeWars().stream().anyMatch(war -> war.involves(clan.id()))) {
            plugin.getMessages().send(player, "war.cannot-claim");
            return;
        }

        plugin.getClanManager().claimTerritoryAsync(clan, player.getLocation(), player, "TERRITORY") // Changed from player.getChunk() to player.getLocation()
                .thenAccept(success -> {
                    if (success) {
                        plugin.runSync(() -> plugin.getMessages().send(player, "territory.claimed"));
                    }
                })
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void unclaim(Player player) {
        requirePermission(player, Permissions.UNCLAIM);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        
        // Prevent unclaiming if at war
        if (plugin.getWarManager().activeWars().stream().anyMatch(war -> war.involves(clan.id()))) {
            plugin.getMessages().send(player, "war.cannot-unclaim");
            return;
        }

        plugin.getClanManager().unclaimTerritoryAsync(clan, TerritoryKey.fromLocation(player.getLocation()), player.getUniqueId())
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(player, "territory.unclaimed")))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void openMenu(Player player) {
        requirePermission(player, Permissions.MENU);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        plugin.getGuiManager().openMain(player, optionalClan.get());
    }

    private void openMembers(Player player) {
        requirePermission(player, Permissions.MENU);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        new ClanMembersMenu(plugin).open(player, optionalClan.get());
    }

    private void openTerritories(Player player) {
        requirePermission(player, Permissions.MENU);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        plugin.getGuiManager().openClanTerritoriesMenu(player, optionalClan.get());
    }

    private void openUpgrades(Player player) {
        requirePermission(player, Permissions.MENU);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        new ClanUpgradesMenu(plugin).open(player, optionalClan.get());
    }

    private void openChest(Player player) {
        requirePermission(player, Permissions.CHEST);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        plugin.getGuiManager().openChestHub(player, optionalClan.get());
    }

    private void openContracts(Player player) {
        requirePermission(player, Permissions.MENU);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        plugin.getGuiManager().openContracts(player, optionalClan.get());
    }

    private void openSpirit(Player player) {
        requirePermission(player, Permissions.MENU);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        plugin.getGuiManager().openSpiritMenu(player, optionalClan.get());
    }

    private void openClanList(Player player) {
        if (plugin.getClanManager().getAllClans().isEmpty()) {
            plugin.getMessages().send(player, "clan.list.empty");
            return;
        }
        new ClanListMenu(plugin, player).open();
    }

    private void openSettings(Player player) {
        requirePermission(player, Permissions.SETTINGS);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        new ClanSettingsMenu(plugin).open(player, optionalClan.get());
    }

    private void openApplications(Player player) {
        requirePermission(player, Permissions.APPLICATIONS);
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        new ClanApplicationsMenu(plugin).open(player, optionalClan.get());
    }

    private void war(Player player, String[] args) {
        requirePermission(player, Permissions.WAR);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.war");
            return;
        }
        Optional<Clan> optionalAttacker = requireClan(player);
        if (optionalAttacker.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan attacker = optionalAttacker.get();
        Clan defender = plugin.getClanManager().getClanByTag(args[1]).orElseThrow(() -> new IllegalStateException("war.not-found"));

        // Оспариваемая территория должна принадлежать защитнику - иначе объявление войны "за"
        // случайный чанк, где стоит атакующий, ни на что не влияет (компас/захват знамени не
        // находят подходящую территорию и молча ничего не выдают игрокам).
        //
        // Важно: находим территорию по фактическому bounding box'у (territory.boundingBox()),
        // а не по TerritoryKey.fromLocation(player.getLocation()). Территория может занимать
        // несколько чанков (integration.advanced-claims.claim-radius по умолчанию 35 => ~71x71
        // блоков), а ClanTerritory#key() всегда фиксирован на чанке МИНИМАЛЬНОГО угла территории.
        // Если бы мы просто брали чанк игрока, WarManager.resolveContestedTerritory (сверяющий
        // territory.key() с contestedTerritory) не нашёл бы территорию всякий раз, когда игрок
        // стоит не в том самом угловом чанке - хотя formально он внутри defender'а по getClanAt.
        boolean withinDefenderTerritory = plugin.getClanManager().getClanAt(player.getLocation())
                .map(owner -> owner.id().equals(defender.id()))
                .orElse(false);
        Optional<ClanTerritory> contestedTerritory = withinDefenderTerritory
                ? defender.territories().stream()
                        .filter(t -> t.boundingBox().contains(player.getLocation().toVector()))
                        .findFirst()
                : Optional.empty();
        if (contestedTerritory.isEmpty()) {
            plugin.sendOperationError(player, new IllegalStateException("war.must-be-in-enemy-territory"));
            return;
        }
        TerritoryKey territoryKey = contestedTerritory.get().key();

        // No success message here: WarManager#beginPendingPhase already notifies every online
        // member of both clans (including this player) once the war is actually registered.
        plugin.getWarManager().startWarAsync(attacker, defender, territoryKey)
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void siege(Player player, String[] args) {
        requirePermission(player, Permissions.WAR);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.siege");
            return;
        }
        Optional<Clan> optionalAttacker = requireClan(player);
        if (optionalAttacker.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan attacker = optionalAttacker.get();
        Clan defender = plugin.getClanManager().getClanByTag(args[1]).orElseThrow(() -> new IllegalStateException("war.not-found"));

        // Same "must be standing inside the defender's territory" requirement as /clan war -
        // see the comment there for why boundingBox() is used instead of TerritoryKey.fromLocation.
        boolean withinDefenderTerritory = plugin.getClanManager().getClanAt(player.getLocation())
                .map(owner -> owner.id().equals(defender.id()))
                .orElse(false);
        Optional<ClanTerritory> contestedTerritory = withinDefenderTerritory
                ? defender.territories().stream()
                        .filter(t -> t.boundingBox().contains(player.getLocation().toVector()))
                        .findFirst()
                : Optional.empty();
        if (contestedTerritory.isEmpty()) {
            plugin.sendOperationError(player, new IllegalStateException("war.must-be-in-enemy-territory"));
            return;
        }
        TerritoryKey territoryKey = contestedTerritory.get().key();

        // No success message here: SiegeManager#beginPendingPhase already notifies every online
        // member of both clans (including this player) once the siege is actually registered.
        plugin.getSiegeManager().startSiegeAsync(attacker, defender, territoryKey)
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void raid(Player player, String[] args) {
        requirePermission(player, Permissions.WAR);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.raid");
            return;
        }
        Optional<Clan> optionalAttacker = requireClan(player);
        if (optionalAttacker.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan attacker = optionalAttacker.get();

        if (args[1].equalsIgnoreCase("loot")) {
            raidLootMoney(player, attacker, args);
            return;
        }
        if (args[1].equalsIgnoreCase("items")) {
            raidLootItems(player, attacker);
            return;
        }

        Clan defender = plugin.getClanManager().getClanByTag(args[1]).orElseThrow(() -> new IllegalStateException("war.not-found"));
        // No success message here: RaidManager#beginPendingPhase already notifies every online
        // member of both clans (including this player) once the raid is actually registered.
        plugin.getRaidManager().startRaidAsync(attacker, defender)
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void raidLootMoney(Player player, Clan attacker, String[] args) {
        if (args.length < 3) {
            plugin.getMessages().send(player, "raid.loot.prompt");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            plugin.getMessages().send(player, "chest.invalid-amount");
            return;
        }
        ClanRaid raid = plugin.getRaidManager().findActiveRaidAsAttacker(attacker.id()).orElse(null);
        if (raid == null) {
            plugin.sendOperationError(player, new IllegalStateException("raid.not-active"));
            return;
        }
        plugin.getRaidManager().lootMoneyAsync(raid, player, amount)
                .thenAccept(looted -> plugin.getMessages().send(player, "chest.withdraw-success", Map.of("amount", String.valueOf(looted))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void raidLootItems(Player player, Clan attacker) {
        ClanRaid raid = plugin.getRaidManager().findActiveRaidAsAttacker(attacker.id()).orElse(null);
        if (raid == null) {
            plugin.sendOperationError(player, new IllegalStateException("raid.not-active"));
            return;
        }
        Optional<Clan> defender = plugin.getClanManager().getClanById(raid.defenderClanId());
        if (defender.isEmpty()) {
            plugin.sendOperationError(player, new IllegalStateException("clan.not-found"));
            return;
        }
        RaidLootMenu.open(plugin, raid, defender.get(), player);
    }

    private void peace(Player player, String[] args) {
        requirePermission(player, Permissions.WAR);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.war"); // Reusing for now
            return;
        }
        Optional<Clan> optionalSource = requireClan(player);
        if (optionalSource.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan source = optionalSource.get();
        Clan target = plugin.getClanManager().getClanByTag(args[1]).orElseThrow(() -> new IllegalStateException("war.not-found"));
        boolean atWar = plugin.getWarManager().areAtWar(source.id(), target.id());
        boolean inSiege = !atWar && plugin.getSiegeManager().areInSiege(source.id(), target.id());
        boolean inRaid = !atWar && !inSiege && plugin.getRaidManager().areInRaid(source.id(), target.id());
        if (!atWar && !inSiege && !inRaid) {
            plugin.sendOperationError(player, new IllegalStateException("war.not-at-war"));
            return;
        }
        plugin.getMessages().sendChatConfirmPrompt(player, "war.peace-confirm-prompt",
                Map.of("tag", target.tag(), "color", target.tagColor()),
                () -> {
                    var future = atWar ? plugin.getWarManager().peaceAsync(source, target)
                            : inSiege ? plugin.getSiegeManager().peaceAsync(source, target)
                            : plugin.getRaidManager().peaceAsync(source, target);
                    future.exceptionally(throwable -> {
                        plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                        return null;
                    });
                },
                () -> plugin.getMessages().send(player, "general.chat-input-cancelled"));
    }

    private void diplomacy(Player player, String[] args, DiplomacyRelation relation) {
        requirePermission(player, Permissions.DIPLOMACY);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.diplomacy");
            return;
        }
        Optional<Clan> optionalSource = requireClan(player);
        if (optionalSource.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan source = optionalSource.get();
        Clan target = plugin.getClanManager().getClanByTag(args[1]).orElseThrow(() -> new IllegalStateException("war.not-found"));
        plugin.getClanManager().setDiplomacyAsync(source, target, relation, player.getUniqueId())
                .thenAccept(clan -> plugin.runSync(() -> plugin.getMessages().send(player, "diplomacy.updated", Map.of("tag", target.tag(), "color", target.tagColor(), "relation", relation.name()))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> plugin.sendOperationError(player, throwable));
                    return null;
                });
    }

    private void ritual(Player player, String[] args) {
        requirePermission(player, Permissions.RITUAL);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.ritual");
            return;
        }
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        RitualType type;
        try {
            type = RitualType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("general.error", exception);
        }
        long remaining = plugin.getRitualManager().cooldownRemaining(clan.id());
        plugin.getRitualManager().startRitualAsync(clan, player.getUniqueId(), type)
                .thenAccept(ritual -> plugin.runSync(() -> plugin.getMessages().send(player, "ritual.started", Map.of("ritual", type.displayName()))))
                .exceptionally(throwable -> {
                    plugin.runSync(() -> {
                        if (remaining > 0L) {
                            plugin.getMessages().send(player, "ritual.cooldown", Map.of("time", formatDuration(remaining)));
                        } else {
                            plugin.sendOperationError(player, throwable);
                        }
                    });
                    return null;
                });
    }

    private void vote(Player player, String[] args) {
        requirePermission(player, Permissions.VOTE);
        if (args.length < 2) {
            plugin.getMessages().send(player, "clan.help.vote");
            return;
        }
        Optional<Clan> optionalClan = requireClan(player);
        if (optionalClan.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }
        Clan clan = optionalClan.get();
        OfflinePlayer candidate = Bukkit.getOfflinePlayer(args[1]);
        plugin.getSuccessionManager().castVote(clan, player.getUniqueId(), candidate.getUniqueId());
        plugin.getMessages().send(player, "succession.voted");
    }

    private void artifact(CommandSender sender, String[] args) {
        requirePermission(sender, Permissions.ADMIN);
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
    
    private void admin(CommandSender sender, String[] args) {
        requirePermission(sender, Permissions.ADMIN);
        
        if (args.length < 3) {
             plugin.getMessages().send(sender, "clan.help.admin");
             return;
        }
        
        String action = args[1].toLowerCase(Locale.ROOT);

        if (action.equals("createnpc")) {
            if (!(sender instanceof Player player)) {
                plugin.getMessages().send(sender, "general.players-only");
                return;
            }
            if (!args[2].equalsIgnoreCase("contracts")) {
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
            return;
        }

        if (action.equals("removenpc")) {
            int targetId;
            try {
                targetId = Integer.parseInt(args[2]);
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
            return;
        }

        if (action.equals("disband")) {
             String clanTag = args[2];
             Optional<Clan> clanOpt = plugin.getClanManager().getClanByTag(clanTag);
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
             return;
        }
        
        if (action.equals("war")) {
            if (args.length < 5) {
                plugin.getMessages().send(sender, "clan.help.admin-war");
                return;
            }
            String subAction = args[2].toLowerCase(Locale.ROOT);
            String clan1Tag = args[3];
            String clan2Tag = args[4];
            
            Optional<Clan> clan1Opt = plugin.getClanManager().getClanByTag(clan1Tag);
            Optional<Clan> clan2Opt = plugin.getClanManager().getClanByTag(clan2Tag);
            
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
            return;
        }
        
        if (action.equals("diplo")) {
            if (args.length < 5) {
                plugin.getMessages().send(sender, "clan.help.admin-diplo");
                return;
            }
            String clan1Tag = args[2];
            String clan2Tag = args[3];
            String relationStr = args[4].toUpperCase(Locale.ROOT);
            
            Optional<Clan> clan1Opt = plugin.getClanManager().getClanByTag(clan1Tag);
            Optional<Clan> clan2Opt = plugin.getClanManager().getClanByTag(clan2Tag);
            
            if (clan1Opt.isEmpty() || clan2Opt.isEmpty()) {
                plugin.getMessages().send(sender, "clan.not-found");
                return;
            }
            
            Clan clan1 = clan1Opt.get();
            Clan clan2 = clan2Opt.get();
            
            DiplomacyRelation relation;
            try {
                relation = DiplomacyRelation.valueOf(relationStr);
            } catch (IllegalArgumentException e) {
                plugin.getMessages().send(sender, "general.invalid-relation");
                return;
            }
            
            plugin.getClanManager().setDiplomacyAsync(clan1, clan2, relation, null) // null actorId
                .thenRun(() -> plugin.runSync(() -> plugin.getMessages().send(sender, "admin.diplo-updated", Map.of("clan1", clan1.tag(), "color1", clan1.tagColor(), "clan2", clan2.tag(), "color2", clan2.tagColor(), "relation", relation.name()))))
                .exceptionally(ex -> {
                    plugin.runSync(() -> plugin.sendOperationError(sender, ex));
                    return null;
                });
            return;
        }
        
        if (args.length < 5) {
             plugin.getMessages().send(sender, "clan.help.admin");
             return;
        }
        
        String subAction = args[2].toLowerCase(Locale.ROOT); // add, remove, set
        String tag = args[3];
        long amount;
        
        try {
            amount = Long.parseLong(args[4]);
        } catch (NumberFormatException e) {
            plugin.getMessages().send(sender, "general.invalid-number");
            return;
        }
        
        Optional<Clan> clanOpt = plugin.getClanManager().getClanByTag(tag);
        if (clanOpt.isEmpty()) {
            plugin.getMessages().send(sender, "clan.not-found");
            return;
        }
        
        Clan clan = clanOpt.get();
        
        if (action.equals("exp")) {
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
        } else if (action.equals("points")) {
            switch (subAction) {
                case "add" -> clan.addUpgradePoints((int) amount);
                case "remove" -> clan.removeUpgradePoints((int) amount);
                case "set" -> clan.setUpgradePoints((int) amount);
            }
            plugin.getClanManager().updateClanAsync(clan);
            plugin.getMessages().send(sender, "admin.points-updated", Map.of("tag", clan.tag(), "color", clan.tagColor()));
        } else {
             plugin.getMessages().send(sender, "clan.help.admin");
        }
    }

    private void reload(CommandSender sender) {
        requirePermission(sender, Permissions.ADMIN);
        plugin.reloadConfig();
        plugin.getMessages().reload();
        plugin.getMessages().send(sender, "general.reloaded");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalStateException("general.players-only");
    }

    private Optional<Clan> requireClan(Player player) {
        return plugin.getClanManager().getPlayerClan(player.getUniqueId());
    }

    private void requirePermission(CommandSender sender, String permission) {
        if (!Permissions.has(sender, permission)) {
            throw new IllegalStateException("general.no-permission");
        }
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        return hours + "h " + minutes + "m";
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}