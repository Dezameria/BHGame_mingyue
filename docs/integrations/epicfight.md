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

## โหมด vanilla สำหรับหาบน้ำ (D51)

ตรวจใน jar Epic Fight 20.14.17 (2026-10-29):

- `AbstractClientPlayerPatch.overrideRender()` ส่ง `RenderEpicFightPlayerEvent` ค่าเริ่มต้นคือ `!ClientConfig.enableOriginalModel || isEpicFightMode()` ถ้าตั้ง `setShouldRender(false)` Epic Fight จะไม่วาดผู้เล่นคนนั้น และ vanilla วาดแทน event นี้มาจาก patch ของผู้เล่นทุกคนที่ client เห็น จึงทำงานบนเครื่องคนดูด้วย
- `PlayerPatch.toVanillaMode(boolean)` / `toEpicFightMode(boolean)` ยิง `ChangePlayerModeEvent` (ยกเลิกได้) ก่อนเปลี่ยนโหมด `ServerPlayerPatch` ส่งโหมดใหม่ให้ทุกคนที่ track ส่วน `LocalPlayerPatch` ส่ง `CPChangePlayerMode` ไป server ก่อนแล้วค่อยยิง event ฝั่ง client จึงต้องยกเลิกทั้งสองฝั่ง
- ใช้ใน `EpicFightBridge` (common: เปลี่ยนโหมด + ล็อก) และ `EpicFightClientBridge.renderVanillaFor` ตอน `/bhgame yoke` (prototype) server เปลี่ยนเป็นโหมด vanilla ส่ง pose ก่อนแล้วจึงคืนโหมดเดิมตอนจบ

## บังคับโหมด vanilla ในเกมอื่นทุกเกม (D78)

- ทุกเกมที่ไม่ใช่หุ่นไม้ (`forcesEpicFightMode() == false`, ไม่ใช่การแบก) ใช้ท่าของ vanilla model: `SessionManager.begin` เรียก `EpicFightCompat.forceVanillaMode` ถ้าเดิมอยู่โหมดต่อสู้จำไว้ใน `Participant.restoreEpicFightMode` แล้ว `finish` คืนด้วย `restoreEpicFightMode` หลังส่ง pose `NONE` (คนออกเกมระหว่างเล่นไม่คืน เหมือน D66)
- ล็อกการสลับไปโหมดต่อสู้ทั้งสองฝั่งด้วย `EpicFightBridge.lockVanillaMode` และให้ Epic Fight ไม่วาดตัว (`renderVanillaFor`) เงื่อนไขเดียวกันคือ `PlayerPose.usesVanillaModel` (ทุก pose ยกเว้น `NONE` และ `WING_CHUN`): server ดู `SessionManager.poseOf`, client ดู `ClientPoses.usesVanillaModel` หาบน้ำใช้ทางเดิม (D51) และเข้าเงื่อนไขนี้ด้วย

## บังคับโหมดต่อสู้บนหุ่นไม้ (D66)

- `TrainingBlock.forcesEpicFightMode()` (หุ่นไม้ = true): `SessionManager.begin` เรียก `EpicFightCompat.forceEpicFightMode` → `EpicFightBridge.toEpicFightModeIfVanilla` ถ้าเดิมอยู่โหมด vanilla จำไว้ใน `Participant.restoreVanillaMode` แล้ว `finish` คืนด้วย `restoreVanillaMode` หลังส่ง pose `NONE` (คนออกเกมระหว่างเล่นไม่คืน)
- ระหว่างเล่นล็อกการสลับไปโหมด vanilla ทั้งสองฝั่งด้วย `EpicFightBridge.lockEpicFightMode`: server ดู `SessionManager.poseOf == WING_CHUN`, client ดู `ClientPoses.isWingChun`

## หุ่นฝึก (D73)

mob `bhgame:sparring_dummy` มี patch ของตัวเอง (`SparringDummyPatch extends HumanoidMobPatch`, armature `Armatures.BIPED`, renderer `PHumanoidRenderer` + `Meshes.BIPED`) ลงทะเบียนผ่าน `EntityPatchRegistryEvent` / `PatchedRenderersEvent.Add` บน mod bus ตอน constructor ของ mod เฉพาะเมื่อมี Epic Fight ท่าที่ admin ตั้งเล่นด้วย `playAnimationSynchronized` **ฝั่งเซิร์ฟ** (ต่างจากหุ่นไม้ที่เล่นฝั่ง client) เพราะต้องการให้ตีโดนจริง รายละเอียดและคำสั่งอยู่ที่ [`design/sparring_dummy.md`](../design/sparring_dummy.md)

ตรวจใน jar 20.14.17 (2026-11-05): `/epicfight animator play` = `LivingEntityPatch.playAnimationSynchronized(AnimationManager.byKey(id), transition)`, `HumanoidMobPatch.initAI` เรียก `setAIAsInfantry` ซึ่งใส่ `AnimatedAttackGoal` + `TargetChasingGoal` ตามอาวุธ patch ของเราจึง override ให้ว่าง, `MobPatch.initAI` ลบ goal ที่เป็น `MeleeAttackGoal`/`AnimatedAttackGoal`/`TargetChasingGoal` ออก `SparringAttackGoal` ไม่ได้ extends พวกนี้จึงไม่โดนลบ

## ต้องทดสอบ

- ท่าแสดงทั้งตอนผู้เล่นอยู่ในโหมดต่อสู้และโหมดปกติ (mining) ของ Epic Fight หรือไม่
- ไม่มีใครโดน damage จากหมัด (ยืนหน้าหุ่นขณะอีกคนเล่น)
- ท่าไม่ค้างหรือตีกับท่าย่อตัวหรือแกว่งแขนแบบ fallback
- มุมมองบุคคลที่หนึ่งของคนเล่นดูโอเค

### ความเร็วท่าของหุ่นฝึก (D75)

`mixin/epicfight/AnimationPlayerMixin`: `@ModifyVariable(method = "tick", at = @At(value = "STORE", ordinal = 0), ordinal = 0)` คูณความเร็วฐาน (ค่าจาก `DynamicAnimation.getPlaySpeed`) ด้วย `SparringDummy.moveSpeed()` (entity data) เฉพาะเมื่อ entity เป็นหุ่นฝึกและ `getRealAnimation()` เป็น `ActionAnimation` แต่ไม่ใช่ `LongHitAnimation` ทำงานทั้งสองฝั่ง โหลดผ่าน `BhGameMixinPlugin` เฉพาะเมื่อมี Epic Fight เหมือน D44 ถ้า Epic Fight เปลี่ยนโค้ดของ `AnimationPlayer.tick` mixin จะไม่ทำงานเงียบๆ (`defaultRequire 0`) หุ่นจะเล่นท่า 1x ไม่ crash
