package com.kutlusoy.nearly.ui

import androidx.compose.ui.graphics.Color

// Placeholder people for the swipe screen until profiles come from the relays
data class Person(val id: String, val name: String, val age: Int, val distanceKm: Double, val bio: String, val from: Color, val to: Color)

val SamplePeople = listOf(
    Person("mia", "Mia", 27, 0.8, "Coffee, climbing and bad puns.", Color(0xFFFF6B8B), Color(0xFFFFB199)),
    Person("jonas", "Jonas", 31, 1.4, "Bakes sourdough on Sundays.", Color(0xFF5B7CFA), Color(0xFF9BD1FF)),
    Person("lea", "Lea", 24, 2.1, "Always up for a walk by the river.", Color(0xFF12B5A6), Color(0xFFA8F0C6)),
    Person("omar", "Omar", 29, 3.0, "Photographer, slow traveller.", Color(0xFFF59E0B), Color(0xFFFDE68A)),
    Person("sofia", "Sofia", 26, 3.6, "Dancing in the kitchen since 1999.", Color(0xFFA855F7), Color(0xFFF0ABFC)),
    Person("nico", "Nico", 33, 4.2, "Board games and long dinners.", Color(0xFF334155), Color(0xFF64748B)),
)

data class Post(val id: String, val person: Person, val caption: String, val minutesAgo: Int, val likes: Int)

val SamplePosts = listOf(
    Post("p1", SamplePeople[0], "Sunday climbing, finally reached the top.", 12, 24),
    Post("p2", SamplePeople[1], "This week's loaf. The crust says it all.", 95, 58),
    Post("p3", SamplePeople[2], "The river was calm this morning.", 260, 31),
    Post("p4", SamplePeople[3], "Golden hour at the old bridge.", 1100, 112),
)
