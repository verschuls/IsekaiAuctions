package me.verschuls.isekaiauctions.addons.multiserver.redis;

import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.atomic.AtomicBoolean;

public class RedisThread extends Thread {

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final PubSub sub;

    RedisThread() {
        super("IsekaiAuction-Redis-Subscriber");
        setDaemon(true);
        sub = new PubSub();
    }

    @Override
    public void run() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try (Jedis jedis = RedisManager.get().getClient().getResource()) {
                Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eSubscribing to Redis channel...", Logger.LogLevel.INFO);
                jedis.getClient().setSoTimeout(0);
                jedis.subscribe(sub, RedisManager.get().getChannel());
            } catch (Exception e) {
                if (running.get()) {
                    Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &cRedis connection lost, reconnecting in 5s...", Logger.LogLevel.WARN);
                    Logger.logError(e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        Logger.sendConsoleMessage("&8[&bIsekaiAuctions&8] &eRedis subscriber thread stopped", Logger.LogLevel.INFO);
    }

    public void shutdown() {
        running.set(false);
        sub.unsubscribe();
        interrupt();
    }

    public static class PubSub extends JedisPubSub {
        @Override
        public void onMessage(String messageChannel, String text) {
            IsekaiAuctions.getExecutor().execute(()->RedisManager.onMessage(messageChannel, text));
        }
    }
}
