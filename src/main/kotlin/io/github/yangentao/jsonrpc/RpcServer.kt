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

    fun onRequest(context: RpcContext, request: RpcRequest): RpcResponse {
        return dispatch(ContextRequest(context, request))
    }

    fun dispatch(context: ContextRequest): RpcResponse {
        val ac = find(context.method)
        if (ac == null) {
            context.failed(RpcError.methodNotFound)
            return context.requireResponse
        }
        try {
            val ls: List<RpcInterceptor> = interClasses.map { it.objectInstance ?: it.createInstance() }
            ls.forEach {
                it.beforeAction(context, ac)
                if (context.committed) return context.requireResponse
            }
            interObjects.forEach {
                it.beforeAction(context, ac)
                if (context.committed) return context.requireResponse
            }
            beforeActions.forEach {
                it.invoke(context, ac)
                if (context.committed) return context.requireResponse
            }
            ac.invoke(context)
            interObjects.forEach { it.afterAction(context, ac) }
            afterActions.forEach { it.invoke(context, ac) }
            ls.forEach { it.afterAction(context, ac) }
        } catch (ex: Exception) {
            ex.printStackTrace()
            if (!context.committed) {
                context.failed(RpcError.internalError(ex.message))
            }
        } finally {
            if (!context.committed) {
                context.failed(RpcError.internal)
            }
        }
        return context.requireResponse
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
    fun beforeAction(context: ContextRequest, action: RpcAction)
    fun afterAction(context: ContextRequest, action: RpcAction) {}
}

internal interface RpcActionInterceptor {
    fun invoke(context: ContextRequest, action: RpcAction)
}

internal class RpcLambdaInterceptor(val lambda: Function2<ContextRequest, RpcAction, Unit>) : RpcActionInterceptor {
    override fun invoke(context: ContextRequest, action: RpcAction) {
        lambda.invoke(context, action)
    }

}

internal class RpcFuncInterceptor(val func: KFunction<Unit>, val funcClass: KClass<*>? = null) : RpcActionInterceptor {
    private val actionParams: List<KParameter> = func.parameters
    private val ownerObject: Any? = func.ownerObject
    private val ownerClass: KClass<*>? = funcClass ?: func.ownerClass

    private fun instanceOwner(): Any? {
        return ownerObject ?: ownerClass?.objectInstance ?: ownerClass?.createInstanceX()
    }

    override fun invoke(context: ContextRequest, action: RpcAction) {
        val map = LinkedHashMap<KParameter, Any?>()
        for (p in actionParams) {
            val v: Any? = when (p.kind) {
                KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> instanceOwner()
                KParameter.Kind.VALUE -> {
                    if (p.acceptClass(ContextRequest::class)) {
                        context
                    } else if (p.acceptClass(RpcAction::class)) {
                        action
                    } else if (p.acceptClass(RpcContext::class)) {
                        context.context
                    } else if (p.acceptClass(RpcRequest::class)) {
                        context.request
                    } else error("Interceptor parameter error: ${p.name}, should be: func(RpcContext, RpcAction)")
                }
            }
            map[p] = v
        }
        func.callBy(map)
    }
}