# Sonic → TestHub capability inventory

Source studied (read-only):
- Server: `/Users/wen/Workspace/TestPlatform/sonic/sonic-server-main`
- Web: `/Users/wen/Workspace/TestPlatform/sonic/sonic-client-web-main`
- (Supporting, for protocol only) `sonic-agent-main`, `sonic-driver-core-main`

---

## 1. Modules and tech stack

### 1.1 Server (`sonic-server-main/pom.xml`)

| Module | Role | Notable |
|---|---|---|
| `sonic-server-common` | shared lib | `RespModel`/`RespEnum`, `JWTTokenTool`, `WebAspect`+`WhiteUrl`, i18n, knife4j |
| `sonic-server-controller` | all business APIs + agent transport WS + Quartz | `ControllerApplication.java` |
| `sonic-server-folder` | file storage service (`/upload`, `/files`) | `UploadController`, `FilesController` |
| `sonic-server-gateway` | Spring Cloud Gateway (WebFlux), auth filter, routes | `AuthFilter`, `CORSConfig`, `UriWithBracketsFilter` |
| `sonic-server-eureka` | service registry | `application.yml` |

Stack: **Spring Boot 3.0.3 / Java 17**, Spring Cloud **2022.0.1**, Eureka client, OpenFeign, Undertow,
MyBatis-Plus + **Actable** (`mybatis-enhance-actable`, auto-DDL from entities), MySQL 8, Quartz **JDBC clustered**,
`spring-boot-starter-websocket` (`jakarta.websocket` `@ServerEndpoint`), springdoc/knife4j, fastjson, java-jwt, LDAP, Lombok.

Profile wiring: `application-{eureka,jdbc,feign,user,logging,sonic-server-*}.yml` in `sonic-server-common/src/main/resources/`.
Notable: controller runs on **`server.port: 0`** (random) and is reached via `lb://` (Eureka). Gateway listens on `3000`.

### 1.2 Gateway routing (`sonic-server-gateway/src/main/resources/application.yml` + `application-sonic-server-gateway.yml`)

| Inbound path | Route | StripPrefix |
|---|---|---|
| `/api/controller/**`, `/api/transport/**` | `lb://sonic-server-controller` | 2 |
| `/api/folder/**` | `lb://sonic-server-folder` | 2 |
| `/websockets/**` | `lb:ws://sonic-server-controller` | 1 (legacy; browser talks to agents directly) |
| `/server/api/controller/**`, `/server/api/transport/**` | controller | 3 |
| `/server/api/folder/**` | folder | 3 |

White list (no JWT): `filter.white-list` = `/projects/list,/websockets,/users/login,/users/register,/users/loginConfig,/swagger,/v3/api-docs,/doc.html,/folder/upload,/keepFiles/,/imageFiles/,/recordFiles/,/logFiles,/packageFiles/`.
Auth is a **header token `SonicToken`** verified in `sonic-server-gateway/.../config/AuthFilter.java`; fine-grained RBAC is a *second*
filter, `sonic-server-controller/.../config/PermissionFilter.java` (path+method → `resources` table → role).

### 1.3 Client web (`sonic-client-web-main/package.json`)

**Vue 3.2 + Element Plus 1.1 + Vite 2.5 + vue-router 4 + Vuex 4**, echarts 5, **jmuxer** (scrcpy H.264 playback),
codemirror 6 (java/python), `vue-draggable-next` (step reorder), vue-i18n (zh/en/zh-TW/ja), axios 0.21.
→ **Not React**; every page must be re-implemented. The HTTP layer is `src/http/axios.js`
(baseURL `/server/api` prod, `http://localhost:3000/server/api` dev; params serialized `arrayFormat: 'brackets'`; `SonicToken` header;
response envelope `{code,message,data}` with `2000` success, `1001` unauthenticated, `1003` permission denied).
Routes: `src/router/index.js`.

---

## 2. Data model (MySQL, tables auto-created by Actable from `models/domain/*.java`)

Base dir: `sonic-server-controller/src/main/java/org/cloud/sonic/controller/models/domain/`.
Schema is **implicit in annotations** — there is no DDL script except Quartz tables (`src/main/resources/data.sql`). Porting = writing explicit DDL.

### 2.1 Projects / modules / case authoring

