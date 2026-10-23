package forge.gamemodes.roguelike;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import forge.LobbyPlayer;
import forge.StaticData;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.roguelike.RunMatchFactory.RunMatch;
import forge.item.PaperCard;

/**
 * One run: the rules of the run mode on top of a {@link RunState}. No UI; screens call these methods.
 *
 * Flow: {@link #getAvailableNodes()}, {@link #enterNode(int)}; for a match node {@link #createMatch}, play
 * it, then {@link #finishMatch}; resolve any {@link RunState#offer} with {@link #choose(int)},
 * {@link #buy(int)} or {@link #skip()}; repeat until the run is won or lost.
 */
public class Run {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final RoguelikeContent content;
    private final RunState state;

    public Run(final RoguelikeContent content, final RunState state) {
        this.content = content;
        this.state = state;
    }

    public static Run start(final RoguelikeContent content, final String starterId, final long seed) {
        return start(content, starterId, seed, null);
    }

    /** Start a run with starting conditions from meta-progression (null for none). */
    public static Run start(final RoguelikeContent content, final String starterId, final long seed, final RunSetup setup) {
        RunConfig cfg = content.getConfig();
        StarterDef starter = content.getStarter(starterId);
        RunState s = new RunState();
        s.seed = seed;
        s.starterId = starter.id;
        s.commander = starter.commander;
        s.deck.putAll(starter.deck);
        s.maxLife = cfg.startingMaxLife;
        s.life = s.maxLife;
        s.retriesLeft = cfg.retries;
        s.gold = cfg.startingGold;
        s.fullPoolSets.addAll(cfg.fullPoolSets);
        if (setup != null) {
            s.characterId = setup.characterId;
            s.maxLife += setup.maxLifeBonus;
            s.life = s.maxLife;
            s.gold += setup.goldBonus;
            s.retriesLeft += setup.retryBonus;
            setup.upgrades.forEach((name, ups) -> s.upgrades.computeIfAbsent(name, k -> new ArrayList<>()).addAll(ups));
            s.baseOverride = setup.base;
            s.relicPool = setup.relicPool == null ? null : new ArrayList<>(setup.relicPool);
            s.extraRewardCards.addAll(setup.extraRewardCards);
            s.unlockedPools.addAll(setup.unlockedPools);
            for (String set : setup.fullPoolSets) {
                if (!s.fullPoolSets.contains(set)) {
                    s.fullPoolSets.add(set);
                }
            }
            s.fullPoolSets.removeAll(setup.disabledSets);
            s.baseAbilities.addAll(setup.baseAbilities);
        }
        s.map = RunMap.generate(cfg, floorRule(cfg, 1).layout != null ? floorRule(cfg, 1).layout : cfg.mapLayout,
                content, s.nextRandom(), Set.of());
        s.log.add("Run started with " + starter.name + " (seed " + seed + ")");
        return new Run(content, s);
    }

    public RunState getState() {
        return state;
    }

    public RoguelikeContent getContent() {
        return content;
    }

    // ---------------------------------------------------------------- map

    /** Nodes the player may enter now; empty while a node is active or the run is over. */
    public List<MapNode> getAvailableNodes() {
        if (state.status != RunStatus.IN_PROGRESS || state.activeNodeId != null) {
            return Collections.emptyList();
        }
        List<MapNode> out = new ArrayList<>();
        List<Integer> ids = state.currentNodeId == null ? state.map.startIds : state.map.get(state.currentNodeId).next;
        for (int id : ids) {
            out.add(state.map.get(id));
        }
        return out;
    }

    public MapNode getActiveNode() {
        return state.activeNodeId == null ? null : state.map.get(state.activeNodeId);
    }

    public void enterNode(final int nodeId) {
        boolean reachable = false;
        for (MapNode n : getAvailableNodes()) {
            reachable |= n.id == nodeId;
        }
        if (!reachable) {
            throw new IllegalStateException("Node " + nodeId + " can't be entered now");
        }
        MapNode node = state.map.get(nodeId);
        state.activeNodeId = nodeId;
        state.log.add("Entered " + RunText.node(this, node));
        switch (node.type) {
            case ENEMY, BOSS -> state.lifeBeforeMatch = state.life;
            case REST -> state.offer = makeCampfireOffer();
            case SHOP -> state.offer = makeShopOffer();
            case UPGRADE -> {
                state.offer = makeUpgradeOffer();
                if (state.offer.options.isEmpty()) {
                    state.offer = null;
                    completeNode();
                }
            }
            case CUT -> state.offer = makeCutOffer();
            case WORKSHOP -> state.offer = makeWorkshopOffer();
            case EVENT -> {
                state.offer = makeEventOffer();
                if (state.offer == null) {
                    state.log.add("Nothing happened");
                    completeNode();
                }
            }
        }
    }

