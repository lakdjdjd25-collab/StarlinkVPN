/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted from SagerNet sing-box-for-android's LocalResolver.
 */
package org.quickping.app.vpn

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class AndroidLocalDnsTransport(
    private val networkMonitor: AndroidDefaultNetworkMonitor,
) : LocalDNSTransport {
    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Raw DNS requires Android 10 or newer" }
        val network = networkMonitor.defaultNetwork ?: error("Physical network is unavailable")
        runBlocking {
            suspendCoroutine { continuation ->
                val signal = CancellationSignal()
                val completion = OneShotContinuation(continuation)
                ctx.onCancel {
                    signal.cancel()
                    completion.fail(CancellationException())
                }
                DnsResolver.getInstance().rawQuery(
                    network,
                    message,
                    DnsResolver.FLAG_NO_RETRY,
                    Dispatchers.IO.asExecutor(),
                    signal,
                    object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                            completion.resume()
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            val cause = error.cause
                            if (cause is ErrnoException) {
                                ctx.errnoCode(cause.errno)
                                completion.resume()
                            } else {
                                completion.fail(error)
                            }
                        }
                    },
                )
            }
        }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        val physicalNetwork = networkMonitor.defaultNetwork ?: error("Physical network is unavailable")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runBlocking {
                try {
                    ctx.success(physicalNetwork.getAllByName(domain).mapNotNull { it.hostAddress }.joinToString("\n"))
                } catch (_: UnknownHostException) {
                    ctx.errorCode(RCODE_NXDOMAIN)
                }
            }
            return
        }
        runBlocking {
            suspendCoroutine { continuation ->
                val signal = CancellationSignal()
                val completion = OneShotContinuation(continuation)
                ctx.onCancel {
                    signal.cancel()
                    completion.fail(CancellationException())
                }
                val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                    override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                        if (rcode == 0) {
                            ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n"))
                        } else {
                            ctx.errorCode(rcode)
                        }
                        completion.resume()
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        val cause = error.cause
                        if (cause is ErrnoException) {
                            ctx.errnoCode(cause.errno)
                            completion.resume()
                        } else {
                            completion.fail(error)
                        }
                    }
                }
                val recordType = when {
                    network.endsWith("4") -> DnsResolver.TYPE_A
                    network.endsWith("6") -> DnsResolver.TYPE_AAAA
                    else -> null
                }
                if (recordType == null) {
                    DnsResolver.getInstance().query(
                        physicalNetwork,
                        domain,
                        DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback,
                    )
                } else {
                    DnsResolver.getInstance().query(
                        physicalNetwork,
                        domain,
                        recordType,
                        DnsResolver.FLAG_NO_RETRY,
                        Dispatchers.IO.asExecutor(),
                        signal,
                        callback,
                    )
                }
            }
        }
    }

    private class OneShotContinuation(
        private val continuation: Continuation<Unit>,
    ) {
        private val completed = AtomicBoolean(false)

        fun resume() {
            if (completed.compareAndSet(false, true)) continuation.resume(Unit)
        }

        fun fail(error: Throwable) {
            if (completed.compareAndSet(false, true)) continuation.resumeWithException(error)
        }
    }

    private companion object {
        const val RCODE_NXDOMAIN = 3
    }
}
