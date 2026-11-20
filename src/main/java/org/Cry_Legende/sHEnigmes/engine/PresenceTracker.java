package org.Cry_Legende.sHEnigmes.engine;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.puzzle.Condition;
import org.Cry_Legende.sHEnigmes.puzzle.Event;
import org.Cry_Legende.sHEnigmes.puzzle.PresencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Les énigmes de présence (type presence) : une fois par seconde, chaque joueur qui se tient dans une zone, ou qui y
 * avait commencé, est vérifié sur son propre scheduler (DivineMC fait tourner les mondes en parallèle). Les secondes
 * tenues ne sont gardées qu'en mémoire.
 */
public final class PresenceTracker {

    private static final long ERROR_LOG_INTERVAL_MS = 60_000;
    /** Un joueur qui tient au bon endroit au mauvais moment est prévenu au plus une fois par cet intervalle. */
    private static final long REFUSE_INTERVAL_MS = 15_000;

    private final SHEnigmes plugin;
    private final ActionRunner actions;
    /** Secondes tenues d'affilée, par joueur puis par énigme ; une entrée n'existe que pendant une tenue. */
    private final Map<UUID, Map<String, Integer>> held = new ConcurrentHashMap<>();
    /** Dernier refus annoncé, par joueur puis par énigme. */
    private final Map<UUID, Map<String, Long>> refused = new ConcurrentHashMap<>();
    private final AtomicLong lastError = new AtomicLong();
    private BukkitTask task;

    public PresenceTracker(SHEnigmes plugin, ActionRunner actions) {
        this.plugin = plugin;
        this.actions = actions;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        held.clear();
        refused.clear();
    }

    /**
     * Après un rechargement : les tenues en cours repartent de zéro (une zone a pu changer ou disparaître).
     */
    public void clear() {
        held.clear();
        refused.clear();
    }

    public void forget(UUID player) {
        held.remove(player);
        refused.remove(player);
    }

    /**
     * Secondes tenues d'affilée par ce joueur pour cette énigme (0 hors d'une tenue).
     */
    public int seconds(UUID player, String id) {
        Map<String, Integer> mine = held.get(player);
        return mine == null ? 0 : mine.getOrDefault(id, 0);
    }

    private void tick() {
        List<PresencePuzzle> zones = new ArrayList<>();
        for (Puzzle puzzle : plugin.config().puzzles().values()) {
            if (puzzle instanceof PresencePuzzle presence) {
                zones.add(presence);
            }
        }
        if (zones.isEmpty()) {
            held.clear();
            return;
        }
        Set<String> worlds = new java.util.HashSet<>();
        zones.forEach(zone -> worlds.add(zone.world()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            // seuls les joueurs du monde d'une zone, ou en pleine tenue (pour l'interrompre), sont vérifiés
            if (!worlds.contains(player.getWorld().getName()) && !held.containsKey(player.getUniqueId())) {
                continue;
            }
            try {
                player.getScheduler().run(plugin, scheduled -> check(player, zones), null);
            } catch (IllegalPluginAccessException e) {
                return;
            }
        }
    }

    private void check(Player player, List<PresencePuzzle> zones) {
        if (!player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        for (PresencePuzzle zone : zones) {
            int before = seconds(id, zone.id());
            // la zone d'abord : les placeholders ne sont lus que pour les joueurs qui s'y tiennent
            boolean holding = zone.contains(player.getLocation()) && allTrue(player, zone, zone.conditions())
                && (zone.hold().isEmpty() || anyTrue(player, zone, zone.hold()));
            if (holding && !allTrue(player, zone, zone.refusal())) {
                // au bon endroit, mais pas au bon moment (conditions-refus) : rien ne compte, il est prévenu de temps en temps
                if (before > 0) {
                    set(id, zone.id(), 0);
                    actions.run(zone.action(Event.INTERRUPTED), variables(player, zone, before));
                }
                long now = System.currentTimeMillis();
                Map<String, Long> mine = refused.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
                Long last = mine.get(zone.id());
                if (last == null || now - last >= REFUSE_INTERVAL_MS) {
                    mine.put(zone.id(), now);
                    actions.run(zone.action(Event.REFUSE), variables(player, zone, 0));
                }
                continue;
            }
            if (!holding) {
                if (before > 0) {
                    set(id, zone.id(), 0);
                    actions.run(zone.action(Event.INTERRUPTED), variables(player, zone, before));
                }
                continue;
            }
            int now = before + 1;
            if (now >= zone.seconds()) {
                set(id, zone.id(), 0);
                actions.run(zone.action(Event.SOLVED), variables(player, zone, now));
            } else {
                set(id, zone.id(), now);
                actions.run(zone.action(Event.PROGRESS), variables(player, zone, now));
            }
        }
    }

    private void set(UUID player, String id, int seconds) {
        if (seconds > 0) {
            held.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(id, seconds);
            return;
        }
        Map<String, Integer> mine = held.get(player);
        if (mine != null) {
            mine.remove(id);
            if (mine.isEmpty()) {
                held.remove(player, mine);
            }
        }
    }

    private boolean allTrue(Player player, PresencePuzzle zone, List<Condition> conditions) {
        for (Condition condition : conditions) {
            if (!test(player, zone, condition)) {
                return false;
            }
        }
        return true;
    }

    private boolean anyTrue(Player player, PresencePuzzle zone, List<Condition> conditions) {
        for (Condition condition : conditions) {
            if (test(player, zone, condition)) {
                return true;
            }
        }
        return false;
    }

    private boolean test(Player player, PresencePuzzle zone, Condition condition) {
        try {
            return condition.test(plugin.resolve(player, condition.left()), plugin.resolve(player, condition.right()));
        } catch (RuntimeException e) {
            // vérifiée chaque seconde : une erreur n'est écrite qu'une fois par minute
            long now = System.currentTimeMillis();
            long last = lastError.get();
            if (now - last > ERROR_LOG_INTERVAL_MS && lastError.compareAndSet(last, now)) {
                plugin.getLogger().warning("Condition '" + condition + "' de '" + zone.id() + "' en erreur pour "
                    + player.getName() + " (comptée comme fausse) : " + e);
            }
            return false;
        }
    }

    private static Map<String, String> variables(Player player, PresencePuzzle zone, int seconds) {
        Map<String, String> variables = new HashMap<>();
        variables.put("player", player.getName());
        variables.put("id", zone.id());
        variables.put("longueur", String.valueOf(zone.seconds()));
        variables.put("duree", String.valueOf(zone.seconds()));
        variables.put("secondes", String.valueOf(seconds));
        variables.put("restants", String.valueOf(Math.max(0, zone.seconds() - seconds)));
        return variables;
    }
}
