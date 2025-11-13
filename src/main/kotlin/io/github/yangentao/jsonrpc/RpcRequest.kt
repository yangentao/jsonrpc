@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.*
import kotlin.reflect.KProperty

class RpcRequest(val id: KsonValue, val method: String, val params: KsonValue?) : RpcPacket() {
    init {
        assert(id.isNull || id is KsonString || id is KsonNum)
        assert(method.isNotEmpty())
    }

    val isNotify: Boolean get() = id is KsonNull
    val hasParams: Boolean = when (params) {
        is KsonObject -> params.isNotEmpty()
        is KsonArray -> params.isNotEmpty()
        else -> false
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

    override fun onJson(jo: KsonObject) {
        when (id) {
            KsonNull -> {}
            is KsonString, is KsonNum -> jo.putAny(Rpc.ID, id)
            else -> error("Json rpc id MUST be num or string")
        }
        jo.putString(Rpc.METHOD, method)
        when (params) {
            is KsonArray if params.isNotEmpty() -> jo.putArray(Rpc.PARAMS, params)
            is KsonObject if params.isNotEmpty() -> jo.putObject(Rpc.PARAMS, params)
            is KsonNull -> {}
            else -> error("Json rpc params MUST be an array or an object")
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T {
        val ob = this.params as? KsonObject ?: return null as T
        val kv = ob[property.userName] ?: return null as T
        return KsonDecoder.decode(property, kv) as T
    }

    companion object {
        fun notify(method: String, params: KsonValue?): RpcRequest {
            return RpcRequest(KsonNull, method, params)
        }

        fun method(method: String, params: KsonValue?): RpcRequest {
            return RpcRequest(KsonNum(Rpc.nextID), method, params)
        }

        fun from(jo: KsonObject): RpcRequest? {
            if (!jo.verifyVersion) return null
            val id: KsonValue = jo[Rpc.ID] ?: KsonNull
            val method: String = jo.getString(Rpc.METHOD) ?: return null
            val params: KsonValue? = jo[Rpc.PARAMS]
            return RpcRequest(id, method, params)
        }

        fun fromBatch(ja: KsonArray): List<RpcRequest> {
            return ja.objectList.mapNotNull { from(it) }
        }
    }
}

object RpcRequestParams {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcRequest, property: KProperty<*>): T {
        return inst.getValue(null, property)
    }
}


