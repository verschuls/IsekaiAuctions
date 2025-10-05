package me.verschuls.isekaiauctions.menus;

import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.inventoryapi.HInventory;
import me.verschuls.isekaiauctions.inventoryapi.item.ClickableItem;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.AuctionType;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.List;

public class ShulkerViewMenu {
    private final Player player;
    private final ConfigurationSection section;
    private final Auction auction;
    private HInventory gui;

    public ShulkerViewMenu(Player player, Auction auction) {
        this.player = player;
        this.section = IsekaiAuctions.getInstance().menusFile.getConfigurationSection("shulker_view_menu");
        this.auction = auction;
    }

    public void open(String back) {
        this.gui = IsekaiAuctions.getInstance().menuHandler.createInventory(this.player, this.section, "shulker_view", null);

        int goBackSlot = this.section.getInt("back");
        ItemStack goBackItem = IsekaiAuctions.getInstance().normalItems.get("go_back");
        if (goBackSlot > 0 && goBackItem != null)
            gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) -> {
                if (auction.getAuctionType().equals(AuctionType.BIN))
                    new BinViewMenu(this.player, auction).open(back);
                else
                    new NormalViewMenu(this.player, auction).open(back);
            }));

        loadItems();
        this.gui.open(this.player);
    }

    private void loadItems() {
        BlockStateMeta bsm = (BlockStateMeta) auction.getAuctionItem().getItemMeta();
        if (bsm == null)
            return;

        ShulkerBox shulkerBox = (ShulkerBox) bsm.getBlockState();

        List<Integer> slots = this.section.getIntegerList("slots");
        ItemStack[] items = shulkerBox.getInventory().getContents();

        int i = 0;
        for (ItemStack item : items) {
            this.gui.setItem(slots.get(i), ClickableItem.empty(item));

            i++;
        }
    }
}
