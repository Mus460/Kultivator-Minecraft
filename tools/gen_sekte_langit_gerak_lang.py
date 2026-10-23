"""Writes lang-src/sekte_langit_gerak.{id_id,en_us}.json (module 37M, Sekte Langit movement; identical keys, Indonesian
first). Run: python tools/gen_sekte_langit_gerak_lang.py [repo root]
"""
import collections
import json
import os
import sys

root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
idk = collections.OrderedDict()
en = collections.OrderedDict()


def k(key, i, e):
    idk[key] = i
    en[key] = e


k("entity.kultivator.feri_awan", "Feri Awan", "Cloud Ferry")
k("kultivator.formasi.spec.awan_transfer.name", "Formasi Pindah Awan", "Cloud Transfer Formation")
k("kultivator.formasi.spec.awan_transfer.desc",
  "Sepasang simpul perpindahan antara tanah dan pulau langit sekte. Satu simpul berdiri di wilayah aula langit (dibangun Inti Awan tingkat 1), simpul lainnya kamu bangun sendiri di tanah, paling jauh 128 blok mendatar dari prasasti, setinggi apa pun. Klik kanan mata formasi sambil jongkok dengan tangan kosong untuk membuka peta simpul: kanal 3 detik (1,5 detik dengan penjaga), 10 qi dari pelancong, 1 Daya dari simpul asal (gratis dengan penjaga), jeda 10 detik. Hingga 4 murid yang mengikuti ikut serta. Paling banyak 2 simpul awan per sekte.",
  "A pair of transfer nodes between the ground and the sect's sky island. One node stands in the sky hall's territory (built by the Cloud Core at tier 1); you lay the other one yourself on the ground, at most 128 blocks from the stele horizontally, at any height. Sneak-use the formation eye with an empty hand to open the node map: a 3 second channel (1.5 with a keeper), 10 qi from the traveller, 1 Daya from the source node (free with a keeper), a 10 second cooldown. Up to 4 following disciples come along. At most 2 cloud nodes per sect.")
k("kultivator.formasi.spec.awan_transfer.hint",
  "Cincin 3×3 kuarsa halus di sekeliling mata formasi dan satu balok kecubung di tiap sudut diagonal (2 blok dari mata). Simpul tanah: dalam 128 blok mendatar dari prasasti aula langit.",
  "A 3×3 ring of smooth quartz around the formation eye and an amethyst block on each diagonal corner (2 blocks from the eye). Ground node: within 128 blocks horizontally of the sky hall's stele.")
k("kultivator.formasi.spec.awan_transfer.source",
  "Huainanzi (dikutip dalam Religions 14(11):1366, \"Searching for the Location of Lord Lao\"): Kunlun bertingkat tiga, dari Liangfeng di bawah sampai langit atas, didaki lapis demi lapis; Kunlun juga disebut salah satu tiang penghubung bumi dan langit (Wikipedia \"Eight Pillars\"). Pasangan simpul, jarak 128 blok dan biayanya adalah rancangan permainan.",
  "Huainanzi (quoted in Religions 14(11):1366, \"Searching for the Location of Lord Lao\"): the three-tiered Kunlun, from Liangfeng below up to the upper heaven, climbed tier by tier; Kunlun is also named a pillar joining earth and heaven (Wikipedia \"Eight Pillars\"). The node pair, the 128 block reach and the costs are game design.")
k("kultivator.formasi.spec.jaring_awan.name", "Formasi Jaring Awan", "Cloud Net Formation")
k("kultivator.formasi.spec.jaring_awan.desc",
  "Jaring awan yang direntang di antara empat Tiang Jaring di tepi pulau langit (tingkat 5). Jaring dasar sekte selalu menangkap anggota yang jatuh; selama formasi ini berjalan, jaring itu juga menangkap tamu yang diundang dan rombongan sekutu, dan anggota bisa melakukan Terjun Awan: melangkah atau melompat dari tepi pulau lalu tahan jongkok (dalam 2 detik) untuk meluncur pelan sampai tanah tanpa luka. Penyerang tidak pernah ditangkap. Upkeep 6 Daya per jam (seperempatnya saat pulau sepi); bisa dijaga murid.",
  "A net of cloud strung between the four Net Poles on the sky island's rim (tier 5). The sect's base net always catches falling members; while this formation runs it also catches invited guests and allied visitors, and members may take the Cloud Dive: step or jump off the island's rim, then hold sneak (within 2 seconds) to glide gently down to the ground unhurt. Attackers are never caught. Upkeep 6 Daya an hour (a quarter while the island is empty); a disciple can keep it.")
