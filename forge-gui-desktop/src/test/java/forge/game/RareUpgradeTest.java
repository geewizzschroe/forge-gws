package forge.game;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtilMana;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Upgrades that change the mana cost or add abilities (the run mode's rare upgrades). */
public class RareUpgradeTest extends AITest {

    private static CardUpgrade cost(int change) {
        return new CardUpgrade(0, 0, List.of(), change, List.of());
    }

    @Test
    public void costChangesMoveTheGenericPartOnly() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card jill = addCardToZone("Jill, Shiva's Dominant", p, ZoneType.Hand); // {2}{U}
        cost(-1).applyTo(jill);
        assertEquals(2, jill.getCMC());
        cost(-2).applyTo(jill); // only one generic left to cut
        assertEquals(1, jill.getCMC());
        assertEquals("{U}", jill.getManaCost().toString());

        Card lions = addCardToZone("Savannah Lions", p, ZoneType.Hand); // {W} 2/1
        new CardUpgrade(4, 4, List.of(), 1, List.of()).applyTo(lions);
        assertEquals(2, lions.getCMC());
        assertEquals(6, lions.getNetPower());
        Card onField = game.getAction().moveToPlay(lions, null, null);
        assertEquals("kept through a zone change", 2, onField.getCMC());
    }

    @Test
    public void anAddedTriggerWorksAfterTheCardMoves() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        addCardToZone("Savannah Lions", opp, ZoneType.Hand);
        Card mine = addCardToZone("Savannah Lions", p, ZoneType.Hand);
        new CardUpgrade(0, 0, List.of(), 0, List.of(
                "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | Execute$ rare_MindRot | TriggerDescription$ Mind Rot — When this creature enters, each opponent discards a card.",
                "SVar:rare_MindRot:DB$ Discard | Defined$ Opponent | NumCards$ 1 | Mode$ TgtChoose")).applyTo(mine);
        game.getAction().moveToPlay(mine, null, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        assertTrue(opp.getCardsIn(ZoneType.Hand).isEmpty());
        assertEquals(1, opp.getCardsIn(ZoneType.Graveyard).size());
    }

    @Test
    public void anAddedStaticReducesSpellCosts() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        addCards("Island", 2, p);
        Card weaver = addCard("Savannah Lions", p);
        Card divination = addCardToZone("Divination", p, ZoneType.Hand); // {2}{U}
        SpellAbility cast = divination.getFirstSpellAbility();
        cast.setActivatingPlayer(p);
        assertFalse(ComputerUtilMana.canPayManaCost(cast, p, 0, false));
        new CardUpgrade(0, 0, List.of(), 0, List.of(
                "S:Mode$ ReduceCost | ValidCard$ Instant,Sorcery | Type$ Spell | Activator$ You | Amount$ 1 | Description$ Spellweaver — Instant and sorcery spells you cast cost {1} less to cast.")).applyTo(weaver);
        game.getAction().checkStateEffects(true);
        assertTrue(ComputerUtilMana.canPayManaCost(cast, p, 0, false));
    }
}
