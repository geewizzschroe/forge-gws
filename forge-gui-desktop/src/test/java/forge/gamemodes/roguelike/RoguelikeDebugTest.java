package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.Loadout;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.Progression;

public class RoguelikeDebugTest extends AITest {

    @Test
    public void progressShortcuts() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        MetaProfile profile = new MetaProfile();
        CharacterDef jill = c.getCharacter("jill");
        List<String> msgs = RoguelikeDebug.raiseToLevel(c, profile, "jill", RoguelikeDebug.maxLevel(jill));
        assertEquals(10, Progression.characterLevel(c, profile.character("jill")).level());
        assertEquals(0, Progression.characterLevel(c, profile.character("jill")).xpIntoLevel());
        assertTrue(msgs.toString(), msgs.stream().anyMatch(m -> m.startsWith("Jill reached level 10")));
        RoguelikeDebug.completeMilestones(c, profile, jill);
        assertTrue(Progression.availableStarters(c, jill, profile.character("jill")).contains("jill_frozen_throne"));
        int account = Progression.accountLevel(c, profile).level();
        RoguelikeDebug.nextAccountLevel(c, profile);
        assertEquals(account + 1, Progression.accountLevel(c, profile).level());
    }

    @Test
    public void runShortcuts() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        MetaProfile profile = new MetaProfile();
        Loadout loadout = new Loadout();
        loadout.starterId = "jill_mono_blue";
        Run run = Run.start(c, "jill_mono_blue", 4, Progression.setup(c, profile, c.getCharacter("jill"), loadout));
        // the start that kept its fight (the others became Events: one tier-1 enemy, once a floor)
        run.enterNode(run.getAvailableNodes().stream().filter(n -> n.type == NodeType.ENEMY).findFirst().orElseThrow().id);
        List<String> msgs = RoguelikeDebug.winFight(run, profile);
        assertTrue(msgs.toString(), msgs.get(1).startsWith("+30 experience."));
        assertEquals(OfferKind.CARD_REWARD, run.getState().offer.kind);
        assertEquals(30, profile.character("jill").xp);
        assertEquals(1, run.getState().matchesWon);

        RoguelikeDebug.addGold(run, 100);
        assertEquals(125, run.getState().gold);
        String relic = RoguelikeDebug.addRelic(run);
        assertTrue(relic, relic.startsWith("Relic added: "));
        assertEquals(1, run.getState().relics.size());
    }
}
