package me.verschuls.auctionsapi;

import com.google.common.collect.ImmutableMultimap;
import me.verschuls.auctionsapi.cache.CategoryCache;
import me.verschuls.auctionsapi.cache.PlayerCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.managers.*;
import me.verschuls.isekaiauctions.menus.AuctionsMenu;
import me.verschuls.isekaiauctions.menus.MainMenu;
import me.verschuls.isekaiauctions.others.Utils;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuctionHook {
    private static final double MAX_PRICE = 1_000_000_000_000_000.0;
    private static final double MAX_BID = 1_000_000_000_000_000.0;
    private static final int MAX_DURATION = 31536000;
    private static final int MAX_AUCTION = 100;

    public static boolean isAuctionTypeDisabled(String type) {
        if (type == null || type.isEmpty())
            return false;
        if (!type.equalsIgnoreCase("bin") && !type.equalsIgnoreCase("normal"))
            return false;

        boolean setting = IsekaiAuctions.getInstance().configFile.getBoolean(type.toLowerCase(Locale.ENGLISH) + "_auction.enabled", true);
        return !setting;
    }

    public static double calculatePriceFeePercent(double price, String type) {
        ConfigurationSection feeSection = IsekaiAuctions.getInstance().configFile.getConfigurationSection(type + "_auction.price_fees");
        if (feeSection == null)
            return 0.0;

        boolean status = IsekaiAuctions.getInstance().configFile.getBoolean(type + "_auction.fee");
        if (!status)
            return 0.0;

        double currentPrice = 0.0;
        double currentFee = 0.0;
        for (String key : feeSection.getKeys(false)) {
            double number;
            try {
                number = Double.parseDouble(key);
            } catch (Exception e) {
                continue;
            }

            if (price < number)
                continue;
            if (currentPrice > 0.0 && currentPrice > number)
                continue;

            currentPrice = number;
            currentFee = feeSection.getDouble(key);
        }

        return Math.max(currentFee, 0.0);
    }

    public static void openMainMenu(Player player) {
        boolean status = IsekaiAuctions.getInstance().configFile.getBoolean("settings.enable_main_menu", true);
        if (!status) {
            PlayerPreferences playerPreferences = PlayerCache.getPreferences(player.getUniqueId());

            new AuctionsMenu(player).open(playerPreferences.getCategory().getName(), playerPreferences.getPage());
            return;
        }

        new MainMenu(player).open();
    }

    public static double calculateDurationFee(long time) {
        ConfigurationSection durationSection = IsekaiAuctions.getInstance().configFile.getConfigurationSection("settings.duration_fee");
        if (durationSection == null)
            return 0.0;

        boolean status = durationSection.getBoolean("enabled", true);
        if (!status)
            return 0.0;

        String formula = durationSection.getString("formula", "%hours% * 50");
        Expression e = new ExpressionBuilder(formula
                .replace("%weeks%", String.valueOf(time/604800))
                .replace("%days%", String.valueOf(time/86400))
                .replace("%seconds%", String.valueOf(time))
                .replace("%minutes%", String.valueOf(time/60))
                .replace("%hours%", String.valueOf(time/3600)))
                .build();
        double formulaPrice = e.evaluate();

        return Math.max(formulaPrice, durationSection.getDouble("minimum_fee", 0.0));
    }

    public static double getPriceLimit(Player player, String type) {
        double max = type.equals("price_limit") ? MAX_PRICE : MAX_BID;

        ConfigurationSection section = IsekaiAuctions.getInstance().configFile.getConfigurationSection("player_limits." + type);
        if (section == null)
            return max;

        double current = section.getDouble("default");
        List<String> permissions = section.getStringList("permissions");
        if (permissions.isEmpty())
            return Math.min(current, max);

        for (String entry : permissions) {
            String[] args = entry.split("[:]", 2);
            if (args.length < 2)
                continue;

            String permission = args[0];
            double amount = Integer.parseInt(args[1]);

            if (!player.hasPermission(permission))
                continue;

            if (amount > current)
                current = amount;
        }

        if (player.isOp())
            return Math.max(max, current);

        return current;
    }

    public static Category getCategory(String name) {
        return CategoryCache.getCategories().get(name);
    }

    public static int getLimit(Player player, String type) {
        int max = type.equals("duration_limit") ? MAX_DURATION : MAX_AUCTION;

        ConfigurationSection section = IsekaiAuctions.getInstance().configFile.getConfigurationSection("player_limits." + type);
        if (section == null)
            return max;

        int current = section.getInt("default");
        List<String> permissions = section.getStringList("permissions");
        if (permissions.isEmpty())
            return Math.min(current, max);

        for (String entry : permissions) {
            String[] args = entry.split(":", 2);
            if (args.length < 2)
                continue;

            String permission = args[0];
            int amount = Integer.parseInt(args[1]);

            if (!player.hasPermission(permission))
                continue;

            if (amount > current)
                current = amount;
        }

        if (player.isOp())
            return Math.max(max, current);

        return current;
    }

    public static String isSellable(Player player, ItemStack item) {
        if (!Utils.hasPermission(player, "item", item.getType().name()))
            return "no_permission_for_item";

        if (CategoryCache.getItemCategory(item).isEmpty())
            return "unsellable_item";

        if (IsekaiAuctions.getInstance().blacklistHandler.isBlacklisted(item))
            return "blacklisted_item";

        return "";
    }

    public static ItemStack getUpdatedAuctionItem(Auction auction) {
        ItemStack itemStack = auction.getAuctionItem();
        if (itemStack == null)
            return null;
        itemStack = itemStack.clone();

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null)
            return null;

        PlayerBid highestBid = auction.getAuctionBids().getHighestBid();
        String type;
        if (auction.isEnded()) {
            if (highestBid == null)
                type = "expired";
            else
                type = "sold";
        } else {
            if (auction.getAuctionType().equals(AuctionType.BIN))
                type = "not_sold";
            else {
                if (highestBid == null)
                    type = "no_bids";
                else
                    type = "bids";
            }
        }

        ConfigurationSection itemSection = IsekaiAuctions.getInstance().messagesFile.getConfigurationSection("lores.auction_items." + auction.getAuctionType().name().toLowerCase(Locale.ENGLISH) + "." + type);
        if (itemSection == null)
            return itemStack;

        String displayName = itemSection.getString("name");
        if (displayName != null && meta.hasDisplayName())
            meta.setDisplayName(Utils.colorize(displayName
                    .replace("%item_name%", meta.getDisplayName())));

        List<String> lore = itemSection.getStringList("lore");
        List<String> newLore = new ArrayList<>();
        if (!lore.isEmpty())
            for (String line : lore) {
                if (line.contains("%item_lore%")) {
                    List<String> itemLore = meta.getLore();
                    if (itemLore != null && !itemLore.isEmpty())
                        for (String itemLine : itemLore)
                            newLore.add(Utils.colorize(itemLine));

                    continue;
                }

                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getAuctionOwner());
                OfflinePlayer buyer = highestBid != null ? Bukkit.getOfflinePlayer(highestBid.getBidOwner()) : null;

                newLore.add(Utils.colorize(line
                        .replace("%bid_amount%", String.valueOf(auction.getAuctionBids().getPlayerBids().size()))
                        .replace("%bid_price%", auction.getEconomy().getText().replace("%price%", highestBid != null ? IsekaiAuctions.getInstance().numberFormat.format(highestBid.getBidPrice()) : ""))
                        .replace("%bidder_displayname%", highestBid != null ? highestBid.getBidOwnerDisplayName() : "")
                        .replace("%buyer_displayname%", highestBid != null ? highestBid.getBidOwnerDisplayName() : "")
                        .replace("%seller_displayname%", auction.getAuctionOwnerDisplayName())
                        .replace("%seller_name%", seller.getName() != null ? seller.getName() : "")
                        .replace("%buyer_name%", buyer != null ? (buyer.getName() != null ? buyer.getName() : "") : "")
                        .replace("%auction_type%", auction.getAuctionType().name())
                        .replace("%auction_price%", auction.getEconomy().getText().replace("%price%", IsekaiAuctions.getInstance().numberFormat.format(auction.getAuctionPrice())))
                        .replace("%auction_time%", IsekaiAuctions.getInstance().timeFormat.formatTime(auction.getAuctionEndTime() - ZonedDateTime.now().toInstant().getEpochSecond(), "auction_times"))
                ));
            }

        meta.setLore(newLore);

        List<String> flags = IsekaiAuctions.getInstance().configFile.getStringList("settings.auction_flags");
        if (!flags.isEmpty()) {
            if (flags.contains("ALL")) {
                if (IsekaiAuctions.getInstance().version > 13)
                    meta.setAttributeModifiers(ImmutableMultimap.of());

                meta.addItemFlags(ItemFlag.values());
            }
            else
                for (String flag : flags)
                    meta.addItemFlags(ItemFlag.valueOf(flag));
        }

        itemStack.setItemMeta(meta);
        return itemStack;
    }
}