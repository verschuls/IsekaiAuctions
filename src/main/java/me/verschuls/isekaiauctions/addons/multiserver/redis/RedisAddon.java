package me.verschuls.isekaiauctions.addons.multiserver.redis;

import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.addons.multiserver.MultiServerManager;

import java.util.UUID;

public class RedisAddon implements MultiServerManager {

    private final RedisManager manager;

    public RedisAddon() {
        this.manager = new RedisManager();
    }

    private void publish(String text) {
        IsekaiAuctions.getInstance().dataHandler.debug("SENT Redis Message: &f" + text + " &8(%level_color%Multi Server&8)");
        try {
            manager.publish(text);
        } catch (Exception e) {
            e.printStackTrace(System.console().writer());
        }
    }

    private boolean publish(UUID uuid, String text) {
        IsekaiAuctions.getInstance().dataHandler.debug("SENT Redis Message: &f" + text + " &8(%level_color%Multi Server&8)");
        try {
            return manager.publish(String.valueOf(uuid), text);
        } catch (Exception e) {
            return false;
        }
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
            return true;
        }
    }

    @Override
    public void removeUpdatingAuction(String uuid, String text) {
        try {
            manager.removeAuctionMessage(uuid, text);
        } catch (Exception e) {
            e.printStackTrace(System.console().writer());

        }
    }

    @Override
    public void shutDown() {
        manager.shutdown();
    }
}