    private void completeNode() {
        if (state.activeNodeId != null) {
            state.visited.add(state.activeNodeId);
        }
        state.currentNodeId = state.activeNodeId;
        state.activeNodeId = null;
        state.offer = null;
    }

    // ---------------------------------------------------------------- matches

    public EncounterDef getActiveEncounter() {
        MapNode node = getActiveNode();
        if (node == null || !node.type.isMatch() || state.offer != null) {
            return null;
        }
        return content.getEncounter(node.encounterId);
    }

    /** The player's Base as it will be in the next match, relics included; null if there is none. */
    public PaperCard getPlayerBase() {
        List<RelicDef> relics = new ArrayList<>();
        for (String id : state.relics) {
            relics.add(content.getRelic(id));
        }
        String baseFile = state.baseOverride != null ? state.baseOverride : content.getStarter(state.starterId).base;
        List<BaseAbility> abilities = new ArrayList<>(state.baseAbilities);
        if (state.baseSupply > 0) {
            abilities.add(new BaseAbility("Supplies", "", content.getConfig().workshopSupplyScript));
        }
        List<String> script = BaseAssembler.assemble(baseFile == null ? null : content.getBase(baseFile), relics, abilities);
        return script == null ? null : BaseCard.fromScript(script);
    }

    /** An enemy's Base, or null if it has none. */
    public PaperCard getEncounterBase(final EncounterDef enc) {
        List<String> buff = floorRule().enemyBaseScript;
        if (buff == null || buff.isEmpty()) {
            return enc.base == null ? null : BaseCard.fromScript(content.getBase(enc.base));
        }
        // the floor's buff joins the enemy's own Base (or a plain one), shown as its own line of rules text
        return BaseCard.fromScript(BaseAssembler.assemble(enc.base == null ? null : content.getBase(enc.base), List.of(),
                List.of(new BaseAbility("Floor " + state.floor, "", buff))));
    }

    /** An enemy's starting life on this floor. */
    public int encounterLife(final EncounterDef enc) {
        return enc.life + floorRule().enemyLifeBonus;
    }

    /**
     * The upgrades this floor gives an enemy's creatures: {@code enemyUpgrades} creature names from its deck, each
     * with a random upgrade from the pool. The same for every look at the same enemy on the same floor of a run.
     */
    public Map<String, RunUpgrade> encounterUpgrades(final EncounterDef enc) {
        int n = floorRule().enemyUpgrades;
        Map<String, RunUpgrade> out = new LinkedHashMap<>();
        if (n <= 0 || content.getUpgrades().isEmpty()) {
            return out;
        }
        Random rnd = new Random(state.seed * 31 + state.floor * 7919L + enc.id.hashCode());
        List<String> creatures = new ArrayList<>();
        for (String c : enc.deck.keySet()) {
            if (isCreature(c)) {
                creatures.add(c);
            }
        }
        for (String c : pick(creatures, n, rnd)) {
            out.put(c, content.getUpgrades().get(rnd.nextInt(content.getUpgrades().size())));
        }
        return out;
    }

    public RunMatch createMatch(final LobbyPlayer hero, final LobbyPlayer enemy) {
        EncounterDef enc = getActiveEncounter();
        if (enc == null) {
            throw new IllegalStateException("No match to play now");
        }
        return RunMatchFactory.create(content, state, enc, hero, enemy);
    }

    /** Apply a finished game to the run. Returns true if the hero won. */
    public boolean finishMatch(final RunMatch match, final Game game) {
        if (!game.isGameOver()) {
            throw new IllegalStateException("The game is not over");
        }
        boolean won = game.getOutcome() != null && !game.getOutcome().isDraw()
                && game.getOutcome().isWinner(match.hero());
        int endLife = 0;
        for (Player p : game.getRegisteredPlayers()) {
            if (p.getRegisteredPlayer() == match.hero()) {
                endLife = p.getLife();
                if (won && state.baseSupply > 0) { // a lost match is retried or ends the run, so its spending doesn't count
                    state.baseSupply = baseSupplyLeft(p);
                }
            }
        }
        return applyMatchResult(match.encounter(), won, endLife);
    }

    /** The SUPPLY counters left on the player's Base at the end of a game. */
    private static int baseSupplyLeft(final Player p) {
        for (Card c : p.getCardsIn(ZoneType.Command)) {
            if (c.getType().hasStringType("Base")) {
                return c.getCounters(CounterEnumType.SUPPLY);
            }
        }
        return 0;
    }

