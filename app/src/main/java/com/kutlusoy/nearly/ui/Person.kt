package com.kutlusoy.nearly.ui

import androidx.compose.ui.graphics.Color

// A person in the swipe deck and in the feed, made from a discovery card
data class Person(
    val id: String,
    val name: String,
    val age: Int,
    val distanceKm: Double,
    val bio: String,
    val from: Color,
    val to: Color,
    val gender: Gender,
    val lookingFor: LookingFor,
    val interests: List<String>,
    val activeDaysAgo: Int,
    val photos: Int = 3,
    val job: String? = null,
    val city: String? = null,
    val languages: List<String> = emptyList(),
    val key: String? = null,
    val genderKnown: Boolean = true,
    val photoRefs: List<com.kutlusoy.nearly.nostr.PhotoRef> = emptyList(),
    val extras: com.kutlusoy.nearly.nostr.CardExtras = com.kutlusoy.nearly.nostr.CardExtras(),
)

// The picture of one photo slot, until photos are loaded every slot has its own gradient
fun Person.photoColors(index: Int): Pair<Color, Color> = when (index % 4) {
    0 -> from to to
    1 -> to to from
    2 -> androidx.compose.ui.graphics.lerp(from, Color.Black, 0.25f) to androidx.compose.ui.graphics.lerp(to, Color.Black, 0.15f)
    else -> androidx.compose.ui.graphics.lerp(to, Color.White, 0.2f) to androidx.compose.ui.graphics.lerp(from, Color.White, 0.2f)
}

data class Post(val id: String, val person: Person, val caption: String, val minutesAgo: Int, val likes: Int)
