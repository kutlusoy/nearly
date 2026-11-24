package com.kutlusoy.nearly.ui

// The feed does not use the discovery settings. It shows the people you follow and the people close to you.
const val NEARBY_KM = 50.0

fun nearbyPeople(people: List<Person>, following: Set<String>): List<Person> =
    people.filter { it.id !in following && it.distanceKm <= NEARBY_KM }.sortedBy { it.distanceKm }
