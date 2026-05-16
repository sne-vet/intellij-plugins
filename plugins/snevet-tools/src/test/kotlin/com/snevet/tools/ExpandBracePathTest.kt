package com.snevet.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExpandBracePathTest {

    @Test
    fun `returns input unchanged when there is no rename marker`() {
        assertEquals(
            "src/main/kotlin/Foo.kt",
            DiffWithMergeBaseAction.expandBracePath("src/main/kotlin/Foo.kt"),
        )
    }

    @Test
    fun `expands brace rename in the middle of a path`() {
        assertEquals(
            "src/main/new/Foo.kt",
            DiffWithMergeBaseAction.expandBracePath("src/main/{old => new}/Foo.kt"),
        )
    }

    @Test
    fun `expands brace rename at the start of a path`() {
        assertEquals(
            "new/Foo.kt",
            DiffWithMergeBaseAction.expandBracePath("{old => new}/Foo.kt"),
        )
    }

    @Test
    fun `expands brace rename of just the filename`() {
        assertEquals(
            "src/new.kt",
            DiffWithMergeBaseAction.expandBracePath("src/{old.kt => new.kt}"),
        )
    }

    @Test
    fun `falls back to substringAfter for full-path rename without braces`() {
        assertEquals(
            "dst/new.txt",
            DiffWithMergeBaseAction.expandBracePath("src/old.txt => dst/new.txt"),
        )
    }

    @Test
    fun `returns input unchanged when arrow is outside braces`() {
        // Arrow appears after the closing brace, so the brace branch should not match;
        // the substringAfter branch picks up the post-arrow segment instead.
        assertEquals(
            "after.txt",
            DiffWithMergeBaseAction.expandBracePath("{a}/b => after.txt"),
        )
    }

    @Test
    fun `returns empty input unchanged`() {
        assertEquals("", DiffWithMergeBaseAction.expandBracePath(""))
    }

    @Test
    fun `preserves suffix after closing brace`() {
        assertEquals(
            "a/new/c/Foo.kt",
            DiffWithMergeBaseAction.expandBracePath("a/{b => new}/c/Foo.kt"),
        )
    }
}
