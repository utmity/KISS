# 共存用Debug APKと今後の署名運用

## 今回の変更と検証

- ベース: `feat/t2e-sequential-keyboard-shortcuts` / `10184a0b`。
- 新規ブランチ: `feat/t2e-sequence-debug-coexist`。
- Debug applicationId: `fr.neamar.kiss.sequence.debug`。
- Debug表示名: `KISS Sequence Debug`（既存のDebug用4言語リソースを変更）。
- versionCode: 224、versionName: 3.26.0。
- Release版のID、namespace、署名設定、ウィジェット機能、2キー順番押しのコードは変更なし。
- ビルド済みAPKのIDと表示名をaaptで確認、apksignerで署名を検証。
- マージ後ManifestのKustom Provider、AndroidX Startup Provider、動的Receiver用permissionが新IDに対応することを確認。
- `gradlew.bat testDebugUnitTest assembleDebug lintDebug --offline`: 成功。
- 全201件のテスト成功（ショートカット12件、ウィジェット関連22件を含む）。lintはエラー0件、警告22件、ヒント3件。
- 端末へのインストール・アンインストール、データ消去、既定ホームの変更は行っていない。

旧ウィジェット版は別パッケージなので、そのまま残せる。
新版は独立した設定・データ領域で動作し、ウィジェット配置も独立する。
新しい版で使うウィジェットは追加・配置し直す必要がある。旧版の配置は旧版に残る。

## 今回の署名

署名設定を変更せず、このPCの既存のAndroid Debug鍵を使用した。
今回、専用の固定署名鍵を新規作成したわけではない。

公開証明書のSHA-256（秘密情報ではない）:

```text
e5d4d9166737742cc479c1e326d2200e111ca95c457b22f81eeef0e8425918fc
```

このAPKをインストールした後、通常の上書き更新を続けるには、同じapplicationIdと
この証明書に対応する同じ署名鍵を使用する。更新ごとにversionCodeも増やす運用にする。
別PCや一時CI環境で自動生成されたDebug鍵は同じ鍵とは限らない。
[Android公式の署名ガイド](https://developer.android.com/studio/publish/app-signing)

## 固定署名鍵を使う運用案

1. **今回のAPKを継続利用する場合**:
   このPCの`%USERPROFILE%\.android\debug.keystore`を再生成・削除せず、暗号化した
   バックアップをリポジトリ外に保管する。上の証明書指紋と一致する鍵を使い続ける。
   標準Debug鍵は開発用なので、一般配布用の署名鍵としては扱わない。
2. **専用鍵で運用を始める場合**:
   新しいアプリを最初にインストールする前に専用の長期署名鍵でAPKを作成し、以後固定する。
   今回のAPKを先にインストールすると、後から別の専用鍵に単純に切り替えたAPKでは
   再び署名不一致になる。切替には別途計画が必要であり、アンインストールで解決しない。
3. 鍵ファイルはリポジトリ外のアクセス制限した保管場所に置く。ローカルでは環境変数や
   リポジトリ外のGradleプロパティでパス・alias・パスワードを注入する。
   CIでは暗号化したSecret保管を使い、ビルド中だけ一時復元する。ログへ値を出さず、
   APKと公開証明書指紋だけを成果物にする。将来の専用Debug signingConfigはこのブランチで
   明示的に設定し、設定不足ならビルドを失敗させ、自動Debug鍵へフォールバックさせない。
4. `.jks`、`.keystore`、`.p12`、秘密のプロパティファイルをコミットしない。
   `.gitignore`だけに頼らず、コミット対象を確認する。今回のコミットには鍵・パスワードは含まれない。
5. 配布前に毎回`apksigner verify --print-certs APKファイル`で証明書指紋を照合する。
   `aapt dump badging APKファイル`でapplicationIdとversionCodeも確認する。

今回の変更は共存用パッケージ化までに限定し、上記の専用署名設定やSecret登録はまだ行っていない。
