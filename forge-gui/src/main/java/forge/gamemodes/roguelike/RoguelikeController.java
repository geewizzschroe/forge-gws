package forge.gamemodes.roguelike;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import forge.LobbyPlayer;
import forge.game.GameView;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.roguelike.RunMatchFactory.RunMatch;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.Loadout;
import forge.gamemodes.roguelike.meta.MatchStatsTracker;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.Progression;
import forge.gamemodes.roguelike.meta.RunProgress;
import forge.gui.FThreads;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgeConstants;
import forge.player.GamePlayerUtil;

/**
 * Glue between the run and the app: keeps the current run, saves it after every step, and plays its
 * matches through the normal match screen. Screens call this; the rules live in {@link Run}.
 */
public final class RoguelikeController {
    private static final RoguelikeController INSTANCE = new RoguelikeController();

    private RoguelikeContent content;
    private Run run;
    private boolean runLoaded;
    private RunMatch activeMatch;
    private MatchStatsTracker tracker;
    private MetaProfile profile;
    /** Messages from the last match (levels, milestones) not yet shown to the player. */
    private final List<String> pendingMessages = new ArrayList<>();


    private RoguelikeController() {
    }

    public static RoguelikeController get() {
        return INSTANCE;
    }

    public RoguelikeContent getContent() {
        if (content == null) {
            content = RoguelikeContent.load();
        }
        return content;
    }

    public File getSaveFile() {
        return new File(ForgeConstants.USER_DIR + "roguelike" + File.separator + "run.json");
    }

    /** The run in progress or just finished, or null if there is none. */
    public Run getRun() {
        if (!runLoaded) {
            runLoaded = true;
            File f = getSaveFile();
            if (f.isFile()) {
                try {
                    run = Run.load(getContent(), f);
                } catch (RuntimeException e) {
                    System.err.println("Could not load the saved run: " + e);
                    run = null;
                }
            }
        }
        return run;
    }

    public File getProfileFile() {
        return new File(ForgeConstants.USER_DIR + "roguelike" + File.separator + "profile.json");
    }

    public MetaProfile getProfile() {
        if (profile == null) {
            try {
                profile = MetaProfile.load(getProfileFile());
            } catch (RuntimeException e) {
                System.err.println("Could not load run-mode progress: " + e);
                profile = new MetaProfile();
            }
        }
        return profile;
    }

    public void saveProfile() {
        getProfile().save(getProfileFile());
    }

    /** Start a run for a character with the given loadout; the loadout is remembered for next time. */
    public Run startRun(final CharacterDef character, final Loadout loadout) {
        getProfile().character(character.id).loadout = loadout;
        saveProfile();
        RunSetup setup = Progression.setup(getContent(), getProfile(), character, loadout);
        run = Run.start(getContent(), loadout.starterId, System.nanoTime(), setup);
        runLoaded = true;
        save();
        return run;
    }

    /** Start a run with a starter and no meta-progression. */
    public Run startRun(final String starterId) {
        run = Run.start(getContent(), starterId, System.nanoTime());
        runLoaded = true;
        save();
        return run;
    }

    /** Messages about levels and milestones since the last call. */
    public List<String> takeMessages() {
        List<String> out = new ArrayList<>(pendingMessages);
        pendingMessages.clear();
        return out;
    }

    public void abandonRun() {
        run = null;
        runLoaded = true;
        activeMatch = null;
        File f = getSaveFile();
        if (f.isFile() && !f.delete()) {
            System.err.println("Could not delete " + f);
        }
    }

    public void save() {
        if (run == null) {
            return;
        }
        File f = getSaveFile();
        f.getParentFile().mkdirs();
        run.save(f);
    }

    /** Start the active node's match on the match screen. */
    public void startMatch() {
        EncounterDef enc = run.getActiveEncounter();
        if (enc == null) {
            throw new IllegalStateException("No match to play now");
        }
        LobbyPlayer human = GamePlayerUtil.getGuiPlayer();
        String aiName = enc.name.equals(human.getName()) ? enc.name + " (AI)" : enc.name;
        LobbyPlayer ai = GamePlayerUtil.createAiPlayer(aiName, -1, -1);
        final RunMatch m = run.createMatch(human, ai);
        activeMatch = m;
        final IGuiGame gui = GuiBase.getInterface().getNewGuiGame();
        final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();
        tracker = null;
        hostedMatch.setStartGameHook(() -> tracker = MatchStatsTracker.attach(hostedMatch.getGame(), m.hero()));
        FThreads.invokeInEdtNowOrLater(() -> hostedMatch.startMatch(m.match().getRules(), null,
                m.match().getPlayers(), m.hero(), gui));
    }

    /** Apply the finished match to the run (once) and save. Returns true if the player won. */
    public boolean finishMatch(final GameView view) {
        if (activeMatch == null || view == null || view.getGame() == null) {
            return false;
        }
        RunMatch m = activeMatch;
        activeMatch = null;
        long xpBefore = run.getState().xpEarned;
        boolean won = run.finishMatch(m, view.getGame());
        pendingMessages.addAll(RunProgress.afterMatch(getContent(), getProfile(), run, m.encounter(), won, xpBefore,
                tracker == null ? null : tracker.getCounters()));
        saveProfile();
        tracker = null;
        save();
        return won;
    }

    /** Call after any run step: records the run-won experience once, when the run has just been won. */
    public void afterStep() {
        if (run == null) {
            return;
        }
        List<String> messages = RunProgress.afterStep(getContent(), getProfile(), run);
        if (!messages.isEmpty()) {
            pendingMessages.addAll(messages);
            saveProfile();
            save();
        }
    }
}
