package gatlingdemostore.pageobjects

import io.gatling.core.Predef._
import io.gatling.http.Predef._


object Customer {

  val csvFeederLoginDetails = csv("data/loginDetails.csv").circular

  def login = {
    feed(csvFeederLoginDetails)
      .exec(http("Load Login Page")
        .get("/login")
        .check(status.is(200))
      )
      .exec(http("Customer Login Action")
        .post("/login")
        .formParam("_csrf", "${csrfValue}")
        .formParam("username", "${username}")
        .formParam("password", "${password}")
      )
      .exec(session => session.set("customerLoggedIn", true))
  }
}