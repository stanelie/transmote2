package com.stanelie.transmote2

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Each platform supplies its own engine: OkHttp on Android (unchanged from
 * before the split), Darwin on iOS.
 */
expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient
