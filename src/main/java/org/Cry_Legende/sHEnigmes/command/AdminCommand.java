package org.Cry_Legende.sHEnigmes.command;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.Cry_Legende.sHEnigmes.config.Messages;
import org.Cry_Legende.sHEnigmes.engine.PuzzleEngine;
import org.Cry_Legende.sHEnigmes.puzzle.CollectionPuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.Puzzle;
import org.Cry_Legende.sHEnigmes.puzzle.SequencePuzzle;
import org.Cry_Legende.sHEnigmes.puzzle.WordPuzzle;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * /shenigmes saisir|jouer|montrer|repondre|trouver|reset|etat|reload
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS =
        List.of("saisir", "jouer", "montrer", "repondre", "trouver", "reset", "etat", "reload");

    private static final String ADMIN_PERMISSION = "shenigmes.admin";

    private final SHEnigmes plugin;

    public AdminCommand(SHEnigmes plugin) {
        this.plugin = plugin;
    }

    /**
     * shenigmes.admin.&lt;sous-commande&gt; ou le parent shenigmes.admin (toutes).
     */
    private static boolean canUse(CommandSender sender, String subCommand) {
        return sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(ADMIN_PERMISSION + "." + subCommand);
    }

    /**
     * Sous-commandes que l'expéditeur a le droit d'utiliser.
     */
    private static List<String> allowed(CommandSender sender) {
        List<String> out = new ArrayList<>();
        for (String sub : SUB_COMMANDS) {
            if (canUse(sender, sub)) {
                out.add(sub);
            }
        }
        return out;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages messages = plugin.messages();
        List<String> permitted = allowed(sender);
        if (permitted.isEmpty()) {
            messages.send(sender, "erreur.pas-permission");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!SUB_COMMANDS.contains(sub)) {
            messages.send(sender, "admin.usage", "{SOUS-COMMANDES}", String.join("|", permitted));
            return true;
        }
        if (!permitted.contains(sub)) {
            // une sous-commande hors de ses droits reste invisible, comme pour un joueur sans droit
            messages.send(sender, "erreur.pas-permission");
            return true;
        }
        PuzzleEngine engine = plugin.engine();
        switch (sub) {
            case "saisir" -> {
                if (args.length != 4) {
                    messages.send(sender, "admin.usage-saisir");
                } else {
                    engine.input(sender, args[1], args[2], args[3]);
                }
            }
            case "jouer" -> {
                if (args.length != 3) {
                    messages.send(sender, "admin.usage-jouer");
                } else {
                    engine.play(sender, args[1], args[2]);
                }
            }
            case "montrer" -> {
                if (args.length != 4) {
                    messages.send(sender, "admin.usage-montrer");
                } else {
                    engine.show(sender, args[1], args[2], args[3]);
                }
            }
            case "repondre" -> {
                if (args.length < 3) {
                    messages.send(sender, "admin.usage-repondre");
                } else {
                    // un champ laissé vide arrive sans texte : c'est une mauvaise réponse, pas une erreur d'usage
                    String text = String.join(" ", List.of(args).subList(3, args.length));
                    engine.answer(sender, args[1], args[2], text);
                }
            }
            case "trouver" -> {
                if (args.length != 4) {
                    messages.send(sender, "admin.usage-trouver");
                } else {
                    engine.find(sender, args[1], args[2], args[3]);
                }
            }
            case "reset" -> {
                if (args.length != 3) {
                    messages.send(sender, "admin.usage-reset");
                } else {
                    engine.reset(sender, args[1], args[2]);
                }
            }
            case "etat" -> {
                if (args.length != 2 && args.length != 3) {
                    messages.send(sender, "admin.usage-etat");
                } else {
                    engine.status(sender, args[1], args.length == 3 ? args[2] : null);
                }
            }
            case "reload" -> plugin.reload(sender);
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> permitted = allowed(sender);
        if (permitted.isEmpty()) {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return permitted.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!permitted.contains(sub) || sub.equals("reload")) {
            return List.of();
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        if (args.length == 3) {
            List<String> ids = new ArrayList<>();
            for (Puzzle puzzle : plugin.config().puzzles().values()) {
                boolean fits = switch (sub) {
                    case "saisir", "jouer" -> puzzle instanceof SequencePuzzle;
                    case "montrer", "repondre" -> puzzle instanceof WordPuzzle;
                    case "trouver" -> puzzle instanceof CollectionPuzzle;
                    default -> true;
                };
                if (fits && puzzle.id().startsWith(prefix)) {
                    ids.add(puzzle.id());
                }
            }
            if (sub.equals("reset") && "*".startsWith(prefix)) {
                ids.add("*");
            }
            return ids;
        }
        if (args.length == 4) {
            Puzzle puzzle = plugin.config().puzzle(args[2]);
            int count = switch (sub) {
                case "saisir" -> puzzle instanceof SequencePuzzle sequence ? sequence.symbols() : 0;
                case "montrer" -> puzzle instanceof WordPuzzle word ? word.pieces() : 0;
                case "trouver" -> puzzle instanceof CollectionPuzzle collection ? collection.pieces() : 0;
                default -> 0;
            };
            return IntStream.rangeClosed(1, count).mapToObj(String::valueOf)
                .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
