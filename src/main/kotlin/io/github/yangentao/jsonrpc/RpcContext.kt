@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.*
import io.github.yangentao.types.AttrStore
import kotlin.reflect.KProperty

open class RpcContext(val request: RpcRequest) {
    val id: KsonValue = request.id
    val method: String = request.method
    val params: KsonValue = request.params ?: KsonNull
    val isNotify: Boolean get() = request.id.isNull

    var response: RpcResponse? = null
        private set

    var committed: Boolean = false
        private set

    val attrs: AttrStore = AttrStore()

    val hasParams: Boolean
        get() {
            return when (params) {
                is KsonObject -> params.isNotEmpty()
                is KsonArray -> params.isNotEmpty()
                else -> false
            }
        }

    fun getParam(name: String): KsonValue? {
        return (params as? KsonObject)?.get(name)
    }

    fun getParam(index: Int): KsonValue? {
        return (params as? KsonArray)?.getOrNull(index)
    }

    fun getInt(name: String): Int? {
        return (params as? KsonObject)?.getInt(name)
    }

    fun getLong(name: String): Long? {
        return (params as? KsonObject)?.getLong(name)
    }

    fun getString(name: String): String? {
        return (params as? KsonObject)?.getString(name)
    }

    fun getBool(name: String): Boolean? {
        return (params as? KsonObject)?.getBool(name)
    }

    fun getInt(index: Int): Int? {
        val kv = getParam(index) ?: return null
        if (kv is KsonNum) return kv.data.toInt()
        return null
    }

    fun getLong(index: Int): Long? {
        val kv = getParam(index) ?: return null
        if (kv is KsonNum) return kv.data.toLong()
        return null
    }

    fun getString(index: Int): String? {
        val kv = getParam(index) ?: return null
        if (kv is KsonString) return kv.data
        return null
    }

    fun getBool(index: Int): Boolean? {
        val kv = getParam(index) ?: return null
        if (kv is KsonBool) return kv.data
        return null
    }

    fun success(result: KsonValue) {
        if (committed) error("Already Committed")
        committed = true
        if (isNotify) return
        this.response = RpcResponse(request.id, result, null)
    }

    fun failed(code: Int, message: String, data: KsonValue? = null) {
        if (committed) error("Already Committed")
        committed = true
        if (isNotify) return
        this.response = RpcResponse(request.id, KsonNull, RpcError(code, message, data))
    }

    fun failed(error: RpcError) {
        if (committed) error("Already Committed")
        committed = true
        if (isNotify) return
        this.response = RpcResponse(request.id, KsonNull, error)
    }
}

object RpcContextAttribute {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcContext, property: KProperty<*>): T {
        return inst.attrs.map[property.userName] as T
    }

    operator fun <T> setValue(inst: RpcContext, property: KProperty<*>, value: T) {
        if (value == null) {
            inst.attrs.map.remove(property.userName)
        } else {
            inst.attrs.map[property.userName] = value
        }
    }
}
