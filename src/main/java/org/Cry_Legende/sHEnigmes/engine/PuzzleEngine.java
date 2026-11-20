package org.Cry_Legende.sHEnigmes.engine;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.config.Messages;
import org.Cry_Legende.sHEnigmes.config.PluginConfig;
import org.Cry_Legende.sHEnigmes.data.PlayerStore;
import org.Cry_Legende.sHEnigmes.data.PuzzleState;
import org.Cry_Legende.sHEnigmes.puzzle.Condition;
import org.Cry_Legende.sHEnigmes.puzzle.Event;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.Cry_Legende.sHEnigmes.puzzle.SequencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.WordPuzzle;
import org.Cry_Legende.sHEnigmes.util.Answer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Les opérations des sous-commandes : saisir, jouer, montrer, repondre, reset et etat. Chacune vérifie ses arguments
 * sur le thread de la commande, puis travaille sur le scheduler du joueur (PDC, état) et envoie les commandes de
 * l'action au thread principal.
 */
public final class PuzzleEngine {

    /**
     * Pseudo Java ou Bedrock : ces caractères suffisent.
     */
    public static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,40}");

    private final SHEnigmes plugin;
    private final PlayerStore store;
    private final ActionRunner actions;
    private final PlaybackManager playbacks;

    public PuzzleEngine(SHEnigmes plugin, PlayerStore store, ActionRunner actions, PlaybackManager playbacks) {
        this.plugin = plugin;
        this.store = store;
        this.actions = actions;
        this.playbacks = playbacks;
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------ vérifications communes

    private Player player(CommandSender sender, String name) {
        if (!PLAYER_NAME.matcher(name).matches()) {
            messages().send(sender, "erreur.joueur-invalide", "{ARG}", name);
            return null;
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            messages().send(sender, "erreur.joueur-hors-ligne", "{PLAYER}", name);
        }
        return player;
    }

    private Puzzle puzzle(CommandSender sender, String id) {
        if (!PluginConfig.ID.matcher(id).matches()) {
            messages().send(sender, "erreur.enigme-invalide", "{ARG}", id);
            return null;
        }
        Puzzle puzzle = plugin.config().puzzle(id);
        if (puzzle == null) {
            messages().send(sender, "erreur.enigme-inconnue", "{ARG}", id);
        }
        return puzzle;
    }

    private SequencePuzzle sequence(CommandSender sender, String id) {
        Puzzle puzzle = puzzle(sender, id);
        if (puzzle == null) {
            return null;
        }
        if (puzzle instanceof SequencePuzzle sequence) {
            return sequence;
        }
        messages().send(sender, "erreur.pas-une-sequence", "{ID}", puzzle.id());
        return null;
    }

    private WordPuzzle word(CommandSender sender, String id) {
        Puzzle puzzle = puzzle(sender, id);
        if (puzzle == null) {
            return null;
        }
        if (puzzle instanceof WordPuzzle word) {
            return word;
        }
        messages().send(sender, "erreur.pas-un-mot", "{ID}", puzzle.id());
        return null;
    }

    /**
     * Exécute le travail sur le scheduler du joueur. Un joueur parti entre-temps reçoit le même message qu'un joueur
     * hors ligne.
     */
    private void onPlayer(CommandSender sender, Player player, Runnable work) {
        Runnable gone = () -> messages().send(sender, "erreur.joueur-hors-ligne", "{PLAYER}", player.getName());
        try {
            ScheduledTask task = player.getScheduler().run(plugin, scheduled -> work.run(), gone);
            if (task == null) {
                gone.run();
            }
        } catch (IllegalPluginAccessException e) {
            // plugin en cours d'arrêt
        }
    }

    /**
     * Vérifie les conditions de l'énigme pour ce joueur. Si l'une est fausse, seule l'action refuse est lancée : rien
     * n'est tiré, saisi ni enregistré. À appeler sur le scheduler du joueur (PlaceholderAPI y lit l'état du joueur).
     */
    private boolean refused(Player player, Puzzle puzzle) {
        for (Condition condition : puzzle.conditions()) {
            boolean passed;
            try {
                passed = condition.test(plugin.resolve(player, condition.left()),
                    plugin.resolve(player, condition.right()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Condition '" + condition + "' de '" + puzzle.id() + "' en erreur pour "
                    + player.getName() + " (comptée comme fausse) : " + e);
                passed = false;
            }
            if (!passed) {
                actions.run(puzzle.action(Event.REFUSE), variables(player, puzzle));
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> variables(Player player, Puzzle puzzle) {
        Map<String, String> variables = new HashMap<>();
        variables.put("player", player.getName());
        variables.put("id", puzzle.id());
        variables.put("longueur", String.valueOf(puzzle.length()));
        return variables;
    }

    // ------------------------------------------------------------------ séquence

    /**
     * /shenigmes saisir : une entrée dans la séquence du joueur.
     */
    public void input(CommandSender sender, String playerName, String id, String symbolText) {
        Player player = player(sender, playerName);
        SequencePuzzle puzzle = player == null ? null : sequence(sender, id);
        if (puzzle == null) {
            return;
        }
        int symbol;
        try {
            symbol = Integer.parseInt(symbolText.strip());
        } catch (NumberFormatException e) {
            symbol = 0;
        }
        if (symbol < 1 || symbol > puzzle.symbols()) {
            messages().send(sender, "erreur.symbole-invalide", "{ARG}", symbolText,
                "{MAX}", String.valueOf(puzzle.symbols()));
            return;
        }
        int entered = symbol;
        onPlayer(sender, player, () -> {
            if (refused(player, puzzle)) {
                return;
            }
            PuzzleState state = store.state(player, puzzle);
            Map<String, String> variables = variables(player, puzzle);
            variables.put("symbole", String.valueOf(entered));
            variables.put("nom", puzzle.nameOf(entered));
            String event;
            synchronized (state) {
                if (state.solved()) {
                    event = Event.ALREADY;
                    variables.put("rang", String.valueOf(puzzle.length()));
                } else {
                    int rank = state.inputSize(System.currentTimeMillis(), puzzle.forgetMillis()) + 1;
                    variables.put("rang", String.valueOf(rank));
                    if (state.symbolAt(rank) == entered) {
                        state.push(entered, System.currentTimeMillis());
                        if (rank >= puzzle.length()) {
                            state.solve();
                            event = Event.SOLVED;
                        } else {
                            event = Event.GOOD;
                        }
                    } else {
                        state.clearInput();
                        event = Event.WRONG;
                    }
                }
            }
            store.save(player, puzzle.id());
            actions.run(puzzle.action(event), variables);
        });
    }

    /**
     * /shenigmes jouer : rejoue le code au joueur.
     */
    public void play(CommandSender sender, String playerName, String id) {
        Player player = player(sender, playerName);
        SequencePuzzle puzzle = player == null ? null : sequence(sender, id);
        if (puzzle == null) {
            return;
        }
        if (puzzle.playback() == null) {
            messages().send(sender, "erreur.jouer-absent", "{ID}", puzzle.id());
            return;
        }
        onPlayer(sender, player, () -> {
            if (refused(player, puzzle)) {
                return;
            }
            PuzzleState state = store.state(player, puzzle);
            playbacks.start(player, puzzle, state.code());
        });
    }

    // ------------------------------------------------------------------ mot

    /**
     * /shenigmes montrer : une pièce du mot, marquée comme vue.
     */
    public void show(CommandSender sender, String playerName, String id, String rankText) {
        Player player = player(sender, playerName);
        WordPuzzle puzzle = player == null ? null : word(sender, id);
        if (puzzle == null) {
            return;
        }
        int rank;
        try {
            rank = Integer.parseInt(rankText.strip());
        } catch (NumberFormatException e) {
            rank = 0;
        }
        if (rank < 1 || rank > puzzle.pieces()) {
            messages().send(sender, "erreur.rang-invalide", "{ARG}", rankText, "{MAX}", String.valueOf(puzzle.pieces()));
            return;
        }
        int shown = rank;
        onPlayer(sender, player, () -> {
            if (refused(player, puzzle)) {
                return;
            }
            PuzzleState state = store.state(player, puzzle);
            Map<String, String> variables = variables(player, puzzle);
            variables.put("rang", String.valueOf(shown));
            variables.put("lettre", state.letterAt(shown));
            state.markSeen(shown);
            store.save(player, puzzle.id());
            actions.run(puzzle.action(Event.SHOW), variables);
        });
    }

    /**
     * /shenigmes repondre : le mot proposé, comparé sans casse, sans accents et sans espaces.
     */
    public void answer(CommandSender sender, String playerName, String id, String text) {
        Player player = player(sender, playerName);
        WordPuzzle puzzle = player == null ? null : word(sender, id);
        if (puzzle == null) {
            return;
        }
        onPlayer(sender, player, () -> {
            if (refused(player, puzzle)) {
                return;
            }
            PuzzleState state = store.state(player, puzzle);
            Map<String, String> variables = variables(player, puzzle);
            String event;
            synchronized (state) {
                if (state.solved()) {
                    event = Event.ALREADY;
                } else if (Answer.same(text, state.word())) {
                    state.solve();
                    event = Event.SOLVED;
                } else {
                    event = Event.WRONG;
                }
            }
            store.save(player, puzzle.id());
            actions.run(puzzle.action(event), variables);
        });
    }

    // ------------------------------------------------------------------ remise à zéro et état

    /**
     * /shenigmes reset : efface le code d'une énigme (null pour toutes), un nouveau sera tiré au prochain besoin.
     */
    public void reset(CommandSender sender, String playerName, String idOrAll) {
        Player player = player(sender, playerName);
        if (player == null) {
            return;
        }
        boolean all = idOrAll.equals("*");
        String id = all ? null : idOrAll.toLowerCase(Locale.ROOT);
        if (!all && !PluginConfig.ID.matcher(id).matches()) {
            messages().send(sender, "erreur.enigme-invalide", "{ARG}", idOrAll);
            return;
        }
        onPlayer(sender, player, () -> {
            if (all) {
                playbacks.cancelAll(player.getUniqueId());
            } else {
                playbacks.cancel(player.getUniqueId(), id);
            }
            List<String> cleared = store.reset(player, id);
            if (all) {
                messages().send(sender, "admin.reset-tout", "{PLAYER}", player.getName(),
                    "{AMOUNT}", String.valueOf(cleared.size()));
            } else if (cleared.isEmpty()) {
                messages().send(sender, "admin.reset-rien", "{PLAYER}", player.getName(), "{ID}", id);
            } else {
                messages().send(sender, "admin.reset", "{PLAYER}", player.getName(), "{ID}", id);
            }
        });
    }

    /**
     * /shenigmes etat : code, saisie, pièces vues et résolue, sans rien tirer.
     */
    public void status(CommandSender sender, String playerName, String id) {
        Player player = player(sender, playerName);
        if (player == null) {
            return;
        }
        List<Puzzle> selected = new ArrayList<>();
        if (id == null) {
            selected.addAll(plugin.config().puzzles().values());
            if (selected.isEmpty()) {
                messages().send(sender, "admin.aucune-enigme");
                return;
            }
        } else {
            Puzzle puzzle = puzzle(sender, id);
            if (puzzle == null) {
                return;
            }
            selected.add(puzzle);
        }
        onPlayer(sender, player, () -> {
            messages().send(sender, "admin.etat-entete", "{PLAYER}", player.getName());
            for (Puzzle puzzle : selected) {
                PuzzleState state = store.peek(player, puzzle);
                if (state == null) {
                    messages().send(sender, "admin.etat-vide", "{ID}", puzzle.id());
                    continue;
                }
                String none = messages().get("admin.etat-aucune");
                String solved = messages().get(state.solved() ? "admin.etat-oui" : "admin.etat-non");
                if (puzzle instanceof SequencePuzzle sequence) {
                    state.inputSize(System.currentTimeMillis(), sequence.forgetMillis());
                    String input = state.input().stream().map(String::valueOf).collect(Collectors.joining("-"));
                    messages().send(sender, "admin.etat-sequence", "{ID}", puzzle.id(), "{CODE}", state.codeText(),
                        "{SAISIE}", input.isEmpty() ? none : input, "{RESOLUE}", solved);
                } else {
                    String seen = state.seenRanks().stream().map(String::valueOf).collect(Collectors.joining(", "));
                    messages().send(sender, "admin.etat-mot", "{ID}", puzzle.id(), "{CODE}", state.codeText(),
                        "{VUES}", seen.isEmpty() ? none : seen, "{RESOLUE}", solved);
                }
            }
        });
    }
}
