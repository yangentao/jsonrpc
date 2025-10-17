package entao

import io.github.yangentao.types.JavaArray
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ArrayTest {
    val arrByte = byteArrayOf(1, 2, 3)
    val arrChar = charArrayOf('1', '2', 'a')
    val arrStr = arrayOf("a", "bc")

    @Test
    fun javaArrayTest() {
        assertTrue(JavaArray.isArray(arrByte))
        assertTrue(JavaArray.isArray(arrChar))
        assertTrue(JavaArray.isArray(arrStr))
        val js = JavaArray(arrStr)
        for (s in js) {
            println(s)
        }
        println(JavaArray(arrByte).map { it.toString() })
        js[0] = "aaa"
        println(arrStr.toList())
    }

    @Suppress("USELESS_IS_CHECK")
    @Test
    fun checkArray() {
        assertFalse(arrByte is Array<*>)
        assertFalse(arrChar is Array<*>)
        assertTrue(arrStr is Array<*>)
        assertTrue(arrByte::class.java.isArray)
        assertTrue(arrChar::class.java.isArray)
        assertTrue(arrStr::class.java.isArray)
    }

}