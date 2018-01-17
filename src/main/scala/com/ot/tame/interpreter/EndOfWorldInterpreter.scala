package com.ot.tame.interpreter

import cats.effect.IO
import com.ot.bones.interpreter.DocInterpreter
import com.ot.bones.interpreter.DocInterpreter.Doc
import com.ot.bones.interpreter.ExtractionInterpreter.{DefaultExtractInterpreter, JsonProducer}
import com.ot.tame.ProgramModuleOp
import com.ot.tame.db.Database.Save
import com.ot.tame.http.{HttpErrorResponse, HttpPostEndpoint, HttpResponse}
import com.ot.tame.validation.Validate.ValidateData


object EndOfWorldInterpreter {


  type ProgramFunction

  //TODO: end result might be IO, Not Unit
  case class DefaultEndOfTheWorldInterpreter(extractionCompile: DefaultExtractInterpreter, mockJsonProducer: JsonProducer) extends cats.arrow.FunctionK[ProgramModuleOp, IO] {

    def printResponse[T]: T => Unit = t => println(s"Responding with ${t}\n")
    def printErrorResponse[T]: T => Unit = t => println(s"Responding with ERROR ${t}\n")
    def mockSave[T]: T => Unit = t => println(s"Saving object to DB: ${t}\n")
    def mockUnitToProducer: () =>JsonProducer = () => mockJsonProducer

    /**
      *
      * @param programModule
      * @tparam A should be a function from X => Y
      * @return
      */
    def apply[A](programModule: ProgramModuleOp[A]): IO[A] = programModule match {
      case v: ValidateData[i,a] => IO(extractionCompile.apply(v.dataDefinitionOp).asInstanceOf[A])
      case http: HttpPostEndpoint[a] => IO(mockUnitToProducer.asInstanceOf[A])
      case http: HttpResponse[a] => IO(printResponse.asInstanceOf[A])
      case http: HttpErrorResponse[a] => IO(printResponse.asInstanceOf[A])
      case save: Save[a] => IO(mockSave.asInstanceOf[A])
    }

  }

  case class EndOfWorldDocumentationInterpreter() {

  }

  val programModuleDocInterpreter = new cats.arrow.FunctionK[ProgramModuleOp, Doc] {

    def apply[A](pmo: ProgramModuleOp[A]) : Doc[A] = pmo match {
      case v: ValidateData[i,a] => Doc(s"Validate data using data definition: (${DocInterpreter.docInterpreter(v.dataDefinitionOp).str})")
      case http: HttpPostEndpoint[a] => Doc(s"HttpPostEndpoint at ${http.endpointPath}")
      case http: HttpResponse[a] => Doc(s"Response Success")
      case http: HttpErrorResponse[a] => Doc(s"Respond Failure")
      case save: Save[a] => Doc(s"Save to the Database")
    }

  }
}
