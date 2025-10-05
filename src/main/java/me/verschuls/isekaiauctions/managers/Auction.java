package me.verschuls.isekaiauctions.managers;

import lombok.Getter;
import lombok.Setter;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.auctionsapi.cache.CategoryCache;
import me.verschuls.auctionsapi.cache.PlayerCache;
import me.verschuls.auctionsapi.events.*;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
public class Auction {
    private final UUID auctionUUID;
    private final double auctionPrice;
    private final AuctionType auctionType;
    private final AuctionBids auctionBids = new AuctionBids();
    private final Economy economy;

    private UUID auctionOwner;
    private String auctionOwnerDisplayName;
    private long auctionStartTime;

    private ItemStack auctionItem;
    private String auctionCategory;
    @Setter private long auctionEndTime;
    @Setter private boolean sellerClaimed = false;

    public Auction(Economy economy, Double price, AuctionType type, long time) {
        this.auctionUUID = UUID.randomUUID();
        this.economy = economy;
        this.auctionPrice = price;
        this.auctionType = type;
        this.auctionStartTime = ZonedDateTime.now().toInstant().getEpochSecond();
        this.auctionEndTime = this.auctionStartTime + time;
    }

    public Auction(UUID uuid, UUID owner, String displayName, ItemStack item, Double price, AuctionType type, String economy, long end, boolean sellerClaimed) {
        this.auctionUUID = uuid;
        this.auctionPrice = price;
        this.auctionItem = item;    
        this.auctionType = type;
        this.auctionEndTime = end;
        this.auctionOwnerDisplayName = displayName;
        this.auctionOwner = owner;
        this.sellerClaimed = sellerClaimed;
        this.auctionCategory = CategoryCache.getItemCategory(item);

        if (economy == null || economy.isEmpty())
            this.economy = IsekaiAuctions.getInstance().createEconomy;
        else
            this.economy = IsekaiAuctions.getInstance().economies.getOrDefault(economy, IsekaiAuctions.getInstance().createEconomy);
    }

