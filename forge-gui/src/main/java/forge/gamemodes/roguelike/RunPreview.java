package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

import forge.StaticData;
import forge.card.CardRules;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.card.CardView;
import forge.item.PaperCard;

/** Card views for screens that show a card as it is in this run: the mode's rewrite, if any, and upgrades. */
public final class RunPreview {
    private static long timestamp = 1;
    /** Ids far above any game's; a negative id would make the view show the printed text. */
    private static int nextId = 1_000_000_000;

    private RunPreview() {
    }

    /**
     * The card as matches play it (the mode's rewrite, if any) with the run's upgrades for its name, plus an
     * extra upgrade (one on offer), if any.
     */
    public static CardView cardView(final Run run, final String name, final RunUpgrade extra) {
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        CardRules override = run.getContent().getOverride(name);
        if (override != null) {
            pc = new PaperCard(override, pc.getEdition(), pc.getRarity()); // as GameRules.applyCardOverride does
        }
        List<RunUpgrade> upgrades = new ArrayList<>(run.getState().upgrades.getOrDefault(name, List.of()));
        if (extra != null) {
            upgrades.add(extra);
        }
        if (upgrades.isEmpty() && override == null) {
            return CardView.getCardForUi(pc);
        }
        Card c = CardFactory.getCard(pc, null, nextId++, null); // a fresh card: the shared UI copy must not change
        for (RunUpgrade u : upgrades) {
            u.toCardUpgrade().applyTo(c, timestamp++);
        }
        c.updatePTforView();
        c.updateKeywords();
        c.updateAbilityTextForView();
        CardView view = c.getView();
        view.setPreview(true); // the printed image would show the printed text, without rewrite or upgrades
        return view;
    }
}
