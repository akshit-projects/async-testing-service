package ab.async.tester.library.driver

import slick.jdbc.PostgresProfile
import slick.lifted.{Rep, SimpleExpression}

trait MyPostgresProfile extends PostgresProfile {

  override val api: API = new API {}

  trait API extends super.API {

    implicit class PostgresStringColumnExtensions(c: Rep[String]) {

      def ilike(pattern: Rep[String]): Rep[Boolean] =
        SimpleExpression.binary[String, String, Boolean] { (left, right, qb) =>
          qb.expr(left)
          qb.sqlBuilder += " ILIKE "
          qb.expr(right)
        }.apply(c, pattern)
    }
  }
}

object MyPostgresProfile extends MyPostgresProfile
