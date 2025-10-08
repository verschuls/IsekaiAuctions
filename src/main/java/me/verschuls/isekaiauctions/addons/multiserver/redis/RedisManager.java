package me.verschuls.isekaiauctions.addons.multiserver.redis;

import eu.decentsoftware.holograms.api.utils.scheduler.S;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.java.Log;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class RedisManager {


    private final String channel;

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;

    private final Set<String> messages = new HashSet<String>();

    RedisManager() {
        FileConfiguration config = IsekaiAuctions.getInstance().configFile;
        this.channel = config.getString("redis.channel");
        this.client = RedisClient.create(String.format("redis://:%s@%s:%d", config.getString("redis.password"), config.getString("redis.host"), config.getInt("redis.port")));
        this.connection = this.client.connect();
        this.pubSubConnection = this.client.connectPubSub();

        connection.async().ping().thenAccept(pong -> {
            Logger.sendConsoleMessage("&8[&bDeluxeAuctions Redis&8] &aRedis successfully connected!", Logger.LogLevel.INFO);
            subscribeToChannel();
        }).exceptionally(e -> {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cRedis Connection Failed: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            Logger.sendConsoleMessage("&8[&bIsekaiAuction&8] &cPlugin will be shutdown!", Logger.LogLevel.ERROR);
            Bukkit.getPluginManager().disablePlugin(IsekaiAuctions.getInstance());
            return null;
        });
    }

    private void subscribeToChannel() {
        if (channel == null || channel.isEmpty()) {
            Logger.sendConsoleMessage("&8[&bDeluxeAuctions Redis&8] &cChannel is invalid, can't subscribe!", Logger.LogLevel.ERROR);
            Logger.sendConsoleMessage("&8[&bIsekaiAuction&8] &cPlugin will be shutdown!", Logger.LogLevel.ERROR);
            Bukkit.getPluginManager().disablePlugin(IsekaiAuctions.getInstance());
            return;
        }
        pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String messageChannel, String text) {
                if (!messageChannel.equals(channel)) return;
                if (messages.contains(text)) {
                    messages.remove(text);
                    return;
                }
                if (text.startsWith("AUCTION"))
                    IsekaiAuctions.getExecutor().execute(()->
                            AuctionCache.addUpdatingAuction(UUID.fromString(text.split(":")[1])));

                IsekaiAuctions.getExecutor().execute(()->IsekaiAuctions.getInstance().multiServerManager.handleMessage(text));
            }
        });
    }


    public boolean isAuctionMessagePublished(String uuid) {
        if (connection == null) return false;
        return CompletableFuture.supplyAsync(()->{
            try {
                long count = connection.async().scard(uuid).get();
                return count > 0L;
            } catch (CancellationException | ExecutionException | InterruptedException e) {
                Logger.sendConsoleMessage("&8[&bDeluxeAuctions Redis&8] &cFailed to check if messages are published for UUID &f" + uuid + " &cin Redis: &f" + e.getMessage(), Logger.LogLevel.WARN);
                return true;
            }
        }, IsekaiAuctions.getExecutor()).exceptionallyAsync(e -> {
            Logger.sendConsoleMessage("&8[&dIsekaiAuctions&8] &cUnexpected Redis error: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            return false;
        }, IsekaiAuctions.getExecutor()).join();
    }

    public void removeAuctionMessage(String uuid, String text) {
        if (connection == null) return;
        connection.async().srem(uuid, text).exceptionallyAsync(e -> {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to remove message &f" + text + " &cfrom Redis: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            return 0L;
        }, IsekaiAuctions.getExecutor());
    }

    public void publish(String text) {
        if (connection == null) return;
        connection.async().publish(this.channel, text).thenAcceptAsync((e)->{
            this.messages.add(text);
            }, IsekaiAuctions.getExecutor()).exceptionallyAsync(e -> {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to publish &f" + text + " &cmessage to Redis: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            return null;
        }, IsekaiAuctions.getExecutor());
    }

    private boolean acquireLock(String lockKey, String lockValue, int expiration) {
        try {
            String result = connection.async().set(lockKey, lockValue, SetArgs.Builder.nx().px(expiration)).get();
            return result.equals("OK");
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseLock(boolean isLockAcquired, String lockKey, String lockValue) {
        if (!isLockAcquired) return;
        if (connection == null) return;
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        connection.async().eval(script, ScriptOutputType.INTEGER, lockKey, lockValue);
    }

    public boolean publish(String uuid, String text) {
        if (connection == null) return false;
        return CompletableFuture.supplyAsync(()->{
            String lockKey = "lock:" + uuid;
            String lockValue = UUID.randomUUID().toString();
            boolean isLockAcquired = false;
            try {
                int lockExpiration = 1000;
                int uuidExpiration = 15;
                isLockAcquired = this.acquireLock(lockKey, lockValue, lockExpiration);
                if (!isLockAcquired) {
                    Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to acquire lock for UUID &f" + uuid, Logger.LogLevel.ERROR);
                    return false;
                }
                this.messages.add(text);
                String lua = "if redis.call('exists', KEYS[1]) == 0 then " +
                        "redis.call('sadd', KEYS[1], ARGV[1]); " +
                        "redis.call('publish', KEYS[2], ARGV[1]); " +
                        "redis.call('expire', KEYS[1], " + uuidExpiration + "); " +
                        "return 1; else return 0; end";
                Long result = connection.sync().eval(lua, ScriptOutputType.INTEGER, new String[]{uuid, this.channel}, text, String.valueOf(uuidExpiration));
                if (result == 0L) Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eUUID &f" + uuid + " &ealready exists or failed to publish.", Logger.LogLevel.ERROR);
                return result == 1L;
            } catch (Exception e) {
                Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to publish &f" + text + " &cmessage to Redis: &f" + e.getMessage(), Logger.LogLevel.ERROR);
                this.releaseLock(isLockAcquired, lockKey, lockValue);
                return false;
            } finally {
                this.releaseLock(isLockAcquired, lockKey, lockValue);
            }
        }, IsekaiAuctions.getExecutor()).exceptionallyAsync(e->{
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cUnexpected Redis error: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            return false;
        }, IsekaiAuctions.getExecutor()).join();
    }


    public void shutdown() {
        connection.close();
        client.shutdown();

        pubSubConnection.async().unsubscribe(channel).thenRun(() -> {
            connection.close();
            pubSubConnection.close();
            client.shutdown();
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eRedis shut down clean!", Logger.LogLevel.INFO);
        });
    }
}
