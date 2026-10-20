package io.xr.harness.app

import io.xr.harness.Fn
import io.xr.harness.core.PreferencesStore
import io.xr.harness.core.TokenCipher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EncryptedTokenStore 行为钉死（saas 仓刀 19 同款）：密文存储/往返、独立持久化、篡改 fail-closed、清除无残留、密文≠明文。 */
class EncryptedTokenStoreTest {

  /** 可逆测试变换：enc(plain) 包裹，解密剥壳；坏密文抛错模拟设备侧失败。 */
  private class MarkCipher : TokenCipher {
    override fun encrypt(plain: String): String = "enc($plain)"

    override fun decrypt(cipherText: String): String =
        if (cipherText.startsWith("enc(") && cipherText.endsWith(")")) {
          cipherText.removePrefix("enc(").removeSuffix(")")
        } else {
          error("密文损坏")
        }
  }

  private class MemPrefs : PreferencesStore {
    val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String) {
      map[key] = value
    }

    override fun remove(key: String) {
      map.remove(key)
    }
  }

  @Test
  @Fn(["M01.F05.I02"])
  fun `save 落密文且读回还原明文`() {
    val prefs = MemPrefs()
    val store = EncryptedTokenStore(prefs, MarkCipher())

    store.save("tk-1", "rt-1")

    assertEquals("enc(tk-1)", prefs.map["tokens.access"])
    assertEquals("tk-1", store.loadAccess())
    assertEquals("rt-1", store.loadRefresh())
  }

  @Test
  @Fn(["M01.F05.I02"])
  fun `access 与 refresh 独立持久化`() {
    val prefs = MemPrefs()
    val store = EncryptedTokenStore(prefs, MarkCipher())

    store.save("tk-1", null)
    assertEquals("tk-1", store.loadAccess())
    assertNull(store.loadRefresh())

    store.save(null, "rt-2")
    assertNull(store.loadAccess())
    assertEquals("rt-2", store.loadRefresh())
  }

  @Test
  @Fn(["M01.F05.I02"])
  fun `篡改密文 fail-closed 返 null 不崩`() {
    val prefs = MemPrefs()
    val store = EncryptedTokenStore(prefs, MarkCipher())
    store.save("tk-1", "rt-1")

    prefs.map["tokens.access"] = "garbage-cipher"

    assertNull(store.loadAccess())
    assertEquals("rt-1", store.loadRefresh())
  }

  @Test
  @Fn(["M01.F05.I02"])
  fun `清除 save null 后无密文残留`() {
    val prefs = MemPrefs()
    val store = EncryptedTokenStore(prefs, MarkCipher())
    store.save("tk-1", "rt-1")

    store.save(null, null)

    assertNull(prefs.map["tokens.access"])
    assertNull(prefs.map["tokens.refresh"])
  }

  @Test
  @Fn(["M01.F05.I02"])
  fun `密文不等于明文（真实 JWT 形状）`() {
    val prefs = MemPrefs()
    val store = EncryptedTokenStore(prefs, MarkCipher())
    val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1LTEifQ.sig-part"

    store.save(jwt, null)

    assertNotEquals(jwt, prefs.map["tokens.access"])
    assertTrue(prefs.map["tokens.access"]!!.startsWith("enc("))
    assertEquals(jwt, store.loadAccess())
  }
}
