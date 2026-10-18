package com.scenio.mth.family

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PinDigestTest {
    @Test fun exactlySixAsciiDigitsAreRequired() {
        assertTrue(PinDigest.valid("003621"))
        listOf("12345","1234567","12 456","１２３４５６","secret").forEach { assertFalse(PinDigest.valid(it)) }
    }
    @Test fun saltedDigestsMatchOnlyTheCorrectPinForBothSupportedAlgorithms() {
        for(algorithm in listOf("PBKDF2WithHmacSHA1","PBKDF2WithHmacSHA256")) {
            val salt=PinDigest.salt();val hash=PinDigest.derive("003621",salt,algorithm)
            assertEquals(32,hash.size);assertTrue(PinDigest.matches("003621",salt,hash,algorithm))
            assertFalse(PinDigest.matches("003620",salt,hash,algorithm))
            assertFalse(hash.contentEquals(PinDigest.derive("003621",PinDigest.salt(),algorithm)))
        }
    }
}
