package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain-text descriptions of a run for screens. */
public final class RunText {

    private RunText() {
    }

    public static String status(final Run run) {
        RunState s = run.getState();
        StringBuilder sb = new StringBuilder();
        sb.append("Life ").append(s.life).append("/").append(s.maxLife)
          .append("   Gold ").append(s.gold)
          .append("   Retries ").append(s.retriesLeft)
          .append("   Floor ").append(s.floor).append('\n');
        sb.append("Commander: ").append(s.commander).append("   Deck: ").append(s.deckSize()).append(" cards\n");
        if (!s.relics.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : s.relics) {
                names.add(run.getContent().getRelic(id).name);
            }
            sb.append("Relics: ").append(String.join(", ", names)).append('\n');
        }
        sb.append('\n').append(prompt(run));
        return sb.toString();
    }

    /** What the player should do now. */
    public static String prompt(final Run run) {
        RunState s = run.getState();
        if (s.status == RunStatus.WON) {
            return "You won the run!";
        }
        if (s.status == RunStatus.LOST) {
            return "The run is over.";
        }
        if (s.offer != null) {
            return switch (s.offer.kind) {
                case CARD_REWARD -> "Victory! Choose a reward.";
                case BOSS_RELIC -> "The boss is beaten! Choose a relic.";
                case SHOP -> "Shop. You have " + s.gold + " gold.";
                case UPGRADE -> "Choose a card to upgrade.";
                case CUT -> "Choose a card to cut from your deck.";
            };
        }
        EncounterDef enc = run.getActiveEncounter();
        if (enc != null) {
            return (enc.boss ? "Boss: " : "Next fight: ") + enc.name + " (" + enc.life + " life)";
        }
        return "Choose where to go next.";
    }

    public static String node(final Run run, final MapNode n) {
        String type = switch (n.type) {
            case ENEMY -> "Enemy";
            case BOSS -> "Boss";
            case SHOP -> "Shop";
            case REST -> "Rest";
            case CUT -> "Cut a card";
            case UPGRADE -> "Upgrade a card";
        };
        if (n.encounterId != null) {
            type += ": " + run.getContent().getEncounter(n.encounterId).name;
        }
        return "Step " + (n.row + 1) + " - " + type;
    }

    public static String option(final Run run, final OfferOption o) {
        StringBuilder sb = new StringBuilder();
        if (o.relicId != null) {
            RelicDef r = run.getContent().getRelic(o.relicId);
            sb.append(r.name).append(" - ").append(r.description);
        } else if (o.cutCard != null) {
            sb.append("Cut ").append(o.cutCard).append(" (x").append(run.getState().deck.getOrDefault(o.cutCard, 0)).append(")");
        } else if (!o.cards.isEmpty()) {
            if (o.label != null) {
                sb.append(o.label).append(": ");
            }
            List<String> cards = new ArrayList<>();
            for (Map.Entry<String, Integer> e : o.cards.entrySet()) {
                cards.add(e.getValue() + "x " + e.getKey());
            }
            sb.append(String.join(", ", cards));
            if (o.upgrade != null) {
                sb.append(" [upgraded: ").append(o.upgrade.describe()).append("]");
            }
        } else if (o.upgrade != null) {
            sb.append(o.upgradeCard).append(": ").append(o.upgrade.describe());
        }
        if (o.price > 0) {
            sb.append(o.bought ? "  (bought)" : "  (" + o.price + " gold)");
        }
        return sb.toString();
    }

    public static String deckList(final Run run) {
        RunState s = run.getState();
        StringBuilder sb = new StringBuilder();
        sb.append("Commander: ").append(s.commander).append(upgrades(s, s.commander)).append("\n\n");
        for (Map.Entry<String, Integer> e : s.deck.entrySet()) {
            sb.append(e.getValue()).append("x ").append(e.getKey()).append(upgrades(s, e.getKey())).append('\n');
        }
        if (!s.relics.isEmpty()) {
            sb.append("\nRelics:\n");
            for (String id : s.relics) {
                RelicDef r = run.getContent().getRelic(id);
                sb.append(r.name).append(" - ").append(r.description).append('\n');
            }
        }
        return sb.toString();
    }

    private static String upgrades(final RunState s, final String name) {
        List<RunUpgrade> ups = s.upgrades.get(name);
        if (ups == null || ups.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (RunUpgrade u : ups) {
            parts.add(u.describe());
        }
        return "  [" + String.join("; ", parts) + "]";
    }
}
