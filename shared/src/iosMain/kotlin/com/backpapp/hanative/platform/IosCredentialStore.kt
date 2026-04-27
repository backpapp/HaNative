package com.backpapp.hanative.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFBridgingRelease
import platform.CoreFoundation.CFBridgingRetain
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
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
        val tokenData = (token as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        SecItemDelete(baseQuery())
        val addQuery = baseQuery()?.also { dict ->
            CFDictionarySetValue(dict, kSecValueData, CFBridgingRetain(tokenData))
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
            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            return NSString.create(data, NSUTF8StringEncoding) as? String
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
