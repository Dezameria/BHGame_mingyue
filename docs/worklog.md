# Worklog

ใหม่สุดอยู่บน

## 2026-10-22

- **ร่างสเปกหาบน้ำยืนม้า (`water_yoke`, D46):** เอาหาบน้ำที่ตัดใน D10 กลับมาแบบยืนม้าอยู่กับที่ (ไม่มีการเดิน), Station, ปุ่ม A/D/Space, cue เอียง (`TAP`) / เด้ง (`TAP`) / ลมเล่น (`FEINT`) ใช้ `BeatTrack`, ระดับน้ำแทน mistake, ต้องการ channel ใหม่ `BODY` และ `PlayerPose.HORSE_STANCE` (ยังไม่ตัดสิน) เป็นเอกสารเท่านั้น ยังไม่มีโค้ด
- **หาบน้ำเดินได้ (D47):** ผู้เล่นเลือกแบบค้าง W เดินเอง สเปก `water_yoke` เขียนใหม่: เส้นทางจากหินทาง `yoke_path_stone` ถึงโอ่ง `yoke_jar` (สแกนตอนเริ่ม ≤ 32 จุด), client เดินตามทาง 70% ความเร็วเดิน, `Camera.PATH`, server ตรวจตกทางทุก 5 tick, ยืนนิ่ง cue ช้าลง เดินครึ่งหลังเร็วขึ้น, `TimeLimit` อัตโนมัติจากความยาวทาง, `PlayerPose.CARRY_YOKE` แทน `HORSE_STANCE` เพิ่ม `walkPath`/`PATH` ใน `core-mechanics.md` หัวข้อ 3 และ 10 (ระบุว่ายังไม่มีโค้ด) เป็นเอกสารเท่านั้น
- **หาบน้ำเป็นของแบก (D48, แทน D47):** ผู้ใช้ต้องการแบกแล้วเดินไปไหนก็ได้ วางที่ไหนก็ได้ สเปก `water_yoke` เขียนใหม่: ประเภทบล็อก Carry (ยกออกจากโลก/วางคืน, ระดับน้ำ 0–3 ใน block entity), เดินด้วย vanilla, ทรงตัว `LMB`/`RMB`, `SPACE` ย่อรับ, ปุ่มยกเลิก = วาง, ยืนนิ่งไม่มี cue, เดินนานยิ่งเร็วขึ้น, เติมน้ำโดยยืนในน้ำตื้น, จบผิดปกติวางบล็อกคืนหรือ drop item แก้ `core-mechanics.md` หัวข้อ 3 (`movement` แทน `walkPath`/`PATH`) และ 10 (บล็อกแบบ Carry) เป็นเอกสารเท่านั้น
- **หาบน้ำกระโดดได้ (D49):** `SPACE` กลับเป็นกระโดดของ vanilla, ตัด cue เด้ง, ลงพื้นแล้วส่ายแรง (เอียงคู่ กรอบแคบลง ซ้อนได้ 2 รอบ), ตกเกิน 2 บล็อกหกทันที, `PatternContext.motion()` เพิ่ม `AIRBORNE`, `CueVisual.SWAY` แทน `BOUNCE` เป็นเอกสารเท่านั้น
- **หาบน้ำวิ่งได้ (D50):** เปิดวิ่ง, เพิ่มแถว "วิ่ง" ใน pattern (cue 16 / 14 / 12 tick, กรอบแคบลง, เอียงคู่ 40%), ความล้าสะสมเร็ว 2 เท่าตอนวิ่ง, `PatternContext.motion()` เพิ่ม `SPRINT` เป็นเอกสารเท่านั้น

## 2026-10-21

