# Refinement proposal, 2026-10-22

A proposal, not decisions. It answers the audit (`reports/audit-2026-10-22.md`) on three fronts: the map, the enemy roster, and the other loose ends. Where there is a real choice to make it is marked **Open**, with the options; where I lean, it says **Lean** and why. Nothing here is built except the small fixes listed in part 3.

---

## 1. The map

### 1.1 Shape: touchstones with branching stretches between them

Three nodes every path goes through (touchstones), with free-choice stretches between them:

```
Row 0   Fight  Fight  Fight     pick your first enemy (tier 1, previewed)
Row 1        UPGRADE            touchstone: everyone gets one upgrade
Rows    ┌ stretch A ┐           3 lanes, every node forks
2-3     └───────────┘           tier 1-2 fights, campfires, events...
Row 4         SHOP              touchstone: everyone can shop
Rows    ┌ stretch B ┐           3 lanes, every node forks
5-7     └───────────┘           tier 2-3 fights
Row 8       CAMPFIRE            touchstone: heal or push your luck
Row 9         BOSS
```

Stretch rules:
- 3 lanes (the phone map has room for 3 to 4).
- Every node links straight up **and** to one diagonal neighbour, so every step inside a stretch is a fork.
- Nodes side by side in a row differ in type where possible, so a fork is a real choice (fight or camp), not two of the same.
- Shop and Rest weights inside the stretches can drop, since the touchstones guarantee them.

How it compares (my Python replica of the generator, 20,000 maps, random walks):

| Layout | Nodes on a path | Fights before boss | Real choices per run | Paths with no shop / no rest |
|---|---|---|---|---|
| **Now** (7 rows) | 8 | 4.3 | 2.4 | 47% / 43% |
| Touchstones, 2+2 stretch rows | 9 | 3.3 | 5.0 | 0% / 0% |
| Touchstones, 2+3 stretch rows | 10 | 3.9 | 6.0 | 0% / 0% |
| Touchstones, 3+3 stretch rows | 11 | 4.5 | 7.0 | 0% / 0% |

**Open A, run length.**
- 2+2 is a shorter run with fewer fights.
- 3+3 roughly matches today's fight count, with three times the choices.
- A fight is 6 to 11 turns per player (audit 1.6), so every extra fight adds real time on a phone.
- **Lean:** 2+3, the middle option.

**Open B, the shop guarantee.**
- (a) A shop row everyone passes, as drawn above.
- (b) "At least one shop on every path", placed anywhere.
- **Lean:** (a). It's simpler to read on the map, and it splits the run into "before the shop" and "after the shop" halves.

**Open C, upgrade before or after the first fight.**
- After (as drawn above): a reward for the first win, and it fits the generator.
- Before: it helps the first fight, which the audit shows is the riskiest (Raiders on row 0).
- With a difficulty ladder (1.3), "after" is safe.

**Open D, row 0.** Do you pick from 2 or 3 previewed tier-1 enemies, or is there one fixed start? Picking gives you a choice from the first step.

### 1.2 The campfire (multi-option rest)

