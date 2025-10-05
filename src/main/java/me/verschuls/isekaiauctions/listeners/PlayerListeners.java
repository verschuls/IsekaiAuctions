package me.verschuls.isekaiauctions.listeners;

import me.verschuls.auctionsapi.cache.PlayerCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.inventoryapi.inventory.InventoryVariables;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListeners implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        IsekaiAuctions.getInstance().databaseManager.loadStat(player.getUniqueId());
        IsekaiAuctions.getInstance().databaseManager.loadItem(player.getUniqueId());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        Player player = e.getPlayer();

        PlayerCache.removeItem(player.getUniqueId());
        PlayerCache.removePreferences(player.getUniqueId());
        PlayerCache.removeStats(player.getUniqueId());
        InventoryVariables.removeCooldown(player);
        InventoryVariables.removePlayerInventory(player);
    }
}
