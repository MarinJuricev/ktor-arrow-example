package io.github.nomisrev

import io.github.nomisrev.routes.userRoutes
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.CORS
import io.ktor.server.plugins.ContentNegotiation
import io.ktor.server.plugins.DefaultHeaders
import io.ktor.server.plugins.maxAgeDuration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main(): Unit =
  runBlocking(Dispatchers.Default) {
    val config = envConfig()
    module(config).use { module ->
      embeddedServer(
          Netty,
          port = config.http.port,
          host = config.http.host,
          parentCoroutineContext = coroutineContext,
        ) { app(config, module) }
        .start(wait = true)
    }
  }

fun Application.app(config: Config, module: Module) {
  configure(config.auth)
  healthRoute(module.pool)
  userRoutes(module.userService)
}

@OptIn(ExperimentalTime::class)
fun Application.configure(config: Config.Auth) {
  install(DefaultHeaders)
  install(ContentNegotiation) {
    json(
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    )
  }
  configureJWT(config)
  install(CORS) {
    header(HttpHeaders.Authorization)
    header(HttpHeaders.ContentType)
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
}
