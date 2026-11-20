package org.Cry_Legende.sHEnigmes.puzzle;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Une condition d'énigme, écrite "&lt;A&gt; &lt;opérateur&gt; &lt;B&gt;" avec des espaces autour de l'opérateur.
 * A et B passent par PlaceholderAPI pour le joueur avant la comparaison (voir {@link #test}).
 *
 * @param left  texte de gauche, tel qu'écrit dans la config
 * @param op    opérateur
 * @param right texte de droite, tel qu'écrit dans la config
 */
public record Condition(String left, Operator op, String right) {

    public enum Operator {
        EQUALS("=="),
        NOT_EQUALS("!="),
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        GREATER(">"),
        LESS("<"),
        CONTAINS("contient"),
        NOT_CONTAINS("!contient");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        static Operator of(String symbol) {
            for (Operator operator : values()) {
                if (operator.symbol.equals(symbol)) {
                    return operator;
                }
            }
            return null;
        }
    }

    // les opérateurs les plus longs d'abord : >= avant >, !contient avant contient
    private static final Pattern LINE = Pattern.compile(
        "^(.*?)\\s+(!contient|contient|==|!=|>=|<=|>|<)(?:\\s+(.*))?$", Pattern.DOTALL);
    private static final Pattern COLORS = Pattern.compile(
        "(?i)[&§]#[0-9a-f]{6}|§x(?:§[0-9a-f]){6}|[&§][0-9a-fk-or]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u2009\\u202F]+");
    private static final Pattern NUMBER_SPACES = Pattern.compile("[\\s\\u00A0\\u2009\\u202F']+");
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");

    /**
     * @return la condition lue, ou null si la ligne n'a pas la forme "A opérateur B"
     */
    public static Condition parse(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = LINE.matcher(line.strip());
        if (!matcher.matches() || matcher.group(1).isBlank()) {
            return null;
        }
        String right = matcher.group(3) == null ? "" : matcher.group(3).strip();
        return new Condition(matcher.group(1).strip(), Operator.of(matcher.group(2)), right);
    }

    /**
     * Compare les deux côtés, déjà résolus par PlaceholderAPI. Si les deux se lisent comme des nombres, la
     * comparaison est numérique (sauf contient et !contient). Sinon elle porte sur du texte sans codes couleur, sans
     * casse ni accents, et ==, !=, contient et !contient sont les seuls opérateurs qui peuvent être vrais.
     */
    public boolean test(String resolvedLeft, String resolvedRight) {
        if (op != Operator.CONTAINS && op != Operator.NOT_CONTAINS) {
            BigDecimal a = number(resolvedLeft);
            BigDecimal b = number(resolvedRight);
            if (a != null && b != null) {
                int order = a.compareTo(b);
                return switch (op) {
                    case EQUALS -> order == 0;
                    case NOT_EQUALS -> order != 0;
                    case GREATER_OR_EQUAL -> order >= 0;
                    case LESS_OR_EQUAL -> order <= 0;
                    case GREATER -> order > 0;
                    case LESS -> order < 0;
                    default -> false;
                };
            }
        }
        String a = plain(resolvedLeft);
        String b = plain(resolvedRight);
        return switch (op) {
            case EQUALS -> a.equals(b);
            case NOT_EQUALS -> !a.equals(b);
            case CONTAINS -> a.contains(b);
            case NOT_CONTAINS -> !a.contains(b);
            default -> false;
        };
    }

    /**
     * Nombre lu dans un texte, null si ce n'est pas un nombre. Espaces (insécables compris) et apostrophes sont
     * ignorés. Un point ou une virgule seul est la virgule décimale ; avec les deux, le dernier est la virgule
     * décimale et l'autre sépare les milliers ; plusieurs du même signe séparent les milliers.
     */
    static BigDecimal number(String text) {
        if (text == null) {
            return null;
        }
        String s = NUMBER_SPACES.matcher(text).replaceAll("");
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            char decimal = dot > comma ? '.' : ',';
            char thousands = decimal == '.' ? ',' : '.';
            if (s.indexOf(decimal) != s.lastIndexOf(decimal)) {
                return null;
            }
            s = s.replace(String.valueOf(thousands), "").replace(decimal, '.');
        } else if (dot >= 0 || comma >= 0) {
            char separator = dot >= 0 ? '.' : ',';
            if (s.indexOf(separator) == s.lastIndexOf(separator)) {
                s = s.replace(separator, '.');
            } else {
                s = s.replace(String.valueOf(separator), "");
            }
        }
        return NUMBER.matcher(s).matches() ? new BigDecimal(s) : null;
    }

    /**
     * Texte comparable : sans codes couleur, sans accents, en minuscules, espaces simplifiés.
     */
    static String plain(String text) {
        if (text == null) {
            return "";
        }
        String s = COLORS.matcher(text).replaceAll("");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return WHITESPACE.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    @Override
    public String toString() {
        return left + " " + op.symbol() + " " + right;
    }
}
