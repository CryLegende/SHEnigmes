package org.Cry_Legende.sHEnigmes.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.Cry_Legende.sHEnigmes.puzzle.SequencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.WordPuzzle;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * État d'un joueur pour une énigme : code, saisie en cours, pièces vues, résolue. Enregistré en JSON compact dans le
 * PersistentDataContainer du joueur. Chaque méthode est synchronisée, un placeholder peut le lire depuis n'importe
 * quel thread ; une suite d'appels qui doit rester cohérente se fait dans un bloc synchronized sur l'état.
 */
public final class PuzzleState {

    private int[] code;
    private String word;
    private final List<Integer> input = new ArrayList<>();
    private final BitSet seen = new BitSet();
    private boolean solved;
    private long lastInput;
    private boolean dirty;

    // ------------------------------------------------------------------ code

    public synchronized boolean hasCode() {
        return code != null || word != null;
    }

    /**
     * Vrai si le code enregistré est encore valide pour la définition actuelle (la config a pu changer).
     */
    public synchronized boolean fits(Puzzle puzzle) {
        switch (puzzle) {
            case SequencePuzzle sequence -> {
                if (code == null || code.length != sequence.length()) {
                    return false;
                }
                BitSet used = new BitSet();
                for (int symbol : code) {
                    if (symbol < 1 || symbol > sequence.symbols() || (!sequence.repetitions() && used.get(symbol))) {
                        return false;
                    }
                    used.set(symbol);
                }
                return true;
            }
            case WordPuzzle wordPuzzle -> {
                return word != null && word.codePointCount(0, word.length()) == wordPuzzle.pieces();
            }
        }
    }

    /**
     * Efface tout l'état (code compris).
     */
    public synchronized void clear() {
        code = null;
        word = null;
        input.clear();
        seen.clear();
        solved = false;
        lastInput = 0;
        dirty = true;
    }

    /**
     * Tire un nouveau code (l'état est d'abord remis à zéro).
     */
    public synchronized void draw(Puzzle puzzle, SecureRandom random) {
        clear();
        switch (puzzle) {
            case SequencePuzzle sequence -> {
                int[] drawn = new int[sequence.length()];
                if (sequence.repetitions()) {
                    for (int i = 0; i < drawn.length; i++) {
                        drawn[i] = random.nextInt(sequence.symbols()) + 1;
                    }
                } else {
                    // permutation : mélange de Fisher-Yates arrêté après length tirages
                    int[] pool = new int[sequence.symbols()];
                    for (int i = 0; i < pool.length; i++) {
                        pool[i] = i + 1;
                    }
                    for (int i = 0; i < drawn.length; i++) {
                        int pick = i + random.nextInt(pool.length - i);
                        int swap = pool[i];
                        pool[i] = pool[pick];
                        pool[pick] = swap;
                        drawn[i] = pool[i];
                    }
                }
                code = drawn;
            }
            case WordPuzzle wordPuzzle -> word = wordPuzzle.words().get(random.nextInt(wordPuzzle.words().size()));
        }
    }

    public synchronized int[] code() {
        return code == null ? new int[0] : code.clone();
    }

    /**
     * Symbole du rang donné (à partir de 1), 0 si le rang n'existe pas.
     */
    public synchronized int symbolAt(int rank) {
        return code != null && rank >= 1 && rank <= code.length ? code[rank - 1] : 0;
    }

    public synchronized String word() {
        return word;
    }

    /**
     * Le code en texte : symboles séparés par un tiret (3-1-4-5-2), ou le mot.
     */
    public synchronized String codeText() {
        if (code != null) {
            StringBuilder text = new StringBuilder();
            for (int symbol : code) {
                if (!text.isEmpty()) {
                    text.append('-');
                }
                text.append(symbol);
            }
            return text.toString();
        }
        return word == null ? "" : word;
    }

