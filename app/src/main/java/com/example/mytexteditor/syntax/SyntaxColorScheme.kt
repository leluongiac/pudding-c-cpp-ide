package com.example.mytexteditor.syntax

import androidx.compose.ui.graphics.Color

data class SyntaxColorScheme(
    // Catppuccin Mocha Palette chuẩn
    val mauve: Color = Color(0xFFCBA6F7),       // Control & Keywords (if, return, class, using...)
    val peach: Color = Color(0xFFFAB387),       // Const, Numbers, Boolean, nullptr
    val sapphire: Color = Color(0xFF74C7EC),    // Casts, Builtins (cout, endl...)
    val yellow: Color = Color(0xFFF9E2AF),      // Types (int, float, void, auto, MyClass...)
    val lavender: Color = Color(0xFFB4BEFE),    // Namespace (std, boost...)
    val blue: Color = Color(0xFF89B4FA),        // Functions (main, methods...)
    val sky: Color = Color(0xFF89DCEB),         // Operators (<<, +, ::, ->, =...)
    val red: Color = Color(0xFFF38BA8),         // `this` pointer
    val green: Color = Color(0xFFA6E3A1),       // Strings, Include path (<iostream>, "header.h")
    val teal: Color = Color(0xFF94E2D5),        // Preprocessor (#include, #define, #pragma...)
    val overlay2: Color = Color(0xFF9399B2),    // Brackets (), {}, Delimiters ;, ,
    val overlay0: Color = Color(0xFF6C7086),    // Comments //, /* */
    val text: Color = Color(0xFFCDD6F4),        // Default Text
    val base: Color = Color(0xFF1E1E2E)         // Background Editor Catppuccin
)