package com.igormaznitsa.jaip_test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ArraySorterTest {

  @Test
  void testFastSort_null() {
    assertNull(ArraySorter.fastSort(null, true));
    assertNull(ArraySorter.fastSort(null, false));
  }

  @Test
  void testFastSort_empty() {
    assertArrayEquals(new int[0], ArraySorter.fastSort(new int[0], true));
    assertArrayEquals(new int[0], ArraySorter.fastSort(new int[0], false));
  }

  @Test
  void testFastSort_single() {
    assertArrayEquals(new int[] {1}, ArraySorter.fastSort(new int[] {1}, true));
    assertArrayEquals(new int[] {1}, ArraySorter.fastSort(new int[] {1}, false));
  }

  @Test
  void testFastSort_multiple() {
    assertArrayEquals(new int[] {-12, -6, 1, 1, 10, 33, 34, 44, 56, 123},
        ArraySorter.fastSort(new int[] {1, 10, -12, 44, 56, 123, 33, 1, 34, -6}, true));
    assertArrayEquals(new int[] {-12, -6, 1, 1, 10, 33, 34, 44, 56, 123},
        ArraySorter.fastSort(new int[] {-6, 34, 1, 33, 123, 56, 44, -12, 10, 1}, false));
  }
}
