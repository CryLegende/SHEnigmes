package org.Cry_Legende.sHEnigmes.config;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Charge messages.yml (le messages.yml du jar sert de valeurs par défaut) et fabrique les textes envoyés.
 */
public final class Messages {

    private final SHEnigmes plugin;

    private volatile YamlConfiguration config;

    public Messages(SHEnigmes plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        config = loaded;
    }

    /**
     * Message coloré dont les placeholders sont remplacés, passés par paires : "{PLAYER}", "Steve".
     * Chaîne vide si la clé n'existe pas ou si le message est vide.
     */
    public String get(String path, String... placeholders) {
        String raw = path == null ? null : config.getString(path);
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String result = raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return Text.color(result);
    }

    public void send(CommandSender sender, String path, String... placeholders) {
        String message = get(path, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }
}
