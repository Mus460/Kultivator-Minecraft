# Sekte yang Hidup (DESIGN 36): proposed NEW Japanese voice lines

Collected by sekte:finish from the ten module reports. Nothing here is generated yet: sounds.json and
tools/gen_voices.py were not edited. Until a line exists, the code reuses existing lines (greet, greet_alt, thanks,
loyal, farewell, tired, fear, careful, cheer, victory, cultivate, protect, injured, attack_alt, boss.awaken/taunt/defeat,
elder.welcome). `<v>` = each disciple voice m1, m2, m3, f1, f2, f3.

| key | speaker | caption (id) | Japanese | module, when |
|---|---|---|---|---|
| disciple.<v>.sulk | m1-m3/f1-f3 | Aku tidak mau. | やりたくありません。 | P, defiant disciple refuses an order |
| disciple.<v>.rebel | m1-m3/f1-f3 | Guru, hari ini aku menantangmu! | 師匠、今日こそ挑ませてもらう！ | P, rebel duel challenge |
| disciple.<v>.submit | m1-m3/f1-f3 | Aku kalah... aku akan tetap di sini. | 負けました…ここに残ります。 | P, rebel loses and stays |
| disciple.<v>.leave_night | m1-m3/f1-f3 | Maafkan aku, Guru. | 師匠、お許しください。 | P, sneaking out at night |
| disciple.<v>.oath | m1-m3/f1-f3 | Mari bersumpah menjadi saudara! | 義兄弟の契りを結ぼう！ | R, sworn-sibling oath |
| disciple.<v>.tea | m1-m3/f1-f3 | Silakan, minum tehnya. | お茶をどうぞ。 | R, tea meeting |
| disciple.<v>.grudge | m1-m3/f1-f3 | Aku tidak akan memaafkanmu. | お前だけは許さない。 | R, grudge formed |
| disciple.<v>.beloved | m1-m3/f1-f3 | Asal bersamamu. | あなたと一緒なら。 | R, lovers / Dao partners |
| disciple.<v>.leitai_salam | m1-m3/f1-f3 | Mohon petunjuknya! | お手合わせ願います！ | T, salute before a lei tai bout |
| disciple.<v>.leitai_juara | m1-m3/f1-f3 | Guru, gelar ini untuk sekte kita! | 師匠、この栄冠は宗門のために！ | T, champion |
| disciple.<v>.leitai_kalah | m1-m3/f1-f3 | Aku belum cukup kuat... musim depan! | まだ力が足りない…次の大会こそ！ | T, lost the final |
| elder.leitai_buka | elder | Turnamen sekte dibuka! Murid-murid, naiklah ke panggung! | 宗門大会、開幕！弟子たちよ、擂台に上がれ！ | T, tournament opens |
| boss.tamu_tantang | boss | Sekte kami datang untuk menguji pedang kalian. | 我が宗門、貴殿らの剣を試しに参った。 | T, inter-sect visitors |
| disciple.<v>.climb_peak | m1-m3/f1-f3 | Aku sampai di puncak! | 頂に着いた！ | G, reached step 1000 |
| disciple.<v>.climb_illusion | m1-m3/f1-f3 | Ini hanya ilusi… aku tidak akan mundur! | これは幻だ…退かぬ！ | G, heart-demon illusion |
| disciple.<v>.mourn | m1-m3/f1-f3 | Beristirahatlah dengan tenang. | 安らかに眠れ。 | A, funeral, first mourner kneels |
| disciple.<v>.study | m1-m3/f1-f3 | Kitab ini... aku mulai mengerti. | この書、少しずつ分かってきた。 | A, reader sits down |
| disciple.<v>.qingming | m1-m3/f1-f3 | Para leluhur, terimalah hormat kami. | ご先祖様、我らの礼をお受けください。 | A, Qingming begins |
| disciple.<v>.report | m1-m3/f1-f3 | Ketua, laporan pagi ini. | 宗主、今朝のご報告です。 | E, elder's morning report (code already plays it when present, else greet) |
| disciple.<v>.depart | m1-m3/f1-f3 | Murid berangkat, Guru. | 行ってまいります、師匠。 | X, mission team leaves |
| disciple.<v>.mission_report | m1-m3/f1-f3 | Guru, murid kembali dan melapor. | 師匠、ただいま戻りました。ご報告いたします。 | X, team back at the gate (X proposed the key `report`; renamed because E's code already uses `report`) |
| disciple.<v>.abandoned | m1-m3/f1-f3 | Guru... meninggalkanku? | 師匠……私を見捨てたのですか。 | X, injured disciple left behind |
| disciple.<v>.robe | m1-m3/f1-f3 | Jubah baru dengan lambang sekte... terima kasih, Guru. | 宗門の紋の新しい衣…ありがとうございます、師匠。 | S, new uniform |
| elder.banner | elder | Bendera sekte telah dikibarkan di gerbang. | 宗門の旗が山門に掲げられました。 | S, first banner raised |
| boss.sect_master | boss | Sektemu berani menantang kami? | 貴様の宗門が我らに挑むか。 | D, enemy sect master at the gate |
| boss.sect_master_defeat | boss | Hari ini... sekte kami tunduk. | 今日より…我が宗門は従おう。 | D, enemy sect master defeated |
| elder.envoy | elder | Utusan sekte kami datang dengan damai. | 我が宗門の使者、和をもって参りました。 | D, NPC envoy arrives |
| disciple.<v>.envoy_return | m1-m3/f1-f3 | Guru, tugas utusan telah selesai. | 師匠、使者の務めを果たしてまいりました。 | D, own envoy returns |
| disciple.<v>.harvest | m1-m3/f1-f3 | Panennya bagus, Guru! | 師匠、良い収穫です！ | W, herb harvest |
| disciple.<v>.ore_found | m1-m3/f1-f3 | Guru, ada yang bersinar di dalam batu! | 師匠、岩の中で何かが光っています！ | W, rare find in the mine |

Totals: 24 disciple keys (x6 voices = 144 clips), 3 elder, 3 boss. Only `disciple.<v>.report` is already looked up by
code; the others need a one-line swap from the reused key once the clips exist.
