package forge.gamemodes.roguelike;

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
    public int retries = 1;
    public int cardRewardChoices = 3;
    public int cardRewardCopies = 2;
    public int bucketRewardChoices = 3;
    public int bucketRewardCards = 3;
    // PLACEHOLDER: how often a reward is a choice of buckets instead of single cards (when buckets exist).
    public double bucketRewardChance = 0.3;

    // PLACEHOLDER: D1 floors. 1 means beating the boss wins the run.
    public int floors = 1;
    // PLACEHOLDER: D8 map shape.
    public int mapRows = 7;
    public int mapColumns = 4;
    public int mapPathsFromStart = 3;
    public double weightEnemy = 0.55;
    public double weightShop = 0.12;
    public double weightRest = 0.13;
    public double weightCut = 0.10;
    public double weightUpgrade = 0.10;
    // PLACEHOLDER: D2 currency and prices.
    public int startingGold = 0;
    public int goldPerEnemy = 25;
    public int goldPerBoss = 100;
    public int shopRelicChoices = 3;
    public int shopRelicPrice = 60;
    public int shopCardChoices = 3;
    public int shopCardPrice = 30;
    // PLACEHOLDER: how much a Rest node heals, as a fraction of run max life.
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
    // PLACEHOLDER: relic choices after a boss.
    public int bossRelicChoices = 3;

    // Decided (DESIGN.md 2.3, card scope): starter and enemy cards come from these set codes.
    public List<String> cardScopeSets = List.of("FIN", "FCA", "FIC", "FDN", "FDC");
    // Cards allowed outside those sets because they belong in a particular deck.
    public List<String> cardScopeExceptions = List.of();
}
