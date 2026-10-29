# Roadmap

หลักคือ **ทำเกมเดียวให้ครบทั้งเส้นทาง (vertical slice) ก่อน ทดสอบจังหวะบนเซิร์ฟจริง แล้วค่อยขยาย** ดูเหตุผลใน [`decisions.md`](decisions.md) D7
ขอบเขตรอบแรกดู D21 และ [`design/minigames/README.md`](design/minigames/README.md)

## คำถามที่รอตอบ

- [x] **mod id, ชื่อ mod และ package:** `bhgame` / `net.deizyn.bhgame` (2026-10-19, D15)
- [x] **entity หรือบล็อก:** บล็อก + แสดงผลแบบ immersive (2026-10-19, D16, D17)
- [x] **ท่าทางผู้เล่น:** Emotecraft/playerAnimator หรือ vanilla (2026-10-19, D18)
- [x] **ขอบเขตรอบแรก, เทียน, หุ่นไม้:** (2026-10-19, D21–D23)
- [x] **หุ่นไม้:** มีท่าปิดชุดด้วย `SPACE` แบบ Audition (2026-10-19, D29)
- [ ] **ชื่อคำสั่ง:** `/bhgame` หรือชื่ออื่น
- [ ] **โมเดล:** block model JSON ธรรมดาพอไหม หรือต้องการ GeckoLib
- [ ] **ใครทำ texture, model, ท่าทางผู้เล่น และเสียง**
- [ ] **Epic Fight:** ผู้เล่นอยู่ใน battle mode บ่อยไหม (ดู [`integrations/playeranim.md`](integrations/playeranim.md))
- [ ] **ภาษาในเกม:** ใช้ `th_th` เป็นหลักและมี `en_us` สำรอง?

## P0 — ตั้งโปรเจกต์
- [x] ก๊อป gradle wrapper, `build.gradle`, `settings.gradle` และ `gradle.properties` จาก `../bhfx` (ตัดส่วน shader, glslang, publish และ `copyToBHSpells` ออก) (2026-10-19)
- [x] `mods.toml`, `pack.mcmeta`, `BhGame.java` (@Mod) ที่ว่างเปล่า, `lang/en_us.json` และ `th_th.json` ที่ว่าง (2026-10-19)
- [x] `./gradlew.bat build` ผ่าน (2026-10-19)
- [x] `runClient` โหลดถึงหน้าแรกได้ ไม่มี error หรือ model/texture หาย (2026-10-19, ตรวจจาก `run/logs/latest.log` ยังไม่ได้เข้าโลก)

## P1a — Vertical slice: `pulse_grass` ใน singleplayer
เป้าหมาย: เล่นหญ้าชีพจรได้ครบหนึ่งรอบ หน้าตาไม่ต้องสวย แต่ **กฎ immersion และรูปแบบ packet ต้องถูกตั้งแต่แรก**

> **สถานะ (2026-10-19):** 🔨 เขียนโค้ดครบทุกข้อด้านล่างแล้ว, `./gradlew.bat build` ผ่าน, `runClient` โหลดได้ **⏳ ยังไม่ได้ทดสอบเล่นในเกม** ข้อจะติ๊กเมื่อผ่าน checklist P1 ใน [`testing.md`](testing.md)
- [ ] `TrainingBlock` / `HerbBlock` / `TrainingBlockEntity` (ไม่ tick, model cross แบบง่ายจาก texture หญ้า vanilla)
- [ ] ความแข็ง ~1.5 วินาที + ข้อความเตือนตอนเริ่มทุบ + ทุบตรงๆ ใน survival = บทลงโทษใหญ่และหญ้าพัง, creative ทุบได้ปกติ
- [ ] `SessionManager` + `TrainingSession` (FOCUS → RUNNING → FINISHED) + `nextEventTick`
- [ ] `InputProfile` (record เต็มรูปแบบตาม D24 แม้ P1 ใช้แค่ `LMB`)
- [ ] network: `S2CSessionStart` (มี InputProfile + TargetSet ว่าง), `S2CCue` (ส่งล่วงหน้า + ส่งจังหวะเป็นชุด), `S2CSessionUpdate`, `S2CSessionEnd`, `C2SInput` (มี flag payload ตั้งแต่แรก) + rate limit + validation
- [ ] ลบ session ครบทุกทาง (logout, ตาย, เปลี่ยนมิติ, บล็อกถูกลบ, chunk unload, server หยุด) + sweep ทุก 100 tick
- [ ] `InputLock` + `InputCapture` ตาม profile + reset ปุ่มตอนจบ + พัก input ของโลก 10 tick
- [ ] `FocusCamera` + ซ่อน HUD vanilla + คำใบ้ใน action bar
- [ ] `TrainingBlockRenderer`: แสงเรืองตามจังหวะ + หญ้ายกขึ้นตามความคืบหน้า **เฉพาะบน client ของคนเล่น**
- [ ] เสียงจังหวะชั่วคราว: `entity.warden.heartbeat` ของ vanilla
- [ ] feedback ตอนกดฝั่ง client ทันที, animation ใช้ `partialTick`
- [ ] `PulseGrassPattern` ตามสเปก, Poison, drop ของจาก loot table, ข้อความผลใน action bar (ทำ `Pattern` interface แบบเล็ก 2 method ไว้แล้ว เพราะ session ต้องเรียกผ่านอะไรสักอย่าง ส่วน `PatternContext`/`Phases`/composite ยังเป็นงาน P2)
- [ ] log + scoreboard แบบง่าย
- [ ] BlockItem ในแท็บ creative