- **หุ่นไม้ต่อยช้าๆ ระหว่างชุด (D45):** `ClientPoses` เริ่มท่าว่าง 10 tick หลังหมัดจริง (เดิม 40), ท่าว่างของ `WING_CHUN` เป็นหมัด Epic Fight ทุก 16–21 tick และเล่นในมุมบุคคลที่หนึ่งของตัวเองด้วย (แกว่งแขน vanilla ยังข้ามในมุมบุคคลที่หนึ่ง) build ผ่าน ยังไม่ได้ลองในเกม
- **ท่าต่อยไม่ขยับตัว (D44, แทน D43 ที่ทำให้กระตุก):** ลบการตรึงตำแหน่งฝั่ง client, เพิ่ม mixin `mixin/epicfight/ActionAnimationMixin` ยกเลิก `ActionAnimation.move` ตอนผู้เล่นอยู่ใน session (`core/session/TrainingPins`: server ดูจาก `SessionManager.isInSession`, client ดูจาก session ของตัวเอง), ตั้ง MixinGradle (`org.spongepowered.mixin` 0.7, `bhgame.mixins.json` แบบ `required=false` + `BhGameMixinPlugin` ข้ามเมื่อไม่มี Epic Fight) ตรวจแล้วด้วย `runServer`: log ขึ้น `Mixing epicfight.ActionAnimationMixin ... into ActionAnimation` และเซิร์ฟขึ้น `Done` ยังไม่ได้ลองใน client
- **ตรึงตำแหน่งผู้เล่นระหว่าง session (D43):** หมัด Epic Fight มี root motion ทำให้ตัวเดินหน้า แก้โดย `ClientSession.anchorX/Z` (ตำแหน่งหลังถูกจัด) + `ClientForgeEvents.onPlayerTick` ตั้งตำแหน่งกลับทุก tick ก่อนส่งให้ server build ผ่าน ยังไม่ได้ลองในเกม
- **หุ่นไม้ D42:** จัดตำแหน่งตอนเริ่ม (`TrainingBlock.standDistance()`, หุ่น = 1.3, `SessionManager.snapToSide` เลือกด้านที่ใกล้สุดที่ว่างและมีพื้นจาก 4 ด้าน แล้ว teleport หันเข้าหุ่น), หมัดมีปุ่มลูกศรติดไป (`S2CPlayerAction.key`, `SequenceTrack.Listener.onStep(..., key)`), เอฟเฟกต์หมัดตามทิศ (`client/pose/PunchEffects`), คนเล่นเล่นหมัด + เอฟเฟกต์เองทันทีที่กด (`ClientSession.LocalPress`, `InputCapture.localPunch`) server ส่งให้คนรอบข้างเท่านั้น (`BhGameNetwork.sendToTracking`), ท่าแกว่งระหว่างชุดใช้ Epic Fight เมื่อมี network version `6` build + test 20 ข้อผ่าน ยังไม่ได้ลองในเกม
- **แก้ค้าง "Joining world" หลังเพิ่ม Epic Fight:** mixin ของ Epic Fight ไม่ทำงานใน dev run (หาชื่อ method แบบ SRG ไม่เจอ) ข้อมูลผู้เล่นของ Epic Fight ไม่ถูกสร้าง จึงค้างและ log เต็มไปด้วย NPE ของ `epicfight:stamina_bar` แก้โดยเพิ่ม `mixin.env.remapRefMap` + `mixin.env.refMapRemappingFile` ใน run ทุกตัวของ `build.gradle` แบบเดียวกับ BHSpells ตรวจแล้วด้วย `runServer`: mixin ของ Epic Fight ไม่ fail เลย และเซิร์ฟขึ้น `Done` ยังไม่ได้ลอง client
- **Epic Fight กับหุ่นไม้ (D41):** soft dependency Epic Fight 20.14.17 (`maven.modrinth:vu3NZ5Ma:KEBfkBat`, `compileOnly` + `runtimeOnly` ใน dev เมื่อ `use_epicfight=true`), `mods.toml` `mandatory=false`, `compat/epicfight/EpicFightCompat` + `client/EpicFightClientBridge` (`FIST_AUTO1-3`, `BIPED_HIT_SHORT` เล่นฝั่ง client), `core/pose/PlayerAction` + `S2CPlayerAction` (throttle 3 tick), `PatternContext.action`, `SequenceTrack.Listener.onStep`/`onFinisher`, `ClientPoses.action` (fallback แกว่งแขน, หยุดแกว่งแบบสุ่ม 40 tick หลังมีท่าจริง) network version `5` build และ test 20 ข้อผ่าน ยังไม่ได้ลองในเกม เอกสาร: `integrations/epicfight.md`
- **หุ่นไม้โหมดไม่รู้จบ (D40):** ไม่มี HP, แต้ม → ด่าน, ความยากตามสูตรต่อด่าน, เลขด่านขึ้นบนจอ (`PatternContext.banner` + `S2CSessionBanner` + `SessionHud.renderBanner`), ข้อความจบบอกด่าน (`setOutcomeMessage`), ด่านสูงสุดใน scoreboard `bhg_dummy_stage` (`recordBest`, `Stats.recordBest`), บทลงโทษแยกไป `WoodenDummyPenalties` (pattern ไม่โหลด block class) network version `4` test 20 ข้อผ่าน (เพิ่ม `WoodenDummyPatternTest` ที่ให้ bot เล่นถึงด่าน 7 และย้าย context ปลอมไปเป็น `FakePatternContext`) ยังไม่ได้ลองในเกม
- **หุ่นไม้ปรับตามผลทดสอบของผู้ใช้ (D39):** แถวลูกศรย้ายมาวาดบนจอ (`client/ArrowRowHud`, ลบ `render/ArrowRowRenderer`), กดผิดหรือ `SPACE` ก่อนครบ = โดนฟาดออก session จบ (`SequenceTrack` โหมด strict + `Listener.onWrongPress`, `PatternContext.fail()`, `CueVisual.ARROWS_STRICT`, client ทำนายแถวแตกแล้วค้างสีแดง), ข้อความ `bhgame.wooden_dummy.outcome.fail` build ผ่าน, test 18 ข้อผ่าน (เพิ่ม strict 2 ข้อ) ยังไม่ได้ลองในเกม

