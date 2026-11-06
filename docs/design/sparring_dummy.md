# หุ่นฝึก (`sparring_dummy`)

> สถานะ: **เขียนโค้ดแล้ว** (2026-11-05, D73; ไม้สั่งหุ่น D74 ใน 0.1.25; หน้าตั้งค่าอังกฤษ 3 แท็บ ความเร็วท่า pattern stats อุปกรณ์ D75 ใน 0.1.26) build ผ่าน **ยังไม่ได้ลองในเกม**

mob รูปคน (`bhgame:sparring_dummy`) ที่เดินไล่และตีเป้าหมายด้วยท่าของ Epic Fight ที่ admin ตั้งด้วยคำสั่ง วนท่าไปเรื่อยๆ ไม่ใช่มินิเกม ไม่มี session, HP, cue หรือ HUD ของ mod เป็น mob ธรรมดาแบบมอนของ Epic Fight ท่าเล่นฝั่งเซิร์ฟ จึงตีโดนจริง (damage, ท่าโดนตี, กันด้วยทักษะของ Epic Fight ได้)

## ทำงานยังไง

1. หาเป้าหมาย (ดู `target` ข้างล่าง) ถ้าเป้าหมายอยู่ไกลกว่า `range` และ `chase true` → เดินเข้าไปหา
2. ถึงระยะแล้ว → หันหาเป้าหมายทันที แล้วเล่นท่าถัดไปในรายการด้วย `playAnimationSynchronized` (เหมือน `/epicfight animator play <หุ่น> <ท่า> <transition>`)
3. รอให้ท่าจบ (Epic Fight บอกว่าหุ่นไม่อยู่ใน action แล้ว: `EntityState.inaction()` ซึ่งรวมตอนโดนตีจนชะงักด้วย) → พัก `pause` tick → ท่าถัดไป
4. ลำดับ `sequence` วนจากท่าแรกถึงท่าสุดท้าย เริ่มท่าแรกใหม่เมื่อเสียเป้าหมายหรือแก้รายการ, `random` สุ่มทุกครั้ง
5. ยังไม่ตั้งท่า → ใช้หมัด 3 ท่าของ Epic Fight (`epicfight:biped/combat/fist_auto1..3`)
6. ท่าที่ Epic Fight ไม่รู้จัก (เช่น ลบ resource pack ไปแล้ว) ถูกข้ามหลัง 1 วินาที

ท่าที่ไม่ใช่ action (เช่นท่ายืน `biped/living/*`) จบ "ทันที" ในสายตาของ AI หุ่นจะเล่นท่าถัดไปหลังพัก `pause` เลย ให้ใช้ท่าโจมตีหรือท่า action

ไม่มี Epic Fight: หุ่นยังเกิดได้ ต่อยแบบ vanilla ทุก 1 วินาที (หรือ `pause` ถ้านานกว่า)

## คำสั่ง (permission 2)

`<หุ่น>` เป็น entity selector เช่น `@e[type=bhgame:sparring_dummy,distance=..5]` ใช้กับหลายตัวพร้อมกันได้ id ของท่าเป็นแบบเดียวกับ `/epicfight animator play` และ tab-complete รายชื่อท่าของ Epic Fight ได้

