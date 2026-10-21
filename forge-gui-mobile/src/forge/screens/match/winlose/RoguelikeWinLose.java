package forge.screens.match.winlose;

import forge.game.GameView;
import forge.gamemodes.roguelike.RoguelikeController;

/** End of a roguelike-run match: the result goes back to the run, then the run screen shows again. */
public class RoguelikeWinLose extends ControlWinLose {
    public RoguelikeWinLose(final ViewWinLose view0, final GameView lastGame0) {
        super(view0, lastGame0);
        view0.getBtnContinue().setVisible(false);
        view0.getBtnRestart().setVisible(false);
    }

    @Override
    public void actionOnQuit() {
        RoguelikeController.get().finishMatch(lastGame);
        super.actionOnQuit();
    }
}
