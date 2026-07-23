package app.phonetube.core.engine

import coil3.network.NetworkClient
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import okio.buffer
import okio.source
import java.net.HttpURLConnection
import java.net.URL

class HttpNetworkClient : NetworkClient {

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T
    ): T {
        val startTime = System.currentTimeMillis()
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true

            for ((key, values) in request.headers.asMap()) {
                for (value in values) {
                    connection.addRequestProperty(key, value)
                }
            }

            connection.connect()
            val responseTime = System.currentTimeMillis()

            val responseHeaders = NetworkHeaders.Builder().apply {
                for ((key, values) in connection.headerFields) {
                    if (key != null) {
                        for (value in values) {
                            add(key, value)
                        }
                    }
                }
            }.build()

            val responseBody = object : NetworkResponseBody {
                private var closed = false

                override suspend fun writeTo(sink: okio.BufferedSink) {
                    val stream = connection.inputStream
                    stream.source().buffer().use { source ->
                        sink.writeAll(source)
                    }
                }

                override suspend fun writeTo(fileSystem: okio.FileSystem, path: okio.Path) {
                    fileSystem.sink(path).buffer().use { sink ->
                        writeTo(sink)
                    }
                }

                override fun close() {
                    if (!closed) {
                        closed = true
                        connection.disconnect()
                    }
                }
            }

            val response = NetworkResponse(
                code = connection.responseCode,
                requestMillis = startTime,
                responseMillis = responseTime,
                headers = responseHeaders,
                body = responseBody
            )

            return block(response)
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
    }
}
