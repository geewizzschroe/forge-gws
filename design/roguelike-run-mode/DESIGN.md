# Roguelike Run Mode: Design Document (working title)

A Legends of Runeterra "Path of Champions"-style run mode for Forge, with a Slay the Spire-style node map, built around an always-available Commander and a landless, deterministic mana system.

This document records every decision made so far, the evidence behind it, and a phased roadmap for building it. It was written at the end of a long design conversation so that a new session can pick the work up cold.

**Revisions.** Second: records the owner's answers to D3, D5, D6, D7, D10 and D12. Third: N4 (how new screens and content reach the phone) is decided; see 2.10. Open items are in section 3.

**Evidence tags used throughout**

| Tag | Meaning |
|---|---|
| **[code]** | Read directly in the source. Path and line numbers given. Not executed. |
| **[run]** | Observed by actually running something (a headless simulation or the real desktop build). |
| **[assumed]** | A design assumption or inference that has not been confirmed with the owner or with a test. |

Anything without a tag is a decision made by the owner.

---

## 1. Pitch

A run-based mode, played mainly on an Android phone. You pick a character, who is a Commander with a themed starter deck. You move across a branching node map. Enemy nodes are real Forge matches. After each match you pick one of three card rewards (each adds 2 copies to your deck) or one of three themed "buckets" of cards. Other nodes are shops, rests, card cuts and card upgrades. The run ends at a boss, who also has a Commander. You cannot edit your deck by hand. The deck only changes through the mode's own mechanics.

What makes it different from other Forge modes:

1. **Mana is not luck.** Decks contain no lands. Each turn you receive a land of your choice, up to a cap of 10.
2. **The Commander is always available** in the command zone, and never changes during a run.
3. **Cards are upgraded in place** for the rest of the run, per card name, and upgrades stack.
4. **Life is a run resource** that carries between matches.
5. **Account-wide meta-progression** unlocks starter decks and improves starting state.

---

## 2. Decisions

### 2.1 Run structure

- Pick a character (Commander) and a starter deck.
- Pick from the available nodes to move forward on a board-style map.
- Node types named so far: **Enemy**, **Boss**, **Shop**, **Rest** (restore health), **Cut a card**, **Upgrade a card**.
- Selecting an Enemy node means playing a match against it.
- After a match: pick **1 of 3 cards** (adds **2 copies**) **or 1 of 3 buckets** (3 randomly selected cards from a themed pool, e.g. "Goblins!", "Big Sorceries", "Green Staples").
- Continue from the node just played. Repeat until the only available node is the Boss.
- Beating the Boss gives a big reward and either moves to a new, harder "floor" or ends the run. **Deferred.**
- There is no manual deck editing anywhere in the mode.

### 2.2 Failure

- If you lose a match you retry that match.
- You get **one retry per run**. A second loss ends the run.
- [assumed] A retry restores the life total you had entering that match, against the same enemy.

### 2.3 Commander and decks

- The Commander is **always accessible** in the command zone and **does not change** during a run.
- Starter decks are centred on the Commander's colour identity, **not as a hard rule**. Off-identity splashes are allowed.
- Boss fights also use Commanders.
- **Deck-shape restrictions are essentially nonexistent**: no singleton rule, no fixed size.
- **Starter deck template** (decided 2026-10-21): 16 different cards, 2 copies each (32 cards): **8 commons, 6 uncommons, 2 rares** (by original printing). A two-colour starter splits each tier in half per colour: 4 commons, 3 uncommons and 1 rare of each colour.
- Worked example: **Jill, Shiva's Dominant** (Final Fantasy set, mono-blue). Her enter trigger bounces any one other nonland permanent. Her starter deck pairs blue with white flicker/bounce effects and good enter-the-battlefield (ETB) payoffs. Jill gets a mono-blue starter deck, then two blue+X starter decks that **unlock through progress**.

### 2.3.1 Card counts and upgrades

- The deck is a set of **card names with counts**. Rewards add copies.
- **Upgrades are per card name, not per copy.** They last for the rest of the run, including copies picked up later.
- Upgrades are **additive**. Numbers and abilities stack.
  - Example: your deck has 2x Savannah Lions with `[+1/+1]`. You take a reward of 2x Savannah Lions with `[haste]`. You now have 4x Savannah Lions, every one with both upgrades.
- **Stacking.** Upgrades that can stack, stack. An upgrade that grants a keyword which does nothing when duplicated (haste, vigilance and similar) has **no additional effect** when it appears more than once. [assumed] The engine's normal keyword rules already give this behaviour; verify with tests (N5).
- A reward card may be offered **with a randomly attached upgrade**.
- Upgrades unlocked in meta-progression apply **at the start of a run**.
- The "Upgrade a card" node modifies a card's features for the rest of the run.

### 2.4 Relics and the Commander

- Relics are acquired from **shops and bosses**.
- The Commander (or its game logic) can be upgraded through relics or level-up progression. The "big cool" example: being able to play Jill as her **Shiva** side directly.
- The Commander itself does not change identity. Upgrades modify it.
- **Relic presentation:** a single command-zone card that displays the player's **relic inventory** and lists each relic's effects. This is the one deliberate exception to the no-UI-space rule (2.7). How its text is generated, and where the relic effects themselves live, are open (N1).

### 2.5 Health

- Life **carries over** between matches.
- **Run max life starts at 20.** It can be raised by meta-progression ("levelling up" the character between runs).
- **In-match max life is double the run max** (40 at the start). Lifegain above that is wasted.
- After a match, run life = `min(end-of-match life, run max)`. Lifegain therefore acts like armour for that match only.

### 2.5.1 Mana model (the landless rule)

This is a **built-in rule of the mode**, part of its background logic. It is **not** a relic and **not** a card. It must take **no UI space**.

- **Decks contain no lands.** This applies to both sides, including boss decks.
- **At the beginning of each player's precombat main phase (after the draw step), if that player controls fewer than 10 lands, that player chooses a basic land type and a basic land of that type is conjured onto the battlefield.** Choosing after the draw means the player sees the new card first.
- **10 lands is the maximum in all cases**, regardless of source (the per-turn land, ramp spells, forced entries).
- Land-search ramp spells are **re-implemented in spirit** to conjure a basic instead of searching.
- **Ramp at the cap creates Treasure tokens.** A ramp effect that would conjure lands while the player is at the cap creates that many Treasure tokens instead. [assumed] If only some of the lands fit, the remainder become Treasures (N2).
- Mana therefore escalates at a fixed pace (1, 2, 3 ... 10) with no luck, as in other digital card games.
- Splashing becomes a per-turn decision rather than a deck-construction one.

*Superseded earlier ideas*: a single "Rainbow Basic" land (rejected: forces a colour pick on every tap), and implementing the rule as a command-zone relic (rejected: takes UI space, and the AI takes the first option; see 4.7).

### 2.6 Meta-progression

