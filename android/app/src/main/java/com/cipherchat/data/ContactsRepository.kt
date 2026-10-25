package com.cipherchat.data

import com.cipherchat.core.common.AppResult
import com.cipherchat.core.network.ApiClient
import com.cipherchat.core.network.BlockDto
import com.cipherchat.core.network.BlockRequest
import com.cipherchat.core.network.ContactRequestsResponse
import com.cipherchat.core.network.CreateContactRequest
import com.cipherchat.core.network.UserDto
import com.cipherchat.core.network.apiCall

class ContactsRepository(private val api: ApiClient) {
    suspend fun contacts(): AppResult<List<UserDto>> = apiCall { api.service().contacts().contacts }
    suspend fun requests(): AppResult<ContactRequestsResponse> = apiCall { api.service().contactRequests() }
    suspend fun search(query: String): AppResult<List<UserDto>> = apiCall { api.service().searchUsers(query.trim()).users }

    /** The server answers identically whether or not the user exists, so success only means "request accepted for processing". */
    suspend fun sendRequest(userId: Long): AppResult<Unit> = apiCall { api.service().createContactRequest(CreateContactRequest(userId)) }
    suspend fun accept(requestId: Long): AppResult<Unit> = apiCall { api.service().acceptRequest(requestId) }
    suspend fun reject(requestId: Long): AppResult<Unit> = apiCall { api.service().rejectRequest(requestId) }
    suspend fun remove(userId: Long): AppResult<Unit> = apiCall { api.service().removeContact(userId) }
    suspend fun blocks(): AppResult<List<BlockDto>> = apiCall { api.service().blocks().blocks }
    suspend fun block(userId: Long): AppResult<Unit> = apiCall { api.service().block(BlockRequest(userId)) }
    suspend fun unblock(blockId: Long): AppResult<Unit> = apiCall { api.service().unblock(blockId) }
}
