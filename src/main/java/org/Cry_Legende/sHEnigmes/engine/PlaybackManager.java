package org.Cry_Legende.sHEnigmes.engine;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.puzzle.SequencePuzzle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rejeu du code d'une séquence (/shenigmes jouer) : une tâche par joueur et par énigme, sur le scheduler du joueur.
 * Un nouveau rejeu remplace le précédent de la même énigme ; la déconnexion les coupe tous.
 */
public final class PlaybackManager {

    private final SHEnigmes plugin;
    private final ActionRunner actions;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    public PlaybackManager(SHEnigmes plugin, ActionRunner actions) {
        this.plugin = plugin;
        this.actions = actions;
    }

    private static String key(UUID uuid, String id) {
        return uuid + "/" + id;
    }

    /**
     * Lance le rejeu : avant et le premier symbole partent tout de suite, puis un symbole tous les intervalle ticks,
     * puis apres un intervalle après le dernier. À appeler sur le scheduler du joueur.
     */
    public void start(Player player, SequencePuzzle puzzle, int[] code) {
        SequencePuzzle.Playback playback = puzzle.playback();
        if (playback == null) {
            return;
        }
        Run run = new Run(player, puzzle, playback, code);
        Run previous = runs.put(run.key, run);
        if (previous != null) {
            previous.cancel();
        }
        run.step();
    }

    public void cancel(UUID uuid, String id) {
        Run run = runs.remove(key(uuid, id));
        if (run != null) {
            run.cancel();
        }
    }

    public void cancelAll(UUID uuid) {
        String prefix = uuid + "/";
        runs.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().cancel();
                return true;
            }
            return false;
        });
    }

    public void cancelEverything() {
        runs.values().forEach(Run::cancel);
        runs.clear();
    }

    /**
     * Nombre de rejeux en cours.
     */
    public int active() {
        return runs.size();
    }

    private final class Run {

        private final String key;
        private final Player player;
        private final SequencePuzzle puzzle;
        private final SequencePuzzle.Playback playback;
        private final int[] code;
        private final int total;
        private int index;
        private volatile boolean cancelled;
        private volatile ScheduledTask pending;

        private Run(Player player, SequencePuzzle puzzle, SequencePuzzle.Playback playback, int[] code) {
            this.key = PlaybackManager.key(player.getUniqueId(), puzzle.id());
            this.player = player;
            this.puzzle = puzzle;
            this.playback = playback;
            this.code = code;
            this.total = code.length + (playback.after().isEmpty() ? 0 : 1);
        }

        private Map<String, String> variables() {
            Map<String, String> variables = new HashMap<>();
            variables.put("player", player.getName());
            variables.put("id", puzzle.id());
            variables.put("longueur", String.valueOf(code.length));
            return variables;
        }

        private void step() {
            if (cancelled) {
                return;
            }
            int step = index++;
            Map<String, String> variables = variables();
            if (step < code.length) {
                if (step == 0) {
                    actions.run(playback.before(), variables);
                }
                int symbol = code[step];
                variables.put("symbole", String.valueOf(symbol));
                variables.put("rang", String.valueOf(step + 1));
                variables.put("nom", puzzle.nameOf(symbol));
                actions.run(playback.symbol().getOrDefault(symbol, List.of()), variables);
            } else {
                actions.run(playback.after(), variables);
            }

            if (index >= total) {
                runs.remove(key, this);
                return;
            }
            try {
                pending = player.getScheduler().runDelayed(plugin, task -> step(), this::retired, playback.interval());
                if (pending == null) {
                    retired();
                }
            } catch (IllegalPluginAccessException e) {
                retired();
            }
        }

        private void retired() {
            cancelled = true;
            runs.remove(key, this);
        }

        private void cancel() {
            cancelled = true;
            ScheduledTask task = pending;
            if (task != null) {
                task.cancel();
            }
        }
    }
}
