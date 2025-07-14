package com.igormaznitsa.jaip_test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ArraySorterTest {

  @Test
  void testFastSort_null() {
    assertNull(ArraySorter.fastSort(null));
  }

  @Test
  void testFastSort_empty() {
    assertArrayEquals(new int[0], ArraySorter.fastSort(new int[0]));
  }

  @Test
  void testFastSort_single() {
    assertArrayEquals(new int[] {1}, ArraySorter.fastSort(new int[] {1}));
  }

  @Test
  void testFastSort_multiple() {
    assertArrayEquals(new int[] {-12, -6, 1, 1, 10, 33, 34, 44, 56, 123},
        ArraySorter.fastSort(new int[] {1, 10, -12, 44, 56, 123, 33, 1, 34, -6}));
  }
}
