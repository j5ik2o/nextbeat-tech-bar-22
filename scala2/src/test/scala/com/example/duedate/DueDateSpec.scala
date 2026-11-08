package com.example.duedate

import java.time._

class DueDateSpec extends munit.FunSuite {

  private val clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC)

  private def parsed(value: String): Option[LocalDate] =
    DueDate(value, clock).toOption.map(_.value)

  private def error(value: String): Option[ParseError] =
    DueDate(value, clock).left.toOption

  private def dueDate(value: String): DueDate =
    DueDate(value, clock).toOption.get

  test("空文字は EmptyInput") {
    assertEquals(error(""), Some(ParseError.EmptyInput))
  }

  test("長さが 10 でなければ InvalidLength") {
    assertEquals(error("2026-11-1"), Some(ParseError.InvalidLength))
  }

  test("数字とハイフン以外は InvalidCharacters") {
    assertEquals(error("2026-11-1a"), Some(ParseError.InvalidCharacters))
  }

  test("正規表現の形を外れると InvalidFormat") {
    assertEquals(error("2026-111-3"), Some(ParseError.InvalidFormat))
  }

  test("存在しない日付は InvalidDate") {
    assertEquals(error("2027-02-30"), Some(ParseError.InvalidDate))
  }

  test("過去日は MustBeInFuture") {
    assertEquals(error("2026-11-07"), Some(ParseError.MustBeInFuture))
  }

  test("当日は MustBeInFuture") {
    assertEquals(error("2026-11-08"), Some(ParseError.MustBeInFuture))
  }

  test("未来日はパースできる") {
    assertEquals(parsed("2026-11-09"), Some(LocalDate.parse("2026-11-09")))
    assertEquals(parsed("2026-11-13"), Some(LocalDate.parse("2026-11-13")))
  }

  test("isOverdue は期限の翌日から true") {
    val due = dueDate("2026-11-13")
    assert(!due.isOverdue(LocalDate.parse("2026-11-12")))
    assert(!due.isOverdue(LocalDate.parse("2026-11-13")))
    assert(due.isOverdue(LocalDate.parse("2026-11-14")))
  }

  test("businessDaysFrom は土日を除いて数える") {
    val due = dueDate("2026-11-13")
    assertEquals(due.businessDaysFrom(LocalDate.parse("2026-11-06"), Set.empty), 5)
  }

  test("extendBusinessDays は営業日だけ進める") {
    val due = dueDate("2026-11-13")
    assertEquals(due.extendBusinessDays(3, Set.empty).value, LocalDate.parse("2026-11-18"))
  }

  test("extendBusinessDays は祝日を飛ばす") {
    val due = dueDate("2026-11-13")
    val holidays = Set(LocalDate.parse("2026-11-16"))
    assertEquals(due.extendBusinessDays(1, holidays).value, LocalDate.parse("2026-11-17"))
  }
}
