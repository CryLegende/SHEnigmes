package org.Cry_Legende.sHEnigmes.listener;

import org.Cry_Legende.sHEnigmes.SHEnigmes;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final SHEnigmes plugin;

    public PlayerListener(SHEnigmes plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.store().join(event.getPlayer(), plugin.config().puzzles().values());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.playbacks().cancelAll(event.getPlayer().getUniqueId());
        plugin.store().quit(event.getPlayer());
    }
}
