package forge.ai.ability;

import java.util.ArrayList;
import java.util.List;

import forge.ai.AiBasicLandChoice;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/** Conjuring: always played; a choice between basic lands goes to the colour the AI needs most. */
public class MakeCardAi extends AlwaysPlayAi {

    @Override
    public String chooseCardName(Player ai, SpellAbility sa, List<ICardFace> faces) {
        List<String> names = new ArrayList<>();
        for (ICardFace face : faces) {
            names.add(face.getName());
        }
        if (!names.isEmpty() && MagicColor.Constant.BASIC_LANDS.containsAll(names)) {
            return AiBasicLandChoice.choose(ai, names);
        }
        return super.chooseCardName(ai, sa, faces);
    }
}
