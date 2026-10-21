package forge.screens.roguelike;

import java.util.ArrayList;
import java.util.List;

import forge.StaticData;
import forge.assets.FSkinFont;
import forge.card.CardZoom;
import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Offer;
import forge.gamemodes.roguelike.OfferKind;
import forge.gamemodes.roguelike.OfferOption;
import forge.gamemodes.roguelike.RoguelikeController;
import forge.gamemodes.roguelike.Run;
import forge.gamemodes.roguelike.RunStatus;
import forge.gamemodes.roguelike.RunText;
import forge.gamemodes.roguelike.StarterDef;
import forge.item.PaperCard;
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
                float h;
                if (child instanceof RunMapView) {
                    h = Math.max(visibleHeight * 0.6f, Utils.AVG_FINGER_HEIGHT * 4);
                } else if (child instanceof FTextArea t) {
                    h = t.getPreferredHeight(visibleWidth);
                } else {
                    h = Utils.AVG_FINGER_HEIGHT;
                }
                child.setBounds(0, y, visibleWidth, h);
                y += h + PADDING;
            }
            return new ScrollBounds(visibleWidth, y);
        }
    });

    /** The map node whose details are shown; reset when it stops being a choice. */
    private Integer selectedNode;

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
            status.setText(RunText.summary(run));
            button("New run", e -> {
                ctrl().abandonRun();
                update();
            });
            text("Run log:\n" + String.join("\n", run.getState().log));
            revalidate();
            return;
        }

        Offer offer = run.getState().offer;
        if (offer != null) {
            List<OfferOption> options = offer.options;
            addCardViewer(run, offer);
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
            List<PaperCard> bases = new ArrayList<>();
            PaperCard enemyBase = run.getEncounterBase(run.getActiveEncounter());
            if (enemyBase != null) {
                bases.add(enemyBase);
            }
            PaperCard playerBase = run.getPlayerBase();
            if (playerBase != null) {
                bases.add(playerBase);
            }
            if (!bases.isEmpty()) {
                button("Look at the Bases", e -> CardZoom.show(bases, 0, null));
            }
        } else {
            List<MapNode> available = run.getAvailableNodes();
            if (selectedNode == null || run.getState().map.nodes.size() <= selectedNode) {
                selectedNode = available.isEmpty() ? null : available.get(0).id;
            }
            actions.add(new RunMapView(run, selectedNode, id -> {
                selectedNode = id;
                update();
            }));
            if (selectedNode != null) {
                MapNode sel = run.getState().map.get(selectedNode);
                text("Step " + (sel.row + 1) + " - " + RunText.nodeDetail(run, sel));
                if (available.contains(sel)) {
                    button("Go: " + RunText.node(run, sel), e -> act(() -> {
                        selectedNode = null;
                        run.enterNode(sel.id);
                    }));
                } else {
                    text("You can't go there from here. Ringed nodes are where you can go next.");
                }
            }
        }

        button("Look at your deck", e -> {
            List<PaperCard> deck = new ArrayList<>();
            deck.add(card(run.getState().commander));
            PaperCard base = run.getPlayerBase();
            if (base != null) {
                deck.add(base);
            }
            for (String name : run.getState().deck.keySet()) {
                deck.add(card(name));
            }
            CardZoom.show(deck, 0, null);
        });
        button("Deck list, upgrades and relics", e -> FOptionPane.showMessageDialog(RunText.deckList(run), "Your deck"));
        button("Abandon run", e -> FOptionPane.showConfirmDialog("Abandon this run? It can't be resumed.",
                "Abandon run", yes -> {
                    if (yes) {
                        ctrl().abandonRun();
                        update();
                    }
                }));
        revalidate();
    }

    /**
     * A "Look at the cards" button for an offer: swipe through every card on offer and take, buy, cut or
     * upgrade the one shown.
     */
    private void addCardViewer(final Run run, final Offer offer) {
        final List<PaperCard> cards = new ArrayList<>();
        final List<Integer> owner = new ArrayList<>();
        for (int i = 0; i < offer.options.size(); i++) {
            OfferOption o = offer.options.get(i);
            List<String> names = new ArrayList<>(o.cards.keySet());
            if (o.cutCard != null) {
                names.add(o.cutCard);
            }
            if (o.upgradeCard != null && o.cards.isEmpty()) {
                names.add(o.upgradeCard);
            }
            for (String name : names) {
                cards.add(card(name));
                owner.add(i);
            }
        }
        if (cards.isEmpty()) {
            return;
        }
        final CardZoom.ActivateHandler handler = new CardZoom.ActivateHandler() {
            @Override
            public String getActivateAction(int index) {
                OfferOption o = offer.options.get(owner.get(index));
                return switch (offer.kind) {
                    case SHOP -> o.bought || o.price > run.getState().gold ? null : "Buy for " + o.price + " gold";
                    case CUT -> "Cut " + o.cutCard;
                    case UPGRADE -> "Upgrade: " + o.upgrade.describe();
                    default -> o.label != null ? "Take " + o.label : "Take " + RunText.option(run, o);
                };
            }

            @Override
            public void setSelectedIndex(int index) {
            }

            @Override
            public void activate(int index) {
                final int option = owner.get(index);
                act(() -> {
                    if (offer.kind == OfferKind.SHOP) {
                        run.buy(option);
                    } else {
                        run.choose(option);
                    }
                });
            }
        };
        button("Look at the cards", e -> CardZoom.show(cards, 0, handler));
    }

    private static PaperCard card(final String name) {
        return StaticData.instance().getCommonCards().getCard(name);
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
