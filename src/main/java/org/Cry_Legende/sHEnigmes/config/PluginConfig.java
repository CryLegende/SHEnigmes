package org.Cry_Legende.sHEnigmes.config;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.puzzle.Condition;
import org.Cry_Legende.sHEnigmes.puzzle.Event;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.Cry_Legende.sHEnigmes.puzzle.SequencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.WordPuzzle;
import org.Cry_Legende.sHEnigmes.util.YamlCheck;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lecture de config.yml. Une énigme mal écrite est ignorée avec un avertissement, les autres restent utilisables.
 */
public final class PluginConfig {

    /**
     * Identifiant d'énigme : sert aussi de clé du PersistentDataContainer, donc a-z 0-9 _ - seulement.
     */
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,40}");

    private static final int MAX_SYMBOLS = 99;
    private static final int MAX_LENGTH = 64;
    private static final int DEFAULT_FORGET_SECONDS = 60;
    private static final int DEFAULT_INTERVAL = 10;

    private final Map<String, Puzzle> puzzles;
    private final List<String> warnings;

    private PluginConfig(Map<String, Puzzle> puzzles, List<String> warnings) {
        this.puzzles = puzzles;
        this.warnings = warnings;
    }

    /**
     * Lit config.yml du dossier du plugin. Chaque avertissement est aussi écrit dans la console.
     */
    public static PluginConfig load(SHEnigmes plugin) {
        List<String> warnings = new ArrayList<>();
        Map<String, Puzzle> puzzles = new LinkedHashMap<>();

        File file = new File(plugin.getDataFolder(), "config.yml");
        String error = YamlCheck.error(file);
        if (error != null) {
            warnings.add("config.yml illisible, aucune énigme chargée : " + error);
        } else {
            // sans les valeurs par défaut du jar : une énigme retirée du fichier ne doit pas revenir
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("enigmes");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection puzzle = section.getConfigurationSection(id);
                    Puzzle read = puzzle == null ? null : readPuzzle(id, puzzle, warnings);
                    if (puzzle == null) {
                        warnings.add("Énigme '" + id + "' ignorée : ce n'est pas un bloc");
                    }
                    if (read != null) {
                        puzzles.put(id, read);
                    }
                }
            }
        }
        warnings.forEach(plugin.getLogger()::warning);
        return new PluginConfig(Collections.unmodifiableMap(puzzles), List.copyOf(warnings));
    }

    private static Puzzle readPuzzle(String id, ConfigurationSection section, List<String> warnings) {
        String prefix = "Énigme '" + id + "' ignorée : ";
        if (!ID.matcher(id).matches()) {
            warnings.add(prefix + "l'identifiant ne peut contenir que a-z 0-9 _ - (40 caractères au plus)");
            return null;
        }
        List<Condition> conditions = readConditions(id, section, warnings);
        if (conditions == null) {
            return null;
        }
        String type = section.getString("type", "").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "sequence" -> readSequence(id, section, conditions, warnings, prefix);
            case "mot" -> readWord(id, section, conditions, warnings, prefix);
            default -> {
                warnings.add(prefix + "'type' vaut '" + section.getString("type") + "' (sequence ou mot)");
                yield null;
            }
        };
    }

    // ------------------------------------------------------------------ séquence

    private static Puzzle readSequence(String id, ConfigurationSection section, List<Condition> conditions,
                                       List<String> warnings, String prefix) {
        int symbols = section.getInt("symboles");
        if (symbols < 1 || symbols > MAX_SYMBOLS) {
            warnings.add(prefix + "'symboles' doit être un entier entre 1 et " + MAX_SYMBOLS);
            return null;
        }
        int length = section.getInt("longueur");
        if (length < 1 || length > MAX_LENGTH) {
            warnings.add(prefix + "'longueur' doit être un entier entre 1 et " + MAX_LENGTH);
            return null;
        }
        boolean repetitions = section.getBoolean("repetitions", false);
        if (!repetitions && length > symbols) {
            warnings.add(prefix + "'longueur' (" + length + ") dépasse 'symboles' (" + symbols
                + ") alors que 'repetitions' est false");
            return null;
        }

        Map<Integer, String> names = new LinkedHashMap<>();
        ConfigurationSection namesSection = section.getConfigurationSection("noms");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                Integer symbol = parseSymbol(key, symbols);
                if (symbol == null) {
                    warnings.add("Énigme '" + id + "' : nom du symbole '" + key + "' ignoré (de 1 à " + symbols + ")");
                } else {
                    names.put(symbol, namesSection.getString(key, ""));
                }
            }
        }

        int forget = section.getInt("oubli-saisie", DEFAULT_FORGET_SECONDS);
        if (forget < 0) {
            warnings.add("Énigme '" + id + "' : 'oubli-saisie' négatif, la saisie n'est jamais oubliée");
            forget = 0;
        }

        Map<String, List<String>> actions = readActions(id, section, Event.SEQUENCE, warnings);
        SequencePuzzle.Playback playback = readPlayback(id, section, symbols, warnings);
        return new SequencePuzzle(id, symbols, length, repetitions, Collections.unmodifiableMap(names),
            section.getString("separateur", " "), forget, conditions, actions, playback);
    }

    private static SequencePuzzle.Playback readPlayback(String id, ConfigurationSection section, int symbols,
                                                        List<String> warnings) {
        ConfigurationSection play = section.getConfigurationSection("jouer");
        if (play == null) {
            return null;
        }
        int interval = play.getInt("intervalle", DEFAULT_INTERVAL);
        if (interval < 1) {
            warnings.add("Énigme '" + id + "' : 'jouer.intervalle' doit être au moins 1 tick, " + DEFAULT_INTERVAL
                + " utilisé");
            interval = DEFAULT_INTERVAL;
        }
        Map<Integer, List<String>> perSymbol = new LinkedHashMap<>();
        ConfigurationSection symbolSection = play.getConfigurationSection("symbole");
        if (symbolSection != null) {
            for (String key : symbolSection.getKeys(false)) {
                Integer symbol = parseSymbol(key, symbols);
                if (symbol == null) {
                    warnings.add("Énigme '" + id + "' : 'jouer.symbole." + key + "' ignoré (de 1 à " + symbols + ")");
                } else {
                    perSymbol.put(symbol, commands(symbolSection, key));
                }
            }
        }
        List<Integer> silent = new ArrayList<>();
        for (int symbol = 1; symbol <= symbols; symbol++) {
            if (perSymbol.getOrDefault(symbol, List.of()).isEmpty()) {
                silent.add(symbol);
            }
        }
        if (!silent.isEmpty()) {
            warnings.add("Énigme '" + id + "' : 'jouer.symbole' sans commande pour " + silent
                + ", ces symboles seront silencieux");
        }
        return new SequencePuzzle.Playback(interval, commands(play, "avant"),
            Collections.unmodifiableMap(perSymbol), commands(play, "apres"));
    }

    // ------------------------------------------------------------------ mot

    private static Puzzle readWord(String id, ConfigurationSection section, List<Condition> conditions,
                                   List<String> warnings, String prefix) {
        int pieces = section.getInt("pieces");
        if (pieces < 1 || pieces > MAX_LENGTH) {
            warnings.add(prefix + "'pieces' doit être un entier entre 1 et " + MAX_LENGTH);
            return null;
        }
        List<String> words = new ArrayList<>();
        List<String> wrongLength = new ArrayList<>();
        for (String raw : section.getStringList("mots")) {
            String word = raw == null ? "" : raw.strip();
            if (word.isEmpty()) {
                continue;
            }
            if (word.codePoints().anyMatch(Character::isWhitespace)) {
                warnings.add("Énigme '" + id + "' : mot '" + word + "' ignoré, il contient un espace");
            } else if (word.codePointCount(0, word.length()) != pieces) {
                wrongLength.add(word + " (" + word.codePointCount(0, word.length()) + ")");
            } else {
                words.add(word);
            }
        }
        if (!wrongLength.isEmpty()) {
            warnings.add("Énigme '" + id + "' : " + wrongLength.size() + " mot(s) ignoré(s), leur longueur n'est pas "
                + "'pieces' (" + pieces + ") : " + String.join(", ", wrongLength));
        }
        if (words.isEmpty()) {
            warnings.add(prefix + "aucun mot de " + pieces + " lettre(s) dans 'mots'");
            return null;
        }
        return new WordPuzzle(id, List.copyOf(words), pieces, conditions,
            readActions(id, section, Event.WORD, warnings));
    }

    // ------------------------------------------------------------------ communs

    /**
     * Conditions de l'énigme. Une ligne illisible fait ignorer l'énigme : l'ignorer seule ouvrirait l'accès à des
     * joueurs qui ne le doivent pas.
     *
     * @return null si une condition est illisible
     */
    private static List<Condition> readConditions(String id, ConfigurationSection section, List<String> warnings) {
        List<String> lines = section.isString("conditions")
            ? List.of(section.getString("conditions", "")) : section.getStringList("conditions");
        List<Condition> conditions = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Condition condition = Condition.parse(line);
            if (condition == null) {
                warnings.add("Énigme '" + id + "' ignorée : condition illisible '" + line
                    + "' (A opérateur B, avec == != >= <= > < contient !contient entourés d'espaces)");
                return null;
            }
            conditions.add(condition);
        }
        return List.copyOf(conditions);
    }

    private static Map<String, List<String>> readActions(String id, ConfigurationSection section,
                                                         List<String> events, List<String> warnings) {
        Map<String, List<String>> actions = new LinkedHashMap<>();
        ConfigurationSection actionSection = section.getConfigurationSection("actions");
        if (actionSection != null) {
            for (String event : actionSection.getKeys(false)) {
                if (events.contains(event)) {
                    actions.put(event, commands(actionSection, event));
                } else {
                    warnings.add("Énigme '" + id + "' : 'actions." + event + "' inconnu (" + String.join(", ", events)
                        + ")");
                }
            }
        }
        return Collections.unmodifiableMap(actions);
    }

    /**
     * Liste de commandes d'une clé : une chaîne seule compte pour une liste d'un élément, les lignes vides sont
     * retirées et un / de tête est enlevé.
     */
    private static List<String> commands(ConfigurationSection section, String key) {
        List<String> raw = section.isString(key) ? List.of(section.getString(key, "")) : section.getStringList(key);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            String command = line == null ? "" : line.strip();
            if (command.startsWith("/")) {
                command = command.substring(1).stripLeading();
            }
            if (!command.isEmpty()) {
                out.add(command);
            }
        }
        return List.copyOf(out);
    }

    private static Integer parseSymbol(String key, int symbols) {
        try {
            int symbol = Integer.parseInt(key.strip());
            return symbol >= 1 && symbol <= symbols ? symbol : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ accès

    public Map<String, Puzzle> puzzles() {
        return puzzles;
    }

    public Puzzle puzzle(String id) {
        return id == null ? null : puzzles.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Problèmes rencontrés à la lecture (énigmes ignorées, clés inconnues...).
     */
    public List<String> warnings() {
        return warnings;
    }
}
