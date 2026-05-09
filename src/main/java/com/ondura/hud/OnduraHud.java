package com.ondura.hud;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import io.th0rgal.oraxen.utils.AdventureUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public class OnduraHud extends JavaPlugin implements Listener {

    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Pattern numberPattern = Pattern.compile("(\\d+(\\.\\d+)?)");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("ondurahud").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Bukkit.getOnlinePlayers().forEach(this::updateHud);
        }, 0L, 40L);
    }

    private void updateHud(Player player) {
        StringBuilder fullHud = new StringBuilder();
        ConfigurationSection huds = getConfig().getConfigurationSection("huds");

        if (huds != null) {
            for (String key : huds.getKeys(false)) {
                fullHud.append(renderHud(huds.getConfigurationSection(key), player));
            }
        }

        Component title = AdventureUtils.MINI_MESSAGE.deserialize(fullHud.toString());
        BossBar bar = activeBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar b = BossBar.bossBar(title, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(b);
            return b;
        });
        bar.name(title);
    }

    private String renderHud(ConfigurationSection cfg, Player player) {
        if (cfg == null) return "";

        List<String> elements = cfg.getStringList("elements");
        StringBuilder contentBuilder = new StringBuilder();
        
        for (String el : elements) {
            if (el == null || el.isEmpty()) continue;
            String parsed = PlaceholderAPI.setPlaceholders(player, el);
            if (cfg.getBoolean("compact_numbers", false)) {
                parsed = formatAllNumbersInString(parsed);
            }
            contentBuilder.append(parsed);
        }
        
        String content = contentBuilder.toString();
        int totalPx = calculateExactWidth(content);
        int padding = cfg.getInt("padding", 4);
        int initialShift = cfg.getInt("initial_shift", 0);

        int bodySegments = (int) Math.ceil(totalPx / 5.0);
        int totalBgWidth = ((bodySegments + 2) * 6) - (bodySegments + 1);

        StringBuilder sb = new StringBuilder();
        sb.append("<shift:").append(initialShift).append(">");
        sb.append(cfg.getString("background.left")).append("<shift:-1>");
        for (int i = 0; i < bodySegments; i++) {
            sb.append(cfg.getString("background.body")).append("<shift:-1>");
        }
        sb.append(cfg.getString("background.right"));
        sb.append("<shift:-").append(totalBgWidth - padding).append(">");
        sb.append(content);
        
        int totalShiftDone = initialShift + padding + totalPx;
        if (totalShiftDone != 0) {
            sb.append("<shift:").append(-totalShiftDone).append(">");
        }


        return sb.toString();
    }
    private int calculateExactWidth(String text) {
        String stripped = text.replaceAll("<[^>]*>", "");
        int width = 0;
        for (char c : stripped.toCharArray()) {
            switch (c) {
                case '1': case 'i': case '.': case ':': case '!': width += 2; break;
                case 'l': case ',': case ';': width += 3; break;
                case 'I': case '[': case ']': case 't': width += 4; break;
                case ' ': case 'f': case 'k': case '(': case ')': case '{': case '}': width += 5; break;
                case 'K': case '$': width += 6; break;
                default: width += 6; break;
            }
        }
        Pattern glyphPattern = Pattern.compile("<glyph:[^>]+>");
        Matcher m = glyphPattern.matcher(text);
        while (m.find()) { width += 12; }
        return width;
    }

    private String formatAllNumbersInString(String input) {
        Matcher matcher = numberPattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(input, lastEnd, matcher.start());
            try {
                double val = Double.parseDouble(matcher.group(1));
                sb.append(formatNumber(val));
            } catch (Exception e) {
                sb.append(matcher.group(1));
            }
            lastEnd = matcher.end();
        }
        sb.append(input.substring(lastEnd));
        return sb.toString();
    }

    private String formatNumber(double value) {
        if (value >= 1_000_000_000) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.format("%.0f", value);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§6[OnduraHud] §aConfig rechargée !");
            return true;
        }
        return false;
    }
}
