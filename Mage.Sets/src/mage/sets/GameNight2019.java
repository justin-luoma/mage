package mage.sets;

import mage.cards.ExpansionSet;
import mage.constants.SetType;

/**
 * @author TheElk801
 */
public final class GameNight2019 extends ExpansionSet {

    private static final GameNight2019 instance = new GameNight2019();

    public static GameNight2019 getInstance() {
        return instance;
    }

    private GameNight2019() {
        super("Game Night 2019", "GN2", ExpansionSet.buildDate(2019, 11, 15), SetType.SUPPLEMENTAL);
        this.hasBasicLands = false; // TODO: change when spoiled
    }
}
