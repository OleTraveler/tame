package com.ot

import cats.free.FreeApplicative
import com.ot.tame.db.DatabaseSyntax
import com.ot.tame.http.HttpSyntax
import com.ot.tame.validation.ValidateSyntax

package object tame {
  /** Bones is the base class defining the FreeAp for each field group defined.*/
  trait ProgramModuleOp[A] {
    //lift any BonesOp into a FreeApplicative
    def lift: ProgramModule[A] = FreeApplicative.lift(this)
  }

  type ProgramModule[A] = FreeApplicative[ProgramModuleOp, A]

  object everything extends DatabaseSyntax with HttpSyntax with ValidateSyntax

}
