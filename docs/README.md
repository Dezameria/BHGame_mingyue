# bhgame_mingyue — คลังความรู้การพัฒนา

Minecraft mod (Forge 1.20.1) สำหรับมินิเกมแนวจอมยุทธ ใช้บนเซิร์ฟโรลเพล Mingyue Eclipse เล่นร่วมกับ BHSpells (`../BHSpells`) และ bhfx (`../bhfx`)

**ก่อนเริ่มงาน ให้อ่านไฟล์นี้ก่อน แล้วค่อยเปิดเฉพาะหมวดที่ต้องใช้** **พอทำเสร็จ ให้บันทึกผลลงหมวดที่ถูกต้อง**

## หมวดเอกสาร

| ไฟล์ | เก็บอะไร | อัปเดตเมื่อ |
| :--- | :--- | :--- |
| [`overview.md`](overview.md) | วิสัยทัศน์, หลักการออกแบบ, คำศัพท์ | หลักการเปลี่ยน |
| [`design/core-mechanics.md`](design/core-mechanics.md) | กลไกกลาง: HP + Pattern, ชนิด cue, ปุ่ม, ผลลัพธ์, บทลงโทษ, การตั้งค่าของ admin | กฎกลางเปลี่ยน |
| [`design/minigames/`](design/minigames/) | สเปกของมินิเกมแต่ละเกม เกมละไฟล์ (ใช้ [`_template.md`](design/minigames/_template.md)) | เพิ่มหรือแก้เกม |
| [`architecture.md`](architecture.md) | โครงสร้าง package และไฟล์, class หลัก, network, lifecycle ของ session | โครงสร้างโค้ดเปลี่ยน |
| [`multiplayer-performance.md`](multiplayer-performance.md) | กฎเรื่องหลายผู้เล่น: packet budget, ความลื่นไหลและการชดเชย ping, CPU, memory ของเซิร์ฟ, การทดสอบโหลด | กฎเรื่อง network หรือ performance เปลี่ยน |
| [`adding-a-minigame.md`](adding-a-minigame.md) | ขั้นตอนเพิ่มมินิเกมใหม่แบบ checklist | ขั้นตอนเปลี่ยน |
| [`integrations/bhfx.md`](integrations/bhfx.md) | วิธีใช้ bhfx แบบ soft dependency | เริ่มใช้ bhfx เพิ่ม หรือ API ของ bhfx เปลี่ยน |
| [`integrations/playeranim.md`](integrations/playeranim.md) | ท่าทางของผู้เล่นที่คนภายนอกเห็น (playerAnimator) และความเสี่ยงกับ Epic Fight | เพิ่มท่าใหม่ หรือพบปัญหากับ mod animation อื่น |
| [`integrations/epicfight.md`](integrations/epicfight.md) | Epic Fight: ท่าต่อยบนหุ่นไม้ (soft dependency, เล่นท่าฝั่ง client) | แก้ท่าหรืออัปเดตเวอร์ชัน Epic Fight |
| [`roadmap.md`](roadmap.md) | Phase งาน, งานค้าง, backlog, คำถามที่รอผู้ใช้ตอบ | เพิ่ม/ปิดงาน หรือแผนเปลี่ยน |
| [`decisions.md`](decisions.md) | การตัดสินใจด้านการออกแบบพร้อมเหตุผล | มีการตัดสินใจที่มีผลระยะยาว |
| [`testing.md`](testing.md) | สถานะการทดสอบ (✅ ผ่าน / ⏳ ยังไม่ทดสอบ) และ checklist ในเกม | ทดสอบอะไรไป หรือมีงานใหม่ที่ต้องทดสอบ |
| [`worklog.md`](worklog.md) | บันทึกงานรายวัน ใหม่สุดอยู่บน | จบงานทุกครั้ง |

## กฎการบันทึก

1. **ข้อเท็จจริงหนึ่งอย่างอยู่ที่เดียว** ที่อื่นให้ลิงก์มา อย่าก๊อปซ้ำ
2. **เขียนวันที่เต็มเสมอ** (`2026-10-19`) ห้ามเขียน "เมื่อวาน" หรือ "ล่าสุด"
3. **ชื่อ class, method, ไฟล์ และคำสั่ง** เขียนตามโค้ดเป๊ะๆ ใน `backticks`
4. **ข้อความที่ไม่จริงแล้วให้แก้หรือขีดทิ้ง** อย่าปล่อยสองบรรทัดที่ขัดกันไว้
5. **บอกระดับความมั่นใจ** แยก "build ผ่าน", "ทดสอบในเกมแล้ว" และ "ยังไม่ได้ทดสอบ" ให้ชัด
