package ab.async.tester.modules

import ab.async.tester.library.cache.RedisLockManager
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

/** Guice module for Redis-related components
  */
class RedisModule extends Module {
  override def bindings(
      environment: Environment,
      configuration: Configuration
  ): Seq[Binding[_]] = {
    Seq(
      bind[RedisLockManager].toSelf.eagerly()
    )
  }
}