    public boolean create(Player player, double totalFee) {
        ItemStack item = PlayerCache.getItem(player.getUniqueId());
        if (item == null || item.getType().equals(Material.AIR)) {
            Utils.sendMessage(player, "item_not_found");
            return false;
        }

        double balance = this.economy.getManager().getBalance(player);
        if (balance < totalFee) {
            Utils.playSound(player, "not_enough_money");
            Utils.sendMessage(player, "not_enough_money", new PlaceholderUtil()
                    .addPlaceholder("%required_money%", IsekaiAuctions.getInstance().numberFormat.format(totalFee-balance)));
            return false;
        }

        this.auctionCategory = CategoryCache.getItemCategory(item);
        if (this.auctionCategory.isEmpty()) {
            Utils.sendMessage(player, "unsellable_item");
            return false;
        }

        this.auctionItem = item;
        this.auctionOwnerDisplayName = !player.getDisplayName().isEmpty() ? player.getDisplayName() : player.getName();
        this.auctionOwner = player.getUniqueId();

        AuctionCreateEvent event = new AuctionCreateEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        PlayerPreferences playerPreferences = PlayerCache.getPreferences(this.auctionOwner);
        boolean status = playerPreferences.updateCreateItem(player, -1, false);
        if (!status)
            return false;

        this.economy.getManager().removeBalance(player, totalFee);

        playerPreferences.setCreateEconomy(IsekaiAuctions.getInstance().createEconomy);
        playerPreferences.setCreatePrice(IsekaiAuctions.getInstance().createPrice);
        playerPreferences.setCreateTime(IsekaiAuctions.getInstance().createTime);

        AuctionCache.addAuction(this);
        AuctionCache.addUpdatingAuction(this.auctionUUID);

        PlayerStats stats = PlayerCache.getStats(this.auctionOwner);
        stats.addCreatedAuction();
        stats.addTotalFees(totalFee);

        IsekaiAuctions.getInstance().dataHandler.writeToLog("[PLAYER CREATED AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") created " + this.auctionType + " auction for " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") auction!");
        IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") created auction (" + this.auctionUUID + ") for " + this.auctionItem.getType().name() + "!");

        IsekaiAuctions.getInstance().databaseManager.saveAuction(this);
        IsekaiAuctions.getInstance().databaseManager.saveStats(stats);
        return true;
    }

    public boolean isEnded() {
        if (this.sellerClaimed)
            return true;

        if (this.auctionType.equals(AuctionType.BIN))
            if (!this.auctionBids.getPlayerBids().isEmpty())
                return true;

        return ZonedDateTime.now().toInstant().getEpochSecond() > this.auctionEndTime;
    }

    public boolean cancel(Player player) {
        // Check if auction is already deleted
        if (AuctionCache.getAuction(this.auctionUUID) == null)
            return false;

        // Check if auction is ended already
        if (isEnded())
            return false;

        // Check if seller is claimed auction
        if (this.isSellerClaimed())
            return false;

        // Check if someone bid to the auction
        if (!this.auctionBids.getPlayerBids().isEmpty())
            return false;

        // Check if player has no empty slot
        if (!Utils.hasEmptySlot(player)) {
            Utils.sendMessage(player, "no_empty_slot");
            return false;
        }

        // Check if player is laggy
        if (Utils.isLaggy(player)) {
            Utils.sendMessage(player, "laggy");
            return false;
        }

        // Check if auction is updating in multi-server system
        if (AuctionCache.isAuctionUpdating(this.auctionUUID)) {
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        // Check if auction is updating in multi-server system
        if (IsekaiAuctions.getInstance().multiServerManager != null && IsekaiAuctions.getInstance().multiServerManager.isAuctionUpdating(this.auctionUUID)) {
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        // Custom event
        AuctionCancelEvent event = new AuctionCancelEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        // Update auction in multi server
        if (IsekaiAuctions.getInstance().multiServerManager != null && !IsekaiAuctions.getInstance().multiServerManager.deleteAuction(this.auctionUUID)) {
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        AuctionCache.addUpdatingAuction(this.auctionUUID);

        // Give Item
        this.sellerClaimed = true;
        player.getInventory().addItem(this.auctionItem.clone());

        // Log
        IsekaiAuctions.getInstance().dataHandler.writeToLog("[PLAYER CANCELLED AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") cancelled " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") auction!");
        IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") cancelled auction (" + this.auctionUUID + ") for " + this.auctionItem.getType().name() + "!");

        // Stats
        PlayerStats stats = PlayerCache.getStats(player.getUniqueId());
        stats.removeCreatedAuction();

        // Remove from Variables
        AuctionCache.addEndedAuction(this);
        AuctionCache.removeAuction(this.auctionUUID);

        // Database
        IsekaiAuctions.getInstance().databaseManager.deleteAuction(this.auctionUUID.toString());
        IsekaiAuctions.getInstance().databaseManager.saveStats(stats);
        return true;
    }

    public boolean placeBid(Player player, double price) {
        if (AuctionCache.getAuction(this.auctionUUID) == null) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") is not found in the auction list, it can't be sold again!");
            return false;
        }

        // Check if auction is already ended
        if (isEnded()) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") is already ended, it can't be sold again!");
            return false;
        }

        // Check if seller is claimed
        if (this.isSellerClaimed()) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") is already claimed by the seller, it can't be sold again!");
            return false;
        }

        // Check if auction type is not normal
        if (!this.auctionType.equals(AuctionType.NORMAL)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") type is not NORMAL, it can't be sold with bid method!");
            return false;
        }

        // Check if player's balance is not enough
        if (this.economy.getManager().getBalance(player) < price) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") does not enough have money for auction (" + this.auctionUUID + ")!");
            return false;
        }

        // Check if bid is low
        AuctionBids bids = this.getAuctionBids();
        double bidPrice = bids.getHighestBid() == null ? this.auctionPrice : bids.getHighestBid().getBidPrice();
        if (price <= bidPrice) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to bid low to auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "low_bid");
            return false;
        }

        // Check if auction is new
        long time = IsekaiAuctions.getInstance().configFile.getLong("settings.bid_cooldown", 0);
        if (time > 0 && this.auctionStartTime > 0) {
            long difference = ZonedDateTime.now().toInstant().getEpochSecond() - this.auctionStartTime;
            if (difference < time) {
                IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to bid new auction (" + this.auctionUUID + "), bid is still on cooldown!");
                Utils.sendMessage(player, "bid_cooldown", new PlaceholderUtil()
                        .addPlaceholder("%seconds_left%", String.valueOf(difference)));
                return false;
            }
        }

        // Check if player is laggy
        if (Utils.isLaggy(player)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is laggy to bid auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "laggy");
            return false;
        }

        // Check if auction is updating in multi-server system
        if (AuctionCache.isAuctionUpdating(this.auctionUUID)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to bid refreshing auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        // Check if auction is updating in multi-server system
        if (IsekaiAuctions.getInstance().multiServerManager != null && IsekaiAuctions.getInstance().multiServerManager.isAuctionUpdating(this.auctionUUID)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to bid refreshing auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        // Custom event
        PlayerBidEvent event = new PlayerBidEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        // Multi server system
        if (IsekaiAuctions.getInstance().multiServerManager != null && ! IsekaiAuctions.getInstance().multiServerManager.playerPlaceBidAuction(this.auctionUUID, player.getUniqueId(), price)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to bid refreshing auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is bid to auction (" + this.auctionUUID + ")!");
        AuctionCache.addUpdatingAuction(this.auctionUUID);

        // Add Time
        this.auctionEndTime += IsekaiAuctions.getInstance().configFile.getLong("settings.add_time_when_bid", 0);

        // Log
        IsekaiAuctions.getInstance().dataHandler.writeToLog("[PLAYER BID AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") bid " + price + " COINS for " + Bukkit.getOfflinePlayer(this.auctionOwner).getName() + "'s " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ")!");

        // Balance
        this.economy.getManager().removeBalance(player, price);

        // Highest Bid
        PlayerBid highestBid = this.auctionBids.getHighestBid();
        if (highestBid != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(highestBid.getBidOwner());
            this.economy.getManager().addBalance(offlinePlayer, highestBid.getBidPrice());
            highestBid.setCollected(true);
        }

        // Stats
        PlayerStats stats = PlayerCache.getStats(player.getUniqueId());
        stats.addTotalBids();
        stats.setHighestBid(price);
        stats.addSpentMoney(price);

        // Bid Auctions
        this.auctionBids.addPlayerBid(new PlayerBid(player, price));

        // Database
        IsekaiAuctions.getInstance().databaseManager.saveAuction(this);
        IsekaiAuctions.getInstance().databaseManager.saveStats(stats);

        return true;
    }

    public boolean purchase(Player player) {
        if (AuctionCache.getAuction(this.auctionUUID) == null) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") is not found in the auction list, it can't be sold again!");
            return false;
        }

        // Checking auction status
        if (isEnded()) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") is already ended, it can't be sold again!");
            return false;
        }

        // If it's claimed already, don't let to sell auction again
        if (this.isSellerClaimed()) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") is already claimed by the seller, it can't be sold again!");
            return false;
        }

        // Checking auction type anyway
        if (!this.auctionType.equals(AuctionType.BIN)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Auction (" + this.auctionUUID + ") type is not BIN, it can't be sold with purchase method!");
            return false;
        }

        // Balance check
        if (this.economy.getManager().getBalance(player) < this.auctionPrice) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") does not enough have money for auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "not_enough_money", new PlaceholderUtil().addPlaceholder("%required_money%", IsekaiAuctions.getInstance().numberFormat.format(this.auctionPrice-this.economy.getManager().getBalance(player))));
            return false;
        }

        // Empty slot check
        if (!Utils.hasEmptySlot(player)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") does not have enough slot to purchase auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "no_empty_slot");
            return false;
        }

        // Check if auction is new
        long time = IsekaiAuctions.getInstance().configFile.getLong("settings.purchase_cooldown", 0);
        if (time > 0 && this.auctionStartTime > 0) {
            long difference = ZonedDateTime.now().toInstant().getEpochSecond() - this.auctionStartTime;
            long remainingTime = time - difference;

            if (difference < time) {
                IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to purchase new auction (" + this.auctionUUID + "), purchase is still on cooldown!");
                Utils.sendMessage(player, "purchase_cooldown", new PlaceholderUtil()
                        .addPlaceholder("%seconds_left%", String.valueOf(remainingTime)));
                return false;
            }
        }

        // Lag check
        if (Utils.isLaggy(player)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is laggy to purchase auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "laggy");
            return false;
        }

        if (AuctionCache.isAuctionUpdating(this.auctionUUID)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to purchase refreshing auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        // Check if auction is updating in multi-server system
        if (IsekaiAuctions.getInstance().multiServerManager != null && IsekaiAuctions.getInstance().multiServerManager.isAuctionUpdating(this.auctionUUID)) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to purchase refreshing auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        // Cancellable event
        AuctionPurchaseEvent event = new AuctionPurchaseEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ")'s auction (" + this.auctionUUID + ") purchase event is cancelled!");
            return false;
        }

        if (IsekaiAuctions.getInstance().multiServerManager != null && !IsekaiAuctions.getInstance().multiServerManager.playerBoughtAuction(this.auctionUUID, player.getUniqueId())) {
            IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is trying to purchase refreshing auction (" + this.auctionUUID + ")!");
            Utils.sendMessage(player, "refreshing");
            return false;
        }

        IsekaiAuctions.getInstance().dataHandler.debug("Player (" + player.getUniqueId() + ") is purchased auction (" + this.auctionUUID + ")!");
        AuctionCache.addUpdatingAuction(this.auctionUUID);

        // Player
        this.economy.getManager().removeBalance(player, this.auctionPrice);
        player.getInventory().addItem(this.auctionItem.clone());

        // Log
        IsekaiAuctions.getInstance().dataHandler.writeToLog("[PLAYER BOUGHT AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") bought " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") for " + this.auctionPrice + " COINS from " + Bukkit.getOfflinePlayer(this.auctionOwner).getName() + "!");

        // Bid auctions
        PlayerBid playerBid = new PlayerBid(player, this.auctionPrice, true);
        this.auctionBids.addPlayerBid(playerBid);

        // Stats
        PlayerStats stats = PlayerCache.getStats(player.getUniqueId());
        stats.addSpentMoney(this.auctionPrice);
        stats.addWonAuction();

       // Database
        IsekaiAuctions.getInstance().databaseManager.saveAuction(this);
        IsekaiAuctions.getInstance().databaseManager.saveStats(stats);

        return true;
    }

    public String sellerCollect(Player player, boolean isAll) {
        if (AuctionCache.getAuction(this.auctionUUID) == null)
            return "";

        if (!isEnded())
            return "";

        if (this.isSellerClaimed())
            return "";

        PlayerBid highestBid = this.auctionBids.getHighestBid();
        if (highestBid == null && !Utils.hasEmptySlot(player)) {
            if (!isAll)
                Utils.sendMessage(player, "no_empty_slot");
            return "";
        }

        if (Utils.isLaggy(player)) {
            if (!isAll)
                Utils.sendMessage(player, "laggy");
            return "";
        }

        if (AuctionCache.isAuctionUpdating(this.auctionUUID)) {
            if (!isAll)
                Utils.sendMessage(player, "refreshing");
            return "";
        }

        // Check if auction is updating in multi-server system
        if (IsekaiAuctions.getInstance().multiServerManager != null && IsekaiAuctions.getInstance().multiServerManager.isAuctionUpdating(this.auctionUUID)) {
            if (!isAll)
                Utils.sendMessage(player, "refreshing");
            return "";
        }

        if (!isAll) {
            AuctionCollectEvent event = new AuctionCollectEvent(player, this, highestBid == null);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled())
                return "";
        }

        boolean isAllClaimed = this.getAuctionBids().isAllCollected();
        if (IsekaiAuctions.getInstance().multiServerManager != null) {
            boolean status;
            if (isAllClaimed)
                status = IsekaiAuctions.getInstance().multiServerManager.deleteAuction(this.auctionUUID);
            else
                status = IsekaiAuctions.getInstance().multiServerManager.sellerCollectedAuction(this.auctionUUID);

            if (!status) {
                if (!isAll)
                    Utils.sendMessage(player, "refreshing");
                return "";
            }
        }

        AuctionCache.addUpdatingAuction(this.auctionUUID);

        this.sellerClaimed = true;
        PlayerStats stats = PlayerCache.getStats(player.getUniqueId());
        String type;

        if (highestBid == null) {
            IsekaiAuctions.getInstance().dataHandler.writeToLog("[SELLER COLLECTED AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") collected ITEM from " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") auction!");
            player.getInventory().addItem(this.auctionItem.clone());
            type = "item";

            stats.addExpiredAuction();
        } else {
            IsekaiAuctions.getInstance().dataHandler.writeToLog("[SELLER COLLECTED AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") collected " + highestBid.getBidPrice() + " COINS from " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") auction!");
            this.economy.getManager().addBalance(player, highestBid.getBidPrice());
            type = "money";

            stats.addSoldAuction();
            stats.addEarnedMoney(highestBid.getBidPrice());
        }

        IsekaiAuctions.getInstance().databaseManager.saveStats(stats);

        // Database
        if (isAllClaimed) {
            AuctionCache.addEndedAuction(this);
            AuctionCache.removeAuction(this.auctionUUID);
            IsekaiAuctions.getInstance().databaseManager.deleteAuction(this.auctionUUID.toString());
        } else
            IsekaiAuctions.getInstance().databaseManager.saveAuction(this);

        return type;
    }

    public String buyerCollect(Player player, boolean isAll) {
        if (AuctionCache.getAuction(this.auctionUUID) == null)
            return "";

        if (!isEnded())
            return "";

        PlayerBid playerBid = this.auctionBids.getPlayerBid(player.getUniqueId());
        if (playerBid == null)
            return "";

        PlayerBid highestBid = this.auctionBids.getHighestBid();
        if (highestBid == null)
            return "";

        if (playerBid == highestBid && !Utils.hasEmptySlot(player)) {
            if (!isAll)
                Utils.sendMessage(player, "no_empty_slot");
            return "";
        }

        if (Utils.isLaggy(player)) {
            if (!isAll)
                Utils.sendMessage(player, "laggy");
            return "";
        }

        if (AuctionCache.isAuctionUpdating(this.auctionUUID)) {
            if (!isAll)
                Utils.sendMessage(player, "refreshing");
            return "";
        }

        // Check if auction is updating in multi-server system
        if (IsekaiAuctions.getInstance().multiServerManager != null && IsekaiAuctions.getInstance().multiServerManager.isAuctionUpdating(this.auctionUUID)) {
            if (!isAll)
                Utils.sendMessage(player, "refreshing");
            return "";
        }

        if (!isAll) {
            AuctionCollectEvent event = new AuctionCollectEvent(player, this, false);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled())
                return "";
        }

        boolean isClaimed = playerBid.isCollected();
        playerBid.setCollected(true);

        boolean isAllClaimed = this.sellerClaimed && this.getAuctionBids().isAllCollected();
        if (IsekaiAuctions.getInstance().multiServerManager != null) {
            boolean status;

            if (isAllClaimed)
                status = IsekaiAuctions.getInstance().multiServerManager.deleteAuction(this.auctionUUID);
            else
                status = IsekaiAuctions.getInstance().multiServerManager.buyerCollectedAuction(this.auctionUUID, player.getUniqueId());

            if (!status) {
                playerBid.setCollected(false);

                if (!isAll)
                    Utils.sendMessage(player, "refreshing");
                return "";
            }
        }

        AuctionCache.addUpdatingAuction(this.auctionUUID);

        String type;
        PlayerStats stats = PlayerCache.getStats(player.getUniqueId());
        if (playerBid == highestBid) {
            type = "item";
            IsekaiAuctions.getInstance().dataHandler.writeToLog("[BUYER COLLECTED AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") collected ITEM from " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") auction!");

            if (!isClaimed)
                player.getInventory().addItem(this.auctionItem.clone());

            stats.addWonAuction();
        } else {
            type = "money";
            IsekaiAuctions.getInstance().dataHandler.writeToLog("[BUYER COLLECTED AUCTION] " + player.getName() + " (" + player.getUniqueId() + ") collected from " + Utils.getDisplayName(this.auctionItem) + " (" + this.auctionUUID + ") auction!");

            if (!isClaimed)
                this.economy.getManager().addBalance(player, playerBid.getBidPrice());

            stats.addLostAuction();
        }

        IsekaiAuctions.getInstance().databaseManager.saveStats(stats);

        // Database
        if (isAllClaimed) {
            AuctionCache.addEndedAuction(this);
            AuctionCache.removeAuction(this.auctionUUID);
            IsekaiAuctions.getInstance().databaseManager.deleteAuction(this.auctionUUID.toString());
        } else
            IsekaiAuctions.getInstance().databaseManager.saveAuction(this);

        return type;
    }
}
