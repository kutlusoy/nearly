package com.kutlusoy.nearly.nostr

enum class PasswordRule { MinLength, Lowercase, Uppercase, Digit, Special, NoUsername }

data class PasswordCheck(val met: Set<PasswordRule>, val strength: Int) {
    val valid: Boolean get() = met.containsAll(PasswordRule.values().toList())
    fun isMet(rule: PasswordRule) = rule in met
}

// Live feedback while the user types: which rules are met and a strength from 0 (empty) to 4
object PasswordPolicy {
    const val MIN_LENGTH = 12

    fun evaluate(password: String, username: String = ""): PasswordCheck {
        val met = mutableSetOf<PasswordRule>()
        if (password.codePointCount(0, password.length) >= MIN_LENGTH) met.add(PasswordRule.MinLength)
        if (password.any { it.isLowerCase() }) met.add(PasswordRule.Lowercase)
        if (password.any { it.isUpperCase() }) met.add(PasswordRule.Uppercase)
        if (password.any { it.isDigit() }) met.add(PasswordRule.Digit)
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) met.add(PasswordRule.Special)
        val name = username.trim()
        if (name.length < 3 || !password.contains(name, ignoreCase = true)) met.add(PasswordRule.NoUsername)
        val strength = when {
            password.isEmpty() -> 0
            met.size < 4 -> 1
            met.size < 6 -> 2
            password.length < 16 -> 3
            else -> 4
        }
        return PasswordCheck(met, strength)
    }
}
