package forge.screens.roguelike;

import java.util.List;

import forge.assets.FSkinFont;
import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Offer;
import forge.gamemodes.roguelike.OfferKind;
import forge.gamemodes.roguelike.OfferOption;
import forge.gamemodes.roguelike.RoguelikeController;
import forge.gamemodes.roguelike.Run;
import forge.gamemodes.roguelike.RunStatus;
import forge.gamemodes.roguelike.RunText;
import forge.gamemodes.roguelike.StarterDef;
import forge.screens.FScreen;
import forge.screens.home.NewGameMenu;
import forge.toolbox.FButton;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FEvent.FEventHandler;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FTextArea;
import forge.util.Utils;

/**
 * Prototype screen for the roguelike run mode: one list of actions for whatever the run needs next
 * (pick a starter, pick a node, fight, pick a reward, shop). The intended home is Adventure (DESIGN.md 2.8).
 */
public class RoguelikeScreen extends FScreen {
    private static final float PADDING = Utils.scale(5f);

    private final FTextArea status = add(new FTextArea(false));
    private final FScrollPane actions = add(new FScrollPane() {
        @Override
        protected ScrollBounds layoutAndGetScrollBounds(float visibleWidth, float visibleHeight) {
            float y = 0;
            for (FDisplayObject child : getChildren()) {
                float h = child instanceof FTextArea t ? t.getPreferredHeight(visibleWidth) : Utils.AVG_FINGER_HEIGHT;
                child.setBounds(0, y, visibleWidth, h);
                y += h + PADDING;
            }
            return new ScrollBounds(visibleWidth, y);
        }
    });

    public RoguelikeScreen() {
        super(null, NewGameMenu.getMenu());
        status.setFont(FSkinFont.get(14));
    }

    @Override
    public void onActivate() {
        update();
    }

    private RoguelikeController ctrl() {
        return RoguelikeController.get();
    }

    private void update() {
        actions.clear();
        Run run;
        try {
            run = ctrl().getRun();
        } catch (RuntimeException e) {
            status.setText("Could not load the run-mode content:\n" + e.getMessage());
            revalidate();
            return;
        }
        if (run == null) {
            status.setText("Roguelike run (prototype)\n\nChoose a character to start a run.");
            for (StarterDef s : ctrl().getContent().getStarters().values()) {
                button(s.name, e -> {
                    ctrl().startRun(s.id);
                    update();
                });
            }
            revalidate();
            return;
        }

        status.setText(RunText.status(run));
        if (run.getState().status != RunStatus.IN_PROGRESS) {
            button("New run", e -> {
                ctrl().abandonRun();
                update();
            });
            text(String.join("\n", run.getState().log));
            revalidate();
            return;
        }

        Offer offer = run.getState().offer;
        if (offer != null) {
            List<OfferOption> options = offer.options;
            for (int i = 0; i < options.size(); i++) {
                final int index = i;
                OfferOption o = options.get(i);
                FButton b = button(RunText.option(run, o), e -> act(() -> {
                    if (offer.kind == OfferKind.SHOP) {
                        run.buy(index);
                    } else {
                        run.choose(index);
                    }
                }));
                if (offer.kind == OfferKind.SHOP && (o.bought || o.price > run.getState().gold)) {
                    b.setEnabled(false);
                }
            }
            if (offer.canSkip) {
                button(offer.kind == OfferKind.SHOP ? "Leave the shop" : "Skip", e -> act(run::skip));
            }
        } else if (run.getActiveEncounter() != null) {
            button("Fight: " + run.getActiveEncounter().name, e -> ctrl().startMatch());
        } else {
            for (MapNode n : run.getAvailableNodes()) {
                button(RunText.node(run, n), e -> act(() -> run.enterNode(n.id)));
            }
        }

        button("View deck and relics", e -> FOptionPane.showMessageDialog(RunText.deckList(run), "Your deck"));
        button("Abandon run", e -> FOptionPane.showConfirmDialog("Abandon this run? It can't be resumed.",
                "Abandon run", yes -> {
                    if (yes) {
                        ctrl().abandonRun();
                        update();
                    }
                }));
        revalidate();
    }

    /** Run a step of the run, save, and redraw. */
    private void act(final Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(e.getMessage(), "Can't do that");
        }
        ctrl().save();
        update();
    }

    private FButton button(final String caption, final FEventHandler handler) {
        return actions.add(new FButton(caption, handler));
    }

    private void text(final String content) {
        FTextArea t = actions.add(new FTextArea(false, content));
        t.setFont(FSkinFont.get(12));
    }

    @Override
    protected void doLayout(float startY, float width, float height) {
        float x = PADDING;
        float w = width - 2 * PADDING;
        float y = startY + PADDING;
        float statusHeight = status.getPreferredHeight(w);
        status.setBounds(x, y, w, statusHeight);
        y += statusHeight + PADDING;
        actions.setBounds(x, y, w, height - y - PADDING);
    }
}
