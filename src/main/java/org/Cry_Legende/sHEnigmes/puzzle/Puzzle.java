package org.Cry_Legende.sHEnigmes.puzzle;

import java.util.List;
import java.util.Map;

/**
 * Une énigme de config.yml : un code secret propre à chaque joueur, soit une séquence de symboles, soit un mot, ou
 * une collecte de pièces numérotées à trouver, ou une présence à tenir dans une zone (sans code).
 */
public sealed interface Puzzle permits SequencePuzzle, WordPuzzle, CollectionPuzzle, PresencePuzzle {

    String id();

    /**
     * Commandes console par événement (bon, faux, reussi, deja, montrer, trouve, refuse, progres, interrompu).
     */
    Map<String, List<String>> actions();

    /**
     * Conditions à remplir pour que les commandes agissent, toutes vraies (liste vide : aucune).
     */
    List<Condition> conditions();

    /**
     * Nombre d'éléments du code (symboles d'une séquence, lettres d'un mot, pièces d'une collecte), ou secondes à
     * tenir d'une présence.
     */
    int length();

    default List<String> action(String event) {
        return actions().getOrDefault(event, List.of());
    }
}
