package ru.voidrp.battlepass.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * HTTP client for syncing premium status with the FastAPI backend.
 * All calls are best-effort: failures are logged and local data is used as fallback.
 */
public final class BackendSyncClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final String gameAuthSecret;
    private final String serverSlug;
    private final Logger log;
    private final HttpClient http;

    public BackendSyncClient(String baseUrl, String gameAuthSecret, String serverSlug, Logger log) {
        this.baseUrl         = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.gameAuthSecret  = gameAuthSecret;
        this.serverSlug      = serverSlug == null ? "" : serverSlug;
        this.log             = log;
        this.http            = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /** Adds the optional X-Server-Slug header for explicit multi-server attribution. */
    private HttpRequest.Builder withSlug(HttpRequest.Builder rb) {
        if (!serverSlug.isBlank()) {
            rb.header("X-Server-Slug", serverSlug);
        }
        return rb;
    }

    /**
     * Fetch premium expiry for a player from the backend.
     * @return expiry epoch millis, or -1 on error, or 0 if no premium
     */
    public long fetchPremiumExpiry(String minecraftUuid) {
        try {
            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/premium/" + minecraftUuid))
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .GET())
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return 0L;
            if (resp.statusCode() != 200) {
                log.warning("[BattlePass] Backend premium check returned " + resp.statusCode() + " for " + minecraftUuid);
                return -1L;
            }
            JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
            boolean hasPremium = body.get("has_premium").getAsBoolean();
            if (!hasPremium) return 0L;
            String expiresAtStr = body.get("expires_at").getAsString();
            // ISO-8601 → epoch millis
            return java.time.Instant.parse(expiresAtStr).toEpochMilli();
        } catch (Exception e) {
            log.warning("[BattlePass] Backend unreachable for premium check (" + minecraftUuid + "): " + e.getMessage());
            return -1L;
        }
    }

    /**
     * Grant premium via backend.
     * @return expiry epoch millis, or -1 on error
     */
    public long grantPremium(String minecraftUuid, String minecraftNickname, int days, String note) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("minecraft_uuid",     minecraftUuid);
            body.addProperty("minecraft_nickname", minecraftNickname);
            body.addProperty("days", days);
            if (note != null) body.addProperty("note", note);

            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/premium/grant"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                log.warning("[BattlePass] Backend grant failed (" + resp.statusCode() + ") for " + minecraftUuid);
                return -1L;
            }
            JsonObject result = JsonParser.parseString(resp.body()).getAsJsonObject();
            String expiresAtStr = result.get("expires_at").getAsString();
            return java.time.Instant.parse(expiresAtStr).toEpochMilli();
        } catch (Exception e) {
            log.warning("[BattlePass] Backend grant error (" + minecraftUuid + "): " + e.getMessage());
            return -1L;
        }
    }

    /**
     * Revoke premium via backend.
     * @return true on success
     */
    public boolean revokePremium(String minecraftUuid) {
        try {
            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/premium/" + minecraftUuid))
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .DELETE())
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return true; // already gone
            return resp.statusCode() == 200 || resp.statusCode() == 204;
        } catch (Exception e) {
            log.warning("[BattlePass] Backend revoke error (" + minecraftUuid + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * Push player's current BP progress (level/xp/season) to the backend for profile display.
     */
    public void pushProgress(String minecraftUuid, String minecraftNickname, String season, int level, long xp, int prestige) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("minecraft_uuid", minecraftUuid);
            body.addProperty("minecraft_nickname", minecraftNickname);
            body.addProperty("season", season);
            body.addProperty("level", level);
            body.addProperty("xp", xp);
            body.addProperty("prestige", prestige);

            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/progress"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 204) {
                log.warning("[BattlePass] Backend progress push failed (" + resp.statusCode() + ") for " + minecraftUuid);
            }
        } catch (Exception e) {
            log.warning("[BattlePass] Backend progress push error (" + minecraftUuid + "): " + e.getMessage());
        }
    }

    /** Pushes a full reward-track snapshot JSON (built by BpTrackSync) for the WebGUI. */
    public void pushTrack(String jsonBody) {
        try {
            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/game-sync/battlepass/track"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 204) {
                log.warning("[BattlePass] Backend track push failed (" + resp.statusCode() + ")");
            }
        } catch (Exception e) {
            log.warning("[BattlePass] Backend track push error: " + e.getMessage());
        }
    }

    /** Pushes today's quest snapshot JSON (built by BpQuestSync) for the WebGUI. */
    public void pushQuests(String jsonBody) {
        try {
            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/game-sync/battlepass/quests"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 204) {
                log.warning("[BattlePass] Backend quests push failed (" + resp.statusCode() + ")");
            }
        } catch (Exception e) {
            log.warning("[BattlePass] Backend quests push error: " + e.getMessage());
        }
    }

    /** Push a reactive in-game notification to a player (shown in the HUD overlay). Blocking. */
    public void pushNotification(String nickname, String type, String title, String body,
                                 String icon, String accent,
                                 String actionType, String actionPayload, String actionLabel) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("minecraft_nickname", nickname);
            o.addProperty("type", type);
            o.addProperty("title", title);
            if (body != null) o.addProperty("body", body);
            if (icon != null) o.addProperty("icon", icon);
            if (accent != null) o.addProperty("accent", accent);
            if (actionType != null) o.addProperty("action_type", actionType);
            if (actionPayload != null) o.addProperty("action_payload", actionPayload);
            if (actionLabel != null) o.addProperty("action_label", actionLabel);

            HttpRequest req = withSlug(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/game-sync/notifications"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(o.toString())))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 201 && resp.statusCode() != 200 && resp.statusCode() != 404) {
                log.warning("[BattlePass] Notification push failed (" + resp.statusCode() + ") for " + nickname);
            }
        } catch (Exception e) {
            log.warning("[BattlePass] Notification push error (" + nickname + "): " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return gameAuthSecret != null && !gameAuthSecret.isBlank();
    }
}