## 2026-10-19
- **P3 เขียนโค้ดแล้ว:** build ผ่าน, unit test 16 ข้อผ่าน (`BeatTrackTest` 6, `SequenceTrackTest` 7, `GaugeTest` 3), **ยังไม่ได้ทดสอบในเกมและยังไม่ได้เปิดเกมดูโมเดล** (เซิร์ฟของผู้ใช้ยังเปิดอยู่ใน `run/`)
  - core: `gauge/Gauge` + `S2CGaugeSync`, `pattern/common/SequenceTrack`, `BeatTrack.Listener`, `PatternContext` เพิ่ม `hit(id, damage)`, `miss(who, id, penalty)`, `win()`, `setFailPenalty`, `gauge(i, flags)`, `block/StationBlock`, `TrainingBlock` เพิ่ม `channel()` และ `lookHeight()`, `Penalties.freeze`/`knockback`, `CueData.steps` (แถวลูกศร), `CueVisual` เพิ่ม `YIN`/`YANG`/`SPARK`/`SPARK_FAKE`/`ARROWS`/`FINISHER`, `InputKey.opposite()`, config `gaugeResyncTicks`, network version `3`
  - game: `polarity_grass`, `thunder_grass`, `wooden_dummy` (ลงทะเบียนใน `Minigames` ครบ 5 เกม)
  - client: `ClientSession` (gauge, ทำนายผลชุดลูกศร, สีของ cue), `InputCapture` (ส่งปุ่มค้างเสมอ), `render/ArrowRowRenderer`, BER แยกตาม channel และใช้สีตาม `CueVisual`, เสียง cue ชั่วคราวต่อ visual
  - resources: โมเดลชั่วคราว (blue_orchid, fern, หุ่นไม้จาก stripped oak log), loot table, lang
  - เอกสาร: D36–D38, `roadmap.md`, `architecture.md`, `testing.md` (checklist P3), สเปกทั้ง 3 เกม
