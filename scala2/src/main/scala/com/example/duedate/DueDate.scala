package com.example.duedate

import java.time._
import java.time.format._

sealed trait ParseError
object ParseError {
  case object EmptyInput extends ParseError
  case object InvalidLength extends ParseError
  case object InvalidCharacters extends ParseError
  case object InvalidFormat extends ParseError
  case object InvalidDate extends ParseError
  case object MustBeInFuture extends ParseError
}

final class DueDate private (val value: LocalDate) {
  def isOverdue(today: LocalDate): Boolean = today.isAfter(value)

  def businessDaysFrom(today: LocalDate, holidays: Set[LocalDate]): Int =
    LazyList
      .iterate(today.plusDays(1))(_.plusDays(1))
      .takeWhile(!_.isAfter(value))
      .count(DueDate.isBusinessDay(_, holidays))

  def extendBusinessDays(n: Int, holidays: Set[LocalDate]): DueDate =
    LazyList
      .iterate(value.plusDays(1))(_.plusDays(1))
      .filter(DueDate.isBusinessDay(_, holidays))
      .take(n)
      .lastOption
      .map(new DueDate(_))
      .getOrElse(this)

  override def equals(other: Any): Boolean =
    other match { case that: DueDate => value == that.value; case _ => false }
  override def hashCode: Int = value.hashCode
  override def toString: String = s"DueDate($value)"
}

object DueDate {
  private final val MaxLength = 10
  private final val DateRegex = "^\\d{4}-\\d{2}-\\d{2}$".r
  private final val Formatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

  def apply(value: String, clock: Clock): Either[ParseError, DueDate] =
    if (value.isEmpty) Left(ParseError.EmptyInput)
    else if (value.length != MaxLength) Left(ParseError.InvalidLength)
    else if (!value.forall(c => c.isDigit || c == '-'))
      Left(ParseError.InvalidCharacters)
    else if (!DateRegex.matches(value)) Left(ParseError.InvalidFormat)
    else
      try {
        val date = LocalDate.parse(value, Formatter)
        if (date.isAfter(LocalDate.now(clock))) Right(new DueDate(date))
        else Left(ParseError.MustBeInFuture)
      } catch { case _: DateTimeParseException => Left(ParseError.InvalidDate) }

  private def isBusinessDay(d: LocalDate, holidays: Set[LocalDate]): Boolean =
    d.getDayOfWeek != DayOfWeek.SATURDAY &&
      d.getDayOfWeek != DayOfWeek.SUNDAY &&
      !holidays.contains(d)
}
