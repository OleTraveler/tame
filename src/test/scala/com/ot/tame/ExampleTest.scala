package com.ot.bones

import java.util.{Date, UUID}

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import com.ot.bones.interpreter.DocInterpreter.Doc
import com.ot.bones.interpreter.ExtractionInterpreter.{DefaultExtractInterpreter, ExtractionError, JsonProducer, ValidateFromProducer}
import com.ot.bones.interpreter.{DocInterpreter, ExtractionInterpreter}
import com.ot.bones.producer.LiftJson
import org.scalatest.FunSuite
import com.ot.tame.everything._
import com.ot.tame.interpreter.EndOfWorldInterpreter


class ExampleTest extends FunSuite {


  test("end of world example") {


    //Define some example data types.
    /** Enumerated CreditCardType */
    sealed abstract class CreditCardType(val abbrev : String)
    object CreditCardTypes extends Enumeration {
      case object Visa extends CreditCardType("Visa")
      case object Mastercard extends CreditCardType("Mastercard")
      case object Amex extends CreditCardType("Amex")
      case object Discover extends CreditCardType("Discover")
    }

    def toCreditCardType: String => Validated[String,CreditCardType] = input => {
      input.toLowerCase match {
        case "visa" => Valid(CreditCardTypes.Visa)
        case "mastercard" => Valid(CreditCardTypes.Mastercard)
        case "amex" => Valid(CreditCardTypes.Amex)
        case "discover" => Valid(CreditCardTypes.Discover)
        case x => Invalid(s"input: ${x} is not a valid credit card type")
      }
    }




    case class BillingLocation(countryIso: String, zipCode: Option[String])
    case class CC(firstFive: String, lastFour: String, uuid: UUID, token: UUID, ccType: CreditCardType,
                  expMonth: Int, expYear: Int, cardholder: String, currencyIso: String, deletedAt: Option[Date], lastModifiedRequest: UUID, billingLocation: Option[BillingLocation])
    val isoList = List("US", "CA", "MX")


    /****** Begin Real Example ******/

    import cats.implicits._
    import com.ot.bones.everything._


    // Here we are defining our expected input data.  This definition will drive the interpreters.
    val extractData = obj.obj12 (
      key("firstFive").string().matchesRegex("[0-9]{5}".r),
      key("lastFour").string().matchesRegex("[0-9]{4}".r),
      key("uuid").string().asUuid(),
      key("token").string().asUuid(),
      key("ccType").string().custom("to CreditCardType", toCreditCardType),
      key("expMonth").int().between(1,12),
      key("expYear").int().between(1950,9999),
      key("cardHolder").string(),
      key("currencyIso").string().length(3),
      key("deletedAt").string().optional().asIsoDateTime(),
      key("lastModifiedRequest").string().asUuid(),
      key("billingLocation").obj2(
        key("countryIso").string().valid(isoList:_*),
        key("zipCode").string().optional()
      ).optional().transform[BillingLocation]
    ).transform[CC]
    //final type is basically DataDefinitionOp[CC]

    //create the program that is responsible for converting JSON into a CC.
    val jsonToCCProgram = extractData.lift.foldMap[ValidateFromProducer](DefaultExtractInterpreter())

    //create the program that is responsible for defining the behavior of a single post endpoint.
    //Please note that httpPostEndpoint, save, httpResponse and httpError response are all MOCKed out with println statments.
    val endOfWorldProgram = (httpPostEndpoint[JsonProducer]("/brainTreeCreditCard").lift, validate[JsonProducer, CC](extractData).lift , save(extractData).lift, httpResponse(extractData).lift, httpErrorResponse[ExtractionError]().lift)
      .mapN( { case (extractJson, doValidation, saveData, respond, respondError) =>

        doValidation(extractJson()) match {
          case Valid(valid) => {
            saveData(valid)
            respond(valid)
          }
          case Invalid(error) => {
            respondError(error.head)
          }
        }

      })


    //Here is our input
    val cc =
      """
        |{
        |  "firstFive" : "12345",
        |  "lastFour" : "4321",
        |  "uuid" : "df15f08c-e6bd-11e7-aeb8-6003089f08b4",
        |  "token" : "e58e7dda-e6bd-11e7-b901-6003089f08b4",
        |  "ccType" : "mastercard",
        |  "expMonth" : 11,
        |  "expYear" : 2022,
        |  "cardHolder" : "Lennart Augustsson",
        |  "currencyIso" : "USD",
        |  "lastModifiedRequest" : "4545d9da-e6be-11e7-86fb-6003089f08b4",
        |  "billingLocation" : {
        |     "countryIso": "US",
        |     "zipCode": "80031"
        |  }
        |}
      """.stripMargin

    //sorry, we still use lift in my projects.  I will soon create a Circe and Argonaut JsonProducer.
    val parsed = net.liftweb.json.parse(cc)
    val jsonProducer = LiftJson(parsed)

    //here, we will test that just the validationt step is working
    val btCc = jsonToCCProgram.apply(jsonProducer)

    //tada!  We have can parse input from JsonProducer to CC using our dataDefinition.
    assert( btCc == Valid(CC("12345", "4321", UUID.fromString("df15f08c-e6bd-11e7-aeb8-6003089f08b4"),
      UUID.fromString("e58e7dda-e6bd-11e7-b901-6003089f08b4"), CreditCardTypes.Mastercard, 11, 2022,
      "Lennart Augustsson", "USD" , None, UUID.fromString("4545d9da-e6be-11e7-86fb-6003089f08b4"),
      Some(BillingLocation("US", Some("80031")))
    )))


    //Now back to our regular we pass the program to the interpreter, the result sill bio IO[Unit] in this case because we are simulating
    val readyToRun = endOfWorldProgram.foldMap[IO](EndOfWorldInterpreter.DefaultEndOfTheWorldInterpreter(ExtractionInterpreter.DefaultExtractInterpreter(), jsonProducer))

    //This is was will run the program
    readyToRun.unsafeRunSync()
    //Here is what is printed and is the expected output:
    //Saving object to DB: CC(12345,4321,df15f08c-e6bd-11e7-aeb8-6003089f08b4,e58e7dda-e6bd-11e7-b901-6003089f08b4,Mastercard,11,2022,Lennart Augustsson,USD,None,4545d9da-e6be-11e7-86fb-6003089f08b4,Some(BillingLocation(US,Some(80031))))
    //Responding with CC(12345,4321,df15f08c-e6bd-11e7-aeb8-6003089f08b4,e58e7dda-e6bd-11e7-b901-6003089f08b4,Mastercard,11,2022,Lennart Augustsson,USD,None,4545d9da-e6be-11e7-86fb-6003089f08b4,Some(BillingLocation(US,Some(80031))))

    //And now, lets print some ugly doc for the validation
    val desc = extractData.lift.foldMap[Doc](DocInterpreter.docInterpreter)

    println(desc)
    //Current output a mess, it will get better in time my friend, I hope you get the idea:
    //(Doc(Required String with key firstFive))(Doc(Required String with key lastFour))(Doc(Converted to UUID)}))
    // (Doc(Converted to UUID)}))(Doc(Custom Conversion: to CreditCardType))(Doc(Required Int with key expMonth))
    // (Doc(Required Int with key expYear))(Doc(Required String with key cardHolder))(Doc(Required String with key currencyIso))
    // (Doc(Required Date with format java.time.format.DateTimeFormatter$ClassicFormat@4aefae17)}))(Doc(Converted to UUID)}))
    // (Doc(converted to Class BillingLocation$3))) mapped into class CC$3)


    //lets see what happends when we document the world
    val programDescription = endOfWorldProgram.foldMap[Doc](EndOfWorldInterpreter.programModuleDocInterpreter)
    println(programDescription)
    //Doc(Doc(object with 12 members: (Doc(Required String with key firstFive))(Doc(Required String with key lastFour))(Doc(Converted to UUID)}))(Doc(Converted to UUID)}))(Doc(Custom Conversion: to CreditCardType))(Doc(Required Int with key expMonth))(Doc(Required Int with key expYear))(Doc(Required String with key cardHolder))(Doc(Required String with key currencyIso))(Doc(Required Date with format java.time.format.DateTimeFormatter$ClassicFormat@49e1e884)}))(Doc(Converted to UUID)}))(Doc(converted to Class BillingLocation))) mapped into class CC)
    //Doc(  {    { HttpPostEndpoint at /brainTreeCreditCard } {    { Validate data using data definition: (Doc(object with 12 members: (Doc(Required String with key firstFive))(Doc(Required String with key lastFour))(Doc(Converted to UUID)}))(Doc(Converted to UUID)}))(Doc(Custom Conversion: to CreditCardType))(Doc(Required Int with key expMonth))(Doc(Required Int with key expYear))(Doc(Required String with key cardHolder))(Doc(Required String with key currencyIso))(Doc(Required Date with format java.time.format.DateTimeFormatter$ClassicFormat@49e1e884)}))(Doc(Converted to UUID)}))(Doc(converted to Class BillingLocation))) mapped into class CC) } {    { Save to the Database } {    { Response Success } { Respond Failure } } } } })





  }

}
