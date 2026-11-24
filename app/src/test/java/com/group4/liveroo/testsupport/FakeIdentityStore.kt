package com.group4.liveroo.testsupport

import com.group4.liveroo.data.prefs.IdentityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory identity for one simulated phone. */
class FakeIdentityStore(id: String, name: String) : IdentityStore {
    private val id = MutableStateFlow(id)
    private val name = MutableStateFlow(name)
    private val onboardedFlow = MutableStateFlow(true)

    override val userId: Flow<String> = this.id
    override val displayName: Flow<String> = this.name
    override val onboarded: Flow<Boolean> = onboardedFlow

    override suspend fun ensureId(): String = id.value

    override suspend fun setName(name: String) {
        this.name.value = name.trim()
        onboardedFlow.value = true
    }

    override suspend fun me(): Pair<String, String> = id.value to name.value
}