- Things acquired during a run (relics, card upgrades, added cards) belong to the run only.
- **Decided (D9, 2026-10-21):**
  - **Experience is earned per match**, not per run: a normal fight gives a little (about 30), a boss a lot (about 150), and finishing a run successfully a great deal (about 500, on top of the final boss). Scaled to the run's difficulty. So a failed run still earns experience for the matches in it.
  - **Two layers.** A **level per character** (Jill levels separately from future characters), and a second, **account-wide** level.
  - **Character level unlocks:** starter decks, higher run max life, upgrades installed at the start of a run, extra gold and extra retries. Putting time into a character makes them more capable and ready for runs with harder starting conditions. Bases: leaning per character (for tuning), not settled.
  - **Account level unlocks:** relics and cards added to the pools, cards unlocked a whole (curated) set at a time, more node types or other things that shake up even a level-1 character's runs; new characters perhaps someday, not yet.
  - **Milestones:** a handful of unlocks with esoteric conditions that unlock extra-out-there starter decks. Example: "Use Jill's enters ability to bounce a boss's [commander] 30 times" ([commander] is a placeholder word; it may become "captain" or similar).
  - **Unlocks happen at set levels**, as is usual in the genre: no choices or skill tree.
  - **Loadout:** before a run, the player picks which unlocks to bring.

### 2.7 UI constraint

Rules and mechanics changes must **not take up space in the UI**. The command zone row on the match screen shows every card in that zone, so card-based rule changes are not acceptable (see 4.8). The one deliberate exception is the single relic-inventory card (2.4).

### 2.8 UI host and entry point

The mode is hosted **inside Adventure mode** and entered from the Adventure menu. This was decided lightly ("Adventure menu, I suppose") and can be revisited if the match-launcher spike (6.1) shows it is too costly. New screens are Adventure scenes. `RewardScene`, `ShopScene`, `InnScene` and `EffectData` are the reusable parts, and `DuelScene` needs decoupling from overworld sprites (4.1).

### 2.9 Decking

A player who must draw from an empty library still loses. **Kept for now.** Deck power and size will be tuned as starter decks and boss decks are built.

### 2.10 Delivery of screens and content (N4)

New layout files and mode content are **bundled inside the app** and **copied into the resource folder on every launch**, so the existing loaders find them where they expect. If the updater wipes the resource folder, the next launch copies them back. The owner and the first design pass independently leaned this way.

Options considered: (1) read the files directly from inside the app with new loading code; (2) **chosen**; (3) host a separate resource bundle and change the updater address; (4) build the screens in code instead of layout files (solves layouts only).

Details:

- The source of truth stays in the repo's resource tree. New files go in their own folder so they cannot collide with official files. On the desktop dev build they are read straight from the repo, with no copying.
- **[assumed]** The Android build bundles that folder into the app's internal assets. A startup step copies it into the resource folder after the updater has finished and before any screen loads. **[code]** The updater hands control onward when it finishes and restarts the app after an update (`AssetsDownloader.checkForUpdates`, called from `Forge.java:284`), so the copy has a natural place to run. The exact spot is not yet confirmed.
- Saves and meta-progression live in the user-data folder, which the updater does not touch (4.10).
- Changing content means rebuilding and reinstalling the app. There is no release process.
- Fallback if the copy step proves awkward: read the bundled files directly with libGDX's internal file access (option 1).
- **Not tested on a device.** Whether reinstalling a rebuilt app triggers the resource re-download depends on the version string; probably not when it is unchanged.

---

## 3. Open design questions

### 3.1 Resolved since the first draft

- **D3. Relic presentation.** One command-zone card showing the relic inventory and each relic's effects (2.4).
- **D5. Duplicate keywords.** Stackable upgrades stack. One-time keywords (haste, vigilance) do nothing extra (2.3.1).
- **D6. Land timing.** Start of the precombat main phase, after the draw (2.5.1).
- **D7. Decking.** Kept for now (2.9).
- **D10. Ramp at the cap.** Creates that many Treasures (2.5.1).
- **D12. UI host.** Adventure, entered from the Adventure menu (2.8).
- **N4. Delivery.** Bundle in the app, copy into the resource folder on every launch (2.10).

### 3.2 Still open

None of these blocks Phase 1 (engine rules).

- **D1. Floors.** After a boss: new, harder floor, or end the run? (Phase 2)
- **D2. Currency.** Shops need one. Where does it come from (enemy nodes?) and what do things cost? (Phase 2)
- **D4. What can an upgrade be?** The engine supports power/toughness, keywords, abilities, mana cost, colours and types (4.4). Which are in scope, and how are they randomised on rewards? (Phase 1 item 4 and Phase 2)
- **D8. Node types and map.** Elites, events? Map shape, nodes per floor, difficulty scaling. (Phase 2)
- **D9. Meta-progression shape.** *Resolved*, see 2.6. Details still open: the experience needed per level and the level count, what "run difficulty" is (experience is scaled by it), whether lost matches give experience, how account experience is earned, and any limit on what a loadout can bring. (Phase 4)
- **D11. Landfall and domain cards.** Keep as free value or exclude from pools? Landfall now triggers every turn, and with chosen basics, domain counts only the types actually chosen. (Phase 2)
- **D13. Retry details.** See the assumption in 2.2. (Phase 2)

### 3.3 New questions raised by those answers

- **N1. Relic-inventory card.** The engine's card text is normally static. How is the text generated and kept current as relics are gained? Do the relics' effects live on that card, or in engine code with the card only displaying them? Untested. (Phase 1 item 6)
- **N2. Treasure fallback scope.** *Resolved:* only a failed ramp (a conjured land kept out by the cap). Each Treasure is tapped or untapped as the land would have been. Partial case: as many lands as fit, the rest as Treasures. Built (Phase 1 item 2). See decision 13.
- **N3. Skipped or extra main phases.** If a player skips their precombat main phase, do they miss that turn's land? No extra land for extra main phases is assumed. [assumed] (Phase 1 item 1)
- **N4. Delivery.** Resolved, see 3.1 and 2.10.
- **N5. Which keywords stack.** Assumed: the engine's normal rules decide (for example Ward and Flanking stack; haste and vigilance do not). Needs tests. (Phase 1 item 4)

### 3.4 Bases (user idea; core shape decided, details open)

Each side of a match has a **Base**: a game object for the place its forces fight from. It is always present, even when the commander is not on the battlefield. It stays out of normal interaction, like an emblem, and doesn't need to be on the battlefield. It could hold the relics (replacing the relic-inventory card, R7) and have its own passive and activated abilities. Bases would be largely interchangeable early in a run, grow stronger as matches are won, and could be swapped at a special node. That makes the Base a second layer of character creation before the run, and another place for upgrades to land.

Engine fit [code, not run]:
- The closest existing piece is the **Vanguard** variant: 106 avatar cards, each kept in its player's command zone for the whole game. They can change starting hand size and life (`HandLifeModifier`) and can have activated abilities used from the command zone (`ActivationZone$ Command`, for example Bosh, Iron Golem Avatar). `RegisteredPlayer` already carries a per-player avatar list (`Player.java` ~2978).
- Command-zone cards are mostly out of reach of normal cards. About a dozen card scripts target something in the command zone; the ones seen by name are mostly about commanders. Not checked one by one. With a curated card pool this can be controlled.
- Phase 1 item 4 already applies per-name upgrades to cards in the command zone (tested with a commander), so P/T and keyword upgrades would reach a Base the same way. Ability upgrades would need a new upgrade kind (perpetual abilities exist in the engine, 4.4).
- The mobile command-zone display shows every command-zone card (`VPlayerPanel.CommandZoneDisplay`, no filtering), so one Base per side keeps the one-card rule (2.7).

