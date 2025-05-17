package ab.async.tester.cache

import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}

/**
 * Guice module for Redis-related components
 */
class RedisModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[RedisLockManager].toSelf.eagerly()
    )
  }
} 