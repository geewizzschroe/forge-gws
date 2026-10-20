# Spikes (throwaway reference code)

Feasibility experiments from the design sessions. They are **not** part of any Maven module and are not production code. They show working API usage for the engine questions in `../DESIGN.md`. All run headless, AI vs AI, in the Commander variant.

| File | What it tests | Matches DESIGN.md |
|---|---|---|
| `Spike.java` | 1: arbitrary deck shape runs; 2: final life readable; 3: per-name additive upgrades via the start-of-game hook; 4: in-match life cap via a scripted replacement effect; 5: a command-zone relic puts the commander onto the battlefield as its back face. Also holds the shared helpers the other spikes reuse. | 4.3-4.6 |
| `Spike2.java` | Re-runs test 1 over many seeds and reports why games ended. | 4.3 |
| `Spike3.java` | Rainbow Basic landless model (superseded). | 4.7 |
| `Spike4.java` | Choose-a-basic: relic route, universal land cap, and the built-in-logic route with an AI colour heuristic. | 4.7 |
| `cards/` | Custom card scripts for tests 4 and 5. | |
| `cards-rainbow/` | Custom card scripts for `Spike3`. | |
| `cards-choose-basic/` | Custom card scripts for `Spike4`. | |

## Running

```
JAR=forge-gui-desktop/target/forge-gui-desktop-<version>-jar-with-dependencies.jar
mkdir -p /tmp/spike-out && javac -cp $JAR -d /tmp/spike-out design/roguelike-run-mode/spikes/*.java
cd forge-gui
java -Djava.awt.headless=true -Duser.home=/tmp/spike-home -Xmx3g -cp $JAR:/tmp/spike-out Spike  ../design/roguelike-run-mode/spikes/cards
java -Djava.awt.headless=true -Duser.home=/tmp/spike-home -Xmx3g -cp $JAR:/tmp/spike-out Spike4 ../design/roguelike-run-mode/spikes/cards-choose-basic
```

Notes: run from the `forge-gui` directory (the card database is found relative to it). `-Duser.home` keeps the run out of the real user folder. Each program prints lines starting `RESULT` or `NOTE`; filter with `grep -E '^(RESULT|NOTE)'`. `Spike2` to `Spike4` use helpers from `Spike`, so compile them together. Spike4 takes an optional second argument `x3` to run only the built-in-logic experiment, and an optional third argument `main1` to create the land at the start of the precombat main phase instead of upkeep (the decided timing, D6).
