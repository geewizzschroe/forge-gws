package forge.gamemodes.roguelike.sim;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import forge.ai.AIOption;
import forge.ai.LobbyPlayerAi;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameLogEntry;
import forge.game.player.Player;
import forge.gamemodes.roguelike.EncounterDef;
import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.gamemodes.roguelike.Run;
import forge.gamemodes.roguelike.RunMatchFactory;
import forge.gamemodes.roguelike.RunMatchFactory.RunMatch;
import forge.gamemodes.roguelike.RunState;
import forge.gamemodes.roguelike.RunSetup;
import forge.gamemodes.roguelike.RunStatus;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.Loadout;
import forge.gamemodes.roguelike.meta.MatchStatsTracker;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.Progression;
import forge.gamemodes.roguelike.meta.RunProgress;

/**
 * Plays the run mode with the AI on both sides, for balancing. The card database must be loaded
 * (FModel initialised) before use. Matches run one at a time.
 *
 * Two kinds of simulation: whole runs ({@link #simulateRun}), where a {@link RunPolicy} makes the
 * run's decisions; and single encounters ({@link #simulateEncounter}), where the starter deck fights
 * one enemy from a fixed life total, to measure that enemy on its own.
 */
public class RunSimulator {
    private final RoguelikeContent content;
    private long matchTimeoutSeconds = 120;
    private File logDir;
    private int logCount;
    private MetaProfile profile;
    private boolean recordProgress = true;
    private String heroProfile;
    private AIOption heroOption;
    private MatchStatsTracker tracker;

    public RunSimulator(final RoguelikeContent content) {
        this.content = content;
    }

    public void setMatchTimeoutSeconds(final long seconds) {
        matchTimeoutSeconds = seconds;
    }

    /**
     * Play runs with meta-progression: each run starts from the character's unlocks in this profile (all
     * switched on) and its experience and counters are recorded into it. Null for plain runs.
     */
    public void setProfile(final MetaProfile profile) {
        setProfile(profile, true);
    }

    /**
     * As {@link #setProfile(MetaProfile)}; with {@code record} false the profile is only read, so every run
     * and encounter starts at the same levels (for comparing content against a hero at a given level).
     * Single encounters use the profile's unlocks too.
     */
    public void setProfile(final MetaProfile profile, final boolean record) {
        this.profile = profile;
        this.recordProgress = record;
    }

    /** The run setup from the profile's unlocks for this starter's character, or null without a profile. */
    private RunSetup setupFor(final String starterId) {
        if (profile == null) {
            return null;
        }
        CharacterDef ch = Progression.characterForStarter(content, starterId);
        if (ch == null) {
            return null;
        }
        Loadout loadout = new Loadout();
        loadout.starterId = starterId;
        return Progression.setup(content, profile, ch, loadout);
    }

    /**
     * How the AI pilots the hero: a profile from res/ai (Default, Cautious, Reckless, Experimental; null for the
     * built-in defaults) and an optional simulation mode (slower; it looks ahead before acting). Enemies keep the default.
     */
    public void setHeroAi(final String profile, final AIOption option) {
        heroProfile = profile;
        heroOption = option;
    }

    private LobbyPlayerAi hero() {
        LobbyPlayerAi hero = new LobbyPlayerAi("Hero", heroOption == null ? null : Set.of(heroOption));
        if (heroProfile != null) {
            hero.setAiProfile(heroProfile);
        }
        return hero;
    }

    /** Write each match's game log to a numbered file in this folder (null to stop). */
    public void setLogDir(final File dir) {
        logDir = dir;
        if (dir != null) {
            dir.mkdirs();
        }
    }

