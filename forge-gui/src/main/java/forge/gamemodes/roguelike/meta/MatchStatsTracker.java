package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventSpellResolved;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;

/**
 * Counts things that happen in a match, for milestones. Attach it to a game before play starts; read
 * {@link #getCounters()} afterwards. Counter names are what milestone definitions refer to.
 */
public class MatchStatsTracker {
    /** The hero's commander's enters trigger returned the enemy's commander (a boss's) to hand. */
    public static final String COMMANDER_ENTERS_BOUNCED_ENEMY_COMMANDER = "commanderEntersBouncedEnemyCommander";

    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final Set<Integer> heroCommanders = new HashSet<>();
    private final Set<Integer> enemyCommanders = new HashSet<>();
    /** Enemy commanders moved from the battlefield to hand since the last ability resolved. */
    private final List<Integer> bouncedSinceLastResolve = new ArrayList<>();

    public static MatchStatsTracker attach(final Game game, final RegisteredPlayer hero) {
        MatchStatsTracker t = new MatchStatsTracker();
        for (Player p : game.getRegisteredPlayers()) {
            Set<Integer> ids = p.getRegisteredPlayer() == hero ? t.heroCommanders : t.enemyCommanders;
            for (Card c : p.getCommanders()) {
                ids.add(c.getId());
            }
        }
        game.subscribeToEvents(t);
        return t;
    }

    public Map<String, Integer> getCounters() {
        return counters;
    }

    @Subscribe
    public void onZoneChange(final GameEventCardChangeZone e) {
        if (e.card() != null && e.from() != null && e.to() != null
                && e.from().zoneType() == ZoneType.Battlefield && e.to().zoneType() == ZoneType.Hand
                && enemyCommanders.contains(e.card().getId())) {
            bouncedSinceLastResolve.add(e.card().getId());
        }
    }

    @Subscribe
    public void onResolve(final GameEventSpellResolved e) {
        if (e.spell() != null && !e.spell().isSpell() && e.spell().getHostCard() != null
                && heroCommanders.contains(e.spell().getHostCard().getId())
                && e.spell().getDescription() != null && e.spell().getDescription().contains("enters")
                && !bouncedSinceLastResolve.isEmpty()) {
            counters.merge(COMMANDER_ENTERS_BOUNCED_ENEMY_COMMANDER, bouncedSinceLastResolve.size(), Integer::sum);
        }
        bouncedSinceLastResolve.clear();
    }
}