- **P2a เขียนโค้ดแล้ว:** build ผ่าน, unit test 6 ข้อผ่าน (`./gradlew.bat test`), **ยังไม่ได้ทดสอบในเกม** และยังไม่ได้ลองเปิด dedicated server กับโค้ดใหม่ (ตอนนั้นมีเซิร์ฟของผู้ใช้เปิดอยู่ในโฟลเดอร์ `run/` จึงเปิดซ้ำไม่ได้)
  - core: `config/BhGameConfig` (server + client, แทนค่าส่วนใหญ่ใน `Tuning`), `session/Participant`, `TrainingSession` หลายคน + implements `PatternContext`, `SessionState.WAITING`, `SessionManager` (เข้าร่วม, รอคู่, `leave` ตาม D32, `finish(outcome, culprit, disconnecting)`), `StressTest`, `pattern/PatternContext`, `Phases`, `pattern/common/BeatTrack`, `Penalties` (`damage`, `fire`, `burst`, `strikeVisual`), `TrainingBlock` (`inputProfile(role)`, `requiredPlayers()`, `waitingHintKey()`, `onAbandoned`)
  - registry: `Minigames.register` หนึ่งบรรทัดต่อเกม (D33), `ModBlocks`/`ModItems` เหลือแค่ DeferredRegister, block entity และแท็บ creative ดึงรายการจาก `Minigames`
  - game: `pulse_grass` ย้ายไปใช้ `BeatTrack`, เพิ่ม `twin_grass` (D35)
  - network: version `2`, `CueData` มี role mask, `S2CSessionStart` มี role/จำนวนคน/waiting, `C2SInput.aimed`, ตัวนับ packet ของ stress
  - client: `ClientSession` รู้ role, cue ของคู่หูจาง 35%, เสียงและ HUD เฉพาะ cue ของตัวเอง, ส่ง yaw/pitch เมื่อ profile ต้องการ, `showKeyHints`/`poseAnimationDistance` จาก config
  - command: `/bhgame stress <n> [game]`, `/bhgame stress stop`, `/bhgame debug` แสดงทุกคนใน session
  - test: `src/test/.../BeatTrackTest.java`
  - เอกสาร: D33–D35, `architecture.md`, `roadmap.md` (แบ่ง P2a/P2b), `core-mechanics.md` หัวข้อ 10, `multiplayer-performance.md`, `adding-a-minigame.md`, `testing.md` (checklist P2a), `twin_grass.md`
- **ระยะเริ่ม (D30):** `TrainingBlock.startReach()`/`startHeightTolerance()` (ค่าเริ่มต้น 3 / 1.5), `HerbBlock` = 1.75 / 1.0, เช็กใน `SessionManager.tryStart` + ข้อความ `bhgame.session.too_far` เพิ่มลง `core-mechanics.md` หัวข้อ 10 build ผ่าน ยังไม่ได้ทดสอบในเกม
- **ไอเดียหญ้าสองคน (D31):** สเปก `design/minigames/twin_grass.md` (หยาง `LMB` / หยิน `RMB`, cue คู่, lobby รอ 30 วินาที), ตารางแกนสำหรับสร้าง variant ของหญ้าใน `design/minigames/README.md`, เพิ่มงาน session หลายคนใน P2 และ `twin_grass` ใน P8
- `twin_grass`: ผู้ใช้ตัดสินว่าคนทิ้งกลางคันโดนบทลงโทษใหญ่ คู่หูได้ `ABORTED` (D32)
- ตั้ง `run/ops.json` (op ให้ `Dev` และ `Player2`) และ `run/server.properties` (`online-mode=false`) สำหรับทดสอบในเครื่อง

- เพิ่ม run config `client2` ใน `build.gradle` (`parent runs.client`, โฟลเดอร์ `run2/`, `--username Player2`) และเพิ่ม `run2` ใน `.gitignore` ตรวจแล้วว่ามี task `runClient2` (`gradlew tasks --all`) ยังไม่ได้เปิดใช้จริง ขั้นตอนตั้งเซิร์ฟ + 2 client อยู่ใน `testing.md`

- **P1b (ส่วนโค้ด) เขียนแล้ว:** build ผ่าน, `runClient` โหลดได้ไม่มี error, **ยังไม่ได้ทดสอบในเกม**
  - `core/pose/PlayerPose` (`NONE`, `PULL_HERB`, `WING_CHUN`, `MEDITATE`, `QI_FLICK` + flag ย่อตัว), `TrainingBlock.pose()` (หญ้า = `PULL_HERB`)
  - `network/S2CPlayerPose` ส่งแบบ `TRACKING_ENTITY_AND_SELF` ตอนเริ่มและจบ session, `SessionEvents.onStartTracking` ส่งให้คนที่เพิ่งเข้ามาในระยะ
  - `client/pose/ClientPoses`: ท่า vanilla fallback (ย่อตัวผ่าน `RenderPlayerEvent`, แกว่งแขนตามเวลาสุ่ม, LOD 32 บล็อก, ลืม pose ของ entity ที่หายไปเกิน 100 tick, ล้างตอน logout และ respawn/เปลี่ยนมิติ)
  - `command/BhGameCommand`: `/bhgame debug` (permission 2) + ตัวนับข้อความใน `BhGameNetwork`
  - เหลือใน P1b: ทดสอบบน dedicated server + 2 client + จำลอง ping (ผู้ใช้ต้องตั้ง `eula=true` เอง)

