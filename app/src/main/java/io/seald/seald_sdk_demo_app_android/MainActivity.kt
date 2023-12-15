package io.seald.seald_sdk_demo_app_android

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.seald.seald_sdk.*
import kotlinx.coroutines.*
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

// Seald account infos:
// First step with Seald: https://docs.seald.io/en/sdk/guides/1-quick-start.html
// Create a team here: https://www.seald.io/create-sdk
const val API_URL = "https://api.staging-0.seald.io/"
const val APP_ID = "1e2600a5-417e-4333-93a6-2b196781b0de"
const val JWT_SHARED_SECRET_ID = "32b4e3db-300b-4916-90e6-0020639c3df0"
const val JWT_SHARED_SECRET = "VstlqoxvQPAxRTDa6cAzWiQiqcgETNP8yYnNyhGWXaI6uS7X5t8csh1xYeLTjTTO"

// The Seald SDK uses a local database that will persist on disk.
// When instantiating a SealdSDK, it is highly recommended to set a symmetric key to encrypt this database.
// This demo will use a fixed key. In an actual app, it should be generated at signup,
// either on the server and retrieved from your backend at login,
// or on the client-side directly and stored in the system's keychain.
const val DATABASE_ENCRYPTION_KEY_B64 = "V4olGDOE5bAWNa9HDCvOACvZ59hUSUdKmpuZNyl1eJQnWKs5/l+PGnKUv4mKjivL3BtU014uRAIF2sOl83o6vQ"

const val SSKS_URL = "https://ssks.soyouz.seald.io/"
const val SSKS_BACKEND_APP_ID = "00000000-0000-0000-0000-000000000001"
const val SSKS_BACKEND_APP_KEY = "00000000-0000-0000-0000-000000000002"
const val SSKS_TMR_CHALLENGE = "aaaaaaaa"

fun deleteRecursive(fileOrDirectory: File) {
    if (fileOrDirectory.isDirectory()) {
        for (child in fileOrDirectory.listFiles()!!) {
            deleteRecursive(child)
        }
    }
    fileOrDirectory.delete()
}

fun randomByteArray(length: Int): ByteArray {
    val random = Random()
    return ByteArray(length) { random.nextInt(256).toByte() }
}

fun randomString(length: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyz"
    val random = Random()
    return (1..length)
        .map { chars[random.nextInt(chars.length)] }
        .joinToString("")
}

class MainActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Delete local database from previous run
        val path = this.applicationContext.filesDir.absolutePath
        deleteRecursive(File(path))

        // Seald uses JWT to manage licenses and identity.
        // JWTs should be generated by your backend, and sent to the user at signup.
        // The JWT secretId and secret can be generated from your administration dashboard. They should NEVER be on client side.
        // However, as this is a demo without a backend, we will use them on the frontend.
        // JWT documentation: https://docs.seald.io/en/sdk/guides/jwt.html
        // identity documentation: https://docs.seald.io/en/sdk/guides/4-identities.html
        val jwtBuilder = JWTBuilder(JWT_SHARED_SECRET_ID, JWT_SHARED_SECRET)

        CoroutineScope(Dispatchers.Default).launch {
            // SDK
            val sdkResultView: TextView = findViewById(R.id.testSDK)
            withContext(Dispatchers.Main) { sdkResultView.text = "test SDK: Running..." }
            val sdkResult = testSDK(path, jwtBuilder)
            withContext(Dispatchers.Main) {
                sdkResultView.text = "test SDK: ${if (sdkResult) "success" else "error"}"
            }
            // SSKS Password
            val ssksPasswordResultView: TextView = findViewById(R.id.testSsksPassword)
            withContext(Dispatchers.Main) {
                ssksPasswordResultView.text = "test SSKS Password: Running..."
            }
            val ssksPasswordResult = testSSKSPassword()
            withContext(Dispatchers.Main) {
                ssksPasswordResultView.text =
                    "test SSKS Password: ${if (ssksPasswordResult) "success" else "error"}"
            }
            // SSKS TMR
            val ssksTmrResultView: TextView = findViewById(R.id.testSsksTMR)
            withContext(Dispatchers.Main) { ssksTmrResultView.text = "test SSKS TMR: Running..." }
            val ssksTmrResult = testSSKSTMR()
            withContext(Dispatchers.Main) {
                ssksTmrResultView.text =
                    "test SSKS TMR: ${if (ssksTmrResult) "success" else "error"}"
            }
        }
    }

    private suspend fun testSDK(
        path: String,
        jwtBuilder: JWTBuilder,
    ): Boolean {
        try {
            // let's instantiate 3 SealdSDK. They will correspond to 3 users that will exchange messages.
            val sdk1 =
                SealdSDK(
                    API_URL,
                    appId = APP_ID,
                    "$path/sdk1",
                    DATABASE_ENCRYPTION_KEY_B64,
                    instanceName = "User1",
                    logLevel = -1,
                )
            val sdk2 =
                SealdSDK(
                    API_URL,
                    appId = APP_ID,
                    "$path/sdk2",
                    DATABASE_ENCRYPTION_KEY_B64,
                    instanceName = "User2",
                    logLevel = -1,
                )
            val sdk3 =
                SealdSDK(
                    API_URL,
                    appId = APP_ID,
                    "$path/sdk3",
                    DATABASE_ENCRYPTION_KEY_B64,
                    instanceName = "User3",
                    logLevel = -1,
                )

            // retrieve info about current user before creating a user should return null
            val retrieveNoAccount = sdk1.getCurrentAccountInfoAsync()
            assert(retrieveNoAccount == null)

            // Create the 3 accounts. Again, the signupJWT should be generated by your backend
            val sdk1Deferred =
                CoroutineScope(
                    Dispatchers.Default,
                ).async { sdk1.createAccount(jwtBuilder.signupJWT(), "User1", "deviceNameUser1") }
            val sdk2Deferred =
                CoroutineScope(
                    Dispatchers.Default,
                ).async { sdk2.createAccount(jwtBuilder.signupJWT(), "User2", "deviceNameUser2") }
            val sdk3Deferred =
                CoroutineScope(
                    Dispatchers.Default,
                ).async { sdk3.createAccount(jwtBuilder.signupJWT(), "User3", "deviceNameUser3") }
            val user1AccountInfo = sdk1Deferred.await()
            val user2AccountInfo = sdk2Deferred.await()
            val user3AccountInfo = sdk3Deferred.await()

            // retrieve info about current user:
            val retrieveAccountInfo = sdk1.getCurrentAccountInfoAsync()
            assert(retrieveAccountInfo != null)
            assert(retrieveAccountInfo?.userId == user1AccountInfo.userId)
            assert(retrieveAccountInfo?.deviceId == user1AccountInfo.deviceId)

            // Create group: https://docs.seald.io/sdk/guides/5-groups.html
            val groupName = "group-1"
            val groupMembers = arrayOf(user1AccountInfo.userId)
            val groupAdmins = arrayOf(user1AccountInfo.userId)
            val groupId = sdk1.createGroupAsync(groupName, groupMembers, groupAdmins)

            // Manage group members and admins
            // Add user2 as group member
            sdk1.addGroupMembersAsync(groupId, arrayOf(user2AccountInfo.userId))
            // user1 add user3 as group member and group admin
            sdk1.addGroupMembersAsync(groupId, arrayOf(user3AccountInfo.userId), arrayOf(user3AccountInfo.userId))
            // user3 can remove user2
            sdk3.removeGroupMembersAsync(groupId, arrayOf(user2AccountInfo.userId))
            // user3 can remove user1 from admins
            sdk3.setGroupAdminsAsync(groupId, arrayOf(), arrayOf(user1AccountInfo.userId))

            // Create encryption session: https://docs.seald.io/sdk/guides/6-encryption-sessions.html
            // user1, user2, and group as recipients
            val allRights = RecipientRights(read = true, forward = true, revoke = true)
            val recipients =
                arrayOf(
                    RecipientWithRights(user1AccountInfo.userId, allRights),
                    RecipientWithRights(user2AccountInfo.userId, allRights),
                    RecipientWithRights(groupId, allRights),
                )
            val es1SDK1 = sdk1.createEncryptionSessionAsync(recipients)
            assert(es1SDK1.retrievalDetails.flow == EncryptionSessionRetrievalFlow.CREATED)

            // Create proxy sessions
            val proxySession1 =
                sdk1.createEncryptionSessionAsync(
                    arrayOf(
                        RecipientWithRights(user1AccountInfo.userId, allRights),
                        RecipientWithRights(user3AccountInfo.userId, allRights),
                    ),
                )
            es1SDK1.addProxySession(proxySession1.sessionId, allRights)

            val proxySession2 =
                sdk1.createEncryptionSessionAsync(
                    arrayOf(
                        RecipientWithRights(user1AccountInfo.userId, allRights),
                        RecipientWithRights(user2AccountInfo.userId, allRights),
                    ),
                )
            es1SDK1.addProxySession(proxySession2.sessionId, allRights)

            // The io.seald.seald_sdk.EncryptionSession object can encrypt and decrypt for user1
            val initialString = "a message that needs to be encrypted!"
            val encryptedMessage = es1SDK1.encryptMessageAsync(initialString)
            val decryptedMessage = es1SDK1.decryptMessageAsync(encryptedMessage)
            assert(initialString == decryptedMessage)

            // Create a test file on disk that we will encrypt/decrypt
            val filename = "testfile.txt"
            val fileContent = "File clear data."
            val clearFile = File(getFilesDir(), "/$filename")
            clearFile.writeText(fileContent)

            // encrypt the test file. Resulting file will be written alongside the source file, with `.seald` extension added
            val encryptedFileURI = es1SDK1.encryptFileFromURIAsync(clearFile.absolutePath)

            // user1 can retrieve the encryptionSession directly from the encrypted file
            val es1SDK1FromFileId = parseSessionIdFromFile(encryptedFileURI)
            assert(es1SDK1FromFileId == es1SDK1.sessionId)
            val es1SDK1FromFile = sdk1.retrieveEncryptionSessionFromFileAsync(encryptedFileURI)
            assert(es1SDK1FromFile.sessionId == es1SDK1.sessionId)
            assert(es1SDK1FromFile.retrievalDetails.flow == EncryptionSessionRetrievalFlow.DIRECT)

            // The retrieved session can decrypt the file.
            // The decrypted file will be named with the name it had at encryption. Any renaming of the encrypted file will be ignore.
            // NOTE: In this example, the decrypted file will have `(1)` suffix to avoid overwriting the original clear file.
            val decryptedFileURI = es1SDK1FromFile.decryptFileFromURIAsync(encryptedFileURI)
            assertTrue { decryptedFileURI.endsWith("testfile (1).txt") }
            val decryptedFile = File(decryptedFileURI)
            assert(fileContent == decryptedFile.readText())

            // Using the file ByteArray
            val file = File(encryptedFileURI)
            val fileBytes = file.readBytes()
            val es1SDK1FromByteId = parseSessionIdFromBytes(fileBytes)
            assert(es1SDK1FromByteId == es1SDK1.sessionId)
            val es1SDK1FromByte = sdk1.retrieveEncryptionSessionFromBytesAsync(fileBytes)
            assert(es1SDK1FromByte.sessionId == es1SDK1.sessionId)
            assert(es1SDK1FromByte.retrievalDetails.flow == EncryptionSessionRetrievalFlow.DIRECT)
            val decryptedFile2URI = es1SDK1FromByte.decryptFileFromURIAsync(encryptedFileURI)
            assertTrue { decryptedFile2URI.endsWith("testfile (2).txt") }
            val decryptedFile2 = File(decryptedFile2URI)
            assert(fileContent == decryptedFile2.readText())

            // user1 can retrieve the EncryptionSession from the encrypted message
            val es1SDK1FromMessId = parseSessionIdFromMessage(encryptedMessage)
            assert(es1SDK1FromMessId == es1SDK1.sessionId)
            val es1SDK1FromMess = sdk1.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, true)
            assert(es1SDK1FromMess.sessionId == es1SDK1.sessionId)
            assert(es1SDK1FromMess.retrievalDetails.flow == EncryptionSessionRetrievalFlow.DIRECT)
            val decryptedMessageFromMess = es1SDK1FromMess.decryptMessageAsync(encryptedMessage)
            assert(initialString == decryptedMessageFromMess)

            // user2 can retrieve the encryptionSession from the session ID.
            val es1SDK2 = sdk2.retrieveEncryptionSessionAsync(es1SDK1.sessionId, true)
            assert(es1SDK2.retrievalDetails.flow == EncryptionSessionRetrievalFlow.DIRECT)
            val decryptedMessageSDK2 = es1SDK2.decryptMessageAsync(encryptedMessage)
            assert(initialString == decryptedMessageSDK2)

            // user3 cannot retrieve the SealdEncryptionSession with lookupGroupKey set to false.
            var exception =
                assertFails {
                    sdk3.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, false, lookupGroupKey = false)
                } as SealdException
            assert(exception.status == 404)

            // user3 can retrieve the encryptionSession from the encrypted message through the group.
            val es1SDK3FromGroup = sdk3.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, true, lookupGroupKey = true)
            assert(es1SDK3FromGroup.retrievalDetails.flow == EncryptionSessionRetrievalFlow.VIA_GROUP)
            assert(es1SDK3FromGroup.retrievalDetails.groupId == groupId)
            val decryptedMessageSDK3 = es1SDK3FromGroup.decryptMessageAsync(encryptedMessage)
            assert(initialString == decryptedMessageSDK3)

            // user3 removes all members of "group-1". A group without member is deleted.
            sdk3.removeGroupMembersAsync(
                groupId,
                arrayOf(user1AccountInfo.userId, user3AccountInfo.userId),
            )

            // user3 could retrieve the previous encryption session only because "group-1" was set as recipient.
            // As the group was deleted, it can no longer access it.
            // user3 still has the encryption session in its cache, but we can disable it.
            exception =
                assertFails {
                    sdk3.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, false, lookupGroupKey = true)
                } as SealdException
            assert(exception.status == 404)

            // user3 can still retrieve the session via proxy.
            val es1SDK3FromProxy = sdk3.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, true, lookupProxyKey = true)
            assert(es1SDK3FromProxy.retrievalDetails.flow == EncryptionSessionRetrievalFlow.VIA_PROXY)
            assert(es1SDK3FromProxy.retrievalDetails.proxySessionId == proxySession1.sessionId)

            // user2 adds user3 as recipient of the encryption session.
            val recipientToAdd = arrayOf(RecipientWithRights(user3AccountInfo.userId, allRights))
            val respAdd = es1SDK2.addRecipientsAsync(recipientToAdd)
            assert(respAdd.size == 1)
            assert(respAdd[user3AccountInfo.deviceId]!!.success) // Note that addRecipient return deviceId

            // user3 can now retrieve it without group or proxy.
            val es1SDK3 = sdk3.retrieveEncryptionSessionAsync(es1SDK1.sessionId, false, lookupGroupKey = false, lookupProxyKey = false)
            assert(es1SDK3.retrievalDetails.flow == EncryptionSessionRetrievalFlow.DIRECT)
            val decryptedMessageAfterAdd = es1SDK3.decryptMessageAsync(encryptedMessage)
            assert(initialString == decryptedMessageAfterAdd)

            // user1 revokes user3 and proxy1 from the encryption session.
            val respRevoke = es1SDK1.revokeRecipientsAsync(arrayOf(user3AccountInfo.userId), arrayOf(proxySession1.sessionId))
            assert(respRevoke.recipients.size == 1)
            assert(respRevoke.recipients[user3AccountInfo.userId]!!.success)
            assert(respRevoke.proxySessions.size == 1)
            assert(respRevoke.proxySessions[proxySession1.sessionId]!!.success)

            // user3 cannot retrieve the session anymore, even with proxy or group
            exception =
                assertFails {
                    sdk3.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, false, lookupProxyKey = true, lookupGroupKey = true)
                } as SealdException
            assert(exception.status == 404)

            // user1 revokes all other recipients from the session
            val respRevokeOther = es1SDK1.revokeOthersAsync()
            assert(respRevokeOther.recipients.size == 2) // revoke user2 and group
            assert(respRevokeOther.recipients[groupId]!!.success)
            assert(respRevokeOther.recipients[user2AccountInfo.userId]!!.success)
            assert(respRevokeOther.proxySessions.size == 1)
            assert(respRevokeOther.proxySessions[proxySession2.sessionId]!!.success)

            // user2 cannot retrieve the session anymore
            exception =
                assertFails {
                    sdk2.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, false)
                } as SealdException
            assert(exception.status == 404)

            // user1 revokes all. It can no longer retrieve it.
            val respRevokeAll = es1SDK1.revokeAllAsync()
            assert(respRevokeAll.recipients.size == 1) // only user1 is left
            assert(respRevokeAll.recipients[user1AccountInfo.userId]!!.success)
            assert(respRevokeAll.proxySessions.size == 0)

            exception =
                assertFails {
                    sdk1.retrieveEncryptionSessionFromMessageAsync(encryptedMessage, false)
                } as SealdException
            assert(exception.status == 404)

            // Create additional data for user1
            val recipientAsync = arrayOf(RecipientWithRights(user1AccountInfo.userId, allRights))
            val es2SDK1 = sdk1.createEncryptionSessionAsync(recipientAsync, true)
            val anotherMessage = "nobody should read that!"
            val secondEncryptedMessage = es2SDK1.encryptMessageAsync(anotherMessage)

            // user1 can renew its key, and still decrypt old messages
            sdk1.renewKeys(Duration.ofDays(365 * 5))
            val es2SDK1AfterRenew = sdk1.retrieveEncryptionSessionAsync(es2SDK1.sessionId, false)
            val decryptedMessageAfterRenew = es2SDK1AfterRenew.decryptMessageAsync(secondEncryptedMessage)
            assert(anotherMessage == decryptedMessageAfterRenew)

            // CONNECTORS https://docs.seald.io/en/sdk/guides/jwt.html#adding-a-userid

            // we can add a custom userId using a JWT
            val customConnectorJWTValue = "user1-custom-id"
            val addConnectorJWT = jwtBuilder.connectorJWT(customConnectorJWTValue, APP_ID)
            sdk1.pushJWTAsync(addConnectorJWT)

            val connectors = sdk1.listConnectorsAsync()
            assert(connectors.size == 1)
            assert(connectors[0].state == io.seald.seald_sdk.ConnectorState.VALIDATED)
            assert(connectors[0].type == io.seald.seald_sdk.ConnectorType.AP)
            assert(connectors[0].sealdId == user1AccountInfo.userId)
            assert(connectors[0].value == "$customConnectorJWTValue@$APP_ID")

            // Retrieve connector by its id
            val retrieveConnector = sdk1.retrieveConnectorAsync(connectors[0].id)
            assert(retrieveConnector.sealdId == user1AccountInfo.userId)
            assert(retrieveConnector.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
            assert(retrieveConnector.type == io.seald.seald_sdk.ConnectorType.AP)
            assert(retrieveConnector.value == "$customConnectorJWTValue@$APP_ID")

            // Retrieve connectors from a user id.
            val connectorsFromSealdId = sdk1.getConnectorsFromSealdIdAsync(user1AccountInfo.userId)
            assert(connectorsFromSealdId.size == 1)
            assert(connectorsFromSealdId[0].state == io.seald.seald_sdk.ConnectorState.VALIDATED)
            assert(connectorsFromSealdId[0].type == io.seald.seald_sdk.ConnectorType.AP)
            assert(connectorsFromSealdId[0].sealdId == user1AccountInfo.userId)
            assert(connectorsFromSealdId[0].value == "$customConnectorJWTValue@$APP_ID")

            // Get sealdId of a user from a connector
            val sealdIds =
                sdk2.getSealdIdsFromConnectorsAsync(
                    arrayOf(
                        io.seald.seald_sdk.ConnectorTypeValue(
                            io.seald.seald_sdk.ConnectorType.AP,
                            "$customConnectorJWTValue@$APP_ID",
                        ),
                    ),
                )
            assert(sealdIds.size == 1)
            assert(sealdIds[0] == user1AccountInfo.userId)

            // user1 can remove a connector
            sdk1.removeConnectorAsync(connectors[0].id)

            // verify that no connector left
            val connectorListAfterRevoke = sdk1.listConnectorsAsync()
            assert(connectorListAfterRevoke.isEmpty())

            // user1 can export its identity
            val exportIdentity = sdk1.exportIdentityAsync()

            // We can instantiate a new SealdSDK, import the exported identity
            val sdk1Exported =
                SealdSDK(
                    API_URL,
                    appId = APP_ID,
                    "$path/sdk1Exported",
                    DATABASE_ENCRYPTION_KEY_B64,
                    instanceName = "sdk1",
                    logLevel = -1,
                )
            sdk1Exported.importIdentityAsync(exportIdentity)

            // SDK with imported identity can decrypt
            val es2SDK1Exported = sdk1Exported.retrieveEncryptionSessionFromMessageAsync(secondEncryptedMessage)
            val clearMessageExportedIdentity = es2SDK1Exported.decryptMessageAsync(secondEncryptedMessage)
            assert(anotherMessage == clearMessageExportedIdentity)

            // user1 can create sub identity
            val subIdentity = sdk1.createSubIdentityAsync("SUB-deviceName")
            assert(subIdentity.deviceId != "")

            // first device needs to reencrypt for the new device
            sdk1.massReencryptAsync(subIdentity.deviceId)
            // We can instantiate a new SealdSDK, import the sub-device identity
            val sdk1SubDevice =
                SealdSDK(
                    API_URL,
                    appId = APP_ID,
                    "$path/sdk1SubDevice",
                    DATABASE_ENCRYPTION_KEY_B64,
                    instanceName = "sdk1",
                    logLevel = -1,
                )
            sdk1SubDevice.importIdentityAsync(subIdentity.backupKey)

            // sub device can decrypt
            val es2SDK1SubDevice = sdk1SubDevice.retrieveEncryptionSessionFromMessageAsync(secondEncryptedMessage, false)

            val clearMessageSubdIdentity = es2SDK1SubDevice.decryptMessageAsync(secondEncryptedMessage)
            assert(anotherMessage == clearMessageSubdIdentity)

            sdk1.heartbeatAsync()

            // close SDKs
            sdk1.close()
            sdk2.close()
            sdk3.close()

            println("SDK tests success!")
            return true
        } catch (e: Throwable) {
            when (e) {
                is AssertionError, is Exception -> {
                    println("SDK tests failed")
                    println(e.printStackTrace())
                    return false
                }
                else -> {
                    println("Fatal error in SDK tests")
                    println(e.printStackTrace())
                    throw e
                }
            }
        }
    }

    private suspend fun testSSKSPassword(): Boolean {
        try {
            // Simulating a Seald identity with random data, for a simpler example.
            val dummyIdentity = randomByteArray(10)
            val ssksPlugin =
                SealdSSKSPasswordPlugin(
                    ssksURL = SSKS_URL,
                    appId = APP_ID,
                    instanceName = "SSKSPassword",
                    logLevel = -1,
                )

            // Test with password
            val userIdPassword = "user-${randomString(10)}" // should be: AccountInfo.userId
            val userPassword = randomString(10)

            // Saving the identity with a password
            ssksPlugin.saveIdentityFromPasswordAsync(userIdPassword, userPassword, dummyIdentity)

            // Retrieving the identity with the password
            val retrievedIdentity = ssksPlugin.retrieveIdentityFromPasswordAsync(userIdPassword, userPassword)
            assert(retrievedIdentity.contentEquals(dummyIdentity))

            // Changing the password
            val newPassword = "newPassword"
            ssksPlugin.changeIdentityPasswordAsync(userIdPassword, userPassword, newPassword)

            // The previous password does not work anymore
            val badPasswordException =
                assertFails {
                    ssksPlugin.retrieveIdentityFromPasswordAsync(userIdPassword, userPassword)
                } as SealdException
            assert(badPasswordException.code == "SSKSPASSWORD_CANNOT_FIND_IDENTITY")

            // Retrieving with the new password works
            val retrieveNewPassword = ssksPlugin.retrieveIdentityFromPasswordAsync(userIdPassword, newPassword)
            assert(retrieveNewPassword.contentEquals(dummyIdentity))

            // Test with raw keys
            val userIdRawKeys = "user-${randomString(10)}"
            val rawEncryptionKey = randomByteArray(64)
            val rawStorageKey = randomString(32)

            // Saving identity with raw keys
            ssksPlugin.saveIdentityFromRawKeysAsync(
                userIdRawKeys,
                rawStorageKey,
                rawEncryptionKey,
                dummyIdentity,
            )

            // Retrieving the identity with raw keys
            val retrievedFromRawKeys =
                ssksPlugin.retrieveIdentityFromRawKeysAsync(
                    userIdRawKeys,
                    rawStorageKey,
                    rawEncryptionKey,
                )
            assert(retrievedFromRawKeys.contentEquals(dummyIdentity))

            // Deleting the identity by saving an empty `Data`
            ssksPlugin.saveIdentityFromRawKeysAsync(
                userIdRawKeys,
                rawStorageKey,
                rawEncryptionKey,
                ByteArray(0),
            )

            // After deleting the identity, cannot retrieve anymore
            val exception =
                assertFails {
                    ssksPlugin.retrieveIdentityFromRawKeysAsync(
                        userIdRawKeys,
                        rawStorageKey,
                        rawEncryptionKey,
                    )
                } as SealdException
            assert(exception.code == "SSKSPASSWORD_CANNOT_FIND_IDENTITY")

            println("SSKS Password tests success!")
            return true
        } catch (e: Throwable) {
            when (e) {
                is AssertionError, is Exception -> {
                    println("SSKS Password tests failed")
                    println(e.printStackTrace())
                    return false
                }
                else -> {
                    println("Fatal error in SSKS Password tests")
                    println(e.printStackTrace())
                    throw e
                }
            }
        }
    }

    private suspend fun testSSKSTMR(): Boolean {
        try {
            val rawTMRSymKey = randomByteArray(64)

            val yourCompanyDummyBackend = SSKSbackend(SSKS_URL, SSKS_BACKEND_APP_ID, SSKS_BACKEND_APP_KEY)
            val ssksPlugin =
                SealdSSKSTmrPlugin(
                    ssksURL = SSKS_URL,
                    appId = APP_ID,
                    instanceName = "SSKSTmr1",
                    logLevel = -1,
                )

            // Simulating a Seald identity with random data, for a simpler example.
            val userId = "user-${randomString(11)}" // should be: AccountInfo.userId
            val dummyIdentity = randomByteArray(10) // should be: sdk.exportIdentity()

            val userEM = "email-${randomString(15)}@test.com"

            // The app backend creates a session to save the identity.
            // This is the first time that this email is storing an identity, so `must_authenticate` is false.
            val authFactor = AuthFactor(AuthFactorType.EM, userEM)
            val authSessionSave =
                yourCompanyDummyBackend.challengeSend(
                    userId,
                    authFactor,
                    createUser = true,
                    forceAuth = false,
                ).await()
            assertEquals(authSessionSave.mustAuthenticate, false)

            // Saving the identity. No challenge necessary because `must_authenticate` is false.
            ssksPlugin.saveIdentityAsync(
                authSessionSave.sessionId,
                authFactor = authFactor,
                rawTMRSymKey = rawTMRSymKey,
                identity = dummyIdentity,
                challenge = "",
            )

            // The app backend creates another session to retrieve the identity.
            // The identity is already saved, so `must_authenticate` is true.
            val authSessionRetrieve =
                yourCompanyDummyBackend.challengeSend(
                    userId,
                    authFactor,
                    createUser = true,
                    forceAuth = false,
                ).await()
            assertEquals(authSessionRetrieve.mustAuthenticate, true)

            // Retrieving identity. Challenge is necessary for this.
            val retrievedNotAuth =
                ssksPlugin.retrieveIdentityAsync(
                    authSessionRetrieve.sessionId,
                    authFactor = authFactor,
                    challenge = SSKS_TMR_CHALLENGE,
                    rawTMRSymKey = rawTMRSymKey,
                )
            assertEquals(retrievedNotAuth.shouldRenewKey, true)
            assert(retrievedNotAuth.identity.contentEquals(dummyIdentity))

            // If initial key has been saved without being fully authenticated, you should renew the user's private key, and save it again.
            // sdk.renewKeys(Duration.ofDays(365 * 5))

            // Let's simulate the renew with another random identity
            val identitySecondKey = randomByteArray(10) // should be the result of: sdk.exportIdentity()
            // to save the newly renewed identity, you can use the `authenticatedSessionId` from the response to `retrieveIdentityAsync`, with no challenge
            ssksPlugin.saveIdentityAsync(
                retrievedNotAuth.authenticatedSessionId,
                authFactor = authFactor,
                rawTMRSymKey = rawTMRSymKey,
                identity = identitySecondKey,
                challenge = "",
            )

            // And now let's retrieve this new saved identity
            val authSessionRetrieve2 =
                yourCompanyDummyBackend.challengeSend(
                    userId,
                    authFactor,
                    createUser = false,
                    forceAuth = false,
                ).await()
            assertEquals(authSessionRetrieve2.mustAuthenticate, true)
            val retrievedSecondKey =
                ssksPlugin.retrieveIdentityAsync(
                    authSessionRetrieve2.sessionId,
                    authFactor = authFactor,
                    challenge = SSKS_TMR_CHALLENGE,
                    rawTMRSymKey = rawTMRSymKey,
                )
            assertEquals(retrievedSecondKey.shouldRenewKey, false)
            assert(retrievedSecondKey.identity.contentEquals(identitySecondKey))

            // Try retrieving with another SealdSsksTMRPlugin instance
            val ssksPluginInst2 =
                SealdSSKSTmrPlugin(
                    ssksURL = SSKS_URL,
                    appId = APP_ID,
                    instanceName = "SSKSTmr2",
                    logLevel = -1,
                )
            val authSessionRetrieve3 =
                yourCompanyDummyBackend.challengeSend(
                    userId,
                    authFactor,
                    createUser = false,
                    forceAuth = false,
                ).await()
            assert(authSessionRetrieve3.mustAuthenticate)
            val inst2Retrieve =
                ssksPluginInst2.retrieveIdentity(
                    authSessionRetrieve3.sessionId,
                    authFactor = authFactor,
                    challenge = SSKS_TMR_CHALLENGE,
                    rawTMRSymKey = rawTMRSymKey,
                )
            assert(!inst2Retrieve.shouldRenewKey)
            assert(inst2Retrieve.identity.contentEquals(identitySecondKey))

            println("SSKS TMR tests success!")
            return true
        } catch (e: Throwable) {
            when (e) {
                is AssertionError, is Exception -> {
                    println("SSKS TMR tests failed")
                    println(e.printStackTrace())
                    return false
                }
                else -> {
                    println("Fatal TMR in SSKS Password tests")
                    println(e.printStackTrace())
                    throw e
                }
            }
        }
    }
}
