package forge.gamemodes.roguelike;

/** What an open choice is for. */
public enum OfferKind {
    /** After an enemy: pick one card (several copies) or one bucket of cards. */
    CARD_REWARD,
    /** After a boss: pick one relic. */
    BOSS_RELIC,
    /** Shop: buy any number of options, then leave. */
    SHOP,
    /** Upgrade node: pick one (card, upgrade) pair. */
    UPGRADE,
    /** Cut node: pick one card name to remove copies of. */
    CUT,
    /** Workshop: buy artifacts, upgrades and Base supplies, any number of times while gold lasts, then leave. */
    WORKSHOP,
    /** Event node: take one option (some cost gold, life or a retry) or, where allowed, walk away. */
    EVENT,
    /** Rest node (a campfire): rest, or train or purge, which open their own choice. */
    CAMPFIRE;

    /** A place to buy things with gold (bought with {@code Run.buy}, left with {@code Run.skip}). */
    public boolean isStore() {
        return this == SHOP || this == WORKSHOP;
    }
}
