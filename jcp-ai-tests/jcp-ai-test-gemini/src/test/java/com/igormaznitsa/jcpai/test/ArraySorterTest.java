package com.igormaznitsa.jcpai.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ArraySorterTest {

  @Test
  void testAdd666() {
    assertEquals(12, ArraySorter.add666(5, 7));
  }

  @Test
  void testFastSort_null() {
    assertNull(ArraySorter.fastSort(null, true));
    assertNull(ArraySorter.fastSort(null, false));
  }

  @Test
  void testFastSort_empty() {
    final int[] array = new int[0];
    assertSame(array, ArraySorter.fastSort(array, true));
    assertSame(array, ArraySorter.fastSort(array, false));
  }

  @Test
  void testFastSort_single() {
    final int[] array = new int[] {1};
    assertSame(array, ArraySorter.fastSort(array, true));
    assertSame(array, ArraySorter.fastSort(array, false));
  }

  @Test
  void testFastSort_multiple() {
    assertArrayEquals(new int[] {-12, -6, 1, 1, 10, 33, 34, 44, 56, 123},
        ArraySorter.fastSort(new int[] {1, 10, -12, 44, 56, 123, 33, 1, 34, -6}, true));
    assertArrayEquals(new int[] {123, 56, 44, 34, 33, 10, 1, 1, -6, -12},
        ArraySorter.fastSort(new int[] {-6, 34, 1, 33, 123, 56, 44, -12, 10, 1}, false));
  }
}
