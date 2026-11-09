# 文字キー単独長押し：入力時系列の測定実験

## 結論と実験の範囲

このブランチは測定専用です。文字キー単独の長押しによるアプリ起動は実装していません。通常入力の遅延を最優先とし、IME変更・特定IMEへの依存を加えずに、描画と検索の保留・変換の取り消しが成立するか調べるためのものです。

PR #1のCtrl方式とは独立して、上流コミット`7d1948a5967f2b4656ab42921761ef828805f804`から分岐しました。PR #1とそのブランチは変更しません。

**採用可能と確認できたのは、入力を保留しない受動的な観測です。** 以下の条件を同時に満たす文字キー単独の起動方式は、まだ確認できていません。

- 通常入力の表示・検索応答を待たせない。
- 最初の文字を長押し判定が終わるまで表示・検索へ反映しない。
- IMEに渡した入力を、長押し時にIME内部の変換状態も含めて確実に取り消す。

最初の2条件は、そのままでは両立しません。IMEへの配送だけを即時にしても、画面表示や検索開始を保留すれば、ユーザーに見える応答は遅延します。

## 入力経路

```text
OS / OEMキーフック
  → SearchEditText.dispatchKeyEventPreIme
  → 選択中のIME
      ├─ IMEが処理する → InputConnectionのcommitText / setComposingTextなど
      └─ IMEが処理しない → Viewの通常dispatchKeyEvent → 必要ならActivity.onKeyDown
  → 検索欄のEditable変更
  → TextWatcher
  → TrimmingTextChangedListener（空白調整・同じqueryの抑制）
  → MainActivity.updateSearchRecords → search → SearchHandler
  → 非同期結果更新 → adapterのDataSetObserver
```

描画`onDraw`はこの検索開始経路とは別です。`onDraw`で文字を隠すだけではEditable、IME、TextWatcher、検索処理は止まりません。取得した実キー記録でも、描画完了の観測より前に検索が始まっています。

`TextWatcher`で表示済み文字を削除すると、IMEのcomposing範囲や選択範囲との整合性に影響します。複数のIME呼び出しとキーイベントに1対1の対応があるとは限りません。日本語ではFの後のUにより、既存の変換中`f`が`ふ`へ置き換わることを観測しました。「最初の1文字だけ後から消す」という前提は成立しません。

## 候補方式の評価

| 方式 | 入力・検索への影響 | 判定 |
|---|---|---|
| pre-IMEで同期的に観測してそのまま返す | 意図的な待ちなし。IMEを通る | 測定用として実装 |
| 描画だけを一時的に隠す | 文字・検索・変換は先へ進む。表示にも遅延が出る | 目的を満たさないため未採用 |
| TextWatcherの検索通知だけを保留 | IMEは進むが短押し時の検索応答が遅れる | 最優先条件に反するため未採用 |
| InputConnectionの編集を保留 | IMEが読む選択位置・周辺文字との不整合、順序問題の危険 | 未採用 |
| 700ms後にsetText("")や削除で戻す | アプリの文字を消せてもIME内部状態の取り消しを保証できない | 未採用 |
| finishComposingTextで戻す | 編集欄の変換中表示を終えるAPIで、入力取り消しではない | 未採用 |
| 入力を表示・検索しながら長押しでアプリ起動 | 待たせないが途中の文字・検索を残す。復帰時の整合性も要検証 | 条件を緩和するなら別途検討可能。今回は未実装 |

全IMEで絶対に不可能と証明したわけではありません。ただし公開APIには「この物理キーに由来するIME内部状態を元に戻す」という共通契約がなく、現時点で安全性を確認できる方式はありません。composing spanがないことも、IME内部にローマ字入力状態がない証明にはなりません。

