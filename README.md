# ビジネスロジックを堅牢に ～Scala 3 のドメインプリミティブ～

Nextbeat Tech Bar #22 のサンプルコードです。

- スライド: [slides.md](slides.md)
- イベント: [Nextbeat Tech Bar #22](https://nextbeat.connpass.com/event/407155/)

## これは何か

「期限」を表す `DueDate` を Scala 3 と Scala 2 の両方で実装した最小のサンプルです。同じ検証と振る舞いを、同じ観点のテストで確認できます。`sbt test` を実行すると、両方のモジュールのテストが通ります。

題材の `DueDate` は、文字列から生成し、生成時に検証し、期限切れ判定や営業日計算といったドメインの振る舞いを持ちます。このような小さな型をドメインプリミティブ、またはバリューオブジェクトと呼びます。

## なぜ（境界で検証し、中では型を信じる）

外部から来る文字列は、そのままでは信用できません。`DueDate.parse` は文字列を検証し、`Either[ParseError, DueDate]` を返します。`Right` を得た後は、`DueDate` を正しい値として扱えます。検証は生成時、つまりシステムの境界に寄せ、内部では型を信じる、という原則です。この設計は "parse, don't validate" と呼ばれます。

Scala 3 の3つの構文を使うと、この原則を少ないコードで運用できます。

- `opaque type` は、実体を定義したファイルの外に隠します。外からは `DueDate` という別の型に見えるので、`String` や `LocalDate` と取り違えません。
- `enum` は代数的データ型を簡潔に書く構文です。ここではエラー型を6つの値を持つ型として表します。
- `extension` は既存の型にメソッドを追加する構文です。暗黙変換を使わずに振る舞いを足せます。

## 動かし方

前提として [mise](https://mise.jdx.dev/) を使います。`mise.toml` に Java 21 と sbt 1.13.1 を書いてあります。

```sh
mise trust
mise install
sbt test
sbt "scala3/run"
```

- `mise trust` と `mise install` で、リポジトリ指定の Java と sbt を用意します。
- `sbt test` は `scala3` と `scala2` の両方でテストを実行します。
- `sbt "scala3/run"` は `scala3` の `Main.scala` を実行し、入力ごとの `OK` またはエラー名を出力します。

`sbt "scala3/run"` の出力例です。

```text
"" -> EmptyInput
"2026-1-1" -> InvalidLength
"2026-11-1a" -> InvalidCharacters
"2026-111-3" -> InvalidFormat
"2027-02-30" -> InvalidDate
"2026-11-07" -> MustBeInFuture
"2026-11-13" -> OK (2026-11-13)
```

## 構成

```text
.
├── build.sbt                  # ルートプロジェクトと2つのサブプロジェクト
├── project/
│   ├── build.properties       # sbt 1.13.1
│   └── plugins.sbt            # scalafmt
├── scala3/
│   └── src/
│       ├── main/scala/com/example/duedate/DueDate.scala
│       ├── main/scala/com/example/duedate/Main.scala
│       └── test/scala/com/example/duedate/DueDateSpec.scala
├── scala2/
│   └── src/
│       ├── main/scala/com/example/duedate/DueDate.scala
│       └── test/scala/com/example/duedate/DueDateSpec.scala
├── slides.md                  # 5分の発表スライド
├── mise.toml
└── .github/workflows/ci.yml
```

`scala3` は Scala 3.3.9、`scala2` は Scala 2.13.18 でビルドします。パッケージ名とファイル名は両方で同じにしてあり、差分を読み比べられます。

## コードの読み方

### 検証の4層

`DueDate.parse` は入力を次の順に検証します。

1. サイズ: 10文字か。違えば `InvalidLength`。
2. 字句的内容: 数字とハイフンだけか。違えば `InvalidCharacters`。
3. 構文: `uuuu-MM-dd` の形か。違えば `InvalidFormat`。
4. 意味: 実在する日付か。違えば `InvalidDate`。未来の日付か。違えば `MustBeInFuture`。

`2027-02-30` は 3 層目まで通り、4 層目の実在判定で落ちます。`2026-11-08` は実在しますが、テストで固定した現在日時と同じなので `MustBeInFuture` になります。

### 現在時刻は外から渡す

`parse(value, clock)` は現在時刻を `Clock` で受け取ります。`isOverdue(today)`、`businessDaysFrom(today, holidays)`、`extendBusinessDays(n, holidays)` も日付や祝日を引数で受け取ります。型の中で `now()` を直接呼ばないので、テストでは `Clock.fixed` で時刻を固定できます。

### Scala 2 と Scala 3 の対応

| 役割 | Scala 2 | Scala 3 |
| --- | --- | --- |
| 実体を隠す | `final class DueDate private` | `opaque type DueDate = LocalDate` |
| エラー型 | `sealed trait ParseError` と6つの `case object` | `enum ParseError` と6つの `case` |
| 等価性 | `equals`、`hashCode`、`toString` を手で定義 | 実体が `LocalDate` なので定義不要 |
| 振る舞いの追加 | クラス内のメソッド、または `implicit class` | `extension` |
| 生成 | `DueDate.apply(value, clock)` | `DueDate.parse(value, clock)` |

Scala 3 のテストでは、`opaque type` の値を直接比較せず、`toString` で `LocalDate` の表記を取り出して比較しています。Scala 2 のテストでは `DueDate.value` を直接比較します。どちらも、利用者が観察できる結果だけを検証しています。

## スライドとの差分

5分に収めるため、またスライドのコードをそのまま動かすために、次の差分があります。

| 箇所 | 差分 | 理由 |
| --- | --- | --- |
| 「Scala 3 の道具箱」 | スライドを削除 | 5分に収めるため。`opaque type`、`enum`、`extension` の説明は本文とコードに残した |
| 「境界で守る」 | スライドを削除し、まとめの4番目の箇条書きに統合 | 同上。原則は「検証は生成時（境界）に寄せ、中では型を信じる」 |
| 振る舞い（Scala 3） | 先頭に `object DueDate:` を追加 | スライドの断片だけではコンパイルできないため |
| 振る舞い（Scala 3） | `businessDaysFrom` をスライドから削除 | 行数を抑えるため。コードとテストには残した |
| 振る舞い（Scala 2） | `businessDaysFrom` をスライドから削除 | 同上。コードとテストには残した |
| まとめ | リポジトリのリンクと `sbt test` を追加 | 参加者が手元で再現できるようにするため |

コード側で補った定義は、スライドで省略していた `MaxLength`、`DateRegex`、`Formatter` です。`private def isBusinessDay` は Scala 2 でも companion object に置き、クラスから `DueDate.isBusinessDay` で呼んでいます。

## ライセンス

MIT ライセンスです。Copyright (c) 2026 Junichi Kato。詳細は [LICENSE](LICENSE) を参照してください。
