#!/bin/sh -x

# スケルトンコード種別: iOSプラグイン
LANG="deviceConnectIosPlugin"

# プロファイル定義ファイルのディレクトリ
SPEC_DIR="./sample-profile-specs"

# スケルトンコード出力先
OUTPUT_DIR="./output/iOS/MyPlugin"

# iOSプラグインの表示名
DISPLAY_NAME="MyPlugin"

# スケルトンコード生成ツールのバイナリ
JAR_FILE="../bin/deviceconnect-codegen.jar"

ARGS="--input-spec-dir $SPEC_DIR  --lang $LANG --display-name $DISPLAY_NAME --output $OUTPUT_DIR"

java -Dfile.encoding=UTF-8 -jar $JAR_FILE $ARGS
