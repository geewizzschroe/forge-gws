package forge.screens.roguelike;

import java.util.ArrayList;
import java.util.List;

import forge.assets.FSkinFont;
import forge.card.CardZoom;
import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Offer;
import forge.gamemodes.roguelike.OfferKind;
import forge.gamemodes.roguelike.OfferOption;
import forge.gamemodes.roguelike.RoguelikeController;
import forge.gamemodes.roguelike.RoguelikeDebug;
import forge.gamemodes.roguelike.RunPreview;
import forge.gamemodes.roguelike.RunUpgrade;
import forge.gamemodes.roguelike.Run;
import forge.gamemodes.roguelike.RunStatus;
import forge.gamemodes.roguelike.RunText;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.gamemodes.roguelike.StarterDef;
import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.CharacterProgress;
import forge.gamemodes.roguelike.meta.Loadout;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.ProgressText;
import forge.gamemodes.roguelike.meta.Progression;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences;
import forge.model.FModel;
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
        showMessages();
    }

    /** Show level-ups and milestones earned since the last look, one after another. */
    private void showMessages() {
        List<String> messages = ctrl().takeMessages();
        if (!messages.isEmpty()) {
            FOptionPane.showMessageDialog(String.join("\n\n", messages), "Progress");
        }
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
            showRunStart();
            addDebugTools(null);
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
            addDebugTools(run);
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
                String tag = o.source != null && devMode() ? "[" + o.source + "] " : "";
                FButton b = button(tag + RunText.option(run, o), e -> act(() -> {
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
                button(offer.kind == OfferKind.SHOP ? "Leave the shop" : offer.returnToShop != null ? "Back to the shop" : "Skip", e -> act(run::skip));
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
            List<Object> deck = new ArrayList<>();
            deck.add(RunPreview.cardView(run, run.getState().commander, null));
            PaperCard base = run.getPlayerBase();
            if (base != null) {
                deck.add(base);
            }
            for (String name : run.getState().deck.keySet()) {
                deck.add(RunPreview.cardView(run, name, null));
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
        addDebugTools(run);
        revalidate();
    }

    /**
     * A "Look at the cards" button for an offer: swipe through every card on offer and take, buy, cut or
     * upgrade the one shown.
     */
    private void addCardViewer(final Run run, final Offer offer) {
        final List<Object> cards = new ArrayList<>();
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
                // show the card as it would be after taking this option (its upgrade included)
                RunUpgrade extra = name.equals(o.upgradeCard) ? o.upgrade : null;
                cards.add(RunPreview.cardView(run, name, extra));
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

    /** Run a step of the run, save, and redraw. */
    private void act(final Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(e.getMessage(), "Can't do that");
        }
        ctrl().afterStep();
        ctrl().save();
        update();
        showMessages();
    }

    // ---------------------------------------------------------------- debug tools

    private boolean showDebug;

    private static boolean devMode() {
        return FModel.getPreferences().getPrefBoolean(ForgePreferences.FPref.DEV_MODE_ENABLED);
    }

    /** Testing shortcuts, behind a toggle at the bottom of the list. Shown only with Forge's developer mode on. */
    private void addDebugTools(final Run run) {
        if (!devMode()) {
            return;
        }
        button(showDebug ? "Hide debug tools" : "Debug tools", e -> {
            showDebug = !showDebug;
            update();
        });
        if (!showDebug) {
            return;
        }
        RoguelikeContent content = ctrl().getContent();
        MetaProfile profile = ctrl().getProfile();
        if (run == null) {
            List<CharacterDef> chars = chosenCharacter != null ? List.of(chosenCharacter) : new ArrayList<>(content.getCharacters().values());
            for (CharacterDef ch : chars) {
                button("Debug: " + ch.name + " +500 experience", e -> debug(RoguelikeDebug.grantXp(content, profile, ch.id, 500)));
                button("Debug: " + ch.name + " to level " + RoguelikeDebug.maxLevel(ch),
                        e -> debug(RoguelikeDebug.raiseToLevel(content, profile, ch.id, RoguelikeDebug.maxLevel(ch))));
                if (!ch.milestones.isEmpty()) {
                    button("Debug: " + ch.name + " complete milestones", e -> debug(RoguelikeDebug.completeMilestones(content, profile, ch)));
                }
            }
            button("Debug: next account level", e -> debug(RoguelikeDebug.nextAccountLevel(content, profile)));
            button("Debug: reset all progress", e -> FOptionPane.showConfirmDialog("Reset every character's and the account's progress?",
                    "Reset progress", yes -> {
                        if (yes) {
                            profile.characters.clear();
                            profile.accountXp = 0;
                            debug(List.of("Progress reset (debug)."));
                        }
                    }));
            return;
        }
        if (run.getState().status != RunStatus.IN_PROGRESS) {
            return;
        }
        if (run.getActiveEncounter() != null) {
            button("Debug: win this fight", e -> debug(RoguelikeDebug.winFight(run, profile)));
        }
        button("Debug: +100 gold", e -> debug(List.of(RoguelikeDebug.addGold(run, 100))));
        button("Debug: heal to full", e -> debug(List.of(RoguelikeDebug.heal(run))));
        button("Debug: add a relic", e -> debug(List.of(RoguelikeDebug.addRelic(run))));
    }

    private void debug(final List<String> messages) {
        ctrl().afterStep();
        ctrl().saveProfile();
        ctrl().save();
        update();
        List<String> all = new ArrayList<>(messages);
        all.addAll(ctrl().takeMessages());
        FOptionPane.showMessageDialog(String.join("\n\n", all), "Debug");
    }

    // ---------------------------------------------------------------- starting a run

    /** Character being set up for a run; null while choosing one. */
    private CharacterDef chosenCharacter;
    private Loadout loadout;

    private void showRunStart() {
        RoguelikeContent content = ctrl().getContent();
        MetaProfile profile = ctrl().getProfile();
        if (content.getCharacters().isEmpty()) { // content without characters: plain starters, no progression
            status.setText("Roguelike run (prototype)\n\nChoose a starter deck.");
            for (StarterDef s : content.getStarters().values()) {
                button(s.name, e -> {
                    ctrl().startRun(s.id);
                    update();
                });
            }
            return;
        }
        if (chosenCharacter == null) {
            status.setText("Roguelike run (prototype)\n" + ProgressText.accountLine(content, profile) + "\n\nChoose a character.");
            for (CharacterDef ch : content.getCharacters().values()) {
                button(ProgressText.levelLine(content, ch, profile.character(ch.id)), e -> {
                    chosenCharacter = ch;
                    loadout = defaultLoadout(content, profile, ch);
                    update();
                });
            }
            return;
        }

        CharacterDef ch = chosenCharacter;
        CharacterProgress progress = profile.character(ch.id);
        status.setText(ProgressText.levelLine(content, ch, progress) + "\n\nChoose a starter deck and what to bring.");
        for (String id : Progression.availableStarters(content, ch, progress)) {
            boolean on = id.equals(loadout.starterId);
            button((on ? "[x] " : "[ ] ") + content.getStarter(id).name, e -> {
                loadout.starterId = id;
                update();
            });
        }
        List<Progression.Unlock> unlocks = Progression.characterUnlocks(content, ch, progress);
        List<String> bases = new ArrayList<>();
        for (Progression.Unlock u : unlocks) {
            if ("base".equals(u.def().kind)) {
                bases.add(u.def().base);
            }
        }
        if (!bases.isEmpty()) {
            String own = content.getStarter(loadout.starterId).base;
            button((loadout.base == null ? "[x] " : "[ ] ") + "Base: the starter's own" + (own == null ? "" : " (" + baseName(content, own) + ")"), e -> {
                loadout.base = null;
                update();
            });
            for (String b : bases) {
                button((b.equals(loadout.base) ? "[x] " : "[ ] ") + "Base: " + baseName(content, b), e -> {
                    loadout.base = b;
                    update();
                });
            }
        }
        for (Progression.Unlock u : unlocks) {
            String kind = u.def().kind;
            if ("starter".equals(kind) || "base".equals(kind)) {
                continue;
            }
            boolean on = !loadout.disabled.contains(u.key());
            button((on ? "[x] " : "[ ] ") + Progression.describe(content, u.def()) + "  (" + u.source() + ")", e -> {
                if (on) {
                    loadout.disabled.add(u.key());
                } else {
                    loadout.disabled.remove(u.key());
                }
                update();
            });
        }
        int toggles = Progression.setToggles(content, profile);
        if (toggles > 0) {
            List<String> sets = Progression.fullPoolSetChoices(content, profile);
            loadout.disabledSets.retainAll(sets);
            text("Full Pool sets (switch off up to " + toggles + "):");
            for (String set : sets) {
                boolean on = !loadout.disabledSets.contains(set);
                FButton b = button((on ? "[x] " : "[ ] ") + Progression.setName(set), e -> {
                    if (on) {
                        loadout.disabledSets.add(set);
                    } else {
                        loadout.disabledSets.remove(set);
                    }
                    update();
                });
                // keep at least one set, and no more off than unlocked
                if (on && (loadout.disabledSets.size() >= toggles || loadout.disabledSets.size() >= sets.size() - 1)) {
                    b.setEnabled(false);
                }
            }
        }
        button("Start the run", e -> {
            ctrl().startRun(ch, loadout);
            chosenCharacter = null;
            update();
        });
        button("Progress", e -> FOptionPane.showMessageDialog(ProgressText.progress(content, profile, ch), ch.name));
        button("Back", e -> {
            chosenCharacter = null;
            update();
        });
    }

    /** The last loadout if it still fits what is unlocked, else the first starter with everything on. */
    private static Loadout defaultLoadout(final RoguelikeContent content, final MetaProfile profile, final CharacterDef ch) {
        CharacterProgress p = profile.character(ch.id);
        List<String> starters = Progression.availableStarters(content, ch, p);
        Loadout l = new Loadout();
        Loadout last = p.loadout;
        l.starterId = last != null && starters.contains(last.starterId) ? last.starterId : starters.get(0);
        if (last != null) {
            l.disabled.addAll(last.disabled);
            l.disabledSets.addAll(last.disabledSets);
            l.base = last.base;
        }
        return l;
    }

    private static String baseName(final RoguelikeContent content, final String file) {
        for (String line : content.getBase(file)) {
            if (line.startsWith("Name:")) {
                return line.substring(5);
            }
        }
        return file;
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
