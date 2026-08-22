/**
 * Встроенный контроллер SimpleX для Android.
 * Предоставляет высокоуровневый API для работы с SimpleX:
 * управление профилями, контактами, группами, каналами,
 * send/recv сообщений с E2EE.
 *
 * Интегрируется с SMPAgent для работы через SMP-протокол.
 */
package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyPair
import java.security.SecureRandom

/**
 * Встроенный контроллер SimpleX.
 * Управляет полным жизненным циклом SimpleX-клиента.
 *
 * @param context контекст приложения
 * @param socksPort порт SOCKS5 прокси
 * @param userHandle отображаемое имя пользователя
 * @param onLog callback логирования
 * @param onStatusChange callback изменения статуса
 * @param onInvitationCreated callback создания приглашения
 * @param onMessageReceived callback получения сообщения
 * @param onContactRequest callback запроса контакта
 * @param onGroupInvite callback приглашения в группу
 * @param onChannelMessage callback канального сообщения
 * @param onGroupMessage callback группового сообщения
 */
class SimpleXEmbeddedController(
    private val context: Context,
    private val socksPort: Int = 10808,
    private val userHandle: String = "user",
    private val onLog: (String) -> Unit,
    private val onStatusChange: (String) -> Unit,
    private val onInvitationCreated: (String) -> Unit,
    private val onMessageReceived: (sender: String, text: String, roomId: String, msgType: String) -> Unit,
    private val onContactRequest: (handle: String, displayName: String, pubKeyB64: String) -> Unit,
    private val onGroupInvite: (fromHandle: String, groupId: String, groupName: String) -> Unit,
    private val onChannelMessage: (channelId: String, channelName: String, text: String) -> Unit,
    private val onGroupMessage: (groupId: String, senderName: String, text: String, timestamp: Long) -> Unit = { _, _, _, _ -> }
) {
    companion object {
        private const val TAG = "SimpleXEmbedded"
    }

    private var agent: SMPAgent? = null
    private var running = false
    private var currentProfileId: String = "default"
    private val profileKeyPairs = mutableMapOf<String, KeyPair>()
    private val activeKeyPair: KeyPair
        get() = profileKeyPairs.getOrPut(currentProfileId) { NaClCrypto.generateKeyPair() }

    /** Сохранённый контакт */
    data class StoredContact(
        val handle: String,            // ID контакта
        val displayName: String,       // отображаемое имя
        val publicKeyB64: String,      // публичный ключ в Base64
        val addedAt: Long = System.currentTimeMillis(),  // дата добавления
        var isBlocked: Boolean = false  // заблокирован?
    )

    /** Инициализировать профиль */
    fun initProfile(profileId: String) {
        if (running) stop()
        currentProfileId = profileId
        profileKeyPairs.getOrPut(profileId) { NaClCrypto.generateKeyPair() }
    }

    /** Получить текущий ID профиля */
    fun getCurrentProfileId(): String = currentProfileId

    /**
     * Запустить контроллер.
     * @param smpOnionAddress .onion адрес SMP-сервера (опционально)
     * @param xftpOnionAddress .onion адрес XFTP-сервера (опционально)
     */
    fun start(smpOnionAddress: String = "", xftpOnionAddress: String = "") {
        if (running) return
        val identity = SMPIdentity(activeKeyPair, displayName = userHandle)
        agent = SMPAgent(
            identity = identity,
            profileId = currentProfileId,
            onMessageReceived = { fromId, text ->
                onLog("MSG from $fromId: $text")
                onMessageReceived(fromId, text, "BOT_CHAT", "text")
            },
            onContactRequest = { inviteJson ->
                try {
                    val json = JSONObject(inviteJson)
                    val handle = json.optString("displayName", "unknown")
                    val pubKey = json.optString("publicKey", "")
                    onLog("Contact request from $handle")
                    onContactRequest(handle, handle, pubKey)
                } catch (e: Exception) {
                    onLog("Contact request parse error: ${e.message}")
                }
            },
            onError = { err -> onLog("Agent error: $err") }
        )
        agent?.setOnGroupMessageCallback { groupId, senderName, text, timestamp ->
            onLog("Group message from $senderName in $groupId: $text")
            onGroupMessage(groupId, senderName, text, timestamp)
        }
        val onionServers = mutableListOf<SMPQueueURI>()
        if (smpOnionAddress.isNotBlank()) {
            SMPProtocol.parseQueueUri(smpOnionAddress)?.let { onionServers.add(it) }
        }
        if (xftpOnionAddress.isNotBlank()) {
            SMPProtocol.parseQueueUri(xftpOnionAddress)?.let { onionServers.add(it) }
        }
        val ok = agent?.start(onionServers) ?: false
        running = ok
        onStatusChange(if (ok) "SIMPLEX_CONNECTED" else "SIMPLEX_ERROR")
        onLog("SMPAgent started: $ok (onion servers: ${onionServers.size})")
    }

    /** Остановить контроллер */
    fun stop() {
        agent?.close()
        agent = null
        running = false
        onStatusChange("SIMPLEX_STOPPED")
    }

    /** Проверить, запущен ли контроллер */
    fun isRunning(): Boolean = running

    /**
     * Сгенерировать приглашение для контакта.
     * @param type тип приглашения ("contact" или "group")
     * @return JSON-строка приглашения или null
     */
    fun generateInvitation(type: String = "contact"): String? {
        if (!running) return null
        val invite = agent?.createInvitation(type)
        if (invite != null) {
            onInvitationCreated(invite)
            onLog("Invitation created: $type")
        }
        return invite
    }

    /**
     * Подключиться к приглашению.
     * @param link JSON-строка приглашения
     * @return ID контакта или null
     */
    fun connectToInvitation(link: String): String? {
        if (!running) return null
        val contactId = agent?.connectToInvite(link, userHandle)
        onLog("Connected to invitation: ${contactId ?: "failed"}")
        return contactId
    }

    /** Отправить сообщение контакту */
    fun sendMessage(contactId: String, text: String): Boolean {
        return agent?.sendMessage(contactId, text) ?: false
    }

    /** Получить список контактов */
    fun getContacts(): List<StoredContact> {
        return agent?.getContacts()?.map { c ->
            StoredContact(
                handle = c.id,
                displayName = c.displayName,
                publicKeyB64 = Base64.encodeToString(c.e2ePublicKey, Base64.NO_WRAP),
                addedAt = System.currentTimeMillis(),
                isBlocked = c.isBlocked
            )
        } ?: emptyList()
    }

    /** Заблокировать контакт */
    fun blockContact(handle: String) {
        agent?.blockContact(handle)
        onLog("Contact blocked: $handle")
    }

    /** Разблокировать контакт */
    fun unblockContact(handle: String) {
        agent?.unblockContact(handle)
        onLog("Contact unblocked: $handle")
    }

    /** Создать группу */
    fun createGroup(name: String): String? {
        val id = agent?.createGroup(name)
        if (id != null) onLog("Group created: $name ($id)")
        return id
    }

    /** Пригласить контакт в группу */
    fun inviteToGroup(groupId: String, contactHandle: String): Boolean {
        val contact = agent?.getContact(contactHandle)
        if (contact != null) {
            val groupName = agent?.getGroups()?.find { it.id == groupId }?.name
                ?: agent?.getDecentralizedGroup(groupId)?.groupName
                ?: "group"
            val msg = JSONObject().apply {
                put("type", "group.invite")
                put("groupId", groupId)
                put("groupName", groupName)
                put("from", userHandle)
                put("fromPublicKey", Base64.encodeToString(activeKeyPair.public.encoded, Base64.NO_WRAP))
            }
            agent?.sendMessage(contactHandle, msg.toString())
            onLog("Invited $contactHandle to group $groupId")
            return true
        }
        onLog("Cannot invite $contactHandle: not found")
        return false
    }

    /** Создать канал */
    fun createChannel(name: String): String? {
        val id = agent?.createChannel(name)
        if (id != null) onLog("Channel created: $name ($id)")
        return id
    }

    /** Отправить сообщение в канал */
    fun sendChannelMessage(channelId: String, text: String): Boolean {
        val channel = agent?.getChannels()?.find { it.id == channelId }
        if (channel != null) {
            val msg = JSONObject().apply {
                put("type", "channel.msg")
                put("channelId", channelId)
                put("channelName", channel.name)
                put("text", text)
                put("from", userHandle)
            }
            agent?.sendMessage(channelId, msg.toString())
            onChannelMessage(channelId, channel.name, text)
            return true
        }
        return false
    }

    /** Включить push-уведомления для контакта */
    fun enablePushNotifications(handle: String): Boolean {
        onLog("Enable push for $handle")
        return agent?.enablePushNotifications(handle) ?: false
    }

    /** Отключить push-уведомления для контакта */
    fun disablePushNotifications(handle: String): Boolean {
        onLog("Disable push for $handle")
        return agent?.disablePushNotifications(handle) ?: false
    }

    /** Отправить файл контакту через XFTP */
    fun sendFile(handle: String, filePath: String, fileData: ByteArray): Boolean {
        onLog("Sending file $filePath to $handle")
        return agent?.sendFile(handle, filePath, fileData) ?: false
    }

    /** Принять приглашение в группу */
    fun acceptGroupInvite(inviteJson: String): String? {
        if (!running) return null
        try {
            val json = JSONObject(inviteJson)
            val groupId = json.optString("groupId", "")
            val groupName = json.optString("groupName", "")
            val inviterName = json.optString("from", "")

            if (groupId.isEmpty()) return null

            // Создаём участника (себя)
            val myMemberId = "gm_" + Base64.encodeToString(
                ByteArray(8).also { SecureRandom().nextBytes(it) },
                Base64.NO_WRAP
            )
            val myMember = GroupMember(
                memberId = myMemberId,
                displayName = userHandle,
                smpQueueUri = "",
                publicKeyB64 = Base64.encodeToString(activeKeyPair.public.encoded, Base64.NO_WRAP),
                role = MemberRole.MEMBER
            )

            val dGroup = DecentralizedGroupState(
                groupId = groupId,
                groupName = groupName,
                members = listOf(myMember),
                messageHistory = emptyList()
            )

            // Регистрируем группу и отправляем join-сообщение пригласившему
            agent?.let { a ->
                a.addDecentralizedGroup(dGroup)
                val joinMsg = JSONObject().apply {
                    put("type", "group.join")
                    put("groupId", groupId)
                    put("memberId", myMemberId)
                    put("displayName", userHandle)
                    put("smpQueueUri", "")
                    put("publicKeyB64", myMember.publicKeyB64)
                    put("role", MemberRole.MEMBER.name)
                }.toString()
                val inviterContact = a.getContacts().find { it.displayName == inviterName }
                if (inviterContact != null) {
                    a.sendMessage(inviterContact.id, joinMsg)
                }
            }

            onLog("Accepted group invite for $groupName ($groupId)")
            return groupId
        } catch (e: Exception) {
            onLog("acceptGroupInvite error: ${e.message}")
            return null
        }
    }

    /** Покинуть группу */
    fun leaveGroup(groupId: String): Boolean {
        if (!running) return false
        val agent = agent ?: return false
        val dGroup = agent.getDecentralizedGroup(groupId) ?: return false
        val myMember = dGroup.members.find { it.displayName == userHandle }
        val memberId = myMember?.memberId ?: userHandle
        val result = agent.getGroupMessageRouter().removeMemberFromGroup(groupId, memberId, agent)
        onLog("Left group $groupId")
        return result
    }

    /** Получить список участников группы */
    fun getGroupMembers(groupId: String): List<String> {
        val dGroup = agent?.getDecentralizedGroup(groupId) ?: return emptyList()
        return dGroup.members.map { it.displayName }
    }

    /** Отправить сообщение в децентрализованную группу */
    fun sendDecentralizedGroupMessage(groupId: String, text: String): Boolean {
        if (!running) return false
        val agent = agent ?: return false
        val dGroup = agent.getDecentralizedGroup(groupId) ?: return false
        return agent.getGroupMessageRouter().sendGroupMessage(dGroup, text, agent)
    }
}
