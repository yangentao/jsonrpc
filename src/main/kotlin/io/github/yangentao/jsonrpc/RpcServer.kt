package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.*
import io.github.yangentao.types.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

class RpcServer() {
    private val actionMap: HashMap<String, RpcAction> = HashMap()
    private val interClasses: ArrayList<KClass<out RpcInterceptor>> = ArrayList()
    private val interObjects: ArrayList<RpcInterceptor> = ArrayList()
    private val beforeActions: ArrayList<RpcActionInterceptor> = ArrayList()
    private val afterActions: ArrayList<RpcActionInterceptor> = ArrayList()
    private val encoders: ArrayList<RpcEncoder> = ArrayList()

    fun addEncoder(encoder: RpcEncoder) {
        if (!encoders.contains(encoder)) {
            encoders.add(encoder)
        }
    }

    fun find(method: String): RpcAction? {
        return actionMap[method]
    }

    fun onRequest(context: RpcContext, request: RpcRequest): RpcResponse? {
        val ac = find(request.method) ?: return context.failed(request.id, RpcError.methodNotFound(data = KsonString(request.method)))

        try {

            val ls: List<RpcInterceptor> = interClasses.map { it.objectInstance ?: it.createInstance() }
            ls.forEach {
                it.beforeAction(context, request, ac)
                if (context.committed) return context.response
            }
            interObjects.forEach {
                it.beforeAction(context, request, ac)
                if (context.committed) return context.response
            }
            beforeActions.forEach {
                it.invoke(context, request, ac)
                if (context.committed) return context.response
            }
            try {
                val r = ac.invoke(context, request)
                if (r == null || r == Unit) {
                    context.commitNotify()
                } else {
                    val enc: RpcEncoder = encoders.firstOrNull { it.matchValue(r) } ?: DefaultRpcEncoder
                    context.response(enc.encodeValue(request, r))
                }
            } catch (re: RpcException) {
                if (!context.committed) {
                    context.failed(request.id, re.error)
                }
            }
            interObjects.forEach { it.afterAction(context, request, ac) }
            afterActions.forEach { it.invoke(context, request, ac) }
            ls.forEach { it.afterAction(context, request, ac) }
        } catch (re: RpcException) {
            return context.failed(request.id, re.error)
        } catch (ex: Exception) {
            ex.printStackTrace()
            if (!context.committed) {
                context.failed(request.id, RpcError.internalError(ex.message))
            }
        } finally {
            if (!context.committed) {
                context.failed(request.id, RpcError.internal)
            }
        }
        return context.response
    }

    fun beforeLambda(lambda: Function3<RpcContext, RpcRequest, RpcAction, Unit>) {
        beforeActions += RpcLambdaInterceptor(lambda)
    }

    fun afterLambda(lambda: Function3<RpcContext, RpcRequest, RpcAction, Unit>) {
        afterActions += RpcLambdaInterceptor(lambda)
    }

    /**
     * beforeXX(context:RpcContext, action:RpcAction){}
     */
    fun before(func: KFunction<Unit>) {
        beforeActions += RpcFuncInterceptor(func)
    }

    /**
     * afterXX(context:RpcContext, action:RpcAction){}
     */
    fun after(func: KFunction<Unit>) {
        afterActions += RpcFuncInterceptor(func)
    }

    fun interceptClass(cls: KClass<out RpcInterceptor>) {
        if (interClasses.contains(cls)) return
        interClasses.add(cls)
    }

    fun intercept(obj: RpcInterceptor) {
        if (interObjects.contains(obj)) return
        interObjects.add(obj)
    }

    fun add(action: KFunction<*>) {
        add(action.userName, action)
    }

    @Synchronized
    fun add(method: String, action: KFunction<*>) {
        if (actionMap.containsKey(method)) error("Json RPC already exist method: $method")
        actionMap[method] = RpcAction(action)
    }

    fun addGroup(group: KClass<*>, noGroupName: Boolean = false) {
        val gname = makeGroupName(group)

        val ls = group.rpcActions()
        for (a in ls) {
            if (noGroupName) {
                add(a.userName, a)
            } else {
                add("${gname}.${a.userName}", a)
            }
        }
    }

    private fun makeGroupName(group: KClass<*>): String {
        val gname = group.userName
        for (a in NAME_TRIM_END) {
            if (gname.length > a.length && gname.endsWith(a)) return gname.substringBeforeLast(a).lowercase()
        }
        return gname.lowercase()
    }

    companion object {
        val NAME_TRIM_END: HashSet<String> = hashSetOf("Rpc", "Action", "Actions", "Page", "Controller", "Group")
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonRpc

private fun KClass<*>.rpcActions(): List<KFunction<*>> {
    return this.memberFunctions.filter { it.isPublic && it.hasAnnotation<JsonRpc>() }
}

interface RpcInterceptor {
    fun beforeAction(context: RpcContext, request: RpcRequest, action: RpcAction)
    fun afterAction(context: RpcContext, request: RpcRequest, action: RpcAction) {}
}

internal interface RpcActionInterceptor {
    fun invoke(context: RpcContext, request: RpcRequest, action: RpcAction)
}

internal class RpcLambdaInterceptor(val lambda: Function3<RpcContext, RpcRequest, RpcAction, Unit>) : RpcActionInterceptor {
    override fun invoke(context: RpcContext, request: RpcRequest, action: RpcAction) {
        lambda.invoke(context, request, action)
    }

}

internal class RpcFuncInterceptor(val func: KFunction<Unit>, val funcClass: KClass<*>? = null) : RpcActionInterceptor {
    private val actionParams: List<KParameter> = func.parameters
    private val ownerObject: Any? = func.ownerObject
    private val ownerClass: KClass<*>? = funcClass ?: func.ownerClass

    private fun instanceOwner(): Any? {
        return ownerObject ?: ownerClass?.objectInstance ?: ownerClass?.createInstanceX()
    }

    override fun invoke(context: RpcContext, request: RpcRequest, action: RpcAction) {
        val map = LinkedHashMap<KParameter, Any?>()
        for (p in actionParams) {
            val v: Any? = when (p.kind) {
                KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> instanceOwner()
                KParameter.Kind.VALUE -> {
                    if (p.acceptClass(RpcContext::class)) {
                        context
                    } else if (p.acceptClass(RpcRequest::class)) {
                        request
                    } else if (p.acceptClass(RpcAction::class)) {
                        action
                    } else error("Interceptor parameter error: ${p.name}, should be: func(RpcContext, RpcAction)")
                }
            }
            map[p] = v
        }
        func.callBy(map)
    }
}

interface RpcEncoder {
    fun matchValue(value: Any): Boolean
    fun encodeValue(request: RpcRequest, value: Any): RpcResponse
}

object DefaultRpcEncoder : RpcEncoder {
    override fun matchValue(value: Any): Boolean {
        return true
    }

    override fun encodeValue(request: RpcRequest, value: Any): RpcResponse {
        return when (value) {
            is RpcError -> RpcResponse.failed(request.id, value)
            is RpcResponse -> value
            is KsonValue -> RpcResponse.success(request.id, value)
            is JsonResult -> {
                if (value.OK) {
                    val d = value.data
                    if (d == null) {
                        RpcResponse.success(request.id, KsonNull)
                    } else {
                        RpcResponse.success(request.id, d as KsonValue)
                    }
                } else {
                    RpcResponse.failed(request.id, message = value.message ?: "request error", code = value.code, data = value.data as? KsonValue)
                }
            }

            else -> RpcResponse.success(request.id, Kson.toKson(value))
        }
    }
}