    /**
     * Lettre du rang donné (à partir de 1), chaîne vide si le rang n'existe pas.
     */
    public synchronized String letterAt(int rank) {
        if (word == null || rank < 1 || rank > word.codePointCount(0, word.length())) {
            return "";
        }
        int start = word.offsetByCodePoints(0, rank - 1);
        return new String(Character.toChars(word.codePointAt(start)));
    }

    // ------------------------------------------------------------------ saisie

    /**
     * Nombre d'entrées en cours. Une saisie restée plus de forgetMillis sans entrée est remise à zéro (0 : jamais).
     */
    public synchronized int inputSize(long now, long forgetMillis) {
        if (!input.isEmpty() && forgetMillis > 0 && now - lastInput > forgetMillis) {
            input.clear();
            dirty = true;
        }
        return input.size();
    }

    public synchronized List<Integer> input() {
        return List.copyOf(input);
    }

    public synchronized void push(int symbol, long now) {
        input.add(symbol);
        lastInput = now;
        dirty = true;
    }

    public synchronized void clearInput() {
        if (!input.isEmpty()) {
            input.clear();
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ pièces

    public synchronized void markSeen(int rank) {
        if (rank >= 1 && !seen.get(rank)) {
            seen.set(rank);
            dirty = true;
        }
    }

    public synchronized boolean isSeen(int rank) {
        return seen.get(rank);
    }

    public synchronized int seenCount() {
        return seen.cardinality();
    }

    public synchronized List<Integer> seenRanks() {
        List<Integer> ranks = new ArrayList<>();
        for (int i = seen.nextSetBit(0); i >= 0; i = seen.nextSetBit(i + 1)) {
            ranks.add(i);
        }
        return ranks;
    }

    // ------------------------------------------------------------------ résolue

    public synchronized boolean solved() {
        return solved;
    }

    /**
     * Marque l'énigme résolue : la saisie est vidée.
     */
    public synchronized void solve() {
        solved = true;
        input.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------ enregistrement

    public synchronized boolean dirty() {
        return dirty;
    }

    /**
     * JSON compact : c code (tableau ou mot), i saisie, t heure de la dernière entrée, v pièces vues, s résolue.
     */
    public synchronized String toJson() {
        JsonObject json = new JsonObject();
        if (code != null) {
            JsonArray array = new JsonArray();
            for (int symbol : code) {
                array.add(symbol);
            }
            json.add("c", array);
        } else if (word != null) {
            json.addProperty("c", word);
        }
        if (!input.isEmpty()) {
            JsonArray array = new JsonArray();
            input.forEach(array::add);
            json.add("i", array);
            json.addProperty("t", lastInput);
        }
        if (!seen.isEmpty()) {
            JsonArray array = new JsonArray();
            seenRanks().forEach(array::add);
            json.add("v", array);
        }
        if (solved) {
            json.addProperty("s", 1);
        }
        dirty = false;
        return json.toString();
    }

    /**
     * @return l'état lu, ou null si le texte n'est pas un état valide
     */
    public static PuzzleState fromJson(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            PuzzleState state = new PuzzleState();
            JsonElement c = json.get("c");
            if (c != null && c.isJsonArray()) {
                JsonArray array = c.getAsJsonArray();
                state.code = new int[array.size()];
                for (int i = 0; i < state.code.length; i++) {
                    state.code[i] = array.get(i).getAsInt();
                }
            } else if (c != null && c.isJsonPrimitive()) {
                state.word = c.getAsString();
            }
            JsonElement i = json.get("i");
            if (i != null && i.isJsonArray()) {
                i.getAsJsonArray().forEach(element -> state.input.add(element.getAsInt()));
            }
            JsonElement t = json.get("t");
            state.lastInput = t == null ? 0 : t.getAsLong();
            JsonElement v = json.get("v");
            if (v != null && v.isJsonArray()) {
                v.getAsJsonArray().forEach(element -> state.seen.set(element.getAsInt()));
            }
            JsonElement s = json.get("s");
            state.solved = s != null && s.getAsInt() == 1;
            return state;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
