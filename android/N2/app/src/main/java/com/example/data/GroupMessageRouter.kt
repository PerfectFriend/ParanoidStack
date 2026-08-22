/**
 * Маршрутизатор групповых сообщений для децентрализованных групп.
 * Обрабатывает входящие/исходящие сообщения групп, управление участниками,
 * рассылку групповых обновлений всем участникам через SMP-протокол.
 */
package com.example.data

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Маршрутизатор сообщений децентрализованных групп.
 * Хранит состояние групп, обрабатывает приглашения, присоединение, выход
 * и рассылает сообщения всем участникам группы.
 */
class GroupMessageRouter {
    private val tag = "GroupMessageRouter"
    private val groupStates = ConcurrentHashMap<String, DecentralizedGroupState>()
    @Volatile
    private var onGroupMessage: ((groupId: String, senderId: String, text: String, timestamp: Long) -> Unit)? = null

    /** Установить callback получения группового сообщения */
    fun setOnGroupMessageCallback(callback: (groupId: String, senderId: String, text: String, timestamp: Long) -> Unit) {
        onGroupMessage = callback
    }

    /**
     * Отправить сообщение всем участникам группы.
     * Сообщение оборачивается в JSON с типом "group.msg".
     * @return true если хотя бы одно сообщение доставлено
     */
    fun sendGroupMessage(group: DecentralizedGroupState, text: String, agent: SMPAgent): Boolean {
        var anyOk = false
        val msgId = "gm_" + Base64.encodeToString(ByteArray(8).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        val timestamp = System.currentTimeMillis()
        val wrapped = JSONObject().apply {
            put("type", "group.msg")
            put("groupId", group.groupId)
            put("senderName", agent.displayName)
            put("text", text)
            put("timestamp", timestamp)
            put("messageId", msgId)
        }.toString()

        for (member in group.members) {
            val sent = agent.sendMessage(member.memberId, wrapped)
            if (sent) anyOk = true
        }

        val msg = GroupMessage(
            messageId = msgId,
            senderId = agent.displayName,
            text = text,
            timestamp = timestamp
        )
        groupStates.put(group.groupId, group.copy(
            messageHistory = group.messageHistory + msg
        ))
        return anyOk
    }

    /** Обработать входящее групповое сообщение */
    fun handleIncomingGroupMessage(json: JSONObject, agent: SMPAgent) {
        val groupId = json.optString("groupId", "")
        val text = json.optString("text", "")
        val senderName = json.optString("senderName", "unknown")
        val messageId = json.optString("messageId", "")
        val timestamp = json.optLong("timestamp", System.currentTimeMillis())

        val group = groupStates[groupId]
        if (group != null) {
            val msg = GroupMessage(
                messageId = messageId,
                senderId = senderName,
                text = text,
                timestamp = timestamp
            )
            groupStates.put(groupId, group.copy(
                messageHistory = group.messageHistory + msg
            ))
        }

        onGroupMessage?.invoke(groupId, senderName, text, timestamp)
    }

    /** Обработать присоединение участника к группе */
    fun handleGroupJoin(json: JSONObject, agent: SMPAgent) {
        val groupId = json.optString("groupId", "")
        val memberId = json.optString("memberId", "")
        val displayName = json.optString("displayName", "unknown")
        val smpQueueUri = json.optString("smpQueueUri", "")
        val publicKeyB64 = json.optString("publicKeyB64", "")
        val roleStr = json.optString("role", "MEMBER")
        val role = try { MemberRole.valueOf(roleStr) } catch (_: Exception) { MemberRole.MEMBER }

        val group = groupStates[groupId] ?: run {
            Log.w(tag, "handleGroupJoin: unknown group $groupId")
            return
        }
        val existingIds = group.members.map { it.memberId }.toSet()
        if (memberId !in existingIds) {
            val newMember = GroupMember(memberId, displayName, smpQueueUri, publicKeyB64, role)
            groupStates.put(groupId, group.copy(members = group.members + newMember))
            Log.i(tag, "Member $displayName ($memberId) joined group ${group.groupName}")
        }
    }

    /** Обработать выход участника из группы */
    fun handleGroupLeave(json: JSONObject, agent: SMPAgent) {
        val groupId = json.optString("groupId", "")
        val memberId = json.optString("memberId", "")

        val group = groupStates[groupId] ?: run {
            Log.w(tag, "handleGroupLeave: unknown group $groupId")
            return
        }
        groupStates.put(groupId, group.copy(
            members = group.members.filter { it.memberId != memberId }
        ))
        Log.i(tag, "Member $memberId left group ${group.groupName}")
    }

    /** Добавить участника в группу и оповестить остальных */
    fun addMemberToGroup(groupId: String, member: GroupMember, agent: SMPAgent): Boolean {
        val group = groupStates[groupId] ?: return false
        groupStates.put(groupId, group.copy(members = group.members + member))

        val joinMsg = JSONObject().apply {
            put("type", "group.join")
            put("groupId", groupId)
            put("memberId", member.memberId)
            put("displayName", member.displayName)
            put("smpQueueUri", member.smpQueueUri)
            put("publicKeyB64", member.publicKeyB64)
            put("role", member.role.name)
        }.toString()

        var anyOk = false
        for (m in group.members) {
            if (m.memberId != member.memberId) {
                if (agent.sendMessage(m.memberId, joinMsg)) anyOk = true
            }
        }
        return anyOk
    }

    /** Удалить участника из группы и оповестить остальных */
    fun removeMemberFromGroup(groupId: String, memberId: String, agent: SMPAgent): Boolean {
        val group = groupStates[groupId] ?: return false
        val updatedMembers = group.members.filter { it.memberId != memberId }
        groupStates.put(groupId, group.copy(members = updatedMembers))

        val leaveMsg = JSONObject().apply {
            put("type", "group.leave")
            put("groupId", groupId)
            put("memberId", memberId)
        }.toString()

        var anyOk = false
        for (m in updatedMembers) {
            if (agent.sendMessage(m.memberId, leaveMsg)) anyOk = true
        }
        return anyOk
    }

    /** Разослать всем участникам обновление информации о группе */
    fun notifyGroupUpdate(groupId: String, agent: SMPAgent) {
        val group = groupStates[groupId] ?: return
        val membersJson = JSONArray()
        for (m in group.members) {
            membersJson.put(JSONObject().apply {
                put("memberId", m.memberId)
                put("displayName", m.displayName)
                put("role", m.role.name)
            })
        }
        val updateMsg = JSONObject().apply {
            put("type", "group.update")
            put("groupId", groupId)
            put("groupName", group.groupName)
            put("members", membersJson)
        }.toString()
        for (m in group.members) {
            agent.sendMessage(m.memberId, updateMsg)
        }
    }

    fun getGroup(groupId: String): DecentralizedGroupState? = groupStates[groupId]

    fun setGroup(groupId: String, state: DecentralizedGroupState) {
        groupStates.put(groupId, state)
    }

    fun getAllGroups(): Map<String, DecentralizedGroupState> = groupStates.toMap()
}
