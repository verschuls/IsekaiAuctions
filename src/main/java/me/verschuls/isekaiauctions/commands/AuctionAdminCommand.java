package me.verschuls.isekaiauctions.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import me.verschuls.auctionsapi.AuctionHook;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.inventoryapi.inventory.InventoryAPI;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.Category;
import me.verschuls.isekaiauctions.menus.*;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Command(name = "auctionadmin", aliases = {"ahadmin", "aucadmin"})
@Permission(value = "isekaiauctions.commands.admin.*")
public class AuctionAdminCommand {


    @Execute
    void main(@Context CommandSender sender) {
        sendHelp(sender);
    }

    @Execute(name = "help")
    void help(@Context CommandSender sender) {
        sendHelp(sender);
    }

    private static void sendHelp(CommandSender sender) {
        Utils.sendMessage(sender, "admin_usage");
    }


    @Execute(name = "menu")
    @Permission(value = "isekaiauctions.commands.admin.menu")
    void menu(@Context CommandSender sender, @Arg String player, @Arg Optional<String> category_) {
        Player b = Bukkit.getPlayerExact(player);
        if (b == null) {
            Utils.sendMessage(sender, "wrong_player",
                    new PlaceholderUtil().addPlaceholder("%command_name%", "ahadmin").addPlaceholder("%player_name%", player));
            return;
        }

        if (category_.isPresent()) {
            Category category = AuctionHook.getCategory(category_.get());
            if (category != null) {
                new AuctionsMenu(b).open(category.getName(), 1);
            } else {
                switch (category_.get()) {
                    case "manage" -> new ManageMenu(b).open(1, "command");
                    case "bids" -> new BidsMenu(b).open(1, "command");
                    case "create" -> new CreateMenu(b).open("command");
                    case "main" -> new MainMenu(b).open();
                    case "stats" -> new StatsMenu(b).open();
                }
                return;
            }
        }
        AuctionHook.openMainMenu(b);
    }

    @Execute(name = "cancel")
    @Permission(value = "isekaiauctions.commands.admin.cancel")
    void cancel(@Context CommandSender sender, @Arg String auctions_uuid) {
        try {
            UUID uuid = UUID.fromString(auctions_uuid);
            Auction auction = AuctionCache.getAuction(uuid);
            if (auction == null) return;

            auction.setAuctionEndTime(ZonedDateTime.now().toInstant().getEpochSecond() - 1000);
            Utils.sendMessage(sender, "admin_cancelled", new PlaceholderUtil()
                    .addPlaceholder("%player_displayname%", auction.getAuctionOwnerDisplayName()));
        } catch (Exception e) {
            Utils.sendMessage(sender, "wrong_auction", null);
        }
    }

    @Execute(name = "lock")
    @Permission(value = "isekaiauctions.commands.admin.lock")
    void lock(@Context CommandSender sender) {
        IsekaiAuctions.getInstance().locked = !IsekaiAuctions.getInstance().locked;
        for (Player player : Bukkit.getOnlinePlayers())
            if (!player.isOp() && InventoryAPI.hasInventory(player))
                player.closeInventory();

        Utils.sendMessage(sender, IsekaiAuctions.getInstance().locked ? "locked" : "unlocked");
    }

    @Execute(name = "reload")
    @Permission(value = "isekaiauctions.commands.admin.reload")
    void reload(@Context CommandSender sender) {
        long start = System.currentTimeMillis();
        IsekaiAuctions.getInstance().reload();

        if (IsekaiAuctions.getInstance().multiServerManager != null)
            IsekaiAuctions.getInstance().multiServerManager.reload();

        Utils.sendMessage(sender, "reloaded", new PlaceholderUtil()
                .addPlaceholder("%reload_time%", String.valueOf(System.currentTimeMillis() - start)));
    }

    @Execute(name = "status")
    @Permission(value = "isekaiauctions.commands.admin.status")
    void status(@Context CommandSender sender) {
        IsekaiAuctionsCommand.sendStatus(sender);
    }
}
