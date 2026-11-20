package org.Cry_Legende.sHEnigmes.puzzle;

import java.util.List;
import java.util.Map;

/**
 * @param words   mots tirables : seulement ceux dont la longueur est égale à pieces
 * @param pieces  nombre de pièces, une lettre par pièce
 */
public record WordPuzzle(String id, List<String> words, int pieces, List<Condition> conditions,
                         Map<String, List<String>> actions)
    implements Puzzle {

    @Override
    public int length() {
        return pieces;
    }
}
