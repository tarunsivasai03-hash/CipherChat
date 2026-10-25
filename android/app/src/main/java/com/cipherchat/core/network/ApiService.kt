package com.cipherchat.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/v1/health") suspend fun health(): HealthDto

    @POST("api/v1/auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("api/v1/auth/logout") suspend fun logout()
    @GET("api/v1/auth/me") suspend fun me(): MeResponse
    @PATCH("api/v1/auth/me") suspend fun updateMe(@Body body: UpdateProfileRequest): UserEnvelope

    @GET("api/v1/users/search") suspend fun searchUsers(@Query("q") query: String): UsersResponse
    @GET("api/v1/contacts") suspend fun contacts(): ContactsResponse
    @DELETE("api/v1/contacts/{id}") suspend fun removeContact(@Path("id") id: Long)
    @GET("api/v1/contact-requests") suspend fun contactRequests(): ContactRequestsResponse
    @POST("api/v1/contact-requests") suspend fun createContactRequest(@Body body: CreateContactRequest)
    @POST("api/v1/contact-requests/{id}/accept") suspend fun acceptRequest(@Path("id") id: Long)
    @POST("api/v1/contact-requests/{id}/reject") suspend fun rejectRequest(@Path("id") id: Long)
    @GET("api/v1/blocks") suspend fun blocks(): BlocksResponse
    @POST("api/v1/blocks") suspend fun block(@Body body: BlockRequest)
    @DELETE("api/v1/blocks/{id}") suspend fun unblock(@Path("id") id: Long)

    @POST("api/v1/conversations") suspend fun createConversation(@Body body: CreateConversationRequest): ConversationEnvelope
    @GET("api/v1/conversations") suspend fun conversations(): ConversationsResponse
    @GET("api/v1/conversations/{id}") suspend fun conversation(@Path("id") id: Long): ConversationEnvelope
    /** Body is a raw JsonObject so an explicit JSON null (= disable timer) is sent rather than omitted. */
    @PATCH("api/v1/conversations/{id}") suspend fun setDisappear(@Path("id") id: Long, @Body body: kotlinx.serialization.json.JsonObject): ConversationEnvelope
    @PATCH("api/v1/conversations/{id}") suspend fun rename(@Path("id") id: Long, @Body body: RenameRequest): ConversationEnvelope
    @POST("api/v1/conversations/{id}/members") suspend fun addMembers(@Path("id") id: Long, @Body body: AddMembersRequest): ConversationEnvelope
    @DELETE("api/v1/conversations/{id}/members/{userId}") suspend fun removeMember(@Path("id") id: Long, @Path("userId") userId: Long)

    @GET("api/v1/conversations/{id}/messages")
    suspend fun messages(@Path("id") id: Long, @Query("before") before: Long?, @Query("limit") limit: Int): MessagesPage

    @POST("api/v1/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") id: Long, @Body body: SendMessageRequest): MessageEnvelope

    @POST("api/v1/conversations/{id}/seen") suspend fun markSeen(@Path("id") id: Long, @Body body: SeenRequest)
    @DELETE("api/v1/messages/{id}") suspend fun deleteMessage(@Path("id") id: Long)
    @PUT("api/v1/messages/{id}/reaction") suspend fun react(@Path("id") id: Long, @Body body: ReactionRequest): ReactionsResponse
    @DELETE("api/v1/messages/{id}/reaction") suspend fun removeReaction(@Path("id") id: Long): ReactionsResponse

    @GET("api/v1/devices") suspend fun devices(): DevicesResponse
    @POST("api/v1/devices/{id}/revoke") suspend fun revokeDevice(@Path("id") id: Long): DeviceEnvelope

    @GET("api/v1/settings") suspend fun settings(): SettingsEnvelope
    @PATCH("api/v1/settings") suspend fun updateSettings(@Body body: UpdateSettingsRequest): SettingsEnvelope

    // DELETE with a body needs @HTTP.
    @retrofit2.http.HTTP(method = "DELETE", path = "api/v1/account", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)
}
