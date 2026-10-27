package com.omeron.util

import com.omeron.data.model.Block
import com.omeron.data.model.HtmlBlock
import com.omeron.data.model.RedditText

/**
 * True when [query] occurs, ignoring case, in a text or code block. Table cells are not searched
 * because the post view does not mark them either.
 */
fun RedditText.containsIgnoreCase(query: String): Boolean {
    return blocks.any { htmlBlock ->
        val isSearchable = htmlBlock.type == HtmlBlock.BlockType.TEXT ||
            htmlBlock.type == HtmlBlock.BlockType.CODE
        val textBlock = htmlBlock.block as? Block.TextBlock
        isSearchable && textBlock != null && textBlock.text.contains(query, ignoreCase = true)
    }
}
