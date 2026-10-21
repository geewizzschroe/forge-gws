package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.LobbyPlayer;
import forge.StaticData;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.BasicLandEscalation;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;

/** Turns the run state and an encounter into a match with the mode's rules. */
public final class RunMatchFactory {

    /** The match plus the hero's registration, used to read the result. */
    public record RunMatch(Match match, RegisteredPlayer hero, RegisteredPlayer enemy, EncounterDef encounter) {
    }

    private RunMatchFactory() {
    }

    public static RunMatch create(final RoguelikeContent content, final RunState state, final EncounterDef encounter,
                                  final LobbyPlayer heroPlayer, final LobbyPlayer enemyPlayer) {
        RunConfig cfg = content.getConfig();
        GameRules rules = new GameRules(GameType.Roguelike);
        rules.setGamesPerMatch(1);
        rules.setWarnAboutAICards(false);
        rules.setConjureBasicLandEachTurn(true);
        rules.setMaxLandsPerPlayer(cfg.landCap);
        for (CardRules o : content.getOverrides()) {
            rules.addCardOverride(o);
        }

        RegisteredPlayer hero = new RegisteredPlayer(deck("Run deck", state.deck, state.commander));
        hero.assignCommander();
        hero.setStartingLife(state.life);
        hero.setMaxLife(state.maxLife * cfg.inMatchLifeMultiplier);
        for (Map.Entry<String, List<RunUpgrade>> e : state.upgrades.entrySet()) {
            for (RunUpgrade u : e.getValue()) {
                hero.addCardUpgrade(e.getKey(), u.toCardUpgrade());
            }
        }
        Run run = new Run(content, state);
        hero.setBase(run.getPlayerBase());
        hero.setPlayer(heroPlayer);

        RegisteredPlayer enemy = new RegisteredPlayer(deck(encounter.name, encounter.deck, encounter.commander));
        if (encounter.commander != null) {
            enemy.assignCommander();
        }
        enemy.setStartingLife(encounter.life);
        enemy.setStartingHand(encounter.hand);
        enemy.setBase(run.getEncounterBase(encounter));
        enemy.setPlayer(enemyPlayer);

        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(hero);
        players.add(enemy);
        return new RunMatch(new Match(rules, players, encounter.name), hero, enemy, encounter);
    }

    /** A landless deck from card names and counts, with the commander in its own section. */
    static Deck deck(final String name, final Map<String, Integer> cards, final String commander) {
        Deck d = new Deck(name);
        for (Map.Entry<String, Integer> e : cards.entrySet()) {
            d.getMain().add(card(e.getKey()), e.getValue());
        }
        if (commander != null) {
            d.getOrCreate(DeckSection.Commander).add(card(commander));
        }
        return BasicLandEscalation.withoutLands(d);
    }

    private static PaperCard card(final String name) {
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        if (pc == null) {
            throw new IllegalArgumentException("Unknown card: " + name);
        }
        return pc;
    }
}
