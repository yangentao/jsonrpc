@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.*

class RpcResponse(val id: KsonValue, val result: KsonValue, val error: RpcError?) : RpcPacket() {
    init {
        assert(id is KsonNum || id is KsonString)
        assert(!(!result.isNull && error != null))
    }

    val isSuccess: Boolean get() = error == null
    val isError: Boolean get() = error != null

    override fun onJson(jo: KsonObject) {
        super.onJson(jo)
        jo.putAny(Rpc.ID, id)
        if (error == null) {
            assert(id is KsonString || id is KsonNum)
            jo.putAny(Rpc.RESULT, result)
        } else {
            val ed = error.data
            jo.putObject(Rpc.ERROR) {
                putInt(Rpc.CODE, error.code)
                putString(Rpc.MESSAGE, error.message)
                if (ed != null && !ed.isNull) {
                    putAny(Rpc.DATA, ed)
                }
            }
        }
    }

    companion object {
        fun success(id: KsonValue, result: KsonValue): RpcResponse {
            return RpcResponse(id, result, null)
        }

        fun failed(id: KsonValue, code: Int, message: String, data: KsonValue? = null): RpcResponse {
            return RpcResponse(id, KsonNull, RpcError(code, message, data ?: KsonNull))
        }

        fun error(id: KsonValue, error: RpcError): RpcResponse {
            return RpcResponse(id, KsonNull, error)
        }

        fun from(jo: KsonObject): RpcResponse? {
            if (!jo.verifyVersion) return null
            val id: KsonValue = jo[Rpc.ID] ?: KsonNull
            val result = jo[Rpc.RESULT]
            if (result != null && id !is KsonNull) {
                return success(id, result)
            }
            val error = jo.getObject(Rpc.ERROR)
            if (error != null) {
                val code: Int = error.getInt(Rpc.CODE) ?: 0
                val message: String = error.getString(Rpc.MESSAGE) ?: "Failed"
                return failed(id, code, message, error[Rpc.DATA])
            }
            return null
        }

        fun from(ja: KsonArray): List<RpcResponse> {
            return ja.objectList.mapNotNull { from(it) }
        }
    }
}

data class RpcError(val code: Int, val message: String, val data: KsonValue? = null) {
    companion object {
        val parse = RpcError(32700, "Parse error")
        val invalidRequest = RpcError(32600, "Invalid Request")
        val methodNotFound = RpcError(32601, "Method NOT Found")
        val invalidParams = RpcError(32602, "Invalid Params")
        val internal = RpcError(32603, "Internal Error")

        fun internalError(data: String?): RpcError {
            return if (data != null) {
                RpcError(32603, "Internal Error", KsonString(data))
            } else internal
        }

        fun server(code: Int, message: String, data: KsonValue? = null): RpcError {
            assert(code in 32000..32099)
            return RpcError(code, message, data)
        }
    }
}





