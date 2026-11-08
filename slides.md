---
marp: true
theme: default
size: 16:9
paginate: true
header: "ビジネスロジックを堅牢に ｜ Scala 3 のドメインプリミティブ"
style: |
  section { font-size: 28px; }
  section.lead { text-align: center; }
  section.lead h1 { font-size: 50px; }
  pre { font-size: 0.72em; }
  section.dense pre { font-size: 0.58em; }
  section.dense2 { font-size: 26px; padding: 36px 50px; }
  section.dense2 pre { font-size: 0.5em; }
  section.dense3 { font-size: 24px; padding: 30px 44px; }
  section.dense3 pre { font-size: 0.48em; line-height: 1.25; }
  table { font-size: 0.82em; }
---

<!-- _class: lead -->
<!-- _header: "" -->
<!-- _paginate: false -->

# ビジネスロジックを堅牢に

## Scala 3 のドメインプリミティブ

かとじゅん（@j5ik2o）
Nextbeat Tech Bar #22

<!--
15秒。テーマは、ビジネスロジックを不正なデータから守る最小単位、ドメインプリミティブです。
Scala 3 でバリューオブジェクトを書くと、Scala 2 の儀式がどれだけ減るかを見ます。
-->

---

<!-- _class: dense2 -->

# Scala 2 の痛み：守れる。でも儀式が多い

```scala
// import java.time._ / java.time.format._
sealed trait ParseError
object ParseError {
  case object EmptyInput extends ParseError
  case object InvalidLength extends ParseError
  // ... InvalidCharacters / InvalidFormat / InvalidDate / MustBeInFuture
}
final class DueDate private (val value: LocalDate) {
  override def equals(other: Any): Boolean =
    other match { case that: DueDate => value == that.value; case _ => false }
  override def hashCode: Int = value.hashCode
  override def toString: String = s"DueDate($value)"
}
object DueDate {
  // MaxLength = 10 / DateRegex / Formatter は省略
  def apply(value: String, clock: Clock): Either[ParseError, DueDate] =
    if (value.isEmpty) Left(ParseError.EmptyInput)
    else if (value.length != MaxLength) Left(ParseError.InvalidLength)
    // ... 文字種 → 正規表現 → 実在日付 → 未来かどうか
```

<!--
50秒。まず Scala 2 です。
不正な値を作れない設計は Scala 2 でも可能ですが、正しく書くとこの量になります。
エラー型に7つの定義、equals、hashCode、toString で4行。さらに営業日の振る舞いはクラスか implicit class に分けて書きます。
痛みは守れないことではなく、守るための儀式の量です。次の Scala 3 版と並べるので、行数と置き場所だけ意識して見てください。
-->

---

<!-- _class: dense3 -->

# DueDate を Scala 3 で書く：生成と検証

```scala
enum ParseError:
  case EmptyInput, InvalidLength, InvalidCharacters,
    InvalidFormat, InvalidDate, MustBeInFuture
opaque type DueDate = LocalDate
object DueDate:
  // MaxLength = 10 / DateRegex / Formatter（STRICT）は省略
  def parse(value: String, clock: Clock): Either[ParseError, DueDate] =
    if value.isEmpty then Left(ParseError.EmptyInput) // 1 サイズ
    else if value.length != MaxLength then Left(ParseError.InvalidLength)
    else if !value.forall(c => c.isDigit || c == '-') then // 2 字句
      Left(ParseError.InvalidCharacters)
    else if !DateRegex.matches(value) then Left(ParseError.InvalidFormat) // 3 構文
    else
      try
        val date = LocalDate.parse(value, Formatter)
        if date.isAfter(LocalDate.now(clock)) then Right(date) // 4 意味（未来）
        else Left(ParseError.MustBeInFuture)
      catch case _: DateTimeParseException => Left(ParseError.InvalidDate)
```

- 検証は4層：サイズ → 字句的内容 → 構文 → 意味。「2027-02-30」は構文を通り、意味で落ちる
- 「未来であること」も意味の検証。現在時刻は Clock で受け取り、now() を直接呼ばない
- opaque type で実体は LocalDate のまま、ファイルの外からは DueDate にしか見えない

