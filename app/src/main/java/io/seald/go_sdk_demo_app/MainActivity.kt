package io.seald.go_sdk_demo_app

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import io.seald.seald_sdk.SealdSDK
import java.io.File
import java.time.Duration

// Seald account infos:
// First step with Seald: https://docs.seald.io/en/sdk/guides/1-quick-start.html
// Create a team here: https://www.seald.io/fr/create-sdk
val JWTSharedSecretId = "00000000-0000-1000-a000-7ea300000019"
val JWTSharedSecret = "o75u89og9rxc9me54qxaxvdutr2t4t25ozj4m64utwemm0osld0zdb02j7gv8t7x"
val apiURL = "https://api-dev.soyouz.seald.io/"
val appId = "00000000-0000-1000-a000-7ea300000018"

// The Seald SDK uses a local database that will persist on disk.
// When instantiating a SealdSDK, it is highly recommended to set a symmetric key to encrypt this database.
// This demo will use a fixed key. It should be generated at signup, and retrieved from your backend at login.
val databaseEncryptionKeyB64 = "V4olGDOE5bAWNa9HDCvOACvZ59hUSUdKmpuZNyl1eJQnWKs5/l+PGnKUv4mKjivL3BtU014uRAIF2sOl83o6vQ"

const val TAG = "MainActivity"

