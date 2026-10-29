# สถาปัตยกรรมและโครงสร้างไฟล์

> สถานะ (2026-10-19): โค้ดถึง **P3** แล้ว (5 เกม: หญ้า 4 ชนิด + หุ่นไม้, `BeatTrack`, `SequenceTrack`, `Gauge`, `StationBlock`) ส่วนที่ยังเป็นแผนมีป้าย (P4) / (P6) / (P8) ในแผนผัง
> mod id `bhgame`, package root `net.deizyn.bhgame` (`<group>` ในแผนผังด้านล่าง = `net/deizyn`)

## เป้าหมายของโครงสร้าง

1. **เพิ่มเกมใหม่โดยไม่ต้องแตะ core:** เกมใหม่ส่วนใหญ่ = block class + pattern class + ลงทะเบียนหนึ่งบรรทัด
2. **client ไม่ต้องรู้จักเกม:** server ส่ง cue พร้อม `visual` hint และส่ง gauge มา renderer กลางของบล็อกกับ HUD กลางวาดเองได้ เกมใหม่ส่วนใหญ่จึง **ไม่ต้องเขียนโค้ดฝั่ง client เลย** ยกเว้นต้องการภาพเฉพาะของตัวเอง
3. **Immersion:** ข้อมูล gameplay ทั้งหมดไปถึงแค่ client ของคนเล่น คนภายนอกได้แค่ animation ของผู้เล่นกับผลลัพธ์ ดู [`design/core-mechanics.md`](design/core-mechanics.md) หัวข้อ 9
4. **แยก core / game / client / compat ให้ชัด:** `core` ห้าม import อะไรจาก `game` ส่วน `game` ใช้ได้เฉพาะ API ของ `core`
5. **ของเสริมเป็น soft dependency:** bhfx และ playerAnimator เรียกผ่าน `compat/` เท่านั้น mod ต้องเล่นได้โดยไม่มีทั้งสองตัว

## ทำไมเป็นบล็อก ไม่ใช่ entity

ดู D16 ใน [`decisions.md`](decisions.md) สรุป: ให้ความรู้สึกเหมือนดอกไม้หรือกล่องของ Minecraft, วางจาก inventory ได้, block entity ที่ไม่ tick แทบไม่กิน CPU แม้วางหลายร้อยอัน, และไม่ต้อง sync อะไรให้คนภายนอกระหว่างเล่น

## โครงสร้างโปรเจกต์

