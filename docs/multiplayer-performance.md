# Multiplayer, Network และ Performance

> สถานะ: **แผน** (2026-10-19) ทุกข้อในไฟล์นี้เป็น "กฎ" ที่โค้ดต้องทำตาม ถ้าจะฝ่าฝืนต้องบันทึกใน [`decisions.md`](decisions.md)
> เป้าหมาย: เซิร์ฟที่มีผู้เล่น **70 คนขึ้นไป** (เซิร์ฟจริงมีเกือบ 70 คน), **ทุกคนเล่นพร้อมกันได้ (70 session)** และ admin วางหญ้าหรือแท่นฝึกไว้หลายร้อยอัน **ต้องไม่ทำให้ MSPT ของเซิร์ฟเพิ่มขึ้นจนรู้สึกได้ และผู้เล่นต้องรู้สึกว่าจังหวะลื่น**

## 1. ภาพรวม: ใครได้ข้อมูลอะไร

| ข้อมูล | ส่งให้ใคร | วิธีส่ง |
| :--- | :--- | :--- |
| HUD ของ session (cue, HP, gauge, ผล) | **ผู้เล่นคนที่เล่นอยู่คนเดียว** | packet ของ mod ผ่าน `PacketDistributor.PLAYER` |
| diegetic cue บนตัววัตถุ (แสงเรือง, ออร่า, ประกาย) | **คนเล่นคนเดียว** | ไม่มี packet แยก client ของคนเล่นวาดเองจาก `S2CCue` ที่ได้อยู่แล้ว **ห้ามใช้ blockstate หรือ block entity sync** |
| ท่าทางของผู้เล่น | คนที่ track ผู้เล่นอยู่ + ตัวเอง (สำหรับมุมมองบุคคลที่สาม, client ไม่แกว่งแขนตัวเองในมุมมองบุคคลที่หนึ่ง) | `S2CPlayerPose` ตอนเริ่มและจบเท่านั้น (`TRACKING_ENTITY_AND_SELF`, นับเป็น 1 ข้อความ) + ส่งให้คนที่เพิ่งเริ่ม track |
| บทลงโทษและผลลัพธ์ (`burst`, `strikeVisual`, ไฟ, บล็อกหาย, ของหล่น) | ผู้เล่นในระยะใกล้ (≤ 24 บล็อก) | เอฟเฟกต์ระยะใกล้ + ของ vanilla เฉพาะตอนเกิดเหตุการณ์ **ไม่ใช้ `Level.explode` หรือ `LightningBolt`** (คำนวณใส่ทุก entity / ถูก track ไกลมาก และทำร้ายคนยืนดู) |
| ผู้เล่นที่ไม่ได้อยู่ใกล้ | ไม่ได้อะไรเลย | — |

คนรอบข้างเห็นแค่ท่าทางกับผลลัพธ์ ไม่เห็น cue และ HUD ซึ่งทั้งช่วยเรื่อง immersion ([`design/core-mechanics.md`](design/core-mechanics.md) หัวข้อ 9) และทำให้ traffic ต่อ session ไม่เพิ่มตามจำนวนคนที่ยืนดู

## 2. Packet budget

**กฎ:** ไม่มี packet ไหนที่ส่งทุก tick ทั้งขาไปและขากลับ

| Packet | ทิศ | ส่งเมื่อ | ความถี่โดยประมาณ |
| :--- | :--- | :--- | :--- |
| `S2CSessionStart` | S→C | เริ่ม session | ครั้งเดียว |
| `S2CCue` | S→C | มี cue ใหม่ **ส่งล่วงหน้า** (หัวข้อ 4) และส่งได้หลาย cue ใน packet เดียว | 0.5–3 ต่อวินาที |
| `S2CSessionUpdate` | S→C | HP, mistakes หรือผลของ cue เปลี่ยน (รวมผล HIT/MISS ไว้ใน packet นี้ ไม่แยก) | ตามจำนวน cue |
| `S2CGaugeSync` | S→C | gauge เปลี่ยน **อัตรา** (เช่น เริ่มหรือหยุดค้าง) ส่ง `value + ratePerTick` แล้ว client คำนวณต่อเอง | ตามการกด/ปล่อย + resync ทุก 20 tick ระหว่างที่ gauge ขยับ |
| `S2CSessionEnd` | S→C | จบ | ครั้งเดียว |
| `S2CPlayerPose` | S→C (คนรอบข้าง) | เริ่มและจบ session, มีคนเริ่ม track ผู้เล่น | ครั้งเดียวต่อคนดู |
| `C2SInput` | C→S | **ตอนกด** ทุกปุ่ม, **ตอนปล่อย** เฉพาะปุ่มใน `holdKeys`, และไม่ส่งการกดตอนไม่มี cue ถ้า `sendIdlePresses=false` (ดับเทียน: ส่งเฉพาะตอนเส้นเล็งโดนเปลวไฟ) | ตามการกดที่มีความหมาย |
| `C2SAbort` | C→S | กดยกเลิก | ครั้งเดียว |

