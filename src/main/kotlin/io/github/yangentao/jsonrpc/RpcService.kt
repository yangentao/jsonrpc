@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.Kson
import io.github.yangentao.kson.KsonArray
import io.github.yangentao.kson.KsonObject
import io.github.yangentao.kson.ksonArray
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class RpcService(workerCount: Int = 4) {
    val server: RpcServer = RpcServer()
    val client: RpcClient = RpcClient(workerCount)

    fun onResponse(response: RpcResponse) {
        client.onResponse(response)
    }

    fun send(method: String, params: List<Pair<String, Any?>>, callback: RpcCallback?, timeoutMS: Long = 20_000): String {
        return send(method, ksonObject(params), callback, timeoutMS)
    }

    fun send(method: String, params: KsonObject, callback: RpcCallback?, timeoutMS: Long = 20_000): String {
        return client.send(method, params, callback, timeoutMS)
    }

    fun batch(items: List<BatchItem>): String {
        return client.batch(items)
    }

    fun onRequest(request: RpcRequest): RpcResponse? {
        return server.onRequest(request)
    }

    fun onRequest(context: RpcContext): RpcResponse? {
        return server.dispatch(context)
    }

    fun beforeLambda(lambda: Function2<RpcContext, RpcAction, Unit>) {
        server.beforeLambda(lambda)
    }

    fun afterLambda(lambda: Function2<RpcContext, RpcAction, Unit>) {
        server.afterLambda(lambda)
    }

    /**
     * beforeXX(context:RpcContext, action:RpcAction){}
     */
    fun before(func: KFunction<Unit>) {
        server.before(func)
    }

    /**
     * afterXX(context:RpcContext, action:RpcAction){}
     */
    fun after(func: KFunction<Unit>) {
        server.after(func)
    }

    fun interceptClass(cls: KClass<out RpcInterceptor>) {
        server.interceptClass(cls)
    }

    fun intercept(obj: RpcInterceptor) {
        server.intercept(obj)
    }

    fun add(action: KFunction<*>) {
        server.add(action)
    }

    @Synchronized
    fun add(method: String, action: KFunction<*>) {
        server.add(method, action)
    }

    fun addGroup(group: KClass<*>, noGroupName: Boolean = false) {
        server.addGroup(group, noGroupName)
    }

    fun onRecvPacket(jo: KsonObject, acceptor: (RpcRequest) -> Boolean): RpcResponse? {
        val p = Rpc.detectPacket(jo)
        if (p is RpcRequest) {
            return if (acceptor(p)) {
                onRequest(p)
            } else {
                null
            }
        }
        if (p is RpcResponse) {
            onResponse(p)
        }
        return null
    }

    fun onRecv(textPacket: String, acceptor: (RpcRequest) -> Boolean): String? {
        try {
            val jv = Kson.parse(textPacket) ?: return null
            if (jv is KsonObject) {
                return onRecvPacket(jv, acceptor)?.toString()
            }
            if (jv is KsonArray) {
                val ls = jv.objectList.mapNotNull {
                    onRecvPacket(it, acceptor)
                }
                return if (ls.isEmpty()) null else ksonArray(ls.map { it.toJson() }).toString()
            }
        } catch (re: RpcInvalidRequestException) {
            return RpcResponse.error(re.id, RpcError.invalidRequest).toString()
        }
        return null
    }
}