Answers so far:
- **B1. Decided:** the Base carries the relics. It replaces the relic-inventory card (R7).
- **B2. Decided:** at baseline a Base looks like a Vanguard avatar (life and hand size) and can have any ability. It is its own thing that obviously copies the avatar's shape, not the Vanguard avatars themselves, so nobody asks for specific avatars to be made selectable (each would need balancing). The only typing for now is the card type "Base". Some Bases should have a Hearthstone-style **Hero Power** (Hearthstone's adventures are another inspiration).
- **B3. Open, post-MVP (minimum viable product):** how a Base grows.
- **B4. Open:** what carries over on a swap. One idea: upgrading or swapping a Base resets its boosts, making it a give-and-take choice.
- **B5. Decided:** enemies and bosses have Bases too, and long term every encounter gets its own. An enemy's Base is where its **gimmick** lives; enemies and bosses don't get relics, so Bases are what keep matches from feeling samey beyond the cards in their decks. Example: "A Troop of Dwarves" fights from a Mountain Pass or a Mountainhome, with generic life and hand size for its stage of the run and a bespoke gimmick such as "Whenever a Dwarf you control enters, create a Treasure token." Bosses could have gimmicks that work with their commanders, or a second gimmick.
- **B6. Decided:** either way works, since Vanguard and this mode never meet. Built as a separate Base slot (see 6.2.1).
- **B7. New, open:** how a Base's life number relates to the player's run life (life carries over between matches, 2.5). For enemies it can simply be their starting life.

---

## 4. Findings (evidence)

### 4.1 How Forge modes are built (closest precedent: Planar Conquest)

- **[code]** Planar Conquest is a complete Commander-based campaign: about 3,060 lines of model code (`forge-gui/src/main/java/forge/gamemodes/planarconquest/`) plus about 3,690 lines of mobile screens (`forge-gui-mobile/src/forge/screens/planarconquest/`).
- **[code]** `ConquestController.startBattle` is the template for launching a match: build a `RegisteredPlayer` per side, call `assignCommander()`, set starting life, create `GameRules(GameType.PlanarConquest)`, then `hostedMatch.startMatch(rules, variants, starter, humanStart, gui)`. Results come back through `showGameOutcome` / `finishEvent`.
- **[code]** A mode touches about ten files outside its own package: `GameType.java`, `DeckFormat.java`, `FDeckEditor.java`, `NewGameMenu.java`, `HomeScreen.java`, `LoadGameMenu.java`, `ViewWinLose.java`, `FModel.java`, `DeckImportController.java`, plus achievements classes.
- **[code]** Adventure's `DuelScene` (834 lines) launches matches but is wired to overworld sprites: `initDuels(PlayerSprite, EnemySprite)` (lines 793-797). Enemy deck, boss flags, intro text, effects and games-per-match all come from the `EnemySprite`.
- **[assumed]** Conquest's map appears to be a grid of locations per plane (`ConquestMultiverseScreen`, `ConquestLocation`), not a branching graph. Only identifiers and fragments were read, not the drawing logic.

### 4.2 Reusable pieces in Adventure

- **[code]** `RewardScene` (`forge-gui-mobile/.../adventure/scene/RewardScene.java`, 747 lines) has a pick-from-shown-rewards type: `Type.RewardChoice` with `ChooseRewardButton` and `remainingSelections` (around lines 582-612, 685).
- **[code]** `RewardData` (`adventure/data/RewardData.java`) can filter by subtype, card type, colours, rarity, mana cost, keywords and card text, so themed buckets ("Goblins!") are expressible as data.
- **[code]** `ShopScene` and `InnScene` exist. Adventure shops spend gold.
- **[code]** `EffectData` (`adventure/data/EffectData.java`) is a relic-like mechanism: starting life, starting hand size, cards started on the battlefield or in the command zone, and effects on the opponent. 130 of Adventure's 200 items carry effects: 75 start a scripted card on the battlefield, 26 start one in the command zone, 43 modify life. They are backed by 68 hand-written card scripts in `forge-gui/res/adventure/common/custom_cards/`. Chandra's Tome is an example with activated abilities used from the command zone.
- **[code]** Adventure's New Game offers Commander precon starters and a "Pile" starter mode (`NewGameScene`, `AdventureModes`).
- **[code]** Adventure writes shards back after a duel by reading the finished game (`DuelScene.GameEnd`, `getHumanControllers()`). It does **not** write life back. It passes its saved life in as starting life (`DuelScene.java:400`).
- **[code]** Realm of Legends ships 985 complete `[Commander]` + `[Main]` deck files, one per legendary creature (`forge-gui/res/adventure/Realm of Legends/decks/legends/`). They are full Commander-sized decks (the sample opened was 84 lines) and would need trimming to serve as starters. They are usable as boss decks.

### 4.3 Engine facts

- **[code]** `Match.startGame(Game, Runnable startGameHook)` (`forge-game/.../Match.java:80`) takes a public hook. It runs inside `PhaseHandler.setupFirstTurn` (`PhaseHandler.java:1014-1027`) after the opening hands are dealt.
- **[code]** `RegisteredPlayer` has: `startingLife` (default 20), `startingHand` (7), `extraCardsOnBattlefield`, `extraCardsInCommandZone`, `commanders`, `assignCommander()`. `forCommander(deck)` sets 40 life; `forVariants` adds 20 for Commander.
- **[code]** There is **no deck legality gate** in `Match`, `Game` or `GameRules`. The only deck-size reference in `Match.java:297` substitutes Wastes for a missing deck. Legality is checked in lobbies and mode code (`GameLobby`, quest/conquest/adventure screens).
- **[run]** A Commander-variant match with 40-card, 4-of-style decks and no singleton rule runs to completion. The AI casts both commanders repeatedly from the command zone (Jill 1-2 times, Krenko 5-7 times per game).
- **[run]** The final life total of the winner is readable after the match (13, 19, 20 in seeded runs). The loser is removed from `game.getPlayers()`, so read loser data via `game.getRegisteredPlayers()`.
- **[code]** Forge itself places an immutable **"Commander Effect"** card in the command zone in Commander games. **[run]** Seen in the hero's command zone at game end.
- **[run]** In 20 seeded games, 15 hero losses were by life reaching zero and 1 by being **milled** (library empty, at 4 life). Decking is a real loss condition at these deck sizes.

### 4.4 Persistent card changes ("upgrades")

