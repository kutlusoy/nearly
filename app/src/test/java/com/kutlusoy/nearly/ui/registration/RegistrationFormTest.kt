package com.kutlusoy.nearly.ui.registration

import com.kutlusoy.nearly.nostr.BirthDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrationFormTest {
    private val today = BirthDate(2026, 11, 21)
    private val words = "abandon ability able about above"

    @Test
    fun usernameRules() {
        assertNull(Rules.usernameError(""))
        assertEquals(FieldError.UsernameShort, Rules.usernameError("ab"))
        assertEquals(FieldError.UsernameLong, Rules.usernameError("a".repeat(25)))
        assertEquals(FieldError.UsernameCharacters, Rules.usernameError("anna smith"))
        assertEquals(FieldError.UsernameCharacters, Rules.usernameError("_anna"))
        assertEquals(FieldError.UsernameCharacters, Rules.usernameError("anna<script>"))
        assertTrue(Rules.isUsernameValid(" Anna.K-1 "))
        assertTrue(Rules.isUsernameValid("Jürgen"))
        assertTrue(Rules.isUsernameValid("Zeynep_Kaya"))
        assertFalse(Rules.isUsernameValid("ab"))
    }

    @Test
    fun emailMustBeValidAndRepeatedTheSame() {
        assertTrue(Rules.isEmailValid("anna@example.org"))
        assertFalse(Rules.isEmailValid("anna@example"))
        assertFalse(Rules.isEmailValid("anna example.org"))
        assertFalse(Rules.isEmailValid("@example.org"))
        val form = RegistrationForm(step = RegistrationStep.Email, email = "anna@example.org", emailAgain = "anna@exampel.org")
        assertEquals(FieldError.EmailMismatch, form.emailError())
        assertFalse(form.canContinue(today))
        val same = form.copy(emailAgain = " Anna@Example.org ")
        assertNull(same.emailError())
        assertTrue(same.canContinue(today))
        assertEquals(FieldError.EmailInvalid, form.copy(email = "nope").emailError())
        assertNull(RegistrationForm().emailError())
    }

    @Test
    fun wordsNeedTheConfirmationAndNewWordsRemoveIt() {
        val form = RegistrationForm(step = RegistrationStep.Words, words = words)
        assertFalse(form.canContinue(today))
        val confirmed = form.copy(wordsSaved = true)
        assertTrue(confirmed.canContinue(today))
        assertFalse(confirmed.withWords("zoo zebra zero zone youth").wordsSaved)
        assertFalse(form.copy(words = "abandon ability", wordsSaved = true).canContinue(today))
    }

    @Test
    fun birthdateMustBeRealAndAdult() {
        fun form(d: String, m: String, y: String) = RegistrationForm(step = RegistrationStep.Birthdate, day = d, month = m, year = y)
        assertTrue(form("21", "11", "2008").canContinue(today))
        assertFalse(form("22", "11", "2008").canContinue(today))
        assertEquals(FieldError.UnderAge, form("22", "11", "2008").birthdateError(today))
        assertEquals(FieldError.DateInvalid, form("31", "02", "2000").birthdateError(today))
        assertEquals(FieldError.DateInvalid, form("1", "1", "1800").birthdateError(today))
        assertEquals(FieldError.DateInvalid, form("1", "1", "2030").birthdateError(today))
        assertNull(form("", "", "").birthdateError(today))
        assertNull(form("1", "1", "20").birthdateError(today))
        assertEquals(BirthDate(2000, 2, 29), form("29", "2", "2000").birthDate(today))
    }

    @Test
    fun stepsRunInOrderAndOnlyWhenTheStepIsValid() {
        var form = RegistrationForm(username = "anna")
        form = form.next(today)
        assertEquals(RegistrationStep.Email, form.step)
        assertEquals(RegistrationStep.Email, form.next(today).step)
        form = form.copy(email = "a@b.de", emailAgain = "a@b.de").next(today)
        assertEquals(RegistrationStep.Words, form.step)
        assertEquals(RegistrationStep.Words, form.copy(words = words).next(today).step)
        form = form.copy(words = words, wordsSaved = true).next(today)
        assertEquals(RegistrationStep.Birthdate, form.step)
        assertTrue(form.isLastStep)
        assertEquals(form, form.copy(day = "1", month = "1", year = "2000").next(today).copy(day = "", month = "", year = ""))
        assertEquals(4, form.stepNumber)
    }

    @Test
    fun backGoesToThePreviousStepAndLeavesAtTheFirst() {
        assertNull(RegistrationForm().back())
        assertEquals(RegistrationStep.Username, RegistrationForm(step = RegistrationStep.Email).back()!!.step)
        assertEquals(RegistrationStep.Words, RegistrationForm(step = RegistrationStep.Birthdate).back()!!.step)
    }

    @Test
    fun signInNeedsUsernameEmailAndFiveWords() {
        assertFalse(SignInForm().canSubmit)
        assertTrue(SignInForm("anna", "anna@example.org", words).canSubmit)
        assertTrue(SignInForm("anna", "anna@example.org", "Abandon-Ability-Able-About-Above").canSubmit)
        assertFalse(SignInForm("anna", "anna@example.org", "abandon ability able about").canSubmit)
        assertFalse(SignInForm("anna", "anna@example.org", "Summer2026!!!!").canSubmit)
        assertEquals(5, SignInForm(words = words).wordCount())
    }
}
