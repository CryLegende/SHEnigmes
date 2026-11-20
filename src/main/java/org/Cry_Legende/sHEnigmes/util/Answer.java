package org.Cry_Legende.sHEnigmes.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Comparaison d'une réponse : sans casse, sans accents, sans espaces.
 */
public final class Answer {

    private Answer() {
    }

    /**
     * Forme comparable d'un texte : accents retirés, espaces (insécables compris) retirés, minuscules.
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(decomposed.length());
        decomposed.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
            if (!mark && !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean same(String a, String b) {
        String left = normalize(a);
        return !left.isEmpty() && left.equals(normalize(b));
    }
}