## P1b — Vertical slice บน dedicated server

> **สถานะ (2026-10-19):** 🔨 เขียนโค้ด 2 ข้อแรกแล้ว, build ผ่าน, `runClient` โหลดได้ **⏳ ยังไม่ได้ทดสอบในเกม** ข้อที่สามเป็นการทดสอบที่ผู้ใช้ต้องทำ

- [ ] ท่าทางผู้เล่นแบบ vanilla fallback (`PULL_HERB`) + `S2CPlayerPose` ส่งให้คนที่ track ผู้เล่น (รวมตัวเองสำหรับมุมมองบุคคลที่สาม) และคนที่เพิ่งเริ่ม track 🔨
- [ ] `/bhgame debug` (นับ session และ packet) 🔨
- [ ] **ทดสอบบน dedicated server + client 2 คน (คนเล่น + คนดู) + จำลอง ping 0/100/200ms** ดู [`testing.md`](testing.md)
  - ผู้ใช้ต้องตั้ง `eula=true` เองใน `run/eula.txt` และ `online-mode=false` สำหรับ client สองตัวในเครื่องเดียว และติดตั้ง clumsy

## P2a — แยก core ให้เป็นระบบ
หลังจากเห็นของจริงแล้ว ค่อยแยกส่วนที่ใช้ซ้ำ

> **สถานะ (2026-10-19):** 🔨 เขียนโค้ดครบทุกข้อด้านล่างแล้ว, build ผ่าน, unit test ของ `BeatTrack` 6 ข้อผ่าน **⏳ ยังไม่ได้ทดสอบในเกม** (ดู checklist P2 ใน [`testing.md`](testing.md))
- [x] `Pattern` / `PatternContext` / `Phases` (2026-10-19)
- [x] `BeatTrack` = ตัวตัดสิน cue แบบกรอบเวลา + `pingGrace` (แทน `CueJudge` สำหรับ TAP/FEINT, D34) และ `BhGameConfig` (server + client) (2026-10-19)
- [x] `Minigames.register` แบบหนึ่งบรรทัด ไม่ใช้ builder (D33) และย้าย `pulse_grass` มาใช้ (2026-10-19)
- [x] `InputCapture` ครบทุกปุ่ม + `holdKeys` (server เก็บปุ่มที่ค้างใน `Participant`) + `sendIdlePresses` + `camera=FREE` + `aimPayload` (ยังไม่มีทิศเฉียง) (2026-10-19)
- [x] `Penalties` เพิ่ม `damage`, `fire`, `burst`, `strikeVisual` (2026-10-19)
- [x] `/bhgame stress <n> [game]` และ `/bhgame stress stop` (2026-10-19) ยังไม่ได้วัดด้วย spark
- [x] session รองรับผู้เข้าร่วมหลายคน (D31, D32): `Participant` + role, `SessionState.WAITING`, cue มี role mask, ออกกลางคันโดนลงโทษ (2026-10-19)
- [x] `twin_grass` เป็นเกมทดสอบ session หลายคน (ดึงมาจาก P8, D35) (2026-10-19)
- [ ] วัด `/bhgame stress 70` และ `150` ด้วย spark

## P2b — ส่วน core ที่ทำพร้อมเกมแรกที่ใช้ (D34)
- [x] `Gauge` + `S2CGaugeSync` (`value + ratePerTick`) → พร้อม `thunder_grass` (P3) (2026-10-19)
- [ ] `TargetSet` + `TargetScanner` + ตัดสิน `AIM` → พร้อม `candle_altar` (P4)
- [x] `SEQUENCE` (`SequenceTrack` + `CueData.steps`) → พร้อม `wooden_dummy` (P3) (2026-10-19)
- [ ] bridge ของ playerAnimator + ท่า vanilla fallback ครบ 4 ท่า, ทดสอบร่วมกับ Epic Fight → ก่อน P6 (ต้องมีไฟล์ท่าก่อน)
- [ ] `CompositePattern` → พร้อมหญ้าผสมคุณสมบัติ (backlog)

