package com.kutlusoy.nearly.nostr

import java.nio.file.Files

const val TEST_PHRASE = "abandon ability able about above"

val CHEAP_COST = KeyDerivation.Cost(memoryKiB = 1024, iterations = 2, parallelism = 1)

fun testVault() = KeyVault(FileKeyStore(Files.createTempDirectory("keys").toFile()), logN = 10, cost = CHEAP_COST)

fun testSession(name: String): Session = testVault().signUp(name, "$name@example.org", TEST_PHRASE)
