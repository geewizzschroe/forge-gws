package forge.gamemodes.roguelike.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import forge.gamemodes.roguelike.RunStatus;

/** Turns simulation records into a Markdown report and CSV rows. */
public final class SimReport {

    private SimReport() {
    }

    /** One CSV line per match, with a header. */
    public static String matchesCsv(final List<MatchRecord> matches) {
        StringBuilder sb = new StringBuilder(MatchRecord.csvHeader()).append('\n');
        for (MatchRecord m : matches) {
            sb.append(m.toCsv()).append('\n');
        }
        return sb.toString();
    }

    public static String markdown(final String title, final List<RunRecord> runs, final List<MatchRecord> encounterTests) {
        StringBuilder sb = new StringBuilder("# ").append(title).append("\n\n");
        List<MatchRecord> runMatches = new ArrayList<>();
        for (RunRecord r : runs) {
            runMatches.addAll(r.matches);
        }

        if (!runs.isEmpty()) {
            long won = runs.stream().filter(r -> r.status == RunStatus.WON).count();
            sb.append("## Whole runs\n\n");
            sb.append("- Runs: ").append(runs.size()).append(", won: ").append(won)
              .append(" (").append(pct(won, runs.size())).append(")\n");
            sb.append("- Matches per run: ").append(fmt(runs.stream().mapToInt(r -> r.matches.size()).average().orElse(0))).append('\n');
            sb.append("- Final deck size: ").append(fmt(runs.stream().mapToInt(r -> r.finalDeckSize).average().orElse(0))).append('\n');
            sb.append("- Relics per run: ").append(fmt(runs.stream().mapToInt(r -> r.relics.size()).average().orElse(0))).append('\n');
            sb.append("- Experience per run: ").append(fmt(runs.stream().mapToLong(r -> r.xpEarned).average().orElse(0))).append('\n');
            sb.append("- Gold left at the end: ").append(fmt(runs.stream().mapToInt(r -> r.goldLeft).average().orElse(0))).append("\n\n");

            Map<String, Integer> endedBy = new TreeMap<>();
            for (RunRecord r : runs) {
                if (r.endedBy != null) {
                    endedBy.merge(r.endedBy, 1, Integer::sum);
                }
            }
            if (!endedBy.isEmpty()) {
                sb.append("Runs ended by:\n\n| Enemy | Runs |\n|---|---|\n");
                endedBy.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(e -> sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n"));
                sb.append('\n');
            }

            Map<String, Integer> nodes = new TreeMap<>();
            for (RunRecord r : runs) {
                r.nodesEntered.forEach((k, v) -> nodes.merge(k, v, Integer::sum));
            }
            sb.append("Nodes entered: ").append(nodes).append("\n\n");

            sb.append("### Matches inside runs\n\n");
            encounterTable(sb, runMatches);

            Map<String, Integer> picks = new LinkedHashMap<>();
            for (RunRecord r : runs) {
                for (String c : r.choices) {
                    picks.merge(c, 1, Integer::sum);
                }
            }
            if (!picks.isEmpty()) {
                sb.append("### Most frequent choices\n\n| Choice | Times |\n|---|---|\n");
                picks.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(20)
                        .forEach(e -> sb.append("| ").append(e.getKey().replace("|", "/")).append(" | ").append(e.getValue()).append(" |\n"));
                sb.append('\n');
            }
        }

        if (!encounterTests.isEmpty()) {
            sb.append("## Single encounters (starter deck, fixed starting life)\n\n");
            encounterTable(sb, encounterTests);
        }
        sb.append("Win rates come from the AI playing both sides; treat them as relative, not as how a person would fare.\n");
        return sb.toString();
    }

    private static void encounterTable(final StringBuilder sb, final List<MatchRecord> matches) {
        Map<String, List<MatchRecord>> by = new TreeMap<>();
        for (MatchRecord m : matches) {
            by.computeIfAbsent(m.encounterName, k -> new ArrayList<>()).add(m);
        }
        sb.append("| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        by.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey())).forEach(e -> {
            List<MatchRecord> ms = e.getValue();
            long wins = ms.stream().filter(m -> m.won).count();
            double before = ms.stream().filter(m -> m.won).mapToInt(m -> m.heroLifeBefore).average().orElse(Double.NaN);
            double after = ms.stream().filter(m -> m.won).mapToInt(m -> m.heroLifeAfter).average().orElse(Double.NaN);
            sb.append("| ").append(e.getKey()).append(" | ").append(ms.size()).append(" | ").append(pct(wins, ms.size()))
              .append(" | ").append(wins == 0 ? "-" : fmt(before) + " -> " + fmt(after))
              .append(" | ").append(fmt(ms.stream().mapToInt(m -> m.turns).average().orElse(0)))
              .append(" | ").append(ms.stream().filter(m -> m.timedOut).count())
              .append(" | ").append(fmt(ms.stream().mapToLong(m -> m.millis).average().orElse(0) / 1000.0))
              .append(" |\n");
        });
        sb.append('\n');
    }

    private static String pct(final long n, final long of) {
        return of == 0 ? "-" : Math.round(100.0 * n / of) + "%";
    }

    private static String fmt(final double d) {
        return Double.isNaN(d) ? "-" : String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