- **P1a เขียนโค้ดแล้ว** (vertical slice `pulse_grass`): build ผ่าน, `runClient` โหลดถึงหน้าแรกไม่มี error, **ยังไม่ได้ทดสอบเล่นในเกม**
  - core: `Tuning` (ค่าคงที่แทน config ใน P1), `input/InputKey`, `input/InputProfile` (ครบตาม D24), `cue/*` (`CueData` มี target index, `CueType` ครบ 5 ชนิด, `CueChannel` 4 แบบ), `pattern/Pattern` (interface เล็ก 2 method), `session/TrainingSession` + `SessionManager` (`nextEventTick`, rate limit, sweep 100 tick, ลบ session ทุกทาง) + `SessionEvents`, `block/TrainingBlock` + `HerbBlock` (ความแข็ง 1.0 = ทุบ 1.5 วินาที, ข้อความเตือน, ทุบตรงๆ = บทลงโทษใหญ่, `forceSolidOn` กันน้ำพัด) + `TrainingBlockEntity` (ไม่ tick, user ไม่เซฟ), `outcome/Penalties`, `Rewards` (loot table แบบ chest), `Stats`
  - game: `pulse_grass/PulseGrassBlock`, `PulseGrassPattern` (สร้างจังหวะล่วงหน้า 4 จังหวะ ส่งเป็นชุดเมื่อเหลือไม่ถึง 60 tick, กดในช่วงหรี่พลาดครั้งเดียวต่อช่วง)
  - network: `BhGameNetwork` + `S2CSessionStart` (มี InputProfile + target set), `S2CCue`, `S2CSessionUpdate`, `S2CSessionEnd`, `C2SInput` (มี flag aim payload), `C2SAbort`
  - client: `ClientSession` (นาฬิกา tick ของ client เอง), `ClientPacketHandler`, `ClientForgeEvents` (ยกเลิก mouse ก่อน vanilla, อ่าน WASD/Space จาก key event, ล็อกการเดินใน `MovementInputUpdateEvent`, ล็อกกล้องผ่าน `ViewportEvent.ComputeCameraAngles` + ตั้ง rotation จริงทุก tick, ซ่อน hotbar/XP/ชื่อไอเทม/crosshair, พัก input 10 tick หลังจบ, `KeyMapping.releaseAll()` ตอนเริ่มและจบ), `InputCapture`, `SessionHud` (คำใบ้ปุ่ม), `render/TrainingBlockRenderer` (แสงเรืองตามจังหวะ + แท่งรากสูงตามความคืบหน้า, ใช้ `RenderType.lightning()`), `ModKeyMappings` (Backspace = หยุดฝึก)
  - resources: model แบบ cross ใช้ texture `warped_roots` ของ vanilla ชั่วคราว, loot table (บล็อกว่าง, `perfect`/`normal` เป็นของชั่วคราว), lang `en_us`/`th_th`
  - เสียงชั่วคราว: `entity.warden.heartbeat` ตอนช่วงสว่าง, `block.grass.hit` ตอนกด
- หุ่นไม้: เพิ่มท่าปิดชุดด้วย `SPACE` แบบ Audition ลงสเปก (D29)

- **วิเคราะห์มินิเกมทั้งหมดและวางโครงสร้างก่อนเริ่มเขียนโค้ด** (D21–D28): ขอบเขตรอบแรก = หญ้า 3 ชนิด + เสื่อฟังลม + แท่นดับเทียน + หุ่นไม้, รอบสอง = หญ้าไฟ + หญ้าพันราก, `qi_station` ย้ายไป backlog
  - เขียน `design/core-mechanics.md` ใหม่: input profile, cue channel 4 แบบ, cue `AIM`/`SEQUENCE`, target set, เวลาจำกัด, บทลงโทษโดนเฉพาะคนเล่น (`burst`/`strikeVisual`), ความแข็งของบล็อก ~1.5 วินาที, พัก input หลังจบ, คำใบ้ตอนเริ่ม
  - สเปกใหม่: `candle_altar.md` (เทียนจริงที่ admin วาง), `wooden_dummy.md` เขียนใหม่เป็นหยงชุนแบบ Audition
  - ปรับสเปก: หญ้า (input profile, channel, คำใบ้, กฎการกดของหญ้าชีพจร), `wind_listener.md` (ผ้าปิดตาที่คนภายนอกเห็น, ลมจากด้านบน), `fire_root`/`tangled_root` เป็นรอบสอง (`tangled_root` ต้องออกแบบใหม่), `qi_station` เป็น backlog
  - ปรับ `architecture.md`, `multiplayer-performance.md` (เป้า 70 คน, `nextEventTick`, กรอง input, LOD, จำกัด particle, `/bhgame stress`), `roadmap.md` (แบ่ง P1a/P1b, เพิ่ม P8), `testing.md`, `overview.md`, `integrations/playeranim.md`, `adding-a-minigame.md`

