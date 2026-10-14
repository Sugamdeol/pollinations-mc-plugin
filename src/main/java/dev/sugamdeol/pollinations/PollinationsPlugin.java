package dev.sugamdeol.pollinations;

import org.bukkit.plugin.java.JavaPlugin;

public final class PollinationsPlugin extends JavaPlugin {

    private ApiClient api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.api = new ApiClient(this);
        PollCommand cmd = new PollCommand(this);
        var pc = getCommand("poll");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getLogger().info("Pollinations enabled. Set your API key in config.yml or with /poll key <key>.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Pollinations disabled.");
    }

    public ApiClient api() {
        return api;
    }

    public String apiKey() {
        return getConfig().getString("api-key", "");
    }

    public String textModel() {
        return getConfig().getString("text-model", "openai/gpt-5.4-nano");
    }

    public String imageModel() {
        return getConfig().getString("image-model", "black-forest-labs/flux.1-schnell");
    }

    public int imageSize() {
        return getConfig().getInt("image-size", 512);
    }

    public int maxHistory() {
        return getConfig().getInt("max-history", 10);
    }
}