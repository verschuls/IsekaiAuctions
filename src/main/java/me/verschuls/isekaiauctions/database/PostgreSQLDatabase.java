package me.verschuls.isekaiauctions.database;

import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.PlayerBid;
import me.verschuls.isekaiauctions.managers.PlayerStats;
import me.verschuls.isekaiauctions.others.Logger;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public class PostgreSQLDatabase extends MySQLDatabase {

    public PostgreSQLDatabase(Config config) {
        super(Type.POSTGRESQL, config);
    }

    @Override
    protected void initTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS auctions (
                uuid UUID PRIMARY KEY,
                owner UUID NOT NULL,
                display_name TEXT,
                item BYTEA NOT NULL,
                bids TEXT,
                price DOUBLE PRECISION NOT NULL,
                end_time BIGINT,
                type VARCHAR(10) NOT NULL CHECK (type IN ('ALL', 'BIN', 'NORMAL')),
                claimed BOOLEAN NOT NULL DEFAULT FALSE,
                economy TEXT
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_items (
              uuid UUID PRIMARY KEY,
              create_item BYTEA
            )
            """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid UUID PRIMARY KEY,
                won_auctions INTEGER NOT NULL DEFAULT 0,
                lost_auctions INTEGER NOT NULL DEFAULT 0,
                total_bids INTEGER NOT NULL DEFAULT 0,
                highest_bid DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                spent_money DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                created_auctions INTEGER NOT NULL DEFAULT 0,
                expired_auctions INTEGER NOT NULL DEFAULT 0,
                sold_auctions INTEGER NOT NULL DEFAULT 0,
                earned_money DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                total_fees DOUBLE PRECISION NOT NULL DEFAULT 0.0
            )
        """);

        } catch (SQLException e) {
            Logger.sendConsoleMessage("Failed to initialize tables", Logger.LogLevel.ERROR);
            Logger.logError(e);
            IsekaiAuctions.disablePlugin();
        }
    }

    @Override
    public void saveAuction(Auction auction) {
        runTask(() -> {
            StringBuilder playerBids = new StringBuilder();
            List<PlayerBid> bids = auction.getAuctionBids().getPlayerBids();
            if (!bids.isEmpty()) {
                for (PlayerBid bid : bids) {
                    playerBids.append(",,").append(bid.toString());
                }
                playerBids.delete(0, 2);
            }

            try (PreparedStatement statement = getConnection()
                    .prepareStatement("""
                    INSERT INTO auctions (uuid, owner, display_name, item, bids, price, end_time, type, claimed, economy)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        owner = EXCLUDED.owner,
                        display_name = EXCLUDED.display_name,
                        item = EXCLUDED.item,
                        bids = EXCLUDED.bids,
                        price = EXCLUDED.price,
                        end_time = EXCLUDED.end_time,
                        type = EXCLUDED.type,
                        claimed = EXCLUDED.claimed,
                        economy = EXCLUDED.economy
                    """)) {
                statement.setString(1, auction.getAuctionUUID().toString());
                statement.setString(2, auction.getAuctionOwner().toString());
                statement.setString(3, auction.getAuctionOwnerDisplayName());
                statement.setBytes(4, auction.getAuctionItem().serializeAsBytes());
                statement.setString(5, playerBids.toString());
                statement.setDouble(6, auction.getAuctionPrice());
                statement.setLong(7, auction.getAuctionEndTime());
                statement.setString(8, auction.getAuctionType().name());
                statement.setBoolean(9, auction.isSellerClaimed());
                statement.setString(10, auction.getEconomy().getKey());

                statement.execute();

                AuctionCache.removeUpdatingAuction(auction.getAuctionUUID());

                if (IsekaiAuctions.getInstance().multiServerManager != null)
                    IsekaiAuctions.getInstance().multiServerManager.loadAuction(auction.getAuctionUUID());
            } catch (SQLException x) {
                reportError("save auction", x);
                AuctionCache.addUpdatingAuction(auction.getAuctionUUID());
            }
        });
    }

    @Override
    public void saveAuctions() {
        runTask(() -> {
            try (PreparedStatement statement = getConnection()
                    .prepareStatement("""
                    INSERT INTO auctions (uuid, owner, display_name, item, bids, price, end_time, type, claimed, economy)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        owner = EXCLUDED.owner,
                        display_name = EXCLUDED.display_name,
                        item = EXCLUDED.item,
                        bids = EXCLUDED.bids,
                        price = EXCLUDED.price,
                        end_time = EXCLUDED.end_time,
                        type = EXCLUDED.type,
                        claimed = EXCLUDED.claimed,
                        economy = EXCLUDED.economy
                    """)) {
                int i = 0;
                long time = System.currentTimeMillis();
                for (Auction auction : AuctionCache.getAuctions().values()) {
                    StringBuilder playerBids = new StringBuilder();
                    List<PlayerBid> bids = auction.getAuctionBids().getPlayerBids();
                    if (!bids.isEmpty()) {
                        for (PlayerBid bid : bids) {
                            playerBids.append(",,").append(bid.toString());
                        }
                        playerBids.delete(0, 2);
                    }

                    statement.setString(1, auction.getAuctionUUID().toString());
                    statement.setString(2, auction.getAuctionOwner().toString());
                    statement.setString(3, auction.getAuctionOwnerDisplayName());
                    statement.setBytes(4, auction.getAuctionItem().serializeAsBytes());
                    statement.setString(5, playerBids.toString());
                    statement.setDouble(6, auction.getAuctionPrice());
                    statement.setLong(7, auction.getAuctionEndTime());
                    statement.setString(8, auction.getAuctionType().name());
                    statement.setBoolean(9, auction.isSellerClaimed());
                    statement.setString(10, auction.getEconomy().getKey());

                    statement.execute();
                    i++;
                }

                Logger.sendConsoleMessage("&f" + i + " %level_color%auctions saved in &f"
                        + (System.currentTimeMillis() - time) + " ms%level_color%!", Logger.LogLevel.INFO);
                IsekaiAuctions.getInstance().converting = false;
            } catch (SQLException x) {
                reportError("save auctions", x);
            }
        });
    }

    @Override
    public void saveItem(UUID uuid, ItemStack item) {
        if (item != null) {
            runTask(() -> {
                try (PreparedStatement statement = getConnection().prepareStatement("""
                    INSERT INTO player_items (uuid, create_item) VALUES (?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET create_item = EXCLUDED.create_item
                    """)) {
                    statement.setString(1, uuid.toString());
                    statement.setBytes(2, item.serializeAsBytes());
                    statement.execute();
                } catch (SQLException x) {
                    reportError("save item", x);
                }
            });
        } else {
            deleteItem(uuid);
        }
    }

    @Override
    public void saveStats(PlayerStats stats) {
        UUID uuid = stats.getPlayer();
        runTask(() -> {
            try (PreparedStatement statement = getConnection()
                    .prepareStatement("""
                    INSERT INTO player_stats (uuid, won_auctions, lost_auctions, total_bids, highest_bid, spent_money, created_auctions, expired_auctions, sold_auctions, earned_money, total_fees)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        won_auctions = EXCLUDED.won_auctions,
                        lost_auctions = EXCLUDED.lost_auctions,
                        total_bids = EXCLUDED.total_bids,
                        highest_bid = EXCLUDED.highest_bid,
                        spent_money = EXCLUDED.spent_money,
                        created_auctions = EXCLUDED.created_auctions,
                        expired_auctions = EXCLUDED.expired_auctions,
                        sold_auctions = EXCLUDED.sold_auctions,
                        earned_money = EXCLUDED.earned_money,
                        total_fees = EXCLUDED.total_fees
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, stats.getWonAuctions());
                statement.setInt(3, stats.getLostAuctions());
                statement.setInt(4, stats.getTotalBids());
                statement.setDouble(5, stats.getHighestBid());
                statement.setDouble(6, stats.getSpentMoney());
                statement.setInt(7, stats.getCreatedAuctions());
                statement.setInt(8, stats.getExpiredAuctions());
                statement.setInt(9, stats.getSoldAuctions());
                statement.setDouble(10, stats.getEarnedMoney());
                statement.setDouble(11, stats.getTotalFees());

                statement.execute();

                if (IsekaiAuctions.getInstance().multiServerManager != null)
                    IsekaiAuctions.getInstance().multiServerManager.updateStats(uuid);
            } catch (SQLException x) {
                reportError("save stats", x);
            }
        });
    }
}