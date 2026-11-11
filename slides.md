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
  section.opaque pre { font-size: 0.68em; }
  section.dense3 { font-size: 24px; padding: 30px 44px; }
  section.dense3 pre { font-size: 0.48em; line-height: 1.25; }
  section.code-large { font-size: 24px; padding: 52px 44px 36px; }
  section.code-large h1 { margin: 0 0 12px; }
  section.code-large pre { font-size: 0.72em; line-height: 1.2; margin: 0 0 12px; padding: 0.6em 0.9em; }
  section.code-large ul { margin: 0; }
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
10秒。テーマは、ビジネスロジックを不正なデータから守る最小単位、ドメインプリミティブです。
Scala 3 でバリューオブジェクトを書くと、Scala 2 の儀式がどれだけ減るかを見ます。
-->

---

<!-- _class: dense2 -->

# Scala 2 の痛み：守れる。でも儀式が多い

`DueDate` は、期限の日付とルールを持つ小さな型。これをドメインプリミティブと呼ぶ。

```scala
// import java.time._ / java.time.format._
// ParseError の定義は省略
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
35秒。まず Scala 2 です。
DueDate は期限という業務上の意味とルールを表す小さな型で、バリューオブジェクトとして実装します。
不正な値を作れない設計は Scala 2 でも可能ですが、正しく書くとこの量になります。
LocalDate をクラスで包み、equals、hashCode、toString を定義しています。
痛みは守れないことではなく、守るための儀式の量です。次の Scala 3 版と並べるので、行数と置き場所だけ意識して見てください。
-->

---

<!-- _class: dense2 opaque -->

# opaque type：実体を隠し、別の型として扱う

コンパイル時は `DueDate` と `LocalDate` を区別し、実行時は同じ値を使う。

```scala
// DueDate.scala のトップレベル
opaque type DueDate = LocalDate

