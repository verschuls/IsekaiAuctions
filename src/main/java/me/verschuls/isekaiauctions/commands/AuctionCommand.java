package me.verschuls.isekaiauctions.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.cooldown.Cooldown;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import eu.decentsoftware.holograms.api.utils.scheduler.S;
import me.verschuls.auctionsapi.AuctionHook;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.auctionsapi.cache.CategoryCache;
import me.verschuls.auctionsapi.cache.PlayerCache;
import me.verschuls.auctionsapi.events.ItemPreviewEvent;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.AuctionType;
import me.verschuls.isekaiauctions.managers.PlayerPreferences;
import me.verschuls.isekaiauctions.menus.*;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.units.qual.A;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Command(name = "auction", aliases = {"ah", "auc"})
@Cooldown(key = "auctions-cooldown", count = 30L, unit = ChronoUnit.SECONDS, bypass = "isekaiauctions.bypass.cooldown")
@Permission(value = "isekaiauctions.commands.*")
public class AuctionCommand {

    @Execute
    @Permission(value = "isekaiauctions.commands.auctions")
    void main(@Context Player player) {
        String menuToOpen = IsekaiAuctions.getInstance().configFile.getString("settings.menu_to_open_directly");
        if (menuToOpen != null && !menuToOpen.isEmpty()) {
            if (menuToOpen.equalsIgnoreCase("auctions")) {
                String category = PlayerCache.getPlayers().containsKey(player.getUniqueId()) ? PlayerCache.getPreferences(player.getUniqueId()).getCategory().getName() : IsekaiAuctions.getInstance().category;
                new AuctionsMenu(player).open(category, 1);
            } else AuctionHook.openMainMenu(player);
            return;
        }
        sendHelp(player);
    }

    @Execute(name = "help")
    void help(@Context Player player) {
        sendHelp(player);
    }

    private static void sendHelp(CommandSender sender) {
        Utils.sendMessage(sender, "player_usage");
    }

    @Execute(name = "bids")
    @Permission(value = "isekaiauctions.commands.bids")
    void bids(@Context Player player) {
        new BidsMenu(player).open(1, "command");
    }

    @Execute(name = "menu")
    @Permission(value = "isekaiauctions.commands.menu")
    void menu(@Context Player player) {
        AuctionHook.openMainMenu(player);
    }

    @Execute(name = "manage")
    @Permission(value = "isekaiauctions.commands.manage")
    void manage(@Context Player player) {
        new ManageMenu(player).open(1, "command");
    }

    @Execute(name = "auctions")
    @Permission(value = "isekaiauctions.commands.auctions")
    void auctions(@Context Player player, @Arg Optional<String> category_) {
        String category = category_.filter(str->CategoryCache.getCategories().containsKey(str)).
                orElseGet(()->PlayerCache.getPlayers().containsKey(player.getUniqueId()) ? PlayerCache.getPreferences(player.getUniqueId()).getCategory().getName() : IsekaiAuctions.getInstance().category);
        new AuctionsMenu(player).open(category, 1);
    }

