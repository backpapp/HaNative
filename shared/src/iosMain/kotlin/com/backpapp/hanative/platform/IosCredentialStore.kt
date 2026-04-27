package com.backpapp.hanative.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
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
        // Delete existing before add (SecItemUpdate not needed for simplicity)
        SecItemDelete(baseQuery())
        val addQuery = baseQuery().apply {
            setObject(tokenData, forKey = kSecValueData)
        }
        SecItemAdd(addQuery, null)
    }

    override suspend fun getToken(): String? {
        val query = baseQuery().apply {
            setObject(true, forKey = kSecReturnData)
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit)
        }
        memScoped {
            val result = alloc<kotlinx.cinterop.ObjCObjectVar<kotlin.Any?>>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return null
            val data = result.value as? NSData ?: return null
            return NSString.create(data, NSUTF8StringEncoding) as? String
        }
    }

    override suspend fun clear() {
        SecItemDelete(baseQuery())
    }

    private fun baseQuery(): NSMutableDictionary = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword!!, forKey = kSecClass as NSString)
        setObject(SERVICE, forKey = kSecAttrService as NSString)
        setObject(ACCOUNT, forKey = kSecAttrAccount as NSString)
    }
}