| คำสั่ง | ผล |
| :--- | :--- |
| `/bhgame sparring <หุ่น> info` (หรือ `moves`) | ค่าที่ตั้งไว้ + รายการท่า |
| `/bhgame sparring <หุ่น> moves add <ท่า> [transitionTimeModifier]` | เพิ่มท่าต่อท้าย (สูงสุด 32 ท่า) |
| `/bhgame sparring <หุ่น> moves set <ท่า> <ท่า> ...` | ตั้งรายการใหม่ทั้งชุด คั่นด้วยเว้นวรรค |
| `/bhgame sparring <หุ่น> moves remove <ลำดับ>` | ลบท่าที่ลำดับนั้น (เริ่ม 1) |
| `/bhgame sparring <หุ่น> moves clear` | ล้าง กลับไปใช้หมัดพื้นฐาน |
| `/bhgame sparring <หุ่น> order sequence\|random` | ตามลำดับ (ค่าเริ่มต้น) / สุ่ม |
| `/bhgame sparring <หุ่น> pause <tick>` | พักหลังท่าจบก่อนท่าถัดไป 0–200 (ค่าเริ่มต้น 10) |
| `/bhgame sparring <หุ่น> range <บล็อก>` | เริ่มท่าเมื่อเป้าหมายอยู่ใกล้เท่านี้ วัดจากกลางตัวถึงกลางตัว 0.5–16 (ค่าเริ่มต้น 2.5) ท่าที่พุ่งไกลตั้งให้มากขึ้น |
| `/bhgame sparring <หุ่น> chase true\|false\|stay\|walk\|run\|auto` | เดินไล่ (ค่าเริ่มต้น) / ยืนอยู่กับที่ รอให้เป้าหมายเข้ามาในระยะ |
| `/bhgame sparring <หุ่น> target nearest` | ตีผู้เล่นที่ใกล้ที่สุดในระยะ `follow_range` 24 บล็อก ที่มองเห็น (ค่าเริ่มต้น ไม่สนผู้เล่น creative/spectator) และตีกลับคนที่ตีมัน |
| `/bhgame sparring <หุ่น> target none` | ไม่หาเป้าหมายเอง ตีกลับเฉพาะคนที่ตีมัน |
| `/bhgame sparring <หุ่น> name <ชื่อ...>` / `name clear` | ตั้งชื่อที่ลอยเหนือหุ่น (แสดงตลอด) ใช้ `&` เป็นโค้ดสี/สไตล์ เช่น `&6&lMaster Wei` ยาวไม่เกิน 48 ตัวอักษร / ลบชื่อ (0.2.1) |
| `/bhgame sparring <หุ่น> stopbelow <%>` | หยุดตีและยืนนิ่งเมื่อเลือดเป้าหมายต่ำกว่า % นี้ของเลือดเต็ม จนเป้าหมายเลือดเต็มอีกครั้ง 0 = ตีไปจนสุด (ค่าเริ่มต้น) สูงสุด 95 (D79) |
| `/bhgame sparring <หุ่น> command <event> <คำสั่ง...>` / `command <event> clear` | ตั้ง/ล้างคำสั่งที่หุ่นรันเองตอนเกิด event (`engage`, `target_low`, `move`, `hurt`, `death`) ต้องมี permission `sparring.commandPermissionLevel` (D79) |
| `/bhgame sparring <หุ่น> target <entity>` | ตีสิ่งมีชีวิตตัวนี้ตลอด (รวมผู้เล่น creative และหุ่นฝึกอีกตัว) ไม่เปลี่ยนไปตีคนอื่นแม้โดนตี ผู้เล่นตายแล้วเกิดใหม่ยังเป็นเป้าเดิม |

ตัวอย่าง: หุ่นที่ใกล้ที่สุดฟันดาบยาว 3 จังหวะแล้วพักครึ่งวินาที

```
/item replace entity @e[type=bhgame:sparring_dummy,limit=1,sort=nearest] weapon.mainhand with minecraft:iron_sword
/bhgame sparring @e[type=bhgame:sparring_dummy,limit=1,sort=nearest] moves set epicfight:biped/combat/longsword_auto1 epicfight:biped/combat/longsword_auto2 epicfight:biped/combat/longsword_auto3
/bhgame sparring @e[type=bhgame:sparring_dummy,limit=1,sort=nearest] pause 10
```

## ไม้สั่งหุ่น (`bhgame:sparring_baton`, D74, D75, D79)

ไอเทมในแท็บ BeeHouse Game (หรือ `/give @s bhgame:sparring_baton`) ใช้ได้เฉพาะคนที่มี permission ระดับ 2 เหมือนคำสั่ง (ปรับได้ที่ server config `sparring.batonPermissionLevel`, 0 = ทุกคนที่ถือไม้) ข้อความของไม้และหน้าตั้งค่าเป็นภาษาอังกฤษตามที่ผู้ใช้ขอ (D75)

**คลิกขวาหุ่นฝึก** → เปิดหน้าตั้งค่าของหุ่นตัวนั้น มี 4 แท็บ ไม่มีอะไรเปลี่ยนจนกว่าจะกด **Save** (Cancel/Esc = ไม่เปลี่ยน)

