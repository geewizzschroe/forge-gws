package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import forge.StaticData;
import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.CharacterProgress;
import forge.gamemodes.roguelike.meta.Loadout;
import forge.gamemodes.roguelike.meta.MatchStatsTracker;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.ProgressText;
import forge.gamemodes.roguelike.meta.Progression;
import forge.gamemodes.roguelike.meta.RunProgress;
import forge.gamemodes.roguelike.sim.RandomPolicy;
import forge.gamemodes.roguelike.sim.RunRecord;
import forge.gamemodes.roguelike.sim.RunSimulator;

public class MetaProgressionTest extends AITest {

    private RoguelikeContent content() {
        initAndCreateGame(); // loads the card database
        return RoguelikeContent.load();
    }

    @Test
    public void levelCurve() {
        assertEquals(new Progression.Level(1, 0, 200), Progression.level(0, 200, 200));
        assertEquals(new Progression.Level(1, 199, 200), Progression.level(199, 200, 200));
        assertEquals(new Progression.Level(2, 0, 400), Progression.level(200, 200, 200));
        assertEquals(new Progression.Level(3, 0, 600), Progression.level(600, 200, 200));
        assertEquals(new Progression.Level(3, 50, 600), Progression.level(650, 200, 200));
    }

    @Test
    public void jillUnlocksArriveAtTheirLevels() {
        RoguelikeContent c = content();
        CharacterDef jill = c.getCharacter("jill");
        CharacterProgress p = new CharacterProgress();
        assertEquals(List.of("jill_mono_blue"), Progression.availableStarters(c, jill, p));
        assertTrue(Progression.characterUnlocks(c, jill, p).isEmpty());

        p.xp = 600; // level 3
        assertEquals(List.of("jill_mono_blue", "jill_blue_white"), Progression.availableStarters(c, jill, p));
        assertEquals(2, Progression.characterUnlocks(c, jill, p).size());
        assertEquals("+2 run max life", Progression.describe(c, Progression.characterUnlocks(c, jill, p).get(0).def()));
        // every unlock in the content can be described (all its ids resolve)
        for (Progression.Unlock u : Progression.upcomingUnlocks(c, jill, p)) {
            assertFalse(Progression.describe(c, u.def()).isEmpty());
        }
    }

    @Test
    public void loadoutBecomesTheRunsStartingState() {
        RoguelikeContent c = content();
        CharacterDef jill = c.getCharacter("jill");
        MetaProfile profile = new MetaProfile();
        profile.character("jill").xp = 4200; // level 7: +2 life, gold, Base, Jill +1/+1, +1 retry
        Loadout loadout = new Loadout();
        loadout.starterId = "jill_blue_white";
        loadout.base = "frozen_bastion.txt";
        for (Progression.Unlock u : Progression.characterUnlocks(c, jill, profile.character("jill"))) {
            if ("gold".equals(u.def().kind)) {
                loadout.disabled.add(u.key()); // leave the gold behind
            }
        }
        RunSetup setup = Progression.setup(c, profile, jill, loadout);
        Run run = Run.start(c, loadout.starterId, 3, setup);
        RunState s = run.getState();
        assertEquals("jill", s.characterId);
        assertEquals(22, s.maxLife);
        assertEquals(22, s.life);
        assertEquals(0, s.gold);
        assertEquals(2, s.retriesLeft);
        assertEquals("+1/+1", s.upgrades.get("Jill, Shiva's Dominant").get(0).describe());
        assertEquals("Frozen Bastion", run.getPlayerBase().getName());
        assertEquals(c.getStarter("jill_blue_white").deck, s.deck);
    }

    @Test
    public void accountLevelsUnlockRelicsPoolsAndSets() {
        RoguelikeContent c = content();
        CharacterDef jill = c.getCharacter("jill");
        Loadout loadout = new Loadout();
        loadout.starterId = "jill_mono_blue";

        MetaProfile fresh = new MetaProfile();
        RunSetup s0 = Progression.setup(c, fresh, jill, loadout);
        assertFalse(s0.relicPool.contains("lucky_coin"));
        assertFalse(s0.relicPool.contains("war_drum"));
        assertTrue(s0.relicPool.contains("whetstone"));
        assertTrue(s0.unlockedPools.isEmpty());
        assertTrue(s0.extraRewardCards.isEmpty());

        MetaProfile veteran = new MetaProfile();
        veteran.accountXp = 500 + 1000 + 1500; // account level 4
        RunSetup s4 = Progression.setup(c, veteran, jill, loadout);
        assertTrue(s4.relicPool.contains("lucky_coin"));
        assertFalse(s4.relicPool.contains("war_drum"));
        assertTrue(s4.unlockedPools.contains("bucket_ramp"));
        assertTrue(s4.extraRewardCards.size() > 20); // the blue and colourless half of Final Fantasy
        for (String name : s4.extraRewardCards) {
            var rules = StaticData.instance().getCommonCards().getCard(name).getRules();
            assertFalse(name, rules.getMainPart().getType().isLand());
            assertTrue(name + " shares no colour with Jill", rules.getColorIdentity().isColorless()
                    || rules.getColorIdentity().hasBlue());
        }
    }