    /** Apply the result of the active node's match (also used by debug tools, without a game). */
    public boolean applyMatchResult(final EncounterDef encounter, final boolean won, final int endLife) {
        if (getActiveEncounter() == null) {
            throw new IllegalStateException("No match is being played");
        }
        MapNode node = getActiveNode();
        RunConfig cfg = content.getConfig();
        if (won) {
            state.xpEarned += Math.round((node.type == NodeType.BOSS ? cfg.xpPerBoss : cfg.xpPerFight) * cfg.xpMultiplier);
            state.matchesWon++;
            state.beaten.add(encounter.name);
            if (encounter.boss) {
                state.bossesBeaten.add(encounter.id);
            }
            state.life = Math.max(1, Math.min(endLife, state.maxLife));
            boolean boss = node.type == NodeType.BOSS;
            int gold = boss ? content.getConfig().goldPerBoss : content.getConfig().goldPerEnemy;
            state.gold += gold;
            state.log.add("Beat " + encounter.name + " with " + endLife + " life; run life " + state.life
                    + ", +" + gold + " gold");
            state.offer = boss ? makeBossRelicOffer() : makeCardRewardOffer();
            if (state.offer.options.isEmpty()) {
                state.offer = null;
                afterMatchReward();
            }
        } else if (state.retriesLeft > 0) {
            state.xpEarned += Math.round(cfg.xpLostMatch * cfg.xpMultiplier);
            state.matchesLost++;
            state.retriesLeft--;
            state.life = state.lifeBeforeMatch;
            state.log.add("Lost to " + encounter.name + "; retrying (" + state.retriesLeft + " retries left)");
        } else {
            state.xpEarned += Math.round(cfg.xpLostMatch * cfg.xpMultiplier);
            state.matchesLost++;
            state.status = RunStatus.LOST;
            state.log.add("Lost to " + encounter.name + "; the run is over");
        }
        return won;
    }

    private void afterMatchReward() {
        MapNode node = getActiveNode();
        completeNode();
        if (node.type == NodeType.BOSS) {
            if (state.floor >= content.getConfig().floors) {
                state.xpEarned += Math.round(content.getConfig().xpRunWon * content.getConfig().xpMultiplier);
                state.status = RunStatus.WON;
                state.log.add("Run won");
            } else {
                state.offer = makeCommanderUpgradeOffer();
                if (state.offer.options.isEmpty()) {
                    state.offer = null;
                    advanceFloor();
                }
            }
        }
    }

    /** The rules of a floor: its entry in {@code floorRules}, the last entry past the end, or none. */
    static FloorRule floorRule(final RunConfig cfg, final int floor) {
        if (cfg.floorRules == null || cfg.floorRules.isEmpty()) {
            return new FloorRule();
        }
        return cfg.floorRules.get(Math.min(floor, cfg.floorRules.size()) - 1);
    }

    public FloorRule floorRule() {
        return floorRule(content.getConfig(), state.floor);
    }

    /** On to the next floor: full life, a new map from that floor's layout, and no boss met again. */
    private void advanceFloor() {
        state.floor++;
        state.life = state.maxLife;
        FloorRule rule = floorRule();
        state.map = RunMap.generate(content.getConfig(), rule.layout != null ? rule.layout : content.getConfig().mapLayout,
                content, state.nextRandom(), new HashSet<>(state.bossesBeaten));
        state.currentNodeId = null;
        state.visited.clear();
        state.log.add("Floor " + state.floor + ": healed to " + state.life + " life");
    }

    /** Between floors: three rare upgrades that would change the commander, one to take. */
    private Offer makeCommanderUpgradeOffer() {
        Offer offer = new Offer(OfferKind.COMMANDER_UPGRADE, false);
        for (RunUpgrade u : pick(usefulUpgrades(state.commander, content.getRareUpgrades()), 3, state.nextRandom())) {
            OfferOption o = new OfferOption();
            o.upgradeCard = state.commander;
            o.upgrade = u;
            offer.options.add(o);
        }
        return offer;
    }

    // ---------------------------------------------------------------- choices