| แท็บ | มีอะไร |
| :--- | :--- |
| **Moves** | เลือกชุดท่าด้วย `<` `>`: **Default moves** (ชุดหลัก) หรือ **Pattern 1..8** (`+ Pattern` / `Delete pattern`) แต่ละชุดมี Order (In order/Random) และ Range ของตัวเอง pattern มีเงื่อนไข `If:` (ดูหัวข้อ pattern) ซ้าย: ท่าในชุด ▲ ▼ ✕ คลิกท่าเพื่อเลือก แล้วตั้ง `Speed x` และ `Transition` ของท่านั้นในแถวล่าง ขวา: รายชื่อท่าของ Epic Fight + ช่องค้นหา คลิกเพื่อเพิ่ม Enter = เพิ่มอันแรก (หรือ id ที่พิมพ์ถ้าไม่เจอ) |
| **Behaviour** | Pause between moves (0–200 tick), Play speed (x0.25–3), Chase (Stay / Walk / Run / Auto = วิ่งเมื่อเป้าหมายไกลกว่า 6 บล็อก), Run speed (x1–3 ของความเร็วเดิน), Target (Nearest player / Hit back only / Me / เป้าเดิม), Stop below (Off, 5–95% ทีละ 5, D79) |
| **Stats & Gear** | Name (ชื่อเหนือหุ่น โค้ดสี `&` สูงสุด 48 ตัวอักษร ว่าง = ไม่มีชื่อ) + ปุ่ม Name: Always / On look (แสดงตลอด หรือเฉพาะตอนเล็งหุ่นแบบป้ายชื่อ vanilla, 0.2.1), Max HP, Attack damage, Armor, Walk speed (base ของ attribute vanilla ค่าเดียวกับ `/attribute`), Invulnerable บันทึกแล้วเติมเลือดเต็ม ช่องอุปกรณ์ 6 ช่อง (มือหลัก มือรอง หัว อก ขา เท้า): คลิกเพื่อเลือกของจากกระเป๋าเรา คลิกขวา = ถอด ของถูก **คัดลอก** (1 ชิ้น) ของเราไม่หาย หุ่นไม่ดรอป |
| **Commands** (D79) | ช่องคำสั่ง 5 ช่อง หนึ่งช่องต่อ event (ดูหัวข้อคำสั่งของหุ่น) คนที่ไม่มี permission `sparring.commandPermissionLevel` เห็นแค่ข้อความบอก ไม่ได้รับคำสั่งที่ตั้งไว้ และเซิร์ฟไม่รับคำสั่งจากเขา |

- **ชื่อ:** หัวหน้าตั้งค่าเป็นชื่อหุ่นตามสีที่ตั้ง + `#id` (ไม่มีชื่อ = `Sparring Dummy #id`) เปลี่ยนตามช่อง Name ระหว่างพิมพ์ ยาวเกินที่ว่างซ้ายของปุ่มแท็บถูกตัด (0.2.2) ไม่ถูก Copy/Paste และถ้าไม่ได้แตะช่อง Name หรือปุ่มแสดงชื่อ การกด Save จะไม่เขียนชื่อทับ (ชื่อที่ตั้งด้วย name tag หรือ JSON ของ `/data` ยังอยู่) เป็น `CustomName` ของ vanilla จึงใช้ name tag หรือ `/data` ก็ได้
- **Copy / Paste:** คัดลอกท่า pattern ค่า Behaviour, Stats และ Commands (ถ้ามีสิทธิ์) (ไม่รวมอุปกรณ์และเป้าหมาย) ไปวางในหน้าของหุ่นตัวอื่น อยู่ในเครื่องเราจนกว่าจะปิดเกม
- ตอนบันทึก เซิร์ฟตรวจ permission, ระยะ (ไม่เกิน 48 บล็อกจากหุ่น), id ท่าทุกท่า (ท่าที่ Epic Fight ไม่รู้จักถูกข้ามและบอกจำนวนใน action bar) และอ่านของจากช่องกระเป๋าของผู้เล่นเองบนเซิร์ฟ ไม่เชื่อ item จาก client
- **Shift + คลิกขวาหุ่นตัวที่หนึ่ง แล้ว Shift + คลิกขวาตัวที่สอง** → สองตัวตั้งเป้าหมายเป็นกันและกัน ระหว่างรอตัวที่สองไม้จะเรืองแสง Shift + คลิกตัวเดิมซ้ำ = ยกเลิก

