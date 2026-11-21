package com.kutlusoy.nearly.ui

import androidx.compose.ui.graphics.Color

// Placeholder people for the swipe screen until profiles come from the relays
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
)

val SamplePeople = listOf(
    Person("mia", "Mia", 27, 0.8, "Coffee, climbing and bad puns.", Color(0xFFFF6B8B), Color(0xFFFFB199), Gender.Woman, LookingFor.Serious, listOf("Climbing", "Coffee", "Puns"), 0),
    Person("jonas", "Jonas", 31, 1.4, "Bakes sourdough on Sundays.", Color(0xFF5B7CFA), Color(0xFF9BD1FF), Gender.Man, LookingFor.Open, listOf("Baking", "Cycling", "Jazz"), 1),
    Person("lea", "Lea", 24, 2.1, "Always up for a walk by the river.", Color(0xFF12B5A6), Color(0xFFA8F0C6), Gender.Woman, LookingFor.Friends, listOf("Hiking", "Photography", "Tea"), 2),
    Person("omar", "Omar", 29, 3.0, "Photographer, slow traveller.", Color(0xFFF59E0B), Color(0xFFFDE68A), Gender.Man, LookingFor.Casual, listOf("Travel", "Film", "Street food"), 0),
    Person("sofia", "Sofia", 26, 3.6, "Dancing in the kitchen since 1999.", Color(0xFFA855F7), Color(0xFFF0ABFC), Gender.Woman, LookingFor.Fun, listOf("Dancing", "Cooking", "Concerts"), 3),
    Person("nico", "Nico", 33, 4.2, "Board games and long dinners.", Color(0xFF334155), Color(0xFF64748B), Gender.Man, LookingFor.Serious, listOf("Board games", "Wine", "Chess"), 9),
    Person("alex", "Alex", 28, 6.5, "Climbs, codes and keeps plants alive.", Color(0xFF0EA5E9), Color(0xFFBAE6FD), Gender.NonBinary, LookingFor.Unsure, listOf("Plants", "Coding", "Bouldering"), 1),
    Person("elif", "Elif", 35, 28.0, "Weekend trips and long breakfasts.", Color(0xFFEF4444), Color(0xFFFCA5A5), Gender.Woman, LookingFor.Serious, listOf("Road trips", "Breakfast", "Books"), 4),
    Person("ben", "Ben", 41, 12.0, "Guitar, grill and good company.", Color(0xFF16A34A), Color(0xFFBBF7D0), Gender.Man, LookingFor.Open, listOf("Music", "Grilling", "Football"), 12),
    Person("marta", "Marta", 22, 45.0, "Student, swimmer, night owl.", Color(0xFF6366F1), Color(0xFFC7D2FE), Gender.Woman, LookingFor.Fun, listOf("Swimming", "Series", "Karaoke"), 0),
)

data class Post(val id: String, val person: Person, val caption: String, val minutesAgo: Int, val likes: Int)

val SamplePosts = listOf(
    Post("p1", SamplePeople[0], "Sunday climbing, finally reached the top.", 12, 24),
    Post("p2", SamplePeople[1], "This week's loaf. The crust says it all.", 95, 58),
    Post("p3", SamplePeople[2], "The river was calm this morning.", 260, 31),
    Post("p4", SamplePeople[3], "Golden hour at the old bridge.", 1100, 112),
)
