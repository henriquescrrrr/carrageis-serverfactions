package pt.henrique.serverfactions.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker interface for all faction GUI holders.
 * Implementing classes must handle clicks and provide an Inventory.
 */
public interface FactionGui extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
}