At a campfire you pick **one** of these:
- **Rest:** heal a share of max life (today 30%, which is 6 of 20).
- **Train:** upgrade a card (pick 1 of 3, like today's Upgrade node).
- **Purge:** cut a card (every copy).

This gives you your "play safe or push for an advantage" decision, and a camp is never useless at full life.

The healing number needs a look whatever else happens. Rest heals 6, but a won fight costs Jill 12 to 16 life (audit 4.4).

**Open E, what happens to the standalone Upgrade and Cut nodes** once the campfire offers both:
- (i) **Retire them.** Stretches hold fights, campfires, shops and (later) events. Upgrades then come from the row-1 touchstone, campfires and rewards; cuts from campfires and the shop.
- (ii) **Keep them, but stronger than the campfire version.** For example, the Upgrade node lets you choose both the card and the upgrade from a bigger offer, or upgrades two cards. The Cut node cuts and also pays some gold.
- (iii) **The campfire offers weaker versions** (a random upgrade on a random card; cut one copy), so the standalone nodes keep a role.
- **Lean:** (i). It's the cleanest, and it also settles the audit's overlap between the free Cut node and the 75-gold shop removal. After (i), cutting at a camp costs you the heal, and cutting at the shop costs gold.

### 1.3 A difficulty ladder

Today each enemy has only `minRow`, and enemies are picked at random, so the hardest normal enemy can come first (audit 1.5).

The proposal is a **tier** per encounter (1, 2 or 3), with the map assigning tiers by position:
- row 0 and stretch A: tier 1 (stretch A can also have tier 2)
- stretch B: tiers 2 to 3
- the boss: its own pool

Add "no enemy twice on one path". Optionally, show which boss waits at the top from the start, as Slay the Spire does.

This is small plumbing. It is not built, because the tiers themselves come from part 2.

### 1.4 The workshop node (added after review; scoping, not built)

Your idea is a node with its own theme: a blacksmith, war camp, artisan caravan or similar. The name is open; "Workshop" below is a placeholder. You buy three kinds of thing there, any number of times while you have gold:

| Service | How it would work | Built from |
|---|---|---|
| **A. Artifacts and Equipment** | A few artifact cards for gold (2 copies each, like shop cards), weighted toward Equipment | the Full Pool, artifacts only, in the run's colours (colourless always fits); FIN and FDN have 36 Equipment and 62 other artifacts before the colour filter |
| **B. Upgrade for gold** | Pick a (card, upgrade) pair from an offer of 3, like the Upgrade node; pay; return to the workshop; can be repeated | the Upgrade node's offer, plus the same "back to the shop" flow the shop's card removal already uses |
| **C. Stock up your Base** | Your Base gains "Remove a supply counter: put a +1/+1 counter on target creature you control. Activate only as a sorcery" and 3 supply counters. Counters you spend stay spent for the rest of the run | engine support exists; see below |

**C is the proof of concept: a resource the Base holds, which runs down over the run.**
- **Tried it:** a Base in the command zone can pay a supply counter from itself. A throwaway test activated it, the Base went from 3 to 2 counters, and the target got +1/+1. The test isn't kept.
- **What's missing is plumbing:**
  - **The count lives in the run.** The run state holds "supply: 3".
  - **Putting counters on at game start.** The Base needs that many counters when the game starts. It's a small engine addition: a "starting counters" field for the Base, next to `RegisteredPlayer.setBase`.
  - **Reading them back.** After the game, the run reads how many are left from the Base in the command zone, and stores that. This works even if the hero lost, because the run reads from registered players, as it already does for life.
- **It generalises:** the same counters plus run state can carry any limited-use Base ability later, such as a once-per-run heal, a few scries or a stored Treasure. Each one is a counter type, a script line and a price.
- Since the counter is a real card counter, the Base shows "Supply: 3" in the match, and what you spend is visible.

**Questions for you before building:**
1. **Uses: per run or per fight?**
   - "Non-replenishing" reads as per run, so spent counters stay spent. I'll build that unless you say otherwise.
   - Per fight would mean 3 in every match until the run ends, which is much stronger.
2. **Which creatures can it target?** Yours only, as I assumed, or any creature (which would allow tricks like pumping the enemy's creature into your blocker's deathtouch)?
3. **Buying again:** does a second purchase add 3 more counters to the same ability (my assumption)?
4. **Prices** (PLACEHOLDER until you set them):
   - an artifact about the shop's 30
   - an upgrade about 40, rising each time like removal, or flat
   - stocking up about 50
5. **Where it appears:** stretch nodes only (my assumption), or as a touchstone? It overlaps a little with the shop touchstone (both sell cards) and with the campfire's Train (free, but you give up the heal).
6. **Name and flavour.** Final Fantasy has Cid's workshops, the Gold Saucer and Moogle shops. Picking one is yours to do, and it only touches the label and the log text.

**Work involved**, about a day of building:
- a node type
- the offer and its three services
- the generalised "return to the workshop" flow (it's hard-coded to removal today)
- the supply counters (engine field, run state, read-back)
- text and screen buttons
- both simulator policies
- tests: buying each service, counters persisting across two matches, upgrade repeats, the screen compiles

---

## 2. Enemy decks and bosses

Shape rules unchanged: 40 cards (60 for bosses), 2 to 4 copies each with few 4-ofs, life 20 (30 for bosses), commanders only on bosses, identity in both the Base and the deck. Every card named below is in the 8-set scope and not quarantined; I checked each one against the card database.

Design notes from the audit that shaped these:
- **Enemy Base gimmicks should be triggers or static effects.** The AI never activated Lightning's Armory in 8 logged games, though it did use Wayfarer's Camp. So whether the AI uses an activated Base ability is unreliable.
- **Tie the gimmick to most of the deck.** Mountain Pass touches only 12 of the Dwarves' 40 cards.
- **Spread the colours.** Every current enemy is red, black or red-white, and the heroes are blue and red-white.
- **Landfall fires every turn in this mode**, once per land, until 10 lands. That makes landfall steady and predictable for an enemy. It is a natural theme here.

### At a glance

| Encounter | Tier | Colours | Identity | Base gimmick (suggested) |
|---|---|---|---|---|
| Chocobo Stables | 1 | G | landfall Birds | keeps one Chocobo on the field |
| Moogle Post | 1 | W | small lifelink fliers | Moogles gain life |
| A Troop of Dwarves *(existing)* | 1 | R(W) | Equipment, Hero tokens | re-aim at Equipment |
| Goblin Raiders *(existing)* | 2 | R | fast swarm | as now |
| The Restless Dead *(existing)* | 2 | B | Zombie grind | as now (fixed today) |
| Vampire Court | 2 | WB | life drain | life gained grows Vampires |
| Summoner's Circle | 2 | GW | Summon Sagas | a Summon ending gives value |
| Malboro Marsh | 2 | BG | deathtouch horrors | "Chef's Knife" for everyone |
| Cactuar Desert | 2 | G | a puzzle fight | small; the deck is the gimmick |
| **Random Encounter!** | any | R + FF monsters | chaos | everyone casts Random Encounter |
| Dragon's Aerie | 3 | R | fliers that get there fast | Dragons cost less |
| Elf Grove | 3 | G(B) | mana elves outrun the land curve | Elf tokens once the grove is big |
| **Boss: Sephiroth** | boss | B | sacrifice and drain, transforms | Jenova Cells: free fodder |
| **Boss: Dion → Bahamut** | boss | W | flying Knights, transforms | Knights hit harder |
| **Boss: Yuna** | boss | GW | Summons that come back once | Aeons grow |
| **Boss: Kefka** | boss | UBR | discard chaos, transforms | everyone loots |
| **Boss: Gilgamesh** | boss | R | the sword collector | equipped creatures strike first |
| Boss: Krenko *(existing)* | boss | R | Goblin swarm | see the note below |

### Tier 1

**Chocobo Stables** (mono-green). Ranchers raising Chocobos. Every turn's land pumps the Birds a little.
- *Base, "Stable Hands":* at the beginning of your end step, if you control no Bird, create the 2/2 Chocobo token Gysahl Greens makes. This keeps a Chocobo around without snowballing.
- *Key cards:* Gysahl Greens, Sazh's Chocobo, Sidequest: Raise a Chocobo, Summon: Fat Chocobo, Snapping Gnarlid; Chocobo Racetrack as the 2-of payoff.
- *Watch:* Racetrack makes a Bird every turn. If tier 1 should stay gentle, hold it to 2 copies or leave it out.

**Moogle Post** (mono-white). A delivery service of small fliers with lifelink. It teaches you to handle fliers without killing you.
- *Base, "Kupo!":* whenever a Moogle you control enters, you gain 1 life.
- *Key cards:* Cloudbound Moogle, Delivery Moogle, Summon: Choco/Mog, Stiltzkin, Moogle Merchant; Moogles' Valor as the rare.
- *Watch:* only about five Moogle cards exist in scope, so the rest is white filler (fliers, soldiers). Lifegain makes fights longer.

**A Troop of Dwarves** (existing). The audit found that its Base touches only the 12 Dwarves, while the deck's real theme is its 13 Equipment.
- *Option:* re-aim the Base at Equipment, for example "Whenever an Equipment you control enters, create a Treasure."
- It's the most beatable enemy today (44% in the audit sweep), so it is a natural tier 1.

### Tier 2

**Goblin Raiders** and **The Restless Dead** (existing). Raiders is the hardest normal enemy, at 8% in the audit sweep. Moving it to tier 2 is most of the ladder fix.

**Vampire Court** (white-black). Nobles who drain you a point at a time. Damage gets through even past blockers, which matters because your life carries over between fights.
- *Base, "Blood Tithe":* at the beginning of your end step, if you gained life this turn, put a +1/+1 counter on target Vampire you control.
- *Key cards:* Sanguine Syphoner, Bishop's Soldier, Marauding Blight-Priest, Bloodthirsty Aerialist, Falkenrath Noble; Elenda, Saint of Dusk as the 2-of rare.

**Summoner's Circle** (green-white). Summoners calling Final Fantasy's Summons, which are Saga creatures that play out over a few turns and then leave. It shows off FIN's signature mechanic, and it foreshadows the Yuna boss.
- *Base, "Eidolon Bond":* whenever a Saga creature you control is sacrificed, you gain 2 life and scry 1.
- *Key cards:* Summon: Choco/Mog, Summon: Primal Garuda, Summon: Fat Chocobo, Garnet, Princess of Alexandria, Clash of the Eikons.
- *Watch:* Summon: Titan is a poor fit here. Its chapter II returns lands from the graveyard, which does nothing without lands.

**Malboro Marsh** (black-green). Swamp horrors and plants: Tonberry's knife, Malboro's breath. An attrition fight where every block is scary.
- *Base, "Chef's Knife":* creatures you control have deathtouch during your turn (Tonberry's own ability, given to everyone).
- *Key cards:* Tonberry, Malboro, Gran Pulse Ochu, plus black and green deathtouch or removal creatures.

**Cactuar Desert** (mono-green, out there). Cactuars are cheap 3/3 tramplers that hop back to hand at end step. The real threat is **Jumbo Cactuar**: a 1/7 that gets +9,999/+0 when it attacks.
- The fight is a puzzle: always keep a chump blocker.
- *Base:* keep it tiny. **Never** give the deck trample or evasion, because Jumbo would then be unblockable death.
- *Key cards:* Cactuar, Jumbo Cactuar, Sabotender, Gran Pulse Ochu.
- *Watch:* one unblocked Jumbo attack ends the run, since life carries over and that is 10,000 damage. It's memorable, but harsh. It's worth deciding whether this is a fight you want, or an idea for an "event" node later.

### The chaos encounter

**Random Encounter!** This is your idea.
- *Base, "Wandering Monsters":* at the beginning of each player's upkeep, that player casts a copy of Random Encounter without paying its mana cost.
  - Random Encounter shuffles your library, mills four, puts each creature card milled onto the battlefield with haste, and returns them to hand at the next end step.
  - The engine can cast a copy of a named card (Tibalt, the Chaotic does it). I haven't tried it from a Base yet.
- *Deck:* a bestiary of weak Final Fantasy monsters, creature-dense so its own encounters hit. Cactuar, Coeurl, Tonberry, Blazing Bomb, Sabotender, Sahagin, Malboro and the like. Keep it deliberately weak, as you said.
- **The catch, decking:**
  - Milling 4 a turn plus drawing 1 burns 5 cards a turn.
  - Your 32-card deck has 25 cards left after the opening hand, so you deck out around your **6th** turn.
  - The enemy's 40-card deck lasts until about its 7th turn.
  - As written, you lose the decking race.
- **Open F, ways around it:**
  - (a) A custom version for this fight: reveal the top four, creatures enter with haste, the rest go to the **bottom** (no mill).
  - (b) Only the enemy casts it.
  - (c) It happens every other turn.
  - (d) It's built as an event node, not a fight.
  - **Lean:** (a). It keeps "both players get a random monster squad every turn" and drops the decking. Your own creatures show up too, about two per cast, since half a starter is creatures.

### Tier 3

**Dragon's Aerie** (mono-red). Dragons arriving a turn early, which matters a lot when mana is exactly one land a turn.
- *Base, "Hoard":* Dragon spells you cast cost {1} less.
- *Key cards:* Dragonlord's Servant, Dragon Hatchling, Firespitter Whelp, Rapacious Dragon, Sparktongue Dragon; Shivan Dragon as the rare.
- *Watch:* Jill's blue decks handle fliers. Lightning's deck has few answers to them.

**Elf Grove** (green, a little black). Mana Elves break this mode's rule that mana comes one land a turn, which is exactly what makes them scary late.
- *Base, "Ancient Grove":* at the beginning of your upkeep, if you control three or more Elves, create a 1/1 green Elf Warrior token.
- *Key cards:* Llanowar Elves, Elvish Mystic, Imperious Perfect, Elvish Archdruid, Dwynen's Elite, Elderfang Disciple, Skemfar Shadowsage.

### Bosses

Several bosses means a run does not always end against the same deck, and the map can show which one waits.

**Sephiroth** (mono-black). Commander: Sephiroth, Fabled SOLDIER.
- He drains a point every time any creature dies. After the fourth death in a turn, he becomes **Sephiroth, One-Winged Angel**: a 5/5 flier, plus an emblem that keeps the drain going.
- *Base, "Jenova Cells":* at the beginning of combat on your turn, create a 1/1 black creature token (fodder to sacrifice).
- *Key cards:* Falkenrath Noble, Midnight Reaper, Vayne's Treachery, Sephiroth's Intervention.
- The fight becomes "don't let him get four deaths in a turn".

**Dion → Bahamut** (mono-white Knights). Commander: Dion, Bahamut's Dominant.
- His Knights fly during his turn. For {4}{W}{W} he transforms into **Bahamut, Warden of Light**: a Saga that puts counters on his team and then destroys a permanent with Gigaflare.
- *Base, "Dragoon's Charge":* whenever a Knight you control attacks, it gets +1/+0 until end of turn.
- *Key cards:* Knight of Grace, Inspiring Paladin, Twinblade Paladin, Syr Alin, the Lion's Claw, Adelbert Steiner; Summon: Knights of Round as the finisher.

**Yuna** (green-white Summons). Commander: Yuna, Hope of Spira.
- On her turn her enchantment creatures (Summons are Sagas, and Sagas are enchantments) get trample, lifelink and ward {2}. Every end step she returns an enchantment from the graveyard with a finality counter, so each Summon comes back once more and is then exiled.
- *Base, "Aeons":* Saga creatures you control get +1/+1.
- *Key cards:* the Summoner's Circle list, scaled up.

**Kefka** (blue-black-red chaos). Commander: Kefka, Court Mage.
- Whenever Kefka enters or attacks, everyone discards. For {8} he transforms into **Kefka, Ruler of Ruin**, a 5/7 flier that draws whenever you lose life on his turn.
- *Base, "Light of Judgment":* at the beginning of your end step, each player discards a card, then draws a card.
- *Key cards:* Kuja, Genome Sorcerer, Kefka, Dancing Mad, plus black-red discard.
- This is the boss version of the Random Encounter chaos.

**Gilgamesh** (mono-red). Commander: Gilgamesh, Master-at-Arms.
- Whenever he enters or attacks, he pulls Equipment off the top six cards and attaches one. It's Final Fantasy's sword collector, and a mirror of Lightning's own Equipment plan.
- *Base, "Big Bridge":* equipped creatures you control have first strike.
- *Key cards:* Samurai's Katana, Brothers Yamazaki, plus Equipment in red and colourless.

**Krenko** (existing).
- With Raiders at tier 2, the boss's deck is still 38 of Raiders' 40 cards plus seven more (audit 1.5), so the last fight repeats a mid-run one.
- **Open G:** rework one of the two (a different tribe for Raiders, or a different Krenko deck), or keep both and rely on there being several bosses.

---

## 3. Other audit items

### Done today (built and pushed)

| Item | What changed |
|---|---|
| Upgrades that change nothing were offered (audit 3.3), e.g. Trample to Lightning, who has it | Rewards and Upgrade nodes leave out an upgrade whose keywords the card already has, printed or from an earlier upgrade. Ward is excepted, because it stacks. Tested |
| The Sunforger unlock text didn't mention its tutor (audit 2.4) | The text now says it's the full card, its instant-casting ability included |
| DESIGN.md N10 said "25% of rewards", the code does 25% of creature options (audit 3.3) | The doc now matches the code |
| No simulation includes any unlocks (audit 5.9) | The simulator takes `-Droguelike.sim.xp=N`: every run and fight starts at that level, so new enemies can be measured against a levelled hero. Tested |

### Ready to build once you've chosen (plumbing)

- Encounter tiers and the row-to-tier mapping (1.3).
- The touchstone map generator, with its settings in `config.json` (1.1).
- The campfire node (1.2).
- New encounter files and Bases, from whichever of part 2 you pick.

### Design questions, with the audit's numbers (no lean unless you ask)

1. **The boss reward does nothing.** With one floor, the 100 gold and the relic after the boss arrive after the run has ended (audit 4.3). Drop them, turn them into account rewards, or build floors.
2. **Relics are barely affordable.** Average gold at a shop is about 59, and every relic costs 60 (audit 4.5). Options:
   - price relics by strength
   - more gold per fight
   - relics from a tougher kind of fight, or from the campfire
3. **XP comes only from wins.** At simulated win rates, level 3 takes 6 to 20 runs (audit 5). Options:
   - some XP for losses
   - XP per node
   - a flatter curve
   - or wait for human win rates, which are likely higher
4. **Frozen Throne milestone.** It needs 30 bounces of a boss's commander. Several bosses (part 2) make that more reachable, or the counter could change.
5. **Level-5 Base unlocks replace the starting Base instead of adding to it** (Frozen Bastion, Bodhum Beachfront; audit 2.2).
6. **Buckets.** No black bucket exists, and Green Ramp is off-colour for both characters, so it almost never shows (audit 5).
7. **Upgrades only reach creatures.** Spells, Equipment and tokens never get one (audit 3.3; DESIGN D4).
8. **The AI doesn't use Lightning's Armory**, so simulations understate her. This is AI work in the engine. I can look into it if you want the simulations to be fair to her.
