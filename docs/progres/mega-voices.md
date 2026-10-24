# Suara Jepang baru 38/39/40 (untuk orkestrator, VOICEVOX)

Dikumpulkan F3 (2026-10-24) dari `tools/gen_voices.py` (`LINES`, `DISCIPLE`, `WORLD`, `LANGIT_37`): semua baris yang
event-nya terdaftar di `voice/VoiceEvents` tetapi belum punya klip di `assets/kultivator/sounds.json`. Teks Jepang,
pembicara dan takarir sudah ada di generator, jadi cukup jalankan sekali:

    python -I tools/gen_voices.py <repo> <voicevox_dir> <voice_dl_dir>

(VOICEVOX CORE 0.17 di `%TEMP%/vvx`, venv `%TEMP%/kv`; lihat docstring generator.) Generator menulis ulang `sounds.json`
(entri suara), `lang-src/voice.*.json` dan `tools/voice_manifest.json`. Sampai klip ada, kode memakai takarir/cadangan.
Baris murid (`disciple`) dibuat untuk keenam suara m1-m3, f1-f3. Tidak ada baris baru dari 40. F3 tidak menjalankan
generator (aturan jadwal 4).

Jumlah: 159 baris (40 baris murid x 6 suara).

| Event | Pembicara | Takarir (kunci) | Jepang | Takarir Indonesia |
|---|---|---|---|---|
| `voice.elder.seteru_lerai` | elder | `kultivator.voice.elder.seteru_lerai` | もうよい！互いの宗門の恥を晒すな。 | Cukup! Kalian mempermalukan sekte masing-masing. |
| `voice.elder.seteru_ganti_rugi` | elder | `kultivator.voice.elder.seteru_ganti_rugi` | 我が宗門が償いをいたそう。 | Sekte kami akan membayar ganti rugi. |
| `voice.heaven.langit_naik` | heaven | `kultivator.voice.heaven.langit_naik` | 山は雲を越え、宗門は天に昇る。 | Gunung melampaui awan; sekte naik ke langit. |
| `voice.disciple.<m1..f3>.pamit` | disciple (6 suara) | `kultivator.voice.disciple.pamit` | 師匠、少し町へ行ってまいります。 | Guru, murid pamit ke kota sebentar. |
| `voice.disciple.<m1..f3>.pulang_belanja` | disciple (6 suara) | `kultivator.voice.disciple.pulang_belanja` | 師匠、ただいま戻りました。お土産です。 | Guru, murid pulang. Ini oleh-olehnya. |
| `voice.disciple.<m1..f3>.tawar` | disciple (6 suara) | `kultivator.voice.disciple.tawar` | 高すぎる！少しまけてくれ。 | Terlalu mahal! Kurangi sedikit. |
| `voice.disciple.<m1..f3>.tawar_sepakat` | disciple (6 suara) | `kultivator.voice.disciple.tawar_sepakat` | よし、それで手を打とう。 | Baiklah, sepakat. |
| `voice.disciple.<m1..f3>.lihat_lapak` | disciple (6 suara) | `kultivator.voice.disciple.lihat_lapak` | ふむ…この丹薬、なかなか良い。 | Hmm... pil ini lumayan. |
| `voice.disciple.<m1..f3>.copet` | disciple (6 suara) | `kultivator.voice.disciple.copet` | 待て、泥棒！ | Berhenti, pencuri! |
| `voice.disciple.<m1..f3>.makan` | disciple (6 suara) | `kultivator.voice.disciple.makan` | わあ、美味しい！ | Wah, enak sekali! |
| `voice.disciple.<m1..f3>.latih` | disciple (6 suara) | `kultivator.voice.disciple.latih` | もう一太刀！ | Satu jurus lagi! |
| `voice.disciple.<m1..f3>.salam_pagi` | disciple (6 suara) | `kultivator.voice.disciple.salam_pagi` | おはようございます、先輩！ | Selamat pagi, Kakak Senior! |
| `voice.disciple.<m1..f3>.tutup_masuk` | disciple (6 suara) | `kultivator.voice.disciple.tutup_masuk` | これより閉関いたします。お邪魔なさらぬよう。 | Murid akan menutup diri. Mohon jangan diganggu. |
| `voice.disciple.<m1..f3>.tutup_keluar` | disciple (6 suara) | `kultivator.voice.disciple.tutup_keluar` | 閉関、終わりました…気が澄んでいる。 | Pengasingan selesai... qi-ku lebih jernih. |
| `voice.disciple.<m1..f3>.hufa_minta` | disciple (6 suara) | `kultivator.voice.disciple.hufa_minta` | 先輩、突破の護法をお願いできますか。 | Senior, maukah menjagaku saat menembus? |
| `voice.disciple.<m1..f3>.hufa_jaga` | disciple (6 suara) | `kultivator.voice.disciple.hufa_jaga` | 安心して、私が護ります。 | Tenang, aku menjagamu. |
| `voice.disciple.<m1..f3>.tembus_sendiri` | disciple (6 suara) | `kultivator.voice.disciple.tembus_sendiri` | 今日、己の力で突破する！ | Hari ini aku menembus dengan kekuatanku sendiri! |
| `voice.disciple.<m1..f3>.hati_lulus` | disciple (6 suara) | `kultivator.voice.disciple.hati_lulus` | 心魔など…私は揺るがない。 | Iblis hati... tidak bisa menggoyahkanku. |
| `voice.disciple.<m1..f3>.dao_pilih` | disciple (6 suara) | `kultivator.voice.disciple.dao_pilih` | 我が道は、この道なり。 | Jalanku adalah Dao ini. |
| `voice.disciple.<m1..f3>.belajar_sendiri` | disciple (6 suara) | `kultivator.voice.disciple.belajar_sendiri` | 自分で悟った！ | Aku memahaminya sendiri! |
| `voice.disciple.<m1..f3>.serap_batu` | disciple (6 suara) | `kultivator.voice.disciple.serap_batu` | この霊石…霊気が澄んでいる。 | Batu roh ini... qi-nya murni. |
| `voice.disciple.<m1..f3>.hadiah` | disciple (6 suara) | `kultivator.voice.disciple.hadiah` | これ、あなたに。受け取ってね。 | Ini untukmu. Jangan ditolak, ya. |
| `voice.disciple.<m1..f3>.tertawan` | disciple (6 suara) | `kultivator.voice.disciple.tertawan` | 師匠…助けて… | Guru... tolong aku... |
| `voice.disciple.<m1..f3>.diselamatkan` | disciple (6 suara) | `kultivator.voice.disciple.diselamatkan` | 師匠が来てくださった！信じていました。 | Guru datang! Aku tahu Guru pasti datang. |
| `voice.disciple.<m1..f3>.iblis_bisik` | disciple (6 suara) | `kultivator.voice.disciple.iblis_bisik` | 力が…もっと力が欲しい… | Kekuatan... aku butuh lebih banyak kekuatan... |
| `voice.disciple.<m1..f3>.ditebus` | disciple (6 suara) | `kultivator.voice.disciple.ditebus` | ありがとうございます、師匠…私は戻りました。 | Terima kasih, Guru... aku kembali. |
| `voice.disciple.<m1..f3>.jalan_ganti` | disciple (6 suara) | `kultivator.voice.disciple.jalan_ganti` | 自分の道を見つけた。 | Aku sudah menemukan jalanku. |
| `voice.disciple.<m1..f3>.seteru_ejek` | disciple (6 suara) | `kultivator.voice.disciple.seteru_ejek` | 小さな宗門の弟子が、ここに座るとはな。 | Murid sekte kecil berani duduk di sini? |
| `voice.disciple.<m1..f3>.seteru_ejek_guru` | disciple (6 suara) | `kultivator.voice.disciple.seteru_ejek_guru` | 師匠に礼儀を教わらなかったのか？ | Gurumu tidak mengajarimu sopan santun? |
| `voice.disciple.<m1..f3>.seteru_balas` | disciple (6 suara) | `kultivator.voice.disciple.seteru_balas` | 口を慎め！ | Jaga mulutmu! |
| `voice.disciple.<m1..f3>.seteru_tantang` | disciple (6 suara) | `kultivator.voice.disciple.seteru_tantang` | 勇気があるなら、擂台に上がれ！ | Kalau berani, naik ke panggung! |
| `voice.disciple.<m1..f3>.seteru_terima` | disciple (6 suara) | `kultivator.voice.disciple.seteru_terima` | その挑戦、受けて立つ。 | Tantangan diterima. |
| `voice.disciple.<m1..f3>.seteru_menang` | disciple (6 suara) | `kultivator.voice.disciple.seteru_menang` | 今日のことを覚えておけ。 | Ingat hari ini. |
| `voice.disciple.<m1..f3>.seteru_kalah` | disciple (6 suara) | `kultivator.voice.disciple.seteru_kalah` | これで終わりだと思うな！ | Ini belum selesai! |
| `voice.disciple.<m1..f3>.seteru_keroyok` | disciple (6 suara) | `kultivator.voice.disciple.seteru_keroyok` | 皆、やってしまえ！ | Saudara-saudara, hajar mereka! |
| `voice.disciple.<m1..f3>.seteru_sergap` | disciple (6 suara) | `kultivator.voice.disciple.seteru_sergap` | この道は我が宗門のものだ。 | Jalan ini milik sekte kami. |
| `voice.disciple.<m1..f3>.seteru_lindungi` | disciple (6 suara) | `kultivator.voice.disciple.seteru_lindungi` | その村人を放せ！ | Lepaskan warga itu! |
| `voice.disciple.<m1..f3>.seteru_curi` | disciple (6 suara) | `kultivator.voice.disciple.seteru_curi` | その巻物を返せ、盗人め！ | Kembalikan gulungan itu, pencuri! |
| `voice.disciple.<m1..f3>.seteru_maaf` | disciple (6 suara) | `kultivator.voice.disciple.seteru_maaf` | あの件は…申し訳なかった。 | Aku... minta maaf atas kejadian itu. |
| `voice.disciple.<m1..f3>.seteru_sumpah` | disciple (6 suara) | `kultivator.voice.disciple.seteru_sumpah` | この恨み、必ず晴らすと誓う！ | Aku bersumpah akan membalas ini! |
| `voice.disciple.<m1..f3>.seteru_damai` | disciple (6 suara) | `kultivator.voice.disciple.seteru_damai` | 今日から、もう敵ではない。 | Mulai hari ini, kita bukan musuh lagi. |
| `voice.disciple.<m1..f3>.seteru_saingan` | disciple (6 suara) | `kultivator.voice.disciple.seteru_saingan` | 次の大会は、私が勝つ。 | Musim depan, aku yang menang. |
| `voice.disciple.<m1..f3>.seteru_kekasih` | disciple (6 suara) | `kultivator.voice.disciple.seteru_kekasih` | 宗門が敵同士でも…私はあなたを選ぶ。 | Walau sekte kita bermusuhan... aku tetap memilihmu. |
| `voice.kotabesar.gate_welcome` | m1 | `kultivator.voice.kotabesar.gate_welcome` | ようこそ、都へ。城内での私闘は禁じられている。 | Selamat datang di ibu kota. Bertarung sendiri di dalam tembok dilarang. |
| `voice.kotabesar.curfew` | m1 | `kultivator.voice.kotabesar.curfew` | 夜鼓が鳴った。門を閉めるぞ！ | Genderang malam sudah berbunyi. Gerbang ditutup! |
| `voice.kotabesar.suspect` | m1 | `kultivator.voice.kotabesar.suspect` | おい、そこの者。妙な真似はするなよ。 | Hei, kau. Jangan macam-macam. |
| `voice.kotabesar.wanted` | m1 | `kultivator.voice.kotabesar.wanted` | 手配書の顔だ！捕らえろ！ | Itu wajah di surat buronan! Tangkap! |
| `voice.kotabesar.fine_paid` | m2 | `kultivator.voice.kotabesar.fine_paid` | 罰金は納められた。行ってよし。 | Denda sudah dibayar. Silakan pergi. |
| `voice.kotabesar.jail` | m1 | `kultivator.voice.kotabesar.jail` | 牢で頭を冷やすがいい。 | Dinginkan kepalamu di sel. |
| `voice.kotabesar.bank_welcome` | m2 | `kultivator.voice.kotabesar.bank_welcome` | 銭荘へようこそ。霊石はしかとお預かりいたします。 | Selamat datang di rumah uang. Batu rohmu aman bersama kami. |
| `voice.kotabesar.bank_note` | m2 | `kultivator.voice.kotabesar.bank_note` | この為替手形は、どの都の銭荘でも換金できます。 | Surat wesel ini bisa ditukar di rumah uang ibu kota mana pun. |
| `voice.kotabesar.gamble_cut` | m3 | `kultivator.voice.kotabesar.gamble_cut` | 切るぞ…さあ、天か地か！ | Kupotong… nah, langit atau bumi! |
| `voice.kotabesar.gamble_win` | m3 | `kultivator.voice.kotabesar.gamble_win` | 出た！霊光だ！ | Keluar! Cahaya roh! |
| `voice.kotabesar.gamble_lose` | m3 | `kultivator.voice.kotabesar.gamble_lose` | ただの石…これもまた天命よ。 | Cuma batu… itu pun takdir. |
| `voice.kotabesar.grand_auction` | m2 | `kultivator.voice.kotabesar.grand_auction` | 大競売会、ただいま開幕でございます！ | Lelang Agung kini dimulai! |
| `voice.kotabesar.alchemy_start` | elder | `kultivator.voice.kotabesar.alchemy_start` | 丹会を始める。炉に火を入れよ！ | Pertemuan Pil dimulai. Nyalakan tungkumu! |
| `voice.kotabesar.alchemy_winner` | elder | `kultivator.voice.kotabesar.alchemy_winner` | 見事な丹だ。今回の首席はそなたである。 | Pil yang indah. Kali ini kaulah yang pertama. |
| `voice.kotabesar.smith_master` | m1 | `kultivator.voice.kotabesar.smith_master` | 良い鉄は、良い火を待つものだ。 | Besi yang baik menunggu api yang baik. |
| `voice.kotabesar.guild_bounty` | f2 | `kultivator.voice.kotabesar.guild_bounty` | 新しい依頼が出ています。腕に覚えは？ | Ada misi baru. Yakin dengan kemampuanmu? |
| `voice.kotabesar.guild_rank` | f2 | `kultivator.voice.kotabesar.guild_rank` | おめでとうございます、位が一つ上がりました。 | Selamat, pangkatmu naik satu. |
| `voice.kotabesar.recruit_call` | m1 | `kultivator.voice.kotabesar.recruit_call` | 我が宗門に入りたい者は、前へ！ | Yang ingin masuk sekte kami, maju! |
| `voice.kotabesar.beast_market` | m3 | `kultivator.voice.kotabesar.beast_market` | この子は満月の夜に生まれた霊狼だよ。 | Yang ini serigala roh yang lahir di malam purnama. |
| `voice.kotabesar.inn_welcome` | f1 | `kultivator.voice.kotabesar.inn_welcome` | いらっしゃいませ。お泊まりですか、お食事ですか？ | Selamat datang! Mau menginap atau makan? |
| `voice.kotabesar.library_hush` | elder | `kultivator.voice.kotabesar.library_hush` | 書閣ではお静かに。 | Harap tenang di perpustakaan. |
| `voice.kotabesar.temple_bell` | elder | `kultivator.voice.kotabesar.temple_bell` | 鐘の音に心を澄ませよ。 | Biarkan lonceng menjernihkan hatimu. |
| `voice.kotabesar.teleport_ready` | f3 | `kultivator.voice.kotabesar.teleport_ready` | 陣が開きます。目を閉じてください。 | Formasi terbuka. Silakan pejamkan mata. |
| `voice.kotabesar.dock_depart` | m2 | `kultivator.voice.kotabesar.dock_depart` | 出航！雲の上で会おう！ | Berangkat! Sampai jumpa di atas awan! |
| `voice.kotabesar.palace_audience` | boss | `kultivator.voice.kotabesar.palace_audience` | 面を上げよ。 | Angkat kepalamu. |
| `voice.kotabesar.lantern_festival` | f1 | `kultivator.voice.kotabesar.lantern_festival` | 灯籠祭りが始まりました！謎かけはいかが？ | Festival Lentera sudah dimulai! Mau teka-teki? |
| `voice.kotabesar.riddle_right` | f1 | `kultivator.voice.kotabesar.riddle_right` | 正解です！お見事！ | Benar! Hebat! |
| `voice.kotabesar.beast_tide` | m1 | `kultivator.voice.kotabesar.beast_tide` | 獣潮だ！城壁へ急げ！ | Gelombang binatang! Cepat ke tembok! |
| `voice.kotabesar.defense_won` | m1 | `kultivator.voice.kotabesar.defense_won` | 都は守られた！ | Ibu kota selamat! |
| `voice.kotabesar.tournament_open` | boss | `kultivator.voice.kotabesar.tournament_open` | 天下の英傑よ、この擂台で力を示せ！ | Para pahlawan dunia, tunjukkan kekuatan kalian di panggung ini! |
| `voice.tempat.village_elder` | elder | `kultivator.voice.tempat.village_elder` | 旅のお方、村で少し休んでいきなされ。 | Pengembara, beristirahatlah sebentar di desa kami. |
| `voice.tempat.village_fear` | elder | `kultivator.voice.tempat.village_fear` | ど、どうかお許しを…しゅうりは我らがいたします。 | M-mohon ampun... kami sendiri yang akan memperbaikinya. |
| `voice.tempat.bill_request` | m1 | `kultivator.voice.tempat.bill_request` | 恐れながら…壊れた分の弁償をお願いしたい。 | Maaf... kami harus meminta ganti rugi atas kerusakannya. |
| `voice.tempat.bill_paid` | m1 | `kultivator.voice.tempat.bill_paid` | ありがたい。すぐに職人を集めます。 | Terima kasih. Akan segera kukumpulkan para tukang. |
| `voice.tempat.bill_refused` | m1 | `kultivator.voice.tempat.bill_refused` | …この恨み、町は忘れませんぞ。 | ...Kota ini tidak akan melupakannya. |
| `voice.tempat.repair_work` | m2 | `kultivator.voice.tempat.repair_work` | よいしょ！もう一枚！ | Hup! Satu papan lagi! |
| `voice.tempat.clan_patriarch` | boss | `kultivator.voice.tempat.clan_patriarch` | 我が一族の地に、何用か。 | Ada urusan apa di tanah klanku? |
| `voice.tempat.young_master` | m3 | `kultivator.voice.tempat.young_master` | 身の程を知れ。この地で俺に逆らうとはな！ | Tahu dirilah. Berani menentangku di tanahku sendiri! |
| `voice.tempat.hermit` | elder | `kultivator.voice.tempat.hermit` | 縁ある者よ、座るがよい。 | Engkau yang berjodoh denganku, duduklah. |
| `voice.tempat.hermit_koan` | elder | `kultivator.voice.tempat.hermit_koan` | 空の器にこそ、道は満ちる。 | Hanya wadah yang kosong dapat diisi Dao. |
| `voice.tempat.inheritance` | heaven | `kultivator.voice.tempat.inheritance` | 我が遺産を継ぐ資格、示してみよ。 | Tunjukkan bahwa kau layak mewarisi peninggalanku. |
| `voice.tempat.inheritance_pass` | heaven | `kultivator.voice.tempat.inheritance_pass` | よかろう。我が道、汝に託す。 | Baiklah. Jalanku kuserahkan padamu. |
| `voice.tempat.demon_hideout` | demon | `kultivator.voice.tempat.demon_hideout` | 生きて帰れると思うな！ | Jangan kira kau bisa pulang hidup-hidup! |
| `voice.tempat.captive_saved` | f1 | `kultivator.voice.tempat.captive_saved` | 助かった…本当にありがとうございます！ | Kami selamat... terima kasih, sungguh! |
| `voice.tempat.waystation` | m2 | `kultivator.voice.tempat.waystation` | 駅で一休みしていきな。水も飯もあるぞ。 | Mampirlah istirahat di pos. Ada air dan makanan. |
| `voice.tempat.peddler` | m2 | `kultivator.voice.tempat.peddler` | 珍しい品だよ、遠い町から運んできたんだ！ | Barang langka, kubawa dari kota yang jauh! |
| `voice.tempat.bandit` | m3 | `kultivator.voice.tempat.bandit` | 通行料を置いていけ！ | Tinggalkan upeti kalau mau lewat! |
| `voice.tempat.tribulation_peak` | heaven | `kultivator.voice.tempat.tribulation_peak` | この峰は、幾多の劫を見届けてきた。 | Puncak ini telah menyaksikan tak terhitung kesengsaraan. |
| `voice.tempat.sect_visit` | m1 | `kultivator.voice.tempat.sect_visit` | 本宗に何のご用でしょうか。 | Ada keperluan apa ke sekte kami? |
| `voice.tempat.child_play` | f1 | `kultivator.voice.tempat.child_play` | 鬼さんこちら！ | Ayo tangkap aku! |
| `voice.cerita.ruyi_thanks` | f2 | `kultivator.voice.cerita.ruyi_thanks` | このご恩は、決して忘れません。 | Budi ini takkan pernah kulupakan. |
| `voice.cerita.moying_reveal` | m3 | `kultivator.voice.cerita.moying_reveal` | 気づくのが遅かったな…！ | Kau terlambat menyadarinya...! |
| `voice.cerita.bao_verdict` | elder | `kultivator.voice.cerita.bao_verdict` | 証拠は揃った。判決を下す！ | Bukti sudah lengkap. Putusan dijatuhkan! |
| `voice.cerita.coronation` | boss | `kultivator.voice.cerita.coronation` | 今日より、この都を汝に託す。 | Mulai hari ini, kota ini kuserahkan padamu. |
| `voice.tema.pelabuhan.welcome` | f2 | `kultivator.voice.tema.pelabuhan.welcome` | 港へようこそ。荷を下ろすなら、まず税関へ。 | Selamat datang di pelabuhan. Kalau mau bongkar muatan, ke bea cukai dulu. |
| `voice.tema.pelabuhan.fish_call` | f1 | `kultivator.voice.tema.pelabuhan.fish_call` | 獲れたてだよ！朝の霊魚、今だけだよ！ | Baru ditangkap! Ikan roh pagi, cuma sekarang! |
| `voice.tema.pelabuhan.ship_arrive` | m2 | `kultivator.voice.tema.pelabuhan.ship_arrive` | 南の船が入ったぞ！荷揚げを急げ！ | Kapal selatan masuk! Cepat bongkar muatan! |
| `voice.tema.pelabuhan.ship_depart` | m2 | `kultivator.voice.tema.pelabuhan.ship_depart` | 錨を上げろ！帆を張れ！ | Angkat jangkar! Kembangkan layar! |
| `voice.tema.pelabuhan.lighthouse` | elder | `kultivator.voice.tema.pelabuhan.lighthouse` | この灯が消えたら、海は人を帰さぬ。 | Kalau pelita ini padam, laut tak akan memulangkan siapa pun. |
| `voice.tema.pelabuhan.pirates` | m1 | `kultivator.voice.tema.pelabuhan.pirates` | 海賊だ！桟橋を守れ！ | Bajak laut! Pertahankan dermaga! |
| `voice.tema.pelabuhan.black_shark` | m3 | `kultivator.voice.tema.pelabuhan.black_shark` | この港の灯は、俺がもらう。 | Pelita pelabuhan ini akan kuambil. |
| `voice.tema.pelabuhan.sea_crossing` | m2 | `kultivator.voice.tema.pelabuhan.sea_crossing` | 霧の向こうは果てなき海だ。しっかり掴まってな。 | Di balik kabut ada Laut Tanpa Batas. Pegangan yang kuat. |
| `voice.tema.pelabuhan.goddess_day` | elder | `kultivator.voice.tema.pelabuhan.goddess_day` | 海の女神に祈りを。今年も無事の航海を。 | Berdoalah pada Dewi Laut, demi pelayaran selamat setahun lagi. |
| `voice.tema.pelabuhan.shipwright` | elder | `kultivator.voice.tema.pelabuhan.shipwright` | 良い船は、竜骨から嘘をつかん。 | Kapal yang baik tak pernah berbohong, mulai dari lunasnya. |
| `voice.tema.pelabuhan.lamp_lit` | elder | `kultivator.voice.tema.pelabuhan.lamp_lit` | 灯がともった…おかえり、みんな。 | Pelita menyala... selamat pulang, semuanya. |
| `voice.tema.oasis.welcome` | m1 | `kultivator.voice.tema.oasis.welcome` | 玉門を越えた先は砂の海だ。水は持ったか？ | Di balik Gerbang Giok ada lautan pasir. Sudah bawa air? |
| `voice.tema.oasis.bazaar` | m3 | `kultivator.voice.tema.oasis.bazaar` | さあさあ、西の香辛料に絨毯だ！値段は相談次第！ | Ayo, ayo! Rempah Barat dan permadani! Harga bisa dirunding! |
| `voice.tema.oasis.haggle_no` | m3 | `kultivator.voice.tema.oasis.haggle_no` | それじゃ商売にならんよ、旦那。 | Kalau segitu, aku tak bisa makan, Tuan. |
| `voice.tema.oasis.haggle_yes` | m3 | `kultivator.voice.tema.oasis.haggle_yes` | ええい、持ってけ！今日だけだぞ！ | Ah, ambillah! Hanya hari ini! |
| `voice.tema.oasis.caravan` | m1 | `kultivator.voice.tema.oasis.caravan` | 隊商が着いたぞ！駱駝に水をやれ！ | Kafilah sudah tiba! Beri minum unta-unta! |
| `voice.tema.oasis.sandstorm` | m1 | `kultivator.voice.tema.oasis.sandstorm` | 砂嵐だ！駱駝を伏せさせろ、中へ入れ！ | Badai pasir! Suruh unta berlutut, masuk ke dalam! |
| `voice.tema.oasis.water_priestess` | f3 | `kultivator.voice.tema.oasis.water_priestess` | 水は命。一滴たりとも無駄にしてはなりません。 | Air adalah kehidupan. Tak satu tetes pun boleh terbuang. |
| `voice.tema.oasis.lord` | boss | `kultivator.voice.tema.oasis.lord` | この町が砂に呑まれる日は来させぬ。 | Takkan kubiarkan datang hari pasir menelan kota ini. |
| `voice.tema.oasis.scorpion` | demon | `kultivator.voice.tema.oasis.scorpion` | 水は我が蠍のもの。砂に還れ！ | Air ini milik kalajengkingku. Kembalilah ke pasir! |
| `voice.tema.oasis.dance` | f1 | `kultivator.voice.tema.oasis.dance` | 今宵は満月、旋舞をご覧あれ！ | Malam ini purnama, saksikanlah tari putar! |
| `voice.tema.air.welcome` | f1 | `kultivator.voice.tema.air.welcome` | 舟に乗っていくかい？町を一回りしてあげるよ。 | Mau naik perahu? Kuantar keliling kota. |
| `voice.tema.air.boat_song` | f1 | `kultivator.voice.tema.air.boat_song` | 水は流れて、橋はめぐる… | Air mengalir, jembatan silih berganti... |
| `voice.tema.air.silk` | f3 | `kultivator.voice.tema.air.silk` | 蘇家の絹は、五代にわたり嘘をついたことがありません。 | Sutra keluarga Su tak pernah berbohong selama lima generasi. |
| `voice.tema.air.tea` | m1 | `kultivator.voice.tema.air.tea` | 一煎目は香りを、二煎目は心を味わうのです。 | Seduhan pertama untuk harumnya, seduhan kedua untuk hatinya. |
| `voice.tema.air.thief` | m2 | `kultivator.voice.tema.air.thief` | 泥棒だ！舟で逃げたぞ！ | Pencuri! Dia kabur naik perahu! |
| `voice.tema.air.lantern_night` | f1 | `kultivator.voice.tema.air.lantern_night` | 灯籠を流しましょう。迷った魂が帰れるように。 | Mari melepas lentera, agar arwah yang tersesat bisa pulang. |
| `voice.tema.air.ghost` | f2 | `kultivator.voice.tema.air.ghost` | わたしの布は…まだ織りかけなの。 | Kainku... masih belum selesai ditenun. |
| `voice.tema.air.ghost_rest` | f2 | `kultivator.voice.tema.air.ghost_rest` | ありがとう…これで、やっと眠れる。 | Terima kasih... akhirnya aku bisa tidur. |
| `voice.tema.air.dragon_boat` | m1 | `kultivator.voice.tema.air.dragon_boat` | 太鼓を合わせろ！竜舟、進め！ | Ikuti genderang! Perahu naga, maju! |
| `voice.tema.air.poet` | elder | `kultivator.voice.tema.air.poet` | 雨を聴く亭で、詩を一つ詠まぬか。 | Maukah kau menggubah satu bait di Paviliun Menyimak Hujan? |
| `voice.tema.gunung.welcome` | m1 | `kultivator.voice.tema.gunung.welcome` | 千段の石段の始まりだ。足元に気をつけて。 | Inilah awal seribu anak tangga. Hati-hati langkahmu. |
| `voice.tema.gunung.porter` | m2 | `kultivator.voice.tema.gunung.porter` | 一歩ずつ、一歩ずつ。山は逃げやせん。 | Selangkah demi selangkah. Gunung tak akan lari. |
| `voice.tema.gunung.lift` | m1 | `kultivator.voice.tema.gunung.lift` | 乗った乗った！雲の上まで一息だ！ | Ayo naik! Ke atas awan dalam sekali napas! |
| `voice.tema.gunung.lift_stuck` | m1 | `kultivator.voice.tema.gunung.lift_stuck` | くそっ、綱が…！動くな、今助ける！ | Sial, talinya...! Jangan bergerak, aku datang! |
| `voice.tema.gunung.monkey` | f1 | `kultivator.voice.tema.gunung.monkey` | あっ、猿に饅頭を取られたね。果物を投げてごらん。 | Wah, bakpaomu direbut kera. Coba lempar buah. |
| `voice.tema.gunung.rockfall` | m1 | `kultivator.voice.tema.gunung.rockfall` | 落石だ！道から離れろ！ | Batu runtuh! Menjauh dari jalan! |
| `voice.tema.gunung.abbot` | elder | `kultivator.voice.tema.gunung.abbot` | 山頂に至る道は千あれど、心に至る道は一つ。 | Seribu jalan menuju puncak, tetapi hanya satu menuju hati. |
| `voice.tema.gunung.pilgrim` | m2 | `kultivator.voice.tema.gunung.pilgrim` | 三歩一拝、願いよ届け。 | Tiga langkah satu sujud, semoga permohonanku sampai. |
| `voice.tema.gunung.buddha_light` | f3 | `kultivator.voice.tema.gunung.buddha_light` | 見なさい、雲の上の光の輪を。仏光です。 | Lihatlah lingkaran cahaya di atas awan. Cahaya Buddha. |
| `voice.tema.gunung.cloud_bell` | elder | `kultivator.voice.tema.gunung.cloud_bell` | 雲の鐘が鳴る。山はあなたを覚えました。 | Lonceng Awan berbunyi. Gunung kini mengingatmu. |
| `voice.tema.iblis.gate_toll` | m1 | `kultivator.voice.tema.iblis.gate_toll` | 通るなら貢ぎ物を置いていけ。命よりは安いぞ。 | Kalau mau lewat, tinggalkan upeti. Lebih murah dari nyawamu. |
| `voice.tema.iblis.gate_weak` | m1 | `kultivator.voice.tema.iblis.gate_weak` | こんな小物、餌にもならん。帰れ。 | Ikan teri sepertimu bahkan tak pantas jadi umpan. Pulang sana. |
| `voice.tema.iblis.pact` | m3 | `kultivator.voice.tema.iblis.pact` | 市場の中で血を流すな。それがこの町唯一の掟だ。 | Jangan tumpahkan darah di dalam pasar. Itulah satu-satunya hukum kota ini. |
| `voice.tema.iblis.hunt_hour` | demon | `kultivator.voice.tema.iblis.hunt_hour` | 夜道の通行料だ。金か、血か？ | Upeti jalan malam. Uang, atau darah? |
| `voice.tema.iblis.arena_open` | m1 | `kultivator.voice.tema.iblis.arena_open` | 血の階段へようこそ！生きて降りられるかな？ | Selamat datang di Tangga Darah! Yakin bisa turun hidup-hidup? |
| `voice.tema.iblis.arena_win` | m1 | `kultivator.voice.tema.iblis.arena_win` | 見たか！新しい名が血に刻まれたぞ！ | Kalian lihat?! Nama baru terukir dengan darah! |
| `voice.tema.iblis.broker` | f3 | `kultivator.voice.tema.iblis.broker` | 欲しいものは何？ここでは、値段のないものはないのよ。 | Apa yang kau inginkan? Di sini, tak ada yang tak berharga. |
| `voice.tema.iblis.forbidden` | elder | `kultivator.voice.tema.iblis.forbidden` | この書は、読む者の何かを喰らう。それでも開くか？ | Kitab ini memakan sesuatu dari pembacanya. Masih mau membukanya? |
| `voice.tema.iblis.captive` | f1 | `kultivator.voice.tema.iblis.captive` | おねがい…村に帰りたいの。 | Tolong... aku ingin pulang ke desaku. |
| `voice.tema.iblis.lord` | boss | `kultivator.voice.tema.iblis.lord` | 正道の者が、わざわざ地獄の門を叩くとはね。 | Jarang sekali, orang jalan lurus mengetuk gerbang neraka atas kemauan sendiri. |
| `voice.tema.iblis.hunter` | f2 | `kultivator.voice.tema.iblis.hunter` | 正道の名において、その首、貰い受ける！ | Atas nama jalan lurus, kepalamu kuambil! |
| `voice.tema.iblis.raid` | m1 | `kultivator.voice.tema.iblis.raid` | 正道の連中が門に来た！迎え撃て！ | Orang-orang jalan lurus di gerbang! Hadapi mereka! |
| `voice.tema.iblis.dawn` | m3 | `kultivator.voice.tema.iblis.dawn` | この町にも、朝は来るんだな。 | Ternyata kota ini pun bisa didatangi pagi. |
| `voice.tema.dagang.sold` | m2 | `kultivator.voice.tema.dagang.sold` | 良い取引でした。また来てくださいよ。 | Transaksi yang bagus. Datang lagi, ya. |
| `voice.tema.dagang.saturated` | m2 | `kultivator.voice.tema.dagang.saturated` | その品はもう溢れててね…安くしか買えんよ。 | Barang itu sudah membanjir... aku cuma bisa beli murah. |
| `voice.sekte_langit.bajak_datang` | Kapten Bajak Awan | `kultivator.voice.sekte_langit.bajak_datang` | 倉の中身を差し出せ！さもなくばこの島を焼き払う！ | Serahkan isi gudang kalian, atau pulau ini kami bakar! |
| `voice.sekte_langit.longbo_datang` | Raksasa Longbo | `kultivator.voice.sekte_langit.longbo_datang` | 祖先は山を支える亀を六匹釣った。お前の亀が七匹目だ！ | Leluhurku memancing enam kura-kura penyangga gunung. Kura-kuramu yang ketujuh! |
| `voice.sekte_langit.longbo_kalah` | Raksasa Longbo | `kultivator.voice.sekte_langit.longbo_kalah` | 馬鹿な…亀一匹さえ釣れぬとは… | Mustahil... seekor kura-kura pun tak terpancing... |
| `voice.sekte_langit.tamu_salam` | Tetua Tamu | `kultivator.voice.sekte_langit.tamu_salam` | 雲の上の同道よ、お招きに感謝する。 | Sahabat di atas awan, terima kasih atas sambutanmu. |
| `voice.sekte_langit.utusan_salam` | Utusan Istana Langit | `kultivator.voice.sekte_langit.utusan_salam` | 天宮はそなたの島を見ていた。天命を受けよ。 | Istana Langit telah melihat pulaumu. Terimalah perintahnya. |
| `voice.sekte_langit.utusan_tantang` | Langit | `kultivator.voice.sekte_langit.utusan_tantang` | 天は聞いた。黄昏に備えよ。 | Langit telah mendengar. Bersiaplah saat senja. |
