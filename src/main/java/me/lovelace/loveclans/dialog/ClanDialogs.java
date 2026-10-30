package me.lovelace.loveclans.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.Clan;
import me.lovelace.loveclans.util.ClanInputValidator;
import me.lovelace.loveclans.util.ClanInputValidator.Field;
import me.lovelace.loveclans.util.ClanNameRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Native Paper dialogs for typing a clan name and tag (instead of a chat prompt). Every window is
 * bound to the player it was opened for, works once, expires, and does its work on the main thread.
 * A rejected value reopens the window with what the player typed and the reason above the fields, so
 * nothing is lost. Each method returns {@code false} when a dialog cannot be shown (switched off in
 * config, or the client cannot display it); the caller then falls back to the chat prompt.
 */
public final class ClanDialogs {
    private static final Duration LIFETIME = Duration.ofMinutes(3);
    private static final int WIDTH = 300;

    private final LoveClansPlugin plugin;

    public ClanDialogs(LoveClansPlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code clans.input.mode}: "dialog" (default) or "chat" for the old chat prompts. */
    public boolean enabled() {
        return "dialog".equalsIgnoreCase(plugin.getConfig().getString("clans.input.mode", "dialog"));
    }

    /** Creation: name and tag in one window. */
    public boolean askNameAndTag(Player player, String name, String tag, BiConsumer<String, String> done, Runnable cancelled) {
        var typed = new LinkedHashMap<String, String>();
        typed.put("name", name);
        typed.put("tag", tag);
        return render(player, "dialog.create.title", Map.of(), List.of(
                        new Input("name", "dialog.create.name-label", plugin.nameLimits().max()),
                        new Input("tag", "dialog.create.tag-label", plugin.tagLimits().max())),
                typed, "dialog.create.confirm", "", null,
                this::firstProblem,
                values -> done.accept(ClanNameRules.clean(values.get("name")), ClanNameRules.clean(values.get("tag"))),
                cancelled);
    }

    /** Rename: one field, prefilled with the current name. */
    public boolean askName(Player player, Clan clan, Consumer<String> done, Runnable cancelled) {
        var typed = new LinkedHashMap<String, String>();
        typed.put("name", clan.name());
        return render(player, "dialog.rename.title", Map.of("current", clan.name()), List.of(
                        new Input("name", "dialog.rename.label", plugin.nameLimits().max())),
                typed, "dialog.rename.confirm", "name", null,
                values -> plugin.inputValidator().firstProblem(Field.NAME, ClanNameRules.clean(values.get("name")), t -> false),
                values -> done.accept(ClanNameRules.clean(values.get("name"))),
                cancelled);
    }

    /** Tag change: one field, prefilled with the current tag; the clan's own tag does not count as taken. */
    public boolean askTag(Player player, Clan clan, Consumer<String> done, Runnable cancelled) {
        var typed = new LinkedHashMap<String, String>();
        typed.put("tag", clan.tag());
        return render(player, "dialog.tag.title", Map.of("current", clan.tag()), List.of(
                        new Input("tag", "dialog.tag.label", plugin.tagLimits().max())),
                typed, "dialog.tag.confirm", "tag", null,
                values -> plugin.inputValidator().firstProblem(Field.TAG, ClanNameRules.clean(values.get("tag")),
                        tag -> plugin.getClanManager().getClanByTag(tag).filter(other -> !other.id().equals(clan.id())).isPresent()),
                values -> done.accept(ClanNameRules.clean(values.get("tag"))),
                cancelled);
    }

    /** Chat fallback: tells the player why a typed value was refused, in the same words as the dialog. */
    public void sendProblem(Player player, String problemKey) {
        player.sendMessage(plugin.getMessages().component("dialog.error." + problemKey, limitsFor(problemKey), player));
    }

    private Optional<String> firstProblem(Map<String, String> values) {
        var validator = plugin.inputValidator();
        return validator.firstProblem(Field.NAME, ClanNameRules.clean(values.get("name")), t -> false)
                .or(() -> validator.firstProblem(Field.TAG, ClanNameRules.clean(values.get("tag")),
                        tag -> plugin.getClanManager().getClanByTag(tag).isPresent()));
    }

    private record Input(String id, String labelKey, int maxLength) {}

    private boolean render(Player player, String titleKey, Map<String, String> titlePlaceholders, List<Input> inputs,
                           Map<String, String> typed, String confirmKey, String rulesOnly, String errorKey,
                           Function<Map<String, String>, Optional<String>> validate,
                           Consumer<Map<String, String>> accept, Runnable cancelled) {
        if (!enabled()) return false;
        var messages = plugin.getMessages();
        var bodies = new ArrayList<DialogBody>();
        if (errorKey != null) {
            bodies.add(DialogBody.plainMessage(messages.component("dialog.error." + errorKey, limitsFor(errorKey), player)));
        }
        if (!"tag".equals(rulesOnly)) {
            bodies.add(DialogBody.plainMessage(messages.component("dialog.rules.name", limitsFor("name"), player)));
        }
        if (!"name".equals(rulesOnly)) {
            bodies.add(DialogBody.plainMessage(messages.component("dialog.rules.tag", limitsFor("tag"), player)));
        }

        var dialogInputs = new ArrayList<DialogInput>();
        for (Input input : inputs) {
            dialogInputs.add(DialogInput.text(input.id(), WIDTH, messages.component(input.labelKey(), titlePlaceholders, player), true,
                    typed.getOrDefault(input.id(), ""), input.maxLength() + 8, null));
        }

        var owner = player.getUniqueId();
        var used = new AtomicBoolean();
        var options = ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build();

        ActionButton confirm = ActionButton.create(messages.component(confirmKey, player), null, 150,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(owner)) return;
                    var values = new HashMap<String, String>();
                    for (Input input : inputs) {
                        values.put(input.id(), Objects.requireNonNullElse(response.getText(input.id()), ""));
                    }
                    // Callbacks arrive off the main thread; nothing touches clan state before the hop.
                    plugin.runSync(() -> {
                        if (!used.compareAndSet(false, true) || !actor.isOnline()) return;
                        Optional<String> problem = validate.apply(values);
                        if (problem.isPresent()) {
                            plugin.getSoundService().playGui(actor, me.lovelace.loveclans.service.GuiSounds.Kind.DENIED);
                            if (!render(actor, titleKey, titlePlaceholders, inputs, values, confirmKey, rulesOnly,
                                    problem.get(), validate, accept, cancelled)) {
                                cancelled.run();
                            }
                            return;
                        }
                        accept.accept(values);
                    });
                }, options));
        ActionButton cancel = ActionButton.create(messages.component("dialog.cancel", player), null, 150,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(owner)) return;
                    plugin.runSync(() -> {
                        if (used.compareAndSet(false, true)) cancelled.run();
                    });
                }, options));

        try {
            var dialog = io.papermc.paper.dialog.Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(messages.component(titleKey, titlePlaceholders, player))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .body(bodies)
                            .inputs(dialogInputs)
                            .build())
                    .type(DialogType.multiAction(List.of(confirm, cancel), null, 2)));
            player.showDialog(dialog);
            return true;
        } catch (RuntimeException | LinkageError unsupported) {
            plugin.getLogger().fine("Диалог не показан, используется чат: " + unsupported);
            return false;
        }
    }

    private Map<String, String> limitsFor(String key) {
        ClanNameRules.Limits limits = key.contains("tag") ? plugin.tagLimits() : plugin.nameLimits();
        return Map.of("min", String.valueOf(limits.min()), "max", String.valueOf(limits.max()));
    }
}
