package org.geysermc.connector.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.geysermc.api.Geyser;
import org.geysermc.connector.GeyserConnector;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class SkinProvider {
    public static final Gson GSON = new GsonBuilder().create();
    public static final boolean ALLOW_THIRD_PARTY_CAPES = ((GeyserConnector)Geyser.getConnector()).getConfig().isAllowThirdPartyCapes();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(ALLOW_THIRD_PARTY_CAPES ? 21 : 14);

    public static final Skin EMPTY_SKIN = new Skin(-1, "");
    public static final byte[] STEVE_SKIN = new ProvidedSkin("bedrock/skin/skin_steve.png").getSkin();
    private static Map<UUID, Skin> cachedSkins = new ConcurrentHashMap<>();
    private static Map<UUID, CompletableFuture<Skin>> requestedSkins = new ConcurrentHashMap<>();

    public static final Cape EMPTY_CAPE = new Cape("", new byte[0], -1, true);
    private static Map<String, Cape> cachedCapes = new ConcurrentHashMap<>();
    private static Map<String, CompletableFuture<Cape>> requestedCapes = new ConcurrentHashMap<>();

    private static final int CACHE_INTERVAL = 8 * 60 * 1000; // 8 minutes

    public static boolean hasSkinCached(UUID uuid) {
        return cachedSkins.containsKey(uuid);
    }

    public static boolean hasCapeCached(String capeUrl) {
        return cachedCapes.containsKey(capeUrl);
    }

    public static Skin getCachedSkin(UUID uuid) {
        return cachedSkins.getOrDefault(uuid, EMPTY_SKIN);
    }

    public static Cape getCachedCape(String capeUrl) {
        return capeUrl != null ? cachedCapes.getOrDefault(capeUrl, EMPTY_CAPE) : EMPTY_CAPE;
    }

    public static CompletableFuture<SkinAndCape> requestSkinAndCape(UUID playerId, String skinUrl, String capeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            long time = System.currentTimeMillis();

            SkinAndCape skinAndCape = new SkinAndCape(
                    getOrDefault(requestSkin(playerId, skinUrl, false), EMPTY_SKIN, 5),
                    getOrDefault(requestCape(capeUrl, false), EMPTY_CAPE, 5)
            );

            Geyser.getLogger().debug("Took " + (System.currentTimeMillis() - time) + "ms for " + playerId);
            return skinAndCape;
        }, EXECUTOR_SERVICE);
    }

    public static CompletableFuture<Skin> requestSkin(UUID playerId, String textureUrl, boolean newThread) {
        if (textureUrl == null || textureUrl.isEmpty()) return CompletableFuture.completedFuture(EMPTY_SKIN);
        if (requestedSkins.containsKey(playerId)) return requestedSkins.get(playerId); // already requested

        if ((System.currentTimeMillis() - CACHE_INTERVAL) < cachedSkins.getOrDefault(playerId, EMPTY_SKIN).getRequestedOn()) {
            // no need to update, still cached
            return CompletableFuture.completedFuture(cachedSkins.get(playerId));
        }

        CompletableFuture<Skin> future;
        if (newThread) {
            future = CompletableFuture.supplyAsync(() -> supplySkin(playerId, textureUrl), EXECUTOR_SERVICE)
                    .whenCompleteAsync((skin, throwable) -> {
                        if (!cachedSkins.getOrDefault(playerId, EMPTY_SKIN).getTextureUrl().equals(textureUrl)) {
                            skin.updated = true;
                            cachedSkins.put(playerId, skin);
                        }
                        requestedSkins.remove(skin.getSkinOwner());
                    });
            requestedSkins.put(playerId, future);
        } else {
            Skin skin = supplySkin(playerId, textureUrl);
            future = CompletableFuture.completedFuture(skin);
            cachedSkins.put(playerId, skin);
        }
        return future;
    }

    public static CompletableFuture<Cape> requestCape(String capeUrl, boolean newThread) {
        if (capeUrl == null || capeUrl.isEmpty()) return CompletableFuture.completedFuture(EMPTY_CAPE);
        if (requestedCapes.containsKey(capeUrl)) return requestedCapes.get(capeUrl); // already requested

        boolean officialCape = capeUrl.startsWith("https://textures.minecraft.net");
        boolean validCache = (System.currentTimeMillis() - CACHE_INTERVAL) < cachedCapes.getOrDefault(capeUrl, EMPTY_CAPE).getRequestedOn();

        if ((cachedCapes.containsKey(capeUrl) && officialCape) || validCache) {
            // the cape is an official cape (static) or the cape doesn't need a update yet
            return CompletableFuture.completedFuture(cachedCapes.get(capeUrl));
        }

        CompletableFuture<Cape> future;
        if (newThread) {
            future = CompletableFuture.supplyAsync(() -> supplyCape(capeUrl), EXECUTOR_SERVICE)
                    .whenCompleteAsync((cape, throwable) -> {
                        cachedCapes.put(capeUrl, cape);
                        requestedCapes.remove(capeUrl);
                    });
            requestedCapes.put(capeUrl, future);
        } else {
            Cape cape = supplyCape(capeUrl); // blocking
            future = CompletableFuture.completedFuture(cape);
            cachedCapes.put(capeUrl, cape);
        }
        return future;
    }

    public static CompletableFuture<Cape> requestUnofficialCape(Cape officialCape, UUID playerId,
                                                                String username, boolean newThread) {
        if (officialCape.isFailed() && ALLOW_THIRD_PARTY_CAPES) {
            for (UnofficalCape cape : UnofficalCape.VALUES) {
                Cape cape1 = getOrDefault(
                        requestCape(cape.getUrlFor(playerId, username), newThread),
                        EMPTY_CAPE, 4
                );
                if (!cape1.isFailed()) {
                    return CompletableFuture.completedFuture(cape1);
                }
            }
        }
        return CompletableFuture.completedFuture(officialCape);
    }

    private static Skin supplySkin(UUID uuid, String textureUrl) {
        byte[] skin = EMPTY_SKIN.getSkinData();
        try {
            skin = requestImage(textureUrl, false);
        } catch (Exception ignored) {} // just ignore I guess
        return new Skin(uuid, textureUrl, skin, System.currentTimeMillis(), false);
    }

    private static Cape supplyCape(String capeUrl) {
        byte[] cape = new byte[0];
        try {
            cape = requestImage(capeUrl, true);
        } catch (Exception ignored) {} // just ignore I guess

        return new Cape(
                capeUrl,
                cape.length > 0 ? cape : EMPTY_CAPE.getCapeData(),
                System.currentTimeMillis(),
                cape.length == 0
        );
    }

    private static byte[] requestImage(String imageUrl, boolean cape) throws Exception {
        BufferedImage image = ImageIO.read(new URL(imageUrl));
        Geyser.getLogger().debug("Downloaded " + imageUrl);

        if (cape) {
            BufferedImage newImage = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);

            Graphics g = newImage.createGraphics();
            g.drawImage(image, 0, 0, 64, 32, null);
            g.dispose();
            image = newImage;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(image.getWidth() * 4 + image.getHeight() * 4);
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgba = image.getRGB(x, y);
                    outputStream.write((rgba >> 16) & 0xFF);
                    outputStream.write((rgba >> 8) & 0xFF);
                    outputStream.write(rgba & 0xFF);
                    outputStream.write((rgba >> 24) & 0xFF);
                }
            }
            image.flush();
            return outputStream.toByteArray();
        } finally {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
        }
    }

    public static <T> T getOrDefault(CompletableFuture<T> future, T defaultValue, int timeoutInSeconds) {
        try {
            return future.get(timeoutInSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return defaultValue;
    }

    @AllArgsConstructor
    @Getter
    public static class SkinAndCape {
        private Skin skin;
        private Cape cape;
    }

    @AllArgsConstructor
    @Getter
    public static class Skin {
        private UUID skinOwner;
        private String textureUrl;
        private byte[] skinData = STEVE_SKIN;
        private long requestedOn;
        private boolean updated;

        private Skin(long requestedOn, String textureUrl) {
            this.requestedOn = requestedOn;
            this.textureUrl = textureUrl;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class Cape {
        private String textureUrl;
        private byte[] capeData;
        private long requestedOn;
        private boolean failed;
    }

    /*
     * Sorted by 'priority'
     */
    @AllArgsConstructor
    @Getter
    public enum UnofficalCape {
        OPTIFINE("http://s.optifine.net/capes/%s.png", CapeUrlType.USERNAME),
        LABYMOD("http://capes.labymod.net/capes/%s.png", CapeUrlType.UUID_DASHED),
        FIVEZIG("http://textures.5zig.net/2/%s", CapeUrlType.UUID),
        MINECRAFTCAPES("https://www.minecraftcapes.co.uk/getCape/%s", CapeUrlType.UUID);

        public static final UnofficalCape[] VALUES = values();
        private String url;
        private CapeUrlType type;

        public String getUrlFor(String type) {
            return String.format(url, type);
        }

        public String getUrlFor(UUID uuid, String username) {
            return getUrlFor(toRequestedType(type, uuid, username));
        }

        public static String toRequestedType(CapeUrlType type, UUID uuid, String username) {
            switch (type) {
                case UUID: return uuid.toString().replace("-", "");
                case UUID_DASHED: return uuid.toString();
                default: return username;
            }
        }
    }

    public enum CapeUrlType {
        USERNAME,
        UUID,
        UUID_DASHED
    }
}
