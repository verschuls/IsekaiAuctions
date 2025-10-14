package me.verschuls.isekaiauctions.addons.multiserver.redis;

import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.addons.multiserver.MultiServerManager;
import me.verschuls.isekaiauctions.others.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RedisAddon implements MultiServerManager {

    private final RedisManager manager;

    public RedisAddon() {
        manager = RedisManager.get();
    }

    private void publish(String text) {
        IsekaiAuctions.getInstance().dataHandler.debug("SENT Redis Message: &f" + text + " &8(%level_color%Multi Server&8)");
        try {
            manager.publish(text);
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    private boolean publish(UUID uuid, String text) {
        IsekaiAuctions.getInstance().dataHandler.debug("SENT Redis Message: &f" + text + " &8(%level_color%Multi Server&8)");
        return CompletableFuture.supplyAsync(()-> manager.publish(String.valueOf(uuid), text),
                IsekaiAuctions.getExecutor()).exceptionallyAsync((e)->{
            Logger.logError(e);
            return false;
        }, IsekaiAuctions.getExecutor()).join();
    }

    @Override
    public void reload() {
        publish("RELOAD");
    }

    @Override
    public void updateStats(UUID playerUUID) {
        publish("STATS_UPDATE:" + playerUUID);
    }

    @Override
    public void loadAuction(UUID auctionUUID) {
       publish("AUCTION_LOAD:" + auctionUUID);
    }

    @Override
    public boolean deleteAuction(UUID auctionUUID) {
        return publish(auctionUUID, "AUCTION_DELETE:" + auctionUUID);
    }

    @Override
    public boolean sellerCollectedAuction(UUID auctionUUID) {
        return publish(auctionUUID, "AUCTION_SELLER_COLLECTED:" + auctionUUID);
    }

    @Override
    public boolean buyerCollectedAuction(UUID auctionUUID, UUID playerUUID) {
        return publish(auctionUUID, "AUCTION_BUYER_COLLECTED:" + auctionUUID + ":" + playerUUID);
    }

    @Override
    public boolean playerBoughtAuction(UUID auctionUUID, UUID playerUUID) {
        return publish(auctionUUID, "AUCTION_BOUGHT:" + auctionUUID + ":" + playerUUID);
    }

    @Override
    public boolean playerPlaceBidAuction(UUID auctionUUID, UUID playerUUID, double bidPrice) {
        return publish(auctionUUID, "AUCTION_PLACE_BID:" + auctionUUID + ":" + playerUUID + ":" + bidPrice);
    }

    @Override
    public boolean isAuctionUpdating(UUID uuid) {
        try {
            return manager.isAuctionMessagePublished(String.valueOf(uuid));
        } catch (Exception e) {
            Logger.logError(e);
            return true;
        }
    }

    @Override
    public void removeUpdatingAuction(String uuid, String text) {
        try {
            manager.removeAuctionMessage(uuid, text);
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    @Override
    public void shutDown() {
        manager.shutdown();
    }
}
