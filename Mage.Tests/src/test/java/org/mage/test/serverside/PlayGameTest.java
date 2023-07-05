package org.mage.test.serverside;

import mage.cards.Card;
import mage.cards.Sets;
import mage.cards.decks.Deck;
import mage.cards.decks.DeckCardLists;
import mage.cards.decks.importer.TxtDeckImporter;
import mage.constants.ColoredManaSymbol;
import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.*;
import mage.game.match.MatchOptions;
import mage.game.match.MatchPlayer;
import mage.game.mulligan.MulliganType;
import mage.player.ai.ComputerPlayer;
import mage.players.Player;
import mage.players.PlayerType;
import mage.util.RandomUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.mage.test.serverside.base.MageTestBase;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author ayratn
 */
public class PlayGameTest extends MageTestBase {

  private static final List<String> colorChoices = new ArrayList<>(Arrays.asList("bu", "bg", "br", "bw", "ug", "ur", "uw", "gr", "gw", "rw", "bur", "buw", "bug", "brg", "brw", "bgw", "wur", "wug", "wrg", "rgu"));
  private static final int DECK_SIZE = 40;

  @Ignore
  @Test
  public void playOneGame() throws GameException, FileNotFoundException, IllegalArgumentException {
    TxtDeckImporter importer = new TxtDeckImporter(false);

    DeckCardLists list1 = importer.importDeck("CounterSurge.txt", false);

    DeckCardLists list2 = importer.importDeck("Black Burn.txt", false);

    Game game = new TwoPlayerDuel(MultiplayerAttackOption.LEFT, RangeOfInfluence.ALL, MulliganType.GAME_DEFAULT.getMulligan(0), 20);

    MatchOptions matchOptions = new MatchOptions("test", "TwoPlayerDuel", true, 2);
    matchOptions.setAttackOption(MultiplayerAttackOption.LEFT);
    matchOptions.setMullgianType(MulliganType.GAME_DEFAULT);
    matchOptions.setLimited(false);

    TwoPlayerMatch match = new TwoPlayerMatch(matchOptions);

    Player computerA = createPlayer("A", PlayerType.COMPUTER_MAD, 10);
    Deck deck = Deck.load(list1, false, false);
    Player computerB = createPlayer("B", PlayerType.COMPUTER_MAD, 10);
    Deck deck2 = Deck.load(list2, false, false);

    computerA.setMatchPlayer(new MatchPlayer(computerB, deck2, match));
    computerB.setMatchPlayer(new MatchPlayer(computerA, deck, match));

//    computerA.setMatchPlayer();


    game.addPlayer(computerA, deck);
    game.loadCards(deck.getCards(), computerA.getId());



    game.addPlayer(computerB, deck2);
    game.loadCards(deck2.getCards(), computerB.getId());

    boolean testMode = true;

    long t1 = System.nanoTime();
    GameOptions options = new GameOptions();
    options.testMode = false;
    game.setGameOptions(options);

    game.start(computerA.getId());
    long t2 = System.nanoTime();

    logger.fatal("Winner: " + game.getWinner());
    logger.fatal("Time: " + (t2 - t1) / 1000000 + " ms");
  }

  private Deck generateRandomDeck() {
    String selectedColors = colorChoices.get(RandomUtil.nextInt(colorChoices.size())).toUpperCase(Locale.ENGLISH);
    List<ColoredManaSymbol> allowedColors = new ArrayList<>();
    logger.info("Building deck with colors: " + selectedColors);
    for (int i = 0; i < selectedColors.length(); i++) {
      char c = selectedColors.charAt(i);
      allowedColors.add(ColoredManaSymbol.lookup(c));
    }
    List<Card> cardPool = Sets.generateRandomCardPool(45, allowedColors);
    return ComputerPlayer.buildDeck(DECK_SIZE, cardPool, allowedColors);
  }
}