## P3 — หญ้าที่เหลือ + หุ่นไม้

> **สถานะ (2026-10-19):** 🔨 เขียนโค้ดครบทุกข้อแล้ว, build ผ่าน, unit test 16 ข้อผ่าน **⏳ ยังไม่ได้ทดสอบในเกม** และยังไม่ได้เปิดเกมเช็กว่าโมเดล/ภาพโหลดได้ (ดู checklist P3 ใน [`testing.md`](testing.md))
- [x] `polarity_grass` (`RMB`, `FEINT`, บทลงโทษตามปุ่มที่ผิด, FAIL ตามธาตุล่าสุด) (2026-10-19)
- [x] `thunder_grass` (ค้าง `LMB` + `SPACE`, gauge `pull`, `strikeVisual`) (2026-10-19)
- [x] `StationBlock` (ไม่ต้องรีเซ็ต, ทุบไม่พังแต่ฟาดกลับ) — บล็อกสองชั้นเลื่อนไป P6 พร้อมโมเดลจริง, NBT `BruteForce` ไป P5 (D36) (2026-10-19)
- [x] `wooden_dummy` (`SEQUENCE`, channel `WORLD_ARROW`, ลูกศรกลับทิศ, ท่าปิดชุด `SPACE`) (2026-10-19)

## P4 — ฟังลม + ดับเทียน
- [ ] `wind_listener` (channel `SPATIAL_AUDIO`, overlay มืด, ผ้าปิดตาผ่าน `PoseLayer`, ลมจากด้านบนใน phase 3)
- [ ] `candle_altar` (`CandleScanner`, cue `AIM`, channel `WORLD_TARGET`, `camera=FREE`, crosshair ลายพู่กัน)

## P5 — เครื่องมือ admin
- [ ] NBT config ครบตาราง [`design/core-mechanics.md`](design/core-mechanics.md) หัวข้อ 11 (+ `Radius` ของดับเทียน)
- [ ] loot table เริ่มต้นของทุกเกม
- [ ] `/bhgame list | abort | stats | reset`
- [ ] scoreboard + log
- [ ] คำใบ้และข้อความผลใน `en_us.json` และ `th_th.json`

## P6 — ความสวยงาม
- [ ] โมเดลและ texture ของทุกบล็อก
- [ ] ภาพ cue จริงของทุก channel (แสง, ออร่า, ประกาย, ลายพู่กันลูกศร, เปลวไฟเทียน)
- [ ] ท่าทางผู้เล่น 4 ท่า (`player_animation/*.json` รูปแบบ Emotecraft) + โมเดลผ้าปิดตา
- [ ] เสียง (รวมไฟล์ mono สำหรับ `wind_listener`)
- [ ] bridge ของ bhfx ดู [`integrations/bhfx.md`](integrations/bhfx.md)
- [ ] `hud.png` สำหรับคำใบ้ปุ่มและ crosshair ลายพู่กัน

## P7 — ปล่อยใช้งาน (รอบแรก)
- [ ] ทดสอบบนเซิร์ฟจริงกับผู้เล่นหลายคน ทุกเกม
- [ ] ทดสอบโหลดผ่านทุกข้อใน [`testing.md`](testing.md) หัวข้อ "ทดสอบโหลด"
- [ ] ทดสอบว่าเล่นได้โดยไม่มี bhfx และไม่มี playerAnimator
- [ ] เขียนคู่มือ admin สั้นๆ (วิธีวาง, จัดลานเทียน, ตั้งค่า NBT, ดูสถิติ)
- [ ] เปิด spark ช่วงจัด event ครั้งแรกเพื่อเก็บตัวเลขจริง

## P8 — รอบสอง
- [ ] `fire_root`
- [ ] `water_yoke` หาบน้ำแบบของแบก เดินไปไหนก็ได้ (📝 ร่างสเปก 2026-10-22, D46, D48) ต้องเพิ่มบล็อกแบบ Carry และ `movement=FREE` ใน core ดู [`design/minigames/water_yoke.md`](design/minigames/water_yoke.md)
- [ ] ออกแบบ `tangled_root` ใหม่ให้ไม่ซ้ำกับหุ่นไม้ แล้วทำ
- [x] ~~`twin_grass`~~ ย้ายไปทำใน P2a เป็นเกมทดสอบ session หลายคน (D35)

## Backlog
ดู [`design/minigames/README.md`](design/minigames/README.md) หัวข้อ Backlog
