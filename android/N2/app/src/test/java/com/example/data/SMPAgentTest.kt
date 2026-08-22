package com.example.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SMPAgentTest {

    private val identity = SMPIdentity(
        keyPair = NaClCrypto.generateKeyPair(),
        displayName = "TestUser"
    )

    private fun createAgent(): SMPAgent = SMPAgent(
        identity = identity,
        onMessageReceived = { _, _ -> },
        onContactRequest = { },
        onError = { },
        profileId = "test_profile"
    )

    @Test
    fun testSMPIdentityDataClass() {
        assertEquals("TestUser", identity.displayName)
        assertTrue(identity.publicKeyB64.isNotEmpty())
        assertTrue(identity.publicKeyX509.isNotEmpty())
        assertTrue(identity.privateKeyX509.isNotEmpty())
    }

    @Test
    fun testCreateGroup() {
        val agent = createAgent()
        val id = agent.createGroup("Test Group")
        assertNotNull(id)
        assertTrue(id.startsWith("grp_"))
        val groups = agent.getGroups()
        assertEquals(1, groups.size)
        assertEquals("Test Group", groups[0].name)
    }

    @Test
    fun testCreateMultipleGroups() {
        val agent = createAgent()
        val id1 = agent.createGroup("Group A")
        val id2 = agent.createGroup("Group B")
        assertNotEquals(id1, id2)
        assertEquals(2, agent.getGroups().size)
    }

    @Test
    fun testCreateChannel() {
        val agent = createAgent()
        val id = agent.createChannel("Test Channel")
        assertNotNull(id)
        assertTrue(id.startsWith("ch_"))
        val channels = agent.getChannels()
        assertEquals(1, channels.size)
        assertEquals("Test Channel", channels[0].name)
    }

    @Test
    fun testCreateChannelWithTopic() {
        val agent = createAgent()
        agent.createChannel("News", "tech")
        val ch = agent.getChannels()[0]
        assertEquals("News", ch.name)
        assertEquals("tech", ch.topic)
    }

    @Test
    fun testGetContactsInitiallyEmpty() {
        val agent = createAgent()
        assertTrue(agent.getContacts().isEmpty())
    }

    @Test
    fun testGetChannelsInitiallyEmpty() {
        val agent = createAgent()
        assertTrue(agent.getChannels().isEmpty())
    }

    @Test
    fun testGetGroupsInitiallyEmpty() {
        val agent = createAgent()
        assertTrue(agent.getGroups().isEmpty())
    }

    @Test
    fun testGroupRoutingInfo() {
        val agent = createAgent()
        val info = GroupRoutingInfo("group1", "member1", "routingKey1")
        agent.addGroupRoutingInfo(info)
        val retrieved = agent.getGroupRoutingInfoByMember("group1", "member1")
        assertNotNull(retrieved)
        assertEquals("routingKey1", retrieved!!.routingKey)
    }

    @Test
    fun testGroupRoutingInfoNoDuplicate() {
        val agent = createAgent()
        agent.addGroupRoutingInfo(GroupRoutingInfo("g1", "m1", "k1"))
        agent.addGroupRoutingInfo(GroupRoutingInfo("g1", "m1", "k2")) // same member, should not add
        val list = agent.getGroupRoutingInfoByMember("g1", "m1")
        assertEquals("k1", list!!.routingKey) // first one preserved
    }

    @Test
    fun testGroupRoutingInfoDifferentMembers() {
        val agent = createAgent()
        agent.addGroupRoutingInfo(GroupRoutingInfo("g1", "m1", "k1"))
        agent.addGroupRoutingInfo(GroupRoutingInfo("g1", "m2", "k2"))
        val r1 = agent.getGroupRoutingInfoByMember("g1", "m1")
        val r2 = agent.getGroupRoutingInfoByMember("g1", "m2")
        assertEquals("k1", r1!!.routingKey)
        assertEquals("k2", r2!!.routingKey)
    }

    @Test
    fun testGroupRoutingInfoNotFound() {
        val agent = createAgent()
        assertNull(agent.getGroupRoutingInfoByMember("nonexistent", "any"))
    }

    @Test
    fun testBlockContactNoCrash() {
        val agent = createAgent()
        agent.blockContact("nonexistent")
        // should not throw
    }

    @Test
    fun testUnblockContactNoCrash() {
        val agent = createAgent()
        agent.unblockContact("nonexistent")
        // should not throw
    }

    @Test
    fun testCreateInvitationReturnsNullWhenNotConnected() {
        val agent = createAgent()
        assertNull(agent.createInvitation("contact"))
    }

    @Test
    fun testConnectToInviteReturnsNullWhenNotConnected() {
        val agent = createAgent()
        assertNull(agent.connectToInvite("{}"))
    }

    @Test
    fun testSendMessageToNonexistentContact() {
        val agent = createAgent()
        assertFalse(agent.sendMessage("nonexistent", "hello"))
    }

    @Test
    fun testCloseDoesNotThrow() {
        val agent = createAgent()
        agent.close()
    }

    @Test
    fun testDecentralizedGroupOperations() {
        val agent = createAgent()
        assertNull(agent.getDecentralizedGroup("nonexistent"))
        assertTrue(agent.getDecentralizedGroups().isEmpty())

        val state = DecentralizedGroupState(
            groupId = "dg1",
            groupName = "Decentralized Group",
            members = emptyList(),
            messageHistory = emptyList()
        )
        agent.addDecentralizedGroup(state)
        assertNotNull(agent.getDecentralizedGroup("dg1"))
        assertEquals(1, agent.getDecentralizedGroups().size)
    }

    @Test
    fun testDisplayName() {
        val agent = createAgent()
        assertEquals("TestUser", agent.displayName)
    }

    @Test
    fun testPublicKeyB64() {
        val agent = createAgent()
        assertEquals(identity.publicKeyB64, agent.publicKeyB64)
    }

    @Test
    fun testSMPDataClasses() {
        val contact = SMPContact(
            id = "ct_123",
            displayName = "Alice",
            serverUri = SMPQueueURI("id", "host.com", 5223, ByteArray(8)),
            recipientId = ByteArray(16),
            senderId = ByteArray(16),
            e2ePublicKey = ByteArray(32),
            isBlocked = false
        )
        assertEquals("ct_123", contact.id)
        assertEquals("Alice", contact.displayName)
        assertFalse(contact.isBlocked)

        val group = SMPGroup("g1", "Group", mutableListOf(SMPGroupMember("m1", "Bob")))
        assertEquals("g1", group.id)
        assertEquals(1, group.members.size)

        val channel = SMPChannel("ch1", "Channel", "topic")
        assertEquals("ch1", channel.id)
        assertEquals("topic", channel.topic)
    }
}
