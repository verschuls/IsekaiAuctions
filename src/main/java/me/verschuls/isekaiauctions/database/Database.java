package me.verschuls.isekaiauctions.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.managers.Auction;
import me.verschuls.isekaiauctions.managers.AuctionType;
import me.verschuls.isekaiauctions.managers.PlayerBid;
import me.verschuls.isekaiauctions.managers.PlayerStats;
import me.verschuls.isekaiauctions.others.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;

public abstract class Database {
    private final Type type;
    private final HikariDataSource dataSource;

    public Database(Type type, Config config) {
        this.type = type;
        this.dataSource = setupHikari(type, config);
        //check connection log it etc.
        initTables();
    }

    protected final void reportError(String func, Exception e) {
        Logger.sendConsoleMessage("Failed to "+func, Logger.LogLevel.ERROR);
        Logger.logError(e);
    }

    public final Type getType() {
        return type;
    }

    private HikariDataSource setupHikari(Type type, Config config) {
        HikariConfig hikariConfig = new HikariConfig();
        // Build JDBC URL based on database type
        String jdbcUrl = switch (type) {
            case H2 -> "jdbc:h2:" + IsekaiAuctions.getInstance().getDataFolder().getAbsolutePath() + "/database;AUTO_SERVER=TRUE";
            case MYSQL, MARIADB, POSTGRESQL -> String.format("jdbc:%s://%s/%s", type.name().toLowerCase(), config.address, config.database);
        };


        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setDriverClassName(type.getDriver());

        hikariConfig.setUsername(config.username);
        hikariConfig.setPassword(config.password);

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(config.maximum_pool_size != null ? config.maximum_pool_size : 20);
        hikariConfig.setMinimumIdle(config.minimum_idle != null ? config.minimum_idle : 10);
        hikariConfig.setMaxLifetime(config.maximum_lifetime != null ? config.maximum_lifetime : 1800000);
        hikariConfig.setConnectionTimeout(config.connection_timeout != null ? config.connection_timeout : 5000);
        hikariConfig.setIdleTimeout(config.idle_timeout != null ? config.idle_timeout : 600000);

        // Performance optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", true);
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", 250);
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);

        // Connection test query
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // Pool name for debugging
        hikariConfig.setPoolName("IsekaiAuctions-"+type.name()+"-Pool");

        return new HikariDataSource(hikariConfig);
    }

    protected final void runTask(Runnable task) {
        IsekaiAuctions.getExecutor().execute(task);
    }

    protected abstract void initTables();

    protected final Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected final void load(ResultSet set) throws SQLException {
        long endTime = set.getLong(7);
        UUID uuid = UUID.fromString(set.getString(1));

        long daysTime = IsekaiAuctions.getInstance().configFile.getInt("settings.purge_auctions", 0) * 86400L;
        if (daysTime > 0) {
            daysTime += endTime;
            if (ZonedDateTime.now().toInstant().getEpochSecond() > daysTime) {
                deleteAuction(uuid);
                AuctionCache.removeUpdatingAuction(uuid);
                return;
            }
        }

        UUID owner = UUID.fromString(set.getString(2));
        String displayName = set.getString(3);

        ItemStack item = ItemStack.deserializeBytes(set.getBytes(4));
        double price = set.getDouble(6);
        AuctionType type = AuctionType.valueOf(set.getString(8));
        boolean isClaimed = set.getBoolean(9);
        String economy = set.getString(10);

        Auction auction = new Auction(uuid, owner, displayName, item, price, type, economy, endTime, isClaimed);
        if (auction.getAuctionCategory().isEmpty()) {
            AuctionCache.removeUpdatingAuction(uuid);
            return;
        }

        String bids = set.getString(5);
        if (bids != null) {
            List<PlayerBid> playerBids = new ArrayList<>();

            String[] args = bids.split(",,");
            for (String arg : args) {
                String[] newArgs = arg.split(",");
                if (newArgs.length < 6)
                    continue;

                UUID orderUUID = UUID.fromString(newArgs[0]);
                UUID ownerUUID = UUID.fromString(newArgs[1]);
                String ownerDisplayName = newArgs[2];
                double bidPrice = Double.parseDouble(newArgs[3]);
                long bidTime = Long.parseLong(newArgs[4]);
                boolean collected = !type.equals(AuctionType.NORMAL) || Boolean.parseBoolean(newArgs[5]);

                PlayerBid playerBid = new PlayerBid(orderUUID, ownerUUID, ownerDisplayName, bidPrice, bidTime,
                        collected);
                playerBids.add(playerBid);
            }

            auction.getAuctionBids().addPlayerBids(playerBids);
        }

        AuctionCache.addAuction(auction);
        AuctionCache.removeUpdatingAuction(auction.getAuctionUUID());
    }

    public abstract void deleteAuction(UUID uuid);
    public abstract void deleteItem(UUID uuid);

    public abstract void loadAuction(UUID uuid);
    public abstract boolean loadAuctions();

    public abstract void loadStat(UUID uuid);
    public abstract void loadItem(UUID uuid);

    public abstract void saveAuction(Auction auction);
    public abstract void saveAuctions();

    public abstract void saveItem(UUID uuid, ItemStack item);
    public abstract void saveStats(PlayerStats stats);

    public final boolean status() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    public final void shutdown() {
        if (dataSource != null && !dataSource.isClosed())
            dataSource.close();
    }

    @Builder
    @Getter
    public static class Config {
        final private String database, username, address, password;
        final private Integer maximum_pool_size, minimum_idle, maximum_lifetime, connection_timeout, idle_timeout;
        public static Config fromSection(ConfigurationSection section) {
            Config.ConfigBuilder builder = Config.builder();
            builder.address(section.getString("address"));
            builder.database(section.getString("database"));
            builder.username(section.getString("username"));
            builder.password(section.getString("password"));
            ConfigurationSection pool_settings = section.getConfigurationSection("pool_settings");
            builder.maximum_pool_size(pool_settings.getInt("maximum_pool_size"));
            builder.minimum_idle(pool_settings.getInt("minimum_idle"));
            builder.maximum_lifetime(pool_settings.getInt("maximum_lifetime"));
            builder.connection_timeout(pool_settings.getInt("connection_timeout"));
            builder.idle_timeout(pool_settings.getInt("idle_timeout"));
            return builder.build();
        }
    }

    @AllArgsConstructor
    @Getter
    public enum Type {
        H2("org.h2.Driver"),
        MYSQL("com.mysql.cj.jdbc.Driver"),
        MARIADB("org.mariadb.jdbc.Driver"),
        POSTGRESQL("org.postgresql.Driver");
        public final String driver;
        public static Optional<Type> match(String value) {
            return Arrays.stream(Type.values()).filter(t -> t.name().equalsIgnoreCase(value)).findFirst();
        }
    }
}