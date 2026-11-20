package org.Cry_Legende.sHEnigmes.puzzle;

import org.bukkit.Location;

import java.util.List;
import java.util.Map;

/**
 * Rester un temps dans une zone, sans code : chaque seconde, un joueur dans la sphère qui remplit les conditions et au
 * moins une condition de tenir avance d'une seconde. Dès qu'il cesse (sorti de la zone, condition fausse, plus aucune
 * tenue), il repart de zéro. Rien n'est enregistré : une déconnexion ou un redémarrage fait aussi repartir de zéro.
 *
 * @param world   monde de la zone
 * @param x       centre de la sphère
 * @param radius  rayon de la sphère, en blocs
 * @param seconds secondes d'affilée à tenir
 * @param hold    conditions de tenue, au moins une vraie (liste vide : rester dans la zone suffit)
 */
public record PresencePuzzle(String id, String world, double x, double y, double z, double radius, int seconds,
                             List<Condition> hold, List<Condition> conditions, Map<String, List<String>> actions)
    implements Puzzle {

    @Override
    public int length() {
        return seconds;
    }

    /**
     * Vrai si ce point est dans la sphère de la zone, dans son monde.
     */
    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
