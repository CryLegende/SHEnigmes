package org.Cry_Legende.sHEnigmes;

import org.Cry_Legende.sHEnigmes.command.AdminCommand;
import org.Cry_Legende.sHEnigmes.config.Messages;
import org.Cry_Legende.sHEnigmes.config.PluginConfig;
import org.Cry_Legende.sHEnigmes.data.PlayerStore;
import org.Cry_Legende.sHEnigmes.engine.ActionRunner;
import org.Cry_Legende.sHEnigmes.engine.PlaybackManager;
import org.Cry_Legende.sHEnigmes.engine.PresenceTracker;
import org.Cry_Legende.sHEnigmes.engine.PuzzleEngine;
import org.Cry_Legende.sHEnigmes.hook.PapiHook;
import org.Cry_Legende.sHEnigmes.listener.PlayerListener;
import org.Cry_Legende.sHEnigmes.util.YamlCheck;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class SHEnigmes extends JavaPlugin {

    private volatile PluginConfig config;
    private volatile Messages messages;
    private PlayerStore store;
    private PlaybackManager playbacks;
    private PuzzleEngine engine;
    private PresenceTracker presences;
    private volatile PapiHook papi;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        messages.load();
        config = PluginConfig.load(this);

        store = new PlayerStore(this);
        ActionRunner actions = new ActionRunner(this);
        playbacks = new PlaybackManager(this, actions);
        engine = new PuzzleEngine(this, store, actions, playbacks);
        presences = new PresenceTracker(this, actions);
        presences.start();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand command = getCommand("shenigmes");
        if (command == null) {
            getLogger().severe("Commande 'shenigmes' absente de plugin.yml");
        } else {
            AdminCommand admin = new AdminCommand(this);
            command.setExecutor(admin);
            command.setTabCompleter(admin);
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            papi = new PapiHook(this);
            if (!papi.register()) {
                getLogger().warning("Impossible d'enregistrer %shenigmes_...% auprès de PlaceholderAPI.");
            }
        } else {
            getLogger().info("PlaceholderAPI absent : les placeholders %shenigmes_...% ne sont pas disponibles.");
        }

        // plugin chargé avec des joueurs déjà en ligne (PlugMan) : leurs états sont relus sur leur scheduler
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(this, task -> store.join(player, config.puzzles().values()), null);
        }
        getLogger().info(config.puzzles().size() + " énigme(s) chargée(s).");
    }

    @Override
    public void onDisable() {
        if (papi != null) {
            papi.unregister();
        }
        if (playbacks != null) {
            playbacks.cancelEverything();
        }
        if (presences != null) {
            presences.stop();
        }
        if (store != null) {
            store.saveAll();
        }
    }

    /**
     * /shenigmes reload : relit config.yml et messages.yml. Un fichier illisible ne change rien : l'ancienne
     * configuration reste en mémoire et l'erreur est affichée avec le nom du fichier. Les rejeux en cours gardent
     * leur définition jusqu'à leur fin, les codes des joueurs ne sont pas touchés, et rien ne demande de
     * redémarrage.
     */
    public void reload(CommandSender sender) {
        saveDefaultConfig();
        for (String name : List.of("config.yml", "messages.yml")) {
            String error = YamlCheck.error(new File(getDataFolder(), name));
            if (error != null) {
                getLogger().warning(name + " illisible, configuration inchangée : " + error);
                messages.send(sender, "admin.erreur-rechargement", "{FICHIER}", name, "{ERREUR}", error);
                return;
            }
        }
        messages.load();
        config = PluginConfig.load(this);
        presences.clear();
        messages.send(sender, "admin.rechargee", "{AMOUNT}", String.valueOf(config.puzzles().size()));
        if (!config.warnings().isEmpty()) {
            messages.send(sender, "admin.rechargee-avertissements", "{AMOUNT}", String.valueOf(config.warnings().size()));
        }
    }

    /**
     * Texte dont les placeholders PlaceholderAPI sont remplacés pour ce joueur (inchangé sans PlaceholderAPI).
     */
    public String resolve(Player player, String text) {
        PapiHook hook = papi;
        return hook == null ? text : hook.apply(player, text);
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public PlayerStore store() {
        return store;
    }

    public PlaybackManager playbacks() {
        return playbacks;
    }

    public PuzzleEngine engine() {
        return engine;
    }

    public PresenceTracker presences() {
        return presences;
    }
}
