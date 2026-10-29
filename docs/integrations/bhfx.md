# การใช้ bhfx

> สถานะ: **แผน** (2026-10-19) ยังไม่ได้เชื่อมจริง อ้างอิงจาก bhfx 0.3.2 และ bridge ของ BHSpells (`../bhfx/docs/integrations/bhspells.md`)

## หลักการ

- bhfx เป็น **soft dependency** mod ต้องเล่นได้ครบโดยไม่มี bhfx ทุกเอฟเฟกต์มี fallback เป็น particle หรือเสียงของ vanilla
- ใช้ได้เฉพาะ `net.deizyn.bhfx.api.*` (`impl.*` เปลี่ยนได้ตลอด)
- ทำ bridge แบบเดียวกับ BHSpells:

| Class | Side | หน้าที่ |
| :--- | :--- | :--- |
| `compat/bhfx/BhfxCompat` | common | ที่ core/game เรียก **ห้าม import bhfx** เช็ก `ModList.get().isLoaded("bhfx")` และ catch `LinkageError` |
| `compat/bhfx/BhfxLoadedBridge` | common | ที่เดียวที่เรียก `BHFx.*` ฝั่ง server |
| `compat/bhfx/BhfxIds` | common | id ของเอฟเฟกต์, `FxKey`, `FxParam` |
| `compat/bhfx/client/BhfxClientCompat` | client | คู่ของ `BhfxCompat` ฝั่ง client |
| `compat/bhfx/client/BhfxClientBridge` | client | ลงทะเบียน Fx (`RegisterFxEvent`) |

## API ที่น่าจะใช้

| ใช้ทำอะไร | API ของ bhfx |
| :--- | :--- |
| สั่น, flash หรือ vignette ตอนโดนลงโทษหรือชนะ (เฉพาะคนเล่น) | `BHFx.pulse(ServerPlayer, ScreenPulse.of().shake(..).flash(..))` |
| เอฟเฟกต์ของบทลงโทษที่ทุกคนรอบๆ เห็น (ระเบิด, ฟ้าผ่า) | `BHFx.start(level, fxId, FxSpawn.at(pos).audience(Audience.tracking()))` |
| เอฟเฟกต์ที่ค้างตลอด session และเป็นส่วนของ gameplay (เช่น heat haze ของ `fire_root`) | `BHFx.start(..., FxSpawn....audience(Audience.only(player)))` + `BHFx.update` + `BHFx.stop` **ต้องเป็น `Audience.only` เสมอ** ตามกฎ immersion (D17) |

## ที่ต้องตัดสินใจตอน P6

- `build.gradle`: ใช้ `compileOnly` + `runtimeOnly fg.deobf("net.deizyn:bhfx:<ver>")` จาก `libs/` แบบเดียวกับ BHSpells
- `mods.toml`: `modId="bhfx" mandatory=false versionRange="[0.3.2,0.4)"`
- task `copyToBHSpells` ของ bhfx copy jar ไปที่ `../BHSpells/libs` เท่านั้น ต้องเพิ่มปลายทาง `../bhgame_mingyue/libs` หรือ copy เอง (ต้องแก้ใน bhfx ไปบันทึกไว้ที่ docs ของ bhfx ด้วย)
- BHSpells ไม่ได้เป็น dependency ของ mod นี้ (ตอนนี้ยังไม่มีอะไรที่ต้องใช้จาก BHSpells)
