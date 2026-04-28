package com.atomictrxn.conduit.worker

import com.atomictrxn.conduit.data.api.models.ChatListItem
import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.test.FakeConduitRepository
import com.atomictrxn.conduit.test.FakeOpenWebUIService
import com.atomictrxn.conduit.test.FakeOpenWebUIServiceFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPollerTest {
    @Test
    fun noApiKeyReturnsSuccessAndDoesNotCallApi() =
        runTest {
            val factory = FakeOpenWebUIServiceFactory()
            val poller =
                NotificationPoller(
                    FakeConduitRepository(initialConfig = ServerConfig("https://openwebui.example.com", "")),
                    factory,
                )

            val result = poller.poll(nowEpochSeconds = NOW)

            assertSuccessWithChatCount(result, 0)
            assertEquals(0, factory.createCalls)
        }

    @Test
    fun notificationsDisabledReturnsSuccessAndDoesNotCallApi() =
        runTest {
            val factory = FakeOpenWebUIServiceFactory()
            val poller =
                NotificationPoller(
                    FakeConduitRepository(
                        initialConfig = ServerConfig("https://openwebui.example.com", "api-key"),
                        notificationsEnabled = false,
                    ),
                    factory,
                )

            val result = poller.poll(nowEpochSeconds = NOW)

            assertSuccessWithChatCount(result, 0)
            assertEquals(0, factory.createCalls)
        }

    @Test
    fun firstRunRecordsTimestampAndSendsNoNotifications() =
        runTest {
            val repository =
                FakeConduitRepository(
                    initialConfig = ServerConfig("https://openwebui.example.com", "api-key"),
                    lastNotificationCheck = 0L,
                )
            val poller = NotificationPoller(repository, FakeOpenWebUIServiceFactory())

            val result = poller.poll(nowEpochSeconds = NOW)

            assertSuccessWithChatCount(result, 0)
            assertEquals(NOW, repository.currentLastNotificationCheck)
        }

    @Test
    fun updatedChatsReturnUpToTenNotifications() =
        runTest {
            val chats = (1..12).map { ChatListItem(id = "chat-$it", title = "Chat $it", updatedAt = 200L + it) }
            val poller =
                NotificationPoller(
                    FakeConduitRepository(
                        initialConfig = ServerConfig("https://openwebui.example.com", "api-key"),
                        lastNotificationCheck = 100L,
                    ),
                    FakeOpenWebUIServiceFactory(FakeOpenWebUIService(chatsProvider = { chats })),
                )

            val result = poller.poll(nowEpochSeconds = NOW)

            assertTrue(result is NotificationPollResult.Success)
            assertEquals(chats.take(10), (result as NotificationPollResult.Success).chats)
        }

    @Test
    fun apiFailureReturnsRetryAndDoesNotAdvanceTimestamp() =
        runTest {
            val repository =
                FakeConduitRepository(
                    initialConfig = ServerConfig("https://openwebui.example.com", "api-key"),
                    lastNotificationCheck = 100L,
                )
            val poller =
                NotificationPoller(
                    repository,
                    FakeOpenWebUIServiceFactory(
                        FakeOpenWebUIService(
                            chatsProvider = {
                                error("boom")
                            },
                        ),
                    ),
                )

            val result = poller.poll(nowEpochSeconds = NOW)

            assertEquals(NotificationPollResult.Retry, result)
            assertEquals(100L, repository.currentLastNotificationCheck)
        }

    private fun assertSuccessWithChatCount(
        result: NotificationPollResult,
        count: Int,
    ) {
        assertTrue(result is NotificationPollResult.Success)
        assertEquals(count, (result as NotificationPollResult.Success).chats.size)
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
