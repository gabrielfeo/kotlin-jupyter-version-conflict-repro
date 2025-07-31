package com.example

import okhttp3.OkHttpClient
import okhttp3.Request

class App {
    fun sendHttpRequest() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .addHeader("Accept", "text/html")
                    .build()
                chain.proceed(newRequest)
            }.build()
        val request = Request.Builder()
            .url("https://google.com/")
            .build()
        client.newCall(request).execute()
        client.dispatcher.executorService.shutdown()
    }
}