    @Test
    public void recordingExperienceAndCountersReportsWhatOpened() {
        RoguelikeContent c = content();
        MetaProfile profile = new MetaProfile();
        List<String> msgs = Progression.record(c, profile, "jill", 650, Map.of());
        assertEquals(650, profile.character("jill").xp);
        assertEquals(650, profile.accountXp);
        assertTrue(msgs.toString(), msgs.get(0).startsWith("Jill reached level 2! Unlocked: +2 run max life"));
        assertTrue(msgs.toString(), msgs.get(1).startsWith("Jill reached level 3! Unlocked: Starter deck: Jill: Shimmering Wake"));
        assertTrue(msgs.toString(), msgs.get(2).startsWith("Account level 2! Unlocked: Relic in the pool: Lucky Coin"));

        Progression.record(c, profile, "jill", 0, Map.of(MatchStatsTracker.COMMANDER_ENTERS_BOUNCED_ENEMY_COMMANDER, 29));
        assertTrue(profile.character("jill").completedMilestones.isEmpty());
        msgs = Progression.record(c, profile, "jill", 0, Map.of(MatchStatsTracker.COMMANDER_ENTERS_BOUNCED_ENEMY_COMMANDER, 1));
        assertEquals(List.of("jill_bounce_boss"), profile.character("jill").completedMilestones);
        assertTrue(msgs.toString(), msgs.get(0).startsWith("Milestone complete: Use Jill's enters ability"));
        assertTrue(Progression.availableStarters(c, c.getCharacter("jill"), profile.character("jill")).contains("jill_frozen_throne"));
        String progress = ProgressText.progress(c, profile, c.getCharacter("jill"));
        assertTrue(progress, progress.contains("Use Jill's enters ability to bounce a boss's commander 30 times: done"));
    }

    @Test(timeOut = 1800000)
    public void simulatedRunsRecordExperienceAndCounters() {
        RoguelikeContent c = content();
        c.getConfig().mapRows = 2;
        MetaProfile profile = new MetaProfile();
        RunSimulator sim = new RunSimulator(c);
        sim.setMatchTimeoutSeconds(90);
        sim.setProfile(profile);
        long xp = 0;
        int wins = 0;
        for (long seed = 1; seed <= 2; seed++) {
            RunRecord r = sim.simulateRun("jill_mono_blue", seed, new RandomPolicy(seed));
            xp += r.xpEarned;
            wins += (int) r.matches.stream().filter(m -> m.won).count();
            if (r.status == RunStatus.WON) {
                assertTrue(r.progressMessages.toString(), r.progressMessages.stream().anyMatch(m -> m.startsWith("Run won!")));
            }
        }
        CharacterProgress jill = profile.character("jill");
        assertEquals(xp, jill.xp);
        assertEquals(xp, profile.accountXp);
        assertEquals(wins, (int) jill.counters.getOrDefault(RunProgress.MATCHES_WON, 0));
    }

    @Test
    public void trackerCountsJillBouncingTheEnemyCommander() {
        Game game = initAndCreateGame();
        Player hero = game.getPlayers().get(1);
        Player enemy = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, hero);
        Card krenko = addCard("Krenko, Mob Boss", enemy);
        enemy.addCommander(krenko);
        Card jill = addCardToZone("Jill, Shiva's Dominant", hero, ZoneType.Command);
        hero.addCommander(jill);
        MatchStatsTracker tracker = MatchStatsTracker.attach(game, hero.getRegisteredPlayer());

        // a plain bounce spell doesn't count
        Card unsummon = addCardToZone("Unsummon", hero, ZoneType.Hand);
        SpellAbility sa = unsummon.getFirstSpellAbility();
        sa.setActivatingPlayer(hero);
        sa.getTargets().add(krenko);
        game.getStack().add(sa);
        playUntilStackClear(game);
        assertTrue(game.getCardState(krenko).isInZone(ZoneType.Hand));
        assertTrue(tracker.getCounters().isEmpty());

        // back onto the battlefield, then Jill enters and her trigger returns it
        Card k2 = game.getAction().moveToPlay(game.getCardState(krenko), null, null);
        game.getAction().moveToPlay(jill, null, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        assertTrue(game.getCardState(k2).isInZone(ZoneType.Hand));
        assertEquals(Integer.valueOf(1), tracker.getCounters().get(MatchStatsTracker.COMMANDER_ENTERS_BOUNCED_ENEMY_COMMANDER));
    }
}