```
bhgame_mingyue/
├─ build.gradle, settings.gradle, gradle.properties   ← ตั้งตาม ../bhfx (Forge 47.4.0, parchment 2023.09.03-1.20.1)
├─ libs/                                              ← bhfx-<ver>.jar (แบบเดียวกับ BHSpells/libs)
├─ docs/                                              ← เอกสารนี้
└─ src/main/
   ├─ java/<group>/bhgame/
   │  ├─ BhGame.java                 @Mod entry, เรียก register ทุกอย่าง
   │  │
   │  ├─ registry/                   ลงทะเบียนของ Forge (DeferredRegister)
   │  │  ├─ ModBlocks.java
   │  │  ├─ ModBlockEntities.java
   │  │  ├─ ModItems.java            BlockItem ของทุกเกม
   │  │  ├─ ModSounds.java
   │  │  ├─ ModCreativeTabs.java
   │  │  └─ Minigames.java           ★ ที่เดียวที่ลงทะเบียนมินิเกม (ดูหัวข้อ "MinigameType")
   │  │
   │  ├─ core/                       ไม่ขึ้นกับเกมใดเกมหนึ่ง (common side)
   │  │  ├─ Tuning.java              ค่าคงที่ที่ไม่ต้องให้ admin ปรับ (FOCUS 40 tick, sweep 100 tick, จำนวนคนต่อ session สูงสุด 4)
   │  │  ├─ input/
   │  │  │  └─ InputProfile.java     record: keys, holdKeys, camera (LOCKED/FREE), crosshair, sendIdlePresses, aimPayload
   │  │  ├─ session/
   │  │  │  ├─ TrainingSession.java  สถานะหนึ่งรอบ: ผู้เข้าร่วม, HP (ใช้ร่วมกัน), mistakes, nextEventTick, BlockPos + dimension — implements PatternContext
   │  │  │  ├─ Participant.java      ผู้เล่นหนึ่งคนใน session: UUID, role (ลำดับที่เข้า), InputProfile ของ role, mistakes, ปุ่มที่ค้าง, rate limit
   │  │  │  ├─ SessionManager.java   map player→session, เริ่ม/เข้าร่วม (WAITING), tick ทุก session, ออกกลางคัน (D32), finish ทางเดียว
   │  │  │  ├─ SessionState.java     WAITING (รอคู่) → FOCUS (ตั้งสมาธิ) → RUNNING → FINISHED
   │  │  │  ├─ StressTest.java       /bhgame stress: session จำลองที่มี bot กดปุ่มสุ่ม ไม่ส่ง packet จริง
   │  │  │  └─ Outcome.java          PERFECT / SUCCESS / FAIL / ABORTED
   │  │  ├─ cue/
   │  │  │  ├─ InputKey.java         LMB, RMB, SPACE, UP, LEFT, DOWN, RIGHT
   │  │  │  ├─ CueType.java          TAP, HOLD, FEINT, AIM, SEQUENCE
   │  │  │  ├─ CueChannel.java       BLOCK, WORLD_ARROW, WORLD_TARGET, SPATIAL_AUDIO
   │  │  │  ├─ Cue.java              record: type, keys/sequence, windowTicks, damage, channel, visual, target index, sound
   │  │  │  ├─ CueVisual.java        enum ของ hint ที่ renderer รู้จัก (GLOW, COLOR_BLUE, COLOR_GOLD, SPARK, ARROW_*, ARROW_REVERSED, FLAME, GHOST_FLAME, WIND_*, ...)
   │  │  │  └─ (AIM judge)           ตรวจมุมและ raycast ของ cue AIM (P4 พร้อม candle_altar) — การตัดสิน TAP/FEINT อยู่ใน BeatTrack
   │  │  ├─ target/                  (P4 พร้อม candle_altar)
   │  │  │  ├─ TargetSet.java        รายการ BlockPos สูงสุด 32 ตัว อ้างด้วย index
   │  │  │  └─ TargetScanner.java    interface: scan(level, origin, player, config) → TargetSet (แต่ละเกมมีของตัวเอง)
   │  │  ├─ pattern/
   │  │  │  ├─ Pattern.java          interface: tick(ctx, now) / onInput(ctx, participant, key, press, now)
   │  │  │  ├─ PatternContext.java   สิ่งที่ pattern เข้าถึงได้: difficulty, hp, random, participants, scheduleAt, graceTicks, sendCues, hit, miss (ไม่เห็น ServerPlayer หรือ Level)
   │  │  │  ├─ Phases.java           byHp(ctx, 60, 25) / byDifficulty(ctx, table)
   │  │  │  ├─ CompositePattern.java (P8) รวมหลาย pattern เข้าด้วยกัน (สำหรับหญ้าผสมคุณสมบัติในอนาคต)
   │  │  │  └─ common/               ชิ้นส่วนที่ใช้ซ้ำ
   │  │  │     ├─ BeatTrack.java           ตัวตัดสิน cue แบบมีกรอบเวลา (TAP/FEINT, role mask, ช่วงมืด, pingGrace) + ส่งเป็นชุด (pulse_grass, twin_grass, polarity)
   │  │  │     │                          มี Listener ให้เกมเลือกบทลงโทษตามปุ่มที่ผิด (polarity) หรือไม่ลด HP ตอน hit (thunder)
   │  │  │     ├─ SequenceTrack.java       ตัวตัดสินชุดลูกศรแบบ Audition + ท่าปิดชุด SPACE (wooden_dummy)
   │  │  │     └─ TargetPattern.java       (P4) เลือกเป้าจาก TargetSet มาเป็น AIM cue (candle_altar)
   │  │  ├─ gauge/
   │  │  │  └─ Gauge.java            ค่า 0..1 + ratePerTick, ส่งเฉพาะตอนเปลี่ยน + resync ทุก gaugeResyncTicks ระหว่างขยับ, flag PROGRESS = ใช้เป็นความคืบหน้าบนบล็อก
   │  │  ├─ block/
   │  │  │  ├─ TrainingBlock.java        ฐานของทุกเกม: คลิกขวาเริ่ม, ทุบตรงๆ = ลงโทษ, ทนระเบิด, ลูกสูบดันไม่ได้
   │  │  │  ├─ TrainingBlockEntity.java  เก็บ NBT config + UUID ของคนที่ใช้อยู่ **ไม่ tick**
   │  │  │  ├─ HerbBlock.java            ใช้ครั้งเดียว: ชนะ = drop + ลบบล็อก, แพ้/ทุบ = ลบบล็อก
   │  │  │  └─ StationBlock.java         ถาวร: ไม่มีอะไรต้องรีเซ็ต, ทุบไม่พัง (ฟาดกลับ), drop ของเฉพาะเมื่อ admin ตั้ง loot table
   │  │  ├─ outcome/
   │  │  │  ├─ Penalty.java          interface apply(ServerPlayer, BlockPos)
   │  │  │  ├─ Penalties.java        factory: effect, damage, fire, freeze, knockback, burst (ระเบิดปลอม), strikeVisual (ฟ้าผ่าปลอม), all(...) — โดนเฉพาะคนเล่น
   │  │  │  ├─ Rewards.java          โยน loot table perfect / normal
   │  │  │  └─ Stats.java            log + scoreboard bhg_perfect / bhg_success / bhg_fail
   │  │  ├─ pose/
   │  │  │  └─ PlayerPose.java       enum ท่าทางที่คนภายนอกเห็น (PULL_HERB, WING_CHUN, MEDITATE, QI_FLICK) + ของประกอบบนตัว (เช่น BLINDFOLD)
   │  │  └─ config/
   │  │     └─ BhGameConfig.java     ForgeConfigSpec server + client, คัดลอกค่าลง static field ตอนโหลด (hot path ไม่แตะ spec)
   │  │
   │  ├─ game/                       ★ หนึ่งโฟลเดอร์ต่อหนึ่งเกม
   │  │  ├─ pulse_grass/
   │  │  │  ├─ PulseGrassBlock.java     extends HerbBlock: ค่าเริ่มต้น, penalty, input profile
   │  │  │  └─ PulseGrassPattern.java   implements Pattern ใช้ BeatTrack
   │  │  ├─ twin_grass/                 2 คน: requiredPlayers() = 2, inputProfile(role)
   │  │  ├─ polarity_grass/ ...
   │  │  ├─ thunder_grass/ ...
   │  │  ├─ wooden_dummy/ ...
   │  │  ├─ wind_listener/ ...
   │  │  ├─ candle_altar/
   │  │  │  ├─ CandleAltarBlock.java
   │  │  │  ├─ CandleAltarPattern.java
   │  │  │  └─ CandleScanner.java       implements TargetScanner: หาเทียน vanilla ที่ยังไม่ติดไฟ + ตรวจว่ามองเห็น
   │  │  └─ <game_id>/ ...
   │  │
   │  ├─ network/
   │  │  ├─ BhGameNetwork.java       SimpleChannel + ลงทะเบียน packet
   │  │  ├─ S2CSessionStart.java     game id (int), BlockPos, InputProfile, TargetSet, gauges เริ่มต้น → คนเล่นคนเดียว
   │  │  ├─ S2CCue.java              cue ล่วงหน้าได้หลายตัว (cue id, opensInTicks, ...)  → คนเล่นคนเดียว
   │  │  ├─ S2CSessionUpdate.java    HP, mistakes, ผล HIT/MISS ของ cue                    → คนเล่นคนเดียว
   │  │  ├─ S2CGaugeSync.java        gauge: value + ratePerTick                          → คนเล่นคนเดียว
   │  │  ├─ S2CSessionEnd.java       outcome                                              → คนเล่นคนเดียว
   │  │  ├─ S2CPlayerPose.java       player id + PlayerPose (เริ่ม/เปลี่ยน/จบ)             → คนที่ track ผู้เล่น (ไม่รวมตัวเอง)
   │  │  ├─ C2SInput.java            key, PRESS/RELEASE, (ไม่บังคับ) yaw+pitch — มี flag บอกว่ามี payload หรือไม่ ตั้งแต่ P1
   │  │  └─ C2SAbort.java            ※ กฎการส่ง packet ทั้งหมดอยู่ใน multiplayer-performance.md
   │  │
   │  ├─ client/                     @OnlyIn(Dist.CLIENT) ทั้งหมด
   │  │  ├─ ClientSession.java       สำเนาสถานะ session ฝั่ง client (มีได้ตัวเดียว)
   │  │  ├─ input/
   │  │  │  ├─ InputLock.java        ล็อกตาม InputProfile (เดินเสมอ, กล้องถ้า LOCKED, ตี/ใช้ของ/กระโดด) + พักรับ input ของโลก 10 tick หลังจบ
   │  │  │  ├─ InputCapture.java     แปลง keybind → InputKey → กรองตาม profile (holdKeys, sendIdlePresses) → C2SInput
   │  │  │  └─ ModKeyMappings.java   keybind ยกเลิก (Backspace)
   │  │  ├─ camera/
   │  │  │  └─ FocusCamera.java      หันกล้องเข้าหาวัตถุอย่างนุ่มนวลตอนเริ่ม แล้วล็อกไว้
   │  │  ├─ render/                       หนึ่ง renderer ต่อหนึ่ง channel
   │  │  │  ├─ TrainingBlockRenderer.java  BlockEntityRenderer วาดเฉพาะเมื่อ ClientSession ชี้มาที่บล็อกนี้ แยกตาม channel ของเกม; BLOCK = แสงสีตาม CueVisual + แท่งราก
   │  │  │  ├─ WorldTargetRenderer.java    channel WORLD_TARGET: เปลวไฟปลอมบนเป้าใน TargetSet (RenderLevelStageEvent + particle)
   │  │  │  ├─ CueVisualRenderer.java      วาด CueVisual แต่ละแบบ (ใช้ร่วมกันทุก renderer)
   │  │  │  ├─ PoseLayer.java              RenderLayer ของผู้เล่น: ของประกอบบนตัวตาม PlayerPose (ผ้าปิดตา)
   │  │  │  └─ <game_id>/                  ภาพเฉพาะเกม (ถ้ามี)
   │  │  ├─ hud/
   │  │  │  ├─ SessionHud.java       IGuiOverlay แบบน้อยที่สุด + ซ่อน HUD vanilla ระหว่าง session
   │  │  │  └─ widget/               KeyHint (เปิดปิดได้), BrushCrosshair (candle_altar), BrushGauge (ใช้เมื่อจำเป็น), Blackout (wind_listener)
   │  │  ├─ pose/
   │  │  │  └─ PoseAnimations.java   รับ S2CPlayerPose แล้วเล่นท่าทางผ่าน compat/playeranim (fallback: แกว่งแขน vanilla)
   │  │  └─ sound/
   │  │     └─ CueSounds.java        channel SPATIAL_AUDIO + เสียง cue ของทุก channel (เฉพาะคนเล่น)
   │  │
   │  ├─ compat/
   │  │  ├─ bhfx/                    ดู integrations/bhfx.md
   │  │  │  ├─ BhfxCompat.java       ที่ game/core เรียก ห้าม import bhfx
   │  │  │  ├─ BhfxLoadedBridge.java ที่เดียวที่เรียก BHFx.*
   │  │  │  └─ client/ ...
   │  │  └─ playeranim/              ดู integrations/playeranim.md
   │  │     ├─ PlayerAnimCompat.java      เช็กว่าโหลดอยู่ไหม ห้าม import playerAnimator
   │  │     └─ PlayerAnimBridge.java      ที่เดียวที่เรียก API ของ playerAnimator
   │  │
   │  └─ command/
   │     └─ BhGameCommand.java       /bhgame list | abort <player> | stats <player> | reset <pos> | debug | stress <n> (dev เท่านั้น)
   │
   └─ resources/
      ├─ META-INF/mods.toml          bhfx และ playeranimator mandatory=false
      ├─ assets/bhgame/
      │  ├─ blockstates/, models/block/, models/item/   โมเดลบล็อกปกติ (คนภายนอกเห็นแค่นี้)
      │  ├─ textures/block/<game_id>*.png
      │  ├─ textures/gui/hud.png     sprite sheet ของ HUD (คำใบ้ปุ่ม, ลายพู่กัน) ไฟล์เดียว
      │  ├─ player_animation/*.json  ท่าทางของผู้เล่น (รูปแบบของ playerAnimator)
      │  ├─ lang/en_us.json, th_th.json
      │  ├─ sounds.json              (เสียงของ wind_listener ห้ามมี subtitle)
      │  └─ sounds/<game_id>/*.ogg
      └─ data/bhgame/loot_tables/
         ├─ blocks/<game_id>.json    ว่าง (ทุบแล้วไม่ drop)
         └─ minigames/<game_id>/{perfect,normal}.json
```

