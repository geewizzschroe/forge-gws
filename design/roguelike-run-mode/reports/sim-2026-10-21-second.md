<!-- Second balance simulation, 2026-10-21: RunBalanceSimTest#fullSimulation after the upstream merge, with Jill's core-based mono-blue starter, the Wayfarer's Camp AI hint and the greedy policy. -->

# Balance simulation, second batch (2026-10-21)

Three batches, AI playing both sides:

| Batch | Starter | Policy | Runs | Single fights per enemy |
|---|---|---|---|---|
| A | jill_mono_blue | random | 10 | 3 |
| B | jill_mono_blue | greedy | 20 | 5 |
| C | lightning_rw | greedy | 20 | 5 |

## Summary

- **No run was won in any batch** (0 of 50). Runs reached the boss 1 time (A), 1 time (B) and 4 times (C).
- **Individual matches are now won.** The first report (`sim-2026-10-21-first.md`) had a 0% hero win rate in every match. Now:

| Enemy | A: Jill, random | B: Jill, greedy | C: Lightning, greedy |
|---|---|---|---|
| A Troop of Dwarves | 14% of 7 | 34% of 32 | 71% of 35 |
| Goblin Raiders | 33% of 18 | 27% of 15 | 63% of 19 |
| The Restless Dead | 0% of 1 | 36% of 11 | 10% of 20 |
| Krenko's Warband (boss) | 0% of 1 | 0% of 1 | 0% of 5 |

  (Matches inside runs. Single-encounter results are in each batch's section below.)
- **Krenko's Warband was never beaten**: 0 of 20 fights across all batches, runs and single encounters, with averages of 3 to 9 turns per batch.
- **The Restless Dead ended half of Lightning's runs** (10 of 20) with a 10% hero win rate there, while Lightning won most Dwarves and Goblin fights.
- **No relics were gained** in any run. Relics come from shops (60 gold) and beating the boss; average gold at the end was 18 to 41.
- **Reward offers include out-of-scope and off-colour cards.** Jill's pools (`blue_core` and the buckets) are mostly out of scope (Preordain, Peel from Reality, Phantom Monster, Frost Lynx). Lightning was offered, and took, the blue "Wings!" flier bucket (Faerie Miscreant, Air Elemental and others), because bucket rewards are shared across characters.
- Several things changed together since the first report (upstream engine, Jill's deck, the Camp hint, the policy), so these numbers can't be pinned on any single change. Batch A versus B is the only like-for-like policy comparison.

Win rates come from the AI piloting the hero, which is weaker than a person, particularly with reactive cards; read them as relative.

## Batch A

### Whole runs

- Runs: 10, won: 0 (0%)
- Matches per run: 2.7
- Final deck size: 33.4
- Relics per run: 0.0
- Experience per run: 21.0
- Gold left at the end: 17.5

Runs ended by:

| Enemy | Runs |
|---|---|
| Goblin Raiders | 5 |
| A Troop of Dwarves | 3 |
| Krenko's Warband | 1 |
| The Restless Dead | 1 |

Nodes entered: {BOSS=1, ENEMY=16, REST=5, SHOP=1, UPGRADE=2}

### Matches inside runs

| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |
|---|---|---|---|---|---|---|
| A Troop of Dwarves | 7 | 14% | 7.0 -> 3.0 | 12.3 | 0 | 2.7 |
| Goblin Raiders | 18 | 33% | 20.0 -> 10.0 | 13.9 | 0 | 3.0 |
| Krenko's Warband | 1 | 0% | - | 3.0 | 0 | 0.6 |
| The Restless Dead | 1 | 0% | - | 15.0 | 0 | 3.0 |

### Most frequent choices

| Choice | Times |
|---|---|
| Chose 2x Essence Scatter | 1 |
| Chose 2x Frost Lynx [upgraded: Ward 1] | 1 |
| Chose 2x Cloudkin Seer [upgraded: +0/+2] | 1 |
| Chose 2x Faerie Miscreant | 1 |
| Chose Brineborn Cutthroat: +0/+2 | 1 |
| Chose 2x Mulldrifter [upgraded: Ward 1] | 1 |
| Chose Moon-Circuit Hacker: Vigilance | 1 |
| Chose 2x Phantom Monster | 1 |
| Chose 2x Aethersnipe | 1 |

### Single encounters (starter deck, fixed starting life)

| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |
|---|---|---|---|---|---|---|
| A Troop of Dwarves | 3 | 33% | 20.0 -> 9.0 | 14.0 | 0 | 3.5 |
| Goblin Raiders | 3 | 67% | 20.0 -> 7.5 | 16.7 | 0 | 4.6 |
| Krenko's Warband | 3 | 0% | - | 9.3 | 0 | 1.2 |
| The Restless Dead | 3 | 0% | - | 18.0 | 0 | 4.1 |

Win rates come from the AI playing both sides; treat them as relative, not as how a person would fare.


---

## Batch B

### Whole runs

- Runs: 20, won: 0 (0%)
- Matches per run: 3.0
- Final deck size: 34.3
- Relics per run: 0.0
- Experience per run: 28.5
- Gold left at the end: 20.8

Runs ended by:

| Enemy | Runs |
|---|---|
| A Troop of Dwarves | 10 |
| Goblin Raiders | 5 |
| The Restless Dead | 4 |
| Krenko's Warband | 1 |

Nodes entered: {BOSS=1, CUT=3, ENEMY=38, REST=6, SHOP=5, UPGRADE=4}

### Matches inside runs

| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |
|---|---|---|---|---|---|---|
| A Troop of Dwarves | 32 | 34% | 19.4 -> 10.7 | 13.2 | 0 | 3.0 |
| Goblin Raiders | 15 | 27% | 20.0 -> 7.5 | 12.8 | 0 | 2.7 |
| Krenko's Warband | 1 | 0% | - | 8.0 | 0 | 1.1 |
| The Restless Dead | 11 | 36% | 13.8 -> 11.8 | 13.6 | 0 | 3.2 |

### Most frequent choices

| Choice | Times |
|---|---|
| Chose 2x Counterspell | 3 |
| Chose 2x Peel from Reality | 3 |
| Chose 2x Preordain | 2 |
| Chose 2x Phantom Monster | 1 |
| Chose Faerie Seer: +1/+1 | 1 |
| Chose Wings!: 1x Cloudkin Seer, 1x Wind Drake, 1x Phantom Monster | 1 |
| Chose Moon-Circuit Hacker: Lifelink | 1 |
| Chose Back to the Hand: 2x Repulse, 1x Into the Roil | 1 |
| Bought 2x Venser, Shaper Savant  (30 gold) | 1 |
| Chose 2x Frost Lynx | 1 |
| Chose 2x Frost Lynx [upgraded: +0/+2] | 1 |
| Bought 2x Phantom Monster  (30 gold) | 1 |
| Chose 2x Faerie Miscreant [upgraded: Vigilance] | 1 |
| Chose 2x Glen Elendra Archmage | 1 |
| Chose High Fae Trickster: Vigilance | 1 |
| Chose 2x Venser, Shaper Savant | 1 |
| Chose Brineborn Cutthroat: +0/+2 | 1 |
| Chose Wings!: 1x Phantom Monster, 1x Air Elemental, 1x Wind Drake | 1 |
| Chose Wings!: 1x Air Elemental, 1x Phantom Monster, 1x Mahamoti Djinn | 1 |
| Chose 2x Merfolk Looter [upgraded: +0/+2] | 1 |

### Single encounters (starter deck, fixed starting life)

| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |
|---|---|---|---|---|---|---|
| A Troop of Dwarves | 5 | 20% | 20.0 -> 3.0 | 12.0 | 0 | 1.9 |
| Goblin Raiders | 5 | 0% | - | 13.6 | 0 | 1.9 |
| Krenko's Warband | 5 | 0% | - | 7.0 | 0 | 1.1 |
| The Restless Dead | 5 | 20% | 20.0 -> 17.0 | 18.4 | 0 | 4.6 |

Win rates come from the AI playing both sides; treat them as relative, not as how a person would fare.


---

## Batch C

### Whole runs

- Runs: 20, won: 0 (0%)
- Matches per run: 4.0
- Final deck size: 37.0
- Relics per run: 0.0
- Experience per run: 58.5
- Gold left at the end: 41.3

Runs ended by:

| Enemy | Runs |
|---|---|
| The Restless Dead | 10 |
| Krenko's Warband | 4 |
| A Troop of Dwarves | 3 |
| Goblin Raiders | 3 |

Nodes entered: {BOSS=4, CUT=6, ENEMY=55, REST=13, SHOP=8, UPGRADE=10}

### Matches inside runs

| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |
|---|---|---|---|---|---|---|
| A Troop of Dwarves | 35 | 71% | 19.5 -> 22.8 | 14.4 | 0 | 3.1 |
| Goblin Raiders | 19 | 63% | 18.3 -> 21.9 | 12.6 | 0 | 2.0 |
| Krenko's Warband | 5 | 0% | - | 7.8 | 0 | 1.1 |
| The Restless Dead | 20 | 10% | 18.5 -> 19.0 | 14.7 | 0 | 4.4 |

### Most frequent choices

| Choice | Times |
|---|---|
| Chose 2x Zack Fair | 4 |
| Chose 2x Dragoon's Lance | 2 |
| Chose Lightning, Army of One: Vigilance | 2 |
| Chose 2x Phoenix Down | 2 |
| Chose 2x Blazing Bomb [upgraded: Trample] | 1 |
| Chose 2x Haste Magic | 1 |
| Chose 2x Thunder Magic | 1 |
| Chose Zack Fair: Haste | 1 |
| Chose 2x Joshua, Phoenix's Dominant | 1 |
| Chose 2x G'raha Tia [upgraded: Trample] | 1 |
| Chose 2x Auron's Inspiration | 1 |
| Chose 2x Crystal Fragments | 1 |
| Chose Wings!: 2x Faerie Miscreant, 1x Cloudkin Seer | 1 |
| Chose Wings!: 1x Air Elemental, 1x Mahamoti Djinn, 1x Faerie Miscreant | 1 |
| Chose Wings!: 1x Phantom Monster, 1x Aethersnipe, 1x Faerie Miscreant | 1 |
| Chose Zack Fair: +1/+1 | 1 |
| Chose Wings!: 1x Wind Drake, 1x Air Elemental, 1x Mahamoti Djinn | 1 |
| Chose 2x Joshua, Phoenix's Dominant [upgraded: Trample] | 1 |
| Chose Zack Fair: Ward 1 | 1 |
| Chose 2x Genji Glove | 1 |

### Single encounters (starter deck, fixed starting life)

| Enemy | Fights | Hero win % | Avg hero life before -> after (wins) | Avg turns | Timeouts | Avg seconds |
|---|---|---|---|---|---|---|
| A Troop of Dwarves | 5 | 60% | 20.0 -> 28.3 | 11.8 | 0 | 2.5 |
| Goblin Raiders | 5 | 40% | 20.0 -> 39.5 | 13.2 | 0 | 1.6 |
| Krenko's Warband | 5 | 0% | - | 7.4 | 0 | 0.9 |
| The Restless Dead | 5 | 40% | 20.0 -> 13.0 | 13.4 | 0 | 2.4 |

Win rates come from the AI playing both sides; treat them as relative, not as how a person would fare.
