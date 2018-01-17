package com.ot.tame.validation

import com.ot.bones.interpreter.ExtractionInterpreter.ValidationResultNel
import com.ot.bones.validation.DataDefinitionOp
import com.ot.tame.ProgramModuleOp
import com.ot.tame.validation.Validate.ValidateData

object Validate {

  case class ValidateData[I,T](dataDefinitionOp: DataDefinitionOp[T]) extends ProgramModuleOp[I => ValidationResultNel[T]]

}



trait ValidateSyntax {

  def validate[I,T](dataDefinitionOp: DataDefinitionOp[T]): ValidateData[I,T] = ValidateData[I,T](dataDefinitionOp)

}

