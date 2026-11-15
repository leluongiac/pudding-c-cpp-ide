package com.example.mytexteditor.lsp

/**
 * Các mô hình dữ liệu chuẩn của Language Server Protocol (LSP 3.17)
 */

data class LspPosition(
    val line: Int,
    val character: Int
)

data class LspRange(
    val start: LspPosition,
    val end: LspPosition
)

enum class CompletionItemKind(val value: Int) {
    Text(1), Method(2), Function(3), Constructor(4), Field(5),
    Variable(6), Class(7), Interface(8), Module(9), Property(10),
    Unit(11), Value(12), Enum(13), Keyword(14), Snippet(15),
    Color(16), File(17), Reference(18), Folder(19), EnumMember(20),
    Constant(21), Struct(22), Event(23), Operator(24), TypeParameter(25);

    companion object {
        fun fromInt(v: Int): CompletionItemKind = entries.firstOrNull { it.value == v } ?: Text
    }
}

data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind = CompletionItemKind.Text,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String? = null,
    val filterText: String? = null,
    val sortText: String? = null,
    val isSnippet: Boolean = false
) {
    /**
     * Tên hiển thị loại ngữ nghĩa ngắn gọn theo phong cách GNOME / IDE
     */
    val displayKind: String
        get() = when (kind) {
            CompletionItemKind.Class -> "class"
            CompletionItemKind.Struct -> "struct"
            CompletionItemKind.Interface -> "interface"
            CompletionItemKind.Method -> "method"
            CompletionItemKind.Function -> "func"
            CompletionItemKind.Constructor -> "ctor"
            CompletionItemKind.Field -> "field"
            CompletionItemKind.Variable -> "var"
            CompletionItemKind.Keyword -> "keyword"
            CompletionItemKind.Snippet -> "snippet"
            CompletionItemKind.Module -> "module"
            CompletionItemKind.Enum -> "enum"
            CompletionItemKind.EnumMember -> "enum_val"
            CompletionItemKind.Constant -> "const"
            CompletionItemKind.TypeParameter -> "type"
            CompletionItemKind.Operator -> "operator"
            else -> "text"
        }
}

data class ParameterInformation(
    val label: String,
    val documentation: String? = null
)

data class SignatureInformation(
    val label: String,
    val documentation: String? = null,
    val parameters: List<ParameterInformation> = emptyList(),
    val activeParameter: Int = 0
)

data class SignatureHelp(
    val signatures: List<SignatureInformation> = emptyList(),
    val activeSignature: Int = 0,
    val activeParameter: Int = 0
)

enum class DiagnosticSeverity(val value: Int) {
    Error(1), Warning(2), Information(3), Hint(4);
    companion object {
        fun fromInt(v: Int): DiagnosticSeverity = entries.firstOrNull { it.value == v } ?: Information
    }
}

data class Diagnostic(
    val range: LspRange,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String? = null
)
