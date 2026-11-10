# KISS T2E Debug：物理キーボードの長押し

## 採用した仕様

ユーザー確認により、IMEを変更しない代替操作として **Ctrl＋文字キーの長押し** を採用しました。文字キー単独の長押しを実装したものではありません。

- Fなどの通常入力は既存のIME経路へそのまま渡します。短押し判定による遅延・キーの再送・検索文字列への直接挿入は行いません。
- KISSの検索欄にフォーカスがあるとき、登録済みのCtrl＋A〜Zを指定時間押すとアプリを起動します。
- 初期時間700ms、設定範囲200〜2000ms。初期状態では機能は無効です。
- 登録済みCtrlの組み合わせは予約され、短押しでは何もしません。例えばCtrl＋Aを登録すると検索欄の「全選択」と競合します。必要なキーだけ登録してください。
- 未登録の組み合わせ、通常の文字入力、Shift・Alt・Meta・Fnとの併用は従来の経路へ渡します。
- Ctrlを離す、別キーを押す、画面に触れる、フォーカスを失う、ホームを再表示する、Activityを離れると待機中の起動をキャンセルします。
- 起動は1回のみ。キャンセル後も捕捉済み文字キーのリピートと解放を消費します。画面復帰後は新しい押下で再開できます。
- アプリ選択・起動には既存KISSのアプリIDと起動処理を利用します。利用不能な割り当てには通知を表示します。

## なぜF単独ではないのか

Android 16の入力配送は、おおむね `Viewのpre-IME → IME → Activity/Viewの通常キー処理` の順です。IMEが消費したキーはActivityの`onKeyDown()`には届きません。

pre-IMEは同期的な処理です。キーを消費して700ms保留した後、任意のIMEへ再投入する公開APIはありません。`View.dispatchKeyEvent()`や`InputConnection.sendKeyEvent()`はIMEへの入力再投入ではありません。最初の文字をIMEへ渡してから削除する方式も、IME内部の変換状態を安全に巻き戻せません。そのため、本改修では通常文字を保留しません。

本来のF単独の短押し／長押しには、選択中IME自身がキーを保留し、短押しはそのIMEの通常入力処理へ戻し、長押しはKISSのアクションへ渡す協調実装が必要です。ホームの有効な入力セッションに限定した設定通知と起動要求、フォーカス喪失時の破棄も必要になります。既存の`utmity/titan-ime`には変更を加えていません。

