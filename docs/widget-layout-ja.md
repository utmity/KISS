# ウィジェット自由配置

通常版と共存する `fr.neamar.kiss.widgets.debug`（KISS Widgets Debug）。Ctrlショートカット版・入力実験版とは別アプリです。設定でミニマリストモードを有効にし、KISSメニューからウィジェットを追加してください。

- 長押しで編集開始。そのままドラッグ、または指を離してから枠内をドラッグで移動。
- 右下の48dpハンドルをドラッグして横幅・高さを変更。Providerが許可した方向だけ変更可能。
- 右上の48dpメニューから削除・Provider設定。枠外タップ／戻るで編集終了。
- 横4列、縦48dp単位で吸着。衝突する配置は赤枠になり、離すと元に戻る。
- ウィジェット領域全体は従来通り2本指スクロール。編集時は画面上下付近でドラッグするとスクロール。
- 配置はwidget IDごとのSharedPreferencesに保存。既存のwidgets-confとIDを保持し、初回は従来の縦並びから移行。高さは48dp単位に切り上げる。

別パッケージの通常版からAndroidのwidget IDは移せないため、このDebug版では追加し直しが必要です。同一パッケージ内のアップデートは配置を保持します。

## 検証（2026-11-10 JST）

- assembleDebug成功。既存175件＋追加5件のJVMテスト成功。
- Android 16、ADBモデル名Titan 2でUIテスト5件成功：横並び保存復元、移動・キャンセル、衝突取り消し、縦横リサイズ、通常タップ。
- インストール済みシステム時計による追加UIテスト1件成功：実WidgetViewの長押し、ドラッグ、Activity再生成後のID・位置復元。
- lintDebug成功（既存警告を含む警告あり）。実端末へのAPKインストール成功。
- 合成MotionEventによるAndroid上の検証です。人の指によるTITAN 2 ELITE固有の操作感、端末の実再起動、各社Provider、TalkBackによる編集は未検証です。

## 制約と確認項目

ピクセル単位の自由配置ではなく4列の格子です。重ね置き・自動並べ替えは行いません。編集はタッチ中心です。Providerが定義する最小サイズとリサイズ方向を尊重します。実機では時計／天気／カレンダーを横並びにし、長押し、端へのドラッグ、縦横サイズ変更、内部ボタン・スクロール、設定画面からの復帰、再起動後の保持を確認してください。

## 再ビルド

Android SDK 36と互換JDKを用意し `gradlew assembleDebug testDebugUnitTest lintDebug`。APKは `app/build/outputs/apk/debug/app-debug.apk`。forkのブランチpushによるActionsのGenerate APKにもdebug artifactがあります（CIが成功した場合）。

UIテストは `WidgetGridTest`。実時計テストは `RealWidgetTest` で、システムAnalogAppWidgetProviderとDebug版のwidget bind許可が必要です。インストール後に `adb shell appwidget grantbind --package fr.neamar.kiss.widgets.debug --user 0`、続いて `adb shell am instrument -w -e class fr.neamar.kiss.androidTest.RealWidgetTest fr.neamar.kiss.widgets.debug.test/androidx.test.runner.AndroidJUnitRunner`。連絡先Providerをテスト内で無効化し、連絡先・通話権限を必要としません。
