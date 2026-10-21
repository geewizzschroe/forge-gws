package forge.gamemodes.roguelike;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import forge.LobbyPlayer;
import forge.StaticData;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.player.Player;
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
            s.baseAbilities.addAll(setup.baseAbilities);
        }
        s.map = RunMap.generate(cfg, content, s.nextRandom());
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
            case REST -> {
                int heal = (int) Math.round(state.maxLife * content.getConfig().restHealFraction);
                state.life = Math.min(state.maxLife, state.life + heal);
                state.log.add("Rested to " + state.life + " life");
                completeNode();
            }
            case SHOP -> state.offer = makeShopOffer();
            case UPGRADE -> {
                state.offer = makeUpgradeOffer();
                if (state.offer.options.isEmpty()) {
                    state.offer = null;
                    completeNode();
                }
            }
            case CUT -> state.offer = makeCutOffer();
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
        List<String> script = BaseAssembler.assemble(baseFile == null ? null : content.getBase(baseFile), relics,
                state.baseAbilities);
        return script == null ? null : BaseCard.fromScript(script);
    }

    /** An enemy's Base, or null if it has none. */
    public PaperCard getEncounterBase(final EncounterDef enc) {
        return enc.base == null ? null : BaseCard.fromScript(content.getBase(enc.base));
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
            }
        }
        MapNode node = getActiveNode();
        RunConfig cfg = content.getConfig();
        if (won) {
            state.xpEarned += Math.round((node.type == NodeType.BOSS ? cfg.xpPerBoss : cfg.xpPerFight) * cfg.xpMultiplier);
            state.matchesWon++;
            state.beaten.add(match.encounter().name);
            state.life = Math.max(1, Math.min(endLife, state.maxLife));
            boolean boss = node.type == NodeType.BOSS;
            int gold = boss ? content.getConfig().goldPerBoss : content.getConfig().goldPerEnemy;
            state.gold += gold;
            state.log.add("Beat " + match.encounter().name + " with " + endLife + " life; run life " + state.life
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
            state.log.add("Lost to " + match.encounter().name + "; retrying (" + state.retriesLeft + " retries left)");
        } else {
            state.xpEarned += Math.round(cfg.xpLostMatch * cfg.xpMultiplier);
            state.matchesLost++;
            state.status = RunStatus.LOST;
            state.log.add("Lost to " + match.encounter().name + "; the run is over");
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
                state.floor++;
                state.map = RunMap.generate(content.getConfig(), content, state.nextRandom());
                state.currentNodeId = null;
                state.visited.clear();
                state.log.add("Floor " + state.floor);
            }
        }
    }

    // ---------------------------------------------------------------- choices

    /** Take one option of the open offer (not for shops; use {@link #buy(int)}). */
    public void choose(final int index) {
        Offer offer = requireOffer();
        if (offer.kind == OfferKind.SHOP) {
            throw new IllegalStateException("Use buy() in a shop");
        }
        OfferOption o = offer.options.get(index);
        state.log.add("Chose " + RunText.option(this, o));
        apply(o);
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
        if (offer.kind != OfferKind.SHOP) {
            throw new IllegalStateException("Not in a shop");
        }
        OfferOption o = offer.options.get(index);
        if (o.bought || state.gold < o.price) {
            throw new IllegalStateException("Can't buy " + o + " with " + state.gold + " gold");
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
        boolean matchReward = offer.kind == OfferKind.CARD_REWARD || offer.kind == OfferKind.BOSS_RELIC;
        state.offer = null;
        state.log.add(offer.kind == OfferKind.SHOP ? "Left the shop" : "Skipped the choice");
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

    private void apply(final OfferOption o) {
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
        List<CardPoolDef> buckets = new ArrayList<>();
        for (CardPoolDef p : content.getPools().values()) {
            if (p.bucket && (!p.locked || state.unlockedPools.contains(p.id))) {
                buckets.add(p);
            }
        }
        Offer offer = new Offer(OfferKind.CARD_REWARD, true);
        if (!buckets.isEmpty() && rnd.nextDouble() < cfg.bucketRewardChance) {
            for (CardPoolDef b : pick(buckets, cfg.bucketRewardChoices, rnd)) {
                OfferOption o = new OfferOption();
                o.label = b.name;
                for (String c : pickAllowingRepeats(b.cards, cfg.bucketRewardCards, rnd)) {
                    o.cards.merge(c, 1, Integer::sum);
                }
                offer.options.add(o);
            }
            return offer;
        }
        for (String c : pick(rewardPool(), cfg.cardRewardChoices, rnd)) {
            OfferOption o = new OfferOption();
            o.cards.put(c, cfg.cardRewardCopies);
            if (isCreature(c) && rnd.nextDouble() < cfg.rewardUpgradeChance && !content.getUpgrades().isEmpty()) {
                o.upgradeCard = c;
                o.upgrade = content.getUpgrades().get(rnd.nextInt(content.getUpgrades().size()));
            }
            offer.options.add(o);
        }
        return offer;
    }

    private Offer makeBossRelicOffer() {
        Offer offer = new Offer(OfferKind.BOSS_RELIC, true);
        for (String id : pick(unownedRelics(), content.getConfig().bossRelicChoices, state.nextRandom())) {
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
        for (String id : pick(unownedRelics(), cfg.shopRelicChoices, rnd)) {
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
        for (String c : pick(creatures, content.getConfig().upgradeChoices, rnd)) {
            OfferOption o = new OfferOption();
            o.upgradeCard = c;
            o.upgrade = content.getUpgrades().get(rnd.nextInt(content.getUpgrades().size()));
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

    private List<String> unownedRelics() {
        List<String> out = new ArrayList<>();
        for (String id : content.getRelics().keySet()) {
            if (!state.relics.contains(id) && (state.relicPool == null || state.relicPool.contains(id))) {
                out.add(id);
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
