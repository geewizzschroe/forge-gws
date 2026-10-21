# Landless check of current content, 2026-10-21

Every card in the current starters, enemy decks and reward pools, checked two ways:

1. `tools/landless_audit.py` (pattern groups; regenerated `landless-audit.md`).
2. A search of each card's rules text for "land", the basic land types, landfall, domain and Town, to catch what the patterns miss.

Bases and relics are not included (they are the mode's own scripts).

**No starter or enemy deck card is affected.** Every card below is in a reward pool. This is a listing only; nothing has been changed.

## Part of the card does nothing

| Card | Pool | What happens without lands in the library |
|---|---|---|
| World Map | `rw_core` | Both abilities search the library for a land, so **the card does nothing at all**. |
| Call the Mountain Chocobo | `rw_core` | The Mountain search finds nothing. The 2/2 Bird token and flashback still work. |
| Cloudbound Moogle | `rw_core` | Plainscycling discards it and finds nothing. The creature itself is fine. |
| Hill Gigas | `rw_core` | Mountaincycling discards it and finds nothing. The creature itself is fine. |
| PuPu UFO | `rw_core` | "Put a land card from your hand onto the battlefield" only works with a land in hand, which in this mode comes only from the landless Cultivate or Kodama's Reach. Its Town ability counts 0, since the conjured lands are basics. |

## Works, but differently

| Card | Pool | What happens |
|---|---|---|
| Sabotender | `rw_core` | Landfall: 1 damage to each opponent every turn from the conjured land, until 10 lands. |
| Ambrosia Whiteheart | `rw_core` | Landfall: +1/+0 every turn until 10 lands. |
| Choco-Comet, Call the Mountain Chocobo | `rw_core` | Their Bird tokens get +1/+0 every turn from the conjured land, until 10 lands. |
| Sandworm | `rw_core` | Destroys a land. The "search for a basic" part finds nothing, but the victim still gets a land each turn while under the cap. |
| Coiling Oracle | `blue_core`, `ub_core`, `uw_core`, `frozen_core` | The top card is never a land, so it always goes to hand. |
| Ghostly Flicker | `uw_core` | Can flicker a land; works normally. |
| Icy Manipulator | `frozen_core` | Can tap a land; works normally. |

## Already handled

Rampant Growth, Cultivate, Kodama's Reach, Explosive Vegetation, Sakura-Tribe Elder and Wood Elves (`bucket_ramp`) have landless overrides in `overrides/`, so they conjure lands instead of searching.

## Pattern-audit counts

The upstream merge of 2026-10-21 left the whole-database counts in `landless-audit.md` unchanged.
