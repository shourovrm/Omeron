package com.omeron.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.omeron.data.model.db.Multireddit
import com.omeron.data.model.db.MultiredditMember
import com.omeron.data.model.db.MultiredditWithMembers
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MultiredditDao : BaseDao<Multireddit> {

    @Query("UPDATE multireddit SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE multireddit SET hidden = :hidden WHERE id = :id")
    abstract suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("DELETE FROM multireddit WHERE id = :id")
    abstract suspend fun deleteFromId(id: Long)

    @Insert
    abstract suspend fun addMember(member: MultiredditMember): Long

    @Delete
    abstract suspend fun removeMember(member: MultiredditMember)

    @Query("DELETE FROM multireddit_member WHERE multireddit_id = :multiredditId AND target_name = :targetName AND type = :type")
    abstract suspend fun removeMember(multiredditId: Long, targetName: String, type: Int)

    @Transaction
    @Query("SELECT * FROM multireddit WHERE profile_id = :profileId")
    abstract fun getMultisWithMembersFromProfile(profileId: Int): Flow<List<MultiredditWithMembers>>

    @Transaction
    @Query("SELECT * FROM multireddit WHERE profile_id = :profileId AND hidden = 0")
    abstract fun getVisibleMultisWithMembersFromProfile(profileId: Int): Flow<List<MultiredditWithMembers>>
}
