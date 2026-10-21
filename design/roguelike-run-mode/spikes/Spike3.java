import com.google.common.eventbus.Subscribe;
import forge.GuiDesktop;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.util.MyRandom;

import java.util.*;

/** Spike: landless decks + a per-turn "Rainbow Basic" spawned by a command-zone relic, AI vs AI. */
public class Spike3 {
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, prefs -> { prefs.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false); prefs.setPref(FPref.UI_LANGUAGE, "en-US"); return null; });
        Spike.loadCustomCards(args[0]);

        PaperCard rainbow = Spike.card("Rainbow Basic");
        System.out.println("NOTE rainbow basic card found in DB: " + (rainbow != null));
        PaperCard engine = Spike.card("Spike Mana Engine"), landfall = Spike.card("Spike Landfall");

        // landless decks (27 spells each, copies allowed); hero also runs 4 mode-rewritten ramp spells
        Object[] heroMain = {"Savannah Lions", 4, "Wall of Omens", 4, "Mulldrifter", 3, "Man-o'-War", 4, "Ephemerate", 4, "Cloudshift", 4, "Spike Rampant Growth", 4};
        Object[] bossMain = {"Lightning Bolt", 5, "Goblin Piker", 5, "Hill Giant", 5, "Shock", 4, "Mons's Goblin Raiders", 5, "Cinder Pyromancer", 3};

        List<String> seedSummaries = new ArrayList<>();
        int printedBasicInfo = 0;
        for (long seed = 1; seed <= 3; seed++) {
            MyRandom.setRandom(new Random(seed));
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(Spike.reg(Spike.deck("HeroNoLands", "Jill, Shiva's Dominant", heroMain), "Hero", 0, 20, engine, landfall));
            pp.add(Spike.reg(Spike.deck("BossNoLands", "Krenko, Mob Boss", bossMain), "Boss", 1, 20, engine));
            final Game[] gh = new Game[1];
            final Map<String, List<Integer>> landsByOwnTurn = new LinkedHashMap<>();
            landsByOwnTurn.put("Hero", new ArrayList<>());
            landsByOwnTurn.put("Boss", new ArrayList<>());
            // sample each player's land count at the start of their own combat, i.e. after the precombat-main spawn trigger
            Object listener = new Object() {
                @Subscribe public void on(GameEventTurnPhase ev) {
                    if (ev.phase() != PhaseType.COMBAT_BEGIN) return;
                    Game g = gh[0];
                    Player ap = g.getPhaseHandler().getPlayerTurn();
                    int lands = 0;
                    for (Card c : ap.getCardsIn(ZoneType.Battlefield)) if (c.isLand()) lands++;
                    landsByOwnTurn.get(ap.getName()).add(lands);
                }
            };
            Game g = createAndRunWithListener(pp, gh, listener);
            Player hero = Spike.named(g, "Hero"), boss = Spike.named(g, "Boss");
            if (printedBasicInfo++ == 0) {
                for (Card c : hero.getCardsIn(ZoneType.Battlefield)) if (c.getName().equals("Rainbow Basic")) {
                    System.out.println("NOTE a Rainbow Basic on the battlefield: type=" + c.getType() + " isBasicLand=" + c.isBasicLand()
                            + " manaAbilities=" + c.getManaAbilities().size() + " colorsProducible=" + c.getManaAbilities().stream().map(a -> a.getManaPart().getOrigProduced()).toList());
                    break;
                }
            }
            int heroCasts = 0, bossCasts = 0, landfallTriggers = 0, mulls = 0;
            for (var e : g.getGameLog().getLogEntries(null)) {
                String s = e.toString();
                if (s.startsWith("Hero cast") || s.contains("Hero cast ")) heroCasts++;
                if (s.startsWith("Boss cast") || s.contains("Boss cast ")) bossCasts++;
                if (s.contains("Spike Landfall")) landfallTriggers++;
                if (s.toLowerCase().contains("mulligan")) { mulls++; System.out.println("NOTE MULL-LINE seed " + seed + ": " + s.replace("\n"," ")); }
            }
            String winner = g.getOutcome() != null && g.getOutcome().getWinningLobbyPlayer() != null ? g.getOutcome().getWinningLobbyPlayer().getName() : "(draw/none)";
            System.out.println("NOTE seed " + seed + ": winner=" + winner + " turns=" + g.getPhaseHandler().getTurn()
                    + " heroCasts=" + heroCasts + " bossCasts=" + bossCasts + " landfallLogLines=" + landfallTriggers + " mulliganLogLines=" + mulls);
            System.out.println("NOTE seed " + seed + ": HERO lands at each of its combats: " + landsByOwnTurn.get("Hero"));
            System.out.println("NOTE seed " + seed + ": BOSS lands at each of its combats: " + landsByOwnTurn.get("Boss"));
        }
        System.exit(0);
    }

    static Game createAndRunWithListener(List<RegisteredPlayer> pp, Game[] holder, Object listener) throws Exception {
        GameRules rules = new GameRules(GameType.Commander);
        rules.setAppliedVariants(EnumSet.of(GameType.Commander));
        Match mc = new Match(rules, pp, "Spike3");
        final Game g = mc.createGame();
        holder[0] = g;
        g.setNoGUIUser();
        g.subscribeToEvents(listener);
        try {
            forge.view.TimeLimitedCodeBlock.runWithTimeout(() -> mc.startGame(g), 180, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) { System.out.println("NOTE run ended with: " + e); }
        return g;
    }
}
