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

    fun send(sender: RpcTextSender, method: String, params: List<Pair<String, Any?>>, callback: RpcCallback?, timeoutMS: Long = 20_000): Boolean {
        return send(sender, method, ksonObject(params), callback, timeoutMS)
    }

    fun send(sender: RpcTextSender, method: String, params: KsonObject, callback: RpcCallback?, timeoutMS: Long = 20_000): Boolean {
        return client.send(sender, method, params, callback, timeoutMS)
    }

    fun sendBatch(sender: RpcTextSender, items: List<BatchItem>): Boolean {
        return client.sendBatch(sender, items)
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

    private fun onRecvPacket(context: RpcContext, jo: KsonObject): RpcResponse? {
        val p = Rpc.detectPacket(jo)
        if (p is RpcRequest) {
            return onRequest(context, p)
        }
        if (p is RpcResponse) {
            onResponse(p)
        }
        return null
    }

    fun onRecv(context: RpcContext, textPacket: String): String? {
        try {
            val jv = Kson.parse(textPacket) ?: return null
            if (jv is KsonObject) {
                return onRecvPacket(context, jv)?.jsonText
            }
            if (jv is KsonArray) {
                val ls = jv.objectList.mapNotNull {
                    onRecvPacket(context, it)
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