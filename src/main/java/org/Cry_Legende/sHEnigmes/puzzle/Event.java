package org.Cry_Legende.sHEnigmes.puzzle;

import java.util.List;

/**
 * Noms des événements d'une énigme, clés de la section actions de config.yml.
 */
public final class Event {

    public static final String GOOD = "bon";
    public static final String WRONG = "faux";
    public static final String SOLVED = "reussi";
    public static final String ALREADY = "deja";
    public static final String SHOW = "montrer";
    public static final String REFUSE = "refuse";
    public static final String FOUND = "trouve";
    public static final String PROGRESS = "progres";
    public static final String INTERRUPTED = "interrompu";

    public static final List<String> SEQUENCE = List.of(GOOD, WRONG, SOLVED, ALREADY, REFUSE);
    public static final List<String> WORD = List.of(SHOW, SOLVED, WRONG, ALREADY, REFUSE);
    public static final List<String> COLLECTION = List.of(FOUND, SOLVED, ALREADY, REFUSE);
    public static final List<String> PRESENCE = List.of(PROGRESS, INTERRUPTED, SOLVED);

    private Event() {
    }
}