- **ขนาด packet:** ใช้ `VarInt`, enum เป็น `byte` และ game id เป็น int id จาก registry (ไม่ส่ง string) packet ส่วนใหญ่ไม่เกิน 16 byte `C2SInput` แบบมี yaw/pitch เพิ่ม 8 byte
- **ประมาณการ (หลัง optimize):** ต่อ session ~1.5–5 packet/วินาที, **70 session พร้อมกัน ≈ 100–300 packet/วินาทีทั้งเซิร์ฟ** ต่อผู้เล่นหนึ่งคนประมาณ 100–200 byte/วินาที ไม่ถึง 1% ของ traffic vanilla ต่อผู้เล่น (10–50 KB/วินาที)

### สิ่งที่ตัดสินใจ **ไม่** optimize (ดูเหตุผลใน D26)
- ส่ง seed ให้ client สร้างจังหวะเอง: โค้ด pattern ต้องให้ผลตรงกันเป๊ะสองฝั่ง bug หายาก และ client รู้ cue ทั้งรอบล่วงหน้า
- รวมผลถูก/พลาดหลายจังหวะเป็น packet เดียว: ผลตอบกลับช้าลงจนรู้สึกหน่วง
- encoding พิเศษหรือ bundle packet: ได้ไม่กี่ byte

## 3. ป้องกัน input ขยะและการโกง

`C2SInput` ทุกตัวต้องผ่านการตรวจฝั่ง server ตามลำดับ (ไม่ผ่านข้อไหน = ทิ้งเงียบๆ):
1. ผู้เล่นมี session อยู่จริง และ session อยู่ในสถานะ `RUNNING`
2. key อยู่ในชุดที่ cue ปัจจุบันรับได้ (หรือเป็น key ที่จะนับว่าพลาด)
3. **rate limit:** ไม่เกิน `inputRateLimit` (ค่าเริ่มต้น 20) event ต่อวินาทีต่อผู้เล่น เกินแล้วทิ้ง (กันคนใช้ macro รัวปุ่ม และกัน packet flood)
4. ผู้เล่นยังอยู่ในระยะ `maxDistance` (ค่าเริ่มต้น 6 บล็อก) จากบล็อก

5. **(AIM เท่านั้น)** yaw/pitch ที่แนบมาต้องคลาดจากทิศที่ server รู้ของผู้เล่นไม่เกิน ~15° และเส้นเล็งต้องโดนเป้าจริงโดยไม่มีบล็อกบัง (server raycast เอง)

handler ของ packet ต้องทำงานใน main thread (`ctx.enqueueWork`) และห้ามเชื่อค่าใดๆ ที่ client ส่งมาโดยไม่ตรวจ

## 4. ความลื่นไหลและการชดเชย ping

### 4.1 ส่ง cue ล่วงหน้า (lead time)

server ไม่ได้ส่ง cue ตอนที่กรอบเวลาเปิด แต่ส่ง **ล่วงหน้า `leadTicks`** (ค่าเริ่มต้น 6 tick = 300ms) พร้อมบอกว่า "กรอบเปิดในอีก N tick"
- client ได้รับก่อนกรอบเปิด → แสดง telegraph (ลูกศรค่อยๆ ขยาย, หญ้าค่อยๆ สว่าง) แล้วกรอบเปิดตรงเวลาบนจอ
- server เปิดกรอบตาม tick ของตัวเอง และรับ input ใน `[open, close + pingGrace]`
- ผลลัพธ์: คนที่ ping 150ms เห็น cue ตรงเวลาเท่ากับคน ping 20ms และกรอบเวลาไม่ต้องกว้างจนง่ายเกินไป