参照：
- [View.dispatchKeyEventPreIme](https://developer.android.com/reference/android/view/View#dispatchKeyEventPreIme(android.view.KeyEvent))
- [InputConnection：setComposingText、finishComposingText、削除と選択位置](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
- [Android 16 ViewRootImplのpre-IME・IME・post-IMEステージ](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/ViewRootImpl.java)

## 測定版の動作

アプリ名：**KISS Input Lab**。Debugパッケージ：`fr.neamar.kiss.timing.debug`。通常版とPR #1の`fr.neamar.kiss.t2e.debug`に共存します。既定ホーム・標準ショートカット・IMEの設定は自動変更しません。

設定 →「入力時系列の実験」で記録を有効にします。初期状態は無効です。

- キーコード、押下・解放・repeat、device、meta、flags、元のイベント時刻を記録。
- 主要InputConnection編集呼び出しの前後、TextWatcher、検索更新・検索開始、検索結果更新、描画処理の完了を記録。
- 各段階のテキスト（最大256 UTF-16単位）、全文の長さ、選択範囲、composing範囲を記録。
- 空の検索欄で修飾キーなしのFを押すと、700ms後に観測マーカーを記録。別のキー、F解放、フォーカス喪失、画面離脱でマーカー待機をキャンセル。
- キーやInputConnection呼び出しは消費・保留・再送せず、元の処理へそのまま同期的に渡す。
- 文字削除、描画隠蔽、検索保留、IME再起動、アプリ起動は行わない。

記録はメモリ上の4096件までです。超過すると古い記録を捨て、書き出しの`dropped`に件数を記録します。文字とキー情報を含むため、テスト文字だけで使用してください。外部送信はありません。書き出しボタンでアプリ専用`files/input-timeline.jsonl`へ保存します。メモリの記録消去だけでは既存の書き出しファイルは消えません。

## 測定の限界

- 意図的な入力待ちや700msバッファはありませんが、観測コードには実行コストがあります。「追加遅延ゼロ」を保証するAPKではありません。
- `encode_ns`はレコードのJSON準備部分の時間です。ロック待ち、バッファ挿入、引数生成、GC、ラッパー全体の時間は含みません。
- `t_ns`は`System.nanoTime()`。`uptime_ms`はKeyEventの時刻との照合用です。これらから人間の物理的な押下開始時刻やディスプレイの表示時刻は直接分かりません。
- `draw.return`はViewの描画処理完了です。画面上でピクセルが表示された時刻ではありません。
- すべてのInputConnection APIを個別に記録しているわけではありません。未計測のAPIもInputConnectionWrapper経由で委譲し、実テキスト変更はTextWatcherで観測します。
- IMEの内部ローマ字バッファ、候補状態、学習状態は観測できません。モード名を自動判定しないため、試行順と人間の記録で対応づけます。
- 記録無効と有効の無作為化した比較試験は未実施です。単一ログから追加遅延や欠落の不存在は断言しません。

## 再現手順

各試行前に検索欄の×で完全に空にしてください。英語／日本語それぞれで：

1. Fを短押し。
2. 検索欄を空にし、Fを約1秒長押し。
3. 検索欄を空にし、`fuji`を連続入力。
4. 日本語では「ふじ」の変換・確定、Backspace、続けて次の語の入力。
5. Shift併用、空でない検索欄、素早い複数キー入力、長押し中の画面離脱も別試行で確認。

測定後、設定で書き出し、記録を無効にします。入力の欠落・順序逆転・変換崩れ・体感遅延があれば、試行と一緒に記録してください。

```powershell
# adb -sには対象のserialを指定する
adb -s <serial> exec-out run-as fr.neamar.kiss.timing.debug cat files/input-timeline.jsonl > trace.jsonl
python tools/analyze_input_timeline.py trace.jsonl > summary.json
```

解析スクリプトは空欄からのF押下ごとに、文字変更・検索開始・描画処理完了までの時間、キー保持時間、リピート回数を整理します。IMEの言語モードや内部状態は推定しません。

## ビルド・テスト・復帰

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w -r -e class fr.neamar.kiss.androidTest.InputTimelineTest fr.neamar.kiss.timing.debug.test/androidx.test.runner.AndroidJUnitRunner
```

自動テストは直接呼び出したInputConnection／Viewの透過性と処理順序の確認です。実IMEで日本語ローマ字変換が成功したことの代替にはなりません。連絡先プロバイダーをテスト期間中だけ無効にし、設定を復元します。権限の追加付与は不要です。

通常版／Ctrl版へ戻す場合は、そのアプリを開き、必要ならAndroidの既定ホームを変更してください。実験版を削除する場合は`fr.neamar.kiss.timing.debug`と`fr.neamar.kiss.timing.debug.test`だけをアンインストールします。他パッケージのデータには影響しません。