## Minigames — จุดลงทะเบียนเดียว

เกมหนึ่งเกม = **block class ที่อธิบายเกม** (override เมธอดของ `TrainingBlock`/`HerbBlock`) + pattern class + **หนึ่งบรรทัด** ใน `registry/Minigames.java` (D33):

```java
public static final RegistryObject<PulseGrassBlock> PULSE_GRASS =
        register("pulse_grass", () -> new PulseGrassBlock(HerbBlock.herbProperties()));
```

`Minigames.register` สร้าง `Block`, `BlockItem`, รายการในแท็บ creative และใส่บล็อกลง `BlockEntityType` กลางให้อัตโนมัติ ชื่อ loot table, คำใบ้ และข้อความผลลัพธ์ได้จาก game id

| เมธอดของ block | ค่าเริ่มต้น | ใช้ทำอะไร |
| :--- | :--- | :--- |
| `inputProfile()` / `inputProfile(role)` | (บังคับ) / เท่ากับ `inputProfile()` | ปุ่มของแต่ละ role |
| `requiredPlayers()` | 1 | มากกว่า 1 = คนแรกรอ (WAITING) จนครบ |
| `createPattern(ctx)` | (บังคับ) | สร้างตอนผู้เล่นครบ |
| `defaultMaxHp()`, `defaultMaxMistakes()` | (บังคับ), 3 | admin ทับได้ด้วย NBT |
| `smallPenalty()`, `bigPenalty()` | (บังคับ) | จาก `Penalties` เท่านั้น |
| `startReach()`, `startHeightTolerance()` | 3 / 1.5 (หญ้า 1.75 / 1) | ระยะเริ่ม (D30) |
| `pose()` | หญ้า = `PULL_HERB` | ท่าที่คนภายนอกเห็น |
| `onWin` / `onFail` / `onAbandoned` | หญ้า: drop + ลบ / ลงโทษทุกคน + ลบ / ลบ | ผลต่อบล็อก |

