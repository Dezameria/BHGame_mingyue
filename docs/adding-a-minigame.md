# วิธีเพิ่มมินิเกมใหม่

ใช้ checklist นี้ทุกครั้งที่เพิ่มเกม ถ้าขั้นตอนไหนต้องแก้ไฟล์ใน `core/` แปลว่าเกมนี้ต้องการกฎใหม่ ให้หยุดแล้วบันทึกเหตุผลใน [`decisions.md`](decisions.md) ก่อน

## 1. ออกแบบ
- [ ] ก๊อป [`design/minigames/_template.md`](design/minigames/_template.md) เป็น `design/minigames/<game_id>.md` แล้วกรอกให้ครบ
- [ ] ตรวจว่าใช้แค่ `LMB`/`RMB`/`SPACE`/WASD, cue 5 ชนิด และ channel 4 แบบที่มีอยู่ ([`design/core-mechanics.md`](design/core-mechanics.md)) ถ้าไม่ได้ → ไปหัวข้อ "จุดขยาย" ใน [`architecture.md`](architecture.md)
- [ ] กำหนด input profile (`camera`, `holdKeys`, `sendIdlePresses`, ...), `PlayerPose` และคำใบ้ตอนเริ่ม
- [ ] ตรวจว่าไม่ซ้ำกับเกมที่มีอยู่ (เช่น หญ้าพันรากกับหุ่นไม้)
- [ ] บทลงโทษใช้แค่รายการใน `Penalties`
- [ ] เพิ่มแถวในตาราง [`design/minigames/README.md`](design/minigames/README.md) และงานใน [`roadmap.md`](roadmap.md)

## 2. โค้ดฝั่ง server
- [ ] สร้าง `game/<game_id>/<Name>Block.java` extends `HerbBlock` หรือ `StationBlock` กำหนด shape, บทลงโทษเล็ก, ใหญ่ และตอนทุบตรงๆ (ใช้ `burst`/`strikeVisual` ห้ามใช้ระเบิดหรือฟ้าผ่าจริง)
- [ ] ถ้าเป้าอยู่ในโลก: สร้าง `TargetScanner` ของเกม
- [ ] เกมหลายคน: override `requiredPlayers()` และ `inputProfile(role)` แล้วใช้ role mask ใน cue (ดู `game/twin_grass/`)
- [ ] สร้าง `game/<game_id>/<Name>Pattern.java` ลองประกอบจาก `pattern/common/` ก่อน (เกมแบบจังหวะใช้ `BeatTrack` ซึ่งตัดสินผลให้แล้ว) เขียนเองเฉพาะส่วนที่ไม่มี pattern ใช้ได้แค่ `PatternContext` ห้ามแตะ `ServerPlayer` หรือ `Level`
- [ ] ลงทะเบียนใน `registry/Minigames.java` หนึ่งบรรทัด: `register("<game_id>", () -> new <Name>Block(HerbBlock.herbProperties()))`
- [ ] ถ้า pattern มีกฎใหม่ที่ `BeatTrack` ไม่ครอบคลุม เขียน JUnit test แบบ `src/test/.../BeatTrackTest.java` (ใช้ `PatternContext` ปลอม ไม่ต้องเปิดเกม) แล้วรัน `./gradlew.bat test`
- [ ] ตรวจกฎใน [`multiplayer-performance.md`](multiplayer-performance.md): ไม่ส่ง packet ทุก tick, ส่ง cue ล่วงหน้า, ไม่สร้าง object ใน `Pattern.tick`, ไม่เปลี่ยน blockstate ระหว่างเล่น
- [ ] ตรวจกฎ immersion ([`design/core-mechanics.md`](design/core-mechanics.md) หัวข้อ 0 และ 9): cue แสดงบนตัววัตถุและเห็นเฉพาะคนเล่น, ไม่มีแถบหรือตัวเลข, เลือก `PlayerPose` ให้คนภายนอกเห็น

## 3. โค้ดฝั่ง client (ส่วนใหญ่ไม่ต้องทำ)
- [ ] ถ้าต้องใช้ `CueVisual` ใหม่: เพิ่ม enum + วาดใน `CueVisualRenderer`
- [ ] ถ้าต้องมีภาพ diegetic เฉพาะ: `client/render/<game_id>/`
- [ ] ถ้าต้องใช้ท่าทางผู้เล่นแบบใหม่: เพิ่มใน `PlayerPose` + `player_animation/<pose>.json`

## 4. Resources
- [ ] `lang/en_us.json` และ `lang/th_th.json`: ชื่อบล็อก, ข้อความผลลัพธ์ใน action bar (ใช้ถ้อยคำในเรื่อง)
- [ ] `blockstates/<game_id>.json`, `models/block/`, `models/item/`, `textures/block/<game_id>*.png`
- [ ] `loot_tables/blocks/<game_id>.json` ที่ว่าง (ทุบแล้วไม่ drop)
- [ ] `sounds/<game_id>/` + เพิ่มใน `sounds.json` และ `ModSounds`
- [ ] `data/bhgame/loot_tables/minigames/<game_id>/perfect.json` และ `normal.json`

## 5. ทดสอบและบันทึก
- [ ] เพิ่ม checklist ของเกมใน [`testing.md`](testing.md) แล้วทดสอบใน singleplayer ก่อน แล้วค่อยบนเซิร์ฟที่มี ping
- [ ] อัปเดตสถานะในสเปกเกม, [`roadmap.md`](roadmap.md) และ [`worklog.md`](worklog.md)
