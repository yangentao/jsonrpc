@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.KsonNull
import io.github.yangentao.kson.KsonObject
import io.github.yangentao.kson.KsonValue
import io.github.yangentao.kson.ksonObject

object Rpc {
    const val JSONRPC = "jsonrpc"
    const val VERSION = "2.0"
    const val ID = "id"
    const val METHOD = "method"
    const val PARAMS = "params"
    const val RESULT = "result"
    const val ERROR = "error"
    const val CODE = "code"
    const val MESSAGE = "message"
    const val DATA = "data"

    private var autoID: Long = 1

    val nextID: Long get() = autoID++

    fun detectPacket(jo: KsonObject): RpcPacket? {
        if (!jo.verifyVersion) return null
        if (jo.containsKey(RESULT) || jo.containsKey(ERROR)) return RpcResponse.from(jo)
        if (jo.containsKey(PARAMS)) return RpcRequest.from(jo)
        if (jo.containsKey(METHOD)) return RpcRequest.from(jo)
        return null
    }
}

open class RpcException(val id: KsonValue, val error: RpcError) : Exception(error.message) {
    constructor(error: RpcError) : this(KsonNull, error)
    constructor(message: String, code: Int = -1, data: KsonValue? = null ) : this(KsonNull, RpcError(message, code, data = data ))

    companion object {
        val badValue = RpcException("无效数据")
        val invalidAccount = RpcException("无效账号")
    }
}

sealed class RpcPacket(val version: String = Rpc.VERSION) {
    fun toJson(): KsonObject {
        val jo = KsonObject()
        jo.putString(Rpc.JSONRPC, version)
        onJson(jo)
        return jo
    }

    abstract fun onJson(jo: KsonObject)

    override fun toString(): String {
        return toJson().toString()
    }
}

internal val KsonObject.verifyVersion: Boolean get() = this.getString(Rpc.JSONRPC) == Rpc.VERSION

internal fun ksonObject(params: List<Pair<String, Any?>>): KsonObject {
    val jo = KsonObject()
    for (p in params) {
        jo.putAny(p.first, p.second)
    }
    return jo
}

class CallError(val message: String, val code: Int = -1, val data: Any? = null) {
    fun toJson(): KsonObject {
        return ksonObject("id" to code, "message" to message, "data" to data)
    }

    override fun toString(): String {
        val s = toJson().toString()
        return "CallError: $s "
    }
}

class CallResult<T : Any> private constructor(val result: T? = null, val error: CallError? = null) {
    val success: Boolean get() = error == null
    val failed: Boolean get() = error != null

    val requiredResult: T
        get() {
            assert(error == null && result != null)
            return result!!
        }

    val errorCode: Int? get() = error?.code
    val errorMessage: String? get() = error?.message
    val errorData: Any? get() = error?.data

    @Suppress("IfThenToElvis")
    override fun toString(): String {
        val s = if (error != null) error.toString() else "$result"
        return "CallResult: $s "
    }

    companion object {
        fun <T : Any> success(result: T?): CallResult<T> = CallResult<T>(result = result)
        fun <T : Any> failed(message: String, code: Int = -1, data: Any? = null): CallResult<T> = CallResult<T>(error = CallError(message = message, code = code, data = data))
    }
}



