package clre20.captureEgg.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 向 GitHub Releases 檢查新版本。
 * - 網路請求在單一背景執行緒執行，不會無限制建立執行緒
 * - 結果快取 10 分鐘，沒有定時輪詢
 * - 同時多個請求只會打一次 GitHub
 */
public final class UpdateChecker {

    public static final String REPO = "clre20/CaptureEgg";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String PAGE = "https://github.com/" + REPO + "/releases/latest";
    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int MAX_BODY = 512 * 1024;

    public enum Status { NEW_VERSION, UP_TO_DATE, DEV_BUILD, NO_RELEASE, FAILED }

    public record Result(Status status, String current, String latest, String url, String error) {
    }

    private final JavaPlugin plugin;
    private final String current;
    private final ExecutorService executor;
    private final HttpClient http;

    private Result cached;
    private long cachedAt;
    private CompletableFuture<Result> inFlight;
    private final List<Consumer<Result>> waiting = new ArrayList<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.current = strip(plugin.getPluginMeta().getVersion());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CaptureEgg-UpdateChecker");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String current() {
        return current;
    }

    /** 取得結果；callback 一律在主執行緒執行。只能從主執行緒呼叫。 */
    public void check(Consumer<Result> callback) {
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            callback.accept(cached);
            return;
        }
        waiting.add(callback);
        if (inFlight != null) {
            return;
        }
        inFlight = CompletableFuture.supplyAsync(this::fetch, executor);
        inFlight.whenComplete((result, err) -> {
            Result r = result != null ? result
                    : new Result(Status.FAILED, current, null, PAGE, err == null ? "unknown" : err.getMessage());
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> deliver(r));
        });
    }

    private void deliver(Result r) {
        inFlight = null;
        // 失敗不快取太久，避免 GitHub 暫時故障時十分鐘都查不到；但仍快取 1 分鐘防止洗 API
        cached = r;
        cachedAt = r.status() == Status.FAILED
                ? System.currentTimeMillis() - CACHE_MILLIS + TimeUnit.MINUTES.toMillis(1)
                : System.currentTimeMillis();
        List<Consumer<Result>> list = new ArrayList<>(waiting);
        waiting.clear();
        for (Consumer<Result> c : list) {
            try {
                c.accept(r);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("更新檢查回呼發生錯誤：" + e.getMessage());
            }
        }
    }

    private Result fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "CaptureEgg-UpdateChecker")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                return new Result(Status.NO_RELEASE, current, null, PAGE, null);
            }
            if (res.statusCode() != 200) {
                return new Result(Status.FAILED, current, null, PAGE, "HTTP " + res.statusCode());
            }
            String body = res.body();
            if (body == null || body.length() > MAX_BODY) {
                return new Result(Status.FAILED, current, null, PAGE, "回應過大或為空");
            }
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonElement tag = json.get("tag_name");
            if (tag == null || !tag.isJsonPrimitive()) {
                return new Result(Status.NO_RELEASE, current, null, PAGE, null);
            }
            String latest = strip(tag.getAsString());
            int cmp = compare(latest, current);
            Status status = cmp > 0 ? Status.NEW_VERSION : cmp < 0 ? Status.DEV_BUILD : Status.UP_TO_DATE;
            return new Result(status, current, latest, PAGE, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Status.FAILED, current, null, PAGE, "interrupted");
        } catch (Exception e) {
            return new Result(Status.FAILED, current, null, PAGE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        waiting.clear();
        inFlight = null;
    }

    /** 去掉 v / V 前綴與空白。 */
    public static String strip(String version) {
        if (version == null) {
            return "0";
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v;
    }

    /** 比較 x.y.z（忽略 -SNAPSHOT 等後綴；缺少的位數視為 0）。 */
    public static int compare(String a, String b) {
        String[] pa = mainPart(a).split("\\.");
        String[] pb = mainPart(b).split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            long x = i < pa.length ? number(pa[i]) : 0;
            long y = i < pb.length ? number(pb[i]) : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return 0;
    }

    private static String mainPart(String v) {
        String s = strip(v);
        int cut = s.indexOf('-');
        if (cut < 0) {
            cut = s.indexOf('+');
        }
        return cut >= 0 ? s.substring(0, cut) : s;
    }

    private static long number(String part) {
        int end = 0;
        while (end < part.length() && end < 9 && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Long.parseLong(part.substring(0, end));
    }
}
