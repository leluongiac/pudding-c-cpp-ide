cmake_minimum_required(VERSION 3.22.1)

project("syntax_engine")

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter/include
    ${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter/src
    ${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter-cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter-cpp/tree_sitter
)

add_library(
    syntax_engine
    SHARED
    tree-sitter/src/lib.c
    tree-sitter-cpp/parser.c
    tree-sitter-cpp/scanner.c
    cpp_highlighter.cpp
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    syntax_engine
    ${log-lib}
)
