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
import mage.game.*;
import mage.game.match.Match;
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
import rs.justin.mage.utils.Combinations;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
      description = "Path to config.xml, default: config/config.xml (same as server config)",
      arity = 1
  )
  private String config;

  @Parameter(
      names = {"--bootstrap", "-b"},
      description = "bootstrap the card repository (default = true)",
      arity = 1
  )
  private boolean bootstrap = true;

  @Parameter(
      names = {"--matches", "-m"},
      description = "Number of matches to simulate",
      arity = 1
  )
  private int matches = 1;

  @Parameter(names = {"--help", "-h"}, help = true)
  private boolean help;

  private int exit = 0;

  private Random random;

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

    main.random = new Random();

    main.run();
  }

  private void run() {
    switch (mode.toLowerCase()) {
      case "duel":
        init();
        duel();
        break;
      case "commander":
        init();
        commander();
        break;
    }

    cleanup();
    System.exit(exit);
  }

  private void init() {
    String configPath = Paths.get("config", "config.xml").toString();

    if (config != null) {
      configPath = Paths.get(config).toString();
    }

    try {
      if (bootstrap) {
        RepositoryUtil.bootstrapLocalDb();
      }
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
  }

  private void duel() {
    if (decks == null || decks.size() != 2) {
      logger.fatal("Wrong number of decks, expected 2");
      exit = -1;
      return;
    }

    Game game = new TwoPlayerDuel(MultiplayerAttackOption.LEFT, RangeOfInfluence.ALL, MulliganType.GAME_DEFAULT.getMulligan(0), 20);

    MatchOptions matchOptions = new MatchOptions("TwoPlayerDuelSimulation", "TwoPlayerDuel", false, 2);
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

      runGame(game, player1);
    } catch (RuntimeException e) {
      exit = -1;
      // error already handled
    }
  }

  private void commander() {
    if (decks == null || decks.size() < 2) {
      logger.fatal("Wrong number of decks, expected at least 2");
      exit = -1;
      return;
    }
    Game game = new CommanderFreeForAll(MultiplayerAttackOption.MULTIPLE, RangeOfInfluence.ALL, MulliganType.GAME_DEFAULT.getMulligan(0), 40);

    MatchOptions matchOptions = new MatchOptions("CommanderSimulation", "Commander", true, decks.size());
    matchOptions.setLimited(false);

    Match match = new CommanderFreeForAllMatch(matchOptions);

    try {
      List<Deck> decklists = new ArrayList<>(decks.size());
      List<Player> players = new ArrayList<>(decks.size());

      List<Integer> playerIndexes = new ArrayList<>(decks.size());

      for (int i = 0; i < decks.size(); i++) {
        String deck = decks.get(i);
        Deck d = loadDeck(deck);
        decklists.add(d);
        players.add(PlayerFactory.instance.createPlayer(PlayerType.COMPUTER_MAD, String.valueOf(i + 1), RangeOfInfluence.ALL, 10).get());
      }

      List<List<Integer>> matchPlayers = Combinations.combinations(playerIndexes, 2);

      for (List<Integer> matches : matchPlayers) {
        int player1Index = matches.get(0);
        int player2Index = matches.get(1);
        Player player1 = players.get(player1Index);
        Player player2 = players.get(player2Index);
        Deck deck1 = decklists.get(player1Index);
        Deck deck2 = decklists.get(player2Index);

        player1.setMatchPlayer(new MatchPlayer(player2, deck2, match));
        player2.setMatchPlayer(new MatchPlayer(player1, deck1, match));
      }

      for (int i = 0; i < players.size(); i++) {
        game.addPlayer(players.get(i), decklists.get(i));
        game.loadCards(decklists.get(i).getCards(), players.get(i).getId());
        match.addPlayer(players.get(i), decklists.get(i));
      }

      GameOptions options = new GameOptions();
      options.testMode = false;

      game.setGameOptions(options);

      runGame(game, players.get(random.nextInt(decks.size())));
    } catch (RuntimeException e) {
      logger.fatal(e);
      exit = -1;
      // error already handled
    }
  }

  private void runGame(Game game, Player startingPlayer) {
    int wins = 0;
    int loses = 0;
    int draws = 0;

    for (int i = 0; i < matches; i++) {
      Game tmp = game.copy();
      long start = System.nanoTime();
      tmp.start(startingPlayer.getId());
      long stop = System.nanoTime();

      logger.info("Time: {} ms", (stop - start) / 1000000);

      String winner = tmp.getWinner();
      System.out.println(winner);

      switch (winner.contains("draw") ? 2 : winner.contains("1") ? 0 : 1) {
        case 0:
          wins += 1;
          break;
        case 1:
          loses += 1;
          break;
        case 2:
          draws += 1;
          break;
      }

      if (wins > matches - i) {
        System.out.println("Player 1 wins");
        exit = 0;
        return;
      }
    }

    if (loses > wins && loses > draws) {
      System.out.println("Player 1 loses");
      exit = 1;
    } else if (wins > loses && wins > draws) {
      System.out.println("Player 1 wins");
      exit = 0;
    } else if (draws > wins && draws > loses) {
      System.out.println("Draw");
      exit = 2;
    }
  }

  private Deck loadDeck(String file) {
    File f = new File(file);
    if (!f.exists()) {
      logger.fatal("Deck file {} doesn't exist", file);
      throw new RuntimeException("failed to load deck");
    }
    try {
      TxtDeckImporter importer = new TxtDeckImporter(true);
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
}