### 4.2 Rhythm ส่งเป็นชุด

เกมที่มีจังหวะ (`pulse_grass`) ส่งตารางจังหวะ 3–4 จังหวะถัดไปใน `S2CCue` ครั้งเดียว client animate ได้เนียนเป๊ะโดยไม่ต้องรอ packet ทุกจังหวะ

### 4.3 Feedback ทันทีฝั่ง client

- พอกดปุ่ม client **เล่นเสียงและ animation ของการกดทันที** (ไม่รอ server)
- ผล HIT/MISS (เสียงถูก/ผิด, ตัวเลข HP) มาตอน server ตอบกลับ ซึ่งช้ากว่าประมาณหนึ่ง ping
- ห้าม client ทำนายผล HIT/MISS เอง เพราะถ้าทายผิดแล้วต้องย้อน จะรู้สึกแย่กว่าช้านิดเดียว

### 4.4 Animation ใช้เวลาฝั่ง client

- ภาพ diegetic (แสง, ตำแหน่งหญ้า, ลูกแก้ว) และ HUD interpolate ด้วย `partialTick` ทุกเฟรม ไม่กระตุกเป็นขั้นทีละ tick
- gauge ใช้ `value + ratePerTick` ที่ server ส่งมา แล้วคำนวณเองทุกเฟรม พอ resync มาถ้าต่างกันเล็กน้อยให้ค่อยๆ ไล่ไปหา ไม่กระโดด

### 4.5 เวลาทั้งหมดฝั่ง server นับเป็น tick

- ไม่ใช้ `System.currentTimeMillis()` ตัดสินผลเด็ดขาด ถ้าเซิร์ฟ lag (TPS ตก) ทุกอย่างช้าลงพร้อมกันอย่างคงเส้นคงวา ไม่ใช่พลาดเพราะ lag
- client แปลง tick → เวลาบนจอด้วยจังหวะ tick ของ client เอง (ซึ่งตาม server อยู่แล้ว)

### 4.6 ปล่อยปุ่มให้สะอาด

ตอน session จบหรือถูกยกเลิก ต้อง reset สถานะ `KeyMapping` ที่ถูกล็อกไว้ทั้งหมด (`setDown(false)`) ไม่งั้นผู้เล่นจะเดินหรือตีค้างเองหลังจบเกม

## 5. Server CPU

### 5.1 บล็อกที่วางไว้ต้องไม่กินอะไรเลย

- training object เป็น **บล็อก + block entity ที่ไม่ tick** (ไม่มี `getTicker`) ตอนไม่มีคนเล่น ต้นทุนเท่ากับดอกไม้ธรรมดา วางกี่ร้อยอันก็ไม่เพิ่ม MSPT
- block entity เก็บแค่ NBT config กับ UUID ของคนที่ใช้อยู่ **ไม่ sync ข้อมูล session ไปให้ client** (`getUpdatePacket` ส่งแค่สิ่งที่ renderer ต้องรู้ตอนโหลด chunk ซึ่งแทบไม่มี)
- **ห้ามเปลี่ยน blockstate ระหว่างเล่น** เพราะ blockstate ส่งให้ทุกคนในระยะและทำให้ chunk section rebuild
- HP เป็นข้อมูลของ session เท่านั้น

### 5.2 Session tick

- `SessionManager` tick เฉพาะ session ที่ active ผ่าน `TickEvent.ServerTickEvent` (phase `END`) ครั้งเดียวต่อ tick ไม่ให้แต่ละบล็อก tick session เอง
- **ทำงานเฉพาะ tick ที่มีเหตุการณ์:** แต่ละ session เก็บ `nextEventTick` (cue ถัดไปต้องส่ง, กรอบเวลาปิด, gauge ถึงเกณฑ์, หมดเวลา) tick อื่นเช็กแค่ตัวเลขตัวเดียวแล้วข้าม ส่วน input ที่เข้ามาจัดการทันทีใน handler ไม่ต้องรอ tick
- การสแกนเป้า (`TargetScanner`) ทำครั้งเดียวตอนเริ่ม session ห้ามสแกนซ้ำระหว่างเล่น
- ใน hot path (`Pattern.tick`, `CueJudge`) **ห้ามสร้าง object ใหม่ทุก tick:** ไม่ใช้ stream, lambda ที่จับตัวแปร หรือ `new` ใน loop ใช้ object ซ้ำ และสุ่มด้วย `RandomSource` ของ session
- งานหนัก (โยน loot, เขียน scoreboard, log) ทำ **ครั้งเดียวตอนจบ** เท่านั้น

