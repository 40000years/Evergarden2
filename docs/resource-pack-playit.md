# แก้ FAILED_DOWNLOAD ผ่าน Playit

ตรวจ URL จาก log วันที่ 26 ตุลาคม 2026 แล้ว HTTP HEAD ผ่าน IPv6 และ GET ผ่าน IPv4 ไปยัง `vivo-radiance.tun.ply.gg:34756` ถูกปิดด้วย Connection reset ก่อนมี HTTP response การอ่านสถานะ Minecraft จากภายนอกในการตรวจครั้งนี้ก็ถูก reset เช่นกัน จึงยังฟันธงไม่ได้ว่าเป็นการกรอง HTTP โดย tunnel หรือปลายทางหยุดทำงานอยู่ ต้องเปรียบเทียบกับ localhost บนเครื่องเซิร์ฟเวอร์

Advance Magic ล้มด้วย FAILED_DOWNLOAD ก่อน จากนั้น Minecraft ยกเลิก Evergarden ที่ยังโหลดไม่เสร็จเป็น DISCARDED ส่วน ModelEngine สร้างแพ็กเสร็จภายหลัง แยกจากการล้มของ Advance Magic ครั้งนี้

## ยืนยันบนเครื่องเซิร์ฟเวอร์

1. ดู `server-port` ใน `server.properties` และบรรทัดเริ่มต้นของปลั๊กอินว่าขึ้น `served through the Minecraft port` หรือ `served on TCP ...`
2. ถ้าแจกผ่านพอร์ตเกม ให้ลอง URL จาก log โดยเปลี่ยน host และ port เป็น `127.0.0.1:<server-port>` บนเครื่องเซิร์ฟเวอร์ เช่น `curl.exe -I http://127.0.0.1:25565/advance-magic/<SHA-1-จาก-log>.zip`
3. ถ้า localhost ตอบ 200 แต่ URL สาธารณะถูก reset ให้แก้ tunnel/public URL ด้านล่าง ถ้า localhost ก็ล้ม ให้ตรวจว่าใช้ JAR ล่าสุดและ host เริ่มทำงานสำเร็จ รวมถึง log `Pack HTTP` ก่อนสรุปว่าเป็น tunnel

## แยกช่องดาวน์โหลดที่รองรับ HTTP

ใช้ tunnel ชนิด TCP ทั่วไปสำหรับแพ็กสองช่อง โดย agent และเซิร์ฟเวอร์ต้องเข้าถึงพอร์ตภายในเดียวกันได้ หากอยู่คนละ container ให้ใช้ที่อยู่ container/host ที่เข้าถึงเซิร์ฟเวอร์ได้

| แพ็ก | พอร์ตปลายทางภายใน |
|---|---:|
| Advance Magic | 8187 |
| Evergarden + Judge/ModelEngine | 8188 |

นำ address และ **พอร์ตสาธารณะ** ที่ Playit ออกให้มาใส่ `public-url` ไม่ใช่พอร์ตภายใน ตัวอย่างต่อไปนี้เป็น fragment สำหรับแก้เฉพาะค่าที่มีอยู่ ไม่ใช่ไฟล์ config ทั้งฉบับ และต้องเปลี่ยน placeholder ก่อนใช้

`plugins/advance-magic/config.yml`:

```yaml
resource-pack:
  enabled: true
  url: ''
  sha1: ''
  host:
    enabled: true
    use-server-port: false
    bind: '0.0.0.0'
    port: 8187
    public-url: 'http://MAGIC_TUNNEL_HOST:PUBLIC_PORT'
```

`plugins/Evergarden/config.yml`:

```yaml
resource-pack:
  enabled: true
  url: ''
  sha1: ''
  host:
    enabled: true
    use-server-port: false
    bind: '0.0.0.0'
    port: 8188
    public-url: 'http://GARDEN_TUNNEL_HOST:PUBLIC_PORT'
```

Judge ใช้ช่องดาวน์โหลดของ Evergarden ร่วมกัน ถ้าเคยกำหนด `world-boss.visuals.pack.host.public-url` หรือ `public-host` แยกไว้ ให้ล้างสองค่านั้นเป็น `''` เพื่อให้ใช้ค่าของ Evergarden

รีสตาร์ตเซิร์ฟเวอร์ แล้วดู `/magic pack` กับ `/evergarden pack` เปิด URL ที่แสดงจากเครื่องผู้เล่น ต้องดาวน์โหลด ZIP ได้จริง แล้วจึงลองเข้าเกมอีกครั้ง

ถ้าบัญชีไม่มี TCP ทั่วไป ใช้โฮสต์ HTTP(S) ภายนอกหรือ reverse proxy ที่เข้าถึงพอร์ตดังกล่าวแทนได้ ดู [คู่มือ HTTPS tunnel ของ Playit](https://playit.gg/support/https-tunnel/) ซึ่งระบุว่าฟีเจอร์ HTTPS tunnel ต้องใช้ Premium

## ให้เข้าเล่นชั่วคราวระหว่างแก้

หลังการย้าย config บังคับแพ็กครั้งแรกแล้ว หากต้องการให้แพ็กเป็นทางเลือกชั่วคราว ให้ตั้ง `resource-pack.required: false` ในทั้งสองปลั๊กอิน และ `world-boss.visuals.pack.required: false` ใน Evergarden แล้วรีสตาร์ต ผู้เล่นที่โหลดแพ็กไม่ได้จะเห็นภาพสำรองหรือขาดโมเดลบางส่วน ค่านี้เปลี่ยนเงื่อนไขการเข้าเล่น แต่ไม่ได้ซ่อมช่องดาวน์โหลด
