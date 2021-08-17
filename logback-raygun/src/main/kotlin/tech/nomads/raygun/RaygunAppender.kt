/*
 *  Copyright 2014-2015 Greg Kopff, 2021 Alwyn Schoeman
 *  All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package tech.nomads.raygun

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.AppenderBase
import com.mindscapehq.raygun4java.core.RaygunClientFactory
import com.mindscapehq.raygun4java.core.RaygunMessageBuilder
import com.mindscapehq.raygun4java.core.messages.RaygunErrorMessage
import com.mindscapehq.raygun4java.core.messages.RaygunErrorStackTraceLineMessage
import java.net.InetAddress
import java.util.*

class RaygunAppender :  AppenderBase<ILoggingEvent>() {
    private val hostName = getMachineName()
    private lateinit var keyMaster: KeyMaster
    private lateinit var tags: Set<String>

    private val factory: RaygunClientFactory by lazy {
        RaygunClientFactory(keyMaster.apiKey(hostName)).withVersion(VERSION)
    }

    companion object {
        private const val NAME: String = "logback-raygun"
        private const val VERSION = "3.0.0"
        private const val URL = "https://github.com/alwyn/logback-raygun"
        private const val PROPERTY_APPLICATION_ID = "tech.nomads.raygun.UserCustomData.applicationId"
        private const val TN = "tech.nomads.raygun.RaygunAppender"
        private const val LOGBACK = "ch.qos.logback."

        private fun getMachineName(): String = InetAddress.getLocalHost().hostName

        private fun buildRaygunMessage(loggingEvent: ILoggingEvent): RaygunErrorMessage {
            val logbackThrowable: IThrowableProxy? = loggingEvent.throwableProxy
            val callerData: Array<StackTraceElement>? =
                if (loggingEvent.hasCallerData()) {
                    loggingEvent.callerData
                } else {
                    null
                }
            return buildRaygunMessage(loggingEvent.formattedMessage, logbackThrowable, callerData)
        }

        private fun buildRaygunMessage(
            message: String,
            exception: IThrowableProxy?,
            callerData: Array<StackTraceElement>? = null
        ): RaygunErrorMessage {
            val error = RaygunErrorMessage(RaygunException())
            val appId: String? = System.getProperty(PROPERTY_APPLICATION_ID)

            lateinit var trace: Array<RaygunErrorStackTraceLineMessage>
            var inner: RaygunErrorMessage? = null
            var className = "Unknown"
            var causalString: String? = null

            exception.let { e ->
                trace = calculateStackTrace(e, callerData)
                if (e != null) {
                    className = e.className
                    causalString = buildCausalString(e)
                    inner = e.cause?.let { c -> buildRaygunMessage("Caused by", c) }
                } else {
                    if (trace.isNotEmpty()) {
                        className = trace[0].className
                    }
                }
            }
            val buff = buildString {
                if (appId != null) append("$appId: ")
                append(message)
                if (causalString != null) append("; $causalString")
            }
            with(error) {
                setMessage(buff)
                className = className
                stackTrace = trace
                inner?.let { inner -> innerError = inner }
            }
            return error
        }

        private fun calculateStackTrace(
            exception: IThrowableProxy?,
            callerData: Array<StackTraceElement>?
        ): Array<RaygunErrorStackTraceLineMessage> {
            if (callerData != null) {
                return Array(callerData.size) { i -> RaygunErrorStackTraceLineMessage(callerData[i]) }
            }
            return if (exception != null)
                buildRaygunStack(exception)
            else {
                val callSite = locateCallSite()
                if (callSite != null) {
                    arrayOf(RaygunErrorStackTraceLineMessage(callSite))
                } else {
                    arrayOf(RaygunErrorStackTraceLineMessage(
                        StackTraceElement(
                            "tech.nomads.raygun.RaygunAdapter",
                            "append",
                            "RaygunAdapter",
                            1
                        )))
                }
            }
        }

        private fun buildCausalString(exception: IThrowableProxy): String =
            buildString {
                append(exception.className)
                if (exception.message != null) append(": ${exception.message}")
                exception.cause?.let { cause -> append("; caused by ${buildCausalString(cause)}") }
            }

        private fun buildRaygunStack(throwableProxy: IThrowableProxy): Array<RaygunErrorStackTraceLineMessage> {
            val proxies = throwableProxy.stackTraceElementProxyArray
            return Array(proxies.size) { i -> RaygunErrorStackTraceLineMessage(proxies[i].stackTraceElement) }
        }

        private fun locateCallSite(): StackTraceElement? =
            RaygunException(fillStacktrace = true).stackTrace
                .firstOrNull { e -> !(e.className.startsWith(TN) || e.className.startsWith(LOGBACK)) }

    }

    override fun append(event: ILoggingEvent) {
        val host = getMachineName()
        val apiKey: String? = this.keyMaster.apiKey(host)

        if (apiKey != null) {
            val client = this.factory.newClient()
            val raygunMessage = RaygunMessageBuilder()
                .setEnvironmentDetails()
                .setMachineName(this.hostName)
                .setClientDetails()
                .setTags(this.tags)
                .build()
            with(raygunMessage.details.client) {
                name = NAME
                version = VERSION
                clientUrlString = URL
            }
            with(raygunMessage.details) {
                error = buildRaygunMessage(event)
                userCustomData =
                    event.mdcPropertyMap.map { (k, v) ->
                        "mdc:$k" to v
                    }
                        .plus("thread" to event.threadName)
                        .plus("logger" to event.loggerName)
                        .plus("applicationId" to System.getProperty(PROPERTY_APPLICATION_ID, "unnamed"))
                        .plus("datetime" to Date(event.timeStamp))
                        .toMap()
            }
            client.send(raygunMessage)
        }
    }

    fun setApiKey(apikey: String?) {
        requireNotNull(apikey) { "apiKey cannot be null" }
        this.keyMaster = KeyMaster.fromConfigString(apikey)
    }

    fun setTags(tags: String?) {
        requireNotNull(tags) { "tags cannot be null" }
        this.tags = tags.split(',').toSet()
    }

}