### 5.3 จำกัดจำนวน (กันกรณีผิดปกติ)

- config `maxConcurrentSessions` (ค่าเริ่มต้น 0 = ไม่จำกัด) เผื่อเซิร์ฟเล็กอยากจำกัด
- ผู้เล่นหนึ่งคนมีได้หนึ่ง session, บล็อกหนึ่งอันมีได้หนึ่ง session (ตรวจทั้งสองทาง)

## 6. หน่วยความจำของเซิร์ฟ (กัน memory leak)

- `SessionManager` เก็บ `Map<UUID, TrainingSession>` (key = UUID ของผู้เล่น) และ block entity เก็บแค่ UUID ของผู้เล่นที่ใช้อยู่
- **session เก็บ UUID + `BlockPos` + dimension ไม่เก็บ reference ของ `ServerPlayer` หรือ `BlockEntity` ไว้ยาว** ดึงใหม่ **เฉพาะตอนที่ต้องใช้** (tick ที่มีเหตุการณ์, ตอนรับ input) จาก `server.getPlayerList().getPlayer(uuid)` และ `level.getBlockEntity(pos)` (เช็ก `isLoaded(pos)` ก่อน) ถ้าได้ null หรือเป็นบล็อกอื่นแล้ว = ยกเลิก session
- ต้องลบ session ทุกทาง: จบปกติ, `PlayerLoggedOutEvent`, `LivingDeathEvent`, `PlayerChangedDimensionEvent`, บล็อกถูกลบ (`onRemove`) หรือ chunk unload, `ServerStoppingEvent`
- UUID ของผู้เล่นที่ block entity เก็บไว้ **ไม่เซฟลง NBT** (transient) ถ้าเซิร์ฟปิดกลางคัน บล็อกจะไม่ติดล็อกค้าง
- **sweep สำรอง:** ทุก 100 tick ตรวจ session ทั้งหมด ตัวไหนที่ผู้เล่นหรือบล็อกหายไปแล้ว → ยกเลิกและลบ
- ฝั่ง client: `ClientSession` เป็น singleton ตัวเดียว ล้างตอน session จบ และตอน `ClientPlayerNetworkEvent.LoggingOut`
- ไม่ใช้ `SavedData`: session ไม่ต้องเซฟ ถ้าเซิร์ฟปิดกลางคัน session หายไปเฉยๆ และหญ้ายังอยู่
- NBT ของ block entity เก็บแค่ config (ไม่กี่ field) หญ้าหลายร้อยต้นจึงแทบไม่กินพื้นที่

## 7. ฝั่ง client (FPS)

- ตอนไม่มีคนเล่น บล็อกวาดด้วย block model ธรรมดา (อยู่ใน chunk mesh) **ไม่มี BlockEntityRenderer ทำงาน** หญ้าหลายร้อยต้นจึงเบาเท่าดอกไม้
- `TrainingBlockRenderer` return ทันทีถ้า `ClientSession` ไม่ได้ชี้มาที่บล็อกนั้น (บน client หนึ่งเครื่องมี BER ที่วาดจริงได้มากสุดอันเดียว)
- ถ้าอยากมี particle บรรยากาศตอนรอ (เช่น ละอองเรืองแสงจางๆ) ให้ใช้ `animateTick` ของบล็อก (ฝั่ง client อยู่แล้ว ไม่มี packet) และใช้ความถี่ต่ำ
- เอฟเฟกต์ bhfx แบบค้าง (`FxKey`) ใช้ **เฉพาะระหว่าง session** ไม่ใช้กับหญ้าที่วางนิ่งๆ หลายร้อยต้น
- HUD ไม่สร้าง object ใหม่ทุกเฟรม texture ของ HUD รวมไว้ในไฟล์เดียว (sprite sheet)
- **ท่าทางผู้เล่น:** ไม่เล่นท่าของ playerAnimator กับผู้เล่นที่อยู่ห่างเกิน 32 บล็อก (ไกลขนาดนั้นมองไม่เห็นรายละเอียด) ใช้ท่าปกติแทน
- **particle:** จำกัดจำนวนต่อเฟรมทั้งของ cue และของบทลงโทษ (ค่าเริ่มต้น 64 ตัวต่อเฟรมจาก mod นี้) และไม่สร้าง particle ของบทลงโทษถ้าอยู่ห่างเกิน 24 บล็อก ลานฝึกที่มีคนเยอะจะได้ไม่ทำให้ FPS ตก

