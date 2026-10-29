# Epic Fight

> สถานะ: **เขียนโค้ดแล้ว** (2026-10-21, D41) build ผ่าน ยังไม่ได้ทดสอบในเกม

## ใช้ทำอะไร

หุ่นไม้ (`wooden_dummy`): กดลูกศรถูกแต่ละครั้ง คนรอบข้าง (และตัวเองในมุมบุคคลที่สาม) เห็นผู้เล่นต่อยหุ่นด้วยท่ามือเปล่าของ Epic Fight สลับซ้ายขวา กดท่าปิดชุด (`SPACE`) เป็นหมัดหนัก กดผิดแล้วโดนหุ่นฟาดเป็นท่าโดนตี

| `PlayerAction` | ท่าของ Epic Fight | ถ้าไม่มี Epic Fight |
| :--- | :--- | :--- |
| `PUNCH_RIGHT` (ลูกศรลำดับคู่) | `Animations.FIST_AUTO1` | แกว่งมือหลัก |
| `PUNCH_LEFT` (ลูกศรลำดับคี่) | `Animations.FIST_AUTO2` | แกว่งมือรอง |
| `PUNCH_FINISH` (ท่าปิดชุด) | `Animations.FIST_AUTO3` | แกว่งมือหลัก |
| `KNOCKED_BACK` (กดผิด) | `Animations.BIPED_HIT_SHORT` | ไม่มี (ท่าโดนตีของ vanilla จาก damage อยู่แล้ว) |

## เป็น soft dependency

- เวอร์ชันเดียวกับ BHSpells: Epic Fight **20.14.17** จาก Modrinth (`maven.modrinth:vu3NZ5Ma:KEBfkBat`)
- `build.gradle`: `compileOnly` เสมอ และ `runtimeOnly` ใน dev เมื่อ `use_epicfight=true` ใน `gradle.properties` (ตั้ง `false` เพื่อทดสอบ fallback แบบ vanilla)
- `mods.toml`: `epicfight` `mandatory=false`, `versionRange="[20.14.1,)"`
- ไม่ต้องลงอะไรเพิ่มบนเซิร์ฟจริง (มี Epic Fight อยู่แล้ว)
- **dev run ต้องมี** `mixin.env.remapRefMap=true` และ `mixin.env.refMapRemappingFile=build/createSrgToMcp/output.srg` (ตั้งไว้ใน `build.gradle` แล้ว) ไม่งั้น mixin ของ Epic Fight ไม่ทำงาน แล้วเกมค้างที่ "Joining world" (เจอแล้ว 2026-10-21) ปัญหานี้มีแค่ใน dev บนเซิร์ฟจริงไม่เกี่ยว

| Class | หน้าที่ |
| :--- | :--- |
| `compat/epicfight/EpicFightCompat` | เช็ก `ModList.isLoaded("epicfight")` + จำว่าเคยเจอ `LinkageError` ไหม **ห้าม import Epic Fight** |
| `compat/epicfight/client/EpicFightClientBridge` | ที่เดียวที่ import Epic Fight: หา `LivingEntityPatch` แล้ว `playAnimationInClientSide` |
| `client/pose/ClientPoses.action` | รับ `S2CPlayerAction` → bridge (ถ้ามี) หรือแกว่งแขน vanilla, ข้ามคนที่อยู่ไกลเกิน `poseAnimationDistance` |

## ทำไมเล่นท่าฝั่ง client ไม่ใช่ server

ท่า `FIST_AUTO*` ของ Epic Fight เป็น **ท่าโจมตี** ถ้า server สั่งเล่น (`playAnimationSynchronized`) Epic Fight จะตรวจการชนจริง และอาจต่อยโดนคนที่ยืนดูอยู่หน้าหุ่น จึงให้ server ส่ง `S2CPlayerAction` ไปให้คนที่ track ผู้เล่น แล้วแต่ละ client เล่นท่าเอง (แบบเดียวกับ `JingGuangPanClient` ของ BHSpells) ท่าที่เล่นฝั่ง client ไม่ทำ damage ใคร

## ไม่ให้ท่าพาตัวเคลื่อน (D44)

ท่าโจมตีและท่าโดนตีของ Epic Fight เป็น `ActionAnimation` ที่เลื่อนตัวตามท่า (root motion) ใน `ActionAnimation.move` mod นี้มี mixin หนึ่งตัว (`mixin/epicfight/ActionAnimationMixin`) ที่ยกเลิก method นี้เมื่อผู้เล่นอยู่ใน session ท่าจึงยังเล่นครบแต่ตัวอยู่กับที่ นอก session ไม่กระทบอะไร

- `bhgame.mixins.json`: `required=false` และ `BhGameMixinPlugin` ข้าม mixin ของ Epic Fight ถ้าไม่ได้ลง Epic Fight
- `remap=false` เพราะ class ของ Epic Fight ไม่ถูก obfuscate (log ใน dev จะเตือนว่าอ่าน refmap ไม่ได้ ไม่เป็นไร)
- ถ้า Epic Fight เปลี่ยนชื่อหรือ signature ของ `move` mixin จะไม่ทำงาน (`defaultRequire=0` ไม่ทำให้เกม crash) ตัวจะกลับมาพุ่งตามท่า ให้เช็ก log ว่ามีบรรทัด `Mixing epicfight.ActionAnimationMixin`

## Packet

- ส่งแบบ `TRACKING_ENTITY_AND_SELF` ไม่เกิน 1 ครั้งต่อ 3 tick ต่อผู้เล่น (`Participant.allowAction`) คนกดเร็วกว่านั้นจะเห็นหมัดน้อยกว่าที่กด แต่ยังดูเป็นชุดหมัด
- ประมาณ 2–6 packet ต่อวินาทีต่อผู้เล่นที่กำลังเล่นหุ่น ส่งถึงทุกคนในระยะ track เฉพาะหุ่นไม้

## ต้องทดสอบ

- ท่าแสดงทั้งตอนผู้เล่นอยู่ในโหมดต่อสู้และโหมดปกติ (mining) ของ Epic Fight หรือไม่
- ไม่มีใครโดน damage จากหมัด (ยืนหน้าหุ่นขณะอีกคนเล่น)
- ท่าไม่ค้างหรือตีกับท่าย่อตัวหรือแกว่งแขนแบบ fallback
- มุมมองบุคคลที่หนึ่งของคนเล่นดูโอเค
