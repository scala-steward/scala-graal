import sbt._

object GenTemplateBoilerplate {

  def apply(outputDir: File): File = {

    val groups =
      (1 to 22).map { n =>
        def up(i: Int) = (i + 65).toChar
        def down(i: Int) = (i + 97).toChar
        val _ABC = (0 until n).map(up)
        val ABC = _ABC.mkString(",")
        val aAbBcC = _ABC.map(A => s"${A.toLower}: $A").mkString(", ")
        val Params = (0 until n).map(i => s"${up(i)}:Param[${up(i)}]").mkString(", ")
        val fromStrs = (0 until n).map(i => s"${up(i)}.fromStr(a($i))").mkString(",")
        val toStrs = (0 until n).map(i => s"${up(i)}.toStr(${down(i)})").mkString(",")
        s"""
           |  def compileI$n[$ABC](f: ($ABC) => String)(implicit $Params): ($ABC) => String =
           |    compile$n[Id, $ABC](f)
           |
           |  def compile$n[Z[_], $ABC](f: ($ABC) => Z[String])(implicit Z: Functor[Z], $Params): Z[($ABC) => String] =
           |    compileGeneric($n, a => f($fromStrs)).map(x => ($aAbBcC) => x(Array($toStrs)))
         """.stripMargin.trim.replaceFirst("^", "  ")
      }

    val Name = "TemplateBoilerplate"

    val sep = s"\n  // ${"=" * 115}\n\n"

    val content =
      s"""
         |package japgolly.scalagraal.util
         |
         |import cats.{Functor, Id}
         |import cats.syntax.functor._
         |import Template.Param
         |
         |abstract class $Name private[util]() {
         |
         |  protected def compileGeneric[F[_]: Functor](arity: Int, f: Array[String] => F[String]): F[Array[String] => String]
         |$sep${groups.mkString("\n" + sep)}
         |}
        """.stripMargin.trim

    val file = (outputDir / "japgolly" / "scalagraal" / "util" / s"$Name.scala").asFile
    IO.write(file, content)
    println(s"Generated ${file.getAbsolutePath}")
    file
  }
}
