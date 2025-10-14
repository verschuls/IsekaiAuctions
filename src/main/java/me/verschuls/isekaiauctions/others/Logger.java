package me.verschuls.isekaiauctions.others;

import lombok.Getter;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.logging.Level;

public class Logger {
    @Getter private static final String prefix = "&8[&bIsekaiAuctions&8]";

    public static void sendConsoleMessage(String message, LogLevel level) {
        if (message == null || message.isEmpty())
            return;
        level = level == null ? LogLevel.INFO : level;

        Bukkit.getConsoleSender().sendMessage(Utils.colorize(getPrefix() + " " + level.getPrefix() + " " + level.getColor() + message
                .replace("%prefix%", getPrefix())
                .replace("%level_prefix%", level.getPrefix())
                .replace("%level_color%", level.getColor())));
    }

    public static void logError(Exception e) {
        IsekaiAuctions.getInstance().getLogger().log(Level.SEVERE, "Error occurred: ", e.fillInStackTrace());
        IsekaiAuctions.getInstance().getLogger().log(Level.SEVERE, Arrays.toString(e.getStackTrace()));
    }

    public static void logError(Throwable e) {
        IsekaiAuctions.getInstance().getLogger().log(Level.SEVERE, "Error occurred: ", e);
    }

    @Getter
    public enum LogLevel {
        INFO("&8(&2INFO&8)", "&a"),
        WARN("&8(&6WARN&8)", "&e"),
        ERROR("&8(&4ERROR&8)", "&c"),
        DEBUG("&8(&4DEBUG&8)", "&c");

        private final String prefix;
        private final String color;

        LogLevel(String prefix, String color) {
            this.prefix = prefix;
            this.color = color;
        }
    }
}
