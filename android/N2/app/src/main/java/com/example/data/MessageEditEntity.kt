/**
 * Room entity for the message edit history table.
 * Stores each revision of an edited message, preserving the original message ID,
 * the updated text, the timestamp of the edit, and an incrementing edit number.
 * Referenced by [AppDatabase.MessageEditDao] for persistence and retrieval.
 */
package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single revision record for an edited message.
 *
 * @property id Auto-generated primary key.
 * @property originalMessageId FK to the original message in [SecureMessageEntity].
 * @property newText The updated message text at this revision.
 * @property editedAt Unix timestamp when this edit was made.
 * @property editNumber Sequential revision number (1-based).
 */
@Entity(tableName = "message_edits")
data class MessageEditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalMessageId: Long,
    val newText: String,
    val editedAt: Long = System.currentTimeMillis(),
    val editNumber: Int = 1
)
