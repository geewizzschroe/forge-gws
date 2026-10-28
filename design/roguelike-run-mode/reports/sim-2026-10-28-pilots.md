# Simulation AI as the hero pilot, 2026-10-28

Can Forge's simulation AI pilot the hero better than the default AI, so that sims come closer to a person's play?

## What was wrong with the hybrid pilot

- **The symptom:** the earlier comparison showed 2,313 "AI failed to play" lines in one batch, and matches took about 23 seconds.
- **The cause:** in hybrid mode the AI copies the game and plays each candidate spell out (the "one-play safety check") before checking whether it can pay for it. Every spell in hand was simulated, affordable or not. The copies then failed to pay, and decisions ran into the AI's time limit.
- **Not the cause:** the landless rules and the game copier. A test that copies a landless game (LandlessSimulationTest) and a land-count check on every copy both came out clean.
- **The fix:** skip the safety check for plays the AI can't pay for (the cost check that follows rejects them anyway). One match went from 391 failures, 8 timeouts and 22.8 seconds to 0 failures, 0 timeouts and about 4 seconds.
- **Full simulation:** that mode still runs out of memory (Java heap) partway through a match, so it stays unused.

## Is it stronger?

Hero wins over A Troop of Dwarves, Summoner's Circle, Dragon's Aerie and Dion: 12 fights each, 48 per hero, the same seeds for every pilot.

| Hero | Default AI | Hybrid | Hybrid, keeping the default checks |
|---|---|---|---|
| Jill (mono-blue) | 22 (46%) | 12 (25%) | 16 (33%) |
| Lightning | 21 (44%) | 20 (42%) | 20 (42%) |

- **Hybrid swaps out the default AI's own checks** before a play (self-damage, enter-the-battlefield effects and others). That makes it more permissive, and it does worse with Jill, most of all against Summoner's Circle (2 of 12 against 9 of 12).
- **Keeping both,** with the safety check as an extra veto (`-Dforge.ai.hybridKeepsDefaultChecks=true`), lands in between. It's also 2 to 3 times slower.
- **Conclusion:** the default AI stays the sims' hero pilot. At 48 fights a hero, differences under about 15 points are noise. Lightning's three results can't be told apart, and only Jill's drop with plain hybrid is clearly real.

## Other checks on the default pilot

- **Mulligans:** 531 of 534 logged opening hands were kept at 7, so the landless decks don't trigger bad mulligans.
- **Targets:** in six logged losses to Dion, Jill's bounce, counterspells and removal went to sensible targets: Dion himself, the Stasis Snare holding a card, the main attackers. The losses look like the matchup, not misplays.
