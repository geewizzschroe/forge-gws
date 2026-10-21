package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import com.google.gson.Gson;

import forge.ai.AITest;
import forge.card.CardRules;
import forge.game.BaseCard;
import forge.item.PaperCard;

/** The shipped content in res/roguelike loads and is consistent. */
public class RoguelikeContentTest extends AITest {

    @Test
    public void contentLoadsAndEveryCardNameIsKnown() {
        initAndCreateGame(); // loads the card database
        RoguelikeContent c = RoguelikeContent.load();
        assertFalse(c.getStarters().isEmpty());
        assertFalse(c.getEncounters().isEmpty());
        assertFalse(c.getRelics().isEmpty());
        assertFalse(c.getUpgrades().isEmpty());
        assertEquals("unknown card names", "[]", c.findUnknownCards().toString());
        boolean boss = false;
        for (EncounterDef e : c.getEncounters().values()) {
            boss |= e.boss;
        }
        assertTrue("content needs at least one boss", boss);
    }

    private static final File SOURCE = new File("../forge-gui/src/main/resources/roguelike");

    @Test
    public void indexListsEveryContentFile() throws Exception {
        List<String> onDisk = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE.toPath())) {
            files.filter(Files::isRegularFile).forEach(f -> onDisk.add(SOURCE.toPath().relativize(f).toString().replace('\\', '/')));
        }
        onDisk.remove("index.txt");
        onDisk.remove("README.md");
        Collections.sort(onDisk);
        List<String> index = new ArrayList<>(Files.readAllLines(new File(SOURCE, "index.txt").toPath()));
        index.removeIf(String::isBlank);
        assertEquals("index.txt is out of date", onDisk, index);
    }

    @Test
    public void bundledContentMatchesTheFolder() {
        initAndCreateGame();
        RoguelikeContent bundled = RoguelikeContent.loadBundled();
        RoguelikeContent folder = RoguelikeContent.load(SOURCE);
        Gson gson = new Gson();
        assertEquals(gson.toJson(folder.getConfig()), gson.toJson(bundled.getConfig()));
        assertEquals(folder.getStarters().keySet(), bundled.getStarters().keySet());
        assertEquals(folder.getEncounters().keySet(), bundled.getEncounters().keySet());
        assertEquals(folder.getRelics().keySet(), bundled.getRelics().keySet());
        assertEquals(folder.getPools().keySet(), bundled.getPools().keySet());
        assertEquals(folder.getOverrides().size(), bundled.getOverrides().size());
        assertEquals(gson.toJson(folder.getBase("mountain_pass.txt")), gson.toJson(bundled.getBase("mountain_pass.txt")));
    }

    @Test
    public void raiderCampGivesGoblinsHaste() {
        forge.game.Game game = initAndCreateGame();
        forge.game.player.Player p = game.getPlayers().get(1);
        RoguelikeContent c = RoguelikeContent.load();
        forge.game.card.Card camp = forge.game.card.Card.fromPaperCard(
                BaseCard.fromScript(c.getBase(c.getEncounter("goblin_raiders").base)), p);
        p.getZone(forge.game.zone.ZoneType.Command).add(camp);
        forge.game.card.Card goblin = addCard("Goblin Piker", p);
        forge.game.card.Card giant = addCard("Hill Giant", p);
        game.getAction().checkStateEffects(true);
        assertTrue(goblin.hasKeyword(forge.game.keyword.Keyword.HASTE));
        assertFalse(giant.hasKeyword(forge.game.keyword.Keyword.HASTE));
    }

    @Test
    public void overridesParseAsTheCardsTheyReplace() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        assertEquals(10, c.getOverrides().size());
        for (CardRules o : c.getOverrides()) {
            PaperCard printed = forge.StaticData.instance().getCommonCards().getCard(o.getName());
            assertEquals(o.getName(), printed.getRules().getType().toString(), o.getType().toString());
            StringBuilder script = new StringBuilder(String.join(" ", o.getMainPart().getAbilities()));
            script.append(String.join(" ", o.getMainPart().getTriggers()));
            o.getMainPart().getVariables().forEach(v -> script.append(v.getValue()));
            assertTrue(o.getName() + " should conjure", script.toString().contains("MakeCard"));
            assertFalse(o.getName() + " should not search", script.toString().contains("ChangeZone"));
        }
    }

    @Test
    public void everyBaseAndAllRelicsTogetherBuildAValidBase() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        List<RelicDef> all = new ArrayList<>(c.getRelics().values());
        for (StarterDef s : c.getStarters().values()) {
            List<String> script = BaseAssembler.assemble(s.base == null ? null : c.getBase(s.base), all);
            PaperCard base = BaseCard.fromScript(script);
            assertTrue(base.getRules().getType().hasStringType("Base"));
        }
        for (EncounterDef e : c.getEncounters().values()) {
            if (e.base != null) {
                assertTrue(BaseCard.fromScript(c.getBase(e.base)).getRules().getType().hasStringType("Base"));
            }
        }
    }
}