k("kultivator.formasi.spec.jaring_awan.hint",
  "Di cakram formasi pulau langit: cincin 3×3 kuarsa halus di sekeliling mata, balok kecubung di bawah mata, empat tiang pilar kuarsa ×2 bermahkota kecubung 3 blok dari mata di tiap sisi, dan tongkat End di tiap sudut diagonal 2 blok dari mata. Keempat Tiang Jaring di tepi pulau harus tetap berdiri.",
  "On the sky island's formation disc: a 3×3 ring of smooth quartz around the eye, an amethyst block under the eye, four quartz pillar ×2 posts crowned with amethyst 3 blocks from the eye on each side, and an end rod on each diagonal corner 2 blocks from the eye. The four Net Poles on the island's rim must still stand.")
k("kultivator.formasi.spec.jaring_awan.source",
  "Liezi, \"Tang Wen\" (Wikipedia \"Guixu\", \"Ao (turtle)\"): Kaisar Langit menyuruh Yuqiang mengirim lima belas kura-kura raksasa untuk menyangga lima gunung melayang di Guixu bergiliran agar tidak hanyut. Jaring awan, tiang dan aturannya adalah rancangan permainan.",
  "Liezi, \"Tang Wen\" (Wikipedia \"Guixu\", \"Ao (turtle)\"): the Emperor of Heaven had Yuqiang send fifteen giant turtles to hold up the five floating mountains of Guixu in turns so they would not drift away. The cloud net, the poles and the rules are game design.")
k("kultivator.sekte_langit.jatuh.tangkap", "Jaring Awan menangkapmu.", "The Cloud Net catches you.")
k("kultivator.sekte_langit.jatuh.terjun", "Terjun Awan: meluncur turun di atas awan...", "Cloud Dive: gliding down on the clouds...")
k("kultivator.sekte_langit.jatuh.terjun_mendarat", "Mendarat dengan ringan, seperti daun jatuh.", "A light landing, like a falling leaf.")
k("kultivator.sekte_langit.jatuh.terjun_sibuk", "Terjun Awan tertahan: %s", "No Cloud Dive now: %s")
k("kultivator.sekte_langit.gerak.pindah.simpul_tanah",
  "Simpul Pindah Awan di tanah tercatat untuk sektemu (paling jauh %s blok mendatar dari prasasti).",
  "Ground Cloud Transfer node registered to your sect (at most %s blocks from the stele horizontally).")
k("kultivator.sekte_langit.gerak.jaring.mati", "Sistem Sekte Langit dimatikan", "Sky sect systems are switched off")
k("kultivator.sekte_langit.gerak.jaring.bukan_langit", "Aula belum berdiri di pulau langit", "The hall does not stand on a sky island yet")
k("kultivator.sekte_langit.gerak.jaring.tingkat", "Pulau langit belum mencapai tingkat %s", "The sky island has not reached tier %s yet")
k("kultivator.sekte_langit.gerak.jaring.luar", "Mata formasi harus berada di pulau langit", "The formation eye must stand on the sky island")
k("kultivator.sekte_langit.gerak.jaring.tiang", "Tiang Jaring berdiri %s dari %s", "Net Poles standing: %s of %s")
k("kultivator.sekte_langit.gerak.dermaga.tidak_ada",
  "Tak ada dermaga langit yang cocok untuk kapal ini: kapal akan menunggu di luar tepi pulau.",
  "No sky dock fits this ship: it will wait off the island's rim.")
k("kultivator.sekte_langit.gerak.dermaga.terisi", "Dermaga sudah terisi kapal lain: menunggu di luar tepi.",
  "The dock was taken by another ship: waiting off the rim.")
k("kultivator.sekte_langit.gerak.dermaga.menunggu", "Kapal menunggu di luar tepi pulau langit.", "The ship waits off the sky island's rim.")
k("kultivator.sekte_langit.gerak.tangga.mulai", "%s memulai Pendakian Pertama di Tangga Awan.", "%s begins the First Climb up the Cloud Stair.")
k("kultivator.sekte_langit.gerak.tangga.tiba", "%s tiba di Gerbang Akar, di atas awan.", "%s reaches the Root Gate, above the clouds.")
k("kultivator.sekte_langit.gerak.tangga.seribu",
  "Seribu anak tangga ditapaki tanpa terbang: %s menit %s detik. Langit mencatat ketekunanmu.",
  "A thousand steps walked without flying: %s min %s s. Heaven notes your perseverance.")
k("kultivator.sekte_langit.gerak.veto", "Murid yang sedang terbang berpatroli atau di Feri Awan tetap di tempatnya.",
  "Disciples on a sword patrol or on the Cloud Ferry stay where they are.")
