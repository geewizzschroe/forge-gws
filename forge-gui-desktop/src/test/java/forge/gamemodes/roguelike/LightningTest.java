package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.keyword.KeywordInterface;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.Loadout;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.Progression;

/** Lightning, the second character: her starter, Base and the unlocks that put abilities on her Base. */
public class LightningTest extends AITest {

    private Run lightningRun(RoguelikeContent c, long xp) {
        CharacterDef ch = c.getCharacter("lightning");
        MetaProfile profile = new MetaProfile();
        profile.character(ch.id).xp = xp;
        Loadout loadout = new Loadout();
        loadout.starterId = "lightning_rw";
        return Run.start(c, loadout.starterId, 1, Progression.setup(c, profile, ch, loadout));
    }

    private Card putBase(Game game, Player p, Run run) {
        Card base = Card.fromPaperCard(run.getPlayerBase(), p);
        p.getZone(ZoneType.Command).add(base);
        game.getAction().checkStateEffects(true);
        return base;
    }

    @Test
    public void starterAndCharacterLoad() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        StarterDef s = c.getStarter("lightning_rw");
        assertEquals(16, s.deck.size());
        assertTrue(s.deck.values().stream().allMatch(n -> n == 2));
        CharacterDef ch = c.getCharacter("lightning");
        assertEquals("Lightning, Army of One", ch.commander);
        assertEquals(ch, Progression.characterForStarter(c, "lightning_rw"));
        assertEquals("Guardian Corps Armory", lightningRun(c, 0).getPlayerBase().getName());
    }

    @Test
    public void equippedTokensHaveHaste() {
        Game game = initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        putBase(game, p, lightningRun(c, 0));

        Card token = addCard("Savannah Lions", p);
        token.setTokenCard(true);
        Card plain = addCard("Savannah Lions", p);
        Card sword = addCard("Short Sword", p);
        game.getAction().checkStateEffects(true);
        assertTrue("an unequipped token has no haste", !token.hasKeyword("Haste"));

        sword.attachToEntity(token, null);
        game.getAction().checkStateEffects(true);
        assertTrue("an equipped token has haste", token.hasKeyword("Haste"));

        sword.attachToEntity(plain, null);
        game.getAction().checkStateEffects(true);
        assertTrue("an equipped nontoken has no haste", !plain.hasKeyword("Haste"));
        assertTrue("the token lost it when unequipped", !token.hasKeyword("Haste"));
    }

    @Test
    public void sunforgerArrivesWithLightningAndGetsSoldierEquip() {
        Game game = initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        Run run = lightningRun(c, 5600); // level 8: both Sunforger unlocks
        String text = run.getPlayerBase().getRules().getMainPart().getNonAbilityText();
        assertTrue("unlocked abilities are not listed apart from their own text: " + text, !text.contains("Unlocked"));
        Card shown = Card.fromPaperCard(run.getPlayerBase(), game.getPlayers().get(1));
        String full = shown.getView().getText(shown.getView().getCurrentState(), null);
        assertTrue(full, full.contains("Sunforger: Whenever your commander enters, conjure a Sunforger"));
        assertTrue(full, full.contains("Sunforger Drill: Sunforgers you control have equip Soldier"));
        assertTrue(full, !full.contains("Unlocked"));
        putBase(game, p, run);

        Card lightning = addCardToZone("Lightning, Army of One", p, ZoneType.Command);
        p.addCommander(lightning);
        Card onField = game.getAction().moveToPlay(lightning, null, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);

        Card sunforger = null;
        for (Card c2 : p.getCardsIn(ZoneType.Battlefield)) {
            if (c2.getName().equals("Sunforger")) {
                sunforger = c2;
            }
        }
        assertNotNull("no Sunforger conjured", sunforger);
        assertEquals(game.getCardState(onField), sunforger.getAttachedTo());
        assertEquals(3 + 4, game.getCardState(onField).getNetPower());

        int soldierEquips = 0;
        for (KeywordInterface k : sunforger.getKeywords()) {
            if (k.getOriginal().startsWith("Equip:1:Creature.Soldier")) {
                soldierEquips++;
            }
        }
        assertEquals(1, soldierEquips);
    }

    @Test
    public void armoryAttachesEquipment() {
        Game game = initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        Card base = putBase(game, p, lightningRun(c, 0));
        Card sword = addCard("Dragoon's Lance", p);
        Card shopkeep = addCard("Item Shopkeep", p);
        SpellAbility sa = base.getFirstSpellAbility();
        assertNotNull(sa);
        sa.setActivatingPlayer(p);
        sa.getTargets().add(sword);
        sa.getSubAbility().setActivatingPlayer(p);
        sa.getSubAbility().getTargets().add(shopkeep);
        AbilityUtils.resolve(sa);
        assertEquals(shopkeep, sword.getAttachedTo());
    }
}
