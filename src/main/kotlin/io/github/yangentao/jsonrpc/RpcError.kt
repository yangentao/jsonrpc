package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.*

data class RpcError(val message: String, val code: Int = -1, val data: KsonValue? = null) {

    fun toJson(): KsonObject {
        if (data == null || data.isNull) {
            return ksonObject(Rpc.CODE to code, Rpc.MESSAGE to message)
        }
        return ksonObject(Rpc.CODE to code, Rpc.MESSAGE to message, Rpc.DATA to data)
    }

    fun with(data: KsonValue?): RpcError {
        return RpcError(message, code, data)
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

fun RpcError.error(id: KsonValue = KsonNull): Nothing {
    throw RpcException(id, this)
}




