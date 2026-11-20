package org.Cry_Legende.sHEnigmes.puzzle;

import java.util.List;
import java.util.Map;

/**
 * @param pieces  nombre de pièces, numérotées de 1 à pieces, à trouver une fois chacune
 */
public record CollectionPuzzle(String id, int pieces, List<Condition> conditions, Map<String, List<String>> actions)
    implements Puzzle {

    @Override
    public int length() {
        return pieces;
    }
}