- **[code]** The engine keeps a list of `PerpetualInterface` records on each `Card` (`Card.java:4715-4745`): `PerpetualPTBoost`, `PerpetualNewPT`, `PerpetualKeywords`, `PerpetualAbilities` (traits, triggers, statics), `PerpetualColors`, `PerpetualTypes`, `PerpetualManaCost`, `PerpetualIncorporate`. `setPerpetual(oldCard)` re-applies them when a card changes zone.
- **[code]** Application is `card.addPerpetual(record); record.applyEffect(card);`.
- **[run]** Applied in the start-of-game hook to every copy of "Savannah Lions" (base 2/1) in every zone: two `PerpetualPTBoost(+1,+1)` and `PerpetualKeywords("Haste")`. All four entered as 4/3 haste, and **kept the upgrades after a bounce and after an exile-and-return**. A control card was unchanged.
- Not tested: the ability, mana-cost, colour and type record kinds; upgrades on tokens or copies of upgraded cards.
- **[code]** Decks (`CardPool`) store counts per `PaperCard` with no identity for individual copies, which is why per-name upgrades are the natural shape.

### 4.5 Life cap

- **[code]** The engine has no maximum-life concept. `Player.gainLife(int, Card, SpellAbility)` (`Player.java:444`) is a single choke point, and set-life effects route through it (`Player.java:420`).
- **[run]** A scripted replacement effect on a command-zone card capped life at 40 correctly: +30 from 20 gave 40, +10 more stayed 40, a 15-life loss gave 25, +5 gave 30. An uncapped opponent reached 50.
- Not tested: real lifelink, and set-life effects, through the cap.

### 4.6 Relics and the Commander

- **[run]** A scripted command-zone relic ability put the commander onto the battlefield as its back face (Jill to Shiva, Warden of Ice, a 4/5 Saga creature). The ability was resolved directly in code, so activation through normal play was not tested.
- **[code]** Shiva's chapter III exiles it and returns it **front face up**. "Play Shiva directly" is therefore a temporary form that reverts to Jill and would re-trigger Jill's enter ability.
- **[code]** Jill (`Jill, Shiva's Dominant`, `{2}{U}`, 2/2): "When Jill enters, return up to one other target nonland permanent to its owner's hand." The trigger is `Origin$ Any`, so it fires on a cast from the command zone and on a flicker.

### 4.7 The mana rule: spike results

All headless, AI vs AI, Commander variant, 20 starting life.

- **[run] Rainbow Basic (superseded).** A custom card typed `Basic Land Plains Island Swamp Mountain Forest` gets five automatic mana abilities. A command-zone trigger conjured one at each precombat main phase while under 10 lands. The no-ramp side had **1, 2, 3 ... 10 lands at its combat step, then stayed at 10, identically in all 6 seeds**. The new land is usable the turn it arrives. Landfall fired for every conjured land. The AI kept every landless 7-card hand and cast 26-34 spells per game.
- **[run] Rewritten ramp spell.** `MakeCard | Zone$ Battlefield | Tapped$ True | Conjure$ True` worked as a "Rampant Growth".
- **[code] `MakeCard` AI.** `ApiType.MakeCard` maps to `AlwaysPlayAi` (`SpellApiToAi.java:124`), which does not override `chooseCardName`, so the default takes the **first** option in the list.
- **[run] Relic route with a choice list.** An upkeep trigger with `Choices$ Plains,Island,Swamp,Mountain,Forest` worked mechanically, but the AI always chose Plains. A mono-red boss **cast zero spells in 3 of 3 games**, and the log warned 39 times about the default logic. Fixing this needs Java, so a script-only version cannot give bosses a sensible choice.
- **[run] Built-in route (emulated).** An event handler at the start of upkeep created the chosen basic directly, with a simple colour-need heuristic for the AI (colour symbols remaining in hand and library, divided by 1 + lands of that colour already out). The boss chose Mountains all ten times. The hero chose a Plains/Islands mix (for example P, I, P, P, I, P, I, P, I, I). Games ran 20-26 turns with 24-34 casts per side and no extra card in the command zone. This is the design adopted.
- **[run] Universal cap.** A replacement effect (`Event$ Moved | Destination$ Battlefield | ValidCard$ Land.YouCtrl | CheckSVar ... GE10 | Prevent$ True | Layer$ CantHappen`) blocked forced land entries: 14 Islands forced onto the capped player left exactly 10 lands; an uncapped player reached 14. In the final engine this becomes Java.
- **[run] Main-phase timing (D6).** The built-in emulation re-run with the land created at the beginning of the precombat main phase (after the draw) behaved the same: boss Mountains ten times out of ten, hero Plains/Islands mix, 22-26 turns, 27-34 casts per side, no exceptions, and the hero's command zone held only the engine's own Commander Effect card.
- **[code]** A Treasure token script exists (`forge-gui/res/tokenscripts/c_a_treasure_sac.txt`) for the ramp-at-cap fallback. The fallback itself is untested.
- **[code]** The AI has colour-aware basic-land choice code in `AiController` (around lines 619, 670, 1562) for its own land searches, which could inform the heuristic. It is not wired to `MakeCard`.
- **[code]** Commander colour-identity restriction on mana applies only to abilities written as `ColorIdentity` mana (`AbilityManaPart.java`), not to ordinary any-colour or basic-land mana.

### 4.8 UI

- **[code]** The mobile match screen's command-zone row (`VPlayerPanel.CommandZoneDisplay`) shows **every card in the command zone**, including effect cards. A search for a hidden/ignored flag found only `isImmutable()` and `isEmblem()`, used for card-detail text. **Not looked at on screen.**
- **[code]** Adventure screens (`UIScene`) build their layout from a JSON file loaded through `Config.getFile` (`UIScene.java:224`), that is, from the resource folder. The Adventure start menu wires its buttons by name from `start_menu.json` (`StartScene.java:45-54`; it includes a "Switch" button to Classic mode). A new screen or menu entry therefore needs either a layout file in the resource folder or code that builds the layout (resolved: 2.10).

### 4.9 Impact of the landless model on the card pool

Rough text-pattern counts over 34,088 card scripts (`forge-gui/res/cardsfolder`). They include digital "A-" rebalanced duplicates, so treat them as approximate. Landcycling was not counted.

| Category | Approx. count |
|---|---|
| Cards that are lands (removed from decks; special abilities lost) | 1,174 |
| Search the library for a land and put it onto the battlefield (ramp) | 244 |
| Search the library for a land to hand | 143 |
| Landfall / land-enters triggers | 248 |
| Count basic land types (domain-style) | 72 |

### 4.10 Platform and delivery (Android)

