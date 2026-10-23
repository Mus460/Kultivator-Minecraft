# Sekte Langit (DESIGN 37) — voice lines requested for the voice pass

Not added to sounds.json or tools/gen_voices.py (voice pass only). Until a clip exists the code falls back to the
listed existing line (LangitSuaraServer.pick / LangitAngkat bells), so nothing is silent or broken.
Caption keys: `kultivator.voice.<key>` (line text) + `subtitles.kultivator.voice.<key>` (speaker), id_id + en_us.

| key | speaker | caption id | fallback | Japanese |
|---|---|---|---|---|
| heaven.langit_naik | Langit (Heaven) | kultivator.voice.heaven.langit_naik | bells + amethyst chime | 山は雲を越え、宗門は天に昇る。 |
| sekte_langit.bajak_datang | Lei Sha, Kapten Bajak Awan | kultivator.voice.sekte_langit.bajak_datang | rogue.ambush | 倉の中身を差し出せ！さもなくばこの島を焼き払う！ |
| sekte_langit.longbo_datang | Raksasa Longbo | kultivator.voice.sekte_langit.longbo_datang | boss.awaken | 祖先は山を支える亀を六匹釣った。お前の亀が七匹目だ！ |
| sekte_langit.longbo_kalah | Raksasa Longbo | kultivator.voice.sekte_langit.longbo_kalah | boss.defeat | 馬鹿な…亀一匹さえ釣れぬとは… |
| sekte_langit.tamu_salam | Tetua Tamu (sekte langit NPC) | kultivator.voice.sekte_langit.tamu_salam | elder.welcome | 雲の上の同道よ、お招きに感謝する。 |
| sekte_langit.utusan_salam | Xian Lingyue, Utusan Istana Langit | kultivator.voice.sekte_langit.utusan_salam | elder.welcome | 天宮はそなたの島を見ていた。天命を受けよ。 |
| sekte_langit.utusan_tantang | Langit (Heaven) | kultivator.voice.sekte_langit.utusan_tantang | heaven.tribulation_begin | 天は聞いた。黄昏に備えよ。 |

Suggested captions (line text):
- heaven.langit_naik — id "Gunung melampaui awan; sekte naik ke langit." / en "The mountain passes the clouds; the sect ascends."
- sekte_langit.bajak_datang — id "Serahkan isi gudang kalian, atau pulau ini kami bakar!" / en "Hand over your storehouse, or we burn this island!"
- sekte_langit.longbo_datang — id "Leluhurku memancing enam kura-kura penyangga gunung. Kura-kuramu yang ketujuh!" /
  en "My ancestor fished up six mountain-bearing turtles. Yours will be the seventh!"
- sekte_langit.longbo_kalah — id "Mustahil... seekor kura-kura pun tak terpancing..." / en "Impossible... not even one turtle..."
- sekte_langit.tamu_salam — id "Sahabat di atas awan, terima kasih atas sambutanmu." / en "Fellow daoist above the clouds, thank you for the welcome."
- sekte_langit.utusan_salam — id "Istana Langit telah melihat pulaumu. Terimalah perintahnya." / en "The Heavenly Court has seen your island. Receive its command."
- sekte_langit.utusan_tantang — id "Langit telah mendengar. Bersiaplah saat senja." / en "Heaven has heard. Prepare yourself at dusk."

Speaker captions (subtitles.*): Langit / Heaven; Kapten Bajak Awan / Cloud Pirate Captain; Raksasa Longbo / Longbo Giant;
Tetua Tamu / Guest Elder; Utusan Istana Langit / Heavenly Court Envoy.
