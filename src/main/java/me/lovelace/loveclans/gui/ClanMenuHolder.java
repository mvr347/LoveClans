package me.lovelace.loveclans.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class ClanMenuHolder implements InventoryHolder {
    private final ClanMenuType type;
    private final UUID clanId;
    private Inventory inventory;

    public ClanMenuHolder(ClanMenuType type, UUID clanId) {
        this.type = type;
        this.clanId = clanId;
    }

    public ClanMenuType type() {
        return type;
    }

    public UUID clanId() {
        return clanId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