## ความเร็วท่า (D75)

- ความเร็วจริงของท่า = **Play speed ของหุ่น × Speed ของท่านั้น** (เช่น x1.5 × x2 = x3) ใช้กับท่า action ทั้งหมด (โจมตี หลบ ฯลฯ) ไม่ใช้กับท่าเดิน ท่าโดนตี และท่าล้ม
- Epic Fight ปรับความเร็วท่าตาม attack speed ให้เฉพาะผู้เล่น mob เล่น 1x เสมอ จึงใช้ mixin `AnimationPlayerMixin` คูณความเร็วฐานที่ `AnimationPlayer.tick` อ่าน ทั้งฝั่งเซิร์ฟ (จังหวะ hit) และ client (ภาพ) ตรงกัน
- ความเร็วส่งไป client เป็น entity data (`DATA_MOVE_SPEED`) AI ตั้งค่า **1 tick ก่อนเล่นท่า** เพื่อให้ client ได้ค่าก่อน packet ท่าของ Epic Fight มาถึง
- "ตีไวๆ": Pause 0 + Play speed x1.5–2 ท่าที่เร็วมาก (เกิน x3) อาจเลยช่วง hit ของบางท่าไปใน tick เดียว ตีไม่โดน
- คำสั่ง: `/bhgame sparring <หุ่น> speed <x>` (Play speed ของหุ่น)

## Pattern ตามเงื่อนไข (D75)

ก่อนออกท่าแต่ละครั้ง หุ่นไล่ดู Pattern 1, 2, ... ตามลำดับ ใช้ pattern แรกที่ (1) มีท่า (2) เงื่อนไขเป็นจริง และ (3) เป้าหมายอยู่ในระยะ Range ของ pattern ถ้าไม่มี ใช้ Default moves ถ้าเป้าหมายอยู่ในระยะของ Default ถ้าไม่ถึงระยะไหนเลยก็เดิน/วิ่งเข้าไปหา (ตาม Chase)

| เงื่อนไข | จริงเมื่อ |
| :--- | :--- |
| Target farther than X blocks | เป้าหมายไกลกว่า X (ใช้คู่กับ Range ที่มากกว่า X เช่นท่าพุ่ง) |
| Target closer than X blocks | เป้าหมายใกล้กว่า X |
| My HP below X% | เลือดหุ่นต่ำกว่า X% |
| Target HP below X% | เลือดเป้าหมายต่ำกว่า X% |
| Chance X% | สุ่มครั้งเดียวต่อท่า (ไม่สุ่มทุก tick) |
| Got hit in the last X s | หุ่นโดนตีในช่วง X วินาทีที่ผ่านมา (ใช้ทำท่าสวนกลับ) |

- เปลี่ยนชุดท่าเมื่อไร ชุดใหม่เริ่มจากท่าแรก ชุดที่ Order = In order จึงเล่นเป็นคอมโบได้
- ตั้ง pattern ได้จากไม้สั่งหุ่นหรือ NBT (`Patterns`) เท่านั้น ไม่มีคำสั่ง (`info` แสดงรายการ)

## หยุดตีเมื่อเป้าหมายเลือดน้อย (D79)