fun deleteRecursive(fileOrDirectory: File) {
    if (fileOrDirectory.isDirectory()) for (child in fileOrDirectory.listFiles()) deleteRecursive(
        child
    )
    fileOrDirectory.delete()
}

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
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
        val jwtBuilder = JWTBuilder(JWTSharedSecretId, JWTSharedSecret)

        // let's instantiate 3 SealdSDK. They will correspond to 3 users that will exchange messages.
        val sdk1 = SealdSDK(apiURL, appId, "$path/sdk1", databaseEncryptionKeyB64, instanceName = "User1", logLevel = -1)
        val sdk2 = SealdSDK(apiURL, appId, "$path/sdk2", databaseEncryptionKeyB64, instanceName = "User2", logLevel = -1)
        val sdk3 = SealdSDK(apiURL, appId, "$path/sdk3", databaseEncryptionKeyB64, instanceName = "User3", logLevel = -1)

        // Create the 3 accounts. Again, the signupJWT should be generated by your backend
        val user1AccountInfo = sdk1.createAccount(jwtBuilder.signupJWT(), "User1", "deviceNameUser1")
        val user2AccountInfo = sdk2.createAccount(jwtBuilder.signupJWT(), "User2", "deviceNameUser2")
        val user3AccountInfo = sdk3.createAccount(jwtBuilder.signupJWT(), "User3", "deviceNameUser3")

        // retrieve info about current user:
        val retrieveAccountInfo = sdk1.getCurrentAccountInfo()
        assert(retrieveAccountInfo != null)
        assert(retrieveAccountInfo?.userId == user1AccountInfo.userId)
        assert(retrieveAccountInfo?.deviceId == user1AccountInfo.deviceId)

        // Create group: https://docs.seald.io/sdk/guides/5-groups.html
        val groupName = "group-1"
        val groupMembers = arrayOf(user1AccountInfo.userId)
        val groupAdmins = arrayOf(user1AccountInfo.userId)
        val groupId = sdk1.createGroup(groupName, groupMembers, groupAdmins)

        // Manage group members and admins
        sdk1.addGroupMembers(groupId, arrayOf(user2AccountInfo.userId)) // Add user2 as group member
        sdk1.addGroupMembers(groupId, arrayOf(user3AccountInfo.userId), arrayOf(user3AccountInfo.userId)) // user1 add user3 as group member and group admin
        sdk3.removeGroupMembers(groupId, arrayOf(user2AccountInfo.userId)) // user3 can remove user2
        sdk3.setGroupAdmins(groupId, arrayOf(), arrayOf(user1AccountInfo.userId)) // user3 can remove user1 from admins

        // Create encryption session: https://docs.seald.io/sdk/guides/6-encryption-sessions.html
        val recipient = arrayOf(user1AccountInfo.userId, user2AccountInfo.userId, groupId)
        val es1SDK1 = sdk1.createEncryptionSession(recipient) // user1, user2, and group as recipients

        // The io.seald.seald_sdk.EncryptionSession object can encrypt and decrypt for user1
        val initialString = "a message that needs to be encrypted!"
        val encryptedMessage = es1SDK1.encryptMessage(initialString)
        val decryptedMessage = es1SDK1.decryptMessage(encryptedMessage)
        assert(initialString == decryptedMessage)

        // user1 can retrieve the EncryptionSession from the encrypted message
        val es1SDK1RetrieveFromMess = sdk1.retrieveEncryptionSessionFromMessage(encryptedMessage, true)
        val decryptedMessageFromMess = es1SDK1RetrieveFromMess.decryptMessage(encryptedMessage)
        assert(initialString == decryptedMessageFromMess)

        // user2 and user3 can retrieve the encryptionSession (from the encrypted message or the session ID).
        val es1SDK2 = sdk2.retrieveEncryptionSession(es1SDK1.sessionId, true)
        val decryptedMessageSDK2 = es1SDK2.decryptMessage(encryptedMessage)
        assert(initialString == decryptedMessageSDK2)

        val es1SDK3FromGroup = sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, true)
        val decryptedMessageSDK3 = es1SDK3FromGroup.decryptMessage(encryptedMessage)
        assert(initialString == decryptedMessageSDK3)

        // user3 removes all members of "group-1". A group without member is deleted.
        sdk3.removeGroupMembers(groupId, arrayOf(user1AccountInfo.userId, user3AccountInfo.userId))

        // user3 could retrieve the previous encryption session only because "group-1" was set as recipient.
        // As the group was deleted, it can no longer access it.
        try {
            // user3 still has the encryption session in its cache, but we can disable it.
            sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            assert(false) // Trigger un-catchable `FATAL EXCEPTION`
        } catch (_: Exception) {}

        // user2 adds user3 as recipient of the encryption session.
        es1SDK2.addRecipients(arrayOf(user3AccountInfo.userId))

        // user3 can now retrieve it.
        val es1SDK3 = sdk3.retrieveEncryptionSession(es1SDK1.sessionId, false)
        val decryptedMessageAfterAdd = es1SDK3.decryptMessage(encryptedMessage)
        assert(initialString == decryptedMessageAfterAdd)

        // user2 revokes user3 from the encryption session.
        es1SDK2.revokeRecipients(arrayOf(user3AccountInfo.userId))

        // user3 cannot retrieve the session anymore
        try {
            sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            assert(false) // Trigger un-catchable `FATAL EXCEPTION`
        } catch (_: Exception) {}

        // user2 revokes all other recipients from the session
        es1SDK2.revokeOthers()

        // user3 cannot retrieve the session
        try {
            sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            assert(false) // Trigger un-catchable `FATAL EXCEPTION`
        } catch (_: Exception) {}

        es1SDK2.revokeAll()
        try {
            sdk2.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            assert(false) // Trigger un-catchable `FATAL EXCEPTION`
        } catch (_: Exception) {}

        // user1 can renew its key, and still decrypt old messages
        sdk1.renewKeys(Duration.ofDays(365 * 5))
        val es1SDK1AfterRenew = sdk1.retrieveEncryptionSession(es1SDK1.sessionId, false)
        val decryptedMessageAfterRenew = es1SDK1AfterRenew.decryptMessage(encryptedMessage)
        assert(initialString == decryptedMessageAfterRenew)

        // CONNECTORS https://docs.seald.io/en/sdk/guides/jwt.html#adding-a-userid

        // we can add a custom userId using a JWT
        val customConnectorJWTValue = "user1-custom-id"
        val addConnectorJWT = jwtBuilder.connectorJWT(customConnectorJWTValue, appId)
        sdk1.pushJWT(addConnectorJWT)

        // Add an email connector
        val customConnectorEmailValue = "email@domain.com"
        val pendingConnector = sdk1.addConnector(customConnectorEmailValue, io.seald.seald_sdk.ConnectorType.EM)
        assert(pendingConnector.sealdId == user1AccountInfo.userId)
        assert(pendingConnector.state == io.seald.seald_sdk.ConnectorState.PENDING)
        assert(pendingConnector.value == customConnectorEmailValue)
        assert(pendingConnector.type == io.seald.seald_sdk.ConnectorType.EM)
        val validatedConnector = sdk1.validateConnector(pendingConnector.id, "000-000")
        assert(validatedConnector.sealdId == user1AccountInfo.userId)
        assert(validatedConnector.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
        assert(validatedConnector.sealdId == pendingConnector.sealdId)
        assert(validatedConnector.value == customConnectorEmailValue)
        assert(validatedConnector.id == pendingConnector.id)


        val connectors = sdk1.listConnectors()
        assert(connectors.size == 2)
        val emailConnector = connectors.find { connector ->  connector.id ==  validatedConnector.id}
        assert(emailConnector?.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
        assert(emailConnector?.type == io.seald.seald_sdk.ConnectorType.EM)
        assert(emailConnector?.sealdId == validatedConnector.sealdId)
        assert(emailConnector?.value == validatedConnector.value)
        assert(emailConnector?.id == validatedConnector.id)

        val jwtConnector = connectors.find { connector ->  connector.id !=  validatedConnector.id}
        assert(jwtConnector?.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
        assert(pendingConnector.type == io.seald.seald_sdk.ConnectorType.EM)
        assert(jwtConnector?.sealdId == validatedConnector.sealdId)
        assert(jwtConnector?.value == "${customConnectorJWTValue}@${appId}")

        // Retrieve connector by its id
        val retrieveConnector = sdk1.retrieveConnector(validatedConnector.id)
        assert(retrieveConnector.sealdId == user1AccountInfo.userId)
        assert(retrieveConnector.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
        assert(retrieveConnector.type == io.seald.seald_sdk.ConnectorType.EM)
        assert(retrieveConnector.value == customConnectorEmailValue)

        // Retrieve connectors from a user id.
        val connectorsFromSealdId = sdk1.getConnectorsFromSealdId(user1AccountInfo.userId)
        assert(connectorsFromSealdId.size == 2)
        val emailConnectorFromSealdId = connectors.find { connector ->  connector.id ==  validatedConnector.id}
        assert(emailConnectorFromSealdId?.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
        assert(emailConnectorFromSealdId?.type == io.seald.seald_sdk.ConnectorType.EM)
        assert(emailConnectorFromSealdId?.sealdId == validatedConnector.sealdId)
        assert(emailConnectorFromSealdId?.value == validatedConnector.value)
        assert(emailConnectorFromSealdId?.id == validatedConnector.id)

        val jwtConnectorFromSealdId = connectors.find { connector ->  connector.id !=  validatedConnector.id}
        assert(jwtConnectorFromSealdId?.state == io.seald.seald_sdk.ConnectorState.VALIDATED)
        assert(jwtConnectorFromSealdId?.type == io.seald.seald_sdk.ConnectorType.AP)
        assert(jwtConnectorFromSealdId?.sealdId == validatedConnector.sealdId)
        assert(jwtConnectorFromSealdId?.value == "${customConnectorJWTValue}@${appId}")

        // Get sealdId of a user from a connector
        val sealdIds = sdk2.getSealdIdsFromConnectors(arrayOf(io.seald.seald_sdk.ConnectorTypeValue(io.seald.seald_sdk.ConnectorType.AP, "${customConnectorJWTValue}@${appId}")))
        assert(sealdIds.size == 1)
        assert(sealdIds[0] == user1AccountInfo.userId)

        // user1 can remove a connector
        sdk1.removeConnector(validatedConnector.id)

        // verify that only one connector left
        val connectorListAfterRevoke = sdk1.listConnectors()
        assert(connectorListAfterRevoke.size == 1)
        assert(connectorListAfterRevoke[0].value == "${customConnectorJWTValue}@${appId}")

        // Create additional data for user1
        val es2SDK1 = sdk1.createEncryptionSession(arrayOf(user1AccountInfo.userId), true)
        val anotherMessage = "nobody should read that!"
        val encMessage = es2SDK1.encryptMessage(anotherMessage)

        // user1 can export its identity
        val exportIdentity = sdk1.exportIdentity()

        // We can instantiate a new SealdSDK, import the exported identity
        val sdk1Exported = SealdSDK(apiURL, appId, "$path/sdk1Exported", databaseEncryptionKeyB64, instanceName = "sdk1", logLevel = -1)
        sdk1Exported.importIdentity(exportIdentity)

        // SDK with imported identity can decrypt
        val es2SDK1Exported = sdk1Exported.retrieveEncryptionSessionFromMessage(encMessage)
        val clearMessageExportedIdentity = es2SDK1Exported.decryptMessage(encMessage)
        assert(anotherMessage == clearMessageExportedIdentity)

        // user1 can create sub identity
        val subIdentity = sdk1.createSubIdentity("SUB-deviceName")
        assert(subIdentity.deviceId != "")

        // first device needs to reencrypt for the new device
        sdk1.massReencrypt(subIdentity.deviceId)
        // We can instantiate a new SealdSDK, import the sub-device identity
        val sdk1SubDevice = SealdSDK(apiURL, appId, "$path/sdk1SubDevice", databaseEncryptionKeyB64, instanceName = "sdk1", logLevel = -1)
        sdk1SubDevice.importIdentity(subIdentity.backupKey)

        // sub device can decrypt
        val es2SDK1SubDevice = sdk1SubDevice.retrieveEncryptionSessionFromMessage(encMessage, false)
        val clearMessageSubdIdentity = es2SDK1SubDevice.decryptMessage(encMessage)
        assert(anotherMessage == clearMessageSubdIdentity)

        sdk1.heartbeat()

        // close SDKs
        sdk1.close()
        sdk2.close()
        sdk3.close()
    }
}