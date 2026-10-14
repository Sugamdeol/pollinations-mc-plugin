package dev.sugamdeol.pollinations;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.metadata.FixedMetadataValue;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PollCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "\u00a7d[Pollinations]\u00a7r ";
    private static final int CHAT_MAX = 240;

    private final PollinationsPlugin plugin;
    /** Per-player chat history (plain /poll chat). */
    private final Map<UUID, List<ApiClient.Msg>> histories = new HashMap<>();
    /** Per-player + per-persona chat histories. */
    private final Map<String, List<ApiClient.Msg>> npcHistories = new HashMap<>();

    public PollCommand(PollinationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "chat" -> {
                requirePlayer(sender);
                String msg = join(args, 1);
                if (msg.isEmpty()) {
                    sender.sendMessage(PREFIX + "Usage: /poll chat <message>");
                    return true;
                }
                chatPlain((Player) sender, msg);
            }
            case "npc" -> {
                requirePlayer(sender);
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + "Usage: /poll npc <persona> <message>. Personas: "
                            + String.join(", ", personaNames()));
                    return true;
                }
                chatPersona((Player) sender, args[1].toLowerCase(), join(args, 2));
            }
            case "image" -> {
                requirePlayer(sender);
                String prompt = join(args, 1);
                if (prompt.isEmpty()) {
                    sender.sendMessage(PREFIX + "Usage: /poll image <prompt>");
                    return true;
                }
                generateMap((Player) sender, prompt);
            }
            case "clear" -> {
                requirePlayer(sender);
                histories.remove(((Player) sender).getUniqueId());
                npcHistories.keySet().removeIf(k -> k.startsWith(((Player) sender).getUniqueId() + ":"));
                sender.sendMessage(PREFIX + "Chat history cleared.");
            }
            case "key" -> {
                if (!sender.hasPermission("pollinations.admin")) {
                    sender.sendMessage(PREFIX + "You need pollinations.admin to set the key.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + "Current key: " + mask(plugin.apiKey()));
                    return true;
                }
                plugin.getConfig().set("api-key", args[1]);
                plugin.saveConfig();
                sender.sendMessage(PREFIX + "API key saved.");
            }
            case "about" -> {
                sender.sendMessage(Component.text("Pollinations ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("v" + plugin.getPluginMeta().getVersion(), NamedTextColor.GRAY))
                        .append(Component.text(" - AI chat, persona NPCs and text-to-image map art for Minecraft servers.")));
                sender.sendMessage(Component.text("Powered by pollinations.ai - each server owner uses their own API key.", NamedTextColor.GRAY));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    // ---------- plain chat ----------

    private void chatPlain(Player player, String message) {
        if (!checkKey(player)) return;
        List<ApiClient.Msg> history = histories.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

        senderWorking(player, "thinking...");
        runAsync(() -> {
            try {
                String reply = plugin.api().chat(null, trimHistory(history), message);
                history.add(new ApiClient.Msg("user", message));
                history.add(new ApiClient.Msg("assistant", reply));
                sendReply(player, "\u00a75" + player.getName() + " (you)\u00a7r: " + message);
                sendReply(player, "\u00a7dPollinations AI\u00a7r: " + reply);
            } catch (Exception e) {
                sendError(player, e);
            }
        });
    }

    // ---------- persona chat ----------

    private void chatPersona(Player player, String persona, String message) {
        if (!checkKey(player)) return;
        String system = plugin.getConfig().getString("personas." + persona);
        if (system == null) {
            player.sendMessage(PREFIX + "Unknown persona. Personas: " + String.join(", ", personaNames()));
            return;
        }
        String key = player.getUniqueId() + ":" + persona;
        List<ApiClient.Msg> history = npcHistories.computeIfAbsent(key, k -> new ArrayList<>());

        senderWorking(player, persona + " is thinking...");
        runAsync(() -> {
            try {
                String reply = plugin.api().chat(system, trimHistory(history), message);
                history.add(new ApiClient.Msg("user", message));
                history.add(new ApiClient.Msg("assistant", reply));
                String label = persona.substring(0, 1).toUpperCase() + persona.substring(1);
                sendReply(player, "\u00a76" + label + "\u00a7f: " + reply);
            } catch (Exception e) {
                sendError(player, e);
            }
        });
    }

    // ---------- image on map ----------

    private void generateMap(Player player, String prompt) {
        if (!checkKey(player)) return;
        senderWorking(player, "painting your map... (this takes a few seconds)");

        runAsync(() -> {
            try {
                Path file = plugin.api().image(prompt);
                BufferedImage img = ImageIO.read(file.toFile());
                if (img == null) throw new IllegalStateException("Could not decode downloaded image");

                runSync(() -> {
                    try {
                        MapView view = Bukkit.createMap(player.getWorld());
                        for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
                            view.removeRenderer(r);
                        }
                        view.addRenderer(new ImageMapRenderer(img));

                        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                        var meta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                        meta.setMapView(view);
                        meta.displayName(Component.text("Pollinations: " + prompt, NamedTextColor.LIGHT_PURPLE));
                        mapItem.setItemMeta(meta);
                        player.getInventory().addItem(mapItem);

                        player.sendMessage(PREFIX + "Map painted with: \u00a7f" + prompt);
                        // keep at most 12 maps per player to avoid filling inventories in tests
                        player.setMetadata("poll-last-map", new FixedMetadataValue(plugin, System.currentTimeMillis()));
                    } catch (Exception e) {
                        player.sendMessage(PREFIX + "\u00a7cFailed to create map: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                sendError(player, e);
            }
        });
    }

    /** Draws a static image once, then stops repainting. */
    private static final class ImageMapRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean drawn;

        ImageMapRenderer(BufferedImage image) {
            this.image = image;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) return;
            canvas.drawImage(0, 0, image);
            drawn = true;
        }
    }

    // ---------- helpers ----------

    private void requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Only players can use this command.");
        }
    }

    private boolean checkKey(Player player) {
        String key = plugin.apiKey();
        if (key == null || key.isBlank() || !key.startsWith("sk_") && !key.startsWith("plln_sk")) {
            player.sendMessage(PREFIX + "\u00a7cNo API key set. An admin must run \u00a7f/poll key <sk_...>\u00a7c or edit config.yml.");
            player.sendMessage(PREFIX + "\u00a77Get a free key at https://enter.pollinations.ai/keys");
            return false;
        }
        return true;
    }

    private List<ApiClient.Msg> trimHistory(List<ApiClient.Msg> history) {
        int max = Math.max(0, plugin.maxHistory());
        if (history.size() <= max) return history;
        return new ArrayList<>(history.subList(history.size() - max, history.size()));
    }

    private void runAsync(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    private void runSync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void senderWorking(Player player, String what) {
        player.sendMessage(PREFIX + "\u00a77" + what);
    }

    private void sendReply(Player player, String fullText) {
        runSync(() -> {
            for (String part : ApiClient.splitForChat(fullText, CHAT_MAX)) {
                player.sendMessage(part);
            }
        });
    }

    private void sendError(Player player, Exception e) {
        plugin.getLogger().warning("API call failed: " + e);
        runSync(() -> player.sendMessage(PREFIX + "\u00a7cError: " + e.getMessage()));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(new String[]{
                PREFIX + "commands:",
                "  \u00a7f/poll chat <message> \u00a77- talk to the AI",
                "  \u00a7f/poll npc <persona> <message> \u00a77- talk to an NPC (" + String.join(", ", personaNames()) + ")",
                "  \u00a7f/poll image <prompt> \u00a77- generate a map from a prompt",
                "  \u00a7f/poll clear \u00a77- clear your chat history",
                "  \u00a7f/poll about"
        });
    }

    private List<String> personaNames() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("personas");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private static String mask(String key) {
        if (key == null || key.length() < 8) return "(not set)";
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("chat", "npc", "image", "clear", "key", "about"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return partial(args[1], personaNames());
        }
        return List.of();
    }

    private static List<String> partial(String typed, List<String> options) {
        String t = typed.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(t)) out.add(o);
        }
        return out;
    }
}