## Lifecycle ของ session

```
ผู้เล่นคลิกขวา TrainingBlock
  → (server) TrainingBlock.use → ตรวจ: ผู้เล่นไม่มี session อื่น, อยู่ในระยะเริ่ม (D30)
  → บล็อกมี session ที่ WAITING และยังไม่ครบ → เข้าร่วมเป็น role ถัดไป / มีคนเล่นอยู่ → ข้อความ busy
  → เกมหลายคนและยังไม่ครบ → WAITING (คุกเข่ารอ, S2CSessionStart แบบ waiting) รอได้ partnerWaitTicks
  → (ถ้าเกมมี TargetScanner) สแกนหาเป้า → ไม่พอ = ไม่เริ่ม + ข้อความในเรื่อง
  → SessionManager.start → สร้าง TrainingSession + Pattern จาก MinigameType
  → S2CSessionStart (คนเล่น: InputProfile + TargetSet) + S2CPlayerPose (คนรอบข้าง)
  → (client คนเล่น) InputLock ตาม profile, FocusCamera (ถ้า LOCKED), ซ่อน HUD vanilla, FOCUS 2 วินาที + คำใบ้ใน action bar
  → RUNNING: server tick → ถ้าถึง nextEventTick: Pattern.tick(ctx) → ctx.emit(cue, เปิดในอีก leadTicks) → S2CCue (ส่งล่วงหน้า)
             (client คนเล่น) renderer ของ channel นั้นวาด cue + CueSounds
             client กดปุ่ม → feedback ทันทีฝั่ง client → กรองตาม profile → C2SInput (AIM: แนบ yaw/pitch)
             → (server) ตรวจ + rate limit → CueJudge → HIT: HP−damage / MISS: mistake+1 + penalty เล็ก
             → S2CSessionUpdate (รวมผล HIT/MISS)
  → จบ (HP=0 / mistakes ครบ / gauge แพ้ / หมด TimeLimit / ยกเลิก)
  → Outcome → Rewards หรือ Penalty ใหญ่ → Stats → บล็อก: ลบ (herb) หรือรีเซ็ต (station)
  → S2CSessionEnd (คนเล่น: InputLock ปิด + พัก input ของโลก 10 tick, ข้อความผลใน action bar) + S2CPlayerPose(NONE) (คนรอบข้าง)

ผู้เล่น survival ทุบ TrainingBlock ตรงๆ
  → บล็อกต้องทุบประมาณ 1.5 วินาที (ไม่แตกในคลิกเดียว), ตอนเริ่มทุบขึ้นข้อความเตือนใน action bar
  → TrainingBlock.playerWillDestroy / onDestroyedByPlayer → Penalty ใหญ่ (herb) หรือเล็ก (station)
  → herb: ลบบล็อกโดยไม่ drop / station: ยกเลิกการทุบ (BruteForce=KEEP)
  → Stats (outcome = FAIL, บันทึกว่า brute force)
```

