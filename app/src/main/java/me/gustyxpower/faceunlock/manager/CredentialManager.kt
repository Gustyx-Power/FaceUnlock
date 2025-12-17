package me.gustyxpower.faceunlock.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manager untuk menyimpan kredensial lock screen secara terenkripsi
 */
class CredentialManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "credential_prefs"
        private const val KEY_ALIAS = "face_unlock_key"
        private const val KEY_LOCK_TYPE = "lock_type"
        private const val KEY_CREDENTIAL = "credential"
        private const val KEY_CREDENTIAL_IV = "credential_iv"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_PATTERN_IV = "pattern_iv"
        
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
    
    enum class LockType {
        NONE,
        PIN,
        PASSWORD,
        PATTERN
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        createKeyIfNeeded()
    }
    
    private fun createKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }
    
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
    
    private fun encrypt(data: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        
        return Pair(
            Base64.encodeToString(encrypted, Base64.DEFAULT),
            Base64.encodeToString(iv, Base64.DEFAULT)
        )
    }
    
    private fun decrypt(encryptedData: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        
        val decrypted = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
        return String(decrypted, Charsets.UTF_8)
    }
    
    // ===== Lock Type =====
    
    fun setLockType(type: LockType) {
        prefs.edit { putString(KEY_LOCK_TYPE, type.name) }
    }
    
    fun getLockType(): LockType {
        val typeName = prefs.getString(KEY_LOCK_TYPE, LockType.NONE.name)
        android.util.Log.d("CredentialManager", "getLockType: typeName=$typeName")
        return try {
            val type = LockType.valueOf(typeName ?: LockType.NONE.name)
            android.util.Log.d("CredentialManager", "getLockType: $type")
            type
        } catch (e: Exception) {
            android.util.Log.e("CredentialManager", "Failed to get lock type", e)
            LockType.NONE
        }
    }
    
    // ===== PIN / Password =====
    
    fun saveCredential(credential: String, type: LockType) {
        val (encrypted, iv) = encrypt(credential)
        prefs.edit {
            putString(KEY_CREDENTIAL, encrypted)
            putString(KEY_CREDENTIAL_IV, iv)
            putString(KEY_LOCK_TYPE, type.name)
        }
    }
    
    fun getCredential(): String? {
        val encrypted = prefs.getString(KEY_CREDENTIAL, null) ?: return null
        val iv = prefs.getString(KEY_CREDENTIAL_IV, null) ?: return null
        
        return try {
            decrypt(encrypted, iv)
        } catch (e: Exception) {
            null
        }
    }
    
    fun hasCredential(): Boolean {
        return prefs.contains(KEY_CREDENTIAL) && prefs.contains(KEY_CREDENTIAL_IV)
    }
    
    // ===== Pattern =====
    // Pattern disimpan sebagai string angka 1-9 yang merepresentasikan posisi grid 3x3
    // Contoh: "1235789" = pola L
    // Grid:
    // 1 2 3
    // 4 5 6
    // 7 8 9
    
    fun savePattern(pattern: String) {
        android.util.Log.d("CredentialManager", "savePattern: $pattern")
        val (encrypted, iv) = encrypt(pattern)
        android.util.Log.d("CredentialManager", "savePattern encrypted: $encrypted")
        prefs.edit {
            putString(KEY_PATTERN, encrypted)
            putString(KEY_PATTERN_IV, iv)
            putString(KEY_LOCK_TYPE, LockType.PATTERN.name)
        }
        android.util.Log.d("CredentialManager", "Pattern saved successfully")
    }
    
    fun getPattern(): String? {
        val encrypted = prefs.getString(KEY_PATTERN, null)
        val iv = prefs.getString(KEY_PATTERN_IV, null)
        
        android.util.Log.d("CredentialManager", "getPattern: encrypted=$encrypted, iv=$iv")
        
        if (encrypted == null || iv == null) {
            android.util.Log.e("CredentialManager", "Pattern data is null!")
            return null
        }
        
        return try {
            val decrypted = decrypt(encrypted, iv)
            android.util.Log.d("CredentialManager", "getPattern decrypted: $decrypted")
            decrypted
        } catch (e: Exception) {
            android.util.Log.e("CredentialManager", "Failed to decrypt pattern", e)
            null
        }
    }
    
    fun hasPattern(): Boolean {
        return prefs.contains(KEY_PATTERN) && prefs.contains(KEY_PATTERN_IV)
    }
    
    // ===== Clear =====
    
    fun clearAll() {
        prefs.edit { clear() }
    }
    
    fun clearCredential() {
        prefs.edit {
            remove(KEY_CREDENTIAL)
            remove(KEY_CREDENTIAL_IV)
            if (getLockType() == LockType.PIN || getLockType() == LockType.PASSWORD) {
                remove(KEY_LOCK_TYPE)
            }
        }
    }
    
    fun clearPattern() {
        prefs.edit {
            remove(KEY_PATTERN)
            remove(KEY_PATTERN_IV)
            if (getLockType() == LockType.PATTERN) {
                remove(KEY_LOCK_TYPE)
            }
        }
    }
}
