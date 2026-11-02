#include "cpp_highlighter.hpp"
#include <algorithm>
#include <cstring>
#include <string_view>
#include <android/log.h>

#define LOG_TAG "CppHighlighter"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" const TSLanguage *tree_sitter_cpp(void);

// Tree-sitter Query chuẩn Catppuccin Mocha C++
// Query chuẩn an toàn 100% cho Tree-sitter C++ (Catppuccin Mocha)
static const char* CPP_HIGHLIGHT_QUERY =
    // F. Preprocessor (Teal)
    "\"#include\" @preproc\n"
    "\"#define\" @preproc\n"
    "\"#if\" @preproc\n"
    "\"#ifdef\" @preproc\n"
    "\"#ifndef\" @preproc\n"
    "\"#endif\" @preproc\n"
    "\"#else\" @preproc\n"
    "\"#elif\" @preproc\n"
    "(preproc_directive) @preproc\n"
    "(system_lib_string) @string\n"

    // A. Control Flow & Keywords (Mauve)
    "[\n"
    " \"if\" \"else\" \"for\" \"while\" \"do\" \"switch\" \"case\" \"default\"\n"
    " \"break\" \"continue\" \"return\" \"co_return\" \"co_yield\"\n"
    " \"try\" \"catch\" \"throw\" \"noexcept\"\n"
    " \"new\" \"delete\" \"sizeof\"\n"
    " \"class\" \"struct\" \"union\" \"enum\" \"typename\" \"template\"\n"
    " \"using\" \"namespace\" \"typedef\"\n"
    " \"static\" \"extern\" \"mutable\"\n"
    " \"public\" \"protected\" \"private\"\n"
    " \"virtual\" \"nullptr\"\n"
    "] @keyword.control\n"

    // A. Const & Immutability (Peach)
    "[\n"
    " \"const\" \"constexpr\" \"volatile\" \"inline\"\n"
    "] @keyword.const\n"

    // C. Builtin objects (Sapphire)
    "((identifier) @builtin (#match? @builtin \"^(cout|cin|cerr|endl)$\"))\n"

    // B. Types & Namespace (Yellow & Lavender)
    "(primitive_type) @type\n"
    "(type_identifier) @type\n"
    "(sized_type_specifier) @type\n"
    "(auto) @type\n"
    "(namespace_identifier) @namespace\n"
    "((identifier) @type (#match? @type \"^(std|string|vector|map|set|pair|tuple)$\"))\n"

    // C. Functions (Blue)
    "(call_expression function: (identifier) @function)\n"
    "(function_declarator declarator: (identifier) @function)\n"

    // D & E. Literals (Peach, Red, Green)
    "(this) @this\n"
    "(number_literal) @number\n"
    "(true) @number\n"
    "(false) @number\n"
    "(string_literal) @string\n"
    "(char_literal) @string\n"

    // G. Operators (Sky)
    "[\n"
    " \"<<\" \">>\" \"<=\" \">=\" \"==\" \"!=\" \"&&\" \"||\" \"->\" \"::\"\n"
    " \"+\" \"-\" \"*\" \"/\" \"%\" \"=\" \"+=\" \"-=\"\n"
    "] @operator\n"

    // H. Comments (Overlay0)
    "(comment) @comment\n";

CppHighlighter::CppHighlighter() : parser(nullptr), query(nullptr) {
    parser = ts_parser_new();
    ts_parser_set_language(parser, tree_sitter_cpp());

    uint32_t error_offset;
    TSQueryError error_type;
    query = ts_query_new(tree_sitter_cpp(), CPP_HIGHLIGHT_QUERY, strlen(CPP_HIGHLIGHT_QUERY), &error_offset, &error_type);
    if (!query) {
        LOGE("Failed to compile C++ query: error %d at offset %u", error_type, error_offset);
    }
}

CppHighlighter::~CppHighlighter() {
    if (query) ts_query_delete(query);
    if (parser) ts_parser_delete(parser);
}

static inline int32_t capture_to_type(const char* name, uint32_t len) {
    std::string_view s(name, len);
    if (s == "keyword.control") return 1;
    if (s == "keyword.const")   return 2;
    if (s == "keyword.cast")    return 3;
    if (s == "type")            return 4;
    if (s == "namespace")       return 5;
    if (s == "function")        return 6;
    if (s == "builtin")         return 7;
    if (s == "this")            return 8;
    if (s == "number")          return 9;
    if (s == "string")          return 10;
    if (s == "preproc")         return 11;
    if (s == "operator")        return 12;
    if (s == "punctuation")     return 13;
    if (s == "comment")         return 14;
    return 0;
}

std::vector<int32_t> CppHighlighter::highlight(const char* utf8_text, size_t length) {
    std::vector<int32_t> tokens;
    if (!parser || !query || length == 0) {
        return tokens;
    }

    std::lock_guard<std::mutex> lock(mtx);

    bool has_non_ascii = false;
    for (size_t i = 0; i < length; ++i) {
        if ((unsigned char)utf8_text[i] >= 0x80) {
            has_non_ascii = true;
            break;
        }
    }

    std::vector<int32_t> byte_to_char;
    if (has_non_ascii) {
        byte_to_char.resize(length + 1);
        size_t b = 0;
        int32_t c = 0;
        while (b < length) {
            byte_to_char[b] = c;
            unsigned char ch = (unsigned char)utf8_text[b];
            if (ch < 0x80) {
                b += 1;
                c += 1;
            } else if ((ch & 0xE0) == 0xC0) {
                if (b + 1 <= length) byte_to_char[b + 1] = c;
                b += 2;
                c += 1;
            } else if ((ch & 0xF0) == 0xE0) {
                if (b + 1 <= length) byte_to_char[b + 1] = c;
                if (b + 2 <= length) byte_to_char[b + 2] = c;
                b += 3;
                c += 1;
            } else if ((ch & 0xF8) == 0xF0) {
                if (b + 1 <= length) byte_to_char[b + 1] = c;
                if (b + 2 <= length) byte_to_char[b + 2] = c;
                if (b + 3 <= length) byte_to_char[b + 3] = c;
                b += 4;
                c += 2;
            } else {
                b += 1;
                c += 1;
            }
        }
        byte_to_char[length] = c;
    }

    TSTree* tree = ts_parser_parse_string(parser, nullptr, utf8_text, length);
    if (!tree) {
        return tokens;
    }

    TSNode root = ts_tree_root_node(tree);
    TSQueryCursor* cursor = ts_query_cursor_new();
    ts_query_cursor_exec(cursor, query, root);

    TSQueryMatch match;
    tokens.reserve(256 * 3);

    while (ts_query_cursor_next_match(cursor, &match)) {
        for (uint16_t i = 0; i < match.capture_count; ++i) {
            uint32_t name_len;
            const char* name = ts_query_capture_name_for_id(query, match.captures[i].index, &name_len);
            int32_t type = capture_to_type(name, name_len);
            if (type <= 0) continue;

            TSNode node = match.captures[i].node;
            uint32_t start_byte = ts_node_start_byte(node);
            uint32_t end_byte = ts_node_end_byte(node);
            if (start_byte >= end_byte || start_byte > length || end_byte > length) continue;

            int32_t start_char = has_non_ascii ? byte_to_char[start_byte] : (int32_t)start_byte;
            int32_t end_char = has_non_ascii ? byte_to_char[end_byte] : (int32_t)end_byte;

            if (start_char < end_char) {
                tokens.push_back(start_char);
                tokens.push_back(end_char);
                tokens.push_back(type);
            }
        }
    }

    ts_query_cursor_delete(cursor);
    ts_tree_delete(tree);
    return tokens;
}