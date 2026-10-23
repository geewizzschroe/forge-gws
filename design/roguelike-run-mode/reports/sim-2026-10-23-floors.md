# Two floors in the simulator, 2026-10-23

The AI plays both sides, so treat the numbers as an ordering, not as how a person would fare. At 70 fights a result is good to about ±11 points; at 10 fights per enemy, to about ±30.

## Whole runs

15 greedy runs per hero, from a fresh profile:

| Hero | Runs won | Runs that reached floor 2 | Matches per run | Match win % inside runs |
|---|---|---|---|---|
| Jill (mono-blue) | 0 | 0 | 3.0 | 33% |
| Lightning | 0 | 0 | 3.5 | 42% |

No run reached floor 2. The AI hero dies on floor 1 after 2 to 4 matches, as it did in the earlier sims (0 runs won in 50). So whole runs can't measure floor 2 with an AI pilot. Runs mostly ended against Goblin Raiders, Malboro Marsh, Chocobo Stables and Moogle Post.

## Floor 1 against floor 2, single fights

There's a new option for this: `-Droguelike.sim.floor=2` fights single enemies as on floor 2, with +5 life, 2 upgraded creature cards and the Veterans Base ability.
- **Setup:** the starter deck at 20 life against the 7 tier 2 and tier 3 enemies, 10 fights each, on the same seeds for both floors.
- **Lightning's deck:** her new starter, with Burst Lightning and Resolute Reinforcements in.

| Enemy | Jill floor 1 | Jill floor 2 | Lightning floor 1 | Lightning floor 2 |
|---|---|---|---|---|
| Dragon's Aerie | 40% | 10% | 30% | 30% |
| Elf Grove | 30% | 20% | 50% | 20% |
| Goblin Raiders | 10% | 10% | 20% | 30% |
| Moogle Post | 20% | 10% | 60% | 20% |
| Summoner's Circle | 60% | 60% | 70% | 20% |
| The Restless Dead | 30% | 30% | 30% | 10% |
| Vampire Court | 20% | 10% | 70% | 30% |
| **All** | **30%** (21/70) | **21%** (15/70) | **47%** (33/70) | **23%** (16/70) |

Both heroes together: floor 1 39% (54/140), floor 2 22% (31/140).

## What this says

- Floor 2's scaling makes the same enemy noticeably harder: about 17 points for both heroes together, and 24 for Lightning alone.
- These fights use the starter deck. A hero who really reaches floor 2 has a larger deck, relics, upgrades and a rare Commander upgrade, and none of that is in these numbers. So they show the change floor 2 makes, not how hard floor 2 would feel.
- The AI hero loses most floor 1 runs early. That says more about the AI pilot than about the floor, but it means whole-run sims tell us little beyond floor 1.
