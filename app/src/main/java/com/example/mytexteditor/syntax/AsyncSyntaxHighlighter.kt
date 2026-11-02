package com.example.mytexteditor.syntax

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AsyncSyntaxHighlighter(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _tokens = mutableStateOf(IntArray(0))
    val tokens: State<IntArray> = _tokens

    private val textFlow = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            textFlow.collectLatest { text ->
                // Chờ 15ms xem user có gõ tiếp không, nếu gõ tiếp thì hủy lệnh cũ tránh tốn pin
                delay(15)
                try {
                    val result = NativeSyntaxEngine.parseCpp(text)
                    _tokens.value = result
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateText(text: String) {
        textFlow.tryEmit(text)
    }
}