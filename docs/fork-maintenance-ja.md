# utmity/KISS 統合版の保守・安全な更新

## 統合範囲と履歴

`integrate/t2e-custom-features`はフォークmasterから分岐し、実機確認済みの
`feat/t2e-sequence-debug-coexist` (`49df00a4`)を履歴ごと一度だけマージする。
その祖先にPR #2の自由配置、#3のスワイプ、#4の統合処理が含まれるため、個々のPRを再適用しない。
masterにある手動ウィジェットプレビューワークフローの追加も確認した上で、古い機能ブランチと
毎回異なる署名のAPKを配布する経路は撤去する。PR #1のCtrl長押しは含めない。

正式統合PRをマージ後、#2〜#4は統合済みとして閉じる。既存ブランチは削除しない。
履歴の強制書換えも行わない。上流の変更は今後も別ブランチでmergeし、ここに記した回帰検証を
通してからフォークmasterへPRで取り込む。機能を独立クラス・リソース・テストに分け、
MainActivity等のフックを小さく保つ。

## 設定・配置互換性

DebugのIDは`fr.neamar.kiss.sequence.debug`、表示名は`KISS Sequence Debug`を維持する。
通常版`fr.neamar.kiss`と旧検証版`fr.neamar.kiss.widgets.debug`には変更を加えない。
versionCodeは224→225→226へ増加。ReleaseのIDと署名設定は変更しない。

`widgets-conf`、`widget-grid-v1-<widgetId>`、既存AppWidgetHost ID、
`widget-swipe-mode`、`sequential-shortcuts-*`のキーと保存形式は維持する。
消去・リセット・データ移行処理を追加しない。
ウィジェット自由配置、移動、リサイズ、復元、衝突処理、2本指スクロール、通常の子View操作、
ショートカットの状態遷移・IME保護の実装は共存版を継承する。

スワイプの未設定時の初期値だけ`edge`から`auto`（スクロール領域以外の全体）にする。
XML defaultValueとWidgetViewの取得時のfallbackを同時に変更し、既に保存された
`edge`・`auto`・`off`は書き換えない。ショートカットは引き続き初期無効。

## 署名鍵をPC内に保持する運用

所有者の選択により**GitHubへ署名鍵・パスワードを登録しない**。
Actions Secretsへの登録手順は不要。CIのAPKは検証専用で、ダウンロード成果物には含めない。
これにより、一時CI環境の自動生成Debug鍵を日常利用版へ誤配布しない。

既存共存版を署名したPCの`%USERPROFILE%\.android\debug.keystore`を維持する。
`ANDROID_USER_HOME`を明示している環境はその配下を使う。
鍵はリポジトリ外で暗号化バックアップし、復元方法を確保する。鍵ファイルとパスワードは
Git、GitHub Secrets、APK成果物、チャットへ貼らない。

既存共存版APKの証明書SHA-256（公開情報）:

```text
e5d4d9166737742cc479c1e326d2200e111ca95c457b22f81eeef0e8425918fc
```

