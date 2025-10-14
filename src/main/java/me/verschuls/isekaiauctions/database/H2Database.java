package me.verschuls.isekaiauctions.database;

import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.auctionsapi.cache.PlayerCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.addons.multiserver.BungeeAddon;
import me.verschuls.isekaiauctions.addons.multiserver.redis.RedisAddon;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.PlayerBid;
import me.verschuls.isekaiauctions.managers.PlayerStats;
import me.verschuls.isekaiauctions.others.Logger;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.List;
import java.util.UUID;

public class H2Database extends Database {

    public H2Database() {
        super(Type.H2, Config.builder().username("sa").password("").maximum_pool_size(10).minimum_idle(3).build());
    }

    @Override
    protected void initTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS auctions (
                uuid VARCHAR(36) PRIMARY KEY,
                owner VARCHAR(36) NOT NULL,
                display_name TEXT,
                item BLOB NOT NULL,
                bids TEXT,
                price DOUBLE NOT NULL,
                end_time BIGINT,
                type VARCHAR(255) NOT NULL,
                claimed BOOLEAN DEFAULT FALSE,
                economy TEXT
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_items (
              uuid VARCHAR(36) PRIMARY KEY,
              create_item BLOB
            );
            """);

            // Player stats table
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid VARCHAR(36) PRIMARY KEY,
                won_auctions INTEGER NOT NULL DEFAULT 0,
                lost_auctions INTEGER NOT NULL DEFAULT 0,
                total_bids INTEGER NOT NULL DEFAULT 0,
                highest_bid DOUBLE NOT NULL DEFAULT 0.0,
                spent_money DOUBLE NOT NULL DEFAULT 0.0,
                created_auctions INTEGER NOT NULL DEFAULT 0,
                expired_auctions INTEGER NOT NULL DEFAULT 0,
                sold_auctions INTEGER NOT NULL DEFAULT 0,
                earned_money DOUBLE NOT NULL DEFAULT 0.0,
                total_fees DOUBLE NOT NULL DEFAULT 0.0
            )
        """);


        } catch (SQLException e) {
            Logger.sendConsoleMessage("Failed to initialize tables", Logger.LogLevel.ERROR);
            Logger.logError(e);
            IsekaiAuctions.disablePlugin();
        }
    }

    @Override
    public void deleteAuction(UUID uuid) {
        runTask(() -> {
            try (PreparedStatement statement = getConnection().prepareStatement("DELETE FROM auctions WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.execute();
            } catch (SQLException x) {
                reportError("delete auction", x);
            }
        });
    }

    @Override
    public void deleteItem(UUID uuid) {
        runTask(() -> {
            try (PreparedStatement statement = getConnection().prepareStatement("DELETE FROM player_items WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.execute();
            } catch (SQLException x) {
                reportError("delete item", x);
            }
        });
    }

    @Override
    public void loadAuction(UUID uuid) {
        runTask(() -> {
            try (PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM auctions WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());

                ResultSet set = statement.executeQuery();
                if (set.next())
                    load(set);
            } catch (SQLException x) {
                reportError("load auction", x);
            }
        });
    }

    @Override
    public void loadItem(UUID uuid) {
        runTask(() -> {
            try (PreparedStatement itemsStatement = getConnection().prepareStatement("SELECT * FROM player_items WHERE uuid = ?")) {
                itemsStatement.setString(1, uuid.toString());
                ResultSet item = itemsStatement.executeQuery();

                if (item.next()) {
                    byte[] createItem = item.getBytes(2);
                    if (createItem != null) {
                        ItemStack newItem = ItemStack.deserializeBytes(createItem);
                        if (newItem != null)
                            PlayerCache.setItem(uuid, newItem);
                        else
                            PlayerCache.removeItem(uuid);
                    }
                }
            } catch (SQLException x) {
                reportError("load item", x);
            }
        });
    }

    @Override
    public boolean loadAuctions() {
        runTask(() -> {
            try (PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM auctions")) {
                ResultSet set = statement.executeQuery();

                long time = System.currentTimeMillis();
                int i = 0;
                while (set.next()) {
                    if (IsekaiAuctions.getInstance().disabled) return;
                    load(set);
                    i++;
                }

                IsekaiAuctions.getInstance().loaded = true;
                if (IsekaiAuctions.getInstance().configFile.getBoolean("redis.enabled", false)) {
                    IsekaiAuctions.getInstance().multiServerManager = new RedisAddon();
                    Logger.sendConsoleMessage("Enabled &fIsekaiAuctions Redis %level_color%support!",
                            Logger.LogLevel.INFO);
                } else if (IsekaiAuctions.getInstance().configFile.getBoolean("addons.bungeecord", false)) {
                    IsekaiAuctions.getInstance().multiServerManager = new BungeeAddon();
                    Logger.sendConsoleMessage("Enabled &fIsekaiAuctions Bungee %level_color%support!",
                            Logger.LogLevel.INFO);
                }

                Logger.sendConsoleMessage("&f" + i + " %level_color%auctions loaded in &f"
                        + (System.currentTimeMillis() - time) + " ms%level_color%!", Logger.LogLevel.INFO);
            } catch (SQLException x) {
                reportError("load auctions", x);
            }
        });
        return true;
    }

    @Override
    public void loadStat(UUID uuid) {
        runTask(() -> {
            try (PreparedStatement statsStatement = getConnection().prepareStatement("SELECT * FROM player_stats WHERE uuid = ?")) {
                statsStatement.setString(1, uuid.toString());
                ResultSet stat = statsStatement.executeQuery();

                if (stat.next()) {
                    int wonAuctions = stat.getInt(2);
                    int lostAuctions = stat.getInt(3);
                    int totalBids = stat.getInt(4);
                    double highestBid = stat.getDouble(5);
                    double spentMoney = stat.getDouble(6);

                    int createdAuctions = stat.getInt(7);
                    int expiredAuctions = stat.getInt(8);
                    int soldAuctions = stat.getInt(9);
                    double earnedMoney = stat.getDouble(10);
                    double totalFees = stat.getDouble(11);

                    PlayerStats data = PlayerCache.getStats(uuid);
                    data.setWonAuctions(wonAuctions);
                    data.setLostAuctions(lostAuctions);
                    data.setTotalBids(totalBids);
                    data.setHighestBid(highestBid);
                    data.setSpentMoney(spentMoney);

                    data.setCreatedAuctions(createdAuctions);
                    data.setExpiredAuctions(expiredAuctions);
                    data.setSoldAuctions(soldAuctions);
                    data.setEarnedMoney(earnedMoney);
                    data.setTotalFees(totalFees);
                }
            } catch (SQLException x) {
                reportError("load stat", x);
            }
        });
    }

    @Override
    public void saveAuctions() {
        runTask(() -> {
            try (PreparedStatement statement = getConnection()
                    .prepareStatement("MERGE INTO auctions (uuid, owner, display_name, item, bids, price, end_time, type, claimed, economy) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                long time = System.currentTimeMillis();
                for (Auction auction : AuctionCache.getAuctions().values()) {
                    StringBuilder playerBids = new StringBuilder();
                    List<PlayerBid> bids = auction.getAuctionBids().getPlayerBids();
                    if (!bids.isEmpty()) {
                        for (PlayerBid bid : bids) {
                            String string = bid.toString();

                            playerBids.append(",,").append(string);
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
    public void saveAuction(Auction auction) {
        runTask(() -> {
            StringBuilder playerBids = new StringBuilder();
            List<PlayerBid> bids = auction.getAuctionBids().getPlayerBids();
            if (!bids.isEmpty()) {
                for (PlayerBid bid : bids) {
                    String string = bid.toString();

                    playerBids.append(",,").append(string);
                }

                playerBids.delete(0, 2);
            }

            try (PreparedStatement statement = getConnection()
                    .prepareStatement("MERGE INTO auctions (uuid, owner, display_name, item, bids, price, end_time, type, claimed, economy) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
    public void saveItem(UUID uuid, ItemStack item) {
        if (item != null) {
            String sql = "MERGE INTO player_items (uuid, create_item) KEY(uuid) VALUES (?, ?)";
            runTask(() -> {
                try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
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
                    .prepareStatement("MERGE INTO player_stats (uuid, won_auctions, lost_auctions, total_bids, highest_bid, spent_money, created_auctions, expired_auctions, sold_auctions, earned_money, total_fees) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