    /** Take one option of the open offer (not for stores; use {@link #buy(int)}). */
    public void choose(final int index) {
        Offer offer = requireOffer();
        if (offer.kind.isStore()) {
            throw new IllegalStateException("Use buy() in a store");
        }
        OfferOption o = offer.options.get(index);
        if (offer.kind == OfferKind.CAMPFIRE) {
            chooseAtCampfire(offer, o);
            return;
        }
        if (offer.returnToShop != null) { // a second choice opened from a store or a campfire
            Offer parent = offer.returnToShop;
            if (state.gold < o.price) {
                throw new IllegalStateException("Can't pay " + o.price + " gold with " + state.gold);
            }
            state.gold -= o.price;
            String paid = o.price > 0 ? "Paid " + o.price + " gold to " : parent.kind == OfferKind.CAMPFIRE ? "At the campfire: " : "";
            if (o.cutCard != null) {
                state.log.add(paid + "remove every " + o.cutCard);
                state.deck.remove(o.cutCard); // every copy, whatever cutCopies says
                if (parent.kind == OfferKind.SHOP) {
                    state.removalsBought++;
                    for (OfferOption shopOption : parent.options) {
                        if (shopOption.removal) {
                            shopOption.bought = true;
                        }
                    }
                }
            } else {
                state.log.add(paid + "upgrade " + o.upgradeCard + ": " + o.upgrade.describe());
                apply(o);
            }
            if (parent.kind == OfferKind.CAMPFIRE) {
                state.offer = null;
                completeNode();
            } else {
                state.offer = parent;
            }
            return;
        }
        if (offer.kind == OfferKind.EVENT) {
            payEventCosts(o);
        }
        state.log.add("Chose " + RunText.option(this, o));
        apply(o);
        if (offer.kind == OfferKind.COMMANDER_UPGRADE) {
            state.offer = null;
            advanceFloor();
            return;
        }
        boolean matchReward = offer.kind == OfferKind.CARD_REWARD || offer.kind == OfferKind.BOSS_RELIC;
        state.offer = null;
        if (matchReward) {
            afterMatchReward();
        } else {
            completeNode();
        }
    }

    public void buy(final int index) {
        Offer offer = requireOffer();
        if (!offer.kind.isStore()) {
            throw new IllegalStateException("Not in a store");
        }
        OfferOption o = offer.options.get(index);
        if (o.bought || state.gold < o.price) {
            throw new IllegalStateException("Can't buy " + o + " with " + state.gold + " gold");
        }
        if (o.removal) {
            // paid for when a card is chosen; backing out returns to the shop for free
            Offer cut = makeCutOffer();
            cut.returnToShop = offer;
            for (OfferOption c : cut.options) {
                c.price = o.price;
            }
            state.offer = cut;
            return;
        }
        if (o.upgradeService) {
            // paid for when an upgrade is chosen; backing out returns to the Workshop for free
            Offer upgrade = makeUpgradeOffer();
            if (upgrade.options.isEmpty()) {
                return;
            }
            upgrade.returnToShop = offer;
            for (OfferOption u : upgrade.options) {
                u.price = o.price;
            }
            state.offer = upgrade;
            return;
        }
        if (o.supply > 0) { // repeatable: each purchase adds more
            state.log.add("Bought " + o.supply + " Base supplies (" + o.price + " gold)");
            state.gold -= o.price;
            state.baseSupply += o.supply;
            return;
        }
        state.log.add("Bought " + RunText.option(this, o));
        state.gold -= o.price;
        o.bought = true;
        apply(o);
    }

    /** Leave a shop, or pass on an offer that allows it. */
    public void skip() {
        Offer offer = requireOffer();
        if (!offer.canSkip) {
            throw new IllegalStateException("This choice can't be skipped");
        }
        if (offer.returnToShop != null) {
            state.offer = offer.returnToShop;
            return;
        }
        boolean matchReward = offer.kind == OfferKind.CARD_REWARD || offer.kind == OfferKind.BOSS_RELIC;
        state.offer = null;
        state.log.add(switch (offer.kind) {
            case SHOP -> "Left the shop";
            case WORKSHOP -> "Left the Workshop";
            case EVENT -> "Walked away";
            default -> "Skipped the choice";
        });
        if (matchReward) {
            afterMatchReward();
        } else {
            completeNode();
        }
    }

    private Offer requireOffer() {
        if (state.offer == null) {
            throw new IllegalStateException("Nothing to choose now");
        }
        return state.offer;
    }

    /** Rest heals and moves on; train and purge open their choice, and backing out of it returns to the fire. */
    private void chooseAtCampfire(final Offer campfire, final OfferOption o) {
        if (o.heal > 0) {
            state.life = Math.min(state.maxLife, state.life + o.heal);
            state.log.add("Rested to " + state.life + " life");
            state.offer = null;
            completeNode();
            return;
        }
        Offer next = o.train ? makeUpgradeOffer() : makeCutOffer();
        if (next.options.isEmpty()) {
            throw new IllegalStateException("Nothing to " + (o.train ? "train" : "purge"));
        }
        next.returnToShop = campfire;
        state.offer = next;
    }