- **[run]** A debug-signed APK of this fork builds in the sandbox. Profile `android-dev-build` installs as package `forge.app.dev`, labelled "Forge (dev)", beside the official app (different package and signature). The official snapshot workflow only runs for the Card-Forge repository and needs its secrets.
- **[code]** For snapshot-versioned builds (this fork is `2.0.16-SNAPSHOT`), the resource download URL is Card-Forge's daily-snapshots release (`AssetsDownloader.java`, `ForgeConstants.GITHUB_SNAPSHOT_URL`). Data files in `forge-gui/res/` do **not** reach a phone through the dev APK unless the updater URL is changed and an `assets.zip` is hosted, or the data is embedded in the app build.
- **[code]** A resource update deletes the whole `res/` folder and re-extracts it (`GuiDownloadZipService.extract`). The user-data folder (`data/` under the app's obb directory on Android) is separate and is not deleted. Account-wide meta-progression should live there.
- **[code]** The in-app updater will offer Card-Forge's official snapshot to a fork build.
- **[code]** Combined with 4.8: Adventure-hosted screens that rely on layout files in `res/` hit the same delivery limit as content data.
- Mobile code is shared between Android and the desktop dev build (`forge-gui-mobile`, `forge-gui-mobile-dev`).

---

## 5. Architecture proposal

> This section is a **proposal** for review, not a record of decisions. Class names are suggestions.

### 5.1 Engine rules (forge-game / forge-ai / forge-gui)

| ID | Rule | Suggested home | Notes |
|---|---|---|---|
| R1 | Landless mana escalation | New per-match rule flag (on `GameRules` or `RegisteredPlayer`); turn-based action in `PhaseHandler.onPhaseBegin()` `case MAIN1:` (`PhaseHandler.java:276`), beside the existing turn-based actions (untap, draw) | Runs after the draw. Uses a chooser interface: human prompt via the player controller, AI via a heuristic that can see the freshly drawn card. Honours R2. See N3. |
| R2 | Global land cap of 10, with Treasure fallback | Engine check on any land entering the battlefield for flagged players | Must hold for conjures, ramp, forced entries. Prevention tested in script form only. Ramp effects at the cap create Treasure tokens (D10, N2). |
| R3 | In-match life cap and starting life | Fields on `RegisteredPlayer`, enforced in `Player.gainLife` | Run mode converts end life to run life. |
| R4 | Per-name upgrades | New `RegisteredPlayer` field (name to list of upgrade records), applied at game start via the existing hook | Reuses the engine's perpetual records. Stackable upgrades stack; duplicate one-time keywords have no extra effect (2.3.1, N5). |
| R5 | Deck normaliser | Mode-level: strip lands from any deck at match start | Applies to starter and boss decks. |
| R6 | Basic-land chooser for `MakeCard` | New AI class for `MakeCard` (or a script-param logic), sharing R1's heuristic | Needed so converted ramp spells work for the AI. |
| R7 | Relic inventory card | One generated command-zone card showing the relics and their effects | Text generation and where effects live are open (N1). |

### 5.2 Run model and content (forge-gui)

- **RunState**: seed, node graph and position, deck as name-to-count, upgrades as name-to-list, relics, run life, run max life, retries left, gold, floor.
- **MetaProfile** (account-wide): unlocked starter decks and upgrades, level/experience, starting-life bonuses. Stored under the user-data folder.
- **Content files**: starter decks (`.dck` with a `[Commander]` section, plus a small metadata file with the unlock condition), bucket definitions in `RewardData` syntax, an upgrade pool, a relic pool, a boss pool.
- **Run flow logic**: node graph generator, reward generator (cards, buckets, attached upgrades), shop, rest, cut and upgrade nodes, retry handling, boss and floor handling.
- **Match launcher** following `ConquestController.startBattle`, then converting the match result to run changes.

### 5.3 UI

New Adventure scenes (decided, 2.8): run start (character and starter pick), node map, reward pick, shop, rest, cut, upgrade, run summary and meta screen, plus an entry on the Adventure menu. Reuse `RewardScene` (pick-one rewards), `ShopScene` and `InnScene`. How layouts and content reach the device: see 2.10.

---

## 6. Roadmap

### 6.1 Phase 0: remaining decisions and spikes

The UI host is decided (2.8). Remaining Phase 0 work:

- **Match-launcher spike.** Launch `DuelScene`, or a minimal equivalent, without overworld sprites (4.1). If this proves expensive, revisit 2.8.
- **Delivery step spike (2.10).** Confirm where the copy-on-launch step runs relative to the updater. The desktop build cannot exercise the Android updater path, so this needs the Android build. Phase 3 can proceed meanwhile on desktop, where the files are read from the repo.
- Answer N1-N3 and N5 as convenient. They shape Phase 1, but none blocks its start.
- Still open and not blocking: D1, D2, D4, D8, D9, D11, D13.

### 6.2 Phase 1: engine rules (headless tests, no UI)

1. **R1 + R2**: built-in mana escalation at the start of the precombat main phase and the 10-land cap, with a chooser interface (human prompt, AI heuristic).
2. **R6**: `MakeCard` AI for basics. Convert a handful of the standard ramp pattern as a proof, including the **Treasure fallback at the cap**. A bulk conversion of the roughly 244 ramp cards is a later task.
3. **R3**: starting life, in-match life cap, end-of-match life read-back (including loser data from registered players).
4. **R4**: upgrade records applied at game start, plus a serialisable upgrade description. Tests for stacking: P/T boosts add; a duplicated haste or vigilance changes nothing; a stackable keyword (for example Ward or Flanking) stacks.
5. **R5**: land stripper for starter and boss decks. Decking stays (2.9).
6. **R7**: the relic-inventory command-zone card and the relic effect mechanism (N1).

**Exit criteria**: new TestNG tests for each rule pass (use the existing `AITest` pattern in `forge-gui-desktop/src/test/java/forge/ai/`); an AI-vs-AI soak over many seeds shows lands going 1 to 10, the cap holding, the Treasure fallback working, sensible colour choices, and no exceptions; the existing test suite for the touched modules still passes.

#### 6.2.1 Phase 1 progress

All engine switches default to off, so normal Forge games are unchanged. Tests live in `forge-gui-desktop/src/test/java/forge/game/`.

| Item | Status | Where | Tests |
|---|---|---|---|
| 1. R1 + R2 | Done [run] | `GameRules.setConjureBasicLandEachTurn` / `setMaxLandsPerPlayer`; `BasicLandEscalation`; turn action in `PhaseHandler` `case MAIN1`; cap check in `GameAction.changeZone` (a blocked land stays where it was); `PlayerController.chooseBasicLandToConjure` (human: pick-one prompt; AI: `AiBasicLandChoice`) | `BasicLandEscalationTest`, including one full AI-vs-AI game with landless decks |
| 2. R6 | Done [run]; 10 ramp cards converted | `forge-ai/.../ability/MakeCardAi` (always plays; a choice made only of basic lands uses `AiBasicLandChoice`, anything else keeps the old first-option pick). Treasure fallback in `MakeCardEffect`: a land it puts onto the battlefield that the cap keeps out becomes a Treasure via `BasicLandEscalation.makeTreasures`, which runs the normal token ability (so token doublers and token triggers apply); tapped if the land would have entered tapped. The built-in per-turn land never conjures at the cap, so it never makes Treasures. Converted cards: `GameRules.addCardOverride(rules)` replaces a card's script for every copy in a match's decks while keeping its set (so its art); the mode's landless versions live in the content's `overrides/` (Rampant Growth, Cultivate, Kodama's Reach, Explosive Vegetation, Farseek, Nature's Lore, Three Visits, Wood Elves, Sakura-Tribe Elder, Harrow). Cards conjured by name mid-game are not overridden. | `BasicLandEscalationTest`: untapped, tapped, room-for-one, no cap; `RunPlaythroughTest.rampDecksNeverPassTheCap` (soak) |
| 3. R3 | Done [run] | `RegisteredPlayer.setMaxLife` (0 = no limit), copied to `Player` at game start; `Player.gainLife` trims the gain after replacement effects, so doublers apply first and life-gain triggers see only life actually gained. The run mode sets it to 2× run max; the engine only knows "a cap". Starting life already existed. | `MaxLifeTest` |
| 4. R4 | Done [run] for P/T and keywords | `CardUpgrade` record (power, toughness, keywords) in `forge-game`; `RegisteredPlayer.addCardUpgrade(name, upgrade)`; applied in `Match.prepareAllZones` after commanders are created, as perpetual effects | `CardUpgradeTest`: additive on every copy, opponent's copies untouched, survives zone changes, commander upgraded in the command zone, repeated keywords kept as separate instances |
| 5. R5 | Done [run]; approach confirmed (decision 14) | `BasicLandEscalation.withoutLands(deck)`: copy of the deck without any card whose front face is a land (nonbasic lands included); spells with a land back face stay | `BasicLandEscalationTest` |
| 6. R7 → Base | Base slot done [run]; relic loading not built (Phase 2) | New card type **Base** (`CardType.CoreType.Base`, classed like Vanguard as a command-zone game piece; label `lblBase` in every language file, in English). It is left out of "choose a card type" lists, so normal games are unchanged; it does appear as one more entry in the deck editor's type filter. `BaseCard.fromScript(lines)` builds a Base from card-script text (Bases are not in the card database). `RegisteredPlayer.setBase(...)` puts it in the command zone at game start. Plan for relics: the run mode assembles each match's Base script from the Base's own lines plus each relic's lines, so no extra engine piece is needed. | `BaseCardTest`: built from script with type line "Base"; Dwarf trigger works from the command zone; paid once-per-turn activated ability (Hero Power shape) works; a registered Base starts in the command zone |

Notes from the build:
- N5 is tested structurally only: two Flanking upgrades give two Flanking instances, and doubled Haste is still Haste. A combat test proving two Flanking instances both trigger is not written. [code]
- N3 as built: the land comes only at the start of the turn's first precombat main phase. What happens when that phase is skipped is not tested. [code]
- The upgrade record is a plain value (P/T numbers and keyword strings), so the run mode can save it as text; save format is Phase 2 work.
- **Exit criteria met** [run]: the ramp soak (`-Droguelike.soakGames=N`, default 3) has green ramp decks reach exactly 10 lands and never more, with Treasures made at the cap, and no exceptions. The soak found one real bug, now fixed with a regression test: the AI's land picker crashed for a player with no commander once nothing left in hand or library needed a colour.
- AI caveat [assumed, untested]: `MakeCardAi` always plays, so the AI may sacrifice Sakura-Tribe Elder right away rather than when it blocks.
- Full suite: `xvfb-run -a mvn -B -pl forge-gui-desktop -am test`. Without a display, `forge.PanelTest` cannot start and the whole desktop test run is aborted before any test runs.

### 6.3 Phase 2: run model and content (headless)

RunState, node graph generator, reward generator (cards, buckets, attached upgrades), shop, rest, cut, upgrade, retry logic, save/load. Include a **headless run simulator** (AI plays both sides) so whole runs can be smoke-tested and tuned. Needs D1, D2, D4, D5, D8, D13.

#### 6.3.1 Phase 2 progress

Built [run], in `forge-gui/src/main/java/forge/gamemodes/roguelike/`. Every open design question is a **placeholder setting** in `RunConfig` (marked PLACEHOLDER there), not a decision.

- **Content** (`RoguelikeContent`): config, upgrade pool, starters, encounters, relics, card pools and buckets as JSON; Bases and card overrides as card scripts. It ships inside the app as classpath resources under `roguelike/` (with an `index.txt`, since bundled files can't be listed), so it reaches Android even though `res/` is replaced by downloaded assets. This is a simpler form of N4 option 2: the app reads its bundled copy directly. A `<user data>/roguelike/content/` folder, if present, is used instead, so content can be edited on a device. All content except `overrides/` is **sample content**.
- **Run state** (`RunState`): seed, deck (name to count), upgrades (name to list), relics, life and max life, retries, gold, floor, map, current and active node, any open choice, and a log. Saved as JSON after every step; an open choice is saved too, so reloading can't reroll it. Randomness is derived from the seed and a step counter, so a run is reproducible.
- **Map** (`RunMap`): rows of nodes joined by upward paths (Slay the Spire style), first row all enemies, every last-row node leading to one Boss. Encounters are assigned per node respecting each enemy's earliest row.
- **Rules** (`Run`): enter a reachable node; Rest heals; Shop, Upgrade and Cut open a choice; Enemy and Boss nodes build a match (`RunMatchFactory`): landless rules, land cap, card overrides, run life as starting life, 2x run max as the in-match cap, upgrades, the commander, the player's Base with relic lines added (`BaseAssembler`, which also writes a "Relics:" list into the Base's rules text, one per line), and the enemy's deck, life, hand size, commander and Base. After a match: win sets run life to min(end life, run max) and gives gold and a reward (cards or buckets after enemies, a relic after the boss); a loss uses a retry (life restored to its value before the match) or ends the run. Beating the last floor's boss wins the run.
- **Mode game type**: `GameType.Roguelike` (label "Roguelike Run"). Its achievements fall back to Constructed's.
- **Commander returns to the command zone** [run]: a dead or exiled commander may go back to the command zone (rule 903.9a), as in Commander games. Forge only checks this when the Commander, Brawl or Planeswalker variant is on, so the check now also runs for `GameType.Roguelike` (found in playtesting: Jill died and stayed in the graveyard). The rest of the Commander variant stays off; in particular 21 commander damage does not make a player lose. Commander tax (+2 each time it is cast from the command zone) applies, as it does wherever a commander exists. Test: `RoguelikeCommanderTest`.
- **Tests**: `RoguelikeContentTest` (content loads, every card name known, overrides conjure instead of search, every Base plus all relics builds, index up to date, bundle equals folder), `RunMapTest` (200 seeds: well-formed, all nodes reachable and leading to the boss; same seed same map), `RunTest` (each node type, shop gold, relic max life, reachability, save and load), `RunPlaythroughTest` (everything from the run reaches the match; whole runs with the AI playing the hero end in a win or a loss; the ramp soak). Sample runs end in losses, as expected with unbalanced sample content.

Tools for tuning and curating (built 2026-10-21):
- **Balance simulator** (`forge-gui/.../roguelike/sim/`: `RunSimulator`, `RunPolicy`, `RandomPolicy`, `SimReport`): plays whole runs with the AI on both sides under a choice policy, and single encounters (the starter deck against one enemy from a fixed life), recording per match the enemy, map step, life before and after, win, turns, timeouts and time. Output: a Markdown report and a CSV of every match, optionally every match's game log. Run with `xvfb-run -a mvn -pl forge-gui-desktop -am test -Dtest=RunBalanceSimTest -Dsurefire.failIfNoSpecifiedTests=false -Droguelike.sim.runs=20 [-Droguelike.sim.fights=10] [-Droguelike.sim.logs=true]`; files land in `forge-gui-desktop/target/roguelike-sim/`. A small smoke test runs with the normal suite. First report: `reports/sim-2026-10-21-first.md` (0 of 10 runs won; see its notes on why).
- **Landless card-pool audit** (`tools/landless_audit.py`, output `landless-audit.md`): groups the card database by how no-lands play changes each card (land searches, landfall, domain, land counts, land sacrifice, land destruction and more), with a note per group and the sample content's cards that fall into each. Pattern-based, so a starting point for D11 and for choosing pools, not a verdict. Rerun with `python3 -I design/roguelike-run-mode/tools/landless_audit.py . design/roguelike-run-mode/landless-audit.md`.

Placeholders and new questions raised by building it (all in `config.json`):
- **N6. Cut node:** how many copies does it remove? Placeholder: all copies of the chosen name.
- **N7. Card vs bucket rewards:** which is offered when? Placeholder: 30% of rewards are a choice of buckets (when buckets exist), otherwise a choice of single cards. Bucket contents are rolled and shown when offered.
- **N8. Shop contents and prices** (part of D2): placeholder 3 relics at 60 gold and 3 cards (2 copies) at 30; gold is 25 per enemy and 100 per boss.
- **N9. Rest:** placeholder heals 30% of run max life.
- **N10. Upgrade node:** placeholder offers 3 random (creature name, upgrade) pairs, pick one; reward cards come upgraded 25% of the time. Upgrade pool: +1/+1, +1/+0, +0/+2, Haste, Vigilance, Trample, Lifelink, Ward 1.
- **N11. Starter deck size:** the sample starter has 30 cards.
- **N12. Quitting mid-match:** closing the app during a match lets you replay that match from the start with no penalty.
- **D1** placeholder: one floor (the boss ends the run). **D8** placeholder: 7 rows, 4 columns, 3 paths from the start, node weights in the config.

### 6.4 Phase 3: UI

The Adventure scenes listed in 5.3, plus the menu entry. Test with the desktop dev build under a virtual display (see 7). The harness has already driven Adventure's start menu, character creation, map and console.

#### 6.4.1 Prototype screen

To get the mode playable on a phone before the Adventure scenes exist, there is a **prototype** entry, "Roguelike Run", in Classic Mode's New Game menu. This does not change decision 8 (Adventure is the intended home); the prototype is a stopgap and can be removed.

- `forge-gui-mobile/.../screens/roguelike/RoguelikeScreen`: one scrolling list of buttons for whatever the run needs next (pick a starter, pick a node, fight, pick a reward, shop, skip), a status line (life, gold, retries, floor, commander, deck size, relics), "View deck and relics" and "Abandon run". Text comes from `RunText` in `forge-gui`.
- `RoguelikeController` (`forge-gui`): keeps the run, saves it to `<user data>/roguelike/run.json` after every step, and starts matches through the normal match screen (`HostedMatch`).
- `RoguelikeWinLose`: the end-of-match screen shows only "Quit Match"; quitting applies the result to the run and returns to the run screen.
- Map: the node list is a drawn map (boss at the top, lettered circles joined by paths, the path taken highlighted, reachable nodes ringed). Tapping a node shows what it holds (an enemy's life, hand size, commander and Base gimmick) with a Go button. The reward prompt says what the match did to life and gold; a finished run shows a summary (result, floor, matches won and lost, enemies beaten, deck, relics, gold) above the run log.
- Card viewer: "Look at the cards" opens Forge's full-size card viewer on everything on offer (rewards, shop cards, upgrade and cut choices); swipe between cards and swipe up to take, buy, upgrade or cut the one shown. "Look at the Bases" shows the enemy's and your own Base (with relics) as generated cards before a fight; "Look at your deck" shows your commander, Base and every card.
- Checked on the desktop build of the mobile app under Xvfb [run]: the menu entry, starting a run, the node list, entering a fight, the match with both Bases and the commander in the command zone (enemy at 16 life with a 6-card hand), the per-turn land prompt, conceding, the retry (Retries 0, life restored, same fight offered again), and the deck view. Later also: the run surviving an app restart, the Bases in the card viewer, and a card reward and an Upgrade node taken by swiping in the viewer (the reward was put into the save by hand, since winning a match by clicking is slow). Winning a match through the UI was not clicked through; that path is covered by the headless tests.
- APK: the `android-dev-build` APK ("Forge (dev)", package `forge.app.dev`) contains the bundled content and the new screen [run: checked in the APK file]. Not yet run on a device.

### 6.5 Phase 4: meta-progression

MetaProfile, unlock rules, level-up effects (run max life, unlocked starter decks and upgrades). Needs D9.

#### 6.5.1 Phase 4 progress

Built [run] in `forge-gui/.../roguelike/meta/`, following the D9 answers (2.6). Details D9 left open are PLACEHOLDER values.

- **Content**: `characters/<id>.json` (`CharacterDef`: commander, starters open from level 1, unlocks per level, milestones with a counter name, target and unlocks) and `meta.json` (`MetaConfig`: experience curves and the account levels). Relics and card pools can be marked `"locked"`; they appear only once an account level unlocks them.
- **Unlock kinds**: per character `starter`, `maxLife`, `gold`, `retry`, `upgrade` (card plus upgrade, installed at run start), `base` (an alternate Base to choose); per account `relic`, `pool` (a locked card pool, buckets included) and `set` (a whole set's nonland cards, minus an `exclude` list, joins the reward pool).
- **Experience** (`RunConfig`): 30 per fight won, 150 per boss, 500 for winning the run, times `xpMultiplier` (PLACEHOLDER 1.0 until run difficulty exists). PLACEHOLDER: a lost match gives 0. Every point goes to the character and to the account.
- **Levels** (`Progression.level`): PLACEHOLDER curves, character level 2 at 200 experience and each next level 200 more than the last (3 at 600, 4 at 1200 ...); account level 2 at 500, then +500 more each time.
- **Loadout**: choose the character, then one unlocked starter, an unlocked Base, and which unlocks to bring (all on by default, remembered for next time). `Progression.setup` turns it into `RunSetup`, which `Run.start` applies (max life, gold, retries, starting upgrades, Base, the relics that can be offered, extra reward cards and unlocked buckets).
- **Milestones** (`MatchStatsTracker`, attached to each match): the first counter is `commanderEntersBouncedEnemyCommander` (your commander's enters trigger returning the enemy commander, a boss's, to hand). Run-level counters: `matchesWon`, `bossesBeaten`, `runsWon`. New counters need a little code each. Known gap: a bounce of the enemy commander by an effect that does not use the stack, followed by your commander's trigger resolving, would be counted.
- **Recording** (`RunProgress`): after each match and after the run is won; level-ups, account levels and completed milestones become messages shown after the match.
- **Prototype screen**: character select (with level and account level), loadout screen with toggles, a Progress view (earned and upcoming unlocks, milestone counters, account unlocks), and progress messages after matches. Saved in `<user data>/roguelike/profile.json`.
- **Sample content**: Jill's levels 2-10 (+2 life, the blue-white starter, +25 gold, the Frozen Bastion Base, Jill +1/+1, +1 retry, the blue-black starter, +3 life, Jill Ward 1); the milestone "bounce a boss's commander 30 times" unlocks a third, odd starter; account levels unlock Lucky Coin, the Green Ramp bucket, the Final Fantasy set (cards sharing a colour with the commander, PLACEHOLDER filter) and War Drum.
- **Simulator**: `RunSimulator.setProfile` plays runs with progression; `-Droguelike.sim.progress=true` adds the levels reached to the report, for pacing.
- **Tests**: `MetaProgressionTest` (level curve, unlocks by level, loadout to run state, account unlocks, recording and milestones, the bounce tracker in a real game, simulated runs recording experience).
- **Base abilities** (unlock kind `baseAbility`): an unlock can add card-script lines to the player's Base, listed on the Base under "Unlocked:". Used for unlocks that need abilities rather than numbers (the per-name upgrades only change power/toughness and keywords, and only on cards in the deck at the start).
- **Second character: Lightning** (`characters/lightning.json`, starter `lightning_rw`, the user's list: 8 commons, 6 uncommons, 2 rares; the rares are colourless and red-white, accepted). Same level structure as Jill, except her starter slots are Base abilities: level 3 "Sunforger" (whenever your commander enters, conjure a Sunforger attached to it, so she comes into play with Sunforger equipped every time) and level 8 "Sunforger Drill" (Sunforgers you control have equip Soldier {1}). Drafted Bases: Guardian Corps Armory (starting; "{2}: attach target Equipment you control to target creature you control, sorcery speed, once each turn") and Bodhum Beachfront (level 5; "whenever an equipped creature you control attacks, it gets +1/+1"). Reward pool `rw_core`: her deck plus Final Fantasy's mono-red, mono-white and colourless nonland commons and uncommons. No milestone yet. Tests: `LightningTest`.
- Not built: "harder starting conditions" (run difficulty), account unlocks of node types (no extra node types exist yet).

### 6.6 Phase 5: Android build and delivery

Build the side-by-side `forge.app.dev` APK. Implement and verify the copy-on-launch delivery step (2.10): bundle the mode's files in the app and copy them into the resource folder after the updater finishes. Disable or redirect the in-app updater prompt. On-device checks.

### 6.7 Phase 6: content and balance

More Commanders and starter decks (including Jill's two unlockable blue+X decks), bosses (starting from the legends decks), bucket, upgrade and relic pools, bulk ramp-card conversion, and balance tuning with the run simulator.

---

## 7. Test strategy and tooling

All of this was used successfully in the design sessions.

- **Headless engine tests.** `forge-gui-desktop/src/test/java/forge/ai/AITest.java` shows how to initialise the card database and build games. `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java` (`forge sim`) is the canonical AI-vs-AI runner.
- **Spike pattern** (`spikes/`): a standalone program compiled against `forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar`, run from the `forge-gui` directory with `-Djava.awt.headless=true -Duser.home=<scratch>`.
- **Real UI, headless.** Run the desktop dev jar (`forge-gui-mobile-dev`) under `Xvfb`, drive it with Java's `java.awt.Robot` (`ui-harness/Click.java`), take screenshots with ImageMagick `import`. In Adventure, **F9** opens the console. `teleport to poi <name>` is a quick way to load map content.
- The UI harness has already driven Adventure's start menu, character creation, map screen and console, which is the host chosen in 2.8.
- **Final check** has to happen on a real device.

---

## 8. Risks

| Risk | Detail |
|---|---|
| AI quality | Boss and hero AI with landless decks, custom colour choice, and upgraded cards is only lightly tested. The AI kept all landless hands and cast many spells, but play strength and balance were not evaluated. |
| Small decks | Decking (4.3) and long games. |
| Command-zone UI | Any card-based rule or relic occupies UI. Unresolved for relics (D3). |
| Delivery step | Copy-on-launch (2.10) is untested on a device: its timing relative to the updater, and whether a rebuilt app triggers a resource re-download. |
| Adventure coupling | Reusing `DuelScene` needs it decoupled from overworld sprites, which risks regressions in Adventure itself. |
| Scope | Conquest, a comparable mode, is about 6,700 lines. A lean first version should be a fraction of that, but this is a guess. |
| Fork drift | Upstream Forge changes the same files regularly. |
| Human prompt and upgrade display | The human land-choice prompt, and how upgrades are shown on cards outside matches, have not been prototyped. |

---

## 9. Decision log and supersessions

1. Rainbow Basic land, tapped for any colour. **Replaced** by a per-turn chosen basic (a colour pick on every tap was judged tedious).
2. Mana rule as a command-zone relic. **Replaced** by a built-in rule (UI space, AI first-option problem, "feels hacky").
3. Land cap as a relic effect. **Replaced** by an engine cap, 10 in all cases.
4. Upgrades per copy. **Replaced** by per name, additive.
5. A "play Shiva directly" relic is a design aspiration; Shiva reverts at chapter III (4.6).
6. Land choice moved from upkeep to the start of the precombat main phase, after the draw (D6).
7. Decking kept for now (D7).
8. UI host: Adventure, entered from the Adventure menu (D12). Lightly held.
9. Relics get one command-zone inventory card (D3), an exception to 2.7. *Superseded by 15: the Base is that card.*
10. Ramp at the cap creates Treasure tokens (D10).
11. Duplicate keywords: stackable ones stack, one-time keywords do nothing extra (D5).
12. Delivery: bundle in the app, copy into the resource folder on every launch (N4, 2.10).
13. Treasure fallback only on a failed ramp: a conjured land kept out by the cap becomes a Treasure, tapped if the land would have entered tapped. Other blocked lands just stay where they were (N2).
14. Land stripping removes every card whose front face is a land, nonbasic lands included; spells with a land back face stay. Curating the mode's card pool handles the rest.
15. Relics live on the player's Base (B1). Bases copy the Vanguard shape but are their own card type, "Base" (B2). Enemies and bosses have Bases that carry their gimmicks (B5).
16. Meta-progression (D9): per-match experience; a level per character plus an account level; character levels unlock starters, max life, starting upgrades, gold and retries; account levels unlock pool content and variety; milestone unlocks for unusual starters; unlocks at fixed levels; pick a loadout before each run.

---

## 10. Notes for the implementing session

- **Repository and branch**: `geewizzschroe/forge-gws`, development branch `claude/android-apk-forked-game-051b3w`. Do not open a pull request unless asked.
- **Build** (sandbox, JDK 21, Maven 3.9): run a full `mvn install -Dmaven.test.skip=true` first. Single-module builds with `-pl` fail unless dependencies are included, so use `-am`. Android APK: download the custom Android Maven plugin from Card-Forge's releases into `~/.m2`, install the Android SDK platform 35 and build-tools 35.0.0, then `mvn -P android-dev-build verify -pl forge-gui-android -am -Dandroid.sdk.path=<sdk> -Dandroid.buildToolsVersion=35.0.0 -Dmaven.test.skip=true`.
- **Gotchas**: Java ignores `$HOME`, so pass `-Duser.home`. `GuiDesktop.getAssetsDir()` returns `""` for non-"git" version strings, so run from the `forge-gui` directory. Do not `pkill -f` a pattern that appears in your own command line.
- **Do not break Adventure.** The mode should add to `GameType`, menus and a new package, not change Adventure behaviour.
- The spike files are throwaway references, not production code. They are not part of any Maven module.
- Keep the evidence tags in this document up to date as claims get verified.
