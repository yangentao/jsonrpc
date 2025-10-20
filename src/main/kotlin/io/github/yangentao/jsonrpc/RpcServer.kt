@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
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

    fun find(method: String): RpcAction? {
        return actionMap[method]
    }

    fun onRequest(contextRequest: RpcContext, request: RpcRequest): RpcResponse {
        return dispatch(ContextRequest(contextRequest, request))
    }

    fun dispatch(contextRequest: ContextRequest): RpcResponse {
        val ac = find(contextRequest.method)
        if (ac == null) {
            contextRequest.failed(RpcError.methodNotFound)
            return contextRequest.requireResponse
        }
        try {
            val ls: List<RpcInterceptor> = interClasses.map { it.objectInstance ?: it.createInstance() }
            ls.forEach {
                it.beforeAction(contextRequest, ac)
                if (contextRequest.committed) return contextRequest.requireResponse
            }
            interObjects.forEach {
                it.beforeAction(contextRequest, ac)
                if (contextRequest.committed) return contextRequest.requireResponse
            }
            beforeActions.forEach {
                it.invoke(contextRequest, ac)
                if (contextRequest.committed) return contextRequest.requireResponse
            }
            ac.invoke(contextRequest)
            interObjects.forEach { it.afterAction(contextRequest, ac) }
            afterActions.forEach { it.invoke(contextRequest, ac) }
            ls.forEach { it.afterAction(contextRequest, ac) }
        } catch (ex: Exception) {
            ex.printStackTrace()
            if (!contextRequest.committed) {
                contextRequest.failed(RpcError.internalError(ex.message))
            }
        } finally {
            if (!contextRequest.committed) {
                contextRequest.failed(RpcError.internal)
            }
        }
        return contextRequest.requireResponse
    }

    fun beforeLambda(lambda: Function2<ContextRequest, RpcAction, Unit>) {
        beforeActions += RpcLambdaInterceptor(lambda)
    }

    fun afterLambda(lambda: Function2<ContextRequest, RpcAction, Unit>) {
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
        val NAME_TRIM_END: HashSet<String> = hashSetOf("Action", "Actions", "Page", "Controller", "Group")
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonRpc

private fun KClass<*>.rpcActions(): List<KFunction<*>> {
    return this.memberFunctions.filter { it.isPublic && it.hasAnnotation<JsonRpc>() }
}

interface RpcInterceptor {
    fun beforeAction(contextRequest: ContextRequest, action: RpcAction)
    fun afterAction(contextRequest: ContextRequest, action: RpcAction) {}
}

internal interface RpcActionInterceptor {
    fun invoke(contextRequest: ContextRequest, action: RpcAction)
}

internal class RpcLambdaInterceptor(val lambda: Function2<ContextRequest, RpcAction, Unit>) : RpcActionInterceptor {
    override fun invoke(contextRequest: ContextRequest, action: RpcAction) {
        lambda.invoke(contextRequest, action)
    }

}

internal class RpcFuncInterceptor(val func: KFunction<Unit>, val funcClass: KClass<*>? = null) : RpcActionInterceptor {
    private val actionParams: List<KParameter> = func.parameters
    private val ownerObject: Any? = func.ownerObject
    private val ownerClass: KClass<*>? = funcClass ?: func.ownerClass

    private fun instanceOwner(): Any? {
        return ownerObject ?: ownerClass?.objectInstance ?: ownerClass?.createInstanceX()
    }

    override fun invoke(contextRequest: ContextRequest, action: RpcAction) {
        val map = LinkedHashMap<KParameter, Any?>()
        for (p in actionParams) {
            val v: Any? = when (p.kind) {
                KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> instanceOwner()
                KParameter.Kind.VALUE -> {
                    if (p.acceptClass(ContextRequest::class)) {
                        contextRequest
                    } else if (p.acceptClass(RpcAction::class)) {
                        action
                    } else if (p.acceptClass(RpcContext::class)) {
                        contextRequest.context
                    } else if (p.acceptClass(RpcRequest::class)) {
                        contextRequest.request
                    } else error("Interceptor parameter error: ${p.name}, should be: func(RpcContext, RpcAction)")
                }
            }
            map[p] = v
        }
        func.callBy(map)
    }
}