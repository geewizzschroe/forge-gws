package forge.game.card.perpetual;

import java.util.Map;

import forge.game.card.Card;

/** SVars a perpetual change brings with it (for the abilities it adds), kept through zone changes. */
public record PerpetualSVars(long timestamp, Map<String, String> svars) implements PerpetualInterface {
    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void applyEffect(Card c) {
        for (Map.Entry<String, String> e : svars.entrySet()) {
            c.setSVar(e.getKey(), e.getValue());
        }
    }
}
