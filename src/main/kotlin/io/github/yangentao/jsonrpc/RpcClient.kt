@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.kson.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RpcClient(workerCount: Int = 4) {
    private val map = HashMap<Long, RemoteAction>()
    val tasks: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var fu: ScheduledFuture<*>? = null

    @Synchronized
    private fun put(id: Long, action: RemoteAction) {
        if (fu == null) {
            fu = tasks.scheduleAtFixedRate(this::checkTimeout, 2000, 2000, TimeUnit.MILLISECONDS)
        }
        map[id] = action
    }

    @Synchronized
    private fun remove(id: Long): RemoteAction? {
        val r = map.remove(id)
        if (map.isEmpty()) {
            fu?.cancel(false)
            fu = null
        }
        return r
    }

    private fun checkTimeout() {
        val tm = System.currentTimeMillis()
        val kSet = HashSet<Long>()
        for (e in map) {
            if (e.value.isExpired(tm)) {
                kSet.add(e.key)
            }
        }
        for (id in kSet) {
            val r = remove(id) ?: continue
            tasks.submit {
                r.callback.onTimeout()
            }
        }
    }

    fun onResponse(response: RpcResponse) {
        val info = remove(response.longID) ?: return
        tasks.submit {
            when (response) {
                is RpcNoResponse -> {}
                is RpcResult -> info.callback.onResult(response.result)
                is RpcFailed -> info.callback.onError(RpcException(response.id, response.error))
            }
        }
    }

    fun sendParams(sender: RpcTextSender, method: String, params: List<Pair<String, Any?>>, callback: RpcCallback?, timeoutMS: Long = 20_000): Boolean {
        return send(sender, method, ksonObject(params), callback, timeoutMS)
    }

    fun send(sender: RpcTextSender, method: String, params: KsonObject, callback: RpcCallback?, timeoutMS: Long = 20_000): Boolean {
        if (callback == null) {
            val r = RpcRequest(KsonNull, method, params)
            return sender.sendText(r.toString())
        }
        val id = Rpc.nextID
        val r = RpcRequest(KsonNum(id), method, params)
        val ok = sender.sendText(r.toString())
        if (ok) put(id, RemoteAction(r, callback, timeoutMS))
        return ok
    }

    fun sendBatch(sender: RpcTextSender, items: List<BatchItem>): Boolean {
        val idSet = HashSet<Long>()
        val ja = KsonArray()
        for (item in items) {
            if (item.callback == null) {
                val r = RpcRequest(KsonNull, item.method, ksonObject(item.params))
                ja.add(r.toJson())
            } else {
                val id = Rpc.nextID
                val r = RpcRequest(KsonNum(id), item.method, ksonObject(item.params))
                ja.add(r.toJson())
                put(id, RemoteAction(r, item.callback, item.timeoutMS))
                idSet.add(id)
            }
        }
        if (ja.isEmpty()) {
            return false
        }
        val ok = sender.sendText(ja.toString())
        if (!ok) {
            for (id in idSet) remove(id)
        }
        return ok
    }

}

data class BatchItem(val method: String, val params: List<Pair<String, Any?>>, val callback: RpcCallback?, val timeoutMS: Long = 20_000)

private data class RemoteAction(val request: RpcRequest, val callback: RpcCallback, val timeoutMS: Long) {
    val time: Long = System.currentTimeMillis()

    fun isExpired(tm: Long = System.currentTimeMillis()): Boolean {
        return time + timeoutMS < tm
    }
}

interface RpcCallback {
    fun onResult(result: KsonValue)
    fun onError(error: RpcException)
    fun onTimeout()
}

fun interface RpcTextSender {
    fun sendText(text: String): Boolean
}



