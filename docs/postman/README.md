# NeriPlayer Postman API Pack

本目录由仓库源码整理生成，方便直接导入 Postman 调试。

## 文件

- `NeriPlayer.postman_collection.json`：Postman Collection，按模块分类的可运行请求。
- `NeriPlayer.postman_environment.json`：Postman 环境变量模板。
- `neriplayer-api.openapi.yaml`：OpenAPI 3.1 文档，包含数据模型和组件 schemas。

当前包包含 81 个 Postman 请求、72 个环境变量、59 个 OpenAPI paths 和 47 个 schemas。
Collection 内所有请求的 `raw` URL 都已改为显式完整地址（如 `https://api.bilibili.com/...`），并同时保留 Postman 原生 URL object（`protocol/host/path/query`）字段，避免导入后 Host/URL 为空。

## 导入顺序

1. 在 Postman 导入 `NeriPlayer.postman_collection.json`。
2. 导入 `NeriPlayer.postman_environment.json` 并选中该环境。
3. 按需填充 Cookie、Token、房间 ID、歌曲 ID、仓库名等变量。
4. 需要接口文档/模型视图时，再导入 `neriplayer-api.openapi.yaml`。

## 调试提示

- 网易云 WEAPI/EAPI 请求：Collection 的 NetEase 文件夹会自动生成 `params` / `encSecKey`。
- Bilibili WBI 请求：先运行 `Bilibili / Utilities / Refresh WBI mixin key`。
- YouTube Music：先运行 `YouTube Music / Bootstrap / Fetch Music Home`，脚本会保存 `yt_api_key`、`yt_client_version`、`yt_visitor_data`。
- `YouTube Playback / Probe Utilities` 覆盖播放器回退、探测脚本和已解析 `googlevideo.com/videoplayback` 直链 Range 验证。
- `Auth / Web Login Utilities` 是仓库中的 WebView/浏览器授权入口，用于拿 Cookie 或创建 GitHub token，不属于 JSON 数据 API。
- Listen Together：运行 `Create Room` 后会自动保存 `lt_room_id`、`lt_token`、`lt_ws_url`。
- GitHub/WebDAV 同步接口会读写真实远端数据，执行 PUT 前请确认环境变量指向测试仓库或测试路径。

## 覆盖来源

- `app/src/main/java/moe/ouom/neriplayer/core/api/`
- `app/src/main/java/moe/ouom/neriplayer/activity/`
- `app/src/main/java/moe/ouom/neriplayer/data/auth/youtube/`
- `app/src/main/java/moe/ouom/neriplayer/listentogether/`
- `app/src/main/java/moe/ouom/neriplayer/data/sync/github/`
- `app/src/main/java/moe/ouom/neriplayer/data/sync/webdav/`
- `np-submodule/NeriPlayer-LTW/src/worker.js`
- `tools_pub/ytmusic_api_probe.py`

## 复查说明

- 已把同一 endpoint 的关键调用变体也列入 Collection，例如 Bilibili 的 BVID/AID 两套参数形态。
- `https://room.internal/*` 是 Cloudflare Durable Object 内部路由，不可由 Postman 直接访问，未作为公网请求单独导入。
- UI 分享链接、封面/媒体直链、HLS 分片 URL 属于运行时动态资源；可用对应的解析接口或 Range Probe 请求调试。