## การวาดภาพ

| ส่วน | ใครเห็น | วาดด้วย |
| :--- | :--- | :--- |
| ตัวบล็อกปกติ | ทุกคน | block model JSON ธรรมดา (แบบ cross เหมือนดอกไม้ หรือโมเดลกล่อง) ไม่ใช้ BER ตอนไม่มีคนเล่น |
| channel `BLOCK` (แสงเรือง, ออร่าสี, ประกาย, หญ้าค่อยๆ ขึ้นจากดิน) | คนเล่นเท่านั้น | `TrainingBlockRenderer` (BlockEntityRenderer) วาดเฉพาะเมื่อ `ClientSession` ชี้มาที่ตำแหน่งนี้ ใช้ `RenderType.eyes` / emissive สำหรับแสง และ particle ฝั่ง client |
| channel `WORLD_ARROW` (แถวลูกศรของหุ่นไม้) | คนเล่นเท่านั้น | **วาดบนจอ** ด้วย `client/ArrowRowHud` (เรียกจาก `SessionHud`) เพราะในโลกโดนหุ่นบัง (D39) ชื่อ channel คงไว้เพื่อไม่ต้องเปลี่ยน protocol |
| channel `WORLD_TARGET` (เปลวไฟบนเทียนจริง) | คนเล่นเท่านั้น | `WorldTargetRenderer` วาดเปลวไฟปลอม + particle ที่ตำแหน่งใน `TargetSet` **ไม่แตะ blockstate ของเทียน** |
| channel `SPATIAL_AUDIO` | คนเล่นเท่านั้น | `CueSounds` เล่นเสียง mono ที่ตำแหน่งรอบตัวผู้เล่น |
| HUD บนจอ | คนเล่นเท่านั้น | `IGuiOverlay` + `GuiGraphics` (`blit` จาก `textures/gui/hud.png`, `fill`) แค่คำใบ้ปุ่ม (เปิดปิดได้), crosshair ลายพู่กัน (ดับเทียน) และแถบลายพู่กันเมื่อจำเป็น ซ่อน HUD vanilla ผ่าน `RenderGuiOverlayEvent.Pre` |
| จอมืด (`wind_listener`) | คนเล่นเท่านั้น | overlay `registerAboveAll` + `fill` สีดำ |
| feedback ทั้งจอ (สั่น, flash, vignette) | คนเล่นเท่านั้น | bhfx `ScreenPulse` (fallback: `fill` สีโปร่งใสวาบเดียว) |
| ท่าทางของผู้เล่น | คนภายนอก | playerAnimator ผ่าน `compat/playeranim` (fallback: ท่า vanilla ตาราง [`integrations/playeranim.md`](integrations/playeranim.md)) ไม่เล่นท่าถ้าผู้เล่นอยู่ไกลเกิน 32 บล็อก |
| ของประกอบบนตัว (ผ้าปิดตา) | คนภายนอก | `PoseLayer` (RenderLayer ของ player renderer) จากสถานะ pose |
| บทลงโทษและผลลัพธ์ | ทุกคน | `burst` / `strikeVisual` เป็นเอฟเฟกต์ระยะใกล้ (ไม่ใช่ระเบิดหรือฟ้าผ่าจริง), ไฟและ effect ของ vanilla, บล็อกหาย, ของเด้งออกมา |

