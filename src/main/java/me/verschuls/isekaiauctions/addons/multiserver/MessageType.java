package me.verschuls.isekaiauctions.addons.multiserver;

import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.PlayerBid;

import java.util.UUID;

public enum MessageType {
    RELOAD {
        @Override
        public void getMessage(String text) {
            IsekaiAuctions.getInstance().reload();
            IsekaiAuctions.getInstance().dataHandler.debug("RELOAD &8(%level_color%Multi Server&8)");
        }
    },

    STATS_UPDATE {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            UUID uuid = UUID.fromString(text);
            IsekaiAuctions.getInstance().databaseManager.loadStat(uuid);

            IsekaiAuctions.getInstance().dataHandler.debug("UPDATE Stats: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    },

    AUCTION_LOAD {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            UUID uuid = UUID.fromString(text);
            IsekaiAuctions.getInstance().databaseManager.loadAuction(uuid);

            AuctionCache.removeUpdatingAuction(uuid);
            IsekaiAuctions.getInstance().multiServerManager.removeUpdatingAuction(text, "AUCTION_LOAD:" + text);

            IsekaiAuctions.getInstance().dataHandler.debug("LOAD Auction: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    },

    AUCTION_DELETE {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            UUID uuid = UUID.fromString(text);
            AuctionCache.removeAuction(uuid);

            AuctionCache.removeUpdatingAuction(uuid);
            IsekaiAuctions.getInstance().multiServerManager.removeUpdatingAuction(text, "AUCTION_DELETE:" + text);

            IsekaiAuctions.getInstance().dataHandler.debug("DELETE Auction: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    },

    AUCTION_SELLER_COLLECTED {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            UUID uuid = UUID.fromString(text);
            Auction auction = AuctionCache.getAuction(uuid);
            if (auction == null)
                return;

            auction.setSellerClaimed(true);

            AuctionCache.removeUpdatingAuction(uuid);
            IsekaiAuctions.getInstance().multiServerManager.removeUpdatingAuction(text, "AUCTION_SELLER_COLLECTED:" + text);

            IsekaiAuctions.getInstance().dataHandler.debug("SELLER COLLECTED Auction: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    },

    AUCTION_BUYER_COLLECTED {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            String[] args = text.split(":");
            if (args.length < 2)
                return;

            UUID uuid = UUID.fromString(args[0]);
            Auction auction = AuctionCache.getAuction(uuid);
            if (auction == null)
                return;

            UUID player = UUID.fromString(args[1]);
            PlayerBid playerBid = auction.getAuctionBids().getPlayerBid(player);
            if (playerBid == null)
                return;

            playerBid.setCollected(true);

            AuctionCache.removeUpdatingAuction(uuid);
            IsekaiAuctions.getInstance().multiServerManager.removeUpdatingAuction(args[0], "AUCTION_BUYER_COLLECTED:" + text);

            IsekaiAuctions.getInstance().dataHandler.debug("BUYER COLLECTED Auction: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    },

    AUCTION_BOUGHT {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            String[] args = text.split(":");
            if (args.length < 2)
                return;

            UUID uuid = UUID.fromString(args[0]);
            Auction auction = AuctionCache.getAuction(uuid);
            if (auction == null)
                return;

            UUID player = UUID.fromString(args[1]);
            auction.getAuctionBids().addPlayerBid(new PlayerBid(player, auction.getAuctionPrice(), true));

            AuctionCache.removeUpdatingAuction(uuid);
            IsekaiAuctions.getInstance().multiServerManager.removeUpdatingAuction(args[0], "AUCTION_BOUGHT:" + text);

            IsekaiAuctions.getInstance().dataHandler.debug("BOUGHT Auction: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    },

    AUCTION_PLACE_BID {
        @Override
        public void getMessage(String text) {
            if (text == null || text.isEmpty())
                return;

            String[] args = text.split(":", 3);
            if (args.length < 3)
                return;

            UUID uuid = UUID.fromString(args[0]);
            Auction auction = AuctionCache.getAuction(uuid);
            if (auction == null)
                return;

            UUID player = UUID.fromString(args[1]);
            double price = Double.parseDouble(args[2]);

            auction.getAuctionBids().addPlayerBid(new PlayerBid(player, price, false));
            auction.setAuctionEndTime(auction.getAuctionEndTime() + IsekaiAuctions.getInstance().configFile.getLong("settings.add_time_when_bid", 0));

            AuctionCache.removeUpdatingAuction(uuid);
            IsekaiAuctions.getInstance().multiServerManager.removeUpdatingAuction(args[0], "AUCTION_PLACE_BID:" + text);

            IsekaiAuctions.getInstance().dataHandler.debug("PLACE BID Auction: &f" + text + " &8(%level_color%Multi Server&8)");
        }
    };

    public abstract void getMessage(String text);
}
