package forge.game;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import forge.card.CardRules;
import forge.item.PaperCard;

public class GameRules {
    private final GameType gameType;
    private boolean manaBurn;
    private boolean orderCombatants;
    private int poisonCountersToLose = 10; // is commonly 10, but turns into 15 for 2HG
    private int gamesPerMatch = 3;
    private int gamesToWinMatch = 2;
    private boolean playForAnte = false;
    private boolean matchAnteRarity = false;
    private boolean anteIncludeBasicLands = false;
    private boolean AISideboardingEnabled = false;
    private boolean sideboardForAI = false;
    private boolean allowCheatShuffle = false;
    private final Set<GameType> appliedVariants = EnumSet.noneOf(GameType.class);
    private int simTimeout = 120;
    private boolean conjureBasicLandEachTurn = false;
    private int maxLandsPerPlayer = 0;
    private final Map<String, CardRules> cardOverrides = new HashMap<>();

    // it's a preference, not rule... but I could hardly find a better place for it
    private boolean useGrayText;

    // whether to warn about cards AI can't play well
    private boolean warnAboutAICards = true;

    public GameRules(final GameType type) {
        this.gameType = type;
    }

    public GameType getGameType() {
        return gameType;
    }

    public boolean hasManaBurn() {
        return manaBurn;
    }
    public void setManaBurn(final boolean manaBurn) {
        this.manaBurn = manaBurn;
    }

    public boolean hasOrderCombatants() {
        return orderCombatants;
    }
    public void setOrderCombatants(final boolean ordered) {
        this.orderCombatants = ordered;
    }

    public int getPoisonCountersToLose() {
        return poisonCountersToLose;
    }
    public void setPoisonCountersToLose(final int amount) {
        this.poisonCountersToLose = amount;
    }

    public int getGamesPerMatch() {
        return gamesPerMatch;
    }
    public void setGamesPerMatch(final int gamesPerMatch) {
        this.gamesPerMatch = gamesPerMatch;
        this.gamesToWinMatch = gamesPerMatch / 2 + 1;
    }

    public boolean useAnte() {
        return playForAnte;
    }
    public void setPlayForAnte(final boolean useAnte) {
        this.playForAnte = useAnte;
    }

    public boolean getMatchAnteRarity() {
        return matchAnteRarity;
    }
    public void setMatchAnteRarity(final boolean matchRarity) {
        matchAnteRarity = matchRarity;
    }

    public boolean getAnteIncludeBasicLands() {
        return anteIncludeBasicLands;
    }
    public void setAnteIncludeBasicLands(final boolean includeBasicLands) {
        anteIncludeBasicLands = includeBasicLands;
    }

    public boolean getSideboardForAI() {
        return sideboardForAI;
    }
    public void setSideboardForAI(final boolean sideboard) {
        sideboardForAI = sideboard;
    }

    public boolean getAISideboardingEnabled() {
        return AISideboardingEnabled;
    }
    public void setAISideboardingEnabled(final boolean aiSideboarding) {
        AISideboardingEnabled = aiSideboarding;
    }

    public boolean isAllowCheatShuffle() {
        return allowCheatShuffle;
    }
    public void setAllowCheatShuffle(boolean allowCheatShuffle) {
        this.allowCheatShuffle = allowCheatShuffle;
    }

    public int getGamesToWinMatch() {
        return gamesToWinMatch;
    }

    public void setAppliedVariants(final Set<GameType> appliedVariants) {
        if (appliedVariants != null && !appliedVariants.isEmpty())
            this.appliedVariants.addAll(appliedVariants);
    }

    public void addAppliedVariant(final GameType variant) {
        this.appliedVariants.add(variant);
    }

    public boolean hasAppliedVariant(final GameType variant) {
        return appliedVariants.contains(variant);
    }

    public boolean hasCommander() {
        return appliedVariants.contains(GameType.Commander)
                || appliedVariants.contains(GameType.Oathbreaker)
                || appliedVariants.contains(GameType.TinyLeaders)
                || appliedVariants.contains(GameType.Brawl);
    }

    public boolean useGrayText() {
        return useGrayText;
    }
    public void setUseGrayText(final boolean useGrayText) {
        this.useGrayText = useGrayText;
    }

    public boolean warnAboutAICards() {
        return warnAboutAICards;
    }
    public void setWarnAboutAICards(final boolean warnAboutAICards) {
        this.warnAboutAICards = warnAboutAICards;
    }

    public int getSimTimeout() {
        return this.simTimeout;
    }

    public void setSimTimeout(final int duration) {
        this.simTimeout = duration;
    }

    /** Landless play: at each player's first precombat main phase they conjure a basic land of their choice. */
    public boolean isConjureBasicLandEachTurn() {
        return conjureBasicLandEachTurn;
    }

    public void setConjureBasicLandEachTurn(final boolean value) {
        this.conjureBasicLandEachTurn = value;
    }

    /** Maximum number of lands a player may control; 0 means no limit. Lands can't enter beyond it. */
    public int getMaxLandsPerPlayer() {
        return maxLandsPerPlayer;
    }

    public void setMaxLandsPerPlayer(final int value) {
        this.maxLandsPerPlayer = value;
    }

    /**
     * Use these rules instead of the printed card's for every card of that name in the decks of this
     * match (for example a landless version of a ramp spell). The card keeps its set, so its art stays.
     */
    public void addCardOverride(final CardRules rules) {
        cardOverrides.put(rules.getName(), rules);
    }
    /** The overriding version of this card, or the card itself if it has no override. */
    public PaperCard applyCardOverride(final PaperCard card) {
        CardRules rules = cardOverrides.get(card.getName());
        if (rules == null) {
            return card;
        }
        return new PaperCard(rules, card.getEdition(), card.getRarity());
    }
}