- HUD ทำ animation ด้วย `PoseStack` และ interpolate ด้วย `partialTick` ทุกเฟรม
- ฟอนต์ vanilla แสดงภาษาไทยได้ แต่สระและวรรณยุกต์บางตัววางเพี้ยน ข้อความบนจอจึงควรสั้น และใช้ภาพเป็นหลัก
- P1 ใช้ภาพแบบง่ายก่อน (บล็อกเปลี่ยนสีด้วย BER, ไม่มี texture HUD) ส่วนภาพจริงทำใน P6 widget แยกไว้จึงเปลี่ยนได้ทีละชิ้น

## Server-authoritative, ping และ performance

- `Pattern` และ `CueJudge` อยู่ฝั่ง server เท่านั้น client ไม่ตัดสินอะไรเอง
- `C2SInput` ไม่ส่ง timestamp ของ client (เชื่อไม่ได้) server ใช้เวลาที่ packet มาถึง แล้วขยายกรอบเวลาด้วย `pingGrace` = min(`player.latency`, `maxPingGraceMs`)
- HOLD: client ส่ง `PRESS` และ `RELEASE` server เก็บสถานะว่าปุ่มไหนค้างอยู่ใน `TrainingSession`
- รายละเอียดเรื่อง packet budget, lead time, ความลื่นไหล, CPU, memory และการทดสอบโหลด อยู่ใน [`multiplayer-performance.md`](multiplayer-performance.md)

