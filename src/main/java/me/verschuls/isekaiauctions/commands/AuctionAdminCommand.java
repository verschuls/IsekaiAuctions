package me.verschuls.isekaiauctions.commands;

import me.verschuls.auctionsapi.AuctionHook;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.inventoryapi.inventory.InventoryAPI;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.Category;
import me.verschuls.isekaiauctions.menus.*;
import me.verschuls.isekaiauctions.others.Logger;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AuctionAdminCommand implements CommandExecutor, TabCompleter {
    private final HashMap<String, List<String>> args = new HashMap<>();

    public AuctionAdminCommand() {
        ConfigurationSection section = IsekaiAuctions.getInstance().configFile.getConfigurationSection("commands");
        if (section == null) {
            this.args.put("reload", Collections.singletonList("reload"));
            this.args.put("menu", Arrays.asList("menu", "open"));
            this.args.put("cancel", Collections.singletonList("cancel"));
            this.args.put("lock", Collections.singletonList("lock"));
        } else {
            this.args.put("reload", section.getStringList("reload"));
            this.args.put("menu", section.getStringList("menu"));
            this.args.put("cancel", section.getStringList("cancel"));
            this.args.put("lock", section.getStringList("lock"));
        }
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        if (!Utils.hasPermission(commandSender, "admin_commands", "command"))
            return Collections.emptyList();

        ArrayList<String> complete = new ArrayList<>();
        this.args.values().forEach(complete::addAll);

        complete.removeIf(type -> !Utils.hasPermission(commandSender, "admin_commands,", type));

        if (args.length == 1)
            return complete;

        return Collections.emptyList();
    }

    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!Utils.hasPermission(commandSender, "admin_commands", "command")) {
            Utils.sendMessage(commandSender, "no_permission");
            return false;
        }

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%command_name%", label);

        if (args.length > 0) {
            if (!IsekaiAuctions.getInstance().loaded) {
                Utils.sendMessage(commandSender, "loading");
                return false;
            }

            String lowerCaseArg = args[0].toLowerCase(Locale.ENGLISH);
            if (this.args.get("cancel").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "cancel")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(commandSender, "admin_cancel_usage", placeholderUtil);
                    return false;
                }

                try {
                    UUID uuid = UUID.fromString(args[1]);
                    Auction auction = AuctionCache.getAuction(uuid);
                    if (auction == null)
                        return false;

                    auction.setAuctionEndTime(ZonedDateTime.now().toInstant().getEpochSecond() - 1000);
                    Utils.sendMessage(commandSender, "admin_cancelled", new PlaceholderUtil()
                            .addPlaceholder("%player_displayname%", auction.getAuctionOwnerDisplayName()));
                    return true;
                } catch (Exception e) {
                    Utils.sendMessage(commandSender, "wrong_auction", null);
                }

                return false;
            }

            if (this.args.get("lock").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "lock")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                IsekaiAuctions.getInstance().locked = !IsekaiAuctions.getInstance().locked;
                for (Player player : Bukkit.getOnlinePlayers())
                    if (!player.isOp() && InventoryAPI.hasInventory(player))
                        player.closeInventory();

                Utils.sendMessage(commandSender, IsekaiAuctions.getInstance().locked ? "locked" : "unlocked");
                return true;
            }

            if (this.args.get("reload").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "reload")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                long start2 = System.currentTimeMillis();
                IsekaiAuctions.getInstance().reload();

                if (IsekaiAuctions.getInstance().multiServerManager != null)
                    IsekaiAuctions.getInstance().multiServerManager.reload();

                Utils.sendMessage(commandSender, "reloaded", new PlaceholderUtil()
                        .addPlaceholder("%reload_time%", String.valueOf(System.currentTimeMillis() - start2)));
                return true;
            }

            if (this.args.get("menu").contains(lowerCaseArg)) {
                if (!Utils.hasPermission(commandSender, "admin_commands", "menu")) {
                    Utils.sendMessage(commandSender, "no_permission");
                    return false;
                }

                if (args.length < 2) {
                    Utils.sendMessage(commandSender, "admin_menu_usage", placeholderUtil);
                    return false;
                }

                Player b = Bukkit.getPlayerExact(args[1]);
                if (b == null) {
                    Utils.sendMessage(commandSender, "wrong_player", placeholderUtil
                            .addPlaceholder("%player_name%", args[1]));
                    return false;
                }

                if (args.length > 2) {
                    Category category = AuctionHook.getCategory(args[2]);
                    if (category != null) {
                        new AuctionsMenu(b).open(category.getName(), 1);
                        return true;
                    } else {
                        switch (args[2]) {
                            case "manage":
                                new ManageMenu(b).open(1, "command");
                                return true;
                            case "bids":
                                new BidsMenu(b).open(1, "command");
                                return true;
                            case "create":
                                new CreateMenu(b).open("command");
                                return true;
                            case "main":
                                new MainMenu(b).open();
                                return true;
                            case "stats":
                                new StatsMenu(b).open();
                                return true;
                        }
                    }
                }

                AuctionHook.openMainMenu(b);
                return true;
            }
        }

        Utils.sendMessage(commandSender, "admin_usage", placeholderUtil);
        return false;
    }
}
