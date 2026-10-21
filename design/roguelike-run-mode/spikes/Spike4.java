import com.google.common.eventbus.Subscribe;
import forge.GuiDesktop;
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

/**
 * Spike 4: choose-a-basic-land-each-upkeep.
 *  X1 = the relic (script) route, AI vs AI: what does the AI choose?
 *  X2 = a universal "never more than 10 lands" cap against forced entries.
 *  X3 = the same rule run as built-in game logic (no card in the command zone) with a simple colour-need heuristic for the AI.
 */
public class Spike4 {
    static final String[] BASICS = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
    static final String[] SYM = {"{W}", "{U}", "{B}", "{R}", "{G}"};

    static String landSummary(Player p) {
        Map<String, Integer> m = new TreeMap<>();
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) if (c.isLand()) m.merge(c.getName(), 1, Integer::sum);
        return m.toString();
    }

    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, prefs -> { prefs.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false); prefs.setPref(FPref.UI_LANGUAGE, "en-US"); return null; });
        Spike.loadCustomCards(args[0]);
        PaperCard engine = Spike.card("Spike Basic Engine"), cap = Spike.card("Spike Land Cap");

        Object[] heroRamp = {"Savannah Lions", 4, "Wall of Omens", 4, "Mulldrifter", 3, "Man-o'-War", 4, "Ephemerate", 4, "Cloudshift", 4, "Spike Rampant Choice", 4};
        Object[] heroPlain = {"Savannah Lions", 4, "Wall of Omens", 5, "Mulldrifter", 4, "Man-o'-War", 5, "Ephemerate", 5, "Cloudshift", 4};
        Object[] boss = {"Lightning Bolt", 5, "Goblin Piker", 5, "Hill Giant", 5, "Shock", 4, "Mons's Goblin Raiders", 5, "Cinder Pyromancer", 3};

        boolean only3 = args.length > 1 && args[1].equals("x3");
        final PhaseType landPhase = args.length > 2 && args[2].equals("main1") ? PhaseType.MAIN1 : PhaseType.UPKEEP;
        System.out.println("NOTE land phase = " + landPhase);
        // ---------------- X1: relic route, AI vs AI ----------------
        for (long seed = 1; seed <= (only3 ? 0 : 3); seed++) {
            final long sd = seed;
            MyRandom.setRandom(new Random(seed));
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(Spike.reg(Spike.deck("Hero", "Jill, Shiva's Dominant", heroRamp), "Hero", 0, 20, engine, cap));
            pp.add(Spike.reg(Spike.deck("Boss", "Krenko, Mob Boss", boss), "Boss", 1, 20, engine, cap));
            Game[] gh = new Game[1];
            Object l = new Object() {
                @Subscribe public void on(GameEventTurnPhase ev) {
                    if (ev.phase() == PhaseType.END_OF_TURN && gh[0].getPhaseHandler().getTurn() >= 14 && gh[0].getPhaseHandler().getTurn() <= 15)
                        System.out.println("NOTE X1 seed " + sd + " turn " + gh[0].getPhaseHandler().getTurn() + " " + gh[0].getPhaseHandler().getPlayerTurn().getName() + " lands: " + landSummary(gh[0].getPhaseHandler().getPlayerTurn()));
                }
            };
            Game g = Spike3.createAndRunWithListener(pp, gh, l);
            int hc = 0, bc = 0;
            for (var e : g.getGameLog().getLogEntries(null)) { String s = e.toString(); if (s.contains("Hero cast ")) hc++; if (s.contains("Boss cast ")) bc++; }
            String w = g.getOutcome() != null && g.getOutcome().getWinningLobbyPlayer() != null ? g.getOutcome().getWinningLobbyPlayer().getName() : "(none)";
            System.out.println("NOTE X1 seed " + seed + ": winner=" + w + " turns=" + g.getPhaseHandler().getTurn() + " heroCasts=" + hc + " bossCasts=" + bc);
        }

        // ---------------- X2: universal cap vs forced entries ----------------
        if (!only3) try {
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(Spike.reg(Spike.deck("Hero", "Jill, Shiva's Dominant", heroPlain), "Hero", 0, 20, cap));
            pp.add(Spike.reg(Spike.deck("Boss", "Krenko, Mob Boss", boss), "Boss", 1, 20));
            Game[] gh = new Game[1];
            StringBuilder out = new StringBuilder();
            Runnable[] hook = new Runnable[1];
            hook[0] = () -> {
                Game g = gh[0];
                try {
                    Player hero = Spike.named(g, "Hero"), bossP = Spike.named(g, "Boss");
                    for (int i = 0; i < 14; i++) {
                        Card c = Card.fromPaperCard(Spike.card("Island"), hero);
                        c.setGameTimestamp(g.getNextTimestamp());
                        hero.getZone(ZoneType.Hand).add(c);
                        g.getAction().moveToPlay(c, null, null);
                    }
                    for (int i = 0; i < 14; i++) {
                        Card c = Card.fromPaperCard(Spike.card("Mountain"), bossP);
                        c.setGameTimestamp(g.getNextTimestamp());
                        bossP.getZone(ZoneType.Hand).add(c);
                        g.getAction().moveToPlay(c, null, null);
                    }
                    int h = 0, b = 0;
                    for (Card c : hero.getCardsIn(ZoneType.Battlefield)) if (c.isLand()) h++;
                    for (Card c : bossP.getCardsIn(ZoneType.Battlefield)) if (c.isLand()) b++;
                    out.append("forced 14 Islands onto hero (has cap relic): lands=").append(h).append("; forced 14 Mountains onto boss (no cap): lands=").append(b);
                } catch (Throwable t) { out.append("hook error: ").append(t); t.printStackTrace(System.out); }
                g.setGameOver(GameEndReason.Draw);
            };
            Spike.createAndRun(pp, hook, gh);
            System.out.println("NOTE X2 " + out);
            System.out.println("RESULT X2 universal 10-land cap holds against forced entries: " + (out.toString().contains("hero (has cap relic): lands=10") ? "PASS" : "FAIL"));
        } catch (Throwable t) { t.printStackTrace(System.out); }

        // ---------------- X3: built-in rule (no relic card), colour-need heuristic for the AI ----------------
        for (long seed = 1; seed <= 3; seed++) {
            MyRandom.setRandom(new Random(seed));
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(Spike.reg(Spike.deck("Hero", "Jill, Shiva's Dominant", heroPlain), "Hero", 0, 20));   // nothing in the command zone but the commander
            pp.add(Spike.reg(Spike.deck("Boss", "Krenko, Mob Boss", boss), "Boss", 1, 20));
            Game[] gh = new Game[1];
            Map<String, List<String>> chosen = new LinkedHashMap<>();
            chosen.put("Hero", new ArrayList<>()); chosen.put("Boss", new ArrayList<>());
            Object l = new Object() {
                @Subscribe public void on(GameEventTurnPhase ev) {
                    if (ev.phase() != landPhase) return;
                    Game g = gh[0];
                    Player ap = g.getPhaseHandler().getPlayerTurn();
                    int lands = 0;
                    for (Card c : ap.getCardsIn(ZoneType.Battlefield)) if (c.isLand()) lands++;
                    if (lands >= 10) return;
                    // heuristic: colour symbols still in hand + library, divided by (1 + lands of that colour already out)
                    int[] need = new int[5], have = new int[5];
                    for (Card c : ap.getCardsIn(Arrays.asList(ZoneType.Hand, ZoneType.Library))) {
                        String mc = c.getManaCost().toString();
                        for (int i = 0; i < 5; i++) { int idx = 0; while ((idx = mc.indexOf(SYM[i], idx)) >= 0) { need[i]++; idx++; } }
                    }
                    for (Card c : ap.getCardsIn(ZoneType.Battlefield)) if (c.isLand()) for (int i = 0; i < 5; i++) if (c.getName().equals(BASICS[i])) have[i]++;
                    int best = 0; double bestScore = -1;
                    for (int i = 0; i < 5; i++) { double s = need[i] / (1.0 + have[i]); if (s > bestScore) { bestScore = s; best = i; } }
                    Card c = Card.fromPaperCard(Spike.card(BASICS[best]), ap);
                    c.setGameTimestamp(g.getNextTimestamp());
                    ap.getZone(ZoneType.Hand).add(c);
                    g.getAction().moveToPlay(c, null, null);
                    chosen.get(ap.getName()).add(BASICS[best].substring(0, 1));
                }
            };
            Game g = Spike3.createAndRunWithListener(pp, gh, l);
            int hc = 0, bc = 0;
            for (var e : g.getGameLog().getLogEntries(null)) { String s = e.toString(); if (s.contains("Hero cast ")) hc++; if (s.contains("Boss cast ")) bc++; }
            String w = g.getOutcome() != null && g.getOutcome().getWinningLobbyPlayer() != null ? g.getOutcome().getWinningLobbyPlayer().getName() : "(none)";
            System.out.println("NOTE X3 seed " + seed + ": winner=" + w + " turns=" + g.getPhaseHandler().getTurn() + " heroCasts=" + hc + " bossCasts=" + bc);
            System.out.println("NOTE X3 seed " + seed + ": hero chose " + chosen.get("Hero") + " | boss chose " + chosen.get("Boss"));
            { StringBuilder cz = new StringBuilder(); for (Card c : Spike.named(g, "Hero").getCardsIn(ZoneType.Command)) cz.append("[").append(c.getName()).append(" | immutable=").append(c.isImmutable()).append(" | emblem=").append(c.isEmblem()).append("] "); System.out.println("NOTE X3 seed " + seed + ": hero command zone at end: " + cz); }
        }
        System.exit(0);
    }
}
