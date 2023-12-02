#!/bin/bash
mkdir -p ./frontend/src/proto
protoc --proto_path=. app/src/main/proto/raft.proto \
  --plugin=protoc-gen-grpc-web=./node_modules/.bin/protoc-gen-grpc-web \
  --js_out=import_style=commonjs:./frontend/src/proto \
  --grpc-web_out=import_style=commonjs,mode=grpcwebtext:./frontend/src/proto \
  --proto_path=app/src/main/proto
