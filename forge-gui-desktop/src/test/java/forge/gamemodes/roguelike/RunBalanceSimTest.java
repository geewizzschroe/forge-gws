package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.testng.SkipException;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.ProgressText;
import forge.gamemodes.roguelike.sim.MatchRecord;
import forge.gamemodes.roguelike.sim.GreedyPolicy;
import forge.gamemodes.roguelike.sim.RandomPolicy;
import forge.gamemodes.roguelike.sim.RunPolicy;
import forge.gamemodes.roguelike.sim.RunRecord;
import forge.gamemodes.roguelike.sim.RunSimulator;
import forge.gamemodes.roguelike.sim.SimReport;

/**
 * The balance simulator. A small smoke test runs with the suite; a full simulation runs only when asked:
 *
 * <pre>
 * xvfb-run -a mvn -pl forge-gui-desktop -am test -Dtest=RunBalanceSimTest -Dsurefire.failIfNoSpecifiedTests=false \
 *     -Droguelike.sim.runs=20 [-Droguelike.sim.fights=10] [-Droguelike.sim.starter=jill_mono_blue] \
 *     [-Droguelike.sim.layout=FIGHTS,UPGRADE,STRETCH 2,BOSS] [-Droguelike.sim.timeout=120] [-Droguelike.sim.logs=true] [-Droguelike.sim.progress=true]
 *     [-Droguelike.sim.policy=greedy] [-Droguelike.sim.xp=N] [-Droguelike.sim.encounters=id,id]
 *     [-Droguelike.sim.heroProfile=Reckless] [-Droguelike.sim.heroAi=hybrid|full]
 * </pre>
 *
 * The report and a CSV of every match are written to forge-gui-desktop/target/roguelike-sim/; with
 * roguelike.sim.logs, every match's game log goes to its logs/ folder. With roguelike.sim.xp, every run and
 * single encounter starts with N experience on the character and the account (their unlocks, all switched on),
 * and nothing is recorded, so all of them start at the same levels.
 */
public class RunBalanceSimTest extends AITest {

    @Test(timeOut = 1800000)
    public void simulatorProducesAReport() throws Exception {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        c.getConfig().mapLayout = List.of("FIGHTS", "STRETCH 1", "BOSS");
        RunSimulator sim = new RunSimulator(c);
        sim.setMatchTimeoutSeconds(90);
        RunRecord run = sim.simulateRun("jill_mono_blue", 1, new RandomPolicy(1));
        RunRecord greedy = sim.simulateRun("lightning_rw", 2, new GreedyPolicy());
        assertTrue(greedy.status != RunStatus.IN_PROGRESS);
        assertTrue(run.status != RunStatus.IN_PROGRESS);
        assertTrue(!run.matches.isEmpty());
        File logs = new File("target/roguelike-sim-smoke-logs");
        sim.setLogDir(logs);
        MatchRecord fight = sim.simulateEncounter("jill_mono_blue", "goblin_raiders", 20, 2);
        assertEquals(20, fight.heroLifeBefore);
        assertTrue(fight.turns > 0);
        File[] written = logs.listFiles((d, n) -> n.contains("goblin_raiders"));
        assertTrue(written != null && written.length > 0);
        String log = Files.readString(written[written.length - 1].toPath());
        assertTrue(log, log.contains("Goblin Raiders") && log.contains("TURN: Turn 1"));

        // a read-only profile at Jill level 2 (+2 run max life) reaches single encounters too
        MetaProfile levelTwo = new MetaProfile();
        levelTwo.character("jill").xp = 30;
        sim.setProfile(levelTwo, false);
        sim.setLogDir(null);
        MatchRecord unlocked = sim.simulateEncounter("jill_mono_blue", "troop_of_dwarves", 22, 3);
        assertEquals(22, unlocked.heroLifeBefore);
        assertEquals(30, levelTwo.character("jill").xp); // read, not recorded
        sim.setProfile(null);

        String report = SimReport.markdown("Smoke", List.of(run), List.of(fight));
        assertTrue(report, report.contains("## Whole runs"));
        assertTrue(report, report.contains("## Single encounters"));
        assertTrue(report, report.contains("| Goblin Raiders | 1 |"));
        String csv = SimReport.matchesCsv(run.matches);
        assertEquals(run.matches.size() + 1, csv.split("\n").length);
    }