## 8. เครื่องมือตรวจสอบ

- `/bhgame debug` (ทำแล้ว 2026-10-19): จำนวน session, จำนวนข้อความที่ส่งออกและรับเข้าในวินาทีที่ผ่านมาและรวมทั้งหมด (นับใน `BhGameNetwork` ข้อความ broadcast นับเป็น 1 ไม่ได้นับต่อผู้รับ) และรายการ session (สูงสุด 20 รายการ: ผู้เล่น, เกม, ตำแหน่ง, สถานะ, HP, mistake, event ถัดไปในอีกกี่ tick) จำนวนบล็อกที่ถูกล็อกเท่ากับจำนวน session จึงไม่ได้แสดงแยก
- `/bhgame stress <n> [game]` และ `/bhgame stress stop` (**ทำแล้ว 2026-10-19**, ใช้ได้เองใน dev environment ส่วนเซิร์ฟจริงต้องเปิด config `enableStressCommand` ค่าเริ่มต้น `false`): สร้าง session จำลอง n ตัวที่ไม่มีผู้เล่นจริงและไม่มีบล็อก (`StressTest`) แต่ละตัวรัน pattern จริง bot กดปุ่มสุ่มทุก 8–27 tick เข้า pattern โดยตรง จึงมีทั้ง hit และพลาด session จบแล้วเริ่มใหม่ให้ครบ n เสมอ ใช้วัด CPU ด้วย spark ส่วน packet ไม่ได้ส่งจริง แต่ `/bhgame debug` แสดงจำนวนที่ "จะส่ง" ต่อวินาที
- ใช้ mod **spark** วัด MSPT และ profile ตอนทดสอบโหลด และ **เปิด spark บนเซิร์ฟจริงช่วงจัด event ครั้งแรก** เพื่อเก็บตัวเลขจริง
- checklist การทดสอบโหลดอยู่ใน [`testing.md`](testing.md)

## 9. ค่า config ที่เกี่ยวข้อง (server)

| ค่า | ค่าเริ่มต้น | ใช้ทำอะไร |
| :--- | :--- | :--- |
| `leadTicks` | 6 | ส่ง cue ล่วงหน้ากี่ tick |
| `maxPingGraceMs` | 250 | ขยายกรอบเวลาให้คน ping สูงได้สูงสุดเท่าไร |
| `inputRateLimit` | 20 | input event ต่อวินาทีต่อผู้เล่น |
| `maxDistance` | 6.0 | ระยะสูงสุดจากบล็อกระหว่างเล่น |
| `maxConcurrentSessions` | 0 | 0 = ไม่จำกัด |
| `partnerWaitTicks` | 600 | เกมหลายคนรอคู่ได้นานเท่าไร |
| `gaugeResyncTicks` | 20 | (P2b, ยังไม่มีใน config) resync gauge ทุกกี่ tick ระหว่างที่ขยับ |
| `aimToleranceDegrees` | 15 | (P2b, ยังไม่มีใน config) มุมที่ yaw/pitch ของ AIM คลาดจากทิศที่ server รู้ได้ |
| `enableStressCommand` | false | เปิด `/bhgame stress` (dev เท่านั้น) |

ไฟล์: `run/world/serverconfig/bhgame-server.toml` (sync ไป client อัตโนมัติ) ค่าที่มีจริงอยู่ใน `core/config/BhGameConfig.java`

**client config** (`config/bhgame-client.toml`): `showKeyHints` (true), `poseAnimationDistance` (32), `maxParticlesPerFrame` (64, ยังไม่มี)
