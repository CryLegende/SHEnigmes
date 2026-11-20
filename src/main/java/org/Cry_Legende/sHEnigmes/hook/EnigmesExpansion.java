package org.Cry_Legende.sHEnigmes.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.data.PuzzleState;
import org.Cry_Legende.sHEnigmes.puzzle.CollectionPuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.PresencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.Cry_Legende.sHEnigmes.puzzle.SequencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.WordPuzzle;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * %shenigmes_&lt;id&gt;_&lt;valeur&gt;% : le code d'un joueur et son avancement. Lire un placeholder tire le code du
 * joueur s'il n'existe pas encore (une collecte n'a pas de code : la lire n'enregistre rien). Un identifiant inconnu, une valeur qui ne s'applique pas à ce type d'énigme ou un
 * joueur hors ligne donnent une chaîne vide.
 */
public final class EnigmesExpansion extends PlaceholderExpansion {

    private static final long ERROR_LOG_INTERVAL_MS = 60_000;

    private final SHEnigmes plugin;
    private final AtomicLong lastError = new AtomicLong();

    public EnigmesExpansion(SHEnigmes plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shenigmes";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        try {
            return resolve(offline, params.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            long last = lastError.get();
            if (now - last > ERROR_LOG_INTERVAL_MS && lastError.compareAndSet(last, now)) {
                plugin.getLogger().log(Level.WARNING, "Erreur du placeholder %shenigmes_" + params + "%", e);
            }
            return "";
        }
    }

    private String resolve(OfflinePlayer offline, String params) {
        // l'identifiant peut contenir des _ : le plus long identifiant qui commence le texte l'emporte
        Puzzle puzzle = null;
        for (Puzzle candidate : plugin.config().puzzles().values()) {
            if (params.startsWith(candidate.id() + "_")
                && (puzzle == null || candidate.id().length() > puzzle.id().length())) {
                puzzle = candidate;
            }
        }
        if (puzzle == null) {
            return "";
        }
        String what = params.substring(puzzle.id().length() + 1);

        // valeurs qui viennent de la config, sans joueur
        if (what.equals("longueur")) {
            return String.valueOf(puzzle.length());
        }
        if (what.equals("duree")) {
            return puzzle instanceof PresencePuzzle presence ? String.valueOf(presence.seconds()) : "";
        }
        if (what.equals("pieces")) {
            return switch (puzzle) {
                case WordPuzzle word -> String.valueOf(word.pieces());
                case CollectionPuzzle collection -> String.valueOf(collection.pieces());
                default -> "";
            };
        }

        Player player = offline == null ? null : offline.getPlayer();
        if (player == null) {
            return "";
        }
        if (puzzle instanceof PresencePuzzle) {
            // rien d'enregistré : seule la tenue en cours se lit
            return switch (what) {
                case "secondes" -> String.valueOf(plugin.presences().seconds(player.getUniqueId(), puzzle.id()));
                case "restants" -> String.valueOf(puzzle.length() - plugin.presences().seconds(player.getUniqueId(), puzzle.id()));
                default -> "";
            };
        }
        SequencePuzzle sequence = puzzle instanceof SequencePuzzle s ? s : null;
        WordPuzzle word = puzzle instanceof WordPuzzle w ? w : null;
        CollectionPuzzle collection = puzzle instanceof CollectionPuzzle c ? c : null;
        PuzzleState state = collection == null ? plugin.store().state(player, puzzle)
            : Objects.requireNonNullElseGet(plugin.store().peek(player, puzzle), PuzzleState::new);

        switch (what) {
            case "resolue" -> {
                return String.valueOf(state.solved());
            }
            case "code" -> {
                return state.codeText();
            }
            case "saisie" -> {
                return sequence == null ? ""
                    : String.valueOf(state.inputSize(System.currentTimeMillis(), sequence.forgetMillis()));
            }
            case "vues" -> {
                return word == null ? "" : String.valueOf(state.seenCount());
            }
            case "trouves" -> {
                return collection == null ? "" : String.valueOf(state.seenCount(collection.pieces()));
            }
            case "restants" -> {
                return collection == null ? ""
                    : String.valueOf(collection.pieces() - state.seenCount(collection.pieces()));
            }
            case "noms" -> {
                if (sequence == null) {
                    return "";
                }
                StringJoiner names = new StringJoiner(sequence.separator());
                for (int symbol : state.code()) {
                    names.add(sequence.nameOf(symbol));
                }
                return names.toString();
            }
            default -> {
            }
        }

        // valeurs indexées : code_<i>, nom_<i>, lettre_<i>, vue_<i>, trouve_<i>
        int underscore = what.indexOf('_');
        if (underscore < 0) {
            return "";
        }
        int rank;
        try {
            rank = Integer.parseInt(what.substring(underscore + 1));
        } catch (NumberFormatException e) {
            return "";
        }
        if (rank < 1 || rank > puzzle.length()) {
            return "";
        }
        return switch (what.substring(0, underscore)) {
            case "code" -> sequence != null ? String.valueOf(state.symbolAt(rank)) : state.letterAt(rank);
            case "nom" -> sequence != null ? sequence.nameOf(state.symbolAt(rank)) : "";
            case "lettre" -> word != null ? state.letterAt(rank) : "";
            case "vue" -> word != null ? String.valueOf(state.isSeen(rank)) : "";
            case "trouve" -> collection != null ? String.valueOf(state.isSeen(rank)) : "";
            default -> "";
        };
    }
}
