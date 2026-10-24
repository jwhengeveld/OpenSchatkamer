package nl.openschatkamer.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.util.Calendar
import java.util.TimeZone

/**
 * Vergelijkt de SRP-berekening met de referentie-implementatie pycognito
 * (zelfde vaste invoer, verwachte uitkomst daar berekend).
 */
class CognitoSrpTest {

    private val fixedA = BigInteger("1f2e3d4c5b6a79881726354453627180fedcba9876543210aabbccddeeff00112233445566778899", 16)

    @Test
    fun `SRP_A en handtekening gelijk aan referentie`() {
        val srp = CognitoSrp(poolName = "YfwXTqT21", fixedA = fixedA)
        assertEquals("338899ec58d3a64cdc91907e11990540ab667843b0043a996503ed5578df72a9da60c94b76e41024970f936daf8f02563071086a1c07cd9b14fde7f2992f71bf87175ad0f2f7896c19d33640916022e8356c010de212b280b55a5752ac4233f2f8624e1c7306a49fc0236f61965295317c08fb5ea3ff7fb5f783ab997c43d8c130a4237e4c0af51438a2c73f352e9edc81d3dce7aa1734e4e025e85a406faaedfb9228d4025eefe1f56ad935d735aabde98c4c4d23b6ba3581ba5e57054300d0c4c198bf94ddb6ce49d6f7042830bbd42e62a063d25a73a7ef03a1af35a8d9514586a2ce55f2c5b4e65eca4540008a9f37cd2d8b55083878842b813bf36ea65df8c5ef098b96dff5029faea08c65da51f2cb5f4f40df04a00f977cbee06cb70d03330053332b10b17776b04bf35a57179c16cda5c837da7e0a7acbb7fb09f3d4ee0884c27b5ee315529ec81396745181e903434fd51480c9eeacb5b52a8f20e33523df4697ef052b7a2cf4ad4b803bfc20f41c3558542e95a0c5b0be6ee28f19", srp.srpA)

        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026, Calendar.OCTOBER, 24, 6, 5, 9)
        }.time
        val (signature, timestamp) = srp.passwordVerifier(
            userIdForSrp = "user-1234",
            password = "Voorbeeld-Wachtwoord-1",
            srpB = "e8b3c97a91075c6a12d3078fdebc51daf4482a6539feecb70ee25d982c6129ed4e1c8891754035b4189e1f68d20cd04eb1ea50d2d8ef27d015f9b98e4f70c6a5342bc1088ed8450bc09684acddf38e50026e9689c9691f435ec31322ce3dab3ce77c3a1a06fb2e71e2ca7e90bea6de0d0a082d50dfcd4d1130463edc9bc7bc0edcc4c8455c9b8ff1a87696b2db041eb8ed6397c742663cf8b4c8533d0998ba9738f74411a0b4955a8c0dbd6d196edbe0d25cc72d43ace42e4340dca8c0d9f2fe3e15e6186b47eeaa85fa2e141b5ab2c07af90c38b15b54d417b43569a9de70086bb3b4e3aa97eb2e031b65bcbf206dc6dba680607fe267eaf70dd1f500f396063ea066bfa5670b01485b3941945800719f7baa4301a5ab8ef716dcae49311021377f0eed8851241555ad24ec9235c79970272f4bdb0b51b238d37b0636d47b56792d8ce3f4e287ce032baa882e413f7230629d57f41329c762f215f39b56d181db1f28294208ed1ee70b7e20b3234a78c4b875291a4a727342565bf96396a633",
            salt = "a1b2c3d4e5f60718",
            secretBlock = "Z2VoZWltLWJsb2stdm9vci10ZXN0LTEyMzQ1Njc4OTA=",
            now = now
        )
        assertEquals("Sat Oct 24 06:05:09 UTC 2026", timestamp)
        assertEquals("Yfn2QXvWltL13jeHmbwJ+U5EH8WwzVivEHnNBf26dQQ=", signature)
    }

    @Test
    fun `dag zonder voorloopnul`() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.NOVEMBER, 3, 9, 7, 1) }.time
        assertEquals("Tue Nov 3 09:07:01 UTC 2026", CognitoSrp.timestamp(now))
    }
}
