package me.verschuls.isekaiauctions.addons.multiserver.redis;

import lombok.AccessLevel;
import lombok.Getter;
import me.verschuls.auctionsapi.cache.AuctionCache;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedisManager {

    @Getter(AccessLevel.PACKAGE)
    private static String channel;

    @Getter(AccessLevel.PACKAGE)
    private final JedisPool client;

    @Getter(AccessLevel.PACKAGE)
    private static final Set<String> messages = ConcurrentHashMap.newKeySet();

    private RedisThread thread;

    private static final RedisManager instance = new RedisManager();

    public static RedisManager get() {
        return instance;
    }

    private RedisManager() {
        FileConfiguration config = IsekaiAuctions.getInstance().configFile;
        channel = config.getString("redis.channel");
        this.client = new JedisPool(getJedisPoolConfig(), String.format("redis://%s:%s@%s:%d",
                config.getString("redis.user", ""), config.getString("redis.password", ""), config.getString("redis.host"), config.getInt("redis.port")));


        Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eRedis is connecting...", Logger.LogLevel.INFO);
        try (Jedis jedis = client.getResource()) {
            if (!jedis.ping().equalsIgnoreCase("pong")) {
                throw new IllegalStateException("Redis ping failed");
            }
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &aRedis successfully connected!", Logger.LogLevel.INFO);
            subscribeToChannel();
        } catch (Exception e) {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cRedis Connection Failed", Logger.LogLevel.ERROR);
            Logger.sendConsoleMessage("&8[&bIsekaiAuction&8] &cPlugin will be shutdown!", Logger.LogLevel.ERROR);
            Bukkit.getPluginManager().disablePlugin(IsekaiAuctions.getInstance());
        }
    }

    private JedisPoolConfig getJedisPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(15);
        config.setMinIdle(5);
        config.setMaxIdle(7);
        config.setMaxWait(Duration.ofSeconds(5));
        config.setTestOnBorrow(true);
        return config;
    }

    private void subscribeToChannel() {
        if (channel == null || channel.isEmpty()) {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cChannel is invalid, can't subscribe!", Logger.LogLevel.ERROR);
            Logger.sendConsoleMessage("&8[&bIsekaiAuction&8] &cPlugin will be shutdown!", Logger.LogLevel.ERROR);
            Bukkit.getPluginManager().disablePlugin(IsekaiAuctions.getInstance());
            return;
        }
        thread = new RedisThread();
        thread.start();
    }

    public static void onMessage(String messageChannel, String text) {
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




    public boolean isAuctionMessagePublished(String uuid) {
        if (client == null) return false;
        try (Jedis jedis = client.getResource()) {
            long count = jedis.scard(uuid);
            return count > 0L;
        } catch (Exception e) {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to check if messages are published for UUID &f" + uuid + " &cin Redis: &f" + e.getMessage(), Logger.LogLevel.WARN);
            return true;
        }
    }

    public void removeAuctionMessage(String uuid, String text) {
        if (client == null) return;
        IsekaiAuctions.getExecutor().execute(()->{
            try (Jedis jedis = client.getResource()) {
                long srem = jedis.srem(uuid, text);
                if (srem == 0) Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to remove message &f" + text + " &cfrom Redis", Logger.LogLevel.ERROR);
            } catch (Exception e) {
                Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to remove message &f" + text + " &cfrom Redis", Logger.LogLevel.ERROR);
                Logger.logError(e);
            }
        });
    }

    public void publish(String text) {
        if (client == null) return;
        IsekaiAuctions.getExecutor().execute(()->{
            try (Jedis jedis = client.getResource()) {
                long subs = jedis.publish(channel, text);
                if (subs >= 0) messages.add(text);
                else Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to publish &f" + text + " &cmessage to Redis", Logger.LogLevel.WARN);
            } catch (Exception e) {
                Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to publish &f" + text + " &cmessage to Redis", Logger.LogLevel.ERROR);
                Logger.logError(e);
            }
        });
    }

    private boolean acquireLock(String lockKey, String lockValue, int expiration) {
        if (client == null) return false;
        try (Jedis jedis = client.getResource()) {
            String result = jedis.set(lockKey, lockValue, SetParams.setParams().nx().px(expiration));
            if (result == null) return false;
            return result.equals("OK");
        } catch (Exception e) {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cUnexpected Redis error: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            return false;
        }
    }

    private void releaseLock(boolean isLockAcquired, String lockKey, String lockValue) {
        if (client == null) return;
        if (!isLockAcquired) return;
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        IsekaiAuctions.getExecutor().execute(()->{
            try (Jedis jedis = client.getResource()) {
                jedis.eval(script, List.of(lockKey), List.of(lockValue));
            } catch (Exception e) {
                Logger.logError(e);
            }
        });
    }

    public boolean publish(String uuid, String text) {
        if (client == null) return false;
        String lockKey = "lock:" + uuid;
        String lockValue = UUID.randomUUID().toString();
        boolean isLockAcquired = false;
        try (Jedis jedis = client.getResource()) {
            int lockExpiration = 1000;
            int uuidExpiration = 15;
            isLockAcquired = this.acquireLock(lockKey, lockValue, lockExpiration);
            if (!isLockAcquired) {
                Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to acquire lock for UUID &f" + uuid, Logger.LogLevel.ERROR);
                return false;
            }
            messages.add(text);
            String lua = """
                if redis.call('exists', KEYS[1]) == 0 then
                    redis.call('sadd', KEYS[1], ARGV[1])
                    redis.call('publish', KEYS[2], ARGV[1])
                    redis.call('expire', KEYS[1], ARGV[2])
                    return 1
                else
                    return 0
                end
                """;
            Long result = (Long)jedis.eval(lua, List.of(uuid, channel), List.of(text, String.valueOf(uuidExpiration)));
            if (result == 0L) Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eUUID &f" + uuid + " &ealready exists or failed to publish.", Logger.LogLevel.ERROR);
            return result == 1L;
        } catch (Exception e) {
            Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cFailed to publish &f" + text + " &cmessage to Redis: &f" + e.getMessage(), Logger.LogLevel.ERROR);
            return false;
        } finally {
            this.releaseLock(isLockAcquired, lockKey, lockValue);
        }
    }


    public void shutdown() {
        if (thread != null) thread.shutdown();
        if (client != null) client.close();
        Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eRedis shut down clean!", Logger.LogLevel.INFO);
    }
}
