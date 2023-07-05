package rs.justin.mage.simulation;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import mage.cards.decks.Deck;
import mage.cards.decks.DeckCardLists;
import mage.cards.decks.importer.TxtDeckImporter;
import mage.cards.repository.CardRepository;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.GameOptions;
import mage.game.TwoPlayerDuel;
import mage.game.TwoPlayerMatch;
import mage.game.match.MatchOptions;
import mage.game.match.MatchPlayer;
import mage.game.match.MatchType;
import mage.game.mulligan.MulliganType;
import mage.players.Player;
import mage.players.PlayerType;
import mage.server.game.GameFactory;
import mage.server.game.PlayerFactory;
import mage.server.util.ConfigFactory;
import mage.server.util.ConfigWrapper;
import mage.server.util.PluginClassLoader;
import mage.server.util.config.GamePlugin;
import mage.server.util.config.Plugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

@Parameters(separators = "=, ")
public class Main {

  private static final Logger logger = LogManager.getLogger(Main.class);

  private static final PluginClassLoader classLoader = new PluginClassLoader();

  @Parameter(description = "mode")
  private String mode;

  @Parameter(
      names = {"--deck", "-d"},
      description = "Path to deck list file, can be specified multiple times",
      variableArity = true
  )
  private List<String> decks;

  @Parameter(
      names = {"--config", "-c"},
      description = "Path to config.xml, default: config/config.xml (same as server config)"
  )
  private String config;

  @Parameter(names = {"--help", "-h"}, help = true)
  private boolean help;

  private int exit = 0;

  public static void main(String[] args) {
    Main main = new Main();
    JCommander builder = JCommander.newBuilder()
        .programName("simulate")
        .addObject(main)
        .build();

    builder.parse(args);

    if (main.getMode() == null || main.getMode().isEmpty()) {
      builder.usage();
      System.exit(0);
    }

    String configPath = Paths.get("config", "config.xml").toString();

    if (main.config != null) {
      configPath = Paths.get(main.config).toString();
    }

    try {
      RepositoryUtil.bootstrapLocalDb();
      CardScanner.scan();

      logger.trace("Reading config file: {}", configPath);
      ConfigWrapper config = new ConfigWrapper(ConfigFactory.loadFromFile(configPath));

      for (GamePlugin plugin : config.getGameTypes()) {
        logger.trace("Loading plugin: {}", plugin.getClassName());
        classLoader.addURL(new File("plugins", plugin.getJar()).toURI().toURL());
        MatchType match = (MatchType) Class.forName(plugin.getTypeName(), true, classLoader).getConstructor().newInstance();
        GameFactory.instance.addGameType(plugin.getName(), match, Class.forName(plugin.getClassName()));
      }

      for (Plugin plugin : config.getPlayerTypes()) {
        logger.trace("Loading plugin: {}", plugin.getClassName());
        classLoader.addURL(new File("plugins", plugin.getJar()).toURI().toURL());
        PlayerFactory.instance.addPlayerType(plugin.getName(), Class.forName(plugin.getClassName(), true, classLoader));
      }
    } catch (Exception e) {
      logger.fatal("Failed to load config file: {}", configPath);
      System.exit(-1);
    }

    main.run();
  }

  private void run() {
    System.out.printf("%s%n%s%n", mode, decks);

    switch (mode.toLowerCase()) {
      case "duel":
        duel();
        break;
    }

    cleanup();
    System.exit(exit);
  }

  private void duel() {
    if (decks == null || decks.size() != 2) {
      logger.fatal("Wrong number of decks, expected 2");
      exit = -1;
      return;
    }

    Game game = new TwoPlayerDuel(MultiplayerAttackOption.LEFT, RangeOfInfluence.ALL, MulliganType.GAME_DEFAULT.getMulligan(0), 20);

    MatchOptions matchOptions = new MatchOptions("TwoPlayerDuelSimulation", "TwoPlayerDuel", true, 2);
    matchOptions.setAttackOption(MultiplayerAttackOption.LEFT);
    matchOptions.setMullgianType(MulliganType.GAME_DEFAULT);
    matchOptions.setLimited(false);

    TwoPlayerMatch match = new TwoPlayerMatch(matchOptions);

    try {
      Deck deck1 = loadDeck(decks.get(0));
      Deck deck2 = loadDeck(decks.get(1));
      Player player1 = PlayerFactory.instance.createPlayer(PlayerType.COMPUTER_MAD, "1", RangeOfInfluence.ALL, 10).get();
      Player player2 = PlayerFactory.instance.createPlayer(PlayerType.COMPUTER_MAD, "2", RangeOfInfluence.ALL, 10).get();

      player1.setMatchPlayer(new MatchPlayer(player2, deck2, match));
      player2.setMatchPlayer(new MatchPlayer(player1, deck1, match));

      game.addPlayer(player1, deck1);
      game.loadCards(deck1.getCards(), player1.getId());

      game.addPlayer(player2, deck2);
      game.loadCards(deck2.getCards(), player2.getId());

      GameOptions options = new GameOptions();
      options.testMode = false;

      game.setGameOptions(options);

      long start = System.nanoTime();
      game.start(player1.getId());
      long stop = System.nanoTime();

      logger.info("Time: {} ms", (stop - start) / 1000000);

      System.out.println(game.getWinner());

      exit = game.getWinner().contains("1") ? 0 : 1;
    } catch (RuntimeException e) {
      logger.error(e);
      // error already handled
    }

//    List<Tuple<Player, Deck>> players = new ArrayList<>(2);
//
//    for (String file : decks) {
//      Deck deck;
//      try {
//        DeckCardLists list = importer.importDeck(file, false);
//        deck = Deck.load(list, true, false);
//      } catch (Exception e) {
//        logger.fatal("Failed to load deck: {} error: {}", file, e.getMessage(), e);
//        return;
//      }
//      try {
//        Player player = PlayerFactory.instance.createPlayer(PlayerType.COMPUTER_MAD, "1", RangeOfInfluence.ALL, 10)
//            .orElseThrow(() -> new RuntimeException("failed to create player"));
//        players.add(new Tuple<>(player, deck));
//      } catch (RuntimeException e) {
//        logger.fatal("Failed to create player");
//        return;
//      }
//    }



  }

  private Deck loadDeck(String file) {
    try {
      TxtDeckImporter importer = new TxtDeckImporter(false);
      DeckCardLists list = importer.importDeck(file, false);
      return Deck.load(list, false, false);
    } catch (Exception e) {
      logger.fatal("failed to load deck: {}, error: {}", file, e.getMessage());
      throw new RuntimeException("failed to load deck");
    }
  }

  public String getMode() {
    return mode;
  }

  public List<String> getDecks() {
    return decks;
  }

  private void cleanup() {
    CardRepository.instance.closeDB();
  }

  static class Tuple<A, B> {
    public final A a;
    public final B b;

    public Tuple(A a, B b) {
      this.a = a;
      this.b = b;
    }
  }
}