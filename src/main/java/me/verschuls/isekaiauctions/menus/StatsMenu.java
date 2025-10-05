package me.verschuls.isekaiauctions.menus;

import me.verschuls.auctionsapi.AuctionHook;
import me.verschuls.auctionsapi.cache.PlayerCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.inventoryapi.HInventory;
import me.verschuls.isekaiauctions.inventoryapi.item.ClickableItem;
import me.verschuls.isekaiauctions.managers.PlayerStats;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class StatsMenu {
    private final Player player;
    private final ConfigurationSection section;
    private HInventory gui;
    private final PlayerStats playerStats;

    public StatsMenu(Player player) {
        this.player = player;
        this.playerStats = PlayerCache.getStats().get(player.getUniqueId());
        this.section = IsekaiAuctions.getInstance().menusFile.getConfigurationSection("stats_menu");
    }

    public void open() {
        this.gui = IsekaiAuctions.getInstance().menuHandler.createInventory(this.player, this.section, "stats", null);

        int goBackSlot = this.section.getInt("back");
        ItemStack goBackItem = IsekaiAuctions.getInstance().normalItems.get("go_back");
        if (goBackSlot > 0 && goBackItem != null)
            gui.setItem(goBackSlot, ClickableItem.of(goBackItem, (event) ->
                    AuctionHook.openMainMenu(this.player)));

        loadBuyerStatsItem();
        loadSellerStatsItem();

        this.gui.open(this.player);
    }

    private void loadBuyerStatsItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("buyer_stats");
        if (itemSection == null)
            return;

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%player_name%", this.player.getName())
                .addPlaceholder("%player_displayname%", this.player.getDisplayName());

        if (this.playerStats != null)
            placeholderUtil
                    .addPlaceholder("%won_auctions_amount%", String.valueOf(this.playerStats.getWonAuctions()))
                    .addPlaceholder("%lost_auctions_amount%", String.valueOf(this.playerStats.getLostAuctions()))
                    .addPlaceholder("%total_bids_amount%", String.valueOf(this.playerStats.getTotalBids()))
                    .addPlaceholder("%highest_bid%", IsekaiAuctions.getInstance().numberFormat.format(this.playerStats.getHighestBid()))
                    .addPlaceholder("%spent_money_amount%", IsekaiAuctions.getInstance().numberFormat.format(this.playerStats.getSpentMoney()));
        else
            placeholderUtil
                    .addPlaceholder("%won_auctions_amount%", "0")
                    .addPlaceholder("%lost_auctions_amount%", "0")
                    .addPlaceholder("%total_bids_amount%", "0")
                    .addPlaceholder("%highest_bid%", "0")
                    .addPlaceholder("%spent_money_amount%", "0");

        ItemStack item = Utils.createItemFromSection(itemSection, placeholderUtil);
        if (item == null)
            return;

        int slot = itemSection.getInt("slot");
        this.gui.setItem(slot, ClickableItem.empty(item));
    }

    private void loadSellerStatsItem() {
        ConfigurationSection itemSection = this.section.getConfigurationSection("seller_stats");
        if (itemSection == null)
            return;

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%player_name%", this.player.getName())
                .addPlaceholder("%player_displayname%", this.player.getDisplayName());

        if (this.playerStats != null)
            placeholderUtil
                    .addPlaceholder("%created_auctions_amount%", String.valueOf(this.playerStats.getCreatedAuctions()))
                    .addPlaceholder("%completed_auctions_with_bids%", String.valueOf(this.playerStats.getSoldAuctions()))
                    .addPlaceholder("%completed_auctions_without_bids%", String.valueOf(this.playerStats.getExpiredAuctions()))
                    .addPlaceholder("%earned_money_amount%", IsekaiAuctions.getInstance().numberFormat.format(this.playerStats.getEarnedMoney()))
                    .addPlaceholder("%spent_money_on_fees%", IsekaiAuctions.getInstance().numberFormat.format(this.playerStats.getTotalFees()));
        else
            placeholderUtil
                    .addPlaceholder("%created_auctions_amount%", "0")
                    .addPlaceholder("%completed_auctions_with_bids%", "0")
                    .addPlaceholder("%completed_auctions_without_bids%", "0")
                    .addPlaceholder("%spent_money_on_fees%", "0")
                    .addPlaceholder("%earned_money_amount%", "0");

        ItemStack item = Utils.createItemFromSection(itemSection, placeholderUtil);
        if (item == null)
            return;

        int slot = itemSection.getInt("slot");
        this.gui.setItem(slot, ClickableItem.empty(item));
    }
}
