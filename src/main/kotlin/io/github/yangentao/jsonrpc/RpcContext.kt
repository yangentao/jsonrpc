@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.KsonArray
import io.github.yangentao.kson.KsonNull
import io.github.yangentao.kson.KsonObject
import io.github.yangentao.kson.KsonValue
import io.github.yangentao.types.AttrStore

open class RpcContext(val request: RpcRequest) {
    val id: KsonValue = request.id
    val method: String = request.method
    val params: KsonValue = request.params ?: KsonNull
    val isNotify: Boolean get() = request.id.isNull

    var response: RpcResponse? = null
        private set

    var committed: Boolean = false
        private set

    val attrs: AttrStore = AttrStore()

    val hasParams: Boolean
        get() {
            return when (params) {
                is KsonObject -> params.isNotEmpty()
                is KsonArray -> params.isNotEmpty()
                else -> false
            }
        }

    fun intParam(name: String): Int? {
        return (params as? KsonObject)?.getInt(name)
    }

    fun longParam(name: String): Long? {
        return (params as? KsonObject)?.getLong(name)
    }

    fun stringParam(name: String): String? {
        return (params as? KsonObject)?.getString(name)
    }

    fun boolParam(name: String): Boolean? {
        return (params as? KsonObject)?.getBool(name)
    }

    fun success(result: KsonValue) {
        if (committed) error("Already Committed")
        committed = true
        if (isNotify) return
        this.response = RpcResponse(request.id, result, null)
    }

    fun failed(code: Int, message: String, data: KsonValue? = null) {
        if (committed) error("Already Committed")
        committed = true
        if (isNotify) return
        this.response = RpcResponse(request.id, KsonNull, RpcError(code, message, data))
    }

    fun failed(error: RpcError) {
        if (committed) error("Already Committed")
        committed = true
        if (isNotify) return
        this.response = RpcResponse(request.id, KsonNull, error)
    }
}