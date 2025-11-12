@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.KsonValue
import io.github.yangentao.types.ICaseMap
import kotlin.reflect.KProperty

open class RpcContext(val session: MutableMap<String, Any> = LinkedHashMap(), extras: Map<String, Any> = emptyMap()) {
    val timeNow: Long = System.currentTimeMillis()
    val extras: ICaseMap<Any> = ICaseMap(extras)
    val outputs: MutableMap<String, Any> = LinkedHashMap()
    var committed: Boolean = false
        private set
    var response: RpcResponse? = null
        private set

    fun commitNotify() {
        if (committed) error("Already Committed")
        committed = true
        this.response = null
    }

    fun response(response: RpcResponse): RpcResponse {
        if (committed) error("Already Committed")
        committed = true
        this.response = response
        return response
    }

    fun success(id: KsonValue, result: KsonValue): RpcResponse {
        return response(RpcResponse.success(id, result))
    }

    fun failed(id: KsonValue, code: Int, message: String, data: KsonValue? = null): RpcResponse {
        return response(RpcResponse.failed(id, message, code = code, data = data))
    }

    fun failed(id: KsonValue, error: RpcError): RpcResponse {
        return response(RpcResponse.failed(id, error))
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

