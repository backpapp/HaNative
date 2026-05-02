package com.backpapp.hanative.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
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

/**
 * Keychain-backed token store. Earlier revisions called
 * `CFDictionaryCreateMutable(null, 0, null, null)`, which installs no retain callbacks
 * on the dictionary — every `cfStr(...)` value created inline was released as soon as
 * its Kotlin holder went out of scope, leaving the SecItem* query reading dangling
 * pointers. Writes appeared to succeed but the entry could not be read back on the
 * next launch, so the app always returned to onboarding.
 *
 * Passing `kCFTypeDictionaryKeyCallBacks` / `kCFTypeDictionaryValueCallBacks` makes the
 * dictionary CFRetain its keys and values for its full lifetime, which fixes the
 * relaunch persistence and lets us safely release each CFString locally with CFRelease
 * once it has been added to the dict.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCredentialStore : CredentialStore {

    override suspend fun saveToken(token: String) {
        val bytes = token.encodeToByteArray()
        memScoped {
            val cfData = bytes.usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
            } ?: return
            try {
                buildBaseQuery()?.let { query ->
                    try {
                        SecItemDelete(query)
                    } finally {
                        CFRelease(query)
                    }
                }
                val addQuery = buildBaseQuery() ?: return
                try {
                    CFDictionarySetValue(addQuery, kSecValueData, cfData)
                    val status = SecItemAdd(addQuery, null)
                    if (status != errSecSuccess) {
                        println("WARN: IosCredentialStore SecItemAdd failed status=$status")
                    }
                } finally {
                    CFRelease(addQuery)
                }
            } finally {
                CFRelease(cfData)
            }
        }
    }

    override suspend fun getToken(): String? {
        val query = buildBaseQuery() ?: return null
        try {
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                if (status != errSecSuccess) return null
                @Suppress("UNCHECKED_CAST")
                val cfData = result.value as? CFDataRef ?: return null
                val length = CFDataGetLength(cfData).toInt()
                val ptr: CPointer<ByteVar> = CFDataGetBytePtr(cfData)?.reinterpret() ?: return null
                return ptr.readBytes(length).decodeToString()
            }
        } finally {
            CFRelease(query)
        }
    }

    override suspend fun clear() {
        val query = buildBaseQuery() ?: return
        try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun buildBaseQuery(): CFMutableDictionaryRef? = memScoped {
        val dict = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: return@memScoped null
        val service = CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8)
        val account = CFStringCreateWithCString(null, ACCOUNT, kCFStringEncodingUTF8)
        try {
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, service)
            CFDictionarySetValue(dict, kSecAttrAccount, account)
        } finally {
            // Dict retains its values via the kCFType* callbacks above, so we can
            // release our local +1 reference here without breaking the dict.
            if (service != null) CFRelease(service)
            if (account != null) CFRelease(account)
        }
        dict
    }
}
