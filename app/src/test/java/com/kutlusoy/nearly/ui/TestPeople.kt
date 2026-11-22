package com.kutlusoy.nearly.ui

import androidx.compose.ui.graphics.Color

// People and posts for the tests of the screens, the app itself has no sample data
val SamplePeople = listOf(
    Person("mia", "Mia", 27, 0.8, "Coffee, climbing and bad puns.", Color(0xFFFF6B8B), Color(0xFFFFB199), Gender.Woman, LookingFor.Serious, listOf("Climbing", "Coffee", "Puns"), 0, 4, "Physiotherapist", "Berlin", listOf("Deutsch", "English")),
    Person("jonas", "Jonas", 31, 1.4, "Bakes sourdough on Sundays.", Color(0xFF5B7CFA), Color(0xFF9BD1FF), Gender.Man, LookingFor.Open, listOf("Baking", "Cycling", "Jazz"), 1, 3, "Baker", "Berlin", listOf("Deutsch", "Français")),
    Person("lea", "Lea", 24, 2.1, "Always up for a walk by the river.", Color(0xFF12B5A6), Color(0xFFA8F0C6), Gender.Woman, LookingFor.Friends, listOf("Hiking", "Photography", "Tea"), 2, 2, "Student", "Berlin", listOf("Deutsch", "English", "Español")),
    Person("omar", "Omar", 29, 3.0, "Photographer, slow traveller.", Color(0xFFF59E0B), Color(0xFFFDE68A), Gender.Man, LookingFor.Casual, listOf("Travel", "Film", "Street food"), 0, 5, "Photographer", "Potsdam", listOf("English", "Türkçe", "Deutsch")),
    Person("sofia", "Sofia", 26, 3.6, "Dancing in the kitchen since 1999.", Color(0xFFA855F7), Color(0xFFF0ABFC), Gender.Woman, LookingFor.Fun, listOf("Dancing", "Cooking", "Concerts"), 3, 3, "Chef", "Berlin", listOf("Italiano", "English")),
    Person("nico", "Nico", 33, 4.2, "Board games and long dinners.", Color(0xFF334155), Color(0xFF64748B), Gender.Man, LookingFor.Serious, listOf("Board games", "Wine", "Chess"), 9, 3, "Teacher", "Berlin", listOf("Deutsch", "Ελληνικά")),
    Person("alex", "Alex", 28, 6.5, "Climbs, codes and keeps plants alive.", Color(0xFF0EA5E9), Color(0xFFBAE6FD), Gender.NonBinary, LookingFor.Unsure, listOf("Plants", "Coding", "Bouldering"), 1, 2, "Software developer", "Berlin", listOf("English", "Deutsch")),
    Person("elif", "Elif", 35, 28.0, "Weekend trips and long breakfasts.", Color(0xFFEF4444), Color(0xFFFCA5A5), Gender.Woman, LookingFor.Serious, listOf("Road trips", "Breakfast", "Books"), 4, 4, "Architect", "Brandenburg", listOf("Türkçe", "Deutsch", "English")),
    Person("ben", "Ben", 41, 12.0, "Guitar, grill and good company.", Color(0xFF16A34A), Color(0xFFBBF7D0), Gender.Man, LookingFor.Open, listOf("Music", "Grilling", "Football"), 12, 3, "Sound engineer", "Berlin", listOf("English", "Deutsch")),
    Person("marta", "Marta", 22, 45.0, "Student, swimmer, night owl.", Color(0xFF6366F1), Color(0xFFC7D2FE), Gender.Woman, LookingFor.Fun, listOf("Swimming", "Series", "Karaoke"), 0, 2, "Student", "Frankfurt (Oder)", listOf("Polski", "Deutsch", "English")),
    Person("paul", "Paul", 38, 120.0, "Sailing, slow mornings, strong coffee.", Color(0xFF0D9488), Color(0xFF99F6E4), Gender.Man, LookingFor.Serious, listOf("Sailing", "Coffee", "Podcasts"), 2, 3, "Journalist", "Hamburg", listOf("Deutsch", "English", "Dansk")),
)

private fun person(id: String) = SamplePeople.first { it.id == id }

val SamplePosts = listOf(
    Post("p1", person("mia"), "Sunday climbing, finally reached the top.", 12, 24),
    Post("p2", person("jonas"), "This week's loaf. The crust says it all.", 95, 58),
    Post("p3", person("lea"), "The river was calm this morning.", 260, 31),
    Post("p4", person("omar"), "Golden hour at the old bridge.", 1100, 112),
    Post("p5", person("sofia"), "Fresh pasta night, come hungry.", 150, 47),
    Post("p6", person("elif"), "Breakfast with a view.", 600, 76),
    Post("p7", person("paul"), "Harbour at sunrise.", 40, 63),
    Post("p8", person("marta"), "Early swim before the lectures.", 480, 19),
)