// 利用側の別ファイル（import と clock の用意は省略）
val raw: LocalDate = LocalDate.of(2026, 11, 13)
val due: DueDate = raw // コンパイルエラー：別の型なので代入できない
val result = DueDate.parse("2026-11-13", clock) // 検証して生成する
```

- 普通の `type DueDate = LocalDate` は別名なので、直接代入できてしまう
- この例では定義側で実体を扱い、別ファイルの利用側には `DueDate` として公開する
- 実行時に値を包むクラスは不要。等価性も実体の `LocalDate` を使う
- `opaque type` は型を区別する仕組み。日付の検証は次の `parse` に書く

<!--
45秒。opaque は「不透明」という意味です。利用側から、型の実体を隠します。
普通の type による別名は LocalDate と同じ型ですが、opaque type は利用側で別の型として扱います。
このコードは2つのファイルの抜粋です。別ファイルから LocalDate をそのまま DueDate に代入するとコンパイルエラーになります。
今回のトップレベルの定義では、同じファイルのトップレベルと companion object の DueDate が実体を扱います。
実行時は LocalDate の値をそのまま使うので、前のスライドのように包むクラスや equals を書く必要がありません。
ただし、型を宣言するだけでは日付を検証しません。次に示す parse を通して、検証に成功した値を返す設計にします。
参考: https://docs.scala-lang.org/scala3/reference/other-new-features/opaques.html
定義の範囲: https://docs.scala-lang.org/scala3/reference/other-new-features/opaques-details.html
-->

---

<!-- _class: code-large -->

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
- 生成時の「未来かどうか」は、外から渡した `Clock` の時刻で判定する
- 文字列を受け取る境界で検証し、成功した `Right` の `DueDate` を内部へ渡す

<!--
55秒。Scala 3 版です。エラー型の列挙は上に置き、ここでは parse の処理を見ます。
parse は検証を4層に分けています。1層目は長さ、2層目は数字とハイフンだけか、3層目は正規表現の形か、4層目は意味です。
意味はさらに2つあり、実在する日付かと、未来かどうか。「2027-02-30」は3層目まで通り、4層目の実在判定で落ちます。
未来の判定には現在時刻が要るので、Clock を引数で受け取ります。テストでは時刻を固定できます。
文字列を受け取る箇所がシステムの境界です。失敗は Left のエラー、成功は Right の DueDate として返します。
内部の処理は DueDate を受け取り、文字列の形式や実在日付の検証を繰り返しません。未来という条件は生成時のもので、時間が経てば期限切れになります。
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
30秒。Scala 2 の振る舞いです。isOverdue と extendBusinessDays を見ます。
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

- 拡張メソッドを定義する `extension (due: DueDate)` で、`due.isOverdue(today)` のように呼べる
- `DueDate` を受け取るメソッドとして、期限切れ判定と営業日での延長を定義する
- 延長は元の値を書き換えず、新しい `DueDate` を返す。時刻と祝日は外から渡す

<!--
45秒。Scala 3 の振る舞いです。extension は拡張メソッドを定義する構文です。
丸括弧の due はメソッドを呼び出す対象です。isOverdue の中では due を使い、利用側では due.isOverdue(today) と呼びます。
opaque type にはクラスの本体がないので、この構文で DueDate の振る舞いを定義します。営業日を計算するロジック自体は Scala 2 と共通です。
isBusinessDay は土日と祝日を除く private の補助関数です。
具体例を挙げます。金曜の 2026年11月6日から、期限の金曜 11月13日までは5営業日。金曜 11月13日を3営業日延ばすと水曜の11月18日になります。
延長は自分を書き換えず、新しい DueDate を返します。today も引数で受け取るので、ここでも now() は呼びません。
businessDaysFrom は同じ extension に置き、テストで確認しています。
-->

---

# 小さな型に、業務の意味とルールを集める

定型記述が減ると、業務の概念ごとに小さな型を定義しやすくなる。

| `DueDate` で表したいこと | Scala 3 での書き方 |
| --- | --- |
| 日付に「期限」という名前を付け、他の日付と区別する | `opaque type` で型を分ける |
| 生成時は未来の日付だけを受け付ける | `parse` で検証する |
| 期限切れ判定・営業日での延長を提供する | `extension` でメソッドを定義する |

- `DueDate` のような型を作るたびに、包むクラスや等価性の定義を書かずに済む
- 引数を `DueDate` にすると、「日付のうち期限を受け取る」と型で伝えられる
- 業務の意味ごとに型とルールを表すことが、ドメインモデルの解像度を上げる

<!--
45秒。ここまでで、期限という意味、生成時の検証、期限に関する振る舞いを1つの型に集めました。
Scala 2 でも同じ設計はできます。Scala 3 では包むクラスや等価性の定義が不要なので、小さな型を作るたびの記述負担が減ります。
その分、業務の概念ごとに型を作りやすくなります。これが、小さな型を多く置けるという話につながります。
たとえば引数の型が LocalDate なら日付までしか分かりませんが、DueDate なら期限を受け取ると読めます。
このように業務上の区別とルールを型で表すことを、ドメインモデルの解像度が上がると言っています。
-->

---

# まとめ

- `opaque type` で実体を隠し、値を包むクラスを書かずに型を区別する
- `extension` で、その型に業務の振る舞いを定義する
- 定型記述を減らし、業務の意味とルールを小さな型で表しやすくする
- 文字列の検証は生成時の境界に集め、内部は検証済みの型を受け取る

リポジトリ: https://github.com/j5ik2o/nextbeat-tech-bar-22
`sbt test` で両モジュールのテストが動きます。

<!--
35秒。opaque type で型を区別し、extension で業務の振る舞いを定義しました。
値を包むクラスや等価性の定型記述が減るので、業務の意味ごとに小さな型を作りやすくなります。
検証は生成時の境界に集め、内部は検証済みの型を受け取ります。
Scala 2 でも実現できる設計を、Scala 3 では少ない定型記述で表せます。コードは GitHub にあり、sbt test で動きます。
-->