| Table | Entity | Columns | Notes |
|---|---|---|---|
| `projects` | `Projects.java` | id, project_name, project_des, project_img, edit_time, robot_type, robot_token, robot_secret, testsuite_alert_robot_ids, global_robot | `int[]` robot ids stored as CSV varchar via `NullableIntArrayTypeHandler` |
| `modules` | `Modules.java` | id, name, project_id | flat, one level |
| `versions` | `Versions.java` | id, version_name, project_id, create_time | |
| `test_cases` | `TestCases.java` | id, name, des, designer, edit_time, module_id, platform, project_id, version | `platform`: 1 Android, 2 iOS, 3 Win, 4 Mac, 5 Web (`models/interfaces/PlatformType.java`) |
| `steps` | `Steps.java` | id, parent_id, case_id, step_type, content, text, sort, error, condition_type, disabled, platform, project_id | `parent_id` = nesting for if/else/while; `error` = failure strategy int; `condition_type`: 0 none,1 if,2 else_if,3 else,4 while (`models/enums/ConditionEnum.java`) |
| `elements` | `Elements.java` | id, ele_name, ele_type, ele_value, project_id, module_id | locator repo: `ele_type` = id/xpath/className/…, `ele_value` = selector |
| `steps_elements` | `StepsElements.java` | steps_id, elements_id | **no PK id**; a step can hold several elements (multi-element steps) |
| `public_steps` | `PublicSteps.java` | id, name, platform, project_id | reusable step library |
| `public_steps_steps` | `PublicStepsSteps.java` | public_steps_id, steps_id | |

### 2.2 Suites / jobs

| Table | Entity | Columns | Notes |
|---|---|---|---|
| `test_suites` | `TestSuites.java` | id, name, platform, cover, is_open_perfmon, perfmon_interval, project_id, alert_robot_ids | `cover`: 1 = case-major, 2 = device-major (`CoverType.java`) |
| `test_suites_test_cases` | `TestSuitesTestCases.java` | test_suites_id, test_cases_id, sort | ordered case list |
| `test_suites_devices` | `TestSuitesDevices.java` | test_suites_id, devices_id, sort | suite is bound to an explicit **device set** |
| `jobs` | `Jobs.java` | id, name, cron_expression, project_id, suite_id, status, type | `status`: 1 enable, 2 disable, 3 once; `type`: 1 test, 2 clean file, 3 clean result, 4 day report, 5 week report (`JobStatus/JobType.java`) |
| Quartz `QRTZ_*` | — | — | created by `data.sql`, store-type jdbc, clustered |

### 2.3 Results

| Table | Entity | Columns | Notes |
|---|---|---|---|
| `results` | `Results.java` | id, create_time, end_time, project_id, suite_id, suite_name, status, strike, send_msg_count, receive_msg_count | one row per suite run; `status` 0 running/1 pass/2 warn/3 fail; `strike` = trigger user or `SYSTEM`; send/receive counters used to detect completion |
| `result_detail` | `ResultDetail.java` | id, result_id, case_id, device_id, type, status, log, des, time | **the granular event log**; `type` = `step` \| `perform` \| `record` \| `status`; `log` is a JSON string (image/video/log paths); `status` 1 pass/2 warn/3 fail |

### 2.4 Devices / agents (there is NO device-group table)

| Table | Entity | Columns | Notes |
|---|---|---|---|
| `devices` | `Devices.java` | id, agent_id, ud_id, name, model, manufacturer, cpu, size, version, platform, is_hm, status, img_url, nick_name, password, user, chi_name, temperature, voltage, level, position | grouping == `agent_id`; `status` ONLINE/OFFLINE/TESTING/DEBUGGING/ERROR/UNAUTHORIZED/DISCONNECTED; `user` = current occupant; `password` = install password; `position` = USB hub slot |
| `agents` | `Agents.java` | id, host, port, secret_key, status, version, system_type, high_temp, high_temp_time, has_hub, lock_version, robot_type, robot_token, robot_secret, alert_robot_ids | `secret_key` is the agent's WS auth key; `host:port` is what the **browser** dials for remote control |

### 2.5 Users / RBAC / misc

| Table | Entity | Columns |
|---|---|---|
| `users` | `Users.java` | id, user_name, password (**MD5**), user_role |
| `roles` | `Roles.java` | id, role_name, comment |
| `resources` | `Resources.java` | id, parent_id, path, method, desc, white, need_auth, version (one row per API path+verb; tree for UI) |
| `resource_roles` | `RoleResources.java` (table `resource_roles`) | id, role_id, res_id |
| `global_params` | `GlobalParams.java` | id, params_key, params_value, project_id (multi-value via `a|b|c`) |
| `scripts` | `Scripts.java` | id, name, script_language, content, project_id |
| `packages` | `Packages.java` | id, pkg_name, platform, branch, url, build_url, project_id |
| `conf_list` | `ConfList.java` | id, conf_key, content, extra — keys `resource`, `remote-debug-timeout`, `idle-debug-timeout` |
| `alert_robots` | `AlertRobots.java` | id, project_id, name, robot_type, robot_token, robot_secret, scene, mute_rule, template |

---

## 3. REST endpoint inventory

Canonical paths are the controller `@RequestMapping` values. Through the gateway the web calls them as
`/server/api/controller/...` → `/...` (e.g. `GET /server/api/controller/devices/list` → `GET /devices/list`).
All responses are `RespModel{code,message,data}` (`sonic-server-common/.../http/RespModel.java`), lists wrapped in `CommentPage` for paging.

