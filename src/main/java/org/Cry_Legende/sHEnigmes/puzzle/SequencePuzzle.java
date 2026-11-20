package org.Cry_Legende.sHEnigmes.puzzle;

import java.util.List;
import java.util.Map;

/**
 * @param symbols       entrées possibles, de 1 à symbols
 * @param length        longueur du code
 * @param repetitions   faux : le code est une permutation (length <= symbols)
 * @param names         nom de chaque symbole (facultatif)
 * @param separator     entre deux noms dans le placeholder noms
 * @param forgetSeconds secondes sans saisie avant que la saisie soit remise à zéro (0 : jamais)
 * @param conditions    conditions des commandes (toutes vraies)
 * @param playback      rejeu du code par /shenigmes jouer (null si la section jouer est absente)
 */
public record SequencePuzzle(String id, int symbols, int length, boolean repetitions, Map<Integer, String> names,
                             String separator, int forgetSeconds, List<Condition> conditions,
                             Map<String, List<String>> actions, Playback playback) implements Puzzle {

    public String nameOf(int symbol) {
        return names.getOrDefault(symbol, String.valueOf(symbol));
    }

    public long forgetMillis() {
        return forgetSeconds * 1000L;
    }

    /**
     * @param interval ticks entre deux symboles (et avant la section apres)
     * @param before   commandes au tout début, avant le premier symbole
     * @param symbol   commandes de chaque symbole
     * @param after    commandes lancées un intervalle après le dernier symbole
     */
    public record Playback(int interval, List<String> before, Map<Integer, List<String>> symbol,
                           List<String> after) {
    }
}
