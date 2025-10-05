package me.verschuls.isekaiauctions.others;

import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.inventoryapi.HInventory;
import me.verschuls.isekaiauctions.inventoryapi.inventory.InventoryAPI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class TaskUtils {
    public static boolean isFolia;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (final ClassNotFoundException e) {
            isFolia = false;
        }
    }

    public static void run(Runnable runnable) {
        if (isFolia) {
            IsekaiAuctions.getInstance().getServer().getGlobalRegionScheduler().execute(IsekaiAuctions.getInstance(), runnable);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTask(IsekaiAuctions.getInstance());
        }
    }

    public static void runAsync(Runnable runnable) {
        if (isFolia) {
            IsekaiAuctions.getInstance().getServer().getGlobalRegionScheduler().execute(IsekaiAuctions.getInstance(), runnable);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskAsynchronously(IsekaiAuctions.getInstance());
        }
    }

    public static void runLater(Runnable runnable, long delayTicks) {
        if (isFolia) {
            IsekaiAuctions.getInstance().getServer().getGlobalRegionScheduler().runDelayed(IsekaiAuctions.getInstance(), task -> runnable.run(), delayTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskLater(IsekaiAuctions.getInstance(), delayTicks);
        }
    }

    public static void runLaterAsync(Runnable runnable, long delayTicks) {
        if (isFolia) {
            IsekaiAuctions.getInstance().getServer().getGlobalRegionScheduler().runDelayed(IsekaiAuctions.getInstance(), task -> runnable.run(), delayTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskLaterAsynchronously(IsekaiAuctions.getInstance(), delayTicks);
        }
    }

    public static void runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            IsekaiAuctions.getInstance().getServer().getGlobalRegionScheduler().runAtFixedRate(IsekaiAuctions.getInstance(), task -> runnable.run(), delayTicks, periodTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskTimerAsynchronously(IsekaiAuctions.getInstance(), delayTicks, periodTicks);
        }
    }

    public static void runTimerAsync(Player player, String id, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            IsekaiAuctions.getInstance().getServer().getGlobalRegionScheduler().runAtFixedRate(IsekaiAuctions.getInstance(), task -> {
                HInventory inventory = InventoryAPI.getInventory(player);
                if (inventory == null) {
                    cancelTask(task);
                    return;
                }

                String inventoryId = inventory.getId();
                if (!inventoryId.equalsIgnoreCase(id)) {
                    if (id.equalsIgnoreCase("auctions") && inventoryId.equalsIgnoreCase("search")) {
                        runnable.run();
                        return;
                    }

                    cancelTask(task);
                    return;
                }

                runnable.run();
            }, delayTicks, periodTicks);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    HInventory inventory = InventoryAPI.getInventory(player);
                    if (inventory == null) {
                        cancel();
                        return;
                    }

                    String inventoryId = inventory.getId();
                    if (!inventoryId.equalsIgnoreCase(id)) {
                        if (id.equalsIgnoreCase("auctions") && inventoryId.equalsIgnoreCase("search")) {
                            runnable.run();
                            return;
                        }

                        cancel();
                        return;
                    }

                    runnable.run();
                }
            }.runTaskTimerAsynchronously(IsekaiAuctions.getInstance(), delayTicks, periodTicks);
        }
    }

    private static void cancelTask(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
        if (!isFolia)
            return;

        task.cancel();
    }
}