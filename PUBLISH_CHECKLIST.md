# YOLO Flutter パッケージ公開チェックリスト

このチェックリストは Flutter パッケージを pub.dev に公開するために必要なタスクを管理するためのものです。各項目を完了したら、チェックボックスにマークを入れてください。

## 1. pubspec.yaml の更新

- [x] 詳細な説明文を追加

  - 更新済み: "Flutter plugin for YOLO (You Only Look Once) models, supporting object detection, segmentation, classification, pose estimation and oriented bounding boxes (OBB) on both Android and iOS."

- [x] ホームページ URL を追加（GitHub リポジトリなど）

  - 更新済み: https://github.com/ultralytics/yolo-flutter

- [x] リポジトリ URL の追加

  - 更新済み: https://github.com/ultralytics/yolo-flutter

- [x] Issue Tracker URL の追加

  - 更新済み: https://github.com/ultralytics/yolo-flutter/issues

- [x] ドキュメント URL の追加

  - 更新済み: https://github.com/ultralytics/yolo-flutter/blob/main/README.md

- [x] 依存関係のバージョン範囲を適切に指定
  - 現在の Flutter SDK: ^3.5.3
  - 現在の plugin_platform_interface: ^2.0.2
  - バージョン指定は適切です

## 2. LICENSE ファイルの更新

- [x] 適切なライセンスを選択

  - 選択済み: AGPL-3.0 (GNU Affero General Public License v3.0)

- [x] LICENSE ファイルを更新
  - 更新済み: LICENSE.txt に AGPL-3.0 ライセンステキスト全文を追加

## 3. README.md の充実

- [x] パッケージの概要・特徴を追加

  - YOLO モデルの説明
  - サポートしている機能（検出、セグメンテーション、分類、ポーズ推定など）

- [x] インストール方法

  ```yaml
  dependencies:
    ultralytics_yolo_android: ^0.0.4
  ```

- [x] 必要なセットアップ（Android/iOS 固有の設定）

  - Android: 必要なパーミッション、minSdkVersion など
  - iOS: 必要な Info.plist エントリ、Podfile 設定など

- [x] 詳細な使用方法と例

  - 画像での物体検出の例
  - カメラフィードでのリアルタイム検出の例
  - 各種 YOLO タスク（検出、セグメンテーション、分類、ポーズ推定）のサンプルコード

- [x] API ドキュメントの概要

  - 主要クラスとメソッドの説明

- [x] プラットフォーム対応状況

  - Android: ✓
  - iOS: ✓
  - Web: ✗
  - macOS/Windows/Linux: ✗

- [x] トラブルシューティング

  - 一般的な問題と解決策

- [ ] スクリーンショットや GIF デモ

## 4. CHANGELOG.md の更新

- [x] 初期リリースの内容を具体的に記述

  ```markdown
  ## 0.0.1

  - Initial release
  - Object detection with YOLOv8 models
  - Segmentation support
  - Image classification support
  - Pose estimation support
  - Oriented Bounding Box (OBB) detection support
  - Android/iOS platform support
  - Real-time detection with camera feed
  - Customizable confidence threshold
  - YoloView Flutter widget implementation
  ```

## 5. API ドキュメント

- [x] dartdoc コメントをコードに追加

  - YOLO クラス
  - YoloView ウィジェット
  - YOLOTask enum
  - YoloPlatform および関連クラス

- [x] コマンドでドキュメント生成
  ```bash
  dart doc . --output=doc/api
  ```

## 6. サンプルアプリケーション

- [ ] example/ ディレクトリのサンプルコードを充実

  - カメラによるリアルタイム検出
  - 画像ファイルからの検出
  - セグメンテーション、分類、ポーズ推定の例

- [x] example/README.md の整備
  - サンプルアプリの実行方法
  - 実装戦略と手順
  - コード構造の説明
  - 機能ロードマップ

## 7. テストと品質確保

- [x] 単体テストの追加

  - YOLO クラスのテスト
  - YoloView ウィジェットのテスト
  - YOLOTask 列挙型のテスト
  - プラットフォーム互換性テスト

- [x] エラーハンドリングの実装

  - カスタム例外クラスの作成（YoloException, ModelLoadingException など）
  - モデル読み込み失敗時の処理
  - 推論エラーの処理
  - 無効な入力の検証
  - プラットフォーム固有の例外処理

- [x] コードの lint 確認
  ```bash
  flutter analyze
  ```
  - 主要なライブラリコードの lint 問題を修正
  - example プロジェクトの問題は後ほど実装時に修正

## 8. パッケージ公開準備

- [x] pub.dev での一意なパッケージ名の確認

  - 元の名前: yolo
  - 新しい名前: ultralytics_yolo_android（変更済み）

- [ ] ドライランで問題がないか確認

  ```bash
  flutter pub publish --dry-run
  ```

- [ ] pub 点数を確認し、改善（80 点以上が望ましい）

## 9. 公開手順

- [ ] pub.dev アカウントの設定

  - Google アカウントでログイン

- [ ] パッケージの公開

  ```bash
  flutter pub publish
  ```

- [ ] 公開後の確認
  - pub.dev でのパッケージページを確認
  - インストール手順が正しいか確認
  - サンプルが動作するか確認

## 追加的な考慮事項

- [ ] バージョン管理戦略の決定

  - セマンティックバージョニング（MAJOR.MINOR.PATCH）
  - 次のリリースの計画

- [ ] コミュニティ貢献ガイドライン

  - CONTRIBUTING.md ファイルの作成

- [ ] CI/CD パイプラインの設定
  - GitHub Actions や Travis CI など

## 10. リリース前の最終チェック項目

- [ ] パッケージサイズの最適化

  - build ディレクトリの除外確認
  - 不要なアセットファイルの削除
  - 大きなサイズのテスト用モデルファイルの削除/縮小

- [ ] example ディレクトリの修正

  - インポートパスの修正（package:ultralytics_yolo_android/...）
  - YoloView コンポーネントのパラメータ確認
  - YOLO クラスの初期化パラメータの確認
  - 必要なアセットの正しい配置
  - image_picker 依存関係の追加
  - エラー処理の改善
  - UI コンポーネントの最適化

- [ ] テストの修正

  - 失敗しているテストの修正
  - platform 特有のモック対応
  - MissingPluginException エラーの解決

- [ ] ライセンス関連の修正

  - LICENSE.txt を LICENSE に名前変更（完了）
  - 依存ライブラリのライセンス互換性確認

- [ ] バージョン更新
  - pubspec.yaml のバージョン番号を 0.0.4 に更新（完了）
  - CHANGELOG.md のバージョン番号を 0.0.4 に更新（完了）
  - インストール手順のバージョン番号更新

---

## 進行状況トラッキング

**完了タスク**: 24 / 43

**現在のフォーカス**:

- example ディレクトリのコード修正
- README にスクリーンショットや GIF デモを追加
- テスト修正
- パッケージサイズの最適化

**次のステップ**:

- example/main.dart の修正
- パッケージサイズ削減対策
- スクリーンショットや GIF デモの準備
- テストの修正