    @Test(timeOut = 86400000)
    public void fullSimulation() throws Exception {
        int runs = Integer.getInteger("roguelike.sim.runs", 0);
        if (runs <= 0) {
            throw new SkipException("set -Droguelike.sim.runs=N to run the balance simulation");
        }
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        String starter = System.getProperty("roguelike.sim.starter", c.getStarters().keySet().iterator().next());
        String layout = System.getProperty("roguelike.sim.layout");
        if (layout != null) {
            c.getConfig().mapLayout = List.of(layout.split(","));
        }
        int fights = Integer.getInteger("roguelike.sim.fights", 0);
        RunSimulator sim = new RunSimulator(c);
        sim.setMatchTimeoutSeconds(Integer.getInteger("roguelike.sim.timeout", 120));
        String heroAi = System.getProperty("roguelike.sim.heroAi", "");
        sim.setHeroAi(System.getProperty("roguelike.sim.heroProfile"), "full".equals(heroAi) ? forge.ai.AIOption.USE_FULL_SIMULATION
                : "hybrid".equals(heroAi) ? forge.ai.AIOption.USE_HYBRID_SIMULATION : null);
        MetaProfile profile = Boolean.getBoolean("roguelike.sim.progress") ? new MetaProfile() : null;
        sim.setProfile(profile);
        long fixedXp = Long.getLong("roguelike.sim.xp", -1L);
        if (fixedXp >= 0) {
            MetaProfile fixed = new MetaProfile();
            fixed.accountXp = fixedXp;
            CharacterDef ch = forge.gamemodes.roguelike.meta.Progression.characterForStarter(c, starter);
            if (ch != null) {
                fixed.character(ch.id).xp = fixedXp;
            }
            sim.setProfile(fixed, false);
        }
        if (Boolean.getBoolean("roguelike.sim.logs")) {
            sim.setLogDir(new File("target/roguelike-sim/logs"));
        }

        List<RunRecord> records = new ArrayList<>();
        List<MatchRecord> all = new ArrayList<>();
        for (long seed = 1; seed <= runs; seed++) {
            RunPolicy policy = "greedy".equals(System.getProperty("roguelike.sim.policy")) ? new GreedyPolicy() : new RandomPolicy(seed);
            RunRecord r = sim.simulateRun(starter, seed, policy);
            records.add(r);
            all.addAll(r.matches);
            System.out.println("sim run " + seed + "/" + runs + ": " + r.status + " after " + r.matches.size() + " matches");
        }
        List<MatchRecord> encounterTests = new ArrayList<>();
        List<String> only = System.getProperty("roguelike.sim.encounters") == null ? null
                : List.of(System.getProperty("roguelike.sim.encounters").split(","));
        for (EncounterDef e : c.getEncounters().values()) {
            if (only != null && !only.contains(e.id)) {
                continue;
            }
            for (int i = 0; i < fights; i++) {
                encounterTests.add(sim.simulateEncounter(starter, e.id, c.getConfig().startingMaxLife, 1000 + i));
            }
        }
        all.addAll(encounterTests);

        File dir = new File("target/roguelike-sim");
        dir.mkdirs();
        String report = SimReport.markdown("Run-mode simulation: " + starter + ", " + runs + " runs"
                + (System.getProperty("roguelike.sim.heroProfile") != null ? ", hero profile " + System.getProperty("roguelike.sim.heroProfile") : "")
                + (heroAi.isEmpty() ? "" : ", hero simulation " + heroAi)
                + (fixedXp >= 0 ? ", hero at " + fixedXp + " XP" : ""), records, encounterTests);
        if (profile != null) {
            StringBuilder sb = new StringBuilder(report).append("\n## Progression (runs played with a fresh profile, all unlocks on)\n\n");
            for (CharacterDef ch : c.getCharacters().values()) {
                sb.append("- ").append(ProgressText.levelLine(c, ch, profile.character(ch.id))).append('\n');
            }
            sb.append("- ").append(ProgressText.accountLine(c, profile)).append('\n');
            report = sb.toString();
        }
        Files.writeString(new File(dir, "report.md").toPath(), report, StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "matches.csv").toPath(), SimReport.matchesCsv(all), StandardCharsets.UTF_8);
        System.out.println(report);
    }
}
