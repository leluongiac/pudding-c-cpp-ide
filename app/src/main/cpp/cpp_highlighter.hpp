#pragma once

#include <vector>
#include <string>
#include <cstdint>
#include <mutex>
#include "tree_sitter/api.h"

class CppHighlighter {
public:
    CppHighlighter();
    ~CppHighlighter();

    // Returns a flat vector of [start_offset, end_offset, token_type, ...]
    std::vector<int32_t> highlight(const char* utf8_text, size_t length);

private:
    TSParser* parser;
    TSQuery* query;
    std::mutex mtx; // Khóa đồng bộ luồng tránh race-condition từ Coroutine
};