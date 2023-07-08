package rs.justin.mage.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombinationsTest {

  @Test
  void generatePermutations() {
    List<Integer> list = Arrays.asList(1, 2, 3, 4);

    List<List<Integer>> actual = Combinations.combinations(list, 2);

    List<List<Integer>> expected = Arrays.asList(
        Arrays.asList(3, 4),
        Arrays.asList(2, 4),
        Arrays.asList(2, 3),
        Arrays.asList(1, 4),
        Arrays.asList(1, 3),
        Arrays.asList(1, 2)
    );

    assertEquals(expected, actual);
  }
}