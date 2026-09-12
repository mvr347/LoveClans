package me.lovelace.loveclans.manager;

import me.lovelace.loveclans.LoveClansPlugin;
import me.lovelace.loveclans.model.artifact.AegisBannerArtifact;
import me.lovelace.loveclans.model.artifact.ArtifactType;
import me.lovelace.loveclans.model.artifact.ClanArtifact;
import me.lovelace.loveclans.model.artifact.WarHornArtifact;
import me.lovelace.loveclans.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Optional;

public final class ArtifactManager {
    private final LoveClansPlugin plugin;
    private final NamespacedKey artifactKey;

    public ArtifactManager(LoveClansPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "artifact");
    }

    public NamespacedKey artifactKey() {
        return artifactKey;
    }

    public ItemStack createArtifact(ArtifactType type) {
        ClanArtifact artifact = switch (type) {
            case WAR_HORN -> new WarHornArtifact();
            case AEGIS_BANNER -> new AegisBannerArtifact();
        };
        return ItemBuilder.of(artifact.material())
                .name(artifact.displayName())
                .lore(MiniMessage.miniMessage().deserialize("<gray>Используется в клановых войнах."))
                .mutate(meta -> meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, type.name()))
                .build();
    }

    public Optional<ArtifactType> readArtifact(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ArtifactType.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
