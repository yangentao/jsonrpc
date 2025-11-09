@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.*

object RpcNoResponse : RpcResponse()

class RpcResult(val id: KsonValue, val result: KsonValue) : RpcResponse() {
    init {
        assert(id is KsonNum || id is KsonString)
    }

    override fun onJson(jo: KsonObject) {
        super.onJson(jo)
        jo.putAny(Rpc.ID, id)
        jo.putAny(Rpc.RESULT, result)
    }
}

class RpcFailed(val id: KsonValue, val error: RpcError) : RpcResponse() {
    constructor(id: KsonValue, code: Int, message: String, data: KsonValue? = null) : this(id, RpcError(message, code, data))

    init {
        assert(id.isNull || id is KsonNum || id is KsonString)
    }

    override fun onJson(jo: KsonObject) {
        super.onJson(jo)
        if (!id.isNull) {
            jo.putAny(Rpc.ID, id)
        }
        jo.putObject(Rpc.ERROR, error.toJson())
    }
}

sealed class RpcResponse() : RpcPacket() {

    val longID: Long
        get() {
            return when (this) {
                is RpcResult -> (id as KsonNum).data.toLong()
                is RpcFailed -> (id as? KsonNum)?.data?.toLong() ?: -1L
                is RpcNoResponse -> -1L
            }
        }

    val jsonText: String?
        get() {
            return when (this) {
                is RpcResult -> this.toString()
                is RpcFailed -> this.toString()
                is RpcNoResponse -> null
            }
        }

    companion object {
        fun from(jo: KsonObject): RpcResponse? {
            if (!jo.verifyVersion) return null
            val id: KsonValue = jo[Rpc.ID] ?: KsonNull
            val result = jo[Rpc.RESULT]
            if (result != null && !id.isNull) {
                return RpcResult(id, result)
            }
            val error = jo.getObject(Rpc.ERROR)
            if (error != null) {
                val code: Int = error.getInt(Rpc.CODE) ?: 0
                val message: String = error.getString(Rpc.MESSAGE) ?: "Failed"
                return RpcFailed(id, code, message, error[Rpc.DATA])
            }
            return null
        }

        fun from(ja: KsonArray): List<RpcResponse> {
            return ja.objectList.mapNotNull { from(it) }
        }
    }
}

data class RpcError(val message: String, val code: Int = -1, val data: KsonValue? = null) {

    fun toJson(): KsonObject {
        if (data == null || data.isNull) {
            return ksonObject(Rpc.CODE to code, Rpc.MESSAGE to message)
        }
        return ksonObject(Rpc.CODE to code, Rpc.MESSAGE to message, Rpc.DATA to data)
    }

    companion object {
        val parse = RpcError("Parse error", 32700)
        val invalidRequest = RpcError("Invalid Request", 32600)
        fun methodNotFound(data: KsonValue? = null) = RpcError("Method NOT Found", 32601, data = data)

        //        val invalidParams = RpcError(32602, "Invalid Params")
        val internal = RpcError("Internal Error", 32603)
        val unauthorized = RpcError("Unauthorized", 401)

        fun invalidParam(param: String = ""): RpcError {
            return RpcError("参数错误 $param ", 32602)
        }

        fun internalError(data: String?): RpcError {
            return if (data != null) {
                RpcError("Internal Error", 32603, KsonString(data))
            } else internal
        }

        fun server(code: Int, message: String, data: KsonValue? = null): RpcError {
            assert(code in 32000..32099)
            return RpcError(message, code, data)
        }
    }
}

fun RpcError.exception(id: KsonValue = KsonNull): RpcException {
    return RpcException(id, this)
}





