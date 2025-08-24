package ab.async.tester.domain.auth

import ab.async.tester.domain.user.User

case class ClientToken(token: String, tokenExp: String, user: User)