    public RunRecord simulateRun(final String starterId, final long seed, final RunPolicy policy) {
        Run run = Run.start(content, starterId, seed, setupFor(starterId));
        RunState s = run.getState();
        RunRecord rec = new RunRecord();
        rec.seed = seed;
        rec.starterId = starterId;
        int logSeen = s.log.size();
        for (int step = 0; step < 500 && s.status == RunStatus.IN_PROGRESS; step++) {
            if (s.offer != null) {
                policy.resolveOffer(run);
            } else if (run.getActiveEncounter() != null) {
                EncounterDef enc = run.getActiveEncounter();
                MatchRecord m = new MatchRecord();
                m.runSeed = seed;
                m.encounterId = enc.id;
                m.encounterName = enc.name;
                m.boss = enc.boss;
                m.floor = s.floor;
                m.row = run.getActiveNode().row;
                m.heroLifeBefore = s.life;
                RunMatch match = run.createMatch(hero(), new LobbyPlayerAi(enc.name, null));
                long xpBefore = s.xpEarned;
                boolean won = run.finishMatch(match, play(match, m));
                if (profile != null && recordProgress) {
                    rec.progressMessages.addAll(RunProgress.afterMatch(content, profile, run, enc, won, xpBefore,
                            tracker == null ? null : tracker.getCounters()));
                }
                rec.matches.add(m);
                if (s.status == RunStatus.LOST) {
                    rec.endedBy = enc.name;
                }
            } else {
                List<MapNode> next = run.getAvailableNodes();
                if (next.isEmpty()) {
                    throw new IllegalStateException("Run " + seed + " is stuck: " + s.log);
                }
                MapNode node = policy.chooseNode(run, next);
                rec.nodesEntered.merge(node.type.name(), 1, Integer::sum);
                run.enterNode(node.id);
            }
            if (profile != null && recordProgress) {
                rec.progressMessages.addAll(RunProgress.afterStep(content, profile, run));
            }
            for (; logSeen < s.log.size(); logSeen++) {
                String line = s.log.get(logSeen);
                if (line.startsWith("Chose ") || line.startsWith("Bought ")) {
                    rec.choices.add(line);
                }
            }
        }
        rec.status = s.status;
        rec.floor = s.floor;
        rec.finalDeckSize = s.deckSize();
        rec.finalLife = s.life;
        rec.finalMaxLife = s.maxLife;
        rec.goldLeft = s.gold;
        rec.relics.addAll(s.relics);
        rec.xpEarned = s.xpEarned;
        return rec;
    }

    private int encounterFloor = 1;

    /** Single encounters are fought as on this floor (its enemy life, upgrades and Base ability). */
    public void setEncounterFloor(final int floor) {
        this.encounterFloor = Math.max(1, floor);
    }

    /** The starter deck fights one enemy, starting at the given life, without any run progress (but with the profile's unlocks, if set). */
    public MatchRecord simulateEncounter(final String starterId, final String encounterId, final int heroLife, final long seed) {
        Run run = Run.start(content, starterId, seed, setupFor(starterId));
        RunState s = run.getState();
        s.life = Math.min(heroLife, s.maxLife);
        s.floor = encounterFloor;
        EncounterDef enc = content.getEncounter(encounterId);
        MatchRecord m = new MatchRecord();
        m.runSeed = seed;
        m.encounterId = enc.id;
        m.encounterName = enc.name;
        m.boss = enc.boss;
        m.floor = encounterFloor;
        m.row = -1;
        m.heroLifeBefore = s.life;
        RunMatch match = RunMatchFactory.create(content, s, enc, hero(), new LobbyPlayerAi(enc.name, null));
        play(match, m);
        return m;
    }

    private void writeLog(final Game game, final MatchRecord m) {
        List<GameLogEntry> entries = game.getGameLog().getLogEntries(null);
        StringBuilder sb = new StringBuilder();
        sb.append("Run seed ").append(m.runSeed).append(", ").append(m.encounterName)
          .append(m.won ? ", hero won" : ", hero lost").append(m.timedOut ? " (timed out)" : "").append('\n');
        for (int i = entries.size() - 1; i >= 0; i--) { // the log is newest first
            sb.append(entries.get(i).type()).append(": ").append(entries.get(i).message()).append('\n');
        }
        String name = String.format(java.util.Locale.ROOT, "%04d-seed%d-%s.txt", ++logCount, m.runSeed, m.encounterId);
        try {
            Files.writeString(new File(logDir, name).toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write " + name + ": " + e);
        }
    }

    private Game play(final RunMatch match, final MatchRecord m) {
        final Game game = match.match().createGame();
        game.setNoGUIUser();
        tracker = null;
        long start = System.currentTimeMillis();
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> f = exec.submit(() -> match.match().startGame(game,
                () -> tracker = MatchStatsTracker.attach(game, match.hero())));
        exec.shutdown();
        try {
            f.get(matchTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            m.timedOut = true;
            game.setGameOver(GameEndReason.Draw);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Match against " + m.encounterName + " failed", e.getCause());
        }
        m.millis = System.currentTimeMillis() - start;
        m.won = game.getOutcome() != null && !game.getOutcome().isDraw() && game.getOutcome().isWinner(match.hero());
        m.turns = game.getPhaseHandler().getTurn();
        for (Player p : game.getRegisteredPlayers()) {
            if (p.getRegisteredPlayer() == match.hero()) {
                m.heroLifeAfter = p.getLife();
            }
        }
        if (logDir != null) {
            writeLog(game, m);
        }
        return game;
    }
}
