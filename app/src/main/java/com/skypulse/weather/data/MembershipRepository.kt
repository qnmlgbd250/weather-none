package com.skypulse.weather.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class ActivationResult {
    SUCCESS,            // 激活成功
    INVALID_CODE,       // 激活码无效
    WRONG_DEVICE,       // 设备不匹配（此码非本设备专属）
    ALREADY_ACTIVATED   // 本设备已激活
}

@Singleton
class MembershipRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sky_pulse_membership",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _isPremium = MutableStateFlow(loadPremiumState())
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private fun loadPremiumState(): Boolean {
        return securePrefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun getActivatedAt(): Long {
        return securePrefs.getLong(KEY_ACTIVATED_AT, 0L)
    }

    /**
     * 获取本设备的设备 ID（8 位大写十六进制）
     * 用户将此 ID 发给开发者，开发者用脚本生成该设备的专属激活码
     */
    fun getDeviceId(): String {
        return getDeviceFingerprint().take(DEVICE_ID_LEN).uppercase()
    }

    /**
     * 校验激活码并激活会员
     *
     * 核心逻辑：激活码 = HMAC-SHA256(SECRET, device_id) 的前8位
     * 每个激活码在生成时就绑定了一台设备，无法在其他设备上使用
     *
     * @param code 用户输入的激活码（XXXX-XXXX 格式，8位）
     * @return 激活结果
     */
    fun activateCode(code: String): ActivationResult {
        if (_isPremium.value) {
            return ActivationResult.ALREADY_ACTIVATED
        }

        val normalizedCode = code.replace("-", "").trim().uppercase()

        if (normalizedCode.length != CODE_LENGTH) {
            return ActivationResult.INVALID_CODE
        }

        // 计算本设备的期望激活码
        val deviceId = getDeviceId()
        val expectedCode = computeCodeForDevice(deviceId)

        // 对比：输入的码必须等于本设备的期望码
        if (!normalizedCode.equals(expectedCode, ignoreCase = true)) {
            return ActivationResult.INVALID_CODE
        }

        // 激活码匹配 — 写入会员状态
        securePrefs.edit()
            .putBoolean(KEY_IS_PREMIUM, true)
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .commit()

        _isPremium.value = true
        return ActivationResult.SUCCESS
    }

    companion object {
        private const val CODE_LENGTH = 8       // XXXX-XXXX 格式
        private const val DEVICE_ID_LEN = 8     // 设备 ID 显示长度

        // HMAC 密钥（与 Python 脚本共享，分段混淆存储）
        private val SECRET: ByteArray by lazy {
            val p1 = "skypulse"
            val p2 = "_hmac_"
            val p3 = "2026_v1"
            (p1 + p2 + p3).toByteArray(Charsets.UTF_8)
        }

        // 设备指纹盐值
        private const val DEVICE_SALT = "sp_dev_salt_7f3a"

        // 存储键
        private const val KEY_IS_PREMIUM = "membership_premium"
        private const val KEY_ACTIVATED_AT = "membership_activated_at"

        /**
         * 根据设备 ID 计算该设备的专属激活码
         * 算法：HMAC-SHA256(SECRET, device_id) → base32 → 取前 8 位
         */
        fun computeCodeForDevice(deviceId: String): String {
            val normalized = deviceId.trim().uppercase()
            val hmac = hmacSha256(SECRET, normalized.toByteArray(Charsets.UTF_8))
            val b32 = base32Encode(hmac)
            return b32.take(CODE_LENGTH)
        }

        /**
         * 计算设备指纹（基于 Android ID + 盐值）
         * 内部使用，返回完整哈希
         */
        @SuppressLint("HardwareIds")
        internal fun computeDeviceFingerprint(context: Context): String {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            val raw = "$androidId$DEVICE_SALT"
            return sha256(raw)
        }

        private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun sha256(input: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }

        private fun base32Encode(data: ByteArray): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val result = StringBuilder()
            var buffer = 0
            var bitsInBuffer = 0

            for (byte in data) {
                buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
                bitsInBuffer += 8
                while (bitsInBuffer >= 5) {
                    val index = (buffer shr (bitsInBuffer - 5)) and 0x1F
                    result.append(alphabet[index])
                    bitsInBuffer -= 5
                }
            }

            if (bitsInBuffer > 0) {
                val index = (buffer shl (5 - bitsInBuffer)) and 0x1F
                result.append(alphabet[index])
            }

            return result.toString()
        }
    }

    // ====== 实例方法（需要 Context） ======

    @SuppressLint("HardwareIds")
    private fun getDeviceFingerprint(): String {
        return computeDeviceFingerprint(context)
    }
}
