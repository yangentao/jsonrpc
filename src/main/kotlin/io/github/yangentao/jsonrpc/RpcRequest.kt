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

    val hasParams: Boolean = when (params) {
        is KsonObject -> params.isNotEmpty()
        is KsonArray -> params.isNotEmpty()
        else -> false
    }

    val isNotify: Boolean get() = id is KsonNull

    override fun onJson(jo: KsonObject) {
        super.onJson(jo)
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

    companion object {
        fun notify(method: String, params: KsonValue?): RpcRequest {
            return RpcRequest(KsonNull, method, params)
        }

        fun method(method: String, params: KsonValue?): RpcRequest {
            return RpcRequest(KsonNum(Rpc.nextID), method, params)
        }

        @Throws(RpcInvalidRequestException::class)
        fun from(jo: KsonObject): RpcRequest? {
            if (!jo.verifyVersion) return null
            val id: KsonValue = jo[Rpc.ID] ?: KsonNull
            val method: String = jo.getString(Rpc.METHOD) ?: throw RpcInvalidRequestException(id)
            val params: KsonValue? = jo[Rpc.PARAMS]
            return RpcRequest(id, method, params)
        }

        @Throws(RpcInvalidRequestException::class)
        fun from(ja: KsonArray): List<RpcRequest> {
            return ja.objectList.mapNotNull { from(it) }
        }
    }
}

object RpcRequestParameter {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> getValue(inst: RpcRequest, property: KProperty<*>): T? {
        val ob = inst.params as? KsonObject ?: return null
        val kv = ob[property.userName] ?: return null
        return KsonDecoder.decode(property, kv) as? T
    }
}


