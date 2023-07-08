package rs.justin.mage.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import mage.cards.decks.DeckCardLists;
import mage.cards.decks.exporter.XmageDeckExporter;
import mage.cards.decks.importer.TxtDeckImporter;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class DeckConverter {
  private static final Logger logger = LogManager.getLogger(DeckConverter.class);
  @Parameter(
      description = "{The deck to convert}"
  )
  private String deck;

  @Parameter(
      names = {"--output", "-o"},
      description = "The output (defaults to (deck).dck)"
  )
  private String output;

  public static void main(String[] args) {
    DeckConverter deckConverter = new DeckConverter();
    JCommander builder = JCommander.newBuilder()
        .programName("deck-converter")
        .addObject(deckConverter)
        .build();

    builder.parse(args);

    if (deckConverter.deck == null || deckConverter.deck.isEmpty()) {
      builder.usage();
      System.exit(-1);
    }

    if (deckConverter.output == null) {
      deckConverter.output = deckConverter.deck.substring(0, deckConverter.deck.lastIndexOf('.')) + ".dck";
    }

    File file = new File(deckConverter.deck);
    if (!file.exists()) {
      System.err.printf("File: %s does not exist%n", deckConverter.deck);
      System.exit(-1);
    }

    RepositoryUtil.bootstrapLocalDb();
    CardScanner.scan();

    deckConverter.run();
  }

  private void run() {
    TxtDeckImporter importer = new TxtDeckImporter(false);
    DeckCardLists list = importer.importDeck(deck, false);
    XmageDeckExporter exporter = new XmageDeckExporter();

    File file = new File(output);
    try {
      exporter.writeDeck(file, list);
    } catch (IOException e) {
      logger.fatal("Failed to write to: {}", output, e);
    }
  }
}