    /** Whether the player can pay an option's gold, life and retry costs (life must stay above 0). */
    public boolean canAfford(final OfferOption o) {
        return state.gold >= o.price && state.life > o.lifeCost && state.retriesLeft >= o.retryCost;
    }

    private void payEventCosts(final OfferOption o) {
        if (!canAfford(o)) {
            throw new IllegalStateException("Can't pay for " + o);
        }
        state.gold -= o.price;
        state.life -= o.lifeCost;
        state.retriesLeft -= o.retryCost;
    }

    private void apply(final OfferOption o) {
        state.gold += o.gold;
        for (Map.Entry<String, Integer> e : o.cards.entrySet()) {
            state.deck.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        if (o.upgrade != null) {
            state.upgrades.computeIfAbsent(o.upgradeCard, k -> new ArrayList<>()).add(o.upgrade);
        }
        if (o.relicId != null) {
            RelicDef r = content.getRelic(o.relicId);
            state.relics.add(r.id);
            if (r.maxLifeBonus != 0) {
                state.maxLife += r.maxLifeBonus;
                state.life = Math.max(1, Math.min(state.maxLife, state.life + Math.max(0, r.maxLifeBonus)));
            }
        }
        if (o.cutCard != null) {
            int have = state.deck.getOrDefault(o.cutCard, 0);
            int cut = content.getConfig().cutCopies <= 0 ? have : Math.min(have, content.getConfig().cutCopies);
            if (have - cut <= 0) {
                state.deck.remove(o.cutCard);
            } else {
                state.deck.put(o.cutCard, have - cut);
            }
        }
    }

    // ---------------------------------------------------------------- offers

    private Offer makeCardRewardOffer() {
        RunConfig cfg = content.getConfig();
        Random rnd = state.nextRandom();
        byte colours = runColours();
        List<CardPoolDef> onColour = new ArrayList<>();
        List<CardPoolDef> offColour = new ArrayList<>();
        Map<String, List<String>> bucketCards = new LinkedHashMap<>();
        for (CardPoolDef p : content.getPools().values()) {
            if (p.bucket && (!p.locked || state.unlockedPools.contains(p.id))) {
                // a set bucket takes its set's cards within the run's colours, so it is always on-colour
                List<String> cards = p.fromSet != null ? FullPool.cards(List.of(p.fromSet), colours, content.getQuarantine()) : p.cards;
                if (cards.isEmpty()) {
                    continue;
                }
                bucketCards.put(p.id, cards);
                if ((colourIdentity(cards) & ~colours) == 0) {
                    onColour.add(p);
                } else {
                    offColour.add(p);
                }
            }
        }
        Offer offer = new Offer(OfferKind.CARD_REWARD, false); // rewards can't be turned down
        if (rnd.nextDouble() < cfg.bucketRewardChance) {
            // buckets in the run's colours, plus now and then one off-colour or mixed-colour bucket to splash
            List<CardPoolDef> offered = new ArrayList<>();
            List<CardPoolDef> splash = new ArrayList<>();
            if (!offColour.isEmpty() && rnd.nextDouble() < cfg.offColourBucketChance) {
                splash.addAll(pick(offColour, 1, rnd));
            }
            offered.addAll(pick(onColour, cfg.bucketRewardChoices - splash.size(), rnd));
            offered.addAll(splash);
            for (CardPoolDef b : offered) {
                OfferOption o = new OfferOption();
                o.label = splash.contains(b) ? b.name + " (splash)" : b.name;
                for (String c : pickAllowingRepeats(bucketCards.get(b.id), cfg.bucketRewardCards, rnd)) {
                    o.cards.merge(c, 1, Integer::sum);
                }
                offer.options.add(o);
            }
            if (!offer.options.isEmpty()) {
                return offer;
            }
        }
        addSingleCardOptions(offer, rnd, colours);
        return offer;
    }

    /** Single-card options: one slot curated, one from the Full Pool, the rest either at even odds, in random order. */
    private void addSingleCardOptions(final Offer offer, final Random rnd, final byte colours) {
        RunConfig cfg = content.getConfig();
        List<Boolean> curatedSlots = new ArrayList<>();
        for (int i = 0; i < cfg.cardRewardChoices; i++) {
            curatedSlots.add(i == 0 || (i > 1 && rnd.nextBoolean()));
        }
        Collections.shuffle(curatedSlots, rnd);
        List<String> curated = rewardPool();
        List<String> full = FullPool.cards(fullPoolSets(), colours, content.getQuarantine());
        List<String> taken = new ArrayList<>();
        for (boolean fromCurated : curatedSlots) {
            List<String> from = new ArrayList<>(fromCurated ? curated : full);
            from.removeAll(taken);
            if (from.isEmpty()) { // that source has run dry: use the other
                fromCurated = !fromCurated;
                from = new ArrayList<>(fromCurated ? curated : full);
                from.removeAll(taken);
            }
            if (from.isEmpty()) {
                break;
            }
            String c = from.get(rnd.nextInt(from.size()));
            taken.add(c);
            OfferOption o = new OfferOption();
            o.source = fromCurated ? "curated" : "full pool";
            o.cards.put(c, cfg.cardRewardCopies);
            List<RunUpgrade> useful = isCreature(c) ? usefulUpgrades(c) : List.of();
            if (!useful.isEmpty() && rnd.nextDouble() < cfg.rewardUpgradeChance) {
                o.upgradeCard = c;
                o.upgrade = useful.get(rnd.nextInt(useful.size()));
            }
            offer.options.add(o);
        }
    }

    private Offer makeBossRelicOffer() {
        Offer offer = new Offer(OfferKind.BOSS_RELIC, true);
        List<String> pool = unownedRelics(false);
        pool.addAll(unownedRelics(true));
        for (String id : pick(pool, content.getConfig().bossRelicChoices, state.nextRandom())) {
            OfferOption o = new OfferOption();
            o.relicId = id;
            o.label = content.getRelic(id).name;
            offer.options.add(o);
        }
        return offer;
    }

    private Offer makeShopOffer() {
        RunConfig cfg = content.getConfig();
        Random rnd = state.nextRandom();
        Offer offer = new Offer(OfferKind.SHOP, true);
        for (String id : pickRelics(cfg.shopRelicChoices, rnd)) {
            OfferOption o = new OfferOption();
            o.relicId = id;
            o.label = content.getRelic(id).name;
            o.price = cfg.shopRelicPrice;
            offer.options.add(o);
        }
        for (String c : pick(rewardPool(), cfg.shopCardChoices, rnd)) {
            OfferOption o = new OfferOption();
            o.cards.put(c, cfg.cardRewardCopies);
            o.price = cfg.shopCardPrice;
            offer.options.add(o);
        }
        OfferOption removal = new OfferOption();
        removal.removal = true;
        removal.label = "Remove a card (every copy)";
        removal.price = cfg.shopRemovalPrice * (state.removalsBought + 1);
        offer.options.add(removal);
        return offer;
    }

    /**
     * The Workshop: artifacts from the Full Pool in the run's colours (each slot Equipment at some odds), the
     * upgrade service and Base supplies.
     */
    private Offer makeWorkshopOffer() {
        RunConfig cfg = content.getConfig();
        Random rnd = state.nextRandom();
        Offer offer = new Offer(OfferKind.WORKSHOP, true);
        List<String> equipment = new ArrayList<>();
        List<String> otherArtifacts = new ArrayList<>();
        for (String c : FullPool.cards(fullPoolSets(), runColours(), content.getQuarantine())) {
            PaperCard pc = StaticData.instance().getCommonCards().getCard(c);
            if (pc == null || !pc.getRules().getType().isArtifact()) {
                continue;
            }
            (pc.getRules().getType().hasSubtype("Equipment") ? equipment : otherArtifacts).add(c);
        }
        Collections.shuffle(equipment, rnd);
        Collections.shuffle(otherArtifacts, rnd);
        for (int i = 0; i < cfg.workshopArtifactChoices && !(equipment.isEmpty() && otherArtifacts.isEmpty()); i++) {
            boolean wantEquipment = rnd.nextDouble() < cfg.workshopEquipmentChance;
            List<String> from = wantEquipment && !equipment.isEmpty() || otherArtifacts.isEmpty() ? equipment : otherArtifacts;
            OfferOption o = new OfferOption();
            o.cards.put(from.remove(0), cfg.cardRewardCopies);
            o.price = cfg.workshopArtifactPrice;
            offer.options.add(o);
        }
        OfferOption upgrade = new OfferOption();
        upgrade.upgradeService = true;
        upgrade.label = "Upgrade a card";
        upgrade.price = cfg.workshopUpgradePrice;
        offer.options.add(upgrade);
        OfferOption supply = new OfferOption();
        supply.supply = cfg.workshopSupplyAmount;
        supply.label = "Stock up your Base: " + cfg.workshopSupplyAmount + " supplies (each puts a +1/+1 counter on"
                + " a creature you control, at sorcery speed; spent supplies don't come back)";
        supply.price = cfg.workshopSupplyPrice;
        offer.options.add(supply);
        return offer;
    }

    /** A campfire: rest, train or purge (train and purge only when there is something to choose). */
    private Offer makeCampfireOffer() {
        Offer offer = new Offer(OfferKind.CAMPFIRE, false);
        OfferOption rest = new OfferOption();
        rest.heal = Math.max(1, (int) Math.round(state.maxLife * content.getConfig().restHealFraction));
        rest.label = "Rest: heal " + rest.heal + " life" + (state.life >= state.maxLife ? " (you're at full life)" : "");
        offer.options.add(rest);
        if (!makeUpgradeOffer().options.isEmpty()) {
            OfferOption train = new OfferOption();
            train.train = true;
            train.label = "Train: upgrade a card (choose 1 of " + content.getConfig().upgradeChoices + ")";
            offer.options.add(train);
        }
        if (!state.deck.isEmpty()) {
            OfferOption purge = new OfferOption();
            purge.purge = true;
            purge.label = "Purge: remove a card (every copy)";
            offer.options.add(purge);
        }
        return offer;
    }

    /** Whether the run qualifies for an event (see {@link EventDef} for each kind's needs). */
    private boolean eligible(final EventDef e) {
        return switch (e.kind) {
            case "relicForLife" -> !unownedRelics(false).isEmpty() && state.life > e.lifeCost;
            case "sellRetry" -> state.retriesLeft >= 1 && e.relic != null && !state.relics.contains(e.relic);
            case "upgrade" -> !makeUpgradeOffer().options.isEmpty();
            default -> true;
        };
    }

    /** An event picked at random (by weight) among those the run qualifies for; null if there is none. */
    private Offer makeEventOffer() {
        Random rnd = state.nextRandom();
        List<EventDef> events = new ArrayList<>();
        double total = 0;
        for (EventDef e : content.getEvents().values()) {
            if (eligible(e)) {
                events.add(e);
                total += e.weight;
            }
        }
        if (events.isEmpty()) {
            return null;
        }
        double roll = rnd.nextDouble() * total;
        EventDef e = events.get(events.size() - 1);
        for (EventDef x : events) {
            roll -= x.weight;
            if (roll < 0) {
                e = x;
                break;
            }
        }
        Offer offer = new Offer(OfferKind.EVENT, true); // even free gold can be refused
        offer.title = e.title;
        offer.text = e.text;
        state.log.add("Event: " + e.title);
        switch (e.kind) {
            case "gold" -> {
                OfferOption o = new OfferOption();
                o.gold = e.amount;
                o.label = e.accept != null ? e.accept : "Take " + e.amount + " gold";
                offer.options.add(o);
            }
            case "upgrade" -> {
                for (OfferOption o : makeUpgradeOffer().options) {
                    o.price = e.price;
                    offer.options.add(o);
                }
            }
            case "card" -> {
                Offer cards = new Offer(OfferKind.CARD_REWARD, true);
                addSingleCardOptions(cards, rnd, runColours());
                offer.options.addAll(cards.options);
            }
            case "relicForLife" -> {
                OfferOption o = new OfferOption();
                o.relicId = pickRelics(1, rnd).get(0);
                o.lifeCost = e.lifeCost;
                o.secret = true;
                o.label = e.accept != null ? e.accept : "Pay " + e.lifeCost + " life for a random relic";
                offer.options.add(o);
            }
            case "sellRetry" -> {
                OfferOption o = new OfferOption();
                o.relicId = e.relic;
                o.retryCost = 1;
                o.secret = true;
                RelicDef r = content.getRelic(e.relic);
                o.label = (e.accept != null ? e.accept : "Hand over a retry") + ": " + r.name + " (" + r.description + ")";
                offer.options.add(o);
            }
            default -> throw new IllegalStateException("Unknown event kind " + e.kind + " in " + e.id);
        }
        return offer;
    }

    private Offer makeUpgradeOffer() {
        Random rnd = state.nextRandom();
        Offer offer = new Offer(OfferKind.UPGRADE, true);
        List<String> creatures = new ArrayList<>();
        creatures.add(state.commander);
        for (String c : state.deck.keySet()) {
            if (isCreature(c)) {
                creatures.add(c);
            }
        }
        if (content.getUpgrades().isEmpty()) {
            return offer;
        }
        creatures.removeIf(c -> usefulUpgrades(c).isEmpty());
        for (String c : pick(creatures, content.getConfig().upgradeChoices, rnd)) {
            List<RunUpgrade> useful = usefulUpgrades(c);
            OfferOption o = new OfferOption();
            o.upgradeCard = c;
            o.upgrade = useful.get(rnd.nextInt(useful.size()));
            offer.options.add(o);
        }
        return offer;
    }

    private Offer makeCutOffer() {
        Offer offer = new Offer(OfferKind.CUT, true);
        for (String c : state.deck.keySet()) {
            OfferOption o = new OfferOption();
            o.cutCard = c;
            offer.options.add(o);
        }
        return offer;
    }

    private List<String> rewardPool() {
        StarterDef starter = content.getStarter(state.starterId);
        List<String> pool = new ArrayList<>(starter.rewardPool == null ? state.deck.keySet() : content.getPool(starter.rewardPool).cards);
        pool.addAll(state.extraRewardCards);
        return pool;
    }

    /** The run's Full Pool sets (older saves have none recorded and use the starting sets). */
    public List<String> fullPoolSets() {
        return state.fullPoolSets.isEmpty() ? content.getConfig().fullPoolSets : state.fullPoolSets;
    }

    /** The run's colours: the colour identity of the commander and every card in the deck. */
    byte runColours() {
        List<String> names = new ArrayList<>(state.deck.keySet());
        names.add(state.commander);
        return colourIdentity(names);
    }

    private static byte colourIdentity(final Iterable<String> names) {
        byte colours = 0;
        for (String name : names) {
            PaperCard pc = name == null ? null : StaticData.instance().getCommonCards().getCard(name);
            if (pc != null) {
                colours |= pc.getRules().getColorIdentity().getColor();
            }
        }
        return colours;
    }

    /** Relics outside a boss's choice: each is a rare one at {@code rareRelicChance}, if any rare one is left. */
    private List<String> pickRelics(final int n, final Random rnd) {
        List<String> normal = unownedRelics(false);
        List<String> rare = unownedRelics(true);
        Collections.shuffle(normal, rnd);
        Collections.shuffle(rare, rnd);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<String> from = !rare.isEmpty() && rnd.nextDouble() < content.getConfig().rareRelicChance ? rare : normal;
            if (from.isEmpty()) {
                break;
            }
            out.add(from.remove(0));
        }
        return out;
    }