<!--
60秒。Scala 3 版です。宣言まわりが enum 3行と opaque type 1行に縮みました。
parse は検証を4層に分けています。1層目は長さ、2層目は数字とハイフンだけか、3層目は正規表現の形か、4層目は意味です。
意味はさらに2つあり、実在する日付かと、未来かどうか。「2027-02-30」は3層目まで通り、4層目の実在判定で落ちます。
未来の判定には現在時刻が要るので、Clock を引数で受け取ります。now() を直接呼ばないので、テストでは固定でき、検証が決定的になります。
-->

---

<!-- _class: dense2 -->

# DueDate の振る舞い（Scala 2）

```scala
// 振る舞いはクラスの中に同居させるのが基本
final class DueDate private (val value: LocalDate) {
  def isOverdue(today: LocalDate): Boolean = today.isAfter(value)

  def extendBusinessDays(n: Int, holidays: Set[LocalDate]): DueDate = {
    var remaining = n
    var d = value
    while (remaining > 0) {
      d = d.plusDays(1)
      if (DueDate.isBusinessDay(d, holidays)) remaining -= 1
    }
    new DueDate(d)
  }

  // businessDaysFrom・equals / hashCode / toString はリポジトリ参照
}
```

<!--
45秒。Scala 2 の振る舞いです。isOverdue と extendBusinessDays を見ます。
期限切れ判定は today が期限より後かを見るだけ。延長は営業日を数えながら日付を進め、最後に新しい DueDate を返します。
営業日を数える businessDaysFrom と、equals などの定義はリポジトリに置いています。
Scala 2 では振る舞いはクラスの中に同居し、型の定義と常に一緒にあります。計算の中身は次の Scala 3 版と同じで、違いは置き場所です。
-->

---

<!-- _class: dense3 -->

# DueDate の振る舞い（Scala 3）

```scala
object DueDate:
  private def isBusinessDay(d: LocalDate, holidays: Set[LocalDate]): Boolean =
    d.getDayOfWeek != DayOfWeek.SATURDAY &&
      d.getDayOfWeek != DayOfWeek.SUNDAY && !holidays.contains(d)

  extension (due: DueDate)
    def isOverdue(today: LocalDate): Boolean = today.isAfter(due)

    def extendBusinessDays(n: Int, holidays: Set[LocalDate]): DueDate =
      var remaining = n
      var d = due
      while remaining > 0 do
        d = d.plusDays(1)
        if isBusinessDay(d, holidays) then remaining -= 1
      d
```

- getter ではない、ドメインの振る舞い：期限切れ判定と営業日での延長
- extension なら、型の定義から切り離して置ける。文脈ごとに必要な振る舞いだけを足せる
- 延長は自分を書き換えず、新しい DueDate を返す。祝日と today は外から渡し、バリューオブジェクトは純粋なまま
- businessDaysFrom は同じ extension にあり、リポジトリとテストで確認できる

<!--
50秒。Scala 3 の振る舞いです。extension で、型の定義から切り離して振る舞いを足せます。
isBusinessDay は土日と祝日を除く private の補助関数です。
具体例を挙げます。金曜の 2026年11月6日から、期限の金曜 11月13日までは5営業日。金曜 11月13日を3営業日延ばすと水曜の11月18日になります。
延長は自分を書き換えず、新しい DueDate を返します。today も引数で受け取るので、ここでも now() は呼びません。
businessDaysFrom は同じ extension に置き、テストで確認しています。
-->

---

# まとめ

- Scala 3 で変わったのは、バリューオブジェクトの「意味」ではなく「儀式の量」
- opaque type、enum、extension の3つで、書くべきでないコードが消えた
- 儀式が減ると、バリューオブジェクトを小さく・多く置く抵抗が減り、ドメインモデルの解像度が上がる
- 検証は生成時（境界）に寄せ、中では型を信じる

リポジトリ: https://github.com/j5ik2o/nextbeat-tech-bar-22
`sbt test` で両モジュールのテストが動きます。

<!--
30秒。Scala 3 で変わったのはバリューオブジェクトの意味ではなく、書く儀式の量でした。
opaque type、enum、extension の3つで、書かなくてよいコードが消えました。
儀式が減ると、バリューオブジェクトを小さく多く置けるので、ドメインモデルの解像度が上がります。
原則は、検証は生成時の境界に寄せ、中では型を信じること。コードは GitHub にあり、sbt test で動きます。
-->
