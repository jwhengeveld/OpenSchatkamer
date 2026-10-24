package nl.openschatkamer.core.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Lokale bibliotheek op het toestel. Kaarten worden als JSON bewaard (zelfde
 * vorm als de snapshots), zodat nieuwe velden in de catalogus geen
 * databasemigratie nodig hebben.
 */

@Entity(tableName = "saved_item", primaryKeys = ["itemKey"])
data class SavedItemEntity(
    val itemKey: String,
    val itemJson: String,
    val savedAt: Long
)

@Entity(tableName = "user_list")
data class UserListEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "user_list_item", primaryKeys = ["listId", "itemKey"])
data class UserListItemEntity(
    val listId: Long,
    val itemKey: String,
    val itemJson: String,
    val addedAt: Long
)

/** Kijkvoortgang én geschiedenis: één rij per uitzending, bijgewerkt bij elk kijken. */
@Entity(tableName = "watch")
data class WatchEntity(
    @androidx.room.PrimaryKey val programId: String,
    val itemJson: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Handmatig als gezien gemarkeerd, of uitgekeken. */
    val watched: Boolean
)

@Entity(tableName = "followed_series")
data class FollowedSeriesEntity(
    @androidx.room.PrimaryKey val seriesId: String,
    val itemJson: String,
    val followedAt: Long,
    /** Aantal afleveringen bij de laatste controle; meer = nieuwe afleveringen. */
    val knownEpisodes: Int,
    val newEpisodes: Int,
    val checkedAt: Long
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM saved_item ORDER BY savedAt DESC")
    fun saved(): Flow<List<SavedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: SavedItemEntity)

    @Query("DELETE FROM saved_item WHERE itemKey = :key")
    suspend fun unsave(key: String)

    @Query("SELECT * FROM user_list ORDER BY createdAt")
    fun lists(): Flow<List<UserListEntity>>

    @Query("SELECT * FROM user_list_item ORDER BY addedAt DESC")
    fun listItems(): Flow<List<UserListItemEntity>>

    @Insert
    suspend fun createList(list: UserListEntity): Long

    @Query("UPDATE user_list SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM user_list WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM user_list_item WHERE listId = :id")
    suspend fun clearList(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToList(item: UserListItemEntity)

    @Query("DELETE FROM user_list_item WHERE listId = :listId AND itemKey = :key")
    suspend fun removeFromList(listId: Long, key: String)

    @Query("SELECT * FROM watch ORDER BY updatedAt DESC")
    fun watch(): Flow<List<WatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatch(entry: WatchEntity)

    @Query("SELECT * FROM watch WHERE programId = :id")
    suspend fun watchEntry(id: String): WatchEntity?

    @Query("DELETE FROM watch WHERE programId = :id")
    suspend fun deleteWatch(id: String)

    @Query("DELETE FROM watch")
    suspend fun clearWatch()

    @Query("SELECT * FROM followed_series ORDER BY followedAt DESC")
    fun followed(): Flow<List<FollowedSeriesEntity>>

    @Query("SELECT * FROM followed_series")
    suspend fun followedNow(): List<FollowedSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun follow(series: FollowedSeriesEntity)

    @Query("DELETE FROM followed_series WHERE seriesId = :id")
    suspend fun unfollow(id: String)
}

@Database(
    entities = [SavedItemEntity::class, UserListEntity::class, UserListItemEntity::class, WatchEntity::class, FollowedSeriesEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        @Volatile
        private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "openschatkamer-library.db")
                    .build().also { instance = it }
            }
    }
}
