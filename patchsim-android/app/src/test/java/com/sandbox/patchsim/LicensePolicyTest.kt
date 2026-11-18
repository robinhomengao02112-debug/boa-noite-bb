package com.sandbox.patchsim

import com.sandbox.patchsim.core.model.LicenseVerdict
import com.sandbox.patchsim.core.sim.LicensePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensePolicyTest {

    @Test fun defaults_to_licensed() {
        val policy = LicensePolicy()
        val r = policy.evaluate("pkg", "nonce-1")
        assertEquals(LicenseVerdict.LICENSED, r.verdict)
        assertTrue(r.allowAccess)
        assertEquals("nonce-1", r.nonce)
    }

    @Test fun configured_not_licensed_denies() {
        val policy = LicensePolicy()
        policy.configure("pkg", LicenseVerdict.NOT_LICENSED)
        val r = policy.evaluate("pkg", "n")
        assertEquals(LicenseVerdict.NOT_LICENSED, r.verdict)
        assertFalse(r.allowAccess)
    }

    @Test fun error_and_retry_do_not_allow_access() {
        val policy = LicensePolicy()
        policy.configure("a", LicenseVerdict.ERROR_SERVER)
        policy.configure("b", LicenseVerdict.RETRY)
        assertFalse(policy.evaluate("a", "n").allowAccess)
        assertFalse(policy.evaluate("b", "n").allowAccess)
    }
}
