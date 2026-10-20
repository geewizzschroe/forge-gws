import forge.CardStorageReader;
import forge.GuiDesktop;
import forge.ImageKeys;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.perpetual.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gui.GuiBase;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.FileUtil;
import forge.view.TimeLimitedCodeBlock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Throwaway feasibility spike for a roguelike run-mode. Prints lines starting with RESULT/NOTE. */
public class Spike {
    static void result(String test, boolean pass, String detail) {
        System.out.println("RESULT " + test + ": " + (pass ? "PASS" : "FAIL") + " -- " + detail);
    }
    static void note(String s) { System.out.println("NOTE " + s); }

    static PaperCard card(String name) {
        PaperCard c = FModel.getMagicDb().getCommonCards().getCard(name);
        if (c == null) note("card not found: " + name);
        return c;
    }

    static void loadCustomCards(String dir) throws Exception {
        final CardRules.Reader rulesReader = new CardRules.Reader();
        for (File f : Objects.requireNonNull(new File(dir).listFiles())) {
            rulesReader.reset();
            List<String> lines = FileUtil.readAllLines(new InputStreamReader(new FileInputStream(f),
                    Charset.forName(CardStorageReader.DEFAULT_CHARSET_NAME)), true);
            CardRules rules = rulesReader.readCard(lines, com.google.common.io.Files.getNameWithoutExtension(f.getName()));
            rules.setCustom();
            PaperCard pc = new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special) {
                @Override public String getImageKey(boolean altState) { return ImageKeys.ADVENTURECARD_PREFIX + getName(); }
            };
            CardDb db = rules.isVariant() ? FModel.getMagicDb().getVariantCards() : FModel.getMagicDb().getCommonCards();
            db.addCard(pc);
            note("loaded custom card: " + pc.getName());
        }
    }

    /** Deck with a commander and an arbitrary-shaped, NON-singleton main. */
    static Deck deck(String name, String commander, Object... namesAndCounts) {
        Deck d = new Deck(name);
        d.getOrCreate(DeckSection.Commander).add(card(commander), 1);
        int total = 0;
        for (int i = 0; i < namesAndCounts.length; i += 2) {
            PaperCard c = card((String) namesAndCounts[i]);
            int n = (Integer) namesAndCounts[i + 1];
            if (c != null) { d.getMain().add(c, n); total += n; }
        }
        note("deck '" + name + "': commander=" + commander + ", main=" + total + " cards, distinct=" + d.getMain().countDistinct());
        return d;
    }

    static RegisteredPlayer reg(Deck d, String name, int idx, int life, IPaperCard... commandZoneExtras) {
        RegisteredPlayer rp = RegisteredPlayer.forCommander(d);
        rp.setStartingLife(life);
        if (commandZoneExtras.length > 0) rp.addExtraCardsInCommandZone(Arrays.asList(commandZoneExtras));
        rp.setPlayer(GamePlayerUtil.createAiPlayer(name, idx, ""));
        return rp;
    }

    static Game run(List<RegisteredPlayer> pp, Runnable[] hookHolder, int timeoutSec) throws Exception {
        GameRules rules = new GameRules(GameType.Commander);
        rules.setAppliedVariants(EnumSet.of(GameType.Commander));
        Match mc = new Match(rules, pp, "Spike");
        final Game g = mc.createGame();
        g.setNoGUIUser();
        Runnable hook = hookHolder == null ? null : () -> hookHolder[0].run();
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> mc.startGame(g, hook), timeoutSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            note("run ended with: " + e);
        }
        return g;
    }

    static Player named(Game g, String n) {
        for (Player p : g.getRegisteredPlayers()) if (p.getName().startsWith(n)) return p;
        throw new IllegalStateException("no player " + n);
    }

    static List<Card> allNamed(Game g, String cardName) {
        List<Card> out = new ArrayList<>();
        for (Card c : g.getCardsIn(Arrays.asList(ZoneType.values()))) if (c.getName().equals(cardName)) out.add(c);
        return out;
    }

    static String desc(Card c) {
        return c.getName() + " " + c.getNetPower() + "/" + c.getNetToughness() + (c.hasKeyword("Haste") ? " haste" : "") + " zone=" + c.getZone().getZoneType();
    }

    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, prefs -> {
            prefs.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, false);
            prefs.setPref(FPref.UI_LANGUAGE, "en-US");
            return null;
        });
        loadCustomCards(args[0]);

        Object[] jillMain = {"Island", 12, "Plains", 5, "Savannah Lions", 4, "Wall of Omens", 4, "Mulldrifter", 3,
                "Man-o'-War", 4, "Ephemerate", 4, "Cloudshift", 4};
        Object[] bossMain = {"Mountain", 17, "Lightning Bolt", 4, "Goblin Piker", 4, "Hill Giant", 4, "Shock", 4,
                "Mons's Goblin Raiders", 4, "Cinder Pyromancer", 3};
        String bossCommander = FModel.getMagicDb().getCommonCards().getCard("Krenko, Mob Boss") != null ? "Krenko, Mob Boss" : "Rakdos, Lord of Riots";

        // ---------------- TEST 1+2: arbitrary deck shape, Commander variant, AI vs AI, read life back ----------------
        try {
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(reg(deck("JillDeck", "Jill, Shiva's Dominant", jillMain), "Hero", 0, 20));
            pp.add(reg(deck("BossDeck", bossCommander, bossMain), "Boss", 1, 20));
            Game g = run(pp, null, 180);
            Player hero = named(g, "Hero"), boss = named(g, "Boss");
            boolean over = g.isGameOver();
            String winner = over && g.getOutcome() != null && g.getOutcome().getWinningLobbyPlayer() != null
                    ? g.getOutcome().getWinningLobbyPlayer().getName() : "(none/draw)";
            result("1 arbitrary deck shape + Commander variant runs to completion", over,
                    "gameOver=" + over + " winner=" + winner + " turns=" + g.getPhaseHandler().getTurn()
                            + " heroCommanders=" + hero.getCommanders().size() + " heroStartLife=20");
            result("2 final life is readable after the match", true,
                    "hero life=" + hero.getLife() + ", boss life=" + boss.getLife());
        } catch (Throwable t) { t.printStackTrace(System.out); result("1/2", false, t.toString()); }

        // ---------------- TEST 3: per-name additive upgrades via the start-of-game hook ----------------
        try {
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(reg(deck("JillDeck", "Jill, Shiva's Dominant", jillMain), "Hero", 0, 20));
            pp.add(reg(deck("BossDeck", bossCommander, bossMain), "Boss", 1, 20));
            final Game[] gh = new Game[1];
            final StringBuilder out = new StringBuilder();
            Runnable[] hook = new Runnable[1];
            hook[0] = () -> {
                Game g = gh[0];
                try {
                    // "reward A: 2x Lions [+1/+1]", "reward B: 2x Lions [haste]", a second +1/+1 reward: all stack per NAME
                    Map<String, List<java.util.function.LongFunction<PerpetualInterface>>> upgrades = new HashMap<>();
                    upgrades.put("Savannah Lions", Arrays.asList(
                            ts -> new PerpetualPTBoost(ts, 1, 1),
                            ts -> new PerpetualPTBoost(ts, 1, 1),
                            ts -> new PerpetualKeywords(ts, Arrays.asList("Haste"), new ArrayList<>(), false)));
                    int touched = 0;
                    for (Card c : g.getCardsIn(Arrays.asList(ZoneType.values()))) {
                        List<java.util.function.LongFunction<PerpetualInterface>> ups = upgrades.get(c.getName());
                        if (ups == null) continue;
                        for (var u : ups) { PerpetualInterface rec = u.apply(g.getNextTimestamp()); c.addPerpetual(rec); rec.applyEffect(c); }
                        touched++;
                    }
                    out.append("upgraded ").append(touched).append(" Savannah Lions copies (any zone)\n");
                    Player hero = named(g, "Hero");
                    List<Card> lions = allNamed(g, "Savannah Lions");
                    Card ctrl = allNamed(g, "Wall of Omens").get(0);
                    out.append("control (un-upgraded Wall of Omens, in zone ").append(ctrl.getZone().getZoneType()).append("): ").append(ctrl.getNetPower()).append("/").append(ctrl.getNetToughness()).append("\n");
                    List<Card> inPlay = new ArrayList<>();
                    for (Card l : lions) inPlay.add(g.getAction().moveToPlay(l, null, null));
                    for (Card c : inPlay) out.append("  entered: ").append(desc(c)).append("\n");
                    Card bounced = g.getAction().moveToHand(inPlay.get(0), null);
                    out.append("  bounced to hand: ").append(desc(bounced)).append("\n");
                    Card back = g.getAction().moveToPlay(bounced, null, null);
                    out.append("  re-entered after bounce: ").append(desc(back)).append("\n");
                    Card exiled = g.getAction().exile(inPlay.get(1), null, null);
                    Card flick = g.getAction().moveToPlay(exiled, null, null);
                    out.append("  re-entered after exile/flicker: ").append(desc(flick)).append("\n");
                    boolean ok = inPlay.stream().allMatch(c -> c.getNetPower() == 4 && c.getNetToughness() == 3 && c.hasKeyword("Haste"))
                            && back.getNetPower() == 4 && back.hasKeyword("Haste") && flick.getNetPower() == 4 && flick.hasKeyword("Haste");
                    out.append("OK=").append(ok);
                } catch (Throwable t) { out.append("hook error: ").append(t); t.printStackTrace(System.out); }
                g.setGameOver(GameEndReason.Draw);
            };
            Game g = createAndRun(pp, hook, gh);
            for (String line : out.toString().split("\n")) note("T3 " + line);
            result("3 per-name additive upgrades (+1/+1, +1/+1, haste) apply to every copy and survive bounce/flicker", out.toString().contains("OK=true"), "see T3 lines");
        } catch (Throwable t) { t.printStackTrace(System.out); result("3", false, t.toString()); }

        // ---------------- TEST 4: in-match life cap via a scripted replacement effect on a command-zone relic ----------------
        try {
            PaperCard cap = card("Spike Life Cap");
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(reg(deck("JillDeck", "Jill, Shiva's Dominant", jillMain), "Hero", 0, 20, cap));
            pp.add(reg(deck("BossDeck", bossCommander, bossMain), "Boss", 1, 20));
            final Game[] gh = new Game[1];
            final StringBuilder out = new StringBuilder();
            Runnable[] hook = new Runnable[1];
            hook[0] = () -> {
                Game g = gh[0];
                try {
                    Player hero = named(g, "Hero"), boss = named(g, "Boss");
                    Card relic = allNamed(g, "Spike Life Cap").get(0);
                    out.append("relic zone=").append(relic.getZone().getZoneType()).append(" replacementEffects=").append(relic.getReplacementEffects().size()).append("\n");
                    out.append("hero start life=").append(hero.getLife()).append("\n");
                    hero.gainLife(30, relic, null);  out.append("after +30: ").append(hero.getLife()).append("  (want 40)\n");
                    int afterFirst = hero.getLife();
                    hero.gainLife(10, relic, null);  out.append("after +10 more: ").append(hero.getLife()).append("  (want 40)\n");
                    hero.loseLife(15, false, false, null); out.append("after -15 damage-like loss: ").append(hero.getLife()).append("  (want 25)\n");
                    hero.gainLife(5, relic, null);   out.append("after +5: ").append(hero.getLife()).append("  (want 30)\n");
                    int heroLast = hero.getLife();
                    boss.gainLife(30, relic, null);  out.append("boss (no relic) after +30: ").append(boss.getLife()).append("  (want 50, uncapped)\n");
                    out.append("OK=").append(afterFirst == 40 && hero.getLife() == heroLast && heroLast == 30 && boss.getLife() == 50);
                } catch (Throwable t) { out.append("hook error: ").append(t); t.printStackTrace(System.out); }
                g.setGameOver(GameEndReason.Draw);
            };
            createAndRun(pp, hook, gh);
            for (String line : out.toString().split("\n")) note("T4 " + line);
            result("4 life gain capped at 2x run-max (40) by a scripted command-zone replacement effect", out.toString().contains("OK=true"), "see T4 lines");
        } catch (Throwable t) { t.printStackTrace(System.out); result("4", false, t.toString()); }

        // ---------------- TEST 5: relic puts the commander onto the battlefield transformed (Jill -> Shiva) ----------------
        try {
            PaperCard relic = card("Spike Shiva Relic");
            List<RegisteredPlayer> pp = new ArrayList<>();
            pp.add(reg(deck("JillDeck", "Jill, Shiva's Dominant", jillMain), "Hero", 0, 20, relic));
            pp.add(reg(deck("BossDeck", bossCommander, bossMain), "Boss", 1, 20));
            final Game[] gh = new Game[1];
            final StringBuilder out = new StringBuilder();
            Runnable[] hook = new Runnable[1];
            hook[0] = () -> {
                Game g = gh[0];
                try {
                    Player hero = named(g, "Hero");
                    Card r = allNamed(g, "Spike Shiva Relic").get(0);
                    Card jill = hero.getCommanders().get(0);
                    out.append("relic zone=").append(r.getZone().getZoneType()).append(", abilities=").append(r.getSpellAbilities().size()).append("; commander zone=").append(jill.getZone().getZoneType()).append(" name=").append(jill.getName()).append("\n");
                    SpellAbility sa = null; for (SpellAbility x : r.getSpellAbilities()) { out.append("  ability: api=").append(x.getApi()).append(" activated=").append(x.isActivatedAbility()).append("\n"); if (x.getApi() == forge.game.ability.ApiType.ChangeZone) sa = x; }
                    sa.setActivatingPlayer(hero);
                    sa.getTargets().add(jill);
                    sa.resolve();
                    Card now = null;
                    for (Card c : hero.getCardsIn(ZoneType.Battlefield)) if (c.isCommander()) now = c;
                    if (now == null) { out.append("commander is not on the battlefield; it is in ").append(hero.getCommanders().get(0).getZone().getZoneType()).append("\n"); }
                    else out.append("battlefield commander: ").append(now.getName()).append(" state=").append(now.getCurrentStateName()).append(" ").append(now.getNetPower()).append("/").append(now.getNetToughness()).append(" types=").append(now.getType()).append("\n");
                    out.append("OK=").append(now != null && now.getName().startsWith("Shiva"));
                } catch (Throwable t) { out.append("hook error: ").append(t); t.printStackTrace(System.out); }
                g.setGameOver(GameEndReason.Draw);
            };
            createAndRun(pp, hook, gh);
            for (String line : out.toString().split("\n")) note("T5 " + line);
            result("5 a command-zone relic puts the commander onto the battlefield as its back face", out.toString().contains("OK=true"), "see T5 lines");
        } catch (Throwable t) { t.printStackTrace(System.out); result("5", false, t.toString()); }

        System.out.println("SPIKE DONE");
        System.exit(0);
    }

    /** Like run(), but lets the hook see the Game it belongs to. */
    static Game createAndRun(List<RegisteredPlayer> pp, Runnable[] hook, Game[] holder) throws Exception {
        GameRules rules = new GameRules(GameType.Commander);
        rules.setAppliedVariants(EnumSet.of(GameType.Commander));
        Match mc = new Match(rules, pp, "Spike");
        final Game g = mc.createGame();
        holder[0] = g;
        g.setNoGUIUser();
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> mc.startGame(g, hook[0]), 120, TimeUnit.SECONDS);
        } catch (Exception e) { note("run ended with: " + e); }
        return g;
    }
}
