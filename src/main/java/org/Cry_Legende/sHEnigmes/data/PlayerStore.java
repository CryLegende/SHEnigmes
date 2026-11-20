package org.Cry_Legende.sHEnigmes.data;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * États des joueurs connectés. La vérité durable est le PersistentDataContainer du joueur (clé shenigmes:&lt;id&gt;,
 * JSON compact) ; une copie en mémoire sert de verrou et de lecture sans risque depuis n'importe quel thread, un
 * placeholder pouvant être demandé hors du thread du joueur (DivineMC fait tourner les mondes en parallèle). Toute
 * écriture dans le PDC se fait sur le scheduler du joueur.
 */
public final class PlayerStore {

    private final SHEnigmes plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Map<String, PuzzleState>> cache = new ConcurrentHashMap<>();

    public PlayerStore(SHEnigmes plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id);
    }

    // ------------------------------------------------------------------ lecture

    /**
     * État du joueur pour cette énigme. Le code est tiré (puis enregistré) s'il n'existe pas encore ou s'il ne
     * correspond plus à la config.
     */
    public PuzzleState state(Player player, Puzzle puzzle) {
        PuzzleState state = load(player, puzzle);
        boolean drew = false;
        synchronized (state) {
            if (state.hasCode() && !state.fits(puzzle)) {
                plugin.getLogger().warning("Code de " + player.getName() + " pour '" + puzzle.id()
                    + "' ne correspond plus à la config, un nouveau code est tiré.");
                state.clear();
            }
            if (!state.hasCode()) {
                state.draw(puzzle, random);
                drew = true;
            }
        }
        if (drew) {
            saveLater(player, puzzle.id());
        }
        return state;
    }

    /**
     * État sans tirer de code : null si le joueur n'a rien d'enregistré pour cette énigme.
     */
    public PuzzleState peek(Player player, Puzzle puzzle) {
        PuzzleState state = load(player, puzzle);
        synchronized (state) {
            return state.hasCode() && state.fits(puzzle) ? state : null;
        }
    }

    private PuzzleState load(Player player, Puzzle puzzle) {
        Map<String, PuzzleState> mine = cache.computeIfAbsent(player.getUniqueId(), uuid -> new ConcurrentHashMap<>());
        return mine.computeIfAbsent(puzzle.id(), id -> read(player, id));
    }

    private PuzzleState read(Player player, String id) {
        String text = player.getPersistentDataContainer().get(key(id), PersistentDataType.STRING);
        if (text != null) {
            PuzzleState state = PuzzleState.fromJson(text);
            if (state != null) {
                return state;
            }
            plugin.getLogger().warning("Donnée illisible de " + player.getName() + " pour '" + id
                + "', elle est ignorée.");
        }
        return new PuzzleState();
    }

    // ------------------------------------------------------------------ écriture

    /**
     * Écrit l'état dans le PDC. À appeler sur le scheduler du joueur.
     */
    public void save(Player player, String id) {
        Map<String, PuzzleState> mine = cache.get(player.getUniqueId());
        PuzzleState state = mine == null ? null : mine.get(id);
        if (state == null || !state.hasCode()) {
            return;
        }
        player.getPersistentDataContainer().set(key(id), PersistentDataType.STRING, state.toJson());
    }

    /**
     * Même chose depuis un thread quelconque : l'écriture passe par le scheduler du joueur.
     */
    public void saveLater(Player player, String id) {
        try {
            player.getScheduler().run(plugin, task -> save(player, id), null);
        } catch (IllegalPluginAccessException e) {
            // plugin en cours d'arrêt : onDisable enregistre ce qui reste
        }
    }

    // ------------------------------------------------------------------ cycle de vie du joueur

    /**
     * Connexion (ou démarrage du plugin avec le joueur en ligne) : relit les énigmes de la config.
     */
    public void join(Player player, Iterable<Puzzle> puzzles) {
        for (Puzzle puzzle : puzzles) {
            load(player, puzzle);
        }
    }

    /**
     * Déconnexion : enregistre ce qui n'est pas encore écrit, puis oublie le joueur.
     */
    public void quit(Player player) {
        saveDirty(player);
        cache.remove(player.getUniqueId());
    }

    private void saveDirty(Player player) {
        Map<String, PuzzleState> mine = cache.get(player.getUniqueId());
        if (mine == null) {
            return;
        }
        for (Map.Entry<String, PuzzleState> entry : mine.entrySet()) {
            if (entry.getValue().dirty()) {
                save(player, entry.getKey());
            }
        }
    }

    /**
     * Arrêt du plugin : enregistre tous les joueurs en ligne (le scheduler n'accepte déjà plus de tâche).
     */
    public void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                saveDirty(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Enregistrement de " + player.getName() + " impossible : " + e);
            }
        }
    }

    // ------------------------------------------------------------------ remise à zéro

    /**
     * Efface le code d'une énigme (un nouveau sera tiré à la prochaine utilisation). À appeler sur le scheduler du
     * joueur.
     *
     * @param id identifiant, ou null pour toutes les énigmes (même celles qui ne sont plus dans la config)
     * @return les identifiants effacés
     */
    public List<String> reset(Player player, String id) {
        List<String> cleared = new ArrayList<>();
        Map<String, PuzzleState> mine = cache.get(player.getUniqueId());
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (id != null) {
            boolean cached = mine != null && mine.remove(id) != null;
            NamespacedKey key = key(id);
            boolean stored = container.has(key);
            if (stored) {
                container.remove(key);
            }
            if (cached || stored) {
                cleared.add(id);
            }
            return cleared;
        }
        if (mine != null) {
            cleared.addAll(mine.keySet());
            mine.clear();
        }
        String namespace = key("x").getNamespace();
        for (NamespacedKey key : List.copyOf(container.getKeys())) {
            if (key.getNamespace().equals(namespace)) {
                container.remove(key);
                if (!cleared.contains(key.getKey())) {
                    cleared.add(key.getKey());
                }
            }
        }
        return cleared;
    }
}
