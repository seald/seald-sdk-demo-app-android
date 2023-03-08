package io.seald.go_sdk_demo_app

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.util.*
import javax.crypto.SecretKey;

class JWTBuilder(JWTSharedSecretId: String, JWTSharedSecret: String) {
    private val JWTSharedSecretId: String
    private val JWTSharedSecret: SecretKey

    init {
        this.JWTSharedSecretId = JWTSharedSecretId
        this.JWTSharedSecret = Keys.hmacShaKeyFor(JWTSharedSecret.toByteArray())
    }

    enum class JWTPermission(val perm: Int) {
        ALL(-1),
        ANONYMOUS_CREATE_MESSAGE(0),
        ANONYMOUS_FIND_KEY(1),
        ANONYMOUS_FIND_SIGCHAIN(2),
        JOIN_TEAM(3),
        ADD_CONNECTOR(4),
    }

    fun signupJWT(): String {
        val date = Date()
        val expiryDate = Date(date.time + 2 * 60 * 60 * 1000)

        return Jwts.builder()
            .setHeaderParam("alg", "HS256")
            .setHeaderParam("typ", "JWT")
            .claim("join_team", true)
            .claim("scopes", JWTPermission.JOIN_TEAM)
            .setId(UUID.randomUUID().toString())
            .setIssuer(JWTSharedSecretId)
            .setIssuedAt(date)
            .setExpiration(expiryDate)
            .signWith(JWTSharedSecret, SignatureAlgorithm.HS256)
            .compact()
    }

    fun connectorJWT(customUserId: String, appId: String): String {
        val date = Date()
        val expiryDate = Date(date.time + 2 * 60 * 60 * 1000)

        return Jwts.builder()
            .setHeaderParam("alg", "HS256")
            .setHeaderParam("typ", "JWT")
            .claim("scopes", JWTPermission.ADD_CONNECTOR)
            .claim("connector_add", mapOf("type" to "AP", "value" to "$customUserId@$appId"))
            .setIssuer(JWTSharedSecretId)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(date)
            .setExpiration(expiryDate)
            .signWith(JWTSharedSecret, SignatureAlgorithm.HS256)
            .compact()
    }

}