# ウィジェット上のスワイプ

設定 → ジェスチャー →「ウィジェット上のスワイプ」。初期値はウィジェットの上下左右の端24dpから開始する1本指スワイプです。認識距離は48／72／96dp。400ms以内（長押し時間が短い端末ではその時間より前）に距離を超え、開始から1秒未満で指を離すと実行します。斜め移動・反転・多点タッチ・キャンセルでは実行しません。

- 「端から」：中央のスクロール・タップはウィジェット専用。端からのスワイプはKISS操作に予約します。
- 「スクロール領域以外の全体」：ScrollView、リスト、RecyclerView、WebView、スクロール可能な子Viewを開始位置で検出し、それ以外で認識。未知の独自Viewは判別できないことがあります。
- 「無効」：従来通り。

上下左右の既存割り当てを利用します。初期値は下が通知、左右がアプリ一覧、上がキーボード。「何もしない」の方向はウィジェットから奪いません。

## イベント配送

`MainActivity.dispatchTouchEvent()`はキーボード・ポップアップ処理であり、そこにグローバルな横取りは追加しません。`WidgetView.dispatchTouchEvent()`で自分のストリームだけを観測し、閾値到達時に子へ一度だけCANCELを配送。UPで一度だけ `ForwarderManager → ExperienceTweaks.onWidgetSwipe → doAction` を呼びます。背景用GestureDetectorへイベントを再生しないため、タップ／ダブルタップ／長押しを誤生成しません。

`WidgetScrollView`の既存2本指スクロールを維持します。長押し編集が始まるとcancelLongPressでスワイプ候補を破棄し、編集側が優先します。機能1のコードには依存せず、独立してmasterへ適用できます。

## 検証（2026-11-10 JST）

- assembleDebug、JVM184件（既存175＋追加9）、lintDebug成功。
- Android 16、ADBモデル名Titan 2でUIテスト8件成功。端スワイプの一度だけの実行、子へのCANCEL、通常タップ、中央操作保護、スクロール領域保護、無効／何もしない、長押し優先、キャンセル・2本指切替を確認。
- 合成MotionEventによる検証。全Provider固有のボタン・スクロールやTITAN 2 ELITEでの人の指による操作感は未検証。
- 端から始めるウィジェット固有のスクロールとKISSスワイプは両立できないため、必要ならモードを変更してください。OSが予約する画面端ジェスチャーはOSが優先する場合があります。

## APK

通常版と共存する `fr.neamar.kiss.widgets.debug`。機能1・機能2・両機能統合版はこの同じIDで更新できるため、同時に3つはインストールしません。別パッケージの通常版、Ctrl版、入力実験版を変更しません。

`gradlew assembleDebug testDebugUnitTest lintDebug`で再ビルド。UIテストは `WidgetSwipeTest`。APKは `app/build/outputs/apk/debug/app-debug.apk`。ブランチpushのActionsにもdebug artifactを生成する既存ワークフローがあります（CI成功時）。
