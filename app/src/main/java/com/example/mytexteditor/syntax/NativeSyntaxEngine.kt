package com.example.mytexteditor.syntax

object NativeSyntaxEngine {
    init {
        System.loadLibrary("syntax_engine")
    }

    external fun parseCpp(text: String): IntArray
}