- ตั้ง **Stop below** (แท็บ Behaviour) หรือ `stopbelow <%>` เมื่อเลือดของเป้าหมายต่ำกว่า % นั้นของเลือดเต็ม หุ่นเลิกตี เลิกไล่ แล้วยืนนิ่ง (มองผู้เล่นใกล้ๆ แบบ vanilla) ท่าที่กำลังเล่นอยู่เล่นต่อจนจบ ไม่ตัดกลางท่า
- กลับมาตีเมื่อเป้าหมาย **เลือดเต็ม** อีกครั้ง (กินอาหารพักจนเต็ม, ยาฟื้นเลือด) ไม่ใช่แค่กลับขึ้นเหนือเส้น เพื่อไม่ให้สลับหยุด/ตีทุกครั้งที่ regen ขึ้นครึ่งหัวใจ
- เปลี่ยนเป้าหมาย (เช่นโดนคนอื่นตี) เป้าใหม่ถูกเช็กใหม่ตั้งแต่ต้น; กลับมาเจอเป้าเดิมที่ยังเลือดน้อยก็หยุดอีก (และรัน `target_low` อีกครั้ง)
- จำแค่ในหน่วยความจำ เซฟ/โหลดโลกหรือแก้ค่า Stop below แล้วเช็กใหม่
- เกม: AI โจมตี (`SparringAttackGoal.canUse`) ถาม `SparringDummy.holdsBack(target)` ทุก tick ที่มีเป้าหมาย เป็นแค่การเทียบเลือดกับ UUID ไม่มีภาระเพิ่ม

## คำสั่งของหุ่น (D79)

หนึ่งคำสั่งต่อ event หุ่นรันเองบนเซิร์ฟแบบ command block: ผู้รันคือหุ่น (`@s`) ที่ตำแหน่งหุ่น permission 2 ไม่แสดงผลลัพธ์ในแชท ใส่ `/` นำหน้าหรือไม่ก็ได้ ยาวไม่เกิน 512 ตัวอักษร อยากรันหลายคำสั่งให้ใช้ `/function`

| Event (NBT/คำสั่ง) | รันเมื่อ | `{target}` คือ |
| :--- | :--- | :--- |
| On engage (`engage`) | เริ่มไล่/ตีเป้าหมาย (รวมตอนกลับมาตีหลังเป้าหมายเลือดเต็ม) | เป้าหมาย |
| On target low HP (`target_low`) | หยุดตีเพราะเลือดเป้าหมายต่ำกว่า Stop below (ครั้งเดียวต่อการหยุด) | เป้าหมาย |
| On each move (`move`) | เริ่มเล่นท่าโจมตีแต่ละท่า | เป้าหมาย |
| On hurt (`hurt`) | โดนสิ่งมีชีวิตตี (ตรงหรือด้วยกระสุน) รวมตอน Invulnerable (นับได้ทุก 10 tick) | คนที่ตี |
| On death (`death`) | ตาย (ก่อนท่าตาย) | คนที่ฆ่า (ถ้ามี) |

- `{target}` ถูกแทนด้วย UUID ซึ่งใช้แทน selector ได้ทุกคำสั่ง เช่น `effect give {target} minecraft:regeneration 5 2`, `scoreboard players add {target} hits 1`, `tellraw {target} {"text":"พอแล้ว พักก่อน"}` ถ้า event นั้นไม่มีใคร (เช่นตายเพราะ `/kill`) คำสั่งที่มี `{target}` ถูกข้าม
- **สิทธิ์:** ตั้งคำสั่งได้ต่อเมื่อมี permission `sparring.commandPermissionLevel` (ค่าเริ่มต้น 2 ตั้งต่ำกว่า 2 ไม่ได้) เพราะคำสั่งรันที่ระดับ 2 เท่า command block ถ้าลด `batonPermissionLevel` ให้คนทั่วไปใช้ไม้ได้ คนพวกนั้นก็ยังแก้คำสั่งไม่ได้และไม่เห็นคำสั่งที่ตั้งไว้ ส่วน `/summon`/`/data` ต้องมีระดับ 2 อยู่แล้ว
- **กันวน:** คำสั่งของหุ่นตัวหนึ่งไปทำให้หุ่นอีกตัวรันคำสั่งได้อีกชั้นเดียว (`MAX_COMMAND_DEPTH` 2) เช่น `hurt` ที่ `/damage` หุ่นอีกตัวไม่วนกันไม่จบ
- NBT: `Sparring.Commands:{engage:"...",target_low:"...",move:"...",hurt:"...",death:"..."}` (เก็บเฉพาะช่องที่ไม่ว่าง)
- Invulnerable: vanilla หยุด `hurt` ก่อนจดว่าใครตี หุ่นที่ไม่ตายจึงไม่รู้ว่าโดนตี ตอนนี้หุ่นจดคนตีเอง (ทุก 10 tick) ทำให้ `hurt`, เงื่อนไข pattern "Got hit in the last" และเป้าหมาย "Hit back only" ใช้กับหุ่น Invulnerable ได้ด้วย (ก่อน 0.1.32 ใช้ไม่ได้)