参照：
- [Android View.dispatchKeyEventPreIme](https://developer.android.com/reference/android/view/View#dispatchKeyEventPreIme(android.view.KeyEvent))
- [Android 16 ViewRootImplの入力ステージ](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/ViewRootImpl.java)
- [Android 16 InputMethodManager（dispatchInputEventは非公開）](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/inputmethod/InputMethodManager.java)

## インストールと設定

1. 端末標準のA〜Zショートカットを解除します。標準側が横取りしたイベントはKISSに届かず、本改修では回避できません。
2. Debug APKをインストールします。パッケージは`fr.neamar.kiss.t2e.debug`です。通常版`fr.neamar.kiss`および既存Debug版`fr.neamar.kiss.debug`と共存します。データは別々です。
3. `KISS T2E Debug`を開き、KISS設定 →「物理キーボードのショートカット」を開きます。
4. 機能を有効にし、Ctrl＋Fなどにアプリを割り当てます。解除は「未割り当て」です。アプリ一覧がまだ空ならホームの読み込み後に設定を開き直します。
5. 長押し時間を指定します。初期値は700msです。
6. ホームとして使う場合はAndroidの「デフォルトのアプリ → ホームアプリ」でこのDebug版を選びます。自動で既定ホームを切り替える処理はありません。

実際にAndroidへCtrlとして配送されるキーが必要です。端末のキーマッピングでCtrlを送れない場合は、この方式にはCtrlキーのある外付けキーボードなどが必要です。Fnキーが自動的にCtrlとして扱われるわけではありません。

### Ctrl＋Fをどう押すか

[メーカー公式のTitan 2向け案内](https://www.unihertz.com/blogs/tutorial/how-to-customize-programmable-buttons-and-keyboard-keys-on-titan-2)では、端末の「設定 → ショートカットキー」でFnをCtrlキーとして割り当てる方法が紹介されています。端末・ファームウェアによって項目名や提供される選択肢は異なる可能性があります。接続端末でこの割り当て操作自体はまだ確認していません。

1. 端末側でFnをCtrlに割り当てます。Fnに元々割り当てられていた機能は置き換わります。Fnの短押し／長押しにアプリを登録する操作とは別です。
2. F自体の端末標準アプリ起動登録は解除します。
3. KISS内の設定で機能を有効にし、「Ctrl + F」にアプリを割り当てます。
4. KISSの検索欄で **Fnを押したままFを700ms以上保持**します。

外部の「ショートカット作成アプリ」は不要です。FnからCtrlへの割り当て項目がない場合や、この操作で起動しない場合は、実際に配送されるキーコード・修飾状態を追加検証する必要があります。

ADBを使う場合（複数接続時は対象端末を`-s`で指定）：

```powershell
adb -s <serial> install -r KISS-T2E-debug.apk
```

## 物理キー・IMEの手動確認

日本語モードと英語モードのそれぞれで以下を実施し、IME名・バージョン・端末機種・結果を記録します。テスト用アプリは「設定」など副作用のないものを推奨します。

| 操作 | 期待結果 |
|---|---|
| F短押し | IME本来の入力として処理され、検索できる |
| `f`に続けて文字を連打 | 順序が維持され、欠落・二重入力がない |
| 日本語で「ふ」「ふじ」などを入力して変換・確定 | 変換が破綻しない |
| Shift＋F | IME本来の大文字等の処理になり、起動しない |
| Ctrl＋Fを700ms未満で解放 | 起動せず、余計な文字がない |
| Ctrl＋Fを700ms以上保持 | 割り当てアプリを1回起動し、余計な文字がない |
| 保持中にCtrlを先に離す | 起動がキャンセルされる |
| 保持中に別キー／Shiftを押す | 起動がキャンセルされる |
| 保持中にタップ・設定画面移動・ホーム再表示 | 画面切替後に遅れて起動しない |
| 起動先でキーを離してKISSへ戻る | 別の登録キーも再び使用できる |
| 未登録キー／未登録Ctrlの組み合わせ | 既存動作が維持される |
| 時間を200ms、2000msに変更 | 指定した保持時間で動作する |
| 割り当て解除・機能無効化 | 捕捉されず、既存の入力経路へ戻る |

ソフトキーボード表示／非表示の両方でも確認してください。IMEによって、日本語モードでFだけを押した時点の表示が英字`f`になるとは限りません。

## 通常版へ戻す

1. Androidの「デフォルトのアプリ → ホームアプリ」で通常版KISSを選択します。
2. 必要なら`KISS T2E Debug`をアンインストールします。通常版の設定・履歴は別パッケージのため残ります。
3. 端末標準ショートカットは必要に応じて手動で再設定してください。本改修は標準登録を変更しません。
4. テストAPKも削除する場合は`fr.neamar.kiss.t2e.debug.test`のみを削除します。

```powershell
adb -s <serial> uninstall fr.neamar.kiss.t2e.debug.test
adb -s <serial> uninstall fr.neamar.kiss.t2e.debug
```

## ビルドと自動テスト

Android SDK 36と上流Gradle wrapperを使用します。上流CIはJDK 21、ローカル検証はAndroid Studio付属JDK 25で実行しました。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w -r -e class fr.neamar.kiss.androidTest.KeyboardShortcutsTest fr.neamar.kiss.t2e.debug.test/androidx.test.runner.AndroidJUnitRunner
```

端末テストはロック解除した状態で、テスト中は画面やキーを操作せず実行してください。テストは専用Debug版の一部設定を一時変更し、終了時に復元します。連絡先アクセスを許可する必要はありません。テスト失敗時には権限ダイアログや画面ロックの有無も確認します。

端末自動テストはpre-IMEに合成イベントを直接渡す統合テストです。OS・OEM・実際の物理キーボード・IMEを通した入力検証とは区別してください。
