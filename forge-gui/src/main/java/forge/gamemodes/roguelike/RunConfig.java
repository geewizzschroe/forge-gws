package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * Tunable numbers for the run mode, loaded from {@code config.json} in the content folder.
 *
 * Values marked PLACEHOLDER stand in for design questions that are still open (see DESIGN.md);
 * they exist so the run can be built and tested, not as decisions.
 */
public class RunConfig {
    // Decided (DESIGN.md 2.2, 2.5, 2.5.1, 2.1)
    public int startingMaxLife = 20;
    public int inMatchLifeMultiplier = 2;
    public int landCap = 10;
    // Decided (DESIGN.md 2.5.1): nonbasic lands the per-turn land choice may offer, when both their colours are
    // in the deck: Final Fantasy's two-colour Towns (they enter tapped).
    public List<String> landPickerExtras = new ArrayList<>();
    public int retries = 1;
    public int cardRewardChoices = 3;
    public int cardRewardCopies = 2;
    public int bucketRewardChoices = 3;
    public int bucketRewardCards = 3;
    // PLACEHOLDER: how often a reward is a choice of buckets instead of single cards (when buckets exist).
    public double bucketRewardChance = 0.3;
    // PLACEHOLDER: chance that a bucket offer includes one bucket outside the run's colours (a splash).
    // Other buckets offered are always within the run's colours.
    public double offColourBucketChance = 0.25;
    // Decided (DESIGN.md 2.5): the sets of the Full Pool, the uncurated source of reward slots, at the start.
    public List<String> fullPoolSets = List.of("FIN", "FDN");

    // Decided 2026-10-23: two floors. Beating a floor's boss gives its relic choice, a full heal and a choice of
    // three rare upgrades for the commander, then the next floor; beating the last boss wins the run.
    public int floors = 2;
    // What changes per floor (FloorRule). PLACEHOLDER: every number in them.
    public List<FloorRule> floorRules = List.of();
    // Decided (proposal 1.1): touchstones every path goes through (an Upgrade after the first fight, a Shop, a
    // campfire before the boss) with branching stretches between them. See RunMap for the entries.
    // PLACEHOLDER: the stretch lengths and the number of lanes.
    // Each entry may end with the enemy tiers its fights draw from (T1, T2-3 ...).
    public List<String> mapLayout = List.of("FIGHTS T1", "UPGRADE", "STRETCH 2 T1-2", "SHOP", "STRETCH 3 T2-3", "REST", "BOSS");
    public int mapLanes = 3;
    // Node weights inside stretches. PLACEHOLDER: the numbers (shop and rest are lower since touchstones guarantee them).
    public double weightEnemy = 0.55;
    public double weightShop = 0.05;
    public double weightRest = 0.12;
    // Decided 2026-10-22: no more standalone Cut and Upgrade nodes; their jobs went to the Workshop, the shop and events.
    // PLACEHOLDER: the Event node's weight.
    public double weightEvent = 0.16;
    // Decided (proposal 1.4): the Workshop appears in the map's stretches, never guaranteed. PLACEHOLDER: the weight.
    public double weightWorkshop = 0.12;
    // PLACEHOLDER: D2 currency and prices.
    public int startingGold = 0;
    public int goldPerEnemy = 25;
    public int goldPerBoss = 100;
    public int shopRelicChoices = 3;
    public int shopRelicPrice = 60;
    public int shopCardChoices = 3;
    public int shopCardPrice = 30;
    // Decided (DESIGN.md 2.5): the shop's card removal costs this, times one more than the times bought before
    // (75, 150, 225 ...). PLACEHOLDER: the amount.
    public int shopRemovalPrice = 75;
    // Decided (proposal 1.4): the Workshop sells artifacts (2 copies each), upgrades for gold (repeatable)
    // and Base supplies: a Base ability with uses that last the whole run. PLACEHOLDER: every number here.
    public int workshopArtifactChoices = 3;
    public int workshopArtifactPrice = 30;
    // Chance that each artifact slot is Equipment (when the run's colours have any).
    public double workshopEquipmentChance = 0.67;
    public int workshopUpgradePrice = 40;
    public int workshopSupplyPrice = 50;
    public int workshopSupplyAmount = 3;
    // The ability Base supplies give; it spends one SUPPLY counter from the Base each time.
    public List<String> workshopSupplyScript = List.of(
            "A:AB$ PutCounter | Cost$ SubCounter<1/SUPPLY> | ActivationZone$ Command | SorcerySpeed$ True"
                    + " | ValidTgts$ Creature.YouCtrl | TgtPrompt$ Select target creature you control | CounterType$ P1P1"
                    + " | CounterNum$ 1 | SpellDescription$ Supplies: put a +1/+1 counter on target creature you control."
                    + " Activate only as a sorcery. (Spent supplies are gone for the rest of the run.)");
    // Decided (proposal 1.2): every Rest node is a campfire: rest (heal), train (upgrade a card) or purge (remove a card).
    // PLACEHOLDER: how much resting heals, as a fraction of run max life.
    public double restHealFraction = 0.3;
    // PLACEHOLDER: how many copies a Cut node removes from the chosen card name (0 = all of them).
    public int cutCopies = 0;
    // PLACEHOLDER: D4 upgrades. Choices at an Upgrade node, and the chance a card reward comes upgraded.
    public int upgradeChoices = 3;
    public double rewardUpgradeChance = 0.25;
    // Experience (D9): per match won, scaled by xpMultiplier (PLACEHOLDER until run difficulty exists).
    public int xpPerFight = 30;
    public int xpPerBoss = 150;
    public int xpRunWon = 500;
    // PLACEHOLDER: experience for a lost match.
    public int xpLostMatch = 0;
    public double xpMultiplier = 1.0;
    // Decided 2026-10-23: rare relics are offered by bosses; elsewhere each relic slot is rare with this chance.
    // PLACEHOLDER: about 1 in 30. Meant to become a level-up perk later.
    public double rareRelicChance = 1.0 / 30;
    // PLACEHOLDER: relic choices after a boss.
    public int bossRelicChoices = 3;

    // Decided (DESIGN.md 2.3, card scope): starter and enemy cards come from these set codes.
    public List<String> cardScopeSets = List.of("FIN", "FCA", "FIC", "RFIN", "FDN", "FDC", "J25", "PFDN");
    // Cards allowed outside those sets because they belong in a particular deck.
    public List<String> cardScopeExceptions = List.of();
}
