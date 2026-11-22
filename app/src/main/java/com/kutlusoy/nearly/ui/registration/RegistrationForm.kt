package com.kutlusoy.nearly.ui.registration

import com.kutlusoy.nearly.nostr.BirthDate
import com.kutlusoy.nearly.nostr.Passphrase

enum class RegistrationStep { Username, Email, Words, Birthdate }

enum class FieldError { UsernameShort, UsernameLong, UsernameCharacters, EmailInvalid, EmailMismatch, DateInvalid, UnderAge }

object Rules {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 24
    private val USERNAME = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*")
    private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}")
    const val MIN_YEAR = 1900

    fun usernameError(value: String): FieldError? {
        val name = value.trim()
        return when {
            name.isEmpty() -> null
            name.length < USERNAME_MIN -> FieldError.UsernameShort
            name.length > USERNAME_MAX -> FieldError.UsernameLong
            !USERNAME.matches(name) -> FieldError.UsernameCharacters
            else -> null
        }
    }

    fun isUsernameValid(value: String) = value.trim().length in USERNAME_MIN..USERNAME_MAX && usernameError(value) == null

    fun isEmailValid(value: String) = EMAIL.matches(value.trim())
}

// All entries of the registration. Pure data and rules, the screens only show it.
data class RegistrationForm(
    val step: RegistrationStep = RegistrationStep.Username,
    val username: String = "",
    val email: String = "",
    val emailAgain: String = "",
    val words: String = "",
    val wordsSaved: Boolean = false,
    val day: String = "",
    val month: String = "",
    val year: String = "",
) {
    fun usernameError() = Rules.usernameError(username)

    fun emailError(): FieldError? = when {
        email.isNotEmpty() && !Rules.isEmailValid(email) -> FieldError.EmailInvalid
        emailAgain.isNotEmpty() && Rules.isEmailValid(email) && AccountKey.same(email, emailAgain).not() -> FieldError.EmailMismatch
        else -> null
    }

    fun birthDate(today: BirthDate): BirthDate? {
        val d = day.trim().toIntOrNull() ?: return null
        val m = month.trim().toIntOrNull() ?: return null
        val y = year.trim().toIntOrNull() ?: return null
        if (y < Rules.MIN_YEAR || y > today.year) return null
        return runCatching { BirthDate(y, m, d) }.getOrNull()
    }

    // Shown only when all three fields are filled
    fun birthdateError(today: BirthDate): FieldError? {
        if (day.isBlank() || month.isBlank() || year.trim().length < 4) return null
        val date = birthDate(today) ?: return FieldError.DateInvalid
        return if (date.isAdultOn(today)) null else FieldError.UnderAge
    }

    fun wordCount(): Int = Passphrase.parse(words)?.size ?: 0

    fun canContinue(today: BirthDate): Boolean = when (step) {
        RegistrationStep.Username -> Rules.isUsernameValid(username)
        RegistrationStep.Email -> Rules.isEmailValid(email) && AccountKey.same(email, emailAgain)
        RegistrationStep.Words -> wordCount() >= Passphrase.MIN_WORDS && wordsSaved
        RegistrationStep.Birthdate -> birthDate(today)?.isAdultOn(today) == true
    }

    val isLastStep: Boolean get() = step == RegistrationStep.Birthdate

    fun next(today: BirthDate): RegistrationForm {
        if (!canContinue(today) || isLastStep) return this
        return copy(step = RegistrationStep.values()[step.ordinal + 1])
    }

    // null when this is the first step: the caller then leaves the registration
    fun back(): RegistrationForm? = if (step.ordinal == 0) null else copy(step = RegistrationStep.values()[step.ordinal - 1])

    // New words invalidate the confirmation
    fun withWords(newWords: String) = copy(words = newWords, wordsSaved = false)

    val stepNumber: Int get() = step.ordinal + 1

    companion object {
        const val STEPS = 4
    }
}

object AccountKey {
    fun same(a: String, b: String) = a.trim().lowercase() == b.trim().lowercase()
}

data class SignInForm(
    val username: String = "",
    val email: String = "",
    val words: String = "",
) {
    fun wordCount(): Int = Passphrase.parse(words)?.size ?: 0

    // A password of an old kind (not the five words) is not supported, the account is derived from the words
    val canSubmit: Boolean get() = Rules.isUsernameValid(username) && Rules.isEmailValid(email) && wordCount() >= Passphrase.MIN_WORDS
}
