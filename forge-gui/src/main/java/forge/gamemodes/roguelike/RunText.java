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
                case CARD_REWARD -> lastLine(s) + "\nChoose a reward.";
                case BOSS_RELIC -> lastLine(s) + "\nThe boss is beaten! Choose a relic.";
                case SHOP -> "Shop. You have " + s.gold + " gold.";
                case UPGRADE -> "Choose a card to upgrade.";
                case CUT -> "Choose a card to cut from your deck.";
            };
        }
        EncounterDef enc = run.getActiveEncounter();
        if (enc != null) {
            return (enc.boss ? "Boss: " : "Next fight: ") + enc.name + " (" + run.enemyLife(enc, run.getActiveNode()) + " life)";
        }
        return "Choose where to go next.";
    }

    private static String lastLine(final RunState s) {
        return s.log.isEmpty() ? "" : s.log.get(s.log.size() - 1);
    }

    /** A few lines about what a node holds, for the map screen. */
    public static String nodeDetail(final Run run, final MapNode n) {
        RunConfig cfg = run.getContent().getConfig();
        return switch (n.type) {
            case ENEMY, BOSS -> {
                EncounterDef e = run.getContent().getEncounter(n.encounterId);
                StringBuilder sb = new StringBuilder();
                sb.append(n.type == NodeType.BOSS ? "Boss: " : "Enemy: ").append(e.name).append('\n');
                sb.append(run.enemyLife(e, n)).append(" life, ").append(e.hand).append("-card hand");
                if (e.commander != null) {
                    sb.append(", commander ").append(e.commander);
                }
                if (e.base != null) {
                    sb.append("\nBase: ").append(baseText(run.getContent().getBase(e.base)));
                }
                yield sb.toString();
            }
            case SHOP -> "Shop: buy relics and cards with gold.";
            case REST -> "Rest: heal " + Math.round(cfg.restHealFraction * 100) + "% of your max life.";
            case CUT -> "Cut a card: remove a card from your deck.";
            case UPGRADE -> "Upgrade a card: choose one of " + cfg.upgradeChoices + " upgrades.";
        };
    }

    /** "Name - rules text" of a Base script. */
    static String baseText(final List<String> script) {
        String name = "";
        String oracle = "";
        for (String line : script) {
            if (line.startsWith("Name:")) {
                name = line.substring(5);
            } else if (line.startsWith("Oracle:")) {
                oracle = line.substring(7).replace("\\n", " ");
            }
        }
        return oracle.isEmpty() ? name : name + " - " + oracle;
    }

    /** The end-of-run summary. */
    public static String summary(final Run run) {
        RunState s = run.getState();
        StringBuilder sb = new StringBuilder();
        sb.append(s.status == RunStatus.WON ? "Run won!" : "Run over.").append('\n');
        sb.append("Character: ").append(run.getContent().getStarter(s.starterId).name).append('\n');
        sb.append("Floor reached: ").append(s.floor).append('\n');
        sb.append("Matches won: ").append(s.matchesWon).append("   lost: ").append(s.matchesLost).append('\n');
        if (!s.beaten.isEmpty()) {
            sb.append("Beaten: ").append(String.join(", ", s.beaten)).append('\n');
        }
        sb.append("Final deck: ").append(s.deckSize()).append(" cards, ").append(s.upgrades.size()).append(" upgraded names\n");
        if (!s.relics.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : s.relics) {
                names.add(run.getContent().getRelic(id).name);
            }
            sb.append("Relics: ").append(String.join(", ", names)).append('\n');
        }
        sb.append("Gold left: ").append(s.gold);
        if (s.characterId != null) {
            sb.append("\nExperience earned: ").append(s.xpEarned);
        }
        return sb.toString();
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
