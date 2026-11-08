package com.example.duedate

import java.time._

object Main:
  def main(args: Array[String]): Unit =
    val clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC)
    val inputs = List(
      "",
      "2026-1-1",
      "2026-11-1a",
      "2026-111-3",
      "2027-02-30",
      "2026-11-07",
      "2026-11-13"
    )

    inputs.foreach { input =>
      DueDate.parse(input, clock) match
        case Right(due)  => println(s"\"$input\" -> OK ($due)")
        case Left(error) => println(s"\"$input\" -> $error")
    }