- ผู้ใช้ตอบเรื่องท่าทาง: "Emotecraft หรือ vanilla" → อัปเดต `integrations/playeranim.md` (ความสัมพันธ์กับ Emotecraft, ตาราง fallback แบบ vanilla ต่อท่า) และ D18

- **เปลี่ยนทิศการออกแบบเป็น immersive** (D16–D20): training object เปลี่ยนจาก entity เป็นบล็อก + block entity ที่ไม่ tick, "Opponent" เปลี่ยนเป็น "Station", ตัดแถบ HP และตัวเลขทั้งหมด, cue แสดงบนตัววัตถุและเห็นเฉพาะคนเล่น, คนภายนอกเห็นแค่ท่าทาง (playerAnimator) กับผลลัพธ์, ทุบตรงๆ = บทลงโทษ (`BruteForce`)
  - เขียนใหม่: `design/core-mechanics.md`, `architecture.md` (เพิ่มหัวข้อ "การวาดภาพ"), สเปกทั้ง 8 เกม, `design/minigames/README.md`, `roadmap.md`
  - เปลี่ยน `qi_master.md` เป็น `qi_station.md` (แท่นประชันปราณ)
  - เพิ่ม `integrations/playeranim.md` (รวมความเสี่ยงกับ Epic Fight)
  - ปรับ `overview.md`, `multiplayer-performance.md`, `adding-a-minigame.md`, `testing.md`, `integrations/bhfx.md`, `decisions.md` (ขีดทิ้ง D5, D11)

- **P0 ตั้งโปรเจกต์:** ก๊อป gradle wrapper (Gradle 8.8) และ `.gitignore` จาก `../bhfx`, เขียน `build.gradle` (ForgeGradle 6, parchment, ตัดส่วน shader, publish และ copy ออก), `settings.gradle`, `gradle.properties` (`mod_id=bhgame`, `mod_group_id=net.deizyn`, `mod_version=0.1.0`), `mods.toml`, `pack.mcmeta`, `BhGame.java` และ lang ที่ว่าง
  - `./gradlew.bat build` ผ่าน (27 วินาที, มี warning เรื่อง deprecated `FMLJavaModLoadingContext.get()` และ `new ResourceLocation(String,String)` แบบเดียวกับ bhfx ไม่เป็นปัญหาบน Forge 47)
  - `runClient` ⏳ ยังไม่ได้ทดสอบ

- เพิ่ม `multiplayer-performance.md`: packet budget, ส่ง cue ล่วงหน้า (`leadTicks`), feedback ฝั่ง client, ตัดสินด้วย tick, `TrainingEntity` extends `Entity`, การลบ session ทุกทาง, rate limit และเกณฑ์ทดสอบโหลด
- ปรับ `architecture.md` (packet ใหม่ `S2CGaugeSync`, รวม `S2CCueResult` เข้ากับ `S2CSessionUpdate`, `/bhgame debug`), `core-mechanics.md` หัวข้อ 9, `decisions.md` D11–D14, `testing.md`, `roadmap.md` และ `adding-a-minigame.md` ให้ตรงกัน

- สรุปแนวคิดจากการคุยออกแบบ: ทุกเกมใช้ HP + Pattern, ปุ่มมีแค่ `LMB`/`RMB`/`SPACE`/WASD, admin วางเอง, ไม่มีเวลาเกิดใหม่และมานา
- สร้างโครง `docs/` ได้แก่ `README.md`, `overview.md`, `design/core-mechanics.md`, สเปก 8 เกมใน `design/minigames/`, `architecture.md`, `adding-a-minigame.md`, `integrations/bhfx.md`, `roadmap.md`, `decisions.md` และ `testing.md`
- ยังไม่มีโค้ด งานถัดไปคือ P0 (ตั้งโปรเจกต์) ต้องรอคำตอบเรื่อง mod id และ package ก่อน ดู [`roadmap.md`](roadmap.md)
