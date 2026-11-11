# ウィジェットの配置サイズと表示倍率

ウィジェットを長押し → 右上の「⋮」→「ウィジェットのサイズと表示」。
「サイズ制限を無視」をONにしてOKを押すと、ProviderのresizeMode・min/maxResizeWidth/Height
による制限を無視し、右下のハンドルで1〜4列、1〜200行の範囲へ変更できる。
4列グリッド、境界、衝突時の巻き戻し、2本指スクロールは維持する。
初期値はOFF。OFFへ戻すだけでは既存配置を強制移動／拡大せず、その後のリサイズに制限を適用する。

表示倍率は別の設定で50〜200%（5%刻み、初期100%）。RemoteViewsのルートViewを中央基準で拡縮する。
固定サイズProviderのテキスト・ボタン・画像も拡縮する。配置領域の変更だけでは文字等の拡大は保証されない。
100%未満は余白が増え、100%超は配置領域外の内容が切れる場合がある。必要に応じて配置領域も広げる。
「倍率を100%に戻す」は倍率のみを戻す。「キャンセル」は変更を保存しない。

Android標準のView変換を使いタップ座標を逆変換する。スクロール領域のヒット判定も同じ変換を使う。
ホストのサイズ、グリッド編集枠、ジェスチャー距離は拡縮しない。Provider更新でViewが入れ替わった場合も
次のlayoutで倍率を適用する。ビットマップを拡大する場合は鮮明度が下がり得る。

サイズ通知は初回layout・配置復元・リサイズ・画面回転後の実測ホスト領域をdpへ変換して送る。
API 31以上では`updateAppWidgetSize(Bundle, List<SizeF>)`、それ以前ではmin/maxの整数版を使う。
現在の画面のサイズを通知し、回転したら改めて通知する。default paddingはAndroid側が差し引くので
二重減算しない。倍率だけを変えてもProviderへ伝える領域は変えない。寸法が同じlayoutの重複通知は省く。
[Android AppWidgetHostView](https://developer.android.com/reference/android/appwidget/AppWidgetHostView#updateAppWidgetSize(android.os.Bundle,%20java.util.List%3Candroid.util.SizeF%3E))

保存形式`widgets-conf`と`widget-grid-v1-<id>`は変更しない。追加キーは
`widget-ignore-limits-<id>`と`widget-scale-percent-<id>`で、当該ウィジェット削除時に片付ける。
既存ウィジェットはOFF／100%のまま。ショートカットとIME処理には変更を加えない。

## 検証範囲と実機確認

WidgetSizingTestは固定200×100pxのRemoteViews相当ルートを使い、描画ピクセル、変換後座標のタップ、
クリッピング、スクロール領域保護、通常／強制リサイズ、衝突、設定保存とActivity再作成を検証する。
RealWidgetTestは実際のシステム時計Providerへのサイズオプション送信と画面回転後の復元を検証する。
時計Providerが存在しない環境ではそのテストのみskipされる。

Amazon MusicのRemoteViews固有レイアウト、ボタン、スクロール、アートワークは実機確認が必要。
サイズ通知を無視するProviderをKISSから再実装することはできない。強制縮小で既にProvider内部に
切り取られた内容を倍率変更だけで復元することもできない。その場合は配置を広げてから倍率を調整する。
初めに「サイズ制限を無視」ONで1列化、次に倍率75%／125%等を試し、再生・曲送りボタンが表示位置で
反応すること、ホーム復帰／回転後の保持、他ウィジェットのタップ・スワイプ・2本指スクロールを確認する。
実機へのインストールとmasterへのマージは所有者の明示許可後に行う。
