package forge.gamemodes.roguelike;

import java.io.File;

import forge.LobbyPlayer;
import forge.game.GameView;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.roguelike.RunMatchFactory.RunMatch;
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

    public Run startRun(final String starterId) {
        run = Run.start(getContent(), starterId, System.nanoTime());
        runLoaded = true;
        save();
        return run;
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
        boolean won = run.finishMatch(m, view.getGame());
        save();
        return won;
    }
}