### 3.1 Devices / agents / remote control — `controller/DevicesController.java` (`/devices`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/devices/list` | paged device list; filters `androidVersion[] iOSVersion[] hmVersion[] manufacturer[] cpu[] size[] agentId[] status[] deviceInfo page pageSize` |
| GET | `/devices/listAll?platform=` | all devices of a platform (suite editor uses this) |
| GET | `/devices?id=` | single device |
| GET | `/devices/findByIdIn?ids[]=` | batch devices (result page) |
| GET | `/devices/listByAgentId?agentId=` | agent's devices (hub UI) |
| GET | `/devices/getFilterOption` | distinct filter values for the UI |
| GET | `/devices/findTemper` | average temperature (UI banner) |
| PUT | `/devices/saveDetail` | edit nickName + install password |
| PUT | `/devices/updateImg` | device tile image |
| GET | `/devices/updatePosition?id&position` | USB-hub slot swap |
| POST | `/devices/occupy` | **REST occupancy**: body `OccupyParams{udId,sasRemotePort,uia2RemotePort,sibRemotePort,wdaServerRemotePort,wdaMjpegRemotePort,token?}`, requires `SonicToken`; returns the `adb connect`/`uia2`/`sib`/`wda` URLs |
| GET | `/devices/release?udId=` | release only if caller is the occupant |
| GET | `/devices/stopDebug?udId=` | force-release a debug session (`msg:"stopDebug"` to agent) |
| DELETE | `/devices?id=` | delete (must be offline) |

`controller/AgentsController.java` (`/agents`): `GET /agents/list`, `GET /agents?id=`, `PUT /agents/update` (name/highTemp/highTempTime/robots),
`GET /agents/hubControl?id&position&type`.

There is **no REST endpoint for install / uninstall / shell / file push / app list / screen**. All of those are
WebSocket commands to the agent (see §5).

`controller/ExchangeController.java` (`/exchange`): `GET /exchange/reboot?id` (reboot device), `GET /exchange/stop?id` (shutdown agent),
`POST /exchange/send?id` (`@WhiteUrl`, internal hop used by `TransportWorker`).

`controller/PackageController.java` (`/packages`): `PUT`, `GET /packages/list?projectId&platform`.

### 3.2 Test cases / steps / elements / public steps

| Controller | Endpoints |
|---|---|
| `TestCasesController` `/testCases` | `GET /list` (projectId, platform, name, moduleIds[], caseAuthorNames[], page, pageSize, idSort, editTimeSort), `GET /listAll`, `GET ?id`, `PUT` (create/update; sets `designer` from JWT), `DELETE ?id`, `GET /deleteCheck?id` (which suites reference it), `GET /findByIdIn?ids[]`, `GET /copy?id`, `GET /listAllCaseAuthor` |
| `StepsController` `/steps` | `GET /listAll?caseId` (ordered tree used by editor), `GET /list`, `GET ?id`, `PUT` (create/update w/ `elements[]` + `childSteps[]`), `DELETE ?id`, `GET /deleteCheck?id` (public-step usage), `PUT /stepSort` (drag reorder), `GET /stepSortTarget?targetStepId&addToTargetNext`, `GET /switchStep?id&type` (indent/outdent), `GET /copy/steps?stepId&toLast`, `GET /resetCaseId?id`, `GET /search/list?searchContent` |
| `ElementsController` `/elements` | `GET /list` (projectId, moduleId, name, page…), `GET ?id`, `PUT`, `DELETE`, `GET /deleteCheck?id`, `GET /copyEle` |
| `PublicStepsController` `/publicSteps` | `GET /list`, `GET /findNameByProjectId`, `GET ?id`, `PUT`, `DELETE`, `GET /deleteCheck`, `GET /copy` |
| `ModulesController` `/modules` | `PUT`, `GET /list?projectId`, `GET ?id`, `DELETE ?id` |
| `VersionsController` `/versions` | `PUT`, `GET /list?projectId`, `GET ?id`, `DELETE ?id` |
| `ScriptsController` `/scripts` | `PUT`, `GET /list`, `GET ?id`, `DELETE` |
| `GlobalParamsController` `/globalParams` | `PUT`, `GET /list?projectId`, `GET ?id`, `DELETE` |

### 3.3 Suites / jobs / results

| Controller | Endpoints |
|---|---|
| `TestSuitesController` `/testSuites` | `PUT` (create/update incl. `testCases[]` + `devices[]` + `alertRobotIds`), `GET /list` (paged), `GET /listAll?projectId`, `GET ?id` (hydrates cases + devices), `DELETE ?id`, **`GET /runSuite?id` → returns `resultId`**, **`GET /forceStopSuite?resultId`** |
| `JobsController` `/jobs` | `PUT` (create/update cron job, validates cron), `GET /list?projectId&page&pageSize`, `GET ?id`, `DELETE ?id`, `GET /updateStatus?id&type=1|2`, `GET /findSysJobs`, `PUT /updateSysJob` (system jobs: clean files, clean results, day/week report) |
| `ResultsController` `/results` | `GET /list?projectId&page&pageSize`, `GET ?id`, `DELETE ?id`, `GET /clean?day=`, `GET /subResultCount?id` (recount), `GET /findCaseStatus?id` (per-case status array for the pie chart), `GET /chart?projectId&startTime&endTime` (pass-rate trend + top cases + top devices + status distribution), `GET /sendDayReport`, `GET /sendWeekReport` |
| `ResultDetailController` `/resultDetail` | `POST /resultDetail` (used by agent push path — body JSON with rid/cid/udId/msg/status/log/time), `GET /list?resultId&caseId&deviceId&type&page`, `GET /listAll?...` |

