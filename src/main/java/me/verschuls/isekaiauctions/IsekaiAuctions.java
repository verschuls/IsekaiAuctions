package me.verschuls.isekaiauctions;

import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.luckperms.LuckPermsPermissionResolver;
import dev.rollczi.litecommands.message.LiteMessages;
import lombok.Getter;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.auctionsapi.cache.CategoryCache;
import me.verschuls.isekaiauctions.addons.DiscordWebhook;
import me.verschuls.isekaiauctions.addons.HeadDatabase;
import me.verschuls.isekaiauctions.addons.Placeholders;
import me.verschuls.isekaiauctions.addons.multiserver.MultiServerManager;
import me.verschuls.isekaiauctions.addons.mute.AdvancedBan;
import me.verschuls.isekaiauctions.addons.mute.BanManager;
import me.verschuls.isekaiauctions.addons.mute.LiteBans;
import me.verschuls.isekaiauctions.addons.mute.MuteManager;
import me.verschuls.isekaiauctions.commands.*;
import me.verschuls.isekaiauctions.database.DatabaseManager;
import me.verschuls.isekaiauctions.handlers.BlacklistHandler;
import me.verschuls.isekaiauctions.handlers.DataHandler;
import me.verschuls.isekaiauctions.handlers.MenuHandler;
import me.verschuls.isekaiauctions.inventoryapi.HInventory;
import me.verschuls.isekaiauctions.inventoryapi.inventory.InventoryAPI;
import me.verschuls.isekaiauctions.listeners.PlayerListeners;
import me.verschuls.isekaiauctions.managers.AuctionType;
import me.verschuls.isekaiauctions.managers.CustomItem;
import me.verschuls.isekaiauctions.managers.Economy;
import me.verschuls.isekaiauctions.managers.SortType;
import me.verschuls.isekaiauctions.menus.InputMenu;
import me.verschuls.isekaiauctions.others.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ConstantConditions")
public class IsekaiAuctions extends JavaPlugin {
    @Getter private static IsekaiAuctions instance;

    public YamlConfiguration economyFile;
    public YamlConfiguration menusFile;
    public YamlConfiguration messagesFile;
    public YamlConfiguration categoriesFile;
    public YamlConfiguration itemsFile;
    public FileConfiguration configFile;

    public DataHandler dataHandler;
    public BlacklistHandler blacklistHandler;
    public MenuHandler menuHandler;

    public Map<String, Economy> economies = new HashMap<>();
    public Map<String, ItemStack> normalItems = new HashMap<>();
    public Map<String, CustomItem> customItems = new HashMap<>();

    public DatabaseManager databaseManager;
    public MuteManager muteManager;

    public NumberFormat numberFormat;
    public TimeFormat timeFormat;
    public InputMenu inputMenu;

    public boolean placeholderApi = false;
    public MultiServerManager multiServerManager;
    public HeadDatabase headDatabase;
    public DiscordWebhook discordWebhook;

    public String returnCategory = "";
    public double fileVersions = 1.1;
    public int version;
    public boolean locked = false;
    public boolean loaded = false;
    public boolean disabled = false;
    public boolean converting = false;

    public AuctionType auctionType;
    public SortType sortType;
    public String rarityType;
    public String category;
    public AuctionType createType;
    public double createPrice;
    public int createTime;
    public Economy createEconomy;

    @Getter
    private static BukkitExecutor executor;

    private LiteCommands<CommandSender> liteCommands;

    @Override
    public void onLoad() {
        executor = new BukkitExecutor(this);
    }

