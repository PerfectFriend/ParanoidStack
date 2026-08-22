/**
 * SMP-агент — высокоуровневый клиент для работы с SMP-протоколом.
 * Управляет соединениями с SMP-серверами, очередями, контактами,
 * группами, каналами, сквозным шифрованием (E2EE) и файлами (XFTP).
 *
 * Агент является центральным компонентом для обмена сообщениями
 * в децентрализованной сети SimpleX.
 *
 * Architecture role:
 *   Orchestrates the complete messaging lifecycle. It manages SMP server sessions (one per server),
 *   maintains contact/group/channel state per profile, handles E2EE key exchange and message
 *   encryption via NaClCrypto, routes group messages through GroupMessageRouter, and coordinates
 *   file transfers via XFTPClient.
 *
 *   The agent pattern decouples the high-level messaging API (used by SimpleXEmbeddedController)
 *   from the low-level SMPClient protocol implementation. Inbound messages are classified by type
 *   (contact-confirm, chat.msg, group.msg, group.join, group.leave) and dispatched appropriately.
 */
package com.example.data

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Идентификация пользователя в сети SimpleX */
data class SMPIdentity(
    val keyPair: KeyPair,                   // долговременная ключевая пара
    val displayName: String = "anonymous"   // отображаемое имя
) {
    val publicKeyX509: ByteArray get() = keyPair.public.encoded
    val privateKeyX509: ByteArray get() = keyPair.private.encoded
    val publicKeyB64: String get() = Base64.encodeToString(publicKeyX509, Base64.NO_WRAP)
}

/** Контакт в сети SimpleX */
data class SMPContact(
    var id: String,                          // ID контакта
    var displayName: String,                 // отображаемое имя
    var serverUri: SMPQueueURI,              // URI очереди
    var recipientId: ByteArray,              // ID получателя
    var senderId: ByteArray,                 // ID отправителя
    var e2ePublicKey: ByteArray,             // публичный ключ E2EE
    var isBlocked: Boolean = false           // заблокирован?
)

/** Группа в сети SimpleX */
data class SMPGroup(
    var id: String,
    var name: String,
    var members: MutableList<SMPGroupMember> = mutableListOf()
)

/** Участник группы */
data class SMPGroupMember(
    var memberId: String,
    var displayName: String,
    var contact: SMPContact? = null
)

/** Канал в сети SimpleX */
data class SMPChannel(
    var id: String,
    var name: String,
    var topic: String = ""
)

/** Информация о маршрутизации в группе */
data class GroupRoutingInfo(
    val groupId: String,
    val memberId: String,
    val routingKey: String
)

/** Сессия с SMP-сервером */
data class SMPServerSession(
    val client: SMPClient,                                    // SMP-клиент
    val queues: MutableList<QueueInfo> = mutableListOf()      // очереди на сервере
) {
    /** Информация об очереди на сервере */
    data class QueueInfo(
        val recipientId: ByteArray,   // ID получателя
        val senderId: ByteArray,      // ID отправителя
        val serverDhKey: ByteArray,   // ключ DH сервера
        val e2eKey: ByteArray,        // ключ E2EE
        val sndSecure: Boolean,       // безопасная отправка
        var secured: Boolean = false  // флаг установки безопасности
    )
}