### 3.4 Users / roles / resources / alert robots / settings

| Controller | Endpoints |
|---|---|
| `UsersController` `/users` | `GET /loginConfig` (white), `POST /login` (white), `POST /register` (white), `GET` (current user from token), `PUT` (change password), `GET /generateToken?day=` (long-lived external token), `GET /list`, `PUT /changeRole?roleId&userId` |
| `RolesController` `/roles` | `GET /list`, `PUT /edit`, `DELETE /delete`, `PUT /update` |
| `ResourcesController` `/resources` | `GET /list` (permission tree), `POST /refresh` (rescan controller mappings), `PUT /edit` (whitelist/auth toggle), `GET /roleResource?roleId` |
| `AlertRobotsController` `/alertRobots` | `PUT`, `GET /list`, `GET /listAll`, `DELETE`, `GET /findDefaultTemplate` |
| `AlertRobotsAdminController` `/alertRobotsAdmin` | same shape, `?scene=agent|system` |
| `ConfListController` `/confList` | `GET /getRemoteTimeout`, `/setRemoteTimeout`, `/getIdleTimeout`, `/setIdleTimeout` |
| `ProjectsController` `/projects` | `PUT`, `GET /list` (white), `GET ?id`, `DELETE ?id` |
| Folder service | `POST /upload` (multipart `file` + `type` ∈ keepFiles/imageFiles/recordFiles/logFiles/packageFiles), `POST /upload/v2`, `POST /upload/recordFiles` (chunked video), `DELETE /files` |

---

## 4. Client-web pages / routes (`src/router/index.js`)

| Route | File | What it does |
|---|---|---|
| `/Login` | `views/Login.vue` | local/LDAP login + register, reads `/users/loginConfig` |
| `/Index/Devices` | `views/Devices.vue` + `components/DeviceDes.vue` | **device center** (project-less entry): tab `device` = card grid w/ filters (OS version, manufacturer, CPU, screen size, agent, status, keyword), auto-refresh switch, average temperature banner; tab `agent` = agent table (secret key copy, hub settings, shutdown, alert-robot config) |
| `/Home/:projectId/ProjectIndex` | `views/ProjectIndex.vue` | project dashboard: pass-rate line chart + top cases/devices (`/results/chart`), quick stats |
| `/Home/:projectId/Devices` | same `Devices.vue` | device center inside project layout |
| `/AndroidRemote/:deviceId`, `/IOSRemote/:deviceId` | `views/RemoteEmulator/index.vue` (splitter) → `AndroidRemote.vue` (150 KB) / `IOSRemote.vue` | **remote control workbench** (see §5.2) |
| `/Home/:projectId/AndroidTestCase`, `/IOSTestCase` | thin wrappers → `components/TestCaseList.vue` | case list: filters by module/name/author, create/edit dialog (`TestCaseUpdate.vue`), copy, delete-with-check, jump to steps |
| `/Home/:projectId/StepListView/:caseId` | `views/StepListView.vue` → `components/StepList.vue` + `StepDraggable.vue` + `StepUpdate.vue` (3.6 k lines) | **step editor**: drag reorder, indent/outdent (`switchStep`), condition blocks, element picker, script editor, public-step insert, ~190 step-type options per platform |
| `/Home/:projectId/TestSuites` | `views/TestSuites.vue` + `components/TestSuiteUpdate.vue` | **suite editor**: name, platform, cover type (case/device major), perfmon on/off + interval, device multi-select, case multi-select + drag order, alert robots; run / force-stop from list |
| `/Home/:projectId/Jobs` | `views/Jobs.vue` | cron job list + editor (name, suite, cron expression, inline cron syntax help), enable/disable, delete |
| `/Home/:projectId/Results` | `views/Results.vue` | result list: id, suite, trigger user, status, start/end, duration, force-stop running suite, delete |
| `/Home/:projectId/ResultDetail/:resultId` | `views/ResultDetail.vue` | **report page**: header (id/suite/user/status/times), pie of per-case status (`/results/findCaseStatus`), device tabs → case list → per-case step log (`StepShow`/`StepLog`), performance tab (`AndroidPerfChart`/`IOSPerfChart`), recording tab |
| `/Home/:projectId/Elements` | `views/Elements.vue` + `ElementUpdate.vue` | element/locator library CRUD, copy |
| `/Home/:projectId/PublicStep` | `views/PublicStep.vue` + `PublicStepUpdate.vue` | reusable step composition (nested step editor) |
| `/Home/:projectId/Scripts` | `views/Scripts.vue` + `CodeEditor.vue` | java/python snippet library |
| `/Home/:projectId/GlobalParams` | `views/GlobalParams.vue` | project global params (values may be `a|b|c` for data-driven split) |
| `/Home/:projectId/Modules` | `views/Modules.vue` | module CRUD |
| `/Home/:projectId/Versions` | `views/Versions.vue` | iteration/version CRUD |
| `/Home/:projectId/Packages` | `views/Packages.vue` + `PackageList.vue` | package registry for `install` steps |
| `/Home/:projectId/ProjectOption` | `views/ProjectOption.vue` | project settings (7-line wrapper) |
| `/Home/:projectId/ProjectAlertRobots` | `views/ProjectAlertRobots.vue` | per-project robot notification binding |
| `/Setting/Resources`, `/Roles`, `/Users`, `/SysJobs`, `/RemoteSettings`, `/SysAlertRobots` | `views/Setting/*.vue` | RBAC tree edit + refresh, role CRUD + role-resource mapping, user list + role assignment, system job cron, remote/idle debug timeouts, system-level robots |
| (dead link) | `views/Home.vue` menu `/Home/:projectId/Crash` | no matching route exists — crash-analysis page was never shipped |

Layout/nav: `views/Home.vue` (project-scoped sidebar), `views/Index.vue` (project-less), `components/Header.vue`
(project switcher, change password, **Access Token generation**, locale switch).

---

## 5. How remote control and execution actually work

### 5.1 Topology (the important part)

```
browser ──HTTP(/server/api/*)──> gateway:3000 ──lb──> controller | folder
browser ──WebSocket (direct!)──> agent.host:agent.port  /websockets/...
agent   ──WebSocket (outbound)──> controller  /agent/{secretKey}   (single control channel)
```

* Server side: `controller/transport/TransportServer.java`, `@ServerEndpoint("/agent/{agentKey}")` (line 45), authenticated by
  `AgentsService.auth(secretKey)`. In-memory session map `tools/BytesTool.java#agentSessionMap`.
* Server→agent push: `controller/transport/TransportWorker.java#send(agentId,json)` — it does **not** hold the socket; it looks up every
  `sonic-server-controller` instance via `DiscoveryClient` and POSTs to `http://host:port/exchange/send?id=` (see `ExchangeController#send`)
  so whichever instance owns the session forwards it. **This is the single most coupled piece of the design** (needs Eureka + sticky session map).
* Browser→agent: `src/views/RemoteEmulator/AndroidRemote.vue:505-525` builds
  `ws://{agent.host}:{agent.port}/websockets/android/{secretKey}/{udId}/{SonicToken}` plus
  `/websockets/android/screen/...`, `/websockets/android/terminal/...`, and `AudioWSServer` `/websockets/audio/{key}/{udId}`.
  iOS mirrors this with `/websockets/ios/...`, `/websockets/ios/screen/...`, `/websockets/ios/terminal/...`,
  WebView inspector `/websockets/webView/{key}/{port}/{id}`.
  ⇒ **the server is not in the media path at all**; agents must be reachable from the browser.

### 5.2 Screen mirroring

* Two modes, switched at runtime by a JSON command on the screen socket: `{type:'switch', detail:'scrcpy'|'minicap'}`
  (`AndroidRemote.vue:1444`, agent `websockets/AndroidScreenWSServer.java:123`).
* **scrcpy**: `sonic-android-scrcpy.jar` launched via `app_process ... com.genymobile.scrcpy.Server 1.23 max_fps=60 tunnel_forward=true
  send_frame_meta=false control=false` (`tests/android/scrcpy/ScrcpyLocalThread.java:101`), raw **H.264 frames sent as binary WS frames**
  (`ScrcpyOutputSocketThread.java` `Buffer.sendTo(session)`), muxed in the browser by **jmuxer** (`src/views/RemoteEmulator/Scrcpy/index.js`).
  `control=false` → input is *not* via scrcpy; it comes over the control socket.
* **minicap**: JPEG frames as binary frames (`tests/android/minicap/*`), rendered into an `<img>`/canvas.
* Audio: separate `AudioWSServer` + `src/lib/audio-processor`.
* Screenshots for test reports are produced **client-side** by drawing the video into a canvas (`getVideoScreenshot()`).

### 5.3 Input / device operations (all over the control socket, JSON `{type, detail}`)

`AndroidRemote.vue` message types → agent `websockets/AndroidWSServer.java:167+`:

`touch` (raw minitouch-encoded multi-touch), `text`, `keyEvent`, `battery`, `install`(apk url/path), `uninstallApp`, `appList`,
`pushFile`, `pullFile`, `openApp`, `killApp`, `tap`, `longPress`, `swipe`, `scan` (push QR image to camera),
`proxy`/`clearProxy`/`installCert`, `startPerfmon`/`stopPerfmon`, `startKeyboard`/`stopKeyboard`, `setPasteboard`/`getPasteboard`,
`find`, `forwardView`, `openDriver`/`closeDriver` (UIAutomator2), `tree`/`eleScreen`/`checkLocation`,
`debug`/`runStep`/`stopStep` (single-step debug), `screenSub/screenAdd/screenAbort`, `rotateDevice`, `logcat`/`stopLogcat`/`wifiList` (terminal socket),
`command`/`stopCmd` (arbitrary adb shell on the terminal socket).

File uploads (APK, QR image, file transfer) go through the **folder** service (`POST /upload` with
`type=packageFiles|imageFiles`), and the returned URL is then sent to the agent.

Device ownership: agent tells the server who occupies (`{msg:"debugUser",udId,token,agentId}`), server writes `devices.user`
(`DevicesServiceImpl#updateDevicesUser`); timeouts from `conf_list` (`remote-debug-timeout`, `idle-debug-timeout`) are pushed to the agent
in the auth handshake and enforced by the agent, which emits `{msg:"stopDebug"}`-style releases.

### 5.4 Test execution orchestration

Server (`services/impl/TestSuitesServiceImpl.java`):

1. `runSuite(suiteId, strike)` (line 88): load suite DTO → drop OFFLINE/DISCONNECTED devices → **create one `results` row**
   (`status=RUNNING`, `strike`, `sendMsgCount` = #cases, or #cases × #devices for cover=device) → build global params
   (`GlobalParams`, values containing `|` are shuffled and distributed per device via `refreshGlobalParams`).
2. `CoverHandler` strategy (`CaseCoverHandler` / `DeviceCoverHandler`, resolved from `Map<Integer,CoverHandler>`):
   for each device (or each case) build a case payload with `packageTestCase()` → `{cid, rid, device[], gp, perf{isOpen,perfInterval},
   steps[]}` where each step is produced by recursive `getStep()`/`handleSteps()` (expands `publicStep` nesting, resolves `install`
   package ids, serializes conditional child steps).
3. `send(agentId, platform, cases)` → `{msg:"suite", pf, cases}` to each agent.
4. `forceStopSuite(resultId)` → sends `{msg:"forceStopSuite"}` and marks the result.

Agent: `transport/TransportClient.java:317` `case "suite"` — builds a **TestNG** suite programmatically (`AndroidTests`/`IOSTests` classes,
`dataInfo` parameter per case) and runs the step tree through `tests/handlers/StepHandlers` +
`NoneConditionHandler / IfHandler / ElseIfHandler / ElseHandler / WhileHandler`, with `AndroidStepHandler`/`IOSStepHandler`
implementing each `stepType`.

Feedback (agent → server, same WS): `{msg:"step"|"perform"|"record"|"status", rid, cid, udId, status, log, des, time}`.
Server: `TransportServer#onMessage` → `ResultDetailServiceImpl#saveByTransport` (deletes-and-inserts the `status` row, then
`resultsService.suiteResult(rid)` recomputes the run status from `send/receive` counters + worst detail status), and
`subResultCount` for progress. Artifacts (screenshots, screen recordings, perf CSVs) are uploaded to the folder service and referenced by URL inside
`result_detail.log` (JSON).

Reports: `ResultsServiceImpl#chart` (pass-rate per day, top failing cases/devices, status distribution) and
`sendDayReport`/`sendWeekReport` → `tools/robot/RobotFactory`/`RobotMessenger` (DingTalk, WeCom, Feishu, YouSpace, Telegram, Line, Slack, generic WebHook;
templates per project/agent with `muteRule`).

Scheduling: `quartz/QuartzJob.java` (a `QuartzJobBean`) switches on `JobType`: TEST_JOB → `testSuitesService.runSuite(...)`,
CLEAN_FILE_JOB → folder Feign `delete(day)`, CLEAN_RESULT_JOB → `resultsService.clean(day)`, DAY/WEEK report.
Cron validation `tools/QuartzJobTools#validateOrDisableCronExpression`; jobs registered through `QuartzHandler`.

---

## 6. Portability into a Spring Boot 3 + React TestHub

### 6.1 Directly reusable (copy the design, not the code)

| Item | Source | Why it ports |
|---|---|---|
| Case → step tree model (`parent_id` + `sort` + `condition_type` + `disabled`) | `models/domain/Steps.java`, `models/dto/StepsDTO.java` | plain relational tree; condition enum is 5 values |
| `results` / `result_detail` split | `Results.java`, `ResultDetail.java` | cheap, gives per-case-per-device evidence + progress counters |
| Element locator library + `steps_elements` N:N + "used by" checks | `elements`, `steps_elements`, `*/deleteCheck` endpoints | solves locator drift, which BDD suites usually lack |
| Reusable step library (`public_steps` + nesting expansion) | `PublicStepsService`, `getStep()/handleSteps()` | recursion pattern is ~40 lines |
| Cover types (case-major vs device-major) + explicit device set per suite | `CoverType`, `test_suites_devices` | maps cleanly onto an existing test-plan concept |
| Global params with `|`-split, shuffled, per-device assignment | `TestSuitesServiceImpl#refreshGlobalParams` | cheap data-driven device matrix; complements TestHub's dataset/env model |
| Step failure strategies + if/else/while | `ConditionEnum`, `error` column, agent `*Handler` classes | TestHub's BDD steps could gain conditional blocks |
| API envelope + paging + i18n error codes | `RespModel`, `CommentPage`, `RespEnum` | trivial, familiar |
| URL-resource + role RBAC seed/refresh | `resources`, `ResourcesService#init/refresh`, `PermissionFilter` | Spring Security can take over; the *idea* of scanning mappings into a permission table is reusable |
| Notification robot abstraction | `tools/robot/{RobotFactory,RobotMessenger,message,vendor}` | pluggable `RobotType` enum + template per project |
| Debug/remote timeouts as config rows | `conf_list` | better than hard-coded config |
| Step-type taxonomy (≈190 leaf options, grouped) | `components/StepUpdate.vue` `androidOptions`/`iosOptions` | a ready-made checklist of what a mobile step DSL must cover |

### 6.2 Needs re-implementation, but the semantics are worth keeping

| Item | Source | What changes |
|---|---|---|
| Suite run payload + progress protocol | `TestSuitesServiceImpl#runSuite/packageTestCase/getStep`, `TransportServer#onMessage` | replace TestNG + `msg` strings with TestHub's own job/step event contract |
| Agent command channel | `TransportWorker` + `/exchange/send` + `BytesTool` session map | TestHub already has an ADB agent; needs a *typed command envelope* and an **outbound** agent connection (agent dials server) instead of Eureka hopscotch |
| Device occupy/release + idle timeout | `DevicesServiceImpl#occupy/release`, `updateDevicesUser` | keep the state machine (ONLINE→DEBUGGING→ONLINE, TESTING for runs, single-owner) |
| Cron scheduling | `quartz/QuartzJob`, `JobsService` | Quartz is fine on Boot 3 but Sonic's clustered-JDBC config + `QRTZ_*` DDL is heavy; a `jobs` table + Quartz (or ShedLock) is enough |
| Step editor UX (drag, indent/outdent, element picker, condition blocks) | `StepDraggable.vue`, `StepUpdate.vue` | must be rebuilt in React; the interaction model is the valuable part |
| Report page (per-device/per-case tabs, pie, perf chart, step log, video) | `views/ResultDetail.vue`, `StepShow.vue`, `StepLog.vue` | rebuild on TestHub's result APIs; reuse the information architecture |
| scrcpy viewer | `views/RemoteEmulator/Scrcpy/index.js` (jmuxer) | TestHub already streams scrcpy — only the `switch` mode negotiation and canvas-screenshot trick are worth copying |

### 6.3 Tightly coupled — do not port

| Item | Where | Why |
|---|---|---|
| Eureka + `DiscoveryClient` + `lb://` + `server.port:0` | `TransportWorker`, gateway yml, `application-eureka.yml` | the controller-to-controller HTTP fan-out exists *only* because WS sessions are in-memory and instances are ephemeral |
| Gateway routes/StripPrefix + white list + `AuthFilter` | `sonic-server-gateway/**` | pure Spring Cloud Gateway; TestHub should use its own security filter chain |
| Actable auto-DDL | `application-jdbc.yml` (`actable.model.pack`, `table.auto: update`) | schema lives in annotations; no migration history, no FKs, MySQL-only (`IFNULL`, `concat`, date funcs in `mapper/*.java`) |
| Agent transport protocol | `msg: auth/agentInfo/heartBeat/occupy/release/debugUser/stopDebug/settings/shutdown/reboot/hub/suite/runStep/forceStopSuite/step/perform/record/status/battery/subResultCount` | undocumented string protocol + TestNG runtime + per-`udId` thread pools |
| `/websockets/android/{key}/{udId}/{token}` direct-to-agent topology | `AndroidRemote.vue:505` | requires agents to expose a public host:port and share the JWT secret; a real platform should proxy |
| Folder service + `imageFiles/recordFiles/logFiles/packageFiles` layout | `sonic-server-folder` | objects should go to TestHub's artifact storage |
| MD5 passwords, `SonicToken` JWT + `superAdmin` string check | `UsersServiceImpl`, `JWTTokenTool`, `PermissionFilter` | weak; replace with TestHub auth |
| `int[]` robot ids as CSV varchar (`NullableIntArrayTypeHandler`) | `tools/NullableIntArrayTypeHandler.java` | replace with join tables / JSONB |
| Vue/Element Plus components | `src/**` | different framework |
| iOS stack (go-ios, WDA, sib, tidevice) | `sonic-ios-bridge-main`, `IOSRemote.vue` | whole separate runtime |

---

## 7. Prioritized gap list for TestHub

TestHub today: device pool + ADB device agent + scrcpy web stream + BDD UI cases + review + test plans + AI runtime + API automation + envs/data-driven.

### P0 — blocks parity on mobile regression

1. **Suite-level orchestration across a device set with case×device matrix** (Sonic: `test_suites_devices` + `cover` 1/2,
   `TestSuitesServiceImpl#runSuite`). TestHub has plans but needs explicit *multi-device* fan-out, per-device run state and
   "skip offline device" semantics.
2. **Step-level execution events + artifacts** (Sonic: `result_detail` with `type=step|perform|record|status`, screenshot/video/perf URLs).
   Without this a failing BDD case gives no per-step evidence. This is the biggest reporting gap.
3. **Server-side job/run model separate from the case** (`results` row per run with `strike`, counters, start/end, status recompute via
   `suiteResult`). Needed for force-stop, progress, and history.
4. **Device occupancy state machine + idle/remote timeouts** (Sonic: `occupy/release/stopDebug`, `devices.user`, `conf_list` timeouts).
   TestHub has a device pool; it needs the lease/expiry/single-owner rules or two users will fight over a phone.
5. **Cron scheduler bound to a suite/plan** (Sonic: `jobs` + `QuartzJob` + `JobStatus` enable/disable/once, plus system jobs for cleanup
   and day/week reports).
6. **Element/locator repository with reuse + impact check** (Sonic: `elements`, `steps_elements`, `deleteCheck`, `copyEle`). BDD steps
   inline their selectors today; centralizing them is the prerequisite for AI self-healing.
7. **Report dashboard API** (Sonic: `results/chart` → pass-rate trend, top failing cases, top devices, status pie;
   `results/findCaseStatus`). Gives reviewers a trend view rather than one run.

### P1 — meaningful capability uplift

8. **Reusable/public step library with nesting + copy** (Sonic: `public_steps`, `public_steps_steps`, `getStep` recursion).
9. **Conditional & loop steps (if / else-if / else / while) + per-step failure strategy** (Sonic: `ConditionEnum`, `parent_id`,
   `error` + agent `*Handler`). Needed for real mobile flows.
10. **Continue-on-error / retry / step-disable** semantics (Sonic: `disabled`, `error`).
11. **Performance capture during runs** (Sonic: `isOpenPerfmon`/`perfmonInterval`, `type=perform` details, `AndroidPerfChart`):
    cpu/mem/fps/battery/temperature sampling per step.
12. **Recording (video) per case** (Sonic: chunked `POST /upload/recordFiles` + `type=record` detail + `<video>` tab).
13. **Notification robots + templates + mute rules** (Sonic: `alert_robots.scene/mute_rule/template`, `RobotFactory` 8 vendors),
    triggered by result status; TestHub likely has only chat webhooks.
14. **Deep-link remote ADB/WDA** (Sonic: `occupy` returns `adb connect host:port`, `uia2` URL, `sib`/`wda` endpoints) — lets engineers
    attach Appium/IDE to the same device the web UI is driving.
15. **RBAC driven by an API-resource catalog** (Sonic: `resources` + `POST /resources/refresh` scanning controller mappings).
16. **Package/version registry + `install`-by-package step** (Sonic: `packages`, `versions`) with Jenkins/build URL.
17. **Script snippet library** (Sonic: `scripts` java/python + `runScript` step) — overlaps TestHub's API automation; only add if
    mobile steps need embedded code.

### P2 — lower value / large cost

18. **iOS remote control + execution** (Sonic: go-ios/WDA/sib, `IOSRemote.vue`, `IOSStepHandler`) — separate runtime, weeks of work.
19. **USB hub control** (`agents.has_hub`, `/agents/hubControl`, `devices.position`, `HubSettings.vue`) — hardware-specific, only useful with the
    same relay boards.
20. **WebView / Poco / game-engine element trees** (`WebViewWSServer`, `PocoPane.vue`, `webViewEle`/`pocoEle` step families) — niche.
21. **Crash analysis page** — Sonic's own menu links to a non-existent `/Crash` route (dead code); don't chase it.

### Explicit non-goals (Sonic has nothing here; keep TestHub's lead)

API automation, Gherkin/BDD authoring + review workflow, environments/datasets, AI runtime, test-plan approval. Sonic's model is
"scripted mobile steps in a flat suite"; TestHub should keep its case/plan/review semantics and borrow only the **execution + evidence +
orchestration** layer (§6.1/§6.2), not the authoring model.

### Suggested integration shape

```
TestHub Spring Boot 3
  └─ run-service: plan → resolve devices (pool) → build case×device matrix → create run + run_steps
  └─ command bus: agent dials out (WSS /agent) and receives {cmd:"run_case", ...}
  └─ event ingest: {evt:"step"|"artifact"|"run_status"} → run_steps + artifacts
  └─ scheduler: jobs table + Quartz/ShedLock → trigger run-service
React
  └─ /devices (card grid + lease state)  /remote/:id (existing scrcpy stream + command socket)
  └─ /suites (case set + device set + cover type)  /runs  /runs/:id (per-device → per-case → per-step + video + perf)
```
