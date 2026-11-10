# 2キー順番押しショートカット

ベース: `feat/widget-grid-swipe-integration` / `f89fdf1f1f8f99d20f4e0fda4bf9fc86bc2b0a8a`

ショートカット実装ブランチ: `feat/t2e-sequential-keyboard-shortcuts`

共存用Debug APKブランチ: `feat/t2e-sequence-debug-coexist`。
ショートカット実装コミット`10184a0b`をベースに、Debug版のアプリIDと表示名のみ変更。
署名設定・ウィジェット・ショートカットの実装は維持する。

## 設定と操作

1. KISSの設定 →「キーボードショートカット」を開く。
2. 「2キー順番押しを有効にする」をオンにする（初期状態はオフ）。
3. 開始キーを選ぶ。初期値はSpace。Tab、CapsLock、A〜Z、0〜9も選択可能。
4. 「アプリの割り当て」でF → Firefox、D → Discord、C → Chromeなどを登録する。
   同じ画面で変更でき、「未登録／割り当て解除」で削除できる。
5. 待機時間は300〜2000ms、初期値800ms。
6. 検索欄が空でフォーカスがある状態で、Spaceを押して**離し**、時間内にFを押す。

開始キーの押下・リピート・解放は消費する。待機時間は解放から計測する。
登録済みの2キー目は押下で一度だけ起動し、リピートと解放は消費する。
未登録キー、無効化・アンインストールされたアプリのキーは通常処理へ直ちに渡す。
タイムアウトした開始キーは破棄する。入力の遅延・再送は行わない。
同時押しは起動しない。Ctrl / Alt / Meta / Shiftとの組み合わせも対象外。

Esc、別のキー操作、画面タッチ、検索テキスト変更、フォーカス喪失、ウィンドウの
フォーカス喪失、Activityのpause、新しいIntent、戻る操作で待機を解除する。
タイムアウトは次のイベントの時刻と期限を比較して判定するため、遅延コールバックは不要。

## IME保護と制約

`SearchEditText.dispatchKeyEventPreIme()`で処理する。IME後のActivityキー処理では
同じ状態遷移を実行せず、二重処理を避ける。Titan IMEへの依存はない。
AndroidはこのAPIをIMEより前のViewへのキー配送として定義している。
[ViewのAPI仕様](https://developer.android.com/reference/android/view/View#dispatchKeyEventPreIme(android.view.KeyEvent))

文字列の長さが0でも、IMEが内部に未確定ローマ字を保持している場合がある。
そのため文字列とcomposing spanの検査に加え、通常の文字キー・削除キーをIMEへ渡した後、
またはInputConnectionへのcommit/composition通知やテキスト変更の後は、開始を抑止する。
composing spanやInputConnectionはIME内部状態の完全な照会手段ではない。
[InputConnectionのAPI仕様](https://developer.android.com/reference/android/view/inputmethod/InputConnection)

**Backspaceで空に戻しただけでは開始を再許可しない。** 検索のクリア操作は
`restartInput()`を要求し、`onCreateInputConnection()`で新しい入力セッションを確認して
再許可する。KISSを離れて戻るなど、新しい入力セッションでも再許可される。
機能が無効な場合にはこの追加のrestartInputを行わない。
IMEが通常の入力セッション契約に従わない場合まで完全な保証はできない。

物理キーコードに対応するため、数字がFn等で別コードとして送られる端末では要確認。
ソフトキーボード由来のイベント、仮想デバイスIDの入力は起動対象外。
Space / Tab / CapsLock / 文字キーの**TITAN 2 ELITEでの取得可否は未検証**。
Androidや端末独自ショートカットがView到達前に消費したキーは、この実装では取得できない。
IMEがキーイベントではなく文字列だけを送る場合も対象外。
CapsLockは消費してもシステムのCaps状態が切り替わる可能性がある。
端末側ショートカットと競合する場合は、その割り当てを解除するか開始キーを変更する。

## 実装範囲

- `KeySequence`: Android非依存の状態遷移。端末IDとdownTimeで同じ押下を識別する。
- `SequentialShortcuts`: 有効条件、IME保護、設定取得、既存の`Result.fromPojo(...).launch(...)`による起動。
- `SearchEditText`: IME前フックと入力セッションの監視。通常イベントはsuperへ渡す。
- `SequentialShortcutPreference`: A〜Z / 0〜9へのアプリ割り当て。ユーザープロファイルを含む既存Pojo IDを保存。
- 設定リソースは英語・日本語。それ以外の言語は英語へフォールバックする。
- ウィジェット配置・リサイズ・ドラッグ・スワイプの実装、既存のCtrl長押しブランチは変更しない。

## 自動検証

Windows / JDK 17 / Android SDK 36、リポジトリのGradle wrapperで実行:

```text
gradlew.bat testDebugUnitTest assembleDebug lintDebug --offline
```

結果: 成功。ユニットテスト201件、失敗0件。
追加の状態遷移テスト12件と既存ウィジェット関連テスト22件を含む。
lint: エラー0件、警告22件、ヒント3件（依存関係や既存コード等）。
これらは実機でのキー配送、設定画面の描画、日本語変換の動作確認を代替しない。

## 実機確認チェックリスト（未実施）

- 無効時: 空欄でSpaceや文字キーが従来どおり処理される。
- Space → F/D/C: 各アプリが1回だけ起動。長押し不要。
- 開始キーだけで800ms経過: 起動・遅延入力なし。
- 300ms / 2000msに変更して境界の前後を確認。
- 2キー目長押し、連打、キー解放前のアプリ切替: 二重起動・文字漏れなし。
- 未登録の2キー目: 文字が直ちに通常入力される。
- 検索途中、変換途中、IME内部にローマ字だけがある状態: Spaceが変換等の通常操作になる。
- Backspaceで空にした直後は抑止され、明示的な検索クリア・新しい入力セッションで復帰する。
- 待機中のEsc・タッチ・ウィジェット操作・フォーカス移動・ホーム再押下・アプリ切替で解除。
- Space / Tab / CapsLock / 文字 / 数字の各開始キーについて、端末側ショートカットの有無とIME別に試す。
- 開始キーと同じ文字の割り当て（例: A → A）、数字の2キー目を確認。
- アプリ変更・解除・アンインストール・無効化・別プロファイルを確認。
- ウィジェットの自由配置、サイズ変更、ドラッグ移動、ウィジェット上スワイプを回帰確認。

ADBにはTitan_2と報告する端末が見えたが、APKインストールや端末設定の変更は行っていない。
共存用APKのapplicationIdは`fr.neamar.kiss.sequence.debug`、表示名は`KISS Sequence Debug`。
旧版`fr.neamar.kiss.widgets.debug`とは別アプリとしてインストールされる。
旧版のアンインストールは不要。設定やウィジェット配置は別管理であり、新版には自動移行されない。
固定署名鍵の運用案と今回の署名証明書情報は`sequence-debug-signing.md`を参照。