/** Главный SMP-агент для обмена сообщениями */
class SMPAgent(
    private val identity: SMPIdentity,                    // наша идентификация
    private val onMessageReceived: (fromId: String, message: String) -> Unit,  // получено сообщение
    private val onContactRequest: (inviteJson: String) -> Unit,                // запрос контакта
    private val onError: (error: String) -> Unit,                              // ошибка
    private val profileId: String = "default"                                  // ID профиля
) {
    private val tag = "SMPAgent"
    private val sessions = ConcurrentHashMap<String, SMPServerSession>()  // сессии к серверам
    private val contactsByProfile = ConcurrentHashMap<String, MutableList<SMPContact>>()
    private val groupsByProfile = ConcurrentHashMap<String, MutableList<SMPGroup>>()
    private val channelsByProfile = ConcurrentHashMap<String, MutableList<SMPChannel>>()
    private val authKeys = ConcurrentHashMap<String, java.security.KeyPair>()  // ключи авторизации
    private val groupMessageRouter = GroupMessageRouter()  // маршрутизатор групп
    private val groupRoutingInfo = ConcurrentHashMap<String, MutableList<GroupRoutingInfo>>()

    private fun c(): MutableList<SMPContact> = contactsByProfile.getOrPut(profileId) { mutableListOf() }
    private fun g(): MutableList<SMPGroup> = groupsByProfile.getOrPut(profileId) { mutableListOf() }
    private fun h(): MutableList<SMPChannel> = channelsByProfile.getOrPut(profileId) { mutableListOf() }

    val displayName: String get() = identity.displayName
    val publicKeyB64: String get() = identity.publicKeyB64

    /** Запустить агента с серверами по умолчанию */
    fun start(): Boolean {
        return start(emptyList())
    }

    /** Запустить агента с указанными onion-серверами */
    fun start(additionalServers: List<SMPQueueURI>): Boolean {
        val servers = SMPProtocol.DEFAULT_SERVERS + additionalServers
        return connectToServers(servers)
    }

    /** Подключиться к SMP-серверам (до 3) */
    private fun connectToServers(servers: List<SMPQueueURI>): Boolean {
        var anyOk = false
        for (srv in servers.take(3)) {
            if (sessions.containsKey(srv.host)) continue
            val client = SMPClient(srv, onServerMessage = { resp -> handleServerMessage(srv, resp) }, onError = { e ->
                Log.e(tag, "SMP ${srv.host}: ${e.message}")
            })
            client.connect().onSuccess {
                client.startReader()
                sessions[srv.host] = SMPServerSession(client)
                anyOk = true
                Log.i(tag, "Connected to ${srv.host}")
            }
        }
        return anyOk
    }

    /**
     * Создать приглашение для контакта.
     * @param type тип приглашения ("contact", "group")
     * @return JSON-строка приглашения
     */
    fun createInvitation(type: String = "contact"): String? {
        val serverEntry = sessions.values.firstOrNull() ?: return null
        val client = serverEntry.client
        val rAuthKey = NaClCrypto.generateKeyPair()
        val rDhKey = NaClCrypto.generateKeyPair()
        val createResult = client.createQueue(rAuthKey.public.encoded, rDhKey.public.encoded, sndSecure = true)
        val qData = when (createResult) {
            is AppResult.Success -> createResult.data
            is AppResult.Error -> return null
        }
        val qUri = SMPQueueURI(
            serverIdentity = client.server.serverIdentity,
            host = client.server.host,
            port = client.server.port,
            queueId = qData.senderId,
            dhPublicKey = rDhKey.public.encoded,
            sndSecure = true
        )
        val queueInfo = SMPServerSession.QueueInfo(
            recipientId = qData.recipientId,
            senderId = qData.senderId,
            serverDhKey = qData.serverDhKey,
            e2eKey = rAuthKey.public.encoded,
            sndSecure = qData.canSndSecure
        )
        serverEntry.queues.add(queueInfo)
        authKeys[qUri.toUri()] = rAuthKey  // will be overwritten if concurrent — acceptable for contact setup

        val invite = JSONObject().apply {
            put("type", type)
            put("smp", qUri.toUri())
            put("displayName", identity.displayName)
            put("publicKey", identity.publicKeyB64)
        }
        return invite.toString()
    }

    /**
     * Подключиться к приглашению (создать контакт).
     * @param inviteJson JSON приглашения
     * @param myLabel наша метка
     * @return ID контакта
     */
    fun connectToInvite(inviteJson: String, myLabel: String = "contact"): String? {
        val invite = try { JSONObject(inviteJson) } catch (e: Exception) { return null }
        val smpStr = try { invite.getString("smp") } catch (e: Exception) { return null }
        val qUri = SMPProtocol.parseQueueUri(smpStr) ?: return null
        val peerName = invite.optString("displayName", "unknown")
        val peerKeyB64 = invite.optString("publicKey", "")

        val serverEntry = sessions.values.firstOrNull() ?: return null
        val client = serverEntry.client
        val myAuthKey = NaClCrypto.generateKeyPair()
        val myDhKey = NaClCrypto.generateKeyPair()
        val createResult = client.createQueue(myAuthKey.public.encoded, myDhKey.public.encoded, sndSecure = true)
        val qData = when (createResult) {
            is AppResult.Success -> createResult.data
            is AppResult.Error -> return null
        }

        val myQueueUri = SMPQueueURI(
            serverIdentity = client.server.serverIdentity,
            host = client.server.host,
            port = client.server.port,
            queueId = qData.senderId,
            dhPublicKey = myDhKey.public.encoded,
            sndSecure = true
        )
        val queueInfo = SMPServerSession.QueueInfo(
            recipientId = qData.recipientId,
            senderId = qData.senderId,
            serverDhKey = qData.serverDhKey,
            e2eKey = myAuthKey.public.encoded,
            sndSecure = qData.canSndSecure
        )
        serverEntry.queues.add(queueInfo)

        // Отправляем подтверждение контакта
        val confirmMsg = JSONObject().apply {
            put("type", "contact-confirm")
            put("smpReply", myQueueUri.toUri())
            put("displayName", identity.displayName)
            put("publicKey", identity.publicKeyB64)
        }
        val msgBody = confirmMsg.toString().encodeToByteArray()
        val sent = client.sendMessage(qUri.queueId, msgBody)
        if (sent is AppResult.Error) return null

        val contact = SMPContact(
            id = qUri.toUri().hashCode().toString(),
            displayName = peerName,
            serverUri = qUri,
            recipientId = qData.recipientId,
            senderId = qData.senderId,
            e2ePublicKey = Base64.decode(peerKeyB64, Base64.NO_WRAP)
        )
        synchronized(c()) { c().add(contact) }
        return contact.id
    }

    /**
     * Отправить сообщение контакту.
     * @param contactId ID контакта
     * @param text текст сообщения
     * @return true если отправлено
     */
    fun sendMessage(contactId: String, text: String): Boolean {
        val contact = synchronized(c()) { c().find { it.id == contactId } } ?: return false
        val serverEntry = sessions[contact.serverUri.host] ?: return false
        val queueInfo = serverEntry.queues.find { q -> q.senderId.contentEquals(contact.senderId) }
        val authKey = authKeys[contact.serverUri.toUri()]

        val msgJson = JSONObject().apply {
            put("type", "chat.msg")
            put("from", identity.displayName)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }
        val msgBody = msgJson.toString().encodeToByteArray()
        // Шифрование E2EE, если есть ключ контакта
        // Uses NaCl crypto_box (XSalsa20-Poly1305) with an ephemeral DH key exchange:
        // 1. Generate ephemeral keypair for this message
        // 2. Compute shared secret via cryptoBoxBeforeNm (HSalsa20 on X25519 DH result)
        // 3. Encrypt with cryptoBoxAfterNm using the shared subKey and correlation ID as nonce
        val encrypted = if (contact.e2ePublicKey.isNotEmpty()) {
            val myKey = NaClCrypto.generateKeyPair()
            val subKey = NaClCrypto.cryptoBoxBeforeNm(contact.e2ePublicKey, myKey.private.encoded)
            NaClCrypto.cryptoBoxAfterNm(msgBody, SMPProtocol.generateCorrId(), subKey)
        } else msgBody

        val authBytes = if (authKey != null) authKey.private.encoded else ByteArray(0)
        return when (serverEntry.client.sendMessage(
            contact.serverUri.queueId,
            encrypted,
            authBytes
        )) {
            is AppResult.Success -> true
            is AppResult.Error -> false
        }
    }

    /** Отправить сообщение в группу */
    fun sendGroupMessage(groupId: String, text: String): Boolean {
        val dGroup = groupMessageRouter.getGroup(groupId)
        if (dGroup != null) {
            return groupMessageRouter.sendGroupMessage(dGroup, text, this)
        }
        val group = synchronized(g()) { g().find { it.id == groupId } } ?: return false
        var anyOk = false
        for (member in group.members) {
            member.contact?.let { if (sendMessage(it.id, text)) anyOk = true }
        }
        return anyOk
    }

    /** Создать группу */
    fun createGroup(name: String): String {
        val id = "grp_" + Base64.encodeToString(ByteArray(8).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        synchronized(g()) { g().add(SMPGroup(id, name)) }
        return id
    }

    /** Создать канал */
    fun createChannel(name: String, topic: String = ""): String {
        val id = "ch_" + Base64.encodeToString(ByteArray(8).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        synchronized(h()) { h().add(SMPChannel(id, name, topic)) }
        return id
    }

    /** Заблокировать контакт */
    fun blockContact(contactId: String) {
        synchronized(c()) {
            c().find { it.id == contactId }?.let {
                it.isBlocked = true
                val srv = sessions[it.serverUri.host]
                srv?.client?.deleteQueue(it.serverUri.queueId)
            }
        }
    }

    /** Разблокировать контакт */
    fun unblockContact(contactId: String) {
        synchronized(c()) { c().find { it.id == contactId }?.isBlocked = false }
    }

    fun getContact(contactId: String): SMPContact? = synchronized(c()) { c().find { it.id == contactId } }
    fun getContacts(): List<SMPContact> = synchronized(c()) { c().toList() }
    fun getGroups(): List<SMPGroup> = synchronized(g()) { g().toList() }
    fun getChannels(): List<SMPChannel> = synchronized(h()) { h().toList() }

    fun getGroupMessageRouter(): GroupMessageRouter = groupMessageRouter

    fun getDecentralizedGroup(groupId: String): DecentralizedGroupState? = groupMessageRouter.getGroup(groupId)
    fun getDecentralizedGroups(): List<DecentralizedGroupState> = groupMessageRouter.getAllGroups().values.toList()

    fun addDecentralizedGroup(state: DecentralizedGroupState) {
        groupMessageRouter.setGroup(state.groupId, state)
    }

    fun setOnGroupMessageCallback(callback: (groupId: String, senderId: String, text: String, timestamp: Long) -> Unit) {
        groupMessageRouter.setOnGroupMessageCallback(callback)
    }

    fun addGroupRoutingInfo(info: GroupRoutingInfo) {
        synchronized(groupRoutingInfo) {
            val list = groupRoutingInfo.getOrPut(info.groupId) { mutableListOf() }
            if (list.none { it.memberId == info.memberId }) {
                list.add(info)
            }
        }
    }

    fun getGroupRoutingInfoByMember(groupId: String, memberId: String): GroupRoutingInfo? =
        synchronized(groupRoutingInfo) {
            groupRoutingInfo[groupId]?.find { it.memberId == memberId }
        }

    /** Включить push-уведомления для контакта */
    fun enablePushNotifications(contactId: String): Boolean {
        val contact = synchronized(c()) { c().find { it.id == contactId } } ?: return false
        val srv = sessions[contact.serverUri.host] ?: return false
        return when (srv.client.enableNotifications(contact.serverUri.queueId)) {
            is AppResult.Success -> true
            is AppResult.Error -> false
        }
    }

    /** Отключить push-уведомления */
    fun disablePushNotifications(contactId: String): Boolean {
        val contact = synchronized(c()) { c().find { it.id == contactId } } ?: return false
        val srv = sessions[contact.serverUri.host] ?: return false
        return when (srv.client.disableNotifications(contact.serverUri.queueId)) {
            is AppResult.Success -> true
            is AppResult.Error -> false
        }
    }

    private val xftpSessions = mutableMapOf<String, XFTPClient>()

    /** Отправить файл контакту через XFTP (reuses cached connections) */
    fun sendFile(contactId: String, filePath: String, fileData: ByteArray): Boolean {
        try {
            val serverKey = SMPProtocol.DEFAULT_SERVERS.firstOrNull()?.host ?: return false
            // Reuse or create XFTPClient for this server
            val srv = xftpSessions.getOrPut(serverKey) {
                XFTPClient(XFTPServer(
                    serverIdentity = SMPProtocol.DEFAULT_SERVERS.firstOrNull()?.serverIdentity ?: "",
                    host = serverKey,
                    port = 443
                ))
            }
            if (!srv.isConnected && !srv.connect()) return false
            val sndKeyPair = NaClCrypto.generateKeyPair()
            val rcvKeyPair = NaClCrypto.generateKeyPair()
            val sndKey = sndKeyPair.public.encoded
            val rcvPubKey = rcvKeyPair.public.encoded
            val rcvPrivKey = rcvKeyPair.private.encoded
            srv.addDecryptionKey(rcvPubKey, rcvPrivKey)
            val digest = MessageDigest.getInstance("SHA-256").digest(fileData)
            val result = srv.registerChunk(sndKey, listOf(rcvPubKey), fileData.size, digest)
            if (result == null) { srv.disconnect(); return false }
            if (!srv.uploadChunk(result.senderId, fileData)) return false
            // Don't disconnect — connection is cached for reuse

            // Send a file description message to the contact over SMP
            // The description includes the chunk ID, recipient key, server info, and SHA-256 digest
            // so the recipient can download and decrypt the file via XFTP
            val contact = synchronized(c()) { c().find { it.id == contactId } } ?: return false
            val fileDesc = JSONObject().apply {
                put("type", "xftp.file")
                put("fileName", filePath)
                put("fileSize", fileData.size)
                put("digest", Base64.encodeToString(digest, Base64.NO_WRAP))
                put("chunkId", Base64.encodeToString(result.senderId, Base64.NO_WRAP))
                put("rcvKey", Base64.encodeToString(rcvPubKey, Base64.NO_WRAP))
                put("encryptedKey", "" ) // recipient derives key out-of-band
                put("serverHost", srv.server.host)
                put("serverIdentity", srv.server.serverIdentity)
            }
            return sendMessage(contactId, fileDesc.toString())
        } catch (e: Exception) {
            Log.e(tag, "sendFile error", e)
            return false
        }
    }

    /** Синхронизировать ожидающие сообщения — подписаться на все очереди контактов */
    fun syncPendingMessages(): Int {
        var synced = 0
        val contactList = synchronized(c()) { c().toList() }
        for (contact in contactList) {
            if (contact.isBlocked) continue
            val srv = sessions[contact.serverUri.host] ?: continue
            val qInfo = srv.queues.find { q ->
                q.senderId.contentEquals(contact.senderId) || q.recipientId.contentEquals(contact.recipientId)
            } ?: continue
            try {
                when (srv.client.subscribe(qInfo.recipientId)) {
                    is AppResult.Success -> {
                        synced++
                        Log.i(tag, "Synced queue for ${contact.displayName}")
                    }
                    is AppResult.Error -> Log.w(tag, "Subscribe failed for ${contact.displayName}")
                }
            } catch (e: Exception) {
                Log.w(tag, "Subscribe error for ${contact.displayName}", e)
            }
        }
        return synced
    }

    /** Закрыть агента — отключить все сессии и очистить ресурсы */
    fun close() {
        for ((_, session) in sessions) {
            session.client.sendPing()
            session.client.disconnect()
        }
        sessions.clear()
        for ((_, xc) in xftpSessions) xc.disconnect()
        xftpSessions.clear()
    }

    /** Обработать входящее сообщение от сервера */
    private fun handleServerMessage(server: SMPQueueURI, resp: SMPProtocol.ParsedResponse) {
        when (resp.command) {
            "MSG" -> handleMessageDelivery(server, resp)
            "END" -> Log.i(tag, "Subscription ended on $server")
            "OK" -> Log.v(tag, "OK")
            else -> if (resp.command.startsWith("ERR")) {
                Log.w(tag, "Server error: ${resp.command} on $server")
            }
        }
    }

    /** Обработать доставку сообщения */
    private fun handleMessageDelivery(server: SMPQueueURI, resp: SMPProtocol.ParsedResponse) {
        val data = resp.params
        try {
            val result = SMPProtocol.decodePaddedString(data, 9) // flags(1) + timestamp(8)
            val msg = result.first
            val msgStr = msg.decodeToString()
            processIncomingMessage(msgStr)
            sessions[server.host]?.client?.ackMessage(resp.entityId)
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse message", e)
        }
    }

    /** Классифицировать и обработать входящее сообщение */
    private fun processIncomingMessage(msgStr: String) {
        try {
            val json = JSONObject(msgStr)
            when (json.optString("type")) {
                "contact-confirm" -> handleContactConfirm(json, msgStr)
                "chat.msg" -> handleChatMessage(json)
                "group.msg" -> groupMessageRouter.handleIncomingGroupMessage(json, this)
                "group.join" -> groupMessageRouter.handleGroupJoin(json, this)
                "group.leave" -> groupMessageRouter.handleGroupLeave(json, this)
                else -> Log.d(tag, "Unknown message type: ${json.optString("type")}")
            }
        } catch (e: Exception) {
            Log.w(tag, "process message error", e)
        }
    }

    /** Обработать подтверждение контакта */
    private fun handleContactConfirm(json: JSONObject, rawMsg: String) {
        val qUriStr = json.optString("smpReply", "")
        val qUri = SMPProtocol.parseQueueUri(qUriStr) ?: return
        val peerName = json.optString("displayName", "unknown")
        val peerKey = json.optString("publicKey", "")
        val contactId = "ct_${qUri.hashCode().toString(16)}"
        val contact = SMPContact(
            id = contactId,
            displayName = peerName,
            serverUri = qUri,
            recipientId = ByteArray(0),
            senderId = ByteArray(0),
            e2ePublicKey = Base64.decode(peerKey, Base64.NO_WRAP)
        )
        synchronized(c()) { c().add(contact) }
        onContactRequest(rawMsg)
    }

    /** Обработать чат-сообщение */
    private fun handleChatMessage(json: JSONObject) {
        val from = json.optString("from", "unknown")
        val text = json.optString("text", "")
        val contact = synchronized(c()) { c().find { it.displayName == from } }
        val fromId = contact?.id ?: from
        onMessageReceived(fromId, text)
    }
}