k("kultivator.sekte_langit.feri.mati", "Feri Awan dimatikan di server ini.", "The Cloud Ferry is switched off on this server.")
k("kultivator.sekte_langit.feri.bukan_langit", "Tidak ada aula langit sektemu di sini.", "There is no sky hall of your sect here.")
k("kultivator.sekte_langit.feri.tingkat", "Halte Feri Awan dibangun pada tingkat %s pulau langit.", "The Cloud Ferry stop is built at sky island tier %s.")
k("kultivator.sekte_langit.feri.halte", "Halte Feri Awan belum lengkap (halte atas dan bawah).", "The Cloud Ferry stops are not complete (upper and lower).")
k("kultivator.sekte_langit.feri.server_sibuk", "Server sedang sibuk; Feri Awan menunggu sebentar.", "The server is busy; the Cloud Ferry waits a moment.")
k("kultivator.sekte_langit.feri.rute", "Feri Awan tidak menemukan jalur di sekitar pulau.", "The Cloud Ferry finds no route around the island.")
k("kultivator.sekte_langit.feri.muat", "Halte belum termuat; panggil lagi sebentar lagi.", "The stop is not loaded yet; call again in a moment.")
k("kultivator.sekte_langit.feri.jalan", "Feri Awan sedang dalam perjalanan.", "The Cloud Ferry is on its way.")
k("kultivator.sekte_langit.feri.terisi", "Feri Awan sedang dinaiki di halte seberang.", "The Cloud Ferry has riders at the other stop.")
k("kultivator.sekte_langit.feri.bukan_anggota", "Hanya anggota sekte dan murid yang boleh menaiki Feri Awan.",
  "Only sect members and disciples may ride the Cloud Ferry.")
k("kultivator.sekte_langit.feri.penuh", "Feri Awan penuh (%s kursi).", "The Cloud Ferry is full (%s seats).")
k("kultivator.sekte_langit.feri.sibuk", "Tidak bisa naik sekarang: %s", "Cannot board now: %s")
k("kultivator.sekte_langit.feri.naik", "Duduklah. Feri berangkat dalam %s detik.", "Take a seat. The ferry leaves in %s s.")
k("kultivator.sekte_langit.feri.lompat", "Kamu melompat dari Feri Awan: jatuh pelan, Jaring Awan menunggu di bawah.",
  "You jumped off the Cloud Ferry: a slow fall, the Cloud Net waits below.")
k("kultivator.sekte_langit.feri.tiba_atas", "Feri Awan merapat di halte pulau langit.", "The Cloud Ferry moors at the sky island's stop.")
k("kultivator.sekte_langit.feri.tiba_bawah", "Feri Awan turun di kaki Tangga Awan.", "The Cloud Ferry sets down at the foot of the Cloud Stair.")
k("kultivator.sekte_langit.feri.datang_bawah", "Lonceng berdentang: Feri Awan turun menjemputmu.", "The bell rings: the Cloud Ferry comes down for you.")
k("kultivator.sekte_langit.feri.datang_atas", "Lonceng berdentang: Feri Awan datang ke halte pulau.", "The bell rings: the Cloud Ferry comes to the island's stop.")

k("kultivator.sekte_langit.gerak.uji.jatuh", "Uji jatuh: kamu dilepas di %s %s %s, jatuh 1 blok per tick.", "Fall test: you were dropped at %s %s %s, falling 1 block a tick.")
k("kultivator.sekte_langit.gerak.uji.terjun_tidak", "Terjun Awan tidak bisa dimulai (Formasi Jaring Awan harus berjalan, kamu anggota dan tidak sibuk); Jaring Awan akan menangkap.", "The Cloud Dive cannot start (the Cloud Net Formation must run, you must be a member and not busy); the Cloud Net will catch you.")
k("kultivator.sekte_langit.gerak.uji.tanpa_murid", "Tak ada muridmu di dekat sini.", "None of your disciples is near.")
k("kultivator.sekte_langit.gerak.uji.jatuh_murid", "%s didorong keluar tepi pulau.", "%s was pushed off the island's rim.")
k("kultivator.sekte_langit.gerak.uji.feri", "Feri Awan dipanggil.", "The Cloud Ferry was called.")
k("kultivator.sekte_langit.gerak.uji.simpul", "Simpul awan %s di %s %s %s: %s, dalam jangkauan prasasti: %s", "Cloud node %s at %s %s %s: %s, within reach of the stele: %s")
k("kultivator.sekte_langit.gerak.uji.tautan", "  %s -> %s tertaut: %s", "  %s -> %s linked: %s")
k("kultivator.sekte_langit.gerak.uji.tanpa_simpul", "Belum ada simpul Formasi Pindah Awan.", "No Cloud Transfer node yet.")
k("kultivator.sekte_langit.gerak.uji.patroli", "Patroli pedang dikirim: %s murid.", "Sword patrol sent: %s disciples.")

assert list(idk) == list(en)
for name, d in (("id_id", idk), ("en_us", en)):
    with open(root + "/lang-src/sekte_langit_gerak.%s.json" % name, "w", encoding="utf-8", newline="\n") as f:
        f.write("{\n")
        items = list(d.items())
        for i, (kk, v) in enumerate(items):
            f.write("\t%s: %s%s\n" % (json.dumps(kk, ensure_ascii=False), json.dumps(v, ensure_ascii=False), "," if i < len(items) - 1 else ""))
        f.write("}\n")
print(len(idk))
