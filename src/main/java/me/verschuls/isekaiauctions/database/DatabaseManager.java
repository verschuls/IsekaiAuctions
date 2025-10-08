package me.verschuls.isekaiauctions.database;

import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.PlayerStats;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface DatabaseManager {
    void deleteAuction(String uuid);

    void loadAuction(UUID uuid);
    boolean loadAuctions();

    void loadStat(UUID uuid);
    void loadItem(UUID uuid);

    void saveAuction(Auction auction);
    void saveAuctions();

    void saveItem(UUID uuid, ItemStack item);
    void saveStats(PlayerStats stats);

    String type();
    boolean status();
    void shutdown();
}