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
import forge.gamemodes.roguelike.sim.MatchRecord;
import forge.gamemodes.roguelike.sim.RandomPolicy;
import forge.gamemodes.roguelike.sim.RunRecord;
import forge.gamemodes.roguelike.sim.RunSimulator;
import forge.gamemodes.roguelike.sim.SimReport;

/**
 * The balance simulator. A small smoke test runs with the suite; a full simulation runs only when asked:
 *
 * <pre>
 * xvfb-run -a mvn -pl forge-gui-desktop -am test -Dtest=RunBalanceSimTest -Dsurefire.failIfNoSpecifiedTests=false \
 *     -Droguelike.sim.runs=20 [-Droguelike.sim.fights=10] [-Droguelike.sim.starter=jill_mono_blue] \
 *     [-Droguelike.sim.rows=7] [-Droguelike.sim.timeout=120] [-Droguelike.sim.logs=true]
 * </pre>
 *
 * The report and a CSV of every match are written to forge-gui-desktop/target/roguelike-sim/; with
 * roguelike.sim.logs, every match's game log goes to its logs/ folder.
 */
public class RunBalanceSimTest extends AITest {

    @Test(timeOut = 1800000)
    public void simulatorProducesAReport() throws Exception {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        c.getConfig().mapRows = 2;
        RunSimulator sim = new RunSimulator(c);
        sim.setMatchTimeoutSeconds(90);
        RunRecord run = sim.simulateRun("jill_mono_blue", 1, new RandomPolicy(1));
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
        c.getConfig().mapRows = Integer.getInteger("roguelike.sim.rows", c.getConfig().mapRows);
        int fights = Integer.getInteger("roguelike.sim.fights", 0);
        RunSimulator sim = new RunSimulator(c);
        sim.setMatchTimeoutSeconds(Integer.getInteger("roguelike.sim.timeout", 120));
        if (Boolean.getBoolean("roguelike.sim.logs")) {
            sim.setLogDir(new File("target/roguelike-sim/logs"));
        }

        List<RunRecord> records = new ArrayList<>();
        List<MatchRecord> all = new ArrayList<>();
        for (long seed = 1; seed <= runs; seed++) {
            RunRecord r = sim.simulateRun(starter, seed, new RandomPolicy(seed));
            records.add(r);
            all.addAll(r.matches);
            System.out.println("sim run " + seed + "/" + runs + ": " + r.status + " after " + r.matches.size() + " matches");
        }
        List<MatchRecord> encounterTests = new ArrayList<>();
        for (EncounterDef e : c.getEncounters().values()) {
            for (int i = 0; i < fights; i++) {
                encounterTests.add(sim.simulateEncounter(starter, e.id, c.getConfig().startingMaxLife, 1000 + i));
            }
        }
        all.addAll(encounterTests);

        File dir = new File("target/roguelike-sim");
        dir.mkdirs();
        String report = SimReport.markdown("Run-mode simulation: " + starter + ", " + runs + " runs", records, encounterTests);
        Files.writeString(new File(dir, "report.md").toPath(), report, StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "matches.csv").toPath(), SimReport.matchesCsv(all), StandardCharsets.UTF_8);
        System.out.println(report);
    }
}
