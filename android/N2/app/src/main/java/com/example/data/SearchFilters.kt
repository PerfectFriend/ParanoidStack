/**
 * Search filter models and a manager for building and parsing filter queries.
 * Supports narrowing search by contact ID, date range, and message type (media/outgoing/incoming).
 * Filters are appended as tokens (e.g. contactId:alice, has:media, is:outgoing) to the raw query.
 */
package com.example.data

/**
 * Filter parameters used to narrow a full-text search query.
 *
 * @property contactId Restrict results to a specific contact.
 * @property dateFrom Earliest timestamp (inclusive), null = no lower bound.
 * @property dateTo Latest timestamp (inclusive), null = no upper bound.
 * @property onlyMedia Only messages that contain media attachments.
 * @property onlyOutgoing Only messages sent by the local user.
 * @property onlyIncoming Only messages received from contacts.
 */
data class SearchFilters(
    val contactId: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val onlyMedia: Boolean = false,
    val onlyOutgoing: Boolean = false,
    val onlyIncoming: Boolean = false
) {
    fun toQuery(originalQuery: String): String = buildString {
        append(originalQuery)
        contactId?.let { append(" contactId:$it") }
        onlyMedia.let { if (it) append(" has:media") }
        onlyOutgoing.let { if (it) append(" is:outgoing") }
        onlyIncoming.let { if (it) append(" is:incoming") }
    }
}

/**
 * Utility for building and parsing search filter tokens.
 * Enriches a base query with filter tokens and extracts filters from a raw query string.
 */
class SearchFilterManager {
    /**
     * Append filter tokens to the base search query string.
     * @return Combined query string with filter tokens appended.
     */
    fun buildQuery(base: String, filters: SearchFilters): String {
        return filters.toQuery(base)
    }

    /**
     * Parse a raw query string, extracting filter tokens and returning
     * the cleaned query together with a [SearchFilters] object.
     * @return Pair of (clean query string, parsed filters).
     */
    fun parseQuery(raw: String): Pair<String, SearchFilters> {
        var query = raw
        var contactId: String? = null
        var onlyMedia = false
        var onlyOutgoing = false
        var onlyIncoming = false

        val tokens = raw.split(" ").filter { it.startsWith("contactId:") || it.startsWith("has:") || it.startsWith("is:") }
        tokens.forEach { token ->
            query = query.replace(token, "").trim()
            when {
                token.startsWith("contactId:") -> contactId = token.removePrefix("contactId:")
                token == "has:media" -> onlyMedia = true
                token == "is:outgoing" -> onlyOutgoing = true
                token == "is:incoming" -> onlyIncoming = true
            }
        }

        return query to SearchFilters(contactId, onlyMedia = onlyMedia, onlyOutgoing = onlyOutgoing, onlyIncoming = onlyIncoming)
    }
}
