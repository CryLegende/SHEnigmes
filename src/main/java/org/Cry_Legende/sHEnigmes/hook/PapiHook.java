package org.Cry_Legende.sHEnigmes.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.bukkit.entity.Player;

/**
 * Seule classe du plugin qui touche à PlaceholderAPI : elle n'est chargée que si PlaceholderAPI est présent.
 */
public final class PapiHook {

    private final EnigmesExpansion expansion;

    public PapiHook(SHEnigmes plugin) {
        this.expansion = new EnigmesExpansion(plugin);
    }

    public boolean register() {
        return expansion.register();
    }

    public String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public void unregister() {
        expansion.unregister();
    }
}
