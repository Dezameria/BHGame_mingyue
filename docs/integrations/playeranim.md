# ท่าทางของผู้เล่น (playerAnimator)

> สถานะ: **แผน** (2026-10-19) ยังไม่ได้เชื่อมจริง

## ทำไม

ตามหลัก immersion ([`../design/core-mechanics.md`](../design/core-mechanics.md) หัวข้อ 9) คนภายนอกต้องเห็นแค่ว่าผู้เล่น **กำลังทำอะไรอยู่ในเรื่อง** เช่น ก้มถอนหญ้า, ตั้งท่ารับหุ่นไม้, นั่งสมาธิ หรือยื่นฝ่ามือใส่ลูกแก้วปราณ โดยไม่เห็น cue ใดๆ

## ใช้อะไร

**playerAnimator** (`dev.kosmx.player-anim:player-animation-lib-forge`) เป็น library ที่ Iron's Spells และ BHSpells ใช้อยู่แล้ว (BHSpells ใช้ `1.0.2-rc1+1.20` จาก `https://maven.kosmx.dev/`) บนเซิร์ฟจึงมีอยู่แล้ว แต่ให้เป็น **soft dependency** เหมือน bhfx

| Class | หน้าที่ |
| :--- | :--- |
| `compat/playeranim/PlayerAnimCompat` | เช็ก `ModList.get().isLoaded("playeranimator")` และ catch `LinkageError` ห้าม import playerAnimator |
| `compat/playeranim/PlayerAnimBridge` | (client) ที่เดียวที่เรียก API ของ playerAnimator: ลงทะเบียน animation layer, เล่นหรือหยุดท่า |
| `client/pose/PoseAnimations` | รับ `S2CPlayerPose` แล้วสั่ง bridge (หรือ fallback) |

### ความสัมพันธ์กับ Emotecraft

- Emotecraft สร้างอยู่บน playerAnimator ตัวเดียวกัน ถ้าเซิร์ฟลง Emotecraft ไว้ ก็มี playerAnimator อยู่แล้วโดยอัตโนมัติ
- **เราไม่เรียก API ของ Emotecraft โดยตรง** แต่เล่นท่าผ่าน playerAnimator เอง (ลงทะเบียน animation layer ของ mod เรา) จึงทำงานได้ทั้งตอนที่มีหรือไม่มี Emotecraft
- ไฟล์ท่าเขียนใน **รูปแบบ emote JSON ของ Emotecraft** (playerAnimator อ่านได้) ทำได้ด้วย Emotecraft emote maker หรือ Blockbench plugin ผู้ใช้ที่เคยทำ emote ให้ Emotecraft จึงทำท่าให้ mod นี้ได้เลย
- ถ้าผู้เล่นกำลังเล่น emote ของ Emotecraft อยู่ตอนเริ่ม session ให้ layer ของเรามี priority สูงกว่า (ท่าของ session ชนะ) และไม่ต้องสั่งหยุด emote ของเขา
- ทั้ง playerAnimator และ Emotecraft เป็น **mod ฝั่ง client ที่ต้องลงทั้งสองฝั่ง** ถ้า client ของคนดูไม่มี playerAnimator คนนั้นจะเห็นท่าแบบ vanilla fallback แทน (packet `S2CPlayerPose` เป็นของ mod เรา ไม่ขึ้นกับ Emotecraft)

## ท่าทาง (`PlayerPose`)

| Pose | ใช้กับ | ท่า |
| :--- | :--- | :--- |
| `PULL_HERB` | หญ้าทุกชนิด | ย่อตัว มือสองข้างจับโคนต้น ดึงเป็นจังหวะช้าๆ (loop) |
| `WING_CHUN` | `wooden_dummy` | ยืนหน้าหุ่น ปัดแขนสลับซ้ายขวาแบบหยงชุน (loop) |
| `MEDITATE` | `wind_listener` | นั่งขัดสมาธิ มือวางบนเข่า (loop) + ของประกอบ `BLINDFOLD` |
| `QI_FLICK` | `candle_altar` | ยืนตั้งท่า ยกมือชี้ ดีดนิ้วเป็นระยะ (loop) ส่วนการหันซ้ายขวาเป็นการหันจริงของผู้เล่น (camera `FREE`) |
| (`PALM_PUSH`) | (`qi_station`, backlog) | ยื่นฝ่ามือสองข้างไปข้างหน้า (loop) |