## ค่าอื่นใช้ของ vanilla

- **damage / HP / ความเร็ว:** `/attribute <หุ่น> minecraft:generic.attack_damage base set 6` ฯลฯ ค่าเริ่มต้น HP 40, damage 4, ความเร็ว 0.3, follow range 24 damage จริงของแต่ละท่าคิดโดย Epic Fight จาก attack damage + อาวุธในมือ + ตัวคูณของท่า
- **อาวุธและชุดเกราะ:** `/item replace entity <หุ่น> weapon.mainhand|weapon.offhand|armor.* with ...` ไม่ดรอปของที่ใส่ให้ (drop chance 0) และไม่เก็บของจากพื้น
- **ไม่ให้ตาย:** `/data merge entity <หุ่น> {Invulnerable:1b}`
- **ตั้งตอนเกิด:** `/summon bhgame:sparring_dummy ~ ~ ~ {Sparring:{Moves:[{Anim:"epicfight:biped/combat/fist_auto1"},{Anim:"epicfight:biped/combat/fist_auto3",Transition:0.1f}],Order:"random",Pause:20,Range:3.0f,Chase:1b,TargetMode:"nearest_player"}}` (`TargetMode`: `nearest_player`, `none`, `fixed` + `Target` เป็น UUID)
- ไม่ despawn, ไม่เกิดเองตามธรรมชาติ (`MobCategory.MISC`) ได้จากไข่ในแท็บ BeeHouse Game หรือ `/summon`

## หน้าตา

- vanilla: `HumanoidMobRenderer` + โมเดลผู้เล่น (layout skin 64×64) + ชั้นเกราะ texture `textures/entity/sparring_dummy.png` (placeholder จากสคริปต์: ไม้ หน้าแกะสลัก ผ้าคาดหัวและผ้าคาดเอวแดง ผ้าพันมือ) วาดทับด้วย skin ผู้เล่นมาตรฐานได้เลย ชั้น overlay (หมวก/เสื้อ/แขนเสื้อ) ไม่ถูกวาด
- Epic Fight: `PHumanoidRenderer` + `Meshes.BIPED` ใช้ texture เดียวกัน

## โค้ด

| Class | หน้าที่ |
| :--- | :--- |
| `game/mob/sparring_dummy/SparringDummy` | entity: ค่าที่ตั้ง (NBT `Sparring`), เป้าหมาย, ลำดับท่า |
| `game/mob/sparring_dummy/SparringAttackGoal` | เดินไล่ → เล่นท่า → รอท่าจบ → พัก (ไม่ import Epic Fight) |
| `game/mob/sparring_dummy/SparringMove` | id ท่า + transition + speed |
| `game/mob/sparring_dummy/SparringPattern` | ชุดท่าตามเงื่อนไข (D75) |
| `game/mob/sparring_dummy/SparringCommands` | คำสั่งต่อ event + NBT/encode (D79) |
| `game/mob/sparring_dummy/SparringStats` | HP damage armor walk speed invulnerable + ลำดับช่องอุปกรณ์ |
| `mixin/epicfight/AnimationPlayerMixin` | คูณความเร็วท่าของหุ่น (D75) |
| `compat/epicfight/SparringDummyPatch` | `HumanoidMobPatch`: ท่ายืน/เดิน/ตาย/โดนตีของ biped, ไม่ใส่ AI ต่อสู้ของ Epic Fight |
| `compat/epicfight/EpicFightMobBridge` | ลงทะเบียน patch + armature `BIPED` (`EntityPatchRegistryEvent`), เล่นท่า, เช็ก `inaction`, รายชื่อท่า |
| `compat/epicfight/client/EpicFightMobClient` | ลงทะเบียน patched renderer (`PatchedRenderersEvent.Add`) |
| `client/render/mob/SparringDummyRenderer` | renderer vanilla |
| `command/SparringCommand` | `/bhgame sparring ...` |
| `game/mob/sparring_dummy/SparringBatonItem` | ไม้สั่งหุ่น: เปิดหน้าตั้งค่า, จับคู่ให้ตีกัน, ตรวจและบันทึกค่าจากหน้าตั้งค่า (D74) |
| `game/mob/sparring_dummy/SparringSettings` | ค่าการสู้ที่หน้าตั้งค่าแก้ (ท่า pattern พัก การไล่ ความเร็ว) + encode/decode |
| `client/screen/SparringScreen` | หน้าตั้งค่า 4 แท็บ (อังกฤษ) |
| `network/S2COpenSparring`, `C2SSparringApply` | เปิดหน้าตั้งค่า / กดบันทึก |
| `registry/ModEntities` | entity type + attribute |

