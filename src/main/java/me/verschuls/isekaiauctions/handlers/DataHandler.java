package me.verschuls.isekaiauctions.handlers;

import me.verschuls.auctionsapi.cache.EnchantCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.configupdater.ConfigUpdater;
import me.verschuls.isekaiauctions.database.*;
import me.verschuls.isekaiauctions.managers.AuctionType;
import me.verschuls.isekaiauctions.managers.CustomItem;
import me.verschuls.isekaiauctions.managers.Economy;
import me.verschuls.isekaiauctions.managers.SortType;
import me.verschuls.isekaiauctions.menus.InputMenu;
import me.verschuls.isekaiauctions.others.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class DataHandler {
    private final File log = new File(IsekaiAuctions.getInstance().getDataFolder(), "logs.txt");
    private final boolean logEnabled = IsekaiAuctions.getInstance().getConfig().getBoolean("settings.enable_log", true);
    private final boolean debugEnabled = IsekaiAuctions.getInstance().getConfig().getBoolean("settings.enable_debug", false);

    public void debug(String message) {
        if (this.debugEnabled)
            Logger.sendConsoleMessage(message, Logger.LogLevel.DEBUG);
    }

    public void writeToLog(String inject) {
        if (!logEnabled)
            return;

        TaskUtils.runAsync(() -> {
            DateFormat simple = new SimpleDateFormat("[MM/dd/yyyy kk:mm:ss] ");
            try {
                FileWriter fileWriter = new FileWriter(this.log, true);

                PrintWriter printWriter = new PrintWriter(fileWriter);
                printWriter.println(simple.format(new Date())+Utils.strip(inject));
                printWriter.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean loadDefaultSettings() {
        IsekaiAuctions.getInstance().rarityType = IsekaiAuctions.getInstance().configFile.getString("settings.default_rarity", "all");
        IsekaiAuctions.getInstance().auctionType = AuctionType.valueOf(IsekaiAuctions.getInstance().configFile.getString("settings.default_filter_type", "ALL").toUpperCase(Locale.ENGLISH));
        IsekaiAuctions.getInstance().sortType = SortType.valueOf(IsekaiAuctions.getInstance().configFile.getString("settings.default_sort_type", "HIGHEST_PRICE").toUpperCase(Locale.ENGLISH));
        IsekaiAuctions.getInstance().category = IsekaiAuctions.getInstance().configFile.getString("settings.default_category", "weapons");
        IsekaiAuctions.getInstance().createType = AuctionType.valueOf(IsekaiAuctions.getInstance().configFile.getString("settings.default_type", "BIN").toUpperCase(Locale.ENGLISH));
        IsekaiAuctions.getInstance().createPrice = IsekaiAuctions.getInstance().configFile.getDouble("settings.default_price", 500);
        IsekaiAuctions.getInstance().createTime = IsekaiAuctions.getInstance().configFile.getInt("settings.default_duration", 21600);
        IsekaiAuctions.getInstance().createEconomy = IsekaiAuctions.getInstance().economies.get(IsekaiAuctions.getInstance().configFile.getString("settings.default_economy"));

        if (IsekaiAuctions.getInstance().createEconomy == null) {
            Logger.sendConsoleMessage("You need to setup the economy system in the economy.yml to use the plugin efficient!", Logger.LogLevel.WARN);
            String type = IsekaiAuctions.getInstance().configFile.getString("economy.type");
            if (type != null && !type.isEmpty()) {
                IsekaiAuctions.getInstance().economies = new HashMap<>();
                new Economy(type);
            }
            else {
                List<Economy> economies = IsekaiAuctions.getInstance().economies.values().stream().toList();
                if (economies.isEmpty())
                    return false;

                IsekaiAuctions.getInstance().createEconomy = economies.get(0);
            }
        }

        return IsekaiAuctions.getInstance().category != null;
    }

    public boolean load() {
        IsekaiAuctions.getInstance().configFile = IsekaiAuctions.getInstance().getConfig();
        IsekaiAuctions.getInstance().economyFile = getConfiguration("economy.yml");
        if (!setupEconomy()) {
            Logger.sendConsoleMessage("There is a problem in economy setup! Plugin is disabling...", Logger.LogLevel.ERROR);
            return false;
        }
        if (!loadDefaultSettings()) {
            Logger.sendConsoleMessage("There is a problem in default category setting (config.yml -> default_category) or economy (economy.yml)! Plugin is disabling...", Logger.LogLevel.ERROR);
            return false;
        }

        ConfigurationSection returnSection = IsekaiAuctions.getInstance().configFile.getConfigurationSection("settings.return_category");
        if (returnSection != null && returnSection.getBoolean("enabled"))
            IsekaiAuctions.getInstance().returnCategory = returnSection.getString("category", "miscellaneous");

        checkOldFiles();
        createDefaultFiles();

        setupDatabase();
        IsekaiAuctions.getInstance().menuHandler = new MenuHandler();
        IsekaiAuctions.getInstance().blacklistHandler = new BlacklistHandler();
        IsekaiAuctions.getInstance().numberFormat = new NumberFormat();
        IsekaiAuctions.getInstance().timeFormat = new TimeFormat();
        IsekaiAuctions.getInstance().inputMenu = new InputMenu();

        registerItems();
        loadCustomItems();
        EnchantCache.loadEnchants();

        return true;
    }

    public void loadCustomItems() {
        ConfigurationSection section = IsekaiAuctions.getInstance().itemsFile.getConfigurationSection("custom_items");
        if (section == null)
            return;

        Set<String> keys = section.getKeys(false);
        if (keys.isEmpty())
            return;

        for (String key : keys)
            new CustomItem(key);
    }

    public void checkOldFiles() {
        double currentVersions = IsekaiAuctions.getInstance().getConfig().getDouble("file_versions", 1.0);
        if (IsekaiAuctions.getInstance().fileVersions > currentVersions) {
            long start = System.currentTimeMillis();

            File messagesFile = new File(IsekaiAuctions.getInstance().getDataFolder(), "messages.yml");
            if (messagesFile.exists()) {
                try {
                    File oldBackupFile = new File(IsekaiAuctions.getInstance().getDataFolder() + File.separator + "backups", "messages_backup.yml");
                    if (!oldBackupFile.exists()) {
                        new File(IsekaiAuctions.getInstance().getDataFolder() + File.separator + "backups").mkdirs();

                        Files.copy(Paths.get(messagesFile.getPath()), Paths.get(IsekaiAuctions.getInstance().getDataFolder() + File.separator + "backups" + File.separator + "messages_backup.yml"));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            File configFile = new File(IsekaiAuctions.getInstance().getDataFolder(), "config.yml");
            if (configFile.exists()) {
                try {
                    File oldBackupFile = new File(IsekaiAuctions.getInstance().getDataFolder() + File.separator + "backups", "config_backup.yml");
                    if (!oldBackupFile.exists()) {
                        new File(IsekaiAuctions.getInstance().getDataFolder() + File.separator + "backups").mkdirs();

                        Files.copy(Paths.get(configFile.getPath()), Paths.get(IsekaiAuctions.getInstance().getDataFolder() + File.separator + "backups" + File.separator + "config_backup.yml"));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                ConfigUpdater.update(IsekaiAuctions.getInstance(), "messages.yml", messagesFile);
                ConfigUpdater.update(IsekaiAuctions.getInstance(), "config.yml", configFile);

                /*
                Arrays.asList(
                                "normal_auction.price_fees",
                                "bin_auction.price_fees",
                                "permissions.category.category_list",
                                "permissions.item.item_list",
                                "time_format.convert_times",
                                "player_limits.bid_limit.permissions",
                                "player_limits.duration_limit.permissions",
                                "player_limits.auction_limit.permissions",
                                "player_limits.price_limit.permissions")
                 */
            } catch (IOException e) {
                e.printStackTrace();
            }

            Logger.sendConsoleMessage("It looks like you are using old versions' files! &7(%level_color%Current/New Version: &f" + currentVersions + "/" + IsekaiAuctions.getInstance().fileVersions + "&7)", Logger.LogLevel.WARN);
            Logger.sendConsoleMessage("Config and messages file is successfully updated! (Old files backed up)", Logger.LogLevel.INFO);
            Logger.sendConsoleMessage("Took &f" + (System.currentTimeMillis() - start) + "ms %level_color%to complete.", Logger.LogLevel.INFO);
        }
    }

    public YamlConfiguration getConfiguration(String name) {
        File file = new File(IsekaiAuctions.getInstance().getDataFolder(), name);
        try {
            if (!file.exists())
                IsekaiAuctions.getInstance().saveResource(name, false);

            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(file);

            return configuration;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void createDefaultFiles() {
        try {
            if (!this.log.exists())
                this.log.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }

        IsekaiAuctions.getInstance().itemsFile = getConfiguration("items.yml");
        IsekaiAuctions.getInstance().categoriesFile = getConfiguration("categories.yml");
        IsekaiAuctions.getInstance().messagesFile = getConfiguration("messages.yml");
        IsekaiAuctions.getInstance().menusFile = getConfiguration("menus.yml");
    }

    public void registerItems() {
        ConfigurationSection itemSection = IsekaiAuctions.getInstance().menusFile.getConfigurationSection("default_items");
        if (itemSection != null) {
            Set<String> keys = itemSection.getKeys(false);
            if (!keys.isEmpty())
                for (String entry : keys) {
                    Object object = itemSection.get(entry);
                    if (object instanceof ItemStack) {
                        IsekaiAuctions.getInstance().normalItems.put(entry, (ItemStack) object);
                        continue;
                    }

                    ItemStack item = Utils.createItemFromSection(itemSection.getConfigurationSection(entry), null);
                    if (item == null)
                        continue;

                    IsekaiAuctions.getInstance().normalItems.put(entry, item);
                }
        }
    }

    public void setupDatabase() {
        if (IsekaiAuctions.getInstance().databaseManager != null) return;
        ConfigurationSection section = IsekaiAuctions.getInstance().getConfig().getConfigurationSection("database");
        Database.Config config = Database.Config.fromSection(section);
        String method = IsekaiAuctions.getInstance().getConfig().getString("storage_method", "h2");
        Database database = switch (Database.Type.match(method).orElse(null)) {
            case H2 -> new H2Database();
            case MYSQL -> new MySQLDatabase(Database.Type.MYSQL ,config);
            case MARIADB -> new MariaDBDatabase(config);
            case POSTGRESQL -> new PostgreSQLDatabase(config);
            case null -> null;
        };
        if (database == null) {
            Logger.sendConsoleMessage("Couldn't find storage method &4\""+method+"\" &cplease check your configuration. &4Plugin will be disabled.", Logger.LogLevel.ERROR);
            IsekaiAuctions.disablePlugin();
            return;
        }
        IsekaiAuctions.getInstance().databaseManager = database;
    }

    private boolean setupEconomy() {
        Set<String> keys = IsekaiAuctions.getInstance().economyFile.getKeys(false);
        if (keys.isEmpty())
            return false;

        for (String key : keys) {
            ConfigurationSection section = IsekaiAuctions.getInstance().economyFile.getConfigurationSection(key);
            if (section == null)
                continue;

            new Economy(section);
        }

        return true;
    }

    public enum DBType {
        H2,
        MYSQL,
        MARIADB,
        POSTGRESQL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}