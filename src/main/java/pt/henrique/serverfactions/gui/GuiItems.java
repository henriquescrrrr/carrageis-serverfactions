package pt.henrique.serverfactions.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Static helpers for building GUI ItemStacks using MiniMessage strings.
 */
public final class GuiItems {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GuiItems() {}

    public static ItemStack create(Material material, String miniName, String... miniLore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(mm(miniName).decoration(TextDecoration.ITALIC, false));
            if (miniLore.length > 0) {
                meta.lore(Arrays.stream(miniLore)
                        .map(l -> mm(l).decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    public static ItemStack create(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream()
                        .map(l -> l.decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    public static ItemStack createHead(org.bukkit.OfflinePlayer owner, String miniName, String... miniLore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(owner);
            meta.displayName(mm(miniName).decoration(TextDecoration.ITALIC, false));
            if (miniLore.length > 0) {
                meta.lore(Arrays.stream(miniLore)
                        .map(l -> mm(l).decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return head;
    }

    public static ItemStack withGlow(ItemStack item) {
        item.editMeta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return item;
    }

    /**
     * Create a grayed-out placeholder item for actions the player can't perform.
     */
    public static ItemStack locked(String miniName, String... miniLore) {
        return create(Material.GRAY_STAINED_GLASS_PANE, miniName, miniLore);
    }

    public static ItemStack filler() {
        return create(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static Component mm(String mini) {
        return MM.deserialize(mini);
    }
}