## Network และ performance

- ตอนสู้ไม่มี packet ของ mod เอง Epic Fight ส่งท่าหนึ่ง packet ต่อท่าให้คนที่ track หุ่น บวก packet ปกติของ mob
- ไม้สั่งหุ่น: `S2COpenSparring` หนึ่งครั้งต่อการคลิกขวา (ถึงคนคลิกคนเดียว), `C2SSparringApply` หนึ่งครั้งต่อการกดบันทึก ขนาดตามจำนวนท่า (ชุดหลัก + pattern สูงสุด 8 ชุด ชุดละไม่เกิน 32 id ≈ 20 KB กรณีแย่สุด ต่ำกว่าเพดาน 32 KB ของ packet ขาไป) decoder ปฏิเสธรายการที่ยาวกว่ากำหนด S2C ส่ง item 6 ชิ้นของหุ่นมาแสดง C2S ส่งแค่เลขช่องกระเป๋า คำสั่ง 5 ช่อง (≤ 512 ตัวอักษร ≈ 7.7 KB กรณีแย่สุด รวมแล้วยังต่ำกว่า 32 KB) ส่งเฉพาะกับคนที่มีสิทธิ์
- คำสั่งของหุ่น (D79) รันตาม event เท่านั้น ไม่รันทุก tick `move` รันหนึ่งครั้งต่อท่า (~1 ครั้ง/วินาทีต่อหุ่นที่กำลังสู้) ภาระขึ้นกับคำสั่งที่ admin ใส่เอง หุ่นเป็นร้อยตัวที่รันคำสั่งหนักทุกท่าทำให้เซิร์ฟช้าได้
- ความเร็วท่า: entity data float 1 ตัว เปลี่ยนเฉพาะเมื่อความเร็วของท่าถัดไปต่างจากเดิม
- AI ทำงานทุก tick เฉพาะตอนมีเป้าหมาย (เหมือน mob ทั่วไป) หุ่นที่ไม่มีเป้าหมายมีแค่ goal มองรอบตัวของ vanilla ไม่ควรวางเป็นร้อยตัว (ต่างจากบล็อกฝึก D16)

## วัดภาระเซิร์ฟ (0.1.27, 2026-11-05)

dev server (`runServer -Puse_epicfight=true`, Epic Fight 20.14.17, ไม่มี mod อื่น) เครื่อง dev, ไม่มีผู้เล่นออนไลน์ (forceload chunk ให้ entity tick) หุ่นจับคู่ตีกันเอง (`target` เป็นอีกตัว ห่างกัน 5 บล็อก, HP 1024, ดาบเหล็ก, ท่า `sword_auto1..3`) วัดด้วย `/forge tps` หลังปล่อยให้สู้ 20 วินาที ค่าหลัง JIT อุ่นเครื่องแล้ว (รอบแรกที่ยังไม่อุ่นสูงกว่านี้ราว 2 เท่า)