## จุดขยายสำหรับเกมในอนาคต

| อยากได้ | ทำที่ไหน |
| :--- | :--- |
| เกมใหม่ที่ใช้ cue, channel และ input profile ที่มีอยู่ | `game/<id>/` + ลงทะเบียนใน `Minigames` เท่านั้น |
| เกมที่เป้าอยู่ในโลก (แบบดับเทียน) | เขียน `TargetScanner` ของเกมนั้น ใช้ channel `WORLD_TARGET` และ cue `AIM` ที่มีอยู่แล้ว |
| คำใบ้ภาพแบบใหม่ (เช่น สีใหม่) | เพิ่มใน `CueVisual` + วาดใน `CueVisualRenderer` |
| gauge แบบใหม่ | ไม่ต้องแก้ core ใช้ `Gauge` ตั้งชื่อและสีเอง |
| ท่าทางผู้เล่นแบบใหม่ | เพิ่มใน `PlayerPose` + ไฟล์ใน `player_animation/` |
| หญ้าผสมคุณสมบัติ | (P8) `CompositePattern` ที่รวม pattern เดิม เช่น gauge ของ thunder + สีของ polarity |
| cue ชนิดใหม่ หรือ channel ใหม่ | เพิ่มใน `CueType` / `CueChannel` + กฎตัดสินใน `CueJudge` + renderer ของ channel **ถือว่าเป็นการเปลี่ยน core** ต้องบันทึกใน [`decisions.md`](decisions.md) (`AIM`, `SEQUENCE` และทั้ง 4 channel มีตั้งแต่แรกแล้ว ตาม D24) |
| หลายผู้เล่นใน session เดียว (co-op, PvP) | **มีแล้ว** (D31): `requiredPlayers()` + `inputProfile(role)` และ cue ที่มี role mask ดูตัวอย่าง `game/twin_grass/` |
| Combatant (entity ที่ตีได้และตีกลับ) | **ระบบแยก** ใน `combatant/` ไม่ใช้ session engine ดู backlog ใน [`design/minigames/README.md`](design/minigames/README.md) |

## กฎของโค้ด

- `core/` ห้าม import `game/`, `client/`, `compat/` ส่วนตัวที่โหลดจริง หรือ `net.deizyn.bhfx` / playerAnimator
- `game/` ห้าม import `client/` ถ้าเกมต้องการภาพเฉพาะ ให้วางใน `client/render/<game_id>/`
- **ห้ามส่งข้อมูล gameplay (cue, HP, gauge) ให้ใครนอกจากคนเล่น** และห้ามใช้ blockstate แสดง cue (blockstate sync ให้ทุกคนและทำให้ chunk rebuild)
- ค่าตัวเลขของเกม (กรอบเวลา, จังหวะ) อยู่ใน pattern class ของเกมนั้น และต้องตรงกับสเปกใน `docs/design/minigames/<game_id>.md`
- ทุกกฎใน [`multiplayer-performance.md`](multiplayer-performance.md) ถือเป็นกฎของโค้ดด้วย (ไม่มี packet รายtick, ไม่สร้าง object ใน hot path, session ไม่เก็บ reference ของ player/block entity)
- ทุกข้อความที่ผู้เล่นเห็นใช้ translation key (`bhgame.<game_id>.*`, `bhgame.hud.*`) มีทั้ง `en_us` และ `th_th`