    @Execute(name = "view")
    @Permission(value = "isekaiauctions.commands.view")
    void view(@Context Player player, @Arg String player_or_auction) {
        PlaceholderUtil placeholderUtil = new PlaceholderUtil().addPlaceholder("%command_name%", "view");
        try {
            UUID uuid = UUID.fromString(player_or_auction);
            Auction auction = AuctionCache.getAuction(uuid);
            if (auction != null) {
                if (auction.getAuctionType().equals(AuctionType.BIN)) new BinViewMenu(player, auction).open("command");
                else new NormalViewMenu(player, auction).open("command");
                return;
            }

            Auction endedAuction = AuctionCache.getEndedAuction(uuid);
            if (endedAuction != null) {
                Utils.sendMessage(player, "ended_auction", placeholderUtil);
                return;
            }

            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                //sendhelp
                return;
            }

            new ViewAuctionsMenu(player, target).open(1);
        } catch(Exception e) {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(player_or_auction);
                new ViewAuctionsMenu(player, target).open(1);
            } catch (Exception ee) {
                Utils.sendMessage(player, "view_usage", placeholderUtil);
            }
        }
    }

    @Execute(name = "sell")
    @Permission(value = "isekaiauctions.commands.sell")
    void sell(@Context Player player, @Arg String price_, @Arg Optional<String> duration, @Arg Optional<String> type_) {
        PlaceholderUtil placeholderUtil = new PlaceholderUtil().addPlaceholder("%command_name%", "ah");
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = slot >= 0 ? player.getInventory().getItem(slot) : null;

        if (item == null || item.getType() == Material.AIR) {
            Utils.sendMessage(player, "wrong_item", placeholderUtil);
            return;
        }

        String sellable = AuctionHook.isSellable(player, item);
        if (!sellable.isEmpty()) {
            Utils.sendMessage(player, sellable);
            return;
        }

        // Price Check
        double price;
        double reversedPrice = IsekaiAuctions.getInstance().numberFormat.reverseFormat(price_);
        if (reversedPrice > 1)
            price = reversedPrice;
        else {
            try {
                price = Double.parseDouble(price_);
            } catch (Exception e) {
                Utils.sendMessage(player, "wrong_price", placeholderUtil);
                return;
            }
        }

        if (price <= 0) {
            Utils.sendMessage(player, "wrong_price", placeholderUtil);
            return;
        }

        // Price Limit Check
        double priceLimit = AuctionHook.getPriceLimit(player, "price_limit");
        if (price > priceLimit) {
            Utils.sendMessage(player, "reached_price_limit", new PlaceholderUtil()
                    .addPlaceholder("%price_limit%", IsekaiAuctions.getInstance().numberFormat.format(priceLimit)));
            return;
        }

        // Time Check
        int time = IsekaiAuctions.getInstance().createTime;
        if (duration.isPresent()) {
            try {
                time = IsekaiAuctions.getInstance().timeFormat.convertTime(duration.get());
            } catch (Exception e) {
                Utils.sendMessage(player, "wrong_duration", placeholderUtil);
                return;
            }
        }

        if (time <= 0) {
            Utils.sendMessage(player, "wrong_duration", placeholderUtil);
            return;
        }

        // Time Limit Check
        int limit = AuctionHook.getLimit(player, "duration_limit");
        if (time > limit) {
            Utils.sendMessage(player, "reached_duration_limit", new PlaceholderUtil()
                    .addPlaceholder("%duration_limit%", String.valueOf(limit)));
            return;
        }


        String type = IsekaiAuctions.getInstance().configFile.getString("settings.default_type", "normal");
        // Auction Type Check
        if (type_.isPresent()) {
            type = type_.get();
            if (!type.equalsIgnoreCase("bin") && !type.equalsIgnoreCase("normal"))
                type = IsekaiAuctions.getInstance().configFile.getString("settings.default_type", "normal");

            // 2. Type Check
            if (!type.equalsIgnoreCase("bin") && !type.equalsIgnoreCase("normal")) {
                Utils.sendMessage(player, "wrong_type", placeholderUtil);
                return;
            }
        }

        // Check if auction type is disabled
        if (AuctionHook.isAuctionTypeDisabled(type)) {
            Utils.sendMessage(player, "disabled_auction_type", new PlaceholderUtil()
                    .addPlaceholder("%auction_type%", type.toUpperCase(Locale.ENGLISH)));
            return;
        }

        // Preview Item Event
        ItemPreviewEvent event = new ItemPreviewEvent(player, item);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        PlayerPreferences playerAuction = PlayerCache.getPreferences(player.getUniqueId());
        boolean status = playerAuction.updateCreateItem(player, slot, true);
        if (!status)
            return ;

        playerAuction.setCreateType(AuctionType.valueOf(type.toUpperCase(Locale.ENGLISH)));
        playerAuction.setCreatePrice(price);
        playerAuction.setCreateTime(time);

        new CreateMenu(player).open("command");
    }

}