    public void registerCommands() {
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), IsekaiAuctions.getInstance());

        this.liteCommands = LiteBukkitFactory.builder(this)
                .permissionResolver(LuckPermsPermissionResolver.blocking())
                .commands(new AuctionCommand(), new AuctionAdminCommand(), new IsekaiAuctionsCommand())
                .invalidUsage(new UsageHandler())
                .missingPermission(new PermissionHandler())
                .message(LiteMessages.COMMAND_COOLDOWN, new CommandCooldown())
                .context(Player.class, new PlayerProvider())
                .build();
    }

    @Override
    public void onEnable() {
        this.version = Integer.parseInt(Bukkit.getBukkitVersion().substring(2, 4).replace(".", ""));
        instance = this;

        saveDefaultConfig();
        if (!getConfig().getBoolean("settings.enable_plugin", true)) {
            Logger.sendConsoleMessage("Plugin is disabled because enable_plugin setting is false in config!", Logger.LogLevel.INFO);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.dataHandler = new DataHandler();
        if (!this.dataHandler.load()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        CategoryCache.loadCategories();

        long time = System.currentTimeMillis();
        if (!this.databaseManager.loadAuctions()) {
            Logger.sendConsoleMessage("There is a problem in database setup! Plugin is disabling...", Logger.LogLevel.ERROR);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.databaseManager.loadStat(player.getUniqueId());
            this.databaseManager.loadItem(player.getUniqueId());
        }

        Logger.sendConsoleMessage("Database successfully loaded! Took &f" + (System.currentTimeMillis() - time) + "ms %level_color%to complete!", Logger.LogLevel.INFO);

        loadAddons();
        registerCommands();
        InventoryAPI.setup(IsekaiAuctions.this);

        if (this.configFile.getBoolean("settings.anti_lag.server.enabled"))
            TaskUtils.runTimerAsync(new ServerTps(), 100L, 1L);

        Logger.sendConsoleMessage("Your server is running on &f1." + this.version + "%level_color%.", Logger.LogLevel.INFO);
        Logger.sendConsoleMessage("Plugin is enabled! Plugin Version: &fv" + getDescription().getVersion(), Logger.LogLevel.INFO);
    }

    @Override
    public void onDisable() {
        this.disabled = true;
        if (this.liteCommands != null)
            liteCommands.unregister();
        if (this.loaded)
            IsekaiAuctions.getInstance().databaseManager.shutdown();
        if (this.multiServerManager != null)
            multiServerManager.shutDown();
        for (Player player : Bukkit.getOnlinePlayers())
            if (InventoryAPI.hasInventory(player))
                player.closeInventory();

        Logger.sendConsoleMessage("Plugin is disabled!", Logger.LogLevel.WARN);
    }

    public void loadAddons() {
        ConfigurationSection addons = this.configFile.getConfigurationSection("addons");
        if (addons == null)
            return;

        if (Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            this.headDatabase = new HeadDatabase();
            Bukkit.getPluginManager().registerEvents(this.headDatabase, this);

            Logger.sendConsoleMessage("Enabled &fHeadDatabase %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderApi = true;
            new Placeholders().register();
            Logger.sendConsoleMessage("Enabled &fPlaceholderAPI %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("BanManager")) {
            this.muteManager = new BanManager();
            Logger.sendConsoleMessage("Enabled &fBanManager %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("AdvancedBan")) {
            this.muteManager = new AdvancedBan();
            Logger.sendConsoleMessage("Enabled &fAdvancedBan %level_color%support!", Logger.LogLevel.INFO);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("LiteBans")) {
            this.muteManager = new LiteBans();
            Logger.sendConsoleMessage("Enabled &fLiteBans %level_color%support!", Logger.LogLevel.INFO);
        }

        if (addons.getBoolean("discord.enabled") && !addons.getString("discord.webhook_url", "").isEmpty()) {
            this.discordWebhook = new DiscordWebhook(addons.getString("discord.webhook_url"));
            Logger.sendConsoleMessage("Enabled &fDiscord Webhook %level_color%support!", Logger.LogLevel.INFO);
        }
    }

    public void reload() {
        Bukkit.getServer().getOnlinePlayers().forEach(player -> {
            HInventory gui = InventoryAPI.getInventory(player);
            if (gui != null)
                player.closeInventory();
        });

        reloadConfig();
        this.dataHandler.load();
        CategoryCache.loadCategories();
    }
}