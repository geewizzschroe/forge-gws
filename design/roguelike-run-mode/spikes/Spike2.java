import forge.GuiDesktop;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.player.GameLossReason;
import forge.gui.GuiBase;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.util.MyRandom;
import java.util.*;

/** Re-run test 1 with a few seeds and report WHY the match ended and whether the commanders were used. */
public class Spike2 {
    static String cmd(Player p) { StringBuilder sb = new StringBuilder(); for (var e : p.getCommanderDamage()) sb.append(e.getKey().getName()).append('=').append(e.getValue()).append(' '); return sb.length() == 0 ? "none" : sb.toString().trim(); }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, prefs -> { prefs.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false); prefs.setPref(FPref.UI_LANGUAGE, "en-US"); return null; });
        Object[] jillMain = {"Island", 12, "Plains", 5, "Savannah Lions", 4, "Wall of Omens", 4, "Mulldrifter", 3, "Man-o'-War", 4, "Ephemerate", 4, "Cloudshift", 4};
        Object[] bossMain = {"Mountain", 17, "Lightning Bolt", 4, "Goblin Piker", 4, "Hill Giant", 4, "Shock", 4, "Mons's Goblin Raiders", 4, "Cinder Pyromancer", 3};
        Map<String,Integer> summary = new TreeMap<>();
        for (long seed : new long[]{4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23}) {
            MyRandom.setRandom(new Random(seed));
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(Spike.reg(Spike.deck("JillDeck", "Jill, Shiva's Dominant", jillMain), "Hero", 0, 20));
            pp.add(Spike.reg(Spike.deck("BossDeck", "Krenko, Mob Boss", bossMain), "Boss", 1, 20));
            Game g = Spike.run(pp, null, 180);
            GameOutcome o = g.getOutcome();
            Player hero = Spike.named(g, "Hero"), boss = Spike.named(g, "Boss");
            String reasonKey = (hero.getOutcome().lossState != null ? "hero lost: " + hero.getOutcome().lossState : "boss lost: " + boss.getOutcome().lossState) + " | heroLife=" + (hero.getOutcome().lossState != null ? "n/a" : "")  ; summary.merge(reasonKey.split(" \\| ")[0], 1, Integer::sum); if (hero.getOutcome().lossState != null && hero.getOutcome().lossState != GameLossReason.LifeReachedZero) System.out.println("NOTE ODD seed " + seed + " heroLife=" + hero.getLife() + " reason=" + hero.getOutcome().lossState);
            if (false) System.out.println("NOTE seed " + seed + ": turns=" + g.getPhaseHandler().getTurn() + " winCondition=" + (o == null ? null : o.getWinCondition())
                    + " heroLife=" + hero.getLife() + " bossLife=" + boss.getLife()
                    + " heroLossReason=" + hero.getOutcome().lossState + " bossOutcome=" + boss.getOutcome().lossState
                    + " heroCmdDmgTaken=" + cmd(hero));
            int jillCasts = 0, krenkoCasts = 0;
            for (var e : g.getGameLog().getLogEntries(null)) {
                String s = e.toString();
                if (s.contains("cast Jill")) jillCasts++;
                if (s.contains("cast Krenko")) krenkoCasts++;
            }
            System.out.println("NOTE seed " + seed + ": log shows Jill cast " + jillCasts + "x, Krenko cast " + krenkoCasts + "x");
        }
        System.out.println("NOTE SUMMARY " + summary);
        System.exit(0);
    }
}
