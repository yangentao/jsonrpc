package entao

import io.github.yangentao.types.EList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EListTest {

    @Test
    fun grow() {
        val ls: EList<Int> = EList(100)
        ls.addAll(1..100)
        assertEquals(100, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
        assertEquals(100, ls[99])
        println(ls)
    }

    @Test
    fun charArr() {
        val ls = EList<Int>(charArrayOf('1', '2', '3'))
        assertEquals(3, ls.size)
        assertEquals('1', ls[0])
        assertEquals('2', ls[1])
    }

    @Test
    fun intArr() {
        val ls = EList<Int>(intArrayOf(1, 2, 3))
        assertEquals(3, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
    }

    @Test
    fun typesInt() {
        val ls = EList(Int::class)
        ls.add(1)
        ls.add(2)
        ls.addAll(arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0))
        assertEquals(32, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
    }

    @Test
    fun types() {
        val ls = EList(String::class)
        ls.add("1")
        ls.add("2")
        assertEquals(2, ls.size)
        assertEquals("1", ls[0])
        assertEquals("2", ls[1])
    }

    @Test
    fun remove() {
        val ls = EList.of(1, 2, 3)
        ls.removeAt(1)
        assertEquals(2, ls.size)
        assertEquals(1, ls[0])
        assertEquals(3, ls[1])
        assertFails { ls[2] }
    }

    @Test
    fun add() {
        val ls = EList.of(1, 2)
        ls.add(4)
        assertEquals(3, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
        assertEquals(4, ls[2])
        assertFails { ls[3] }
    }

    @Test
    fun create() {
        val ls = EList.of(1, 2, 3)
        assertEquals(3, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
        assertEquals(3, ls[2])
        assertFails { ls[3] }
    }

    @Test
    fun create2() {
        val ls = EList(arrayOf(1, 2, 3))
        assertEquals(3, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
        assertEquals(3, ls[2])
        assertFails { ls[3] }
    }

    @Test
    fun create3() {
        val ls = EList(listOf(1, 2, 3))
        assertEquals(3, ls.size)
        assertEquals(1, ls[0])
        assertEquals(2, ls[1])
        assertEquals(3, ls[2])
        assertFails { ls[3] }
    }
}