package rs.justin.mage.utils;

import java.util.ArrayList;
import java.util.List;

public class Combinations {

  private Combinations() {
  }

  public static List<List<Integer>> combinations(List<Integer> inputSet, int k) {
    List<List<Integer>> results = new ArrayList<>();
    combinationsInternal(inputSet, k, results, new ArrayList<>(), 0);
    return results;
  }

  private static void combinationsInternal(
      List<Integer> inputSet, int k, List<List<Integer>> results, ArrayList<Integer> accumulator, int index) {
    int needToAccumulate = k - accumulator.size();
    int canAccumulate = inputSet.size() - index;

    if (accumulator.size() == k) {
      results.add(new ArrayList<>(accumulator));
    } else if (needToAccumulate <= canAccumulate) {
      combinationsInternal(inputSet, k, results, accumulator, index + 1);
      accumulator.add(inputSet.get(index));
      combinationsInternal(inputSet, k, results, accumulator, index + 1);
      accumulator.remove(accumulator.size() - 1);
    }
  }
}
