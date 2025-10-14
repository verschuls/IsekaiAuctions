package me.verschuls.isekaiauctions.database;

import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MariaDBDatabase extends MySQLDatabase {

    public MariaDBDatabase(Config config) {
        super(Type.MARIADB, config);
    }

    @Override
    protected void initTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS auctions (
                uuid UUID PRIMARY KEY,
                owner UUID NOT NULL,
                display_name TEXT,
                item BLOB NOT NULL,
                bids TEXT,
                price DOUBLE NOT NULL,
                end_time BIGINT,
                type ENUM("ALL", "BIN", "NORMAL") NOT NULL,
                claimed BOOLEAN NOT NULL DEFAULT FALSE,
                economy TINYTEXT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_items (
              uuid UUID PRIMARY KEY,
              create_item BLOB
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
            """);

            // Player stats table
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid UUID PRIMARY KEY,
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
        """);


        } catch (SQLException e) {
            Logger.sendConsoleMessage("Failed to initialize tables", Logger.LogLevel.ERROR);
            Logger.logError(e);
            IsekaiAuctions.disablePlugin();
        }
    }
}