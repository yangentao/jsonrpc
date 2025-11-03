@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.*
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

    fun onRequest(context: RpcContext, request: RpcRequest): RpcResponse {
        return server.onRequest(context, request)
    }

    fun beforeLambda(lambda: Function3<RpcContext, RpcRequest, RpcAction, Unit>) {
        server.beforeLambda(lambda)
    }

    fun afterLambda(lambda: Function3<RpcContext, RpcRequest, RpcAction, Unit>) {
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

    fun addEncoder(encoder: RpcEncoder) {
        server.addEncoder(encoder)
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

    private fun onRecvPacket(context: RpcContext, jo: KsonObject, acceptor: (RpcRequest) -> Boolean): RpcResponse? {
        val p = Rpc.detectPacket(jo)
        if (p is RpcRequest) {
            return if (acceptor(p)) {
                onRequest(context, p)
            } else {
                null
            }
        }
        if (p is RpcResponse) {
            onResponse(p)
        }
        return null
    }

    fun onRecv(context: RpcContext, textPacket: String): String? {
        return onRecv(context, textPacket) { true }
    }

    fun onRecv(context: RpcContext, textPacket: String, acceptor: (RpcRequest) -> Boolean): String? {
        try {
            val jv = Kson.parse(textPacket) ?: return null
            if (jv is KsonObject) {
                return onRecvPacket(context, jv, acceptor)?.jsonText
            }
            if (jv is KsonArray) {
                val ls = jv.objectList.mapNotNull {
                    onRecvPacket(context, it, acceptor)
                }.filter { it !is RpcNoResponse }
                return if (ls.isEmpty()) null else ksonArray(ls.map { it.toJson() }).toString()
            }
        } catch (re: RpcException) {
            return RpcFailed(re.id, re.error).toString()
        } catch (e: Exception) {
            return RpcFailed(KsonNull, RpcError.internalError(e.message)).toString()
        }
        return null
    }
}