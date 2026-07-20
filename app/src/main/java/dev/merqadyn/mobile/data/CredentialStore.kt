package dev.merqadyn.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ConnectionProfile(
    val serverUrl: String,
    val merchantId: String,
    val deviceId: String,
    val deviceToken: String,
)

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<ConnectionProfile?> = _profile

    @Synchronized
    fun save(profile: ConnectionProfile) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(profile.deviceId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(profile.deviceToken.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(SERVER_URL, profile.serverUrl)
            .putString(MERCHANT_ID, profile.merchantId)
            .putString(DEVICE_ID, profile.deviceId)
            .putString(TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(TOKEN_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
        _profile.value = profile
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
        _profile.value = null
    }

    private fun read(): ConnectionProfile? = try {
        val serverUrl = preferences.getString(SERVER_URL, null) ?: return null
        val merchantId = preferences.getString(MERCHANT_ID, null) ?: return null
        val deviceId = preferences.getString(DEVICE_ID, null) ?: return null
        val iv = Base64.decode(preferences.getString(TOKEN_IV, null) ?: return null, Base64.NO_WRAP)
        val encrypted = Base64.decode(preferences.getString(TOKEN_VALUE, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(deviceId.toByteArray(Charsets.UTF_8))
        val token = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        ConnectionProfile(serverUrl, merchantId, deviceId, token)
    } catch (_: Exception) {
        preferences.edit().clear().apply()
        null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "merqadyn-secure-profile"
        const val SERVER_URL = "server-url"
        const val MERCHANT_ID = "merchant-id"
        const val DEVICE_ID = "device-id"
        const val TOKEN_IV = "token-iv"
        const val TOKEN_VALUE = "token-value"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "merqadyn-device-token-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
