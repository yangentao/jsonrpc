@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.KsonObject
import io.github.yangentao.kson.KsonValue
import kotlin.reflect.KProperty

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

open class RpcException(message: String) : Exception(message)
class RpcInvalidRequestException(val id: KsonValue) : RpcException("Invalid Request")

sealed class RpcPacket(val version: String = Rpc.VERSION) {
    fun toJson(): KsonObject {
        val jo = KsonObject()
        jo.putString(Rpc.JSONRPC, version)
        onJson(jo)
        return jo
    }

    open fun onJson(jo: KsonObject) {

    }

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

fun interface TextSender {
    fun sendText(text: String): Boolean
}

fun interface TextReceiver {
    fun onRecvText(text: String): Boolean
}

interface RpcSession {
    fun removeSession(name: String)
    fun getSession(name: String): Any?
    fun putSession(name: String, value: Any?)
}

interface RpcExtra {
    fun getExtra(name: String): Any?
}

object RpcEmptyExtra : RpcExtra {
    override fun getExtra(name: String): Any? {
        return null
    }

}

object RpcEmptySession : RpcSession {
    override fun removeSession(name: String) {
    }

    override fun getSession(name: String): Any? {
        return null
    }

    override fun putSession(name: String, value: Any?) {
    }
}

object RpcSessionPrpoerties {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcSession, property: KProperty<*>): T {
        return inst.getSession(property.userName) as T
    }

    operator fun <T> setValue(inst: RpcSession, property: KProperty<*>, value: T) {
        if (value == null) {
            inst.removeSession(property.userName)
        } else {
            inst.putSession(property.userName, value)
        }
    }
}

object RpcExtraProperties {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcExtra, property: KProperty<*>): T {
        return inst.getExtra(property.userName) as T
    }
}

