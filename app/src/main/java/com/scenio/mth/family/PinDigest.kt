package com.scenio.mth.family

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinDigest {
    const val ITERATIONS=210_000
    fun valid(pin:String)=pin.matches(Regex("[0-9]{6}"))
    fun salt()=ByteArray(16).also { SecureRandom().nextBytes(it) }
    fun derive(pin:String,salt:ByteArray,algorithm:String):ByteArray {
        require(valid(pin) && salt.size==16 && algorithm in listOf("PBKDF2WithHmacSHA256","PBKDF2WithHmacSHA1"))
        val spec=PBEKeySpec(pin.toCharArray(),salt,ITERATIONS,256)
        return try { SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun matches(pin:String,salt:ByteArray,hash:ByteArray,algorithm:String)=valid(pin) && MessageDigest.isEqual(derive(pin,salt,algorithm),hash)
}