**ของประกอบบนตัว:** pose มีของประกอบได้ เช่น `BLINDFOLD` (ผ้าปิดตา) วาดด้วย `PoseLayer` (RenderLayer ของ player renderer) บน client ของทุกคนจากสถานะ pose ที่ได้อยู่แล้ว ไม่มี packet เพิ่ม ใช้ได้ทั้งตอนมีและไม่มี playerAnimator

- ท่าเป็นแบบ loop ช้าๆ **ไม่สะท้อนการกดปุ่มจริง** (ถ้ากระตุกตามปุ่ม คนภายนอกจะอ่าน gameplay ได้และเปลือง packet)
- ไฟล์ animation อยู่ที่ `assets/bhgame/player_animation/*.json` ทำด้วย Blockbench + plugin ของ playerAnimator (หรือ Emotecraft format)
- ส่ง `S2CPlayerPose` แค่ตอนเริ่มและตอนจบ session (ดู [`../multiplayer-performance.md`](../multiplayer-performance.md))
- คนที่เข้ามาในระยะทีหลัง: ตอนผู้เล่นเริ่มถูก track (`PlayerEvent.StartTracking`) ให้ส่ง pose ปัจจุบันให้คนนั้นด้วย

## Fallback แบบ vanilla (ถ้าไม่มี playerAnimator)

> **ทำแล้ว (2026-10-19, ยังไม่ได้ทดสอบในเกม):** `client/pose/ClientPoses` ตอนนี้ใช้ fallback เสมอ เพราะ bridge ของ playerAnimator เป็นงาน P2
> - ท่าย่อตัว: ตั้ง `PlayerModel.crouching = true` และเลื่อนตัวลง 0.125 ใน `RenderPlayerEvent.Pre` (priority LOWEST) แล้ว pop ใน `Post` เพราะ pose ของ entity ถูก server sync ทับทุก tick จึงตั้ง `setPose` เองไม่ได้
> - แกว่งแขน: เรียก `player.swing(hand)` ฝั่ง client ตามเวลาสุ่ม
> - ผู้เล่นตัวเองในมุมมองบุคคลที่หนึ่งไม่แกว่งแขน (ไม่งั้นจะเห็นแขนแกว่งผ่านหน้าจอ)

ต้องดูพอใช้ได้จริง ไม่ใช่แค่ไม่ error เพราะเป็นทางเลือกที่ผู้ใช้ยอมรับ (2026-10-19):

| Pose | ท่าแบบ vanilla |
| :--- | :--- |
| `PULL_HERB` | ย่อตัว (`Pose.CROUCHING`) + แกว่งแขน (`swing`) ทุก 1–2 วินาที |
| `WING_CHUN` | ยืนนิ่ง + แกว่งแขนสลับซ้ายขวาเป็นระยะ |
| `MEDITATE` | ย่อตัวค้าง (vanilla ไม่มีท่านั่ง) + ผ้าปิดตา |
| `QI_FLICK` | แกว่งแขนขวาทุก 1–2 วินาที |

ทุกท่าไม่เล่นกับผู้เล่นที่อยู่ห่างเกิน `poseAnimationDistance` (32 บล็อก)

ทำฝั่ง client จาก `S2CPlayerPose` ไม่ส่ง packet `swing` เพิ่มจาก server

## ความเสี่ยงที่ต้องทดสอบ

- **Epic Fight:** เซิร์ฟใช้ Epic Fight (BHSpells อ้างถึง `epicfight_version`) ซึ่งมีระบบ animation ของผู้เล่นเอง ถ้าผู้เล่นอยู่ใน battle mode ท่าของ playerAnimator อาจไม่แสดง หรือแสดงซ้อนกัน **ต้องทดสอบใน P1** ทางแก้ที่เป็นไปได้: สลับผู้เล่นเป็น mining mode ระหว่าง session หรือใช้ระบบ animation ของ Epic Fight แทน
- ท่าของ Iron's Spells (ตอนร่ายเวท) อาจชนกัน แต่ระหว่าง session การใช้ของถูกล็อกอยู่ จึงไม่น่าเกิด

## ที่ต้องตัดสินใจตอนทำ

- `build.gradle`: `compileOnly fg.deobf("dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20")` + `runtimeOnly` สำหรับ dev
- `mods.toml`: `modId="playeranimator" mandatory=false`
