package com.omeron.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omeron.data.local.dao.CommentDao
import com.omeron.data.local.dao.FollowedUserDao
import com.omeron.data.local.dao.HistoryDao
import com.omeron.data.local.dao.MultiredditDao
import com.omeron.data.local.dao.PostDao
import com.omeron.data.local.dao.ProfileDao
import com.omeron.data.local.dao.RedirectDao
import com.omeron.data.local.dao.SubscriptionDao
import com.omeron.data.model.Comment
import com.omeron.data.model.db.FollowedUser
import com.omeron.data.model.db.History
import com.omeron.data.model.db.Multireddit
import com.omeron.data.model.db.MultiredditMember
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.db.Profile
import com.omeron.data.model.db.Redirect
import com.omeron.data.model.db.Subscription

@Database(
    entities = [
        Subscription::class,
        History::class,
        Profile::class,
        PostEntity::class,
        Comment.CommentEntity::class,
        Redirect::class,
        FollowedUser::class,
        Multireddit::class,
        MultiredditMember::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RedditDatabase : RoomDatabase() {

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun historyDao(): HistoryDao

    abstract fun profileDao(): ProfileDao

    abstract fun postDao(): PostDao

    abstract fun commentDao(): CommentDao

    abstract fun redirectDao(): RedirectDao

    abstract fun followedUserDao(): FollowedUserDao

    abstract fun multiredditDao(): MultiredditDao

    class Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            insertDefaultProfile(db)
        }
    }

    companion object {
        private const val DEFAULT_PROFILE_ID = 1
        private const val DEFAULT_PROFILE_NAME = "Omeron"

        private fun insertDefaultProfile(database: SupportSQLiteDatabase) {
            database.execSQL("INSERT INTO profile (id, name) VALUES($DEFAULT_PROFILE_ID, '$DEFAULT_PROFILE_NAME')")
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `profile` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent())
                insertDefaultProfile(database)

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `new_subscription` (
                        `name` TEXT NOT NULL COLLATE NOCASE, 
                        `time` INTEGER NOT NULL, 
                        `icon` TEXT, 
                        `profile_id` INTEGER DEFAULT $DEFAULT_PROFILE_ID NOT NULL, 
                    PRIMARY KEY(`name`, `profile_id`), 
                    FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                    """.trimIndent())
                database.execSQL("""
                    INSERT INTO new_subscription (name, time, icon) 
                    SELECT name, time, icon FROM subscription
                    """.trimIndent())
                database.execSQL("DROP TABLE subscription")
                database.execSQL("ALTER TABLE new_subscription RENAME TO subscription")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `new_history` (
                        `post_id` TEXT NOT NULL, 
                        `time` INTEGER NOT NULL, 
                        `profile_id` INTEGER DEFAULT $DEFAULT_PROFILE_ID NOT NULL, 
                    PRIMARY KEY(`post_id`, `profile_id`), 
                    FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                    """.trimIndent())
                database.execSQL("INSERT INTO new_history (post_id, time) SELECT post_id, time FROM history")
                database.execSQL("DROP TABLE history")
                database.execSQL("ALTER TABLE new_history RENAME TO history")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `post` (
                        `id` TEXT NOT NULL, 
                        `subreddit` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `ratio` INTEGER NOT NULL, 
                        `total_awards` INTEGER NOT NULL, 
                        `oc` INTEGER NOT NULL, 
                        `score` TEXT NOT NULL, 
                        `type` INTEGER NOT NULL, 
                        `domain` TEXT NOT NULL, 
                        `self` INTEGER NOT NULL, 
                        `self_text_html` TEXT, 
                        `suggested_sorting` TEXT NOT NULL, 
                        `nsfw` INTEGER NOT NULL, 
                        `preview` TEXT, 
                        `spoiler` INTEGER NOT NULL, 
                        `archived` INTEGER NOT NULL, 
                        `locked` INTEGER NOT NULL, 
                        `poster_type` INTEGER NOT NULL, 
                        `author` TEXT NOT NULL, 
                        `comments_number` TEXT NOT NULL, 
                        `permalink` TEXT NOT NULL, 
                        `stickied` INTEGER NOT NULL, 
                        `url` TEXT NOT NULL, 
                        `created` INTEGER NOT NULL, 
                        `media_type` TEXT NOT NULL, 
                        `media_url` TEXT NOT NULL, 
                        `time` INTEGER NOT NULL, 
                        `profile_id` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`, `profile_id`), 
                    FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                    """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `comment` (
                        `total_awards` INTEGER NOT NULL, 
                        `link_id` TEXT NOT NULL, 
                        `author` TEXT NOT NULL, 
                        `score` TEXT NOT NULL, 
                        `body_html` TEXT NOT NULL, 
                        `edited` INTEGER NOT NULL, 
                        `submitter` INTEGER NOT NULL, 
                        `stickied` INTEGER NOT NULL, 
                        `score_hidden` INTEGER NOT NULL, 
                        `permalink` TEXT NOT NULL, 
                        `id` TEXT NOT NULL, 
                        `created` INTEGER NOT NULL, 
                        `controversiality` INTEGER NOT NULL, 
                        `poster_type` INTEGER NOT NULL, 
                        `link_title` TEXT, 
                        `link_permalink` TEXT, 
                        `link_author` TEXT, 
                        `subreddit` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `time` INTEGER NOT NULL, 
                        `profile_id` INTEGER NOT NULL, 
                    PRIMARY KEY(`name`, `profile_id`), 
                    FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                    """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_history_profile_id` ON `history` (`profile_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_post_profile_id` ON `post` (`profile_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_subscription_profile_id` ON `subscription` (`profile_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_comment_profile_id` ON `comment` (`profile_id`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `redirect` (
                        `pattern` TEXT NOT NULL, 
                        `redirect` TEXT NOT NULL, 
                        `service` TEXT NOT NULL, 
                        `mode` INTEGER NOT NULL, 
                        PRIMARY KEY(`service`)
                    )
                    """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE subscription ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0"
                )

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `followed_user` (
                        `name` TEXT NOT NULL COLLATE NOCASE,
                        `icon` TEXT,
                        `time` INTEGER NOT NULL,
                        `hidden` INTEGER NOT NULL DEFAULT 0,
                        `profile_id` INTEGER NOT NULL,
                    PRIMARY KEY(`name`, `profile_id`),
                    FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_followed_user_profile_id` ON `followed_user` (`profile_id`)"
                )

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `multireddit` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `hidden` INTEGER NOT NULL DEFAULT 0,
                        `profile_id` INTEGER NOT NULL,
                    FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_multireddit_profile_id` ON `multireddit` (`profile_id`)"
                )

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `multireddit_member` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `multireddit_id` INTEGER NOT NULL,
                        `target_name` TEXT NOT NULL,
                        `type` INTEGER NOT NULL,
                    FOREIGN KEY(`multireddit_id`) REFERENCES `multireddit`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_multireddit_member_multireddit_id` ON `multireddit_member` (`multireddit_id`)"
                )
            }
        }
    }
}
