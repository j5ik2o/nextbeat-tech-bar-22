package com.example.duedate

import java.time._
import java.time.format._

enum ParseError:
  case EmptyInput, InvalidLength, InvalidCharacters,
    InvalidFormat, InvalidDate, MustBeInFuture

opaque type DueDate = LocalDate

object DueDate:
  private final val MaxLength = 10
  private final val DateRegex = "^\\d{4}-\\d{2}-\\d{2}$".r
  private final val Formatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

  def parse(value: String, clock: Clock): Either[ParseError, DueDate] =
    if value.isEmpty then Left(ParseError.EmptyInput)
    else if value.length != MaxLength then Left(ParseError.InvalidLength)
    else if !value.forall(c => c.isDigit || c == '-') then Left(ParseError.InvalidCharacters)
    else if !DateRegex.matches(value) then Left(ParseError.InvalidFormat)
    else
      try
        val date = LocalDate.parse(value, Formatter)
        if date.isAfter(LocalDate.now(clock)) then Right(date)
        else Left(ParseError.MustBeInFuture)
      catch case _: DateTimeParseException => Left(ParseError.InvalidDate)

  private def isBusinessDay(d: LocalDate, holidays: Set[LocalDate]): Boolean =
    d.getDayOfWeek != DayOfWeek.SATURDAY &&
      d.getDayOfWeek != DayOfWeek.SUNDAY && !holidays.contains(d)

  extension (due: DueDate)
    def isOverdue(today: LocalDate): Boolean = today.isAfter(due)

    def businessDaysFrom(today: LocalDate, holidays: Set[LocalDate]): Int =
      var count = 0
      var d = today.plusDays(1)
      while !d.isAfter(due) do
        if isBusinessDay(d, holidays) then count += 1
        d = d.plusDays(1)
      count

    def extendBusinessDays(n: Int, holidays: Set[LocalDate]): DueDate =
      var remaining = n
      var d = due
      while remaining > 0 do
        d = d.plusDays(1)
        if isBusinessDay(d, holidays) then remaining -= 1
      d