`.github/sequence-certificate.sha256`にも固定している。鍵を新規生成して同じIDを使っても
通常の上書き更新はできない。鍵を失った場合は元の鍵のバックアップを復元する。
復元できなければ既存アプリを残し、別IDで評価しながら設定の書き出し等を検討する。
別IDにウィジェットのバインドIDを単純コピーしない。アンインストールを解決策にしない。
[Android公式の署名仕様](https://developer.android.com/studio/publish/app-signing)

## 更新用APKの作成（Windows / PowerShell）

日常利用している共存版の元APKを保管し、同じPCで次を実行する。

```powershell
git switch feat/widget-scale-and-safe-updates
git pull --ff-only
.\scripts\build-sequence-update.ps1 -ReferenceApk 'D:\APK保管\KISS-sequence-debug-coexist.apk'
```

SDKが標準位置でなければ`-SdkPath`、成果物の保存先は`-OutputDirectory`で指定できる。
SDK・JDK・依存関係の導入は必要。既存署名鍵が無い場合はビルド前に停止する。
スクリプトは元APKのIDと署名、ビルド・ユニットテスト・lint、新APKのIDと署名・versionCodeを
確認した場合だけ、`app/build/sequence-update/`にAPKとメタデータを出力する。
元APKと新APKの証明書が固定指紋に一致することが必須。単に「同じID」だけでは許可しない。
署名鍵の内容は読み出して表示せず、ビルド中に鍵ファイルが変化していないことも検査する。
ビルド対象のcommit SHAと作業ツリー変更の有無をメタデータに記録する。

スクリプトは端末を操作しない。更新時はこの検証済みAPKを通常の上書きインストールで使う。
アプリ削除・`pm clear`・自動バックアップ復元は不要。
将来の変更でも配布更新のversionCodeを必ず増加させる。同値もスクリプトで拒否する。
`-ReferenceApk`には最後にインストールしたAPKを指定する（古いAPKを指定すると、
それより新しい端末内の版との比較にはならない）。スクリプトは端末を読み書きしない。
既存ファイルの署名・ID・バージョンだけ再検査する場合:

```powershell
.\scripts\build-sequence-update.ps1 -ReferenceApk 'D:\APK保管\前回.apk' -VerifyApk 'D:\APK保管\今回.apk'
```

ビルド前にGradleのDebug署名設定の公開メタデータを取得し、既存鍵のパスと固定証明書を照合する。
Titan IMEなど別プロジェクトの鍵を指定した場合も一致しなければ停止する。
配布APKの検証は別途apksignerとaaptで行う。署名設定の全文はログへ出さない。

## GitHub Actions

`App testing` (`.github/workflows/ci.yml`)をpush・master向けPR・手動実行で起動する。
Checkoutはイベントが指すソースを使用し、特定の古い機能ブランチには固定しない。
PRはGitHubの試験マージcommit、pushはそのcommitを検証する。
手動実行はActionsの「Use workflow from」でブランチを選ぶ。
成果物名とrunのcommit情報で検証対象を特定できる。

- JVMユニットテスト、lint、Debug APK、Release APKのビルド。
- API 29 / 36の使い捨てエミュレーターでWidgetGrid / WidgetSwipe / RealWidget / SequentialInput / WidgetScroll / WidgetSizingのUIテスト。
- テスト結果・lintレポートのみ14日保存。日常利用の更新用APKは上記ローカル手順から取得する。
- 実機のデータを書き換え得るUIテストは、普段使う端末に対して実行しない。
- RealWidgetTestはシステムのAnalogAppWidgetProviderがない環境ではskipする。

## 実機確認との区別

所有者は以前の統合版でウィジェット機能一式、共存版で順番押しの実動作を確認済み。
自動テストでは保存形式・デフォルト値の互換性、通常入力とIME保護、タッチの配分などを検証する。
合成KeyEvent/MotionEventはTITAN 2 ELITEの物理キー配送やすべてのIME・Providerを再現しない。
正式統合版を上書きした後の配置保持、端末再起動後の復元、日本語変換中の入力、各Providerの
固有操作は実機で最終確認する。自動で端末へインストールはしない。

以前の各機能ドキュメントのAPK配布・署名運用より、この文書の手順を優先する。

## 鍵の現状と管理方式の比較（2026-11-11）

現在のPCでは`C:\Users\kaben\.android\debug.keystore`が存在し、Gradle Debug設定の公開証明書が
上記固定指紋と一致する。鍵の生成・移動・パスワード変更・GitHub登録はしていない。
`.android`のファイル名情報と`Documents\Codex`内の一般的な鍵／暗号化鍵の拡張子を調査した範囲では、
別のバックアップは確認できていない。別ディスク、暗号化コンテナ、クラウド、オフライン媒体の
バックアップ有無は未確認。「バックアップ済み」とは判断しない。

| 方式 | 利点 | 注意点 |
| --- | --- | --- |
| 現在のPC内の鍵・ローカル署名 | 鍵をCIへ送らず、既存APKを継続更新できる | PC故障・削除に備えた復元可能なバックアップが必要。PCのアクセス権／ディスク保護に依存 |
| GitHub Actions Environment Secrets | 手動承認付きCI署名を自動化できる | 実行コードから鍵を読み出せるため、workflow・依存Action・承認者の保護が必要。ログの自動マスクは完全ではない |
| 暗号化した同じ鍵を別媒体へ保管 | 元鍵を変更せずPC故障に備えられる | 復号情報を別管理し、復元テストが必要。暗号化ファイルと復号情報の同時漏洩に注意 |

提案は「現在のローカル署名を維持し、同じ鍵の暗号化バックアップを別媒体に作り、隔離した
復元先で証明書一致を確認する」。**保存先・暗号化方式・復号情報の管理先を所有者と確認してから
実施する。今回は実施しない。** Debug鍵の標準的な保護だけを強い秘密保管と見なさない。
APKや公開証明書から秘密鍵は復元できない。鍵紛失時も既存アプリは消さず、まず元鍵を探す。

根拠: [Android署名](https://developer.android.com/studio/publish/app-signing)、
[GitHub Actionsの安全な利用](https://docs.github.com/en/actions/reference/security/secure-use)。
