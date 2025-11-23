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

public class MySQLDatabase extends Database {

    public MySQLDatabase(Type type, Config config) {
        super(type, config);
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
                end_time INT(11),
                type TINYTEXT NOT NULL,
                claimed BOOLEAN,
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
                won_auctions INTEGER NOT NULL,
                lost_auctions INTEGER NOT NULL,
                total_bids INTEGER NOT NULL,
                highest_bid DOUBLE NOT NULL,
                spent_money DOUBLE NOT NULL,
                created_auctions INTEGER NOT NULL,
                expired_auctions INTEGER NOT NULL,
                sold_auctions INTEGER NOT NULL,
                earned_money DOUBLE NOT NULL,
                total_fees DOUBLE NOT NULL
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
            try (Connection con = getConnection();
                 PreparedStatement statement = con.prepareStatement("SELECT * FROM auctions WHERE uuid = ?")) {
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
            try (Connection con = getConnection();
                 PreparedStatement itemsStatement = con.prepareStatement("SELECT * FROM player_items WHERE uuid = ?")) {
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
            try (Connection con = getConnection();
                 PreparedStatement statement = con.prepareStatement("SELECT * FROM auctions")) {
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
            try (Connection con = getConnection();
                 PreparedStatement statsStatement = con.prepareStatement("SELECT * FROM player_stats WHERE uuid = ?")) {
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
            try (Connection con = getConnection();
                    PreparedStatement statement = con
                    .prepareStatement("REPLACE INTO auctions (uuid, owner, display_name, item, bids, price, end_time, type, claimed, economy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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

            try (Connection con = getConnection();
                    PreparedStatement statement = con
                    .prepareStatement("REPLACE INTO auctions (uuid, owner, display_name, item, bids, price, end_time, type, claimed, economy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            String sql = "REPLACE INTO player_items (uuid, create_item) VALUES (?, ?)";
            runTask(() -> {
                try (Connection con = getConnection();
                        PreparedStatement statement = con.prepareStatement(sql)) {
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
            try (Connection con = getConnection();
                    PreparedStatement statement = con
                    .prepareStatement("REPLACE INTO player_stats (uuid, won_auctions, lost_auctions, total_bids, highest_bid, spent_money, created_auctions, expired_auctions, sold_auctions, earned_money, total_fees) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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