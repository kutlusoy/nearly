package com.kutlusoy.nearly.ui

// Everything that brings the account back. The words are null when this phone does not hold them.
data class AccountBackup(val username: String, val email: String, val words: String?, val npub: String, val nsec: String)
