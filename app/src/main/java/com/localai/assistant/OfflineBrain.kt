package com.localai.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, fully offline intent/utility engine.
 * This is not a generative language model; it handles a limited set of local intents.
 */
object OfflineBrain {
    fun respond(message: String): String {
        val text = message.trim()
        val lower = text.lowercase(Locale.ROOT)

        if (lower.isEmpty()) return "Type a question or try: help, time, date, or calculate 12 * (4 + 3)."
        if (matchesAny(lower, "help", "what can you do", "commands", "features")) {
            return "I can work offline for a few built-in tasks:\n\n• Calculate arithmetic: calculate 12 * (4 + 3)\n• Tell the current date: date\n• Tell the current time: time\n• Explain privacy and this app's current limits\n\nThis is the first offline command engine, not yet a full language model. A compact model can be added separately after checking storage and RAM."
        }
        if (lower == "time" || lower.contains("what time is it") || lower.contains("current time")) {
            return "Your device's current time is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        }
        if (lower == "date" || lower.contains("what is the date") || lower.contains("today's date") || lower.contains("what day is it")) {
            return "Your device's date is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
        }
        if (lower.contains("who are you") || lower.contains("what are you")) {
            return "I'm Local AI. Right now I have a lightweight offline command engine for a few tasks. I do not yet have a generative language model connected, so I won't pretend I can answer every question like a full AI."
        }
        if (lower.contains("internet") || lower.contains("online") || lower.contains("privacy") || lower.contains("private")) {
            return "This command engine processes your message on this device and does not need an internet connection. The app's current answers are limited; a future model must also run on-device to keep model inference offline."
        }
        if (lower.contains("voice assistant") || lower.contains("microphone")) {
            return "This app is text-only by design. It does not use a microphone or provide a voice assistant."
        }

        val expression = extractExpression(text)
        if (expression != null) {
            return try {
                val value = ArithmeticParser(expression).parse()
                val formatted = if (value.isFinite() && value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
                }
                "$expression = $formatted"
            } catch (_: Exception) {
                "I couldn't calculate that expression. Try something like: calculate 12 * (4 + 3). Supported operators: +, -, *, /, %, and parentheses."
            }
        }

        return "I can handle a few offline commands, but I don't have a language model yet for open-ended questions. Try “help”, “time”, “date”, or “calculate 12 * (4 + 3)”."
    }

    private fun matchesAny(text: String, vararg values: String): Boolean =
        values.any { text == it || text.contains(it) }

    private fun extractExpression(input: String): String? {
        val trimmed = input.trim()
        val candidate = when {
            trimmed.startsWith("calculate ", true) -> trimmed.substringAfter(' ')
            trimmed.startsWith("calc ", true) -> trimmed.substringAfter(' ')
            trimmed.startsWith("what is ", true) -> trimmed.substringAfter(' ')
            trimmed.matches(Regex("[0-9.()\\s+*/%^-]+")) -> trimmed
            else -> return null
        }.trim().removeSuffix("?").trim()
        if (candidate.isEmpty() || !candidate.any { it.isDigit() } ||
            !candidate.all { it.isDigit() || it in ".() +-* /%^".replace(" ", "") }) return null
        return candidate
    }

    private class ArithmeticParser(private val source: String) {
        private var pos = 0

        fun parse(): Double {
            val result = expression()
            skipSpaces()
            require(pos == source.length) { "Unexpected character" }
            require(result.isFinite()) { "Result is not finite" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * power()
                    consume('/') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "Division by zero" }
                        value / divisor
                    }
                    consume('%') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "Division by zero" }
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            var value = unary()
            skipSpaces()
            if (consume('^')) value = Math.pow(value, power())
            return value
        }

        private fun unary(): Double {
            skipSpaces()
            return when {
                consume('+') -> unary()
                consume('-') -> -unary()
                consume('(') -> {
                    val value = expression()
                    skipSpaces()
                    require(consume(')')) { "Missing closing parenthesis" }
                    value
                }
                else -> number()
            }
        }

        private fun number(): Double {
            skipSpaces()
            val start = pos
            var dots = 0
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) {
                if (source[pos] == '.') dots++
                require(dots <= 1) { "Invalid number" }
                pos++
            }
            require(pos > start) { "Expected a number" }
            return source.substring(start, pos).toDouble()
        }

        private fun consume(char: Char): Boolean {
            if (pos < source.length && source[pos] == char) {
                pos++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (pos < source.length && source[pos].isWhitespace()) pos++
        }
    }
}
