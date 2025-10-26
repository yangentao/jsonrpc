@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.KsonValue
import kotlin.reflect.KProperty

open class RpcContext(val session: MutableMap<String, Any> = LinkedHashMap(), val extras: Map<String, Any> = emptyMap()) {
    val outputs: MutableMap<String, Any> = LinkedHashMap()
    var committed: Boolean = false
        private set
    val timeNow: Long = System.currentTimeMillis()
    private var responseValue: RpcResponse? = null

    val response: RpcResponse get() = responseValue!!

    fun response(response: RpcResponse) {
        if (committed) error("Already Committed")
        committed = true
        this.responseValue = response
    }

    fun success(id: KsonValue, result: KsonValue): RpcResponse {
        response(if (id.isNull) RpcNoResponse else RpcResult(id, result))
        return this.response
    }

    fun failed(id: KsonValue, code: Int, message: String, data: KsonValue? = null): RpcResponse {
        return failed(id, RpcError(code, message, data))
    }

    fun failed(id: KsonValue, error: RpcError): RpcResponse {
        response(if (id.isNull) RpcNoResponse else RpcFailed(id, error))
        return this.response
    }
}

object RpcContextSessions {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcContext, property: KProperty<*>): T {
        return inst.session[property.userName] as T
    }

    operator fun <T> setValue(inst: RpcContext, property: KProperty<*>, value: T) {
        if (value == null) {
            inst.session.remove(property.userName)
        } else {
            inst.session[property.userName] = value
        }
    }
}

object RpcContextOutputs {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcContext, property: KProperty<*>): T {
        return inst.outputs[property.userName] as T
    }

    operator fun <T> setValue(inst: RpcContext, property: KProperty<*>, value: T) {
        if (value == null) {
            inst.outputs.remove(property.userName)
        } else {
            inst.outputs[property.userName] = value
        }
    }
}

object RpcContextExtras {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(inst: RpcContext, property: KProperty<*>): T {
        return inst.extras[property.userName] as T
    }
}

