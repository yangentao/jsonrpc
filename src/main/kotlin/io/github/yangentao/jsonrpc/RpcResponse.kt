@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.*

class RpcResponse(val id: KsonValue, val result: KsonValue? = null, val error: RpcError? = null) : RpcPacket() {
    init {
        assert(result == null || error == null);
        if (error == null) {
            assert(id is KsonNum || id is KsonString)
        } else {
            assert(id is KsonNum || id is KsonString || id is KsonNull)
        }
    }

    override fun onJson(jo: KsonObject) {
        jo.putAny(Rpc.ID, id)
        if (failed) {
            jo.putObject(Rpc.ERROR, error!!.toJson())
        } else {
            jo.putAny(Rpc.RESULT, result)
        }
    }

    val success: Boolean get() = error == null
    val failed: Boolean get() = error != null

    val longID: Long get() = (id as KsonNum).data.toLong()

    val jsonText: String get() = this.toString()

    companion object {
        fun success(id: KsonValue, result: KsonValue): RpcResponse {
            return RpcResponse(id, result = result)
        }

        fun failed(id: KsonValue, error: RpcError): RpcResponse {
            return RpcResponse(id, error = error)
        }

        fun failed(id: KsonValue, message: String, code: Int = -1, data: KsonValue? = null): RpcResponse {
            return failed(id, RpcError(message, code, data))
        }

        fun from(jo: KsonObject): RpcResponse? {
            if (!jo.verifyVersion) return null
            val id: KsonValue = jo[Rpc.ID] ?: throw RpcError.invalidRequest.exception()
            val error = jo.getObject(Rpc.ERROR)
            if (error != null) {
                return RpcResponse.failed(id, error.getString(Rpc.MESSAGE) ?: "Failed", code = error.getInt(Rpc.CODE) ?: 0, data = error[Rpc.DATA])
            }
            val result = jo[Rpc.RESULT]
            return RpcResponse.success(id, result ?: KsonNull)
        }

        fun from(ja: KsonArray): List<RpcResponse> {
            return ja.objectList.mapNotNull { from(it) }
        }
    }
}