    private List<String> unownedRelics(final boolean rare) {
        List<String> out = new ArrayList<>();
        for (RelicDef r : content.getRelics().values()) {
            String id = r.id;
            if (!r.eventOnly && r.rare == rare && !state.relics.contains(id) && (state.relicPool == null || state.relicPool.contains(id))) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The pool's upgrades that would change this card. A keyword-only upgrade is left out when the card
     * already has each of its keywords, printed or from this run's upgrades (Ward stacks, so it always counts).
     */
    List<RunUpgrade> usefulUpgrades(final String name) {
        return usefulUpgrades(name, content.getUpgrades());
    }

    /** As {@link #usefulUpgrades(String)} for any pool; a cost cut also needs generic mana left in the cost to cut. */
    List<RunUpgrade> usefulUpgrades(final String name, final List<RunUpgrade> pool) {
        Set<String> has = new HashSet<>();
        int generic = 0;
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        if (pc != null) {
            for (String k : pc.getRules().getMainPart().getKeywords()) {
                has.add(k);
            }
            generic = Math.max(0, pc.getRules().getManaCost().getGenericCost());
        }
        for (RunUpgrade u : state.upgrades.getOrDefault(name, List.of())) {
            has.addAll(u.keywords);
            generic = Math.max(0, generic + u.costChange);
        }
        List<RunUpgrade> out = new ArrayList<>();
        for (RunUpgrade u : pool) {
            boolean changes = u.power != 0 || u.toughness != 0 || !u.abilities.isEmpty() || u.costChange > 0
                    || (u.costChange < 0 && generic > 0);
            for (String k : u.keywords) {
                changes |= k.startsWith("Ward") || !has.contains(k);
            }
            if (changes) {
                out.add(u);
            }
        }
        return out;
    }

    private static boolean isCreature(final String name) {
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        return pc != null && pc.getRules().getType().isCreature();
    }

    /** Up to n distinct items in random order. */
    static <T> List<T> pick(final List<T> from, final int n, final Random rnd) {
        List<T> copy = new ArrayList<>(new LinkedHashSet<>(from));
        Collections.shuffle(copy, rnd);
        return new ArrayList<>(copy.subList(0, Math.min(n, copy.size())));
    }

    private static List<String> pickAllowingRepeats(final List<String> from, final int n, final Random rnd) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n && !from.isEmpty(); i++) {
            out.add(from.get(rnd.nextInt(from.size())));
        }
        return out;
    }

    // ---------------------------------------------------------------- saving

    public void save(final File file) {
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(state, w);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the run to " + file, e);
        }
    }

    public static Run load(final RoguelikeContent content, final File file) {
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return new Run(content, GSON.fromJson(r, RunState.class));
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the run from " + file, e);
        }
    }
}
