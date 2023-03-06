package io.seald.go_sdk_demo_app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.seald.seald_sdk.SealdSDK
import java.io.File

val JWTSharedSecretId = "00000000-0000-1000-a000-7ea300000019"
val JWTSharedSecret = "o75u89og9rxc9me54qxaxvdutr2t4t25ozj4m64utwemm0osld0zdb02j7gv8t7x"
val databaseEncryptionKeyB64 = "V4olGDOE5bAWNa9HDCvOACvZ59hUSUdKmpuZNyl1eJQnWKs5/l+PGnKUv4mKjivL3BtU014uRAIF2sOl83o6vQ"
val apiURL = "https://api-dev.soyouz.seald.io/"
val appId = "00000000-0000-1000-a000-7ea300000018"

const val TAG = "MainActivity"

fun deleteRecursive(fileOrDirectory: File) {
    if (fileOrDirectory.isDirectory()) for (child in fileOrDirectory.listFiles()) deleteRecursive(
        child
    )
    fileOrDirectory.delete()
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val jwtBuilder = JWTBuilder(JWTSharedSecretId, JWTSharedSecret)
        val signupJWT = jwtBuilder.makeJWT(JWTBuilder.JWTPermission.ALL)

        val path = this.applicationContext.filesDir.absolutePath
        deleteRecursive(File(path))
        val sdk1 = SealdSDK(apiURL, appId, "$path/sdk1", databaseEncryptionKeyB64, instanceName = "sdk1", logLevel = -1)
        val sdk2 = SealdSDK(apiURL, appId, "$path/sdk2", databaseEncryptionKeyB64, instanceName = "sdk2", logLevel = -1)
        val sdk3 = SealdSDK(apiURL, appId, "$path/sdk3", databaseEncryptionKeyB64, instanceName = "sdk3", logLevel = -1)

        val user1AccInfo = sdk1.createAccount(signupJWT, "displayName", "deviceName")
        Log.d(TAG, "user1Id: ${user1AccInfo.userId}")
        val user2AccInfo = sdk2.createAccount(signupJWT, "displayName", "deviceName")
        Log.d(TAG, "user2Id: ${user2AccInfo.userId}")
        val user3AccInfo = sdk3.createAccount(signupJWT, "displayName", "deviceName")
        Log.d(TAG, "user3Id: ${user3AccInfo.userId}")

        val groupName = "AC/DC"
        val groupMembers = arrayOf(user1AccInfo.userId, user2AccInfo.userId)
        val groupAdmins = arrayOf(user1AccInfo.userId)
        val groupId = sdk1.createGroup(groupName, groupMembers, groupAdmins)
        sdk1.addGroupMembers(groupId, arrayOf(user3AccInfo.userId), arrayOf(user3AccInfo.userId))
        sdk3.removeGroupMembers(groupId, arrayOf(user2AccInfo.userId))
        sdk3.setGroupAdmins(groupId, arrayOf<String>(), arrayOf(user1AccInfo.userId))

        val recipient = arrayOf(user1AccInfo.userId, user2AccInfo.userId, groupId)
        Log.d(TAG, "recipient ${recipient.joinToString(" ")}")
        val es1SDK1 = sdk1.createEncryptionSession(recipient, true)
        Log.d(TAG, "es1SDK1.sessionId: ${es1SDK1.sessionId}")

        sdk3.renewGroupKey(groupId)

        val clearString = "coucou"
        val encryptedMessage = es1SDK1.encryptMessage(clearString)
        val decryptedMessage = es1SDK1.decryptMessage(encryptedMessage)
        Log.d(TAG, "es1SDK1 encryptedMessage: $encryptedMessage")
        Log.d(TAG, "es1SDK1 decryptedMessage: $decryptedMessage")

        Log.d(TAG, "sdk2 retrieve from message")
        val es1SDK1FromMess = sdk1.retrieveEncryptionSessionFromMessage(encryptedMessage, true)
        val decryptedMessage1FromMess = es1SDK1FromMess.decryptMessage(encryptedMessage)
        Log.d(TAG, "es1SDK1FromMess decryptedMessage1FromMess: $decryptedMessage1FromMess")

        Log.d(TAG, "sdk2 retrieve from id")
        val es1SDK2 = sdk2.retrieveEncryptionSession(es1SDK1.sessionId, true)
        val decryptedMessage2 = es1SDK2.decryptMessage(encryptedMessage)
        Log.d(TAG, "es1SDK2 decryptedMessage2: $decryptedMessage2")

        val es1SDK3FromGroup = sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, true)
        val decryptedMessage3 = es1SDK3FromGroup.decryptMessage(encryptedMessage)
        Log.d(TAG, "es1SDK3FromGroup decryptedMessage2: $decryptedMessage3")

        sdk1.renewKeys(24 * 60 * 60)
        val es1SDK1AfterRenew = sdk1.retrieveEncryptionSession(es1SDK1.sessionId, false)
        val decryptedMessageAfterRenew = es1SDK1AfterRenew.decryptMessage(encryptedMessage)
        Log.d(TAG, "es1SDK1AfterRenew decryptedMessageAfterRenew: $decryptedMessageAfterRenew")

        sdk3.removeGroupMembers(groupId, arrayOf(user1AccInfo.userId, user3AccInfo.userId)) // Delete group

        try {
            sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            Log.d(TAG, "SHOULD NOT HAPPEN")
        } catch (e: Exception) {
            Log.d(TAG, "sdk3 retrieve failed as expected - group deleted")
        }

        es1SDK2.addRecipients(arrayOf(user3AccInfo.userId))
        es1SDK2.revokeRecipients(arrayOf(user1AccInfo.userId))
        // es1 has user2 and user3 as recipients
        try {
            sdk1.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            Log.d(TAG, "SHOULD NOT HAPPEN")
        } catch (e: Exception) {
            Log.d(TAG, "sdk1 retrieve failed as expected - after revokeRecipients")
        }

        val es1SDK3 = sdk3.retrieveEncryptionSession(es1SDK1.sessionId, false)
        val decryptedMessageAfterAdd = es1SDK3.decryptMessage(encryptedMessage)
        Log.d(TAG, "es1SDK3 decryptedMessageAfterAdd: $decryptedMessageAfterAdd")

        es1SDK2.revokeOthers()
        try {
            sdk3.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            Log.d(TAG, "SHOULD NOT HAPPEN")
        } catch (e: Exception) {
            Log.d(TAG, "sdk3 retrieve failed as expected - after revokeOthers")
        }

        es1SDK2.revokeAll()
        try {
            sdk2.retrieveEncryptionSessionFromMessage(encryptedMessage, false)
            Log.d(TAG, "SHOULD NOT HAPPEN")
        } catch (e: Exception) {
            Log.d(TAG, "sdk2 retrieve failed as expected - after revokeAll")
        }
    }
}