package me.verschuls.isekaiauctions.others;

import lombok.AllArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

@AllArgsConstructor
public class BukkitExecutor implements Executor {

    private final JavaPlugin plugin;

    @Override
    public void execute(@NotNull Runnable command) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, command);
    }
}
