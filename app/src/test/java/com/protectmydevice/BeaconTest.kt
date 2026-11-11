package com.protectmydevice

import com.protectmydevice.ble.BeaconAdvertiser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BeaconTest {
    @Test fun idIsStableInsideWindowAndRotatesAfter() {
        val t = 1_000_000_000_000L
        assertArrayEquals(BeaconAdvertiser.beaconId("s", t), BeaconAdvertiser.beaconId("s", t + 1000))
        assertFalse(BeaconAdvertiser.beaconId("s", t).contentEquals(BeaconAdvertiser.beaconId("s", t + 16 * 60_000)))
    }

    @Test fun differentSecretsGiveDifferentIds() {
        assertFalse(BeaconAdvertiser.beaconId("a", 5L).contentEquals(BeaconAdvertiser.beaconId("b", 5L)))
    }
}
