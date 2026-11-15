package com.example.mytexteditor

import com.example.mytexteditor.lsp.ClangdManager
import com.example.mytexteditor.lsp.LspPosition
import com.example.mytexteditor.lsp.LspPositionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LspOptimizationTest {

    @Test
    fun testPositionUtilsOffsetToPositionAndBack() {
        val text = "int main() {\n    // Tiếng Việt có dấu: Xin chào\n    return 0;\n}"
        val offset = text.indexOf("Xin chào")
        val pos = LspPositionUtils.offsetToPosition(text, offset)

        assertEquals(1, pos.line)
        val lineStart = text.indexOf('\n') + 1
        assertEquals(offset - lineStart, pos.character)

        val reconstructedOffset = LspPositionUtils.positionToOffset(text, pos)
        assertEquals(offset, reconstructedOffset)
    }

    @Test
    fun testIncrementalChangeSingleInsertion() {
        val oldText = "int x = ;"
        val newText = "int x = 10;"

        val change = ClangdManager.computeIncrementalChange(oldText, newText)
        assertNotNull(change)
        val (range, insertedText) = change!!

        assertEquals(8, LspPositionUtils.positionToOffset(oldText, range.start))
        assertEquals(8, LspPositionUtils.positionToOffset(oldText, range.end))
        assertEquals("10", insertedText)

        // Apply change to oldText and verify it matches newText
        val startOff = LspPositionUtils.positionToOffset(oldText, range.start)
        val endOff = LspPositionUtils.positionToOffset(oldText, range.end)
        val applied = oldText.substring(0, startOff) + insertedText + oldText.substring(endOff)
        assertEquals(newText, applied)
    }

    @Test
    fun testIncrementalChangeDeletion() {
        val oldText = "std::vector<int> items;"
        val newText = "std::items;"

        val change = ClangdManager.computeIncrementalChange(oldText, newText)
        assertNotNull(change)
        val (range, insertedText) = change!!

        assertEquals("", insertedText)
        val startOff = LspPositionUtils.positionToOffset(oldText, range.start)
        val endOff = LspPositionUtils.positionToOffset(oldText, range.end)
        val applied = oldText.substring(0, startOff) + insertedText + oldText.substring(endOff)
        assertEquals(newText, applied)
    }

    @Test
    fun testIncrementalChangeMultiline() {
        val oldText = "int main() {\n    return 0;\n}"
        val newText = "int main() {\n    int a = 5;\n    int b = 10;\n    return 0;\n}"

        val change = ClangdManager.computeIncrementalChange(oldText, newText)
        assertNotNull(change)
        val (range, insertedText) = change!!

        val startOff = LspPositionUtils.positionToOffset(oldText, range.start)
        val endOff = LspPositionUtils.positionToOffset(oldText, range.end)
        val applied = oldText.substring(0, startOff) + insertedText + oldText.substring(endOff)
        assertEquals(newText, applied)
    }

    @Test
    fun testIncrementalChangeNoDifference() {
        val text = "int a = 1;"
        val change = ClangdManager.computeIncrementalChange(text, text)
        assertNull(change)
    }

    @Test
    fun testIncrementalChangeWithSurrogatePairs() {
        // Emoji is 2 UTF-16 code units (surrogate pair)
        val oldText = "std::string greeting = \"Hello \uD83D\uDE00\";"
        val newText = "std::string greeting = \"Hello \uD83D\uDE00 World\";"

        val change = ClangdManager.computeIncrementalChange(oldText, newText)
        assertNotNull(change)
        val (range, insertedText) = change!!

        assertEquals(" World", insertedText)
        val startOff = LspPositionUtils.positionToOffset(oldText, range.start)
        val endOff = LspPositionUtils.positionToOffset(oldText, range.end)
        val applied = oldText.substring(0, startOff) + insertedText + oldText.substring(endOff)
        assertEquals(newText, applied)
    }

    @Test
    fun testIncrementalChangeReplaceMiddle() {
        val oldText = "for (int i = 0; i < n; ++i)"
        val newText = "for (size_t idx = 0; idx < n; ++idx)"

        val change = ClangdManager.computeIncrementalChange(oldText, newText)
        assertNotNull(change)
        val (range, insertedText) = change!!

        val startOff = LspPositionUtils.positionToOffset(oldText, range.start)
        val endOff = LspPositionUtils.positionToOffset(oldText, range.end)
        val applied = oldText.substring(0, startOff) + insertedText + oldText.substring(endOff)
        assertEquals(newText, applied)
    }

    @Test
    fun testIncrementalChangeReplaceAll() {
        val oldText = "hello world"
        val newText = "completely different content"

        val change = ClangdManager.computeIncrementalChange(oldText, newText)
        assertNotNull(change)
        val (range, insertedText) = change!!

        val startOff = LspPositionUtils.positionToOffset(oldText, range.start)
        val endOff = LspPositionUtils.positionToOffset(oldText, range.end)
        val applied = oldText.substring(0, startOff) + insertedText + oldText.substring(endOff)
        assertEquals(newText, applied)
    }
}
