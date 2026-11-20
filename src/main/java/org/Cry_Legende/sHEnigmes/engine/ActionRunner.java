package org.Cry_Legende.sHEnigmes.engine;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lance les commandes d'une action en console, dans l'ordre, sur le thread principal (Bukkit refuse toute commande
 * lancée depuis un autre thread : l'appelant est souvent le scheduler d'un joueur, et DivineMC fait tourner les
 * mondes en parallèle). Les variables %player%, %id%, %symbole%, %rang%, %longueur%, %lettre%, %nom%, %trouves%,
 * %pieces% et %restants% sont remplacées ; un autre %...% reste tel quel.
 */
public final class ActionRunner {

    private static final Pattern VARIABLE = Pattern.compile("%([a-z]+)%");

    private final SHEnigmes plugin;

    public ActionRunner(SHEnigmes plugin) {
        this.plugin = plugin;
    }

    /**
     * Remplace les variables des commandes puis les envoie au thread principal d'un seul bloc : elles gardent leur
     * ordre et ne sont jamais mêlées aux commandes d'une autre action.
     */
    public void run(List<String> commands, Map<String, String> variables) {
        if (commands.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>(commands.size());
        for (String command : commands) {
            lines.add(replace(command, variables));
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> dispatch(lines));
        } catch (IllegalPluginAccessException e) {
            // plugin en cours d'arrêt
        }
    }

    private void dispatch(List<String> lines) {
        for (String line : lines) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Commande en erreur (" + line + ") : " + e);
            }
        }
    }

    /**
     * Une seule passe : une valeur remplacée qui contiendrait elle-même un %...% n'est pas remplacée à son tour.
     */
    static String replace(String command, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(command);
        StringBuilder out = new StringBuilder(command.length() + 16);
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
