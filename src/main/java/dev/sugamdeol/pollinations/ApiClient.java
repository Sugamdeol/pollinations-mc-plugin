package dev.sugamdeol.pollinations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Pollinations API client (gen.pollinations.ai).
 */
public final class ApiClient {

    private static final String BASE = "https://gen.pollinations.ai";
    private final PollinationsPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final Gson gson = new Gson();

    ApiClient(PollinationsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One chat message: role + content. */
    public record Msg(String role, String content) {
        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("role", role);
            o.addProperty("content", content);
            return o;
        }
    }

    /**
     * Calls POST /v1/chat/completions and returns the assistant reply text.
     * system may be null. history may be empty.
     */
    public String chat(String system, List<Msg> history, String userMessage) throws IOException, InterruptedException {
        JsonArray messages = new JsonArray();
        if (system != null && !system.isBlank()) {
            messages.add(new Msg("system", system).toJson());
        }
        for (Msg m : history) {
            messages.add(m.toJson());
        }
        messages.add(new Msg("user", userMessage).toJson());

        JsonObject body = new JsonObject();
        body.addProperty("model", plugin.textModel());
        body.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + plugin.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("API error " + resp.statusCode() + ": " + shorten(resp.body(), 300));
        }
        JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
        return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    /**
     * Downloads a generated image from GET /image/{prompt} into a temp file
     * and returns the file path.
     */
    public Path image(String prompt) throws IOException, InterruptedException {
        String model = URLEncoder.encode(plugin.imageModel(), StandardCharsets.UTF_8);
        String enc = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        int size = plugin.imageSize();
        String url = BASE + "/image/" + enc
                + "?model=" + model
                + "&width=" + size + "&height=" + size
                + "&key=" + URLEncoder.encode(plugin.apiKey(), StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();

        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IOException("Image API error " + resp.statusCode() + ": "
                    + shorten(new String(resp.body(), StandardCharsets.UTF_8), 300));
        }
        String ct = resp.headers().firstValue("Content-Type").orElse("");
        if (!ct.startsWith("image/")) {
            throw new IOException("Image API returned " + ct + " instead of an image");
        }
        Path dir = plugin.getDataFolder().toPath().resolve("images");
        Files.createDirectories(dir);
        Path file = dir.resolve("img-" + System.currentTimeMillis() + ".jpg");
        Files.write(file, resp.body());
        return file;
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** Utility used by the command to split long replies into chat-sized pieces. */
    public static List<String> splitForChat(String text, int maxLen) {
        List<String> parts = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String line = paragraph.trim();
            if (line.isEmpty()) continue;
            while (line.length() > maxLen) {
                int cut = line.lastIndexOf(' ', maxLen);
                if (cut <= 0) cut = maxLen;
                parts.add(line.substring(0, cut).trim());
                line = line.substring(cut).trim();
            }
            if (!line.isEmpty()) parts.add(line);
        }
        return parts;
    }
}