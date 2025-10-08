package me.verschuls.isekaiauctions.commands;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import eu.decentsoftware.holograms.api.utils.scheduler.S;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

@Command(name = "isekaiauctions")
@Permission(value = "isekaiauctions.commands.admin")
public class IsekaiAuctionsCommand {

    @Execute
    void info(@Context CommandSender sender) {
        sendStatus(sender);
    }

    public static void sendStatus(CommandSender sender) {
        List<String> status = new ArrayList<>();
        status.add("&6[&aIsekaiAuctions&6] &ePlugin Status/Info");
        status.add("&6| &eAuthors: &6"+IsekaiAuctions.getInstance().getPluginMeta().getAuthors().toString().replace("[", "").replace("]", ""));
        status.add("&6| &eGitHub: &6https://github.com/verschuls/IsekaiAuctions");
        status.add("&6| &eVersion: &6"+IsekaiAuctions.getInstance().getPluginMeta().getVersion());
        status.add("&6| &eDatabase&6[&f"+IsekaiAuctions.getInstance().databaseManager.type()+"&6]&e: "+(IsekaiAuctions.getInstance().databaseManager.status() ? "&a" : "&c")+"Operational");
        status.add("&6| &eMain Commands: &6/ah /ahadmin");
        for (String msg : status)
            sender.sendMessage(Utils.colorize(msg));
    }
}
