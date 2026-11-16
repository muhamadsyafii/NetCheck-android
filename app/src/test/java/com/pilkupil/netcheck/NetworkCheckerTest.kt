package com.pilkupil.netcheck

import com.pilkupil.netcheck.network.NetworkChecker
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkCheckerTest {
  @Test
  fun calculatesMegabitsPerSecondUsingDecimalUnits() {
    val result = NetworkChecker.calculateMbps(bytes = 10_000_000, durationSeconds = 2.0)

    assertEquals(40.0, result, 0.001)
  }
}
