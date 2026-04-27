package com.backpapp.hanative.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE = "com.backpapp.hanative"
private const val ACCOUNT = "ha_auth_token"

@OptIn(ExperimentalForeignApi::class)
class IosCredentialStore : CredentialStore {

    override suspend fun saveToken(token: String) {
        val ubytes = token.encodeToByteArray().asUByteArray()
        val cfData = ubytes.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0), ubytes.size.toLong())
        } ?: return
        SecItemDelete(baseQuery())
        val addQuery = baseQuery()?.also { dict ->
            CFDictionarySetValue(dict, kSecValueData, cfData)
        }
        SecItemAdd(addQuery, null)
    }

    override suspend fun getToken(): String? {
        val query = baseQuery()?.also { dict ->
            CFDictionarySetValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        }
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return null
            @Suppress("UNCHECKED_CAST")
            val cfData = result.value as? CFDataRef ?: return null
            val length = CFDataGetLength(cfData).toInt()
            val ptr = CFDataGetBytePtr(cfData) ?: return null
            return ByteArray(length) { ptr[it].toByte() }.decodeToString()
        }
    }

    override suspend fun clear() {
        SecItemDelete(baseQuery())
    }

    private fun baseQuery(): CFMutableDictionaryRef? =
        CFDictionaryCreateMutable(null, 0, null, null)?.also { dict ->
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, cfStr(SERVICE))
            CFDictionarySetValue(dict, kSecAttrAccount, cfStr(ACCOUNT))
        }

    private fun cfStr(s: String) = CFStringCreateWithCString(null, s, kCFStringEncodingUTF8)
}
