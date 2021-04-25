package gatlingdemostore

import gatlingdemostore.pageobjects._
import scala.concurrent.duration._
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._
import sun.security.util.Length

import scala.util.Random
import scala.util.hashing.Hashing.Default

class DemoStoreSimulations extends Simulation {

	val domain = "demostore.gatling.io"

	val httpProtocol = http
		.baseUrl("https://" + domain)

	def userCount: Int = getProperty("USERS", "5").toInt
	def rampDuration: Int = getProperty("RAMP_DURATION", "10").toInt
	def testDuration: Int = getProperty("DURATION", "60").toInt

	private def getProperty(propertyName: String, defaultValue: String) = {
		Option(System.getenv(propertyName))
			.orElse(Option(System.getProperty(propertyName)))
			.getOrElse(defaultValue)
	}

	val rnd = new Random()

	def randomString(length: Int): String = {
		rnd.alphanumeric.filter(_.isLetter).take(length).mkString
	}

	before {
		println(s"Stress Testing Complete")
	}

	after {
		println(s"Running test with ${userCount} users")
		println(s"Running test with ${rampDuration} seconds")
		println(s"Running test with ${userCount} seconds")
	}

	val initSesion = exec(flushCookieJar)
		.exec(session => session.set("randomNumber", 100))
		.exec(session => session.set("customerLoggedIn", false))
		.exec(session => session.set("cartTotal", 0.00))
		.exec(addCookie(Cookie("sessionId", randomString(10)).withDomain(domain)))

	val scn = scenario("Record Simulation")
		.exec(initSesion)
		.exec(CmsPages.homepage)
		.pause(2)
		.exec(CmsPages.aboutUs)
		.pause(2)
		.exec(Catalog.Category.view)
		.pause(2)
		.exec(Catalog.Product.add)
		.pause(2)
		.exec(Checkout.viewCart)
		.pause(2)
		.exec(Checkout.completeCheckout)

	object UserJourneys {
		def minPause = 100.milliseconds
		def maxPause = 500.milliseconds

		def browseStore = {
			exec(initSesion)
				.exec(CmsPages.homepage)
				.pause(maxPause)
				.exec(CmsPages.aboutUs)
				.pause(minPause, maxPause)
				.repeat(5) {
					exec(Catalog.Category.view)
						.pause(minPause, maxPause)
						.exec(Catalog.Product.view)
				}
		}

		def abandonCart = {
			exec(initSesion)
				.exec(CmsPages.homepage)
				.pause(maxPause)
				.exec(Catalog.Category.view)
				.pause(minPause, maxPause)
				.exec(Catalog.Product.view)
				.pause(minPause, maxPause)
				.exec(Catalog.Product.add)
		}

		def completePurchase = {
			exec(initSesion)
				.exec(CmsPages.homepage)
				.pause(maxPause)
				.exec(Catalog.Category.view)
				.pause(minPause, maxPause)
				.exec(Catalog.Product.view)
				.pause(minPause, maxPause)
				.exec(Catalog.Product.add)
				.pause(minPause, maxPause)
				.exec(Checkout.viewCart)
				.pause(minPause, maxPause)
				.exec(Checkout.completeCheckout)
				.pause(minPause, maxPause)
		}
	}

	object Scenarios {
		def default = scenario("Default Load Test")
			.during(60.seconds) {
				randomSwitch(
					75d -> exec(UserJourneys.browseStore),
					15d -> exec(UserJourneys.abandonCart),
					10d -> exec(UserJourneys.completePurchase)
				)
			}

		def highPurchase = scenario("High Purchase Load Test")
			.during(60.seconds) {
				randomSwitch(
					25d -> exec(UserJourneys.browseStore),
					25d -> exec(UserJourneys.abandonCart),
					50d -> exec(UserJourneys.completePurchase)
				)
			}
	}

//Closed Model
//	setUp(
//		scn.inject(
//			constantConcurrentUsers(10) during(20.seconds),
//			rampConcurrentUsers(10) to (20) during(20.seconds)
//		).protocols(httpProtocol)
//	)

//	setUp(
//		scn.inject(
//			atOnceUsers(1),
//			nothingFor(5.seconds),
//			rampUsers(10) during (20.seconds),
//			nothingFor(10.seconds),
//			constantUsersPerSec(1) during(20.seconds)
//		).protocols(httpProtocol)
//	)

//	Witch Throttle
//	setUp(
//		scn.inject(
//			constantUsersPerSec(1) during(3.minutes)
//		).protocols(httpProtocol).throttle(
//			reachRps(10) in (30.seconds),
//			holdFor(60.seconds),
//			jumpToRps(20),
//			holdFor(60.seconds))
//	).maxDuration(3.minutes)

	setUp(
//		Parallel
		Scenarios.default
			.inject(rampUsers(userCount) during(rampDuration.seconds)).protocols(httpProtocol),
		Scenarios.highPurchase
			.inject(rampUsers(5) during(10.seconds)).protocols(httpProtocol)
	)

//		Sequential
//			.andThen(
//				Scenarios.highPurchase
//					.inject(rampUsers(5) during(10.seconds)).protocols(httpProtocol)
//			)
}