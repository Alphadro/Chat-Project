package fit.vcare.apps.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
//ChatDatabase
@Database(entities = [MessageEntity::class], version = 5, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, ChatDatabase::class.java, "chat_db"
                )
                    .fallbackToDestructiveMigration() // ← جدید: schema عوض شد، کش قدیمی پاک و از نو ساخته می‌شه
                    .build()
                    .also { INSTANCE = it }
            }
    }
}