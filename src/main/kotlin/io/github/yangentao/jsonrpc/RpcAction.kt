@file:Suppress("unused")

package io.github.yangentao.jsonrpc

import io.github.yangentao.anno.userName
import io.github.yangentao.kson.*
import io.github.yangentao.types.acceptClass
import io.github.yangentao.types.ownerClass
import io.github.yangentao.types.ownerObject
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

open class RpcAction(val action: KCallable<*>, val group: KClass<*>? = null) {
    private val actionParams: List<KParameter> = action.parameters
    val ownerObject: Any? = (action as? KFunction<*>)?.ownerObject
    val ownerClass: KClass<*>? = group ?: (action as? KFunction<*>)?.ownerClass

    inline fun <reified T : Annotation> hasAnnotation(): Boolean {
        return action.hasAnnotation<T>() || (ownerClass?.hasAnnotation<T>() ?: false)
    }

    inline fun <reified T : Annotation> findAnnotation(): T? {
        return action.findAnnotation<T>() ?: ownerClass?.findAnnotation<T>()
    }

    private fun instanceGroup(): Any? {
        return ownerObject ?: ownerClass?.objectInstance ?: ownerClass?.createInstance()
    }

    fun invoke(contextRequest: ContextRequest) {
        val inst: Any? = instanceGroup()
        val request: RpcRequest = contextRequest.request

        val r: Any? = when (request.params) {
            null, KsonNull -> {
                val map = HashMap<KParameter, Any?>()
                for (p in actionParams) {
                    when (p.kind) {
                        KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> {
                            if (inst != null) {
                                map[p] = inst
                            } else {
                                error("Json RPC instance is null: " + request.method)
                            }
                        }

                        KParameter.Kind.VALUE -> {
                            if (p.acceptClass(ContextRequest::class)) {
                                map[p] = contextRequest
                            } else if (p.acceptClass(RpcContext::class)) {
                                map[p] = contextRequest.context
                            } else if (p.acceptClass(RpcRequest::class)) {
                                map[p] = contextRequest.request
                            } else {
                                if (p.isOptional) {
                                    continue
                                } else {
                                    contextRequest.failed(RpcError.invalidParams)
                                    return
                                }
                            }
                        }
                    }
                }
                action.callBy(map)
            }

            is KsonArray -> {
                val ls = ArrayList<Any?>()
                val iter = request.params.iterator()
                for (p in actionParams) {
                    when (p.kind) {
                        KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> {
                            if (inst == null) error("Json RPC instance is null: " + request.method)
                            ls.add(inst)
                        }

                        KParameter.Kind.VALUE -> {
                            if (p.acceptClass(ContextRequest::class)) {
                                ls.add(contextRequest)
                            } else if (p.acceptClass(RpcContext::class)) {
                                ls.add(contextRequest.context)
                            } else if (p.acceptClass(RpcRequest::class)) {
                                ls.add(contextRequest.request)
                            } else if (iter.hasNext()) {
                                val v = p.fromRpcValue(iter.next())
                                ls.add(v)
                            } else if (!p.isOptional) {
                                contextRequest.failed(RpcError.invalidParams)
                                return
                            }

                        }
                    }

                }
                action.call(*ls.toTypedArray())
            }

            is KsonObject -> {
                val map = HashMap<KParameter, Any?>()
                for (p in actionParams) {
                    when (p.kind) {
                        KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> {
                            if (inst != null) {
                                map[p] = inst
                            } else {
                                error("Json RPC instance is null: " + request.method)
                            }
                        }

                        KParameter.Kind.VALUE -> {
                            if (p.acceptClass(ContextRequest::class)) {
                                map[p] = contextRequest
                            } else if (p.acceptClass(RpcContext::class)) {
                                map[p] = contextRequest.context
                            } else if (p.acceptClass(RpcRequest::class)) {
                                map[p] = contextRequest.request
                            } else {
                                val jv = request.params[p.userName]
                                if (jv != null) {
                                    map[p] = p.fromRpcValue(jv)
                                } else if (p.isOptional) {
                                    continue
                                } else {
                                    contextRequest.failed(RpcError.invalidParams)
                                    return
                                }
                            }
                        }
                    }
                }
                action.callBy(map)
            }

            else -> {
                contextRequest.failed(RpcError.invalidRequest)
                return
            }

        }

        if (r is Unit) {
            contextRequest.successNotify()
        } else {
            contextRequest.success(Kson.toKson(r))
        }
    }

}

private fun KParameter.fromRpcValue(jv: KsonValue): Any? {
    return KsonDecoder.decodeByType(jv, this.type, null)
}

