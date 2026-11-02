package com.example.mytexteditor.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class TreeSitterVisualTransformation(
    private val tokens: IntArray,
    private val colorScheme: SyntaxColorScheme = SyntaxColorScheme()
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val textLength = text.length

        if (tokens.isEmpty() || textLength == 0) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(text)
        val count = tokens.size / 3

        for (i in 0 until count) {
            val idx = i * 3
            val start = tokens[idx]
            val end = tokens[idx + 1]
            val type = tokens[idx + 2]

            if (start in 0 until textLength && end in (start + 1)..textLength) {
                val color = when (type) {
                    1 -> colorScheme.mauve       // Keyword Control
                    2 -> colorScheme.peach       // Keyword Const
                    3 -> colorScheme.sapphire    // Keyword Cast
                    4 -> colorScheme.yellow      // Type
                    5 -> colorScheme.lavender    // Namespace
                    6 -> colorScheme.blue        // Function
                    7 -> colorScheme.sapphire    // Builtin (cout, endl)
                    8 -> colorScheme.red         // this
                    9 -> colorScheme.peach       // Number / Boolean / Nullptr
                    10 -> colorScheme.green      // String & Header paths
                    11 -> colorScheme.teal       // Preprocessor (#include)
                    12 -> colorScheme.sky        // Operator (<<, +...)
                    13 -> colorScheme.overlay2   // Punctuation
                    14 -> colorScheme.overlay0   // Comment
                    else -> null
                }
                if (color != null) {
                    builder.addStyle(SpanStyle(color = color), start, end)
                }
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TreeSitterVisualTransformation) return false
        return tokens.contentEquals(other.tokens) && colorScheme == other.colorScheme
    }

    override fun hashCode(): Int {
        return tokens.contentHashCode() * 31 + colorScheme.hashCode()
    }
}