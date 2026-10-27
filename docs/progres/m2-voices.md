# Voices of DESIGN 41 + 42 — lines for the voice pass (F-END only)

Owners: 41V-P (section 41), 42V-B / 42V-F / D-42V (sections 42). Nobody but **F-END** edits `tools/gen_voices.py` or
`assets/kultivator/sounds.json` or runs VOICEVOX. Until a clip exists every caller falls back to its listed line.

D-42V check: every `Voice.say` / `VoiceEvents.exists` key in the code is listed below, and `VoiceEvents.GABUNG` (28 keys) and
`VoiceEvents.KOTA42` (105 keys) equal the tables exactly (133 clips in all; the 5 additions of D-41C and 42D-P are in the lists
since D-42V). Nothing else in the code asks for a `gabung.*` / `kota42.*` voice.

## 41 Gabung Sekte (41.11.7, 26 lines, 41V-P)

Group `GABUNG` in `voice/VoiceEvents.java` (keys `gabung.<line>`). It is registered only while
`VoiceEvents.GABUNG_SIAP` is true: **F-END sets it to `true`** after the 26 clips are generated, otherwise `exists()`
stays false and the callers (`Masuk.ucap`, `Voice.say`) keep their fallback. Caption keys `kultivator.voice.gabung.<line>`
(line text) + `subtitles.kultivator.voice.gabung.<line>` (speaker) go into lang `sekte_gabung_suara` (written by
`gen_voices.py`, id_id + en_us). `gen_voices.py` `WORLD` tuple: `(key, speaker, ja, en, id, emotion)`.

| key | speaker (voice) | caption id | fallback | emotion | Japanese |
|---|---|---|---|---|---|
| gabung.gerbang_panggil | Penjaga Gerbang Sekte (m1) | kultivator.voice.gabung.gerbang_panggil | kotabesar.recruit_call | angry | 入門試験を受ける者は、前へ出よ！ |
| gabung.tangga_tekanan | Tetua Penerimaan (elder) | kultivator.voice.gabung.tangga_tekanan | elder.welcome | calm | 心を静めよ。山はお前を試している。 |
| gabung.ujian_mulai | Murid Pengawas (m2) | kultivator.voice.gabung.ujian_mulai | disciple.m2.cheer | normal | 試験、始め！ |
| gabung.ujian_lulus | Tetua Penerimaan (elder) | kultivator.voice.gabung.ujian_lulus | elder.advice | calm | ふむ…悪くない。 |
| gabung.ujian_gagal | Tetua Penerimaan (elder) | kultivator.voice.gabung.ujian_gagal | elder.advice | calm | 縁がなかったようだな。三日後にまた来い。 |
| gabung.diterima | Tetua Penerimaan (elder) | kultivator.voice.gabung.diterima | elder.welcome | joy | 今日より、お前は我が宗門の弟子だ。 |
| gabung.istimewa | Tetua Penerimaan (elder) | kultivator.voice.gabung.istimewa | elder.welcome | joy | これほどの才…誰が弟子に取るか、揉めそうだな。 |
| gabung.sumpah_aturan_1 | Tetua Hukum (elder) | kultivator.voice.gabung.sumpah_aturan_1 | elder.advice | calm | 同門を害するなかれ。 |
| gabung.sumpah_aturan_2 | Tetua Hukum (elder) | kultivator.voice.gabung.sumpah_aturan_2 | elder.advice | calm | 宗門の秘伝を漏らすなかれ。 |
| gabung.sumpah_aturan_3 | Tetua Hukum (elder) | kultivator.voice.gabung.sumpah_aturan_3 | elder.advice | calm | 宗門の危機には、必ず駆けつけよ。 |
| gabung.shifu_pilih | Shifu (elder) | kultivator.voice.gabung.shifu_pilih | elder.inheritance | joy | わしの弟子にならぬか？ |
| gabung.shifu_lindungi | Shifu (elder) | kultivator.voice.gabung.shifu_lindungi | elder.advice | angry | わしの弟子に手を出すとは、いい度胸だ。 |
| gabung.shifu_lampaui | Shifu (elder) | kultivator.voice.gabung.shifu_lampaui | elder.inheritance | joy | はっはっは！弟子が師を超えたか！ |
| gabung.sapa_adik | Kakak Seperguruan (f1) | kultivator.voice.gabung.sapa_adik | disciple.f1.greet | joy | 師弟、おはよう！ |
| gabung.sapa_kakak | Adik Seperguruan (m3) | kultivator.voice.gabung.sapa_kakak | disciple.m3.greet | normal | 師兄、お疲れ様です。 |
| gabung.saingan_ejek | Saingan Seperguruan (m3) | kultivator.voice.gabung.saingan_ejek | disciple.m3.seteru_ejek | angry | 雑用係がここで何をしている？ |
| gabung.panggilan_perang | Penjaga Gerbang Sekte (m1) | kultivator.voice.gabung.panggilan_perang | disciple.m1.attack | angry | 敵が山門に迫っている！弟子たちよ、集え！ |
| gabung.hukum_tegur | Tetua Hukum (elder) | kultivator.voice.gabung.hukum_tegur | elder.advice | angry | 門規を忘れたか。 |
| gabung.hukum_kurung | Tetua Hukum (elder) | kultivator.voice.gabung.hukum_kurung | elder.advice | calm | 思過崖で己を省みよ。 |
| gabung.usir | Ketua Sekte (elder) | kultivator.voice.gabung.usir | elder.advice | angry | 今日をもって、お前を破門とする。二度と我が名を口にするな。 |
| gabung.pamit | Shifu (elder) | kultivator.voice.gabung.pamit | disciple.m1.farewell | calm | 行くがよい。道は一つではない。 |
| gabung.pemburu | Pemburu Sekte (m1) | kultivator.voice.gabung.pemburu | rogue.ambush | angry | 裏切り者め、宗門の名において捕らえる！ |
| gabung.ketua_serah | Ketua Lama (boss) | kultivator.voice.gabung.ketua_serah | elder.inheritance | calm | この衣と鉢を、お前に託そう。 |
| gabung.ketua_baru | Murid Pengawas (m2) | kultivator.voice.gabung.ketua_baru | disciple.m2.cheer | joy | 新宗主に、拝礼！ |
| gabung.tamu_sambut | Murid Pengawas (m2) | kultivator.voice.gabung.tamu_sambut | elder.welcome | normal | 客卿長老、ようこそお越しくださいました。 |
| gabung.naik_kelas | Langit (heaven) | kultivator.voice.gabung.naik_kelas | heaven.realm | normal | 一宗、天に名を連ねる。 |

Captions (line text, `kultivator.voice.gabung.<line>`), id_id / en_us:
- gerbang_panggil — "Yang ikut ujian masuk, maju!" / "Those taking the entrance exam, step forward!"
- tangga_tekanan — "Tenangkan hatimu. Gunung sedang mengujimu." / "Still your heart. The mountain is testing you."
- ujian_mulai — "Ujian, mulai!" / "The test, begin!"
- ujian_lulus — "Hmm... tidak buruk." / "Hmm... not bad."
- ujian_gagal — "Tampaknya belum berjodoh. Datanglah tiga hari lagi." / "It seems fate is not with you yet. Come back in three days."
- diterima — "Mulai hari ini, kau murid sekte kami." / "From this day on, you are a disciple of our sect."
- istimewa — "Bakat sebesar ini... para tetua akan berebut." / "Talent like this... the elders will fight over who takes you."
- sumpah_aturan_1 — "Jangan mencelakai saudara seperguruan." / "Never harm a fellow disciple."
- sumpah_aturan_2 — "Jangan membocorkan warisan rahasia sekte." / "Never betray the sect's secret teachings."
- sumpah_aturan_3 — "Saat sekte dalam bahaya, datanglah tanpa ragu." / "When the sect is in danger, come without fail."
- shifu_pilih — "Maukah kau menjadi muridku?" / "Will you become my disciple?"
- shifu_lindungi — "Berani sekali menyentuh muridku." / "You have nerve, laying a hand on my disciple."
- shifu_lampaui — "Hahaha! Murid sudah melampaui guru!" / "Hahaha! The disciple has surpassed the master!"
- sapa_adik — "Adik seperguruan, selamat pagi!" / "Good morning, little martial brother!"
- sapa_kakak — "Kakak senior, terima kasih atas kerja kerasnya." / "Senior brother, thank you for your hard work."
- saingan_ejek — "Pesuruh sedang apa di sini?" / "What is an errand boy doing here?"
- panggilan_perang — "Musuh mendekati gerbang! Para murid, berkumpul!" / "The enemy nears the mountain gate! Disciples, assemble!"
- hukum_tegur — "Sudah lupa aturan sekte?" / "Have you forgotten the sect's rules?"
- hukum_kurung — "Renungkan dirimu di Tebing Renungan." / "Reflect on yourself at the Cliff of Contemplation."
- usir — "Mulai hari ini kau diusir. Jangan pernah sebut namaku lagi." / "As of today you are expelled. Never speak my name again."
- pamit — "Pergilah. Jalan tidak hanya satu." / "Go. There is more than one road."
- pemburu — "Pengkhianat, atas nama sekte kau kutangkap!" / "Traitor, in the name of the sect, you are under arrest!"
- ketua_serah — "Jubah dan mangkuk ini, kuserahkan padamu." / "This robe and this bowl, I entrust to you."
- ketua_baru — "Hormat kepada Ketua baru!" / "Pay respects to the new Sect Master!"
- tamu_sambut — "Selamat datang, Tetua Tamu." / "Welcome, Guest Elder."
- naik_kelas — "Satu sekte mencatatkan namanya di langit." / "A sect writes its name among the heavens."

Speaker captions (`subtitles.kultivator.voice.gabung.<line>`), id_id / en_us: Penjaga Gerbang Sekte / Sect Gate Guard;
Tetua Penerimaan / Admissions Elder; Murid Pengawas / Proctor Disciple; Tetua Hukum / Law Elder; Shifu / Master;
Kakak Seperguruan / Senior Sister; Adik Seperguruan / Junior Brother; Saingan Seperguruan / Rival Disciple;
Ketua Sekte / Sect Master; Pemburu Sekte / Sect Hunter; Ketua Lama / Old Sect Master; Langit / Heaven.

F-END checklist for 41: add the 26 tuples to `WORLD` (speaker ids above are `WORLD_SPEAKER` keys), run the voice pass,
verify 26 new `.ogg` under `sounds/voice/gabung/` and no old clip changed, then set `VoiceEvents.GABUNG_SIAP = true`.

## D-41C additions (2 lines of 41C-K, 41.6.9 Disciple Tournament and 41.5.7 Shifu lesson)

Used by `hidup/TurnamenMurid` and `hidup/Pelajaran` through `Voice.say`; silent with a chat caption until generated. F-END adds
`turnamen_juara` and `pelajaran_mulai` to the 41 group `GABUNG` in `voice/VoiceEvents.java` together with the 26 above (28 clips).

| key | speaker (voice) | caption id | fallback | emotion | Japanese |
|---|---|---|---|---|---|
| gabung.turnamen_juara | Tetua Sekte di lei tai (elder) | kultivator.voice.gabung.turnamen_juara | elder.advice | joy | 見事だ。今日より、お前が門内一の弟子だ。 |
| gabung.pelajaran_mulai | Shifu (elder) | kultivator.voice.gabung.pelajaran_mulai | elder.advice | calm | 座れ。今日は一つ、大事なことを教えよう。 |

Captions id_id / en_us:
- turnamen_juara — "Luar biasa. Mulai hari ini, kaulah murid terbaik perguruan." / "Splendid. From today you are the first disciple of this school."
- pelajaran_mulai — "Duduklah. Hari ini akan kuajarkan satu hal penting." / "Sit. Today I will teach you one thing that matters."

Speaker captions: Tetua Sekte / Sect Elder; Shifu / Master (both already listed above).

## 42 Kota yang Hidup (42.12.7 + 42.5A.8, 102 clips, 42V-B)

Group `KOTA42` in `voice/VoiceEvents.java` (keys `kota42.<line>`), registered only while `VoiceEvents.KOTA42_SIAP`
is true: **F-END sets it to `true`** after the clips exist. Until then every caller keeps its text bubble and the
fallback in the table (the far murmur `gumam.<type>` of `ObrolanSuara`, 42.12.7). Caption keys
`kultivator.voice.kota42.<line>` (line text) + `subtitles.kultivator.voice.kota42.<line>` (speaker) go into lang
`kota_hidup_suara` (written by `gen_voices.py`, id_id + en_us). `WORLD` tuple `(key, speaker, ja, en, id, emotion)`.
The design table has 77 lines; **`kota42.ronda_geng2` (the second watch call) is added here** so the night watch
calls all five watches and the count is the design's 78 + 24 = 102.

| key | speaker (voice) | caption id | fallback | emotion | Japanese |
|---|---|---|---|---|---|
| kota42.sapa_pagi | Warga Perempuan (f1) | kultivator.voice.kota42.sapa_pagi | gumam.wanita | joy | おはようございます！今日もいい天気ですね。 |
| kota42.sapa_kenal | Pedagang Langganan (m2) | kultivator.voice.kota42.sapa_kenal | gumam.pria | joy | おや、旦那！また来てくださったんですね。 |
| kota42.sapa_asing | Warga Curiga (m3) | kultivator.voice.kota42.sapa_asing | gumam.pria | normal | 見かけない顔だな。旅のお方かい？ |
| kota42.sapa_akrab | Bibi Tetangga (f2) | kultivator.voice.kota42.sapa_akrab | gumam.wanita | joy | あら、いらっしゃい！お茶でも飲んでいって。 |
| kota42.terima_kasih | Warga yang Ditolong (f1) | kultivator.voice.kota42.terima_kasih | gumam.wanita | joy | 本当にありがとうございます。このご恩は忘れません。 |
| kota42.diselamatkan | Warga yang Diselamatkan (m2) | kultivator.voice.kota42.diselamatkan | gumam.pria | sad | あの火事のとき、あなたがいなければ…！ |
| kota42.anak_kagum | Anak Kota (f1) | kultivator.voice.kota42.anak_kagum | gumam.wanita | joy | すごい！お兄さん、空を飛べるの？ |
| kota42.tanya_jalan | Warga Ramah (m1) | kultivator.voice.kota42.tanya_jalan | gumam.pria | normal | ああ、それなら東へまっすぐ、橋を渡った先ですよ。 |
| kota42.tidak_tahu | Pendatang Baru (f3) | kultivator.voice.kota42.tidak_tahu | gumam.wanita | normal | ごめんなさい、私も来たばかりで…井戸の奥さんたちに聞いてみて。 |
| kota42.gosip_bisik | Ibu-Ibu Sumur (f2) | kultivator.voice.kota42.gosip_bisik | gumam.wanita | normal | ねえ、聞いた？張さんの息子と李さんの娘がね… |
| kota42.gosip_tawa | Ibu-Ibu Sumur (f1) | kultivator.voice.kota42.gosip_tawa | gumam.wanita | joy | まあ、大げさなんだから！ |
| kota42.duka | Anak yang Berduka (m2) | kultivator.voice.kota42.duka | gumam.pria | sad | 父が…昨夜、亡くなりました。 |
| kota42.bayi_lahir | Ayah Baru (m1) | kultivator.voice.kota42.bayi_lahir | gumam.pria | joy | 生まれた！男の子だ！みんな、赤い卵だよ！ |
| kota42.nikah_undang | Ibu Pengantin (f2) | kultivator.voice.kota42.nikah_undang | gumam.wanita | joy | 明日、娘が嫁ぐんです。ぜひいらしてください。 |
| kota42.takut_serangan | Warga Ketakutan (f3) | kultivator.voice.kota42.takut_serangan | gumam.wanita | fear | 早く中へ！獣が城壁の外まで来てる！ |
| kota42.marah | Warga Pemarah (m3) | kultivator.voice.kota42.marah | gumam.pria | angry | なんだと！もう一度言ってみろ！ |
| kota42.mabuk | Pemabuk (m1) | kultivator.voice.kota42.mabuk | gumam.pria | normal | ひっく…月が…二つ…いや、一つか… |
| kota42.catur_skak | Kakek Pecatur (elder) | kultivator.voice.kota42.catur_skak | gumam.kakek | joy | 王手！どうだ、参ったか！ |
| kota42.catur_kalah | Kakek Pecatur (elder) | kultivator.voice.kota42.catur_kalah | gumam.kakek | sad | むう…今日は風が悪い。 |
| kota42.pedagang_seru | Pedagang Sayur (m2) | kultivator.voice.kota42.pedagang_seru | gumam.pria | joy | 安いよ安いよ！今朝採れたての野菜だよ！ |
| kota42.harga_naik | Ibu Rumah Tangga (f2) | kultivator.voice.kota42.harga_naik | gumam.wanita | sad | お米がまた上がったのよ。困ったわねえ。 |
| kota42.ronda_geng1 | Lao Geng, Penjaga Malam (elder) | kultivator.voice.kota42.ronda_geng1 | gumam.kakek | normal | てんかわき、ものかわく。ひのようじん！ |
| kota42.ronda_geng2 | Lao Geng, Penjaga Malam (elder) | kultivator.voice.kota42.ronda_geng2 | gumam.kakek | calm | 二更、平安無事でござーい！ |
| kota42.ronda_geng3 | Lao Geng, Penjaga Malam (elder) | kultivator.voice.kota42.ronda_geng3 | gumam.kakek | normal | 戸締まり用心、泥棒に気をつけよ！ |
| kota42.ronda_geng4 | Xiao Bang, Murid Ronda (m3) | kultivator.voice.kota42.ronda_geng4 | gumam.pria | calm | 四更でございます。安らかにお休みなされ。 |
| kota42.ronda_geng5 | Lao Geng, Penjaga Malam (elder) | kultivator.voice.kota42.ronda_geng5 | gumam.kakek | calm | 五更！夜明けは近いぞ。 |
| kota42.copet_teriak | Korban Copet (f1) | kultivator.voice.kota42.copet_teriak | gumam.wanita | fear | スリだ！誰か捕まえて！ |
| kota42.copet_tabrak | Pencopet (m3) | kultivator.voice.kota42.copet_tabrak | gumam.pria | normal | おっと、失礼！ |
| kota42.copet_tertangkap | Pencopet (m3) | kultivator.voice.kota42.copet_tertangkap | gumam.pria | fear | ま、参った！返します、返しますから！ |
| kota42.kebakaran | Warga Panik (m1) | kultivator.voice.kota42.kebakaran | gumam.pria | fear | 火事だ！水を持ってこい！ |
| kota42.api_padam | Pemilik Rumah (m2) | kultivator.voice.kota42.api_padam | gumam.pria | joy | 消えた…助かった…ありがとうございます！ |
| kota42.gerobak | Kusir Gerobak (m2) | kultivator.voice.kota42.gerobak | gumam.pria | sad | ああ、荷が！誰か手を貸してくれ！ |
| kota42.lerai | Warga Pelerai (m3) | kultivator.voice.kota42.lerai | gumam.pria | angry | やめろ！二人とも、やめるんだ！ |
| kota42.lao_shuo_buka | Lao Shuo, Pendongeng (elder) | kultivator.voice.kota42.lao_shuo_buka | gumam.kakek | calm | さてさて、今日お話しするのは… |
| kota42.lao_shuo_tutup | Lao Shuo, Pendongeng (elder) | kultivator.voice.kota42.lao_shuo_tutup | gumam.kakek | joy | 続きは、また明日のお楽しみ！ |
| kota42.akrobat_buka | Nyonya Hong Ling (f1) | kultivator.voice.kota42.akrobat_buka | gumam.wanita | joy | さあさあ、お立ち会い！三日月一座の妙技、とくとご覧あれ！ |
| kota42.penjual_obat | Penjual Obat (m2) | kultivator.voice.kota42.penjual_obat | gumam.pria | joy | この膏薬ひと塗りで、腰の痛みもたちまち消える！ |
| kota42.sekolah_hafal | Murid Sekolah (f1) | kultivator.voice.kota42.sekolah_hafal | gumam.wanita | normal | じんしのしょ、せいほんぜん。せいあいちかく、ならいあいとおし。 |
| kota42.guru_panggil | Guru Sekolah (m2) | kultivator.voice.kota42.guru_panggil | gumam.pria | normal | さあ、みんな並んで。学堂へ行くよ。 |
| kota42.taichi | Kakek Taichi (elder) | kultivator.voice.kota42.taichi | gumam.kakek | calm | ゆっくり息を吸って…吐いて… |
| kota42.comblang | Bibi Mei, Mak Comblang (f2) | kultivator.voice.kota42.comblang | gumam.wanita | joy | いいご縁がありますよ。任せてちょうだい！ |
| kota42.nikah_jemput | Pengiring Pengantin (m1) | kultivator.voice.kota42.nikah_jemput | gumam.pria | joy | 花嫁のお迎えだ！道を開けてくれ！ |
| kota42.nikah_sembah | Pemandu Upacara (elder) | kultivator.voice.kota42.nikah_sembah | gumam.kakek | joy | いっぱい、てんち！にはい、こうどう！ふうふ、たいはい！ |
| kota42.bersulang | Tamu Pesta (m2) | kultivator.voice.kota42.bersulang | gumam.pria | joy | 新郎新婦に、乾杯！ |
| kota42.makam_pimpin | Pengurus Duka (elder) | kultivator.voice.kota42.makam_pimpin | gumam.kakek | sad | 故人を偲び、皆で送りましょう。 |
| kota42.makam_ratap | Janda yang Berduka (f2) | kultivator.voice.kota42.makam_ratap | gumam.wanita | sad | どうして…どうして先に逝ってしまったの… |
| kota42.zhuazhou_kuas | Ibu Bayi (f1) | kultivator.voice.kota42.zhuazhou_kuas | gumam.wanita | joy | 見て！筆を掴んだわ！きっと学者になるわね！ |
| kota42.zhuazhou_pedang | Ayah Bayi (m1) | kultivator.voice.kota42.zhuazhou_pedang | gumam.pria | joy | 剣を掴んだぞ！この子は仙人になるかもしれん！ |
| kota42.rekrut_terima | Petugas Kantor Rekrutmen (m2) | kultivator.voice.kota42.rekrut_terima | gumam.pria | joy | おめでとう。今日から君は我が宗門の候補だ。 |
| kota42.rekrut_pamit | Calon Murid (f1) | kultivator.voice.kota42.rekrut_pamit | gumam.wanita | sad | お父さん、お母さん…行ってきます！ |
| kota42.restu_tolak | Ayah Pemilik Toko (m3) | kultivator.voice.kota42.restu_tolak | gumam.pria | angry | 息子は店を継ぐんだ。仙人なんぞにはやらん！ |
| kota42.pelindung_sapa | Warga (m1) | kultivator.voice.kota42.pelindung_sapa | gumam.pria | joy | 守護宗門の方だ！ありがたい！ |
| kota42.bela_panggil | Utusan Kota (m2) | kultivator.voice.kota42.bela_panggil | gumam.pria | fear | 獣の群れが町に迫っています！どうかお助けを！ |
| kota42.bela_menang | Utusan Kota (m2) | kultivator.voice.kota42.bela_menang | gumam.pria | joy | 町は守られた！宗門万歳！ |
| kota42.panji_naik | Wali Kota (elder) | kultivator.voice.kota42.panji_naik | gumam.kakek | calm | 今日よりこの町は、貴宗の庇護を受けます。 |
| kota42.mandor | Mandor Tukang (elder) | kultivator.voice.kota42.mandor | gumam.kakek | calm | 礎がしっかりしてこそ、家は百年もつ。 |
| kota42.kavling_selesai | Tukang (m1) | kultivator.voice.kota42.kavling_selesai | gumam.pria | joy | 新しい家が建ったぞ！祝いだ！ |
| kota42.bangun_kembali | Tukang (m2) | kultivator.voice.kota42.bangun_kembali | gumam.pria | calm | 壊れても、また建てればいい。みんなでな。 |
| kota42.sandi_malam | Penjaga Pasar Hantu (demon) | kultivator.voice.kota42.sandi_malam | gumam.pria | calm | 月のない夜、石の下で水が眠る… |
| kota42.lentera_hijau | Penjaga Pasar Hantu (demon) | kultivator.voice.kota42.lentera_hijau | gumam.pria | calm | 顔は見ない、名は聞かない。それがここの決まりだ。 |
| kota42.razia | Pedagang Gelap (m1) | kultivator.voice.kota42.razia | gumam.pria | fear | 役人だ！逃げろ！ |
| kota42.dadu_besar | Penjudi (m3) | kultivator.voice.kota42.dadu_besar | gumam.pria | joy | 大だ！大が来たぞ！ |
| kota42.dadu_kecil | Penjudi (m3) | kultivator.voice.kota42.dadu_kecil | gumam.pria | sad | くそっ、また小か… |
| kota42.huaquan | Peminum Kedai (m1) | kultivator.voice.kota42.huaquan | gumam.pria | joy | ごかいしゅ！ろくろくじゅん！ |
| kota42.wayang_buka | Dalang Wayang Bayang (m2) | kultivator.voice.kota42.wayang_buka | gumam.pria | joy | 影絵芝居の始まり始まり！ |
| kota42.sup_nenek | Nenek Tang (f2) | kultivator.voice.kota42.sup_nenek | gumam.wanita | calm | 寒かったろう。熱いスープをお飲み。 |
| kota42.laba | Biksu Kuil (elder) | kultivator.voice.kota42.laba | gumam.kakek | calm | ろうはちの粥だ。誰でも並びなされ。 |
| kota42.tahun_baru | Anak Kota (f1) | kultivator.voice.kota42.tahun_baru | gumam.wanita | joy | 新年おめでとう！お年玉ちょうだい！ |
| kota42.pindah | Warga yang Pindah (m2) | kultivator.voice.kota42.pindah | gumam.pria | normal | 水の都に仕事があるそうで…家族で引っ越します。 |
| kota42.pejabat_sapa | Warga (m1) | kultivator.voice.kota42.pejabat_sapa | gumam.pria | normal | これは、お役人様！ |
| kota42.putusan | Hakim Kota (elder) | kultivator.voice.kota42.putusan | gumam.kakek | angry | 判決を下す！ |
| kota42.suap | Penyuap (m3) | kultivator.voice.kota42.suap | gumam.pria | normal | まあまあ、これはほんの気持ちで… |
| kota42.pajak_keluh | Pedagang (m3) | kultivator.voice.kota42.pajak_keluh | gumam.pria | sad | また税が上がるのか… |
| kota42.cucu_cerita | Kakek Pencerita (elder) | kultivator.voice.kota42.cucu_cerita | gumam.kakek | calm | おじいちゃんの若い頃にな、空飛ぶ剣士が町を救ってくれたんじゃ。 |
| kota42.ah_qin | Ah Qin, Pengamen (f2) | kultivator.voice.kota42.ah_qin | gumam.wanita | sad | この曲は、亡き師匠の曲です。 |
| kota42.lao_geng_pamit | Lao Geng, Penjaga Malam (elder) | kultivator.voice.kota42.lao_geng_pamit | gumam.kakek | sad | わしの拍子木も、今夜で最後か… |
| kota42.xiao_bang | Xiao Bang, Murid Ronda (m3) | kultivator.voice.kota42.xiao_bang | gumam.pria | joy | 師匠、見ててください。僕が町の夜を守ります！ |
| kota42.xiao_yu | Xiao Yu (f1) | kultivator.voice.kota42.xiao_yu | gumam.wanita | joy | 葉っぱがね、私の手の上で浮かぶの！ |

Interjections (42.5A.8; played at the start of a bubble, the bubble text stays Indonesian/English; `_l`/`_p` by the
speaker's sex):

| key | speaker (voice) | caption id | fallback | emotion | Japanese |
|---|---|---|---|---|---|
| kota42.seru.ya_l | Warga (m2) | kultivator.voice.kota42.seru.ya_l | (bubble only) | normal | はい。 |
| kota42.seru.ya_p | Warga Perempuan (f1) | kultivator.voice.kota42.seru.ya_p | (bubble only) | normal | はい。 |
| kota42.seru.eh_l | Warga (m3) | kultivator.voice.kota42.seru.eh_l | (bubble only) | fear | ええっ？ |
| kota42.seru.eh_p | Warga Perempuan (f2) | kultivator.voice.kota42.seru.eh_p | (bubble only) | fear | ええっ？ |
| kota42.seru.hmm_l | Warga (m2) | kultivator.voice.kota42.seru.hmm_l | (bubble only) | calm | ふむ… |
| kota42.seru.hmm_p | Warga Perempuan (f2) | kultivator.voice.kota42.seru.hmm_p | (bubble only) | calm | うーん… |
| kota42.seru.oh_l | Warga (m1) | kultivator.voice.kota42.seru.oh_l | (bubble only) | joy | へえ！ |
| kota42.seru.oh_p | Warga Perempuan (f1) | kultivator.voice.kota42.seru.oh_p | (bubble only) | joy | へえ！ |
| kota42.seru.tawa_l | Warga (m1) | kultivator.voice.kota42.seru.tawa_l | (bubble only) | joy | あははっ！ |
| kota42.seru.tawa_p | Warga Perempuan (f1) | kultivator.voice.kota42.seru.tawa_p | (bubble only) | joy | あははっ！ |
| kota42.seru.cih | Warga (m3) | kultivator.voice.kota42.seru.cih | (bubble only) | angry | ちっ。 |
| kota42.seru.aduh_l | Warga (m2) | kultivator.voice.kota42.seru.aduh_l | (bubble only) | sad | やれやれ… |
| kota42.seru.aduh_p | Warga Perempuan (f2) | kultivator.voice.kota42.seru.aduh_p | (bubble only) | sad | やれやれ… |
| kota42.seru.terima_kasih_l | Warga (m2) | kultivator.voice.kota42.seru.terima_kasih_l | (bubble only) | joy | どうも！ |
| kota42.seru.terima_kasih_p | Warga Perempuan (f1) | kultivator.voice.kota42.seru.terima_kasih_p | (bubble only) | joy | どうも！ |
| kota42.seru.maaf_l | Warga (m3) | kultivator.voice.kota42.seru.maaf_l | (bubble only) | sad | すみません… |
| kota42.seru.maaf_p | Warga Perempuan (f3) | kultivator.voice.kota42.seru.maaf_p | (bubble only) | sad | すみません… |
| kota42.seru.wah_anak | Anak (f1) | kultivator.voice.kota42.seru.wah_anak | (bubble only) | joy | わあっ！ |
| kota42.seru.tetua_hmm | Tetua (elder) | kultivator.voice.kota42.seru.tetua_hmm | (bubble only) | calm | ほっほっ。 |
| kota42.seru.tetua_marah | Tetua (elder) | kultivator.voice.kota42.seru.tetua_marah | (bubble only) | angry | 馬鹿者！ |
| kota42.seru.mabuk | Warga (m1) | kultivator.voice.kota42.seru.mabuk | (bubble only) | normal | ひっく。 |
| kota42.seru.bisik_p | Warga Perempuan (f2) | kultivator.voice.kota42.seru.bisik_p | (bubble only) | calm | しーっ。 |
| kota42.seru.takut_p | Warga Perempuan (f3) | kultivator.voice.kota42.seru.takut_p | (bubble only) | fear | ひっ…！ |
| kota42.seru.setuju_l | Warga (m1) | kultivator.voice.kota42.seru.setuju_l | (bubble only) | joy | そうだそうだ！ |

Captions (line text, `kultivator.voice.kota42.<line>`), id_id / en_us:
- sapa_pagi — "Selamat pagi! Cuacanya bagus hari ini." / "Good morning! Lovely weather today."
- sapa_kenal — "Wah, Tuan! Datang lagi rupanya." / "Why, sir! Back again, I see."
- sapa_asing — "Wajahmu asing. Pengembara, ya?" / "Don't know your face. A traveller, eh?"
- sapa_akrab — "Wah, mampir! Minum teh dulu." / "Oh, come in! Have some tea first."
- terima_kasih — "Terima kasih sungguh. Budi ini takkan kulupakan." / "Thank you, truly. I won't forget this kindness."
- diselamatkan — "Waktu kebakaran itu, kalau bukan karena Tuan...!" / "That fire... if it weren't for you, sir...!"
- anak_kagum — "Hebat! Kakak bisa terbang?" / "Wow! Can you really fly?"
- tanya_jalan — "Oh, itu lurus ke timur, di seberang jembatan." / "Oh, that's straight east, just across the bridge."
- tidak_tahu — "Maaf, aku juga baru datang... tanyakan ibu-ibu di sumur." / "Sorry, I've only just arrived too... ask the women at the well."
- gosip_bisik — "Hei, sudah dengar? Putra keluarga Zhang dan putri keluarga Li..." / "Psst, heard the news? The Zhang boy and the Li girl..."
- gosip_tawa — "Ah, dia memang suka melebih-lebihkan!" / "Oh, she always blows things up!"
- duka — "Ayah... wafat semalam." / "Father... passed away last night."
- bayi_lahir — "Sudah lahir! Laki-laki! Semua, ini telur merah!" / "It's born! A boy! Everyone, red eggs!"
- nikah_undang — "Besok putriku menikah. Datanglah." / "My daughter marries tomorrow. Please come."
- takut_serangan — "Cepat masuk! Binatang sudah sampai di luar tembok!" / "Get inside, quick! The beasts are right outside the walls!"
- marah — "Apa katamu! Ulangi sekali lagi!" / "What did you say! Say that again!"
- mabuk — "Hik... bulannya... dua... bukan, satu..." / "Hic... two... moons... no, one..."
- catur_skak — "Skak! Bagaimana, menyerah?" / "Check! Well, do you give up?"
- catur_kalah — "Hmm... anginnya sedang buruk hari ini." / "Hmm... the wind's against me today."
- pedagang_seru — "Murah, murah! Sayur baru dipetik pagi ini!" / "Cheap, cheap! Vegetables picked this morning!"
- harga_naik — "Beras naik lagi. Repot sekali." / "Rice went up again. What a bother."
- ronda_geng1 — "Udara kering, hati-hati api dan lilin!" / "Dry skies, dry things, mind the fire!"
- ronda_geng2 — "Geng kedua, semua aman!" / "Second watch, and all is well!"
- ronda_geng3 — "Kunci pintu, waspada pencuri!" / "Lock your doors, beware of thieves!"
- ronda_geng4 — "Geng keempat. Tidurlah dengan tenang." / "The fourth watch. Sleep in peace."
- ronda_geng5 — "Geng kelima! Fajar sudah dekat." / "Fifth watch! Dawn is near."
- copet_teriak — "Copet! Tolong tangkap!" / "Pickpocket! Somebody stop him!"
- copet_tabrak — "Ups, maaf!" / "Oops, pardon me!"
- copet_tertangkap — "A-ampun! Kukembalikan, kukembalikan!" / "M-mercy! I'll give it back, I'll give it back!"
- kebakaran — "Kebakaran! Bawa air!" / "Fire! Bring water!"
- api_padam — "Padam... selamat... terima kasih!" / "It's out... we're saved... thank you!"
- gerobak — "Aduh, muatanku! Tolong bantu!" / "Oh no, my load! Someone lend a hand!"
- lerai — "Hentikan! Kalian berdua, hentikan!" / "Stop it! Both of you, stop!"
- lao_shuo_buka — "Nah, nah, yang akan kuceritakan hari ini..." / "Now then, now then, today's tale is..."
- lao_shuo_tutup — "Lanjutannya, nantikan besok!" / "For the rest, come back tomorrow!"
- akrobat_buka — "Ayo, ayo, saksikan kehebatan Rombongan Bulan Sabit!" / "Roll up, roll up, behold the wonders of the Crescent Moon Troupe!"
- penjual_obat — "Sekali oles plester ini, sakit pinggang langsung hilang!" / "One dab of this plaster and your back pain is gone!"
- sekolah_hafal — "Manusia pada awalnya berwatak baik; wataknya dekat, kebiasaannya menjauhkan." / "Men at their birth are good by nature; nature is close, habit sets apart."
- guru_panggil — "Ayo semua berbaris. Kita ke sekolah." / "Line up, everyone. We're off to school."
- taichi — "Tarik napas pelan... hembuskan..." / "Breathe in slowly... and out..."
- comblang — "Ada jodoh yang bagus. Serahkan padaku!" / "There's a fine match for you. Leave it to me!"
- nikah_jemput — "Penjemputan pengantin! Beri jalan!" / "Here comes the bride's escort! Make way!"
- nikah_sembah — "Sembah langit dan bumi! Sembah orang tua! Saling bersembah!" / "Bow to Heaven and Earth! Bow to the parents! Bow to each other!"
- bersulang — "Untuk kedua mempelai, bersulang!" / "To the bride and groom, cheers!"
- makam_pimpin — "Mari kita kenang almarhum dan mengantarnya bersama." / "Let us remember the departed and see them off together."
- makam_ratap — "Kenapa... kenapa pergi lebih dulu..." / "Why... why did you go first..."
- zhuazhou_kuas — "Lihat! Dia meraih kuas! Pasti jadi sarjana!" / "Look! He grabbed the brush! He'll be a scholar for sure!"
- zhuazhou_pedang — "Dia meraih pedang! Anak ini mungkin jadi kultivator!" / "He grabbed the sword! This child might become a cultivator!"
- rekrut_terima — "Selamat. Mulai hari ini kau calon murid sekte kami." / "Congratulations. From today you are a candidate of our sect."
- rekrut_pamit — "Ayah, Ibu... aku berangkat!" / "Father, Mother... I'm off!"
- restu_tolak — "Putraku akan mewarisi toko. Takkan kuserahkan jadi kultivator!" / "My son inherits the shop. I won't hand him to the immortals!"
- pelindung_sapa — "Itu orang dari sekte pelindung! Syukurlah!" / "It's someone from the protecting sect! Thank heavens!"
- bela_panggil — "Kawanan binatang mendekati kota! Tolonglah kami!" / "A beast horde is closing in on the town! Please, help us!"
- bela_menang — "Kota terselamatkan! Hidup sekte!" / "The town is saved! Long live the sect!"
- panji_naik — "Mulai hari ini kota ini di bawah naungan sekte Tuan." / "From this day, this town lies under your sect's protection."
- mandor — "Hanya bila pondasinya kokoh, rumah bertahan seratus tahun." / "Only on firm foundations does a house stand a hundred years."
- kavling_selesai — "Rumah baru sudah berdiri! Mari rayakan!" / "The new house is up! Let's celebrate!"
- bangun_kembali — "Kalau hancur, kita bangun lagi. Bersama-sama." / "If it falls, we build it again. Together."
- sandi_malam — "Malam tanpa bulan, air tidur di bawah batu..." / "On a moonless night, water sleeps beneath the stone..."
- lentera_hijau — "Tak melihat wajah, tak bertanya nama. Itu aturan di sini." / "We don't look at faces, we don't ask names. That's the rule here."
- razia — "Petugas! Lari!" / "Officials! Run!"
- dadu_besar — "Besar! Keluar besar!" / "Big! Big came up!"
- dadu_kecil — "Sial, kecil lagi..." / "Damn it, small again..."
- huaquan — "Lima juara! Enam-enam lancar!" / "Five champions! Six-six smooth sailing!"
- wayang_buka — "Pertunjukan wayang bayang dimulai!" / "The shadow play begins, it begins!"
- sup_nenek — "Dingin, kan? Minumlah sup panas ini." / "Cold out, wasn't it? Drink some hot soup."
- laba — "Ini bubur Laba. Siapa saja silakan antre." / "Here is Laba porridge. Everyone, line up."
- tahun_baru — "Selamat tahun baru! Minta angpao!" / "Happy New Year! Red envelope, please!"
- pindah — "Katanya ada kerja di Kota Air... sekeluarga kami pindah." / "They say there's work in the Water City... the whole family is moving."
- pejabat_sapa — "Wah, Tuan Pejabat!" / "Oh, Lord Official!"
- putusan — "Putusan dijatuhkan!" / "The verdict is passed!"
- suap — "Sudahlah, ini sekadar tanda terima kasih..." / "Now, now, this is just a small token of thanks..."
- pajak_keluh — "Pajak naik lagi?..." / "Taxes up again?..."
- cucu_cerita — "Waktu Kakek masih muda, seorang pendekar terbang menyelamatkan kota ini." / "When Grandpa was young, a flying swordsman saved this town."
- ah_qin — "Lagu ini lagu almarhum guruku." / "This tune was my late master's."
- lao_geng_pamit — "Kentonganku pun, malam ini yang terakhir..." / "My clapper too... tonight is its last..."
- xiao_bang — "Guru, lihatlah. Aku yang akan menjaga malam kota!" / "Master, watch me. I'll guard the town's nights!"
- xiao_yu — "Daunnya melayang di atas tanganku!" / "The leaf floats above my hand!"
- seru.ya_l — "Ya." / "Yes."
- seru.ya_p — "Ya." / "Yes."
- seru.eh_l — "Hah?" / "Huh?"
- seru.eh_p — "Hah?" / "Huh?"
- seru.hmm_l — "Hmm..." / "Hmm..."
- seru.hmm_p — "Hmm..." / "Hmm..."
- seru.oh_l — "Oh ya!" / "Oh, really!"
- seru.oh_p — "Oh ya!" / "Oh, really!"
- seru.tawa_l — "Hahaha!" / "Hahaha!"
- seru.tawa_p — "Hahaha!" / "Hahaha!"
- seru.cih — "Cih." / "Tch."
- seru.aduh_l — "Aduh..." / "Oh dear..."
- seru.aduh_p — "Aduh..." / "Oh dear..."
- seru.terima_kasih_l — "Terima kasih!" / "Thanks!"
- seru.terima_kasih_p — "Terima kasih!" / "Thanks!"
- seru.maaf_l — "Maaf..." / "Sorry..."
- seru.maaf_p — "Maaf..." / "Sorry..."
- seru.wah_anak — "Wah!" / "Wow!"
- seru.tetua_hmm — "Ho ho." / "Ho ho."
- seru.tetua_marah — "Dasar bodoh!" / "You fool!"
- seru.mabuk — "Hik." / "Hic."
- seru.bisik_p — "Sst." / "Shh."
- seru.takut_p — "Hii...!" / "Eek...!"
- seru.setuju_l — "Betul, betul!" / "Right, right!"

Speaker captions (`subtitles.kultivator.voice.kota42.<line>`), id_id / en_us, with their lines:
- Warga Perempuan / Townswoman: sapa_pagi, seru.ya_p, seru.eh_p, seru.hmm_p, seru.oh_p, seru.tawa_p, seru.aduh_p, seru.terima_kasih_p, seru.maaf_p, seru.bisik_p, seru.takut_p
- Pedagang Langganan / Regular Merchant: sapa_kenal
- Warga Curiga / Wary Townsman: sapa_asing
- Bibi Tetangga / Neighbour Auntie: sapa_akrab
- Warga yang Ditolong / Grateful Townswoman: terima_kasih
- Warga yang Diselamatkan / Rescued Townsman: diselamatkan
- Anak Kota / Town Child: anak_kagum, tahun_baru
- Warga Ramah / Helpful Townsman: tanya_jalan
- Pendatang Baru / Newcomer: tidak_tahu
- Ibu-Ibu Sumur / Well Gossip: gosip_bisik, gosip_tawa
- Anak yang Berduka / Grieving Son: duka
- Ayah Baru / New Father: bayi_lahir
- Ibu Pengantin / Bride's Mother: nikah_undang
- Warga Ketakutan / Frightened Townswoman: takut_serangan
- Warga Pemarah / Angry Townsman: marah
- Pemabuk / Drunkard: mabuk
- Kakek Pecatur / Old Chess Player: catur_skak, catur_kalah
- Pedagang Sayur / Greengrocer: pedagang_seru
- Ibu Rumah Tangga / Housewife: harga_naik
- Lao Geng, Penjaga Malam / Lao Geng, Night Watchman: ronda_geng1, ronda_geng2, ronda_geng3, ronda_geng5, lao_geng_pamit
- Xiao Bang, Murid Ronda / Xiao Bang, Watch Apprentice: ronda_geng4, xiao_bang
- Korban Copet / Pickpocket Victim: copet_teriak
- Pencopet / Pickpocket: copet_tabrak, copet_tertangkap
- Warga Panik / Panicked Townsman: kebakaran
- Pemilik Rumah / Homeowner: api_padam
- Kusir Gerobak / Carter: gerobak
- Warga Pelerai / Peacemaker: lerai
- Lao Shuo, Pendongeng / Lao Shuo, Storyteller: lao_shuo_buka, lao_shuo_tutup
- Nyonya Hong Ling / Madam Hong Ling: akrobat_buka
- Penjual Obat / Medicine Hawker: penjual_obat
- Murid Sekolah / Schoolchild: sekolah_hafal
- Guru Sekolah / Schoolteacher: guru_panggil
- Kakek Taichi / Tai Chi Elder: taichi
- Bibi Mei, Mak Comblang / Auntie Mei, Matchmaker: comblang
- Pengiring Pengantin / Bridal Escort: nikah_jemput
- Pemandu Upacara / Master of Ceremonies: nikah_sembah
- Tamu Pesta / Wedding Guest: bersulang
- Pengurus Duka / Funeral Master: makam_pimpin
- Janda yang Berduka / Grieving Widow: makam_ratap
- Ibu Bayi / Baby's Mother: zhuazhou_kuas
- Ayah Bayi / Baby's Father: zhuazhou_pedang
- Petugas Kantor Rekrutmen / Recruitment Officer: rekrut_terima
- Calon Murid / Young Recruit: rekrut_pamit
- Ayah Pemilik Toko / Shopkeeper Father: restu_tolak
- Warga / Townsman: pelindung_sapa, pejabat_sapa, seru.ya_l, seru.eh_l, seru.hmm_l, seru.oh_l, seru.tawa_l, seru.cih, seru.aduh_l, seru.terima_kasih_l, seru.maaf_l, seru.mabuk, seru.setuju_l
- Utusan Kota / Town Messenger: bela_panggil, bela_menang
- Wali Kota / Mayor: panji_naik
- Mandor Tukang / Master Builder: mandor
- Tukang / Builder: kavling_selesai, bangun_kembali
- Penjaga Pasar Hantu / Ghost Market Keeper: sandi_malam, lentera_hijau
- Pedagang Gelap / Black Market Trader: razia
- Penjudi / Gambler: dadu_besar, dadu_kecil
- Peminum Kedai / Tavern Drinker: huaquan
- Dalang Wayang Bayang / Shadow Puppeteer: wayang_buka
- Nenek Tang / Granny Tang: sup_nenek
- Biksu Kuil / Temple Abbot: laba
- Warga yang Pindah / Departing Townsman: pindah
- Hakim Kota / Town Magistrate: putusan
- Penyuap / Briber: suap
- Pedagang / Merchant: pajak_keluh
- Kakek Pencerita / Storytelling Grandfather: cucu_cerita
- Ah Qin, Pengamen / Ah Qin, Street Musician: ah_qin
- Xiao Yu / Xiao Yu: xiao_yu
- Anak / Child: seru.wah_anak
- Tetua / Elder: seru.tetua_hmm, seru.tetua_marah

Readings for `READ` in `gen_voices.py` (captions keep the kanji; add only those VOICEVOX misreads): 二更 にこう,
四更 しこう, 五更 ごこう, 王手 おうて, 膏薬 こうやく, 宗門 しゅうもん, 庇護 ひご, 礎 いしずえ, 貴宗 きしゅう,
新郎新婦 しんろうしんぷ, 故人 こじん, 偲び しのび, 判決 はんけつ, 拍子木 ひょうしぎ, 師匠 ししょう, 旦那 だんな,
影絵芝居 かげえしばい, 一座 いちざ, 妙技 みょうぎ, お立ち会い おたちあい, 学堂 がくどう, 守護宗門 しゅごしゅうもん.

F-END checklist for 42: add the 102 tuples to `WORLD` (speakers are `WORLD_SPEAKER` keys; `demon` = the Ghost
Market keeper), run the voice pass, verify 102 new `.ogg` under `sounds/voice/kota42/` and no old clip changed, then
set `VoiceEvents.KOTA42_SIAP = true`. Synth sounds of 42V-F (`gen_kota_hidup_suara.py`) are listed by 42V-F in a
`## sounds.json` section below.

## 42D-P additions (3 lines for the ceremonies, 42.7.7)

Used by `Upacara` (guarded by `VoiceEvents.exists`, the bubble text from lang `kota_hidup_peristiwa` always shows). Same group
and flag as the 102 clips above (`kota42.*`, `KOTA42_SIAP`): F-END adds these 3 tuples to `WORLD` too (105 clips in all).

| key | speaker (voice) | caption id | fallback | emotion | Japanese |
|---|---|---|---|---|---|
| kota42.nikah_selamat | Tamu Pesta (f1) | kultivator.voice.kota42.nikah_selamat | gumam.wanita | joy | ご結婚おめでとうございます！末永くお幸せに！ |
| kota42.makam_sujud | Keluarga Almarhum (m3) | kultivator.voice.kota42.makam_sujud | gumam.pria | sad | どうか、安らかに眠ってください…。 |
| kota42.sebulan_selamat | Tetangga Tua (elder) | kultivator.voice.kota42.sebulan_selamat | gumam.kakek | joy | 満月おめでとう。丈夫に育つんだよ。 |

Indonesian captions (`en` is written by 42V as for the others):
- nikah_selamat: "Selamat atas pernikahan kalian! Semoga bahagia selamanya!" / "Congratulations on your wedding! May you be happy forever!"
- makam_sujud: "Semoga beristirahat dengan tenang..." / "May you rest in peace..."
- sebulan_selamat: "Selamat sebulan! Tumbuhlah sehat, ya." / "Happy one month! Grow up healthy, little one."

## sounds.json (42V-F: synthesised sounds of 42.12.6, no VOICEVOX)

F-END runs `python tools/gen_kota_hidup_suara.py <repo> --merge-sounds-json` (venv with numpy + soundfile; `C:\Users\Musyaffa\AppData\Local\Temp\kv\Scripts\python.exe`
has both). It rewrites the 65 clips under `assets/kultivator/sounds/kota42/`, the two beds, the 11 tunes
`data/kultivator/kota/melodi/*.json` and the lang fragment `lang-src/kota_hidup_subtitle.*.json`, and merges the 60 entries below
into `sounds.json` (every other entry kept; it deletes only keys starting with `kota42.` and the two `ambient.kota.*` beds of
this section first). The same 60 entries, ready to paste, are in `docs/progres/m2-42V-F-suara.json`. All sounds are played by
the client only (`fx/SuaraKota42`, local `SoundEvent`s, no registry entry), so nothing else in Java needs the ids.

| Event (`kultivator:`) | Files under `kota42/` (weighted equally) | Subtitle key (`subtitles.kultivator.`) | Used by |
|---|---|---|---|
| `kota42.erhu.0` .. `.11` | `erhu_<n>` | `kota42.erhu` | `MelodiJalan` (busker, night district, wedding/funeral lead) |
| `kota42.suona.0` .. `.11` | `suona_<n>` | `kota42.suona` | wedding and funeral processions, strolling musicians |
| `kota42.pipa.0` .. `.11` | `pipa_<n>` | `kota42.pipa` | night district, tea house |
| `kota42.dizi.0` .. `.11` | `dizi_<n>` | `kota42.dizi` | night district, festival evenings |
| `kota42.gong.0` .. `.2` | `gong_<n>` (big, medium, small) | `kota42.gong` | night watch strike, processions, lion dance |
| `kota42.kentongan.0` .. `.2` | `kentongan_<n>` | `kota42.kentongan` | night watch (`ronda` tune) |
| `kota42.genderang_kecil` | `genderang_kecil_0`, `_1` | `kota42.genderang_kecil` | processions, lion dance |
| `kota42.balok_kayu` | `balok_kayu_0`, `_1` | `kota42.balok_kayu` | processions, lion dance, market tunes |
| `kota42.petasan` | `petasan_0` .. `_5` | `kota42.petasan` | `Petasan` strings (weddings, New Year, festivals, plot openings) |
| `kota42.merpati_peluit` | `merpati_peluit` | `kota42.merpati_peluit` | `MerpatiFx` (flocks that take off or circle) |
| `ambient.kota.malam_meriah` | `ambient/kota/malam_meriah` (stream) | `ambient.kota.malam_meriah` | `RiuhKota` night-district bed |
| `ambient.kota.upacara` | `ambient/kota/upacara` (stream) | `ambient.kota.upacara` | `RiuhKota` distant-procession bed |

Subtitles (id / en) are in `lang-src/kota_hidup_subtitle.id_id.json` / `.en_us.json` (10 sound captions, 2 bed captions) together
with the tune names `kultivator.kota42.melodi.<id>` (11). Credit for CREDITS.md: all of these are synthesised in
`tools/gen_kota_hidup_suara.py` (own work); the two beds also mix the existing `ambient/kota/jalan` and `kedai` loops.
