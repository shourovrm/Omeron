package com.omeron.data.local.mapper

import com.omeron.data.model.db.SubredditEntity
import com.omeron.data.remote.api.reddit.model.AboutData
import com.omeron.di.DispatchersModule
import com.omeron.util.HtmlParser
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubredditMapper2 @Inject constructor(
    @DispatchersModule.DefaultDispatcher defaultDispatcher: CoroutineDispatcher
) : Mapper<AboutData, SubredditEntity>(defaultDispatcher) {

    private val htmlParser: HtmlParser = HtmlParser(defaultDispatcher)

    override suspend fun toEntity(from: AboutData): SubredditEntity {
        with(from) {
            return SubredditEntity(
                wikiEnabled ?: false,
                displayName,
                getHeader(),
                title,
                getPrimaryColor(),
                activeUserCount,
                getIcon(),
                subscribers,
                quarantine ?: false,
                htmlParser.separateHtmlBlocks(publicDescriptionHtml),
                getKeyColor(),
                getBackgroundColor(),
                over18 ?: false,
                htmlParser.separateHtmlBlocks(descriptionHtml),
                url,
                getTimeInMillis()
            )
        }
    }
}
