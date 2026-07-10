package pt.henrique.serverfactions.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static pt.henrique.serverfactions.gui.GuiItems.filler;

/**
 * Base GUI holder with per-slot click handlers.
 * All faction GUIs use this instead of implementing FactionGui directly.
 */
public class GuiHolder implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> clickHandlers = new HashMap<>();
    private final Map<Integer, BiConsumer<Player, ClickType>> richClickHandlers = new HashMap<>();

    /**
     * Create a GUI with the given raw slot count and title.
     *
     * @param size  raw inventory size (must be a multiple of 9: 9, 18, 27, 36, 45, 54)
     * @param title inventory title component
     */
    public GuiHolder(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    // ======================== ITEM MANAGEMENT ========================

    /**
     * Set an item in a slot with a simple click handler.
     * If slot is negative the call is silently ignored (unconfigured slot).
     */
    public void setItem(int slot, ItemStack item, Consumer<Player> handler) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (handler != null) {
            clickHandlers.put(slot, handler);
        }
    }

    /**
     * Set an item in a slot with a rich click handler (distinguishes left/right/shift clicks).
     * If slot is negative the call is silently ignored.
     */
    public void setItem(int slot, ItemStack item, BiConsumer<Player, ClickType> handler) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (handler != null) {
            richClickHandlers.put(slot, handler);
        }
    }

    /**
     * Set an item in a slot with no click handler (display only).
     * If slot is negative the call is silently ignored.
     */
    public void setItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
    }

    /**
     * Fill all slots with filler glass panes.
     */
    public void fill() {
        ItemStack fillerItem = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillerItem);
        }
    }

    /**
     * Fill a specific row with filler glass panes.
     */
    public void fillRow(int row) {
        ItemStack fillerItem = filler();
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, fillerItem);
        }
    }

    /**
     * Fill the last row with filler glass panes (navigation row).
     */
    public void fillLastRow() {
        fillRow((inventory.getSize() / 9) - 1);
    }

    // ======================== CLICK HANDLER ACCESS ========================

    /**
     * Get the simple click handler for a slot.
     */
    public Consumer<Player> getClickHandler(int slot) {
        return clickHandlers.get(slot);
    }

    /**
     * Get the rich click handler for a slot.
     */
    public BiConsumer<Player, ClickType> getRichClickHandler(int slot) {
        return richClickHandlers.get(slot);
    }

    // ======================== INVENTORY ========================

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Open this GUI for a player.
     */
    public void openFor(Player player) {
        player.openInventory(inventory);
    }
}