| สถานการณ์ | ms/tick | เพิ่มจากโลกว่าง | ต่อตัว |
| :--- | ---: | ---: | ---: |
| โลกว่าง | 0.8 | | |
| 100 ตัวยืนเฉย ไม่มีเป้าหมาย | 1.7 (เทียบโลกว่าง 1.4 ในรอบนั้น) | ~0.3 | ~3 µs |
| 100 iron golem ยืนเฉย (เทียบ mob vanilla + patch Epic Fight) | 2.4 | ~1.0 | ~10 µs |
| 100 ตัวตีกัน (เดิน, พัก 10) | 1.9 | 1.1 | ~11 µs |
| 200 ตัวตีกัน | 3.0 | 2.3 | ~11 µs |
| 200 ตัวตีกัน ความเร็วท่า x2 พัก 0 วิ่ง auto | 3.3 | 2.6 | ~13 µs |
| 200 ตัวตีกัน + pattern 3 ชุด/ตัว (รอบไม่อุ่น) | 7.9 เทียบ 8.1 ไม่มี pattern | ~0 | |
| 400 ตัวตีกัน | 5.4 | 4.6 | ~11 µs |

- งบต่อ tick คือ 50 ms: **400 ตัวที่ตีกันพร้อมกันใช้ราว 9%** บนเครื่อง dev หุ่นที่ยืนเฉยถูกกว่า mob vanilla ทั่วไป pattern แทบไม่มีผล (เช็กเงื่อนไขเป็นแค่การเทียบตัวเลข)
- เซิร์ฟจริงมี mod อื่นที่แปะ capability/event ให้ทุก entity และ CPU ต่างกัน ให้คูณเผื่อ 2–3 เท่า ก็ยังสบายที่หลักร้อยตัว
- หุ่น tick เฉพาะใน chunk ที่อยู่ในระยะ simulation distance ของผู้เล่น (เหมือน mob ทั่วไป) หุ่นในลานฝึกที่ไม่มีใครอยู่ใกล้ไม่กินอะไร
- **ที่ไม่ได้วัด:** network และ FPS ของ client เพราะไม่มีผู้เล่นต่อ ประมาณ: Epic Fight ส่ง packet 1 ตัวต่อท่าให้ทุกคนที่ track หุ่น 200 ตัวที่ตีกันในสายตาผู้เล่นคนเดียว ≈ 200–300 packet/วินาที (เล็ก ไม่กี่สิบ byte) บวก packet ขยับตัวของ vanilla ฝั่ง client การวาดคนแบบ Epic Fight (mesh มีกระดูก) หนักกว่า mob vanilla มาก ถ้าวางหุ่นเป็นร้อยในจุดเดียวที่มีคนยืนดู FPS ของคนดูจะตกก่อนเซิร์ฟ แนะนำให้หุ่นที่เห็นพร้อมกันในจุดเดียวไม่เกินราว 30–50 ตัว

### ที่ optimize ไปใน 0.1.27

- **หาทางเดินใหม่น้อยลง:** pathfinding เป็นส่วนที่แพงที่สุดของการไล่ เดิมหาใหม่ทุก 10 tick ตอนนี้หาใหม่เฉพาะเมื่อเป้าหมายขยับเกิน 1 บล็อกจากปลายทางเดิมหรือเดินถึงแล้ว และเป้าที่ไกลกว่า 16 บล็อกหาห่างขึ้นเป็น 20 tick (แบบ `MeleeAttackGoal`)
- **ไม่ค้น capability ทุก tick:** patch ของ Epic Fight เก็บไว้ใน goal ครั้งเดียว (`EpicFightCompat.patchOf`) แทนการ `getCapability` ทุก tick (ไล่ provider ของทุก mod ที่แปะ capability)
- **ไม่ค้นหาผู้เล่นเมื่อไม่จำเป็น:** goal หาผู้เล่นใกล้สุด (สแกนผู้เล่น + raycast มองเห็น) ไม่ทำงานเลยในโหมด `target none` และเป้าตายตัว
- **Auto run มีสองเกณฑ์:** เริ่มวิ่งเมื่อไกลกว่า 6 บล็อก กลับมาเดินเมื่อใกล้กว่า 4.5 บล็อก ไม่สลับวิ่ง/เดิน (ส่ง flag + attribute ให้ client) ทุก tick เมื่อเป้าหมายยืนอยู่ตรงเส้น
- ที่ถูกอยู่แล้ว: ความเร็วท่าเป็น entity data ที่ส่งเฉพาะเมื่อค่าเปลี่ยน, mixin เป็นแค่ `instanceof` ต่อ animation player, เป้าตายตัวหา UUID ทุก 10 tick (hash lookup)

