package com.example.calltranslator.util

object PhoneNumberUtils {
    /**
     * Normalizes a phone number to a consistent format for storage and comparison.
     * Example: "+91 98765-43210" -> "+919876543210"
     * Handles common Indian formats as per user requirements.
     */
    fun normalize(phoneNumber: String): String {
        // Remove all non-digit characters except '+'
        var cleaned = phoneNumber.replace(Regex("[^\\d+]"), "")

        // If it starts with '0', remove it and assume India (+91)
        if (cleaned.startsWith("0") && cleaned.length == 11) {
            cleaned = "+91" + cleaned.substring(1)
        }
        // If it's exactly 10 digits, assume India (+91)
        else if (cleaned.length == 10 && !cleaned.startsWith("+")) {
            cleaned = "+91" + cleaned
        }
        // If it's 12 digits starting with 91, add '+'
        else if (cleaned.length == 12 && cleaned.startsWith("91") && !cleaned.startsWith("+")) {
            cleaned = "+" + cleaned
        }

        return cleaned
    }
}
