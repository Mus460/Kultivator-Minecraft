"""Writes lang-src/sekte_turnamen.id_id.json and .en_us.json (DESIGN 36.2.3, module T) with identical keys.

Indonesian is primary. Keys start with kultivator.sekte.turnamen. except the few whose format another system fixes:
the blueprint component name (kultivator.blueprint.comp.<id>), the hub card (kultivator.hub.card.<id>[.tip]), the
entity name and the advancement texts.

Run with: python tools/gen_sekte_turnamen_lang.py
"""
import json
import os

T = "kultivator.sekte.turnamen."

ENTRIES = [
    # key, Indonesian, English
    ("kultivator.blueprint.comp.sx_turnamen_tribun", "Tribun Lei Tai", "Lei Tai Tribune"),
    ("kultivator.hub.card.sekte_turnamen_papan", "Turnamen", "Tournament"),
    ("kultivator.hub.card.sekte_turnamen_papan.tip", "Papan musim (Elo), bagan yang sedang berjalan, daftar juara dan tantangan antar-sekte.",
     "The season board (Elo), the live bracket, the roll of champions and inter-sect challenges."),
    ("entity.kultivator.penonton", "Penonton", "Spectator"),
    ("advancement.kultivator.sekte.turnamen_juara.title", "Juara Lei Tai", "Champion of the Lei Tai"),
    ("advancement.kultivator.sekte.turnamen_juara.description", "Seorang muridmu menjuarai turnamen sekte di panggung lei tai.",
     "One of your disciples wins the sect tournament on the lei tai."),

    (T + "name.antar", "Turnamen Antar-Sekte", "Inter-Sect Tournament"),
    (T + "mulai_leitai", "Musim %s dibuka di panggung lei tai. Para murid naik ke tribun.", "Season %s opens on the lei tai. The disciples take the tribune."),
    (T + "bout.cancelled", "babak dihentikan", "the bout was stopped"),
    (T + "jenis.biasa", "Daftar Kuat", "Strong Ranking"),
    (T + "jenis.elit", "Turnamen Elit", "Elite Tournament"),
    (T + "jenis.antar_sekte", "Antar-Sekte", "Inter-Sect"),

    (T + "penonton.utusan", "Utusan %s", "Envoy of %s"),
    (T + "penonton.warga", "Warga %s", "Villager of %s"),
    (T + "penonton.sekte_jauh", "sekte jauh", "a distant sect"),
    (T + "penonton.salam_utusan", "%s memberi hormat: \"Pertandingan yang bagus, Ketua Sekte.\"", "%s bows: \"A fine contest, Sect Master.\""),
    (T + "penonton.salam_warga", "%s bersorak: \"Kami datang jauh-jauh untuk menonton!\"", "%s cheers: \"We came a long way to watch!\""),

    (T + "log.penonton", "Tribun penuh: %s murid, %s utusan, %s warga desa.", "The tribune fills: %s disciples, %s envoys, %s villagers."),
    (T + "log.juara", "Musim %s: %s naik sebagai juara lei tai.", "Season %s: %s stands as champion of the lei tai."),
    (T + "log.tantangan", "%s menantang sekte kita ke turnamen antar-sekte.", "%s challenges our sect to an inter-sect tournament."),
    (T + "log.tantangan_lewat", "Tantangan %s lewat tanpa jawaban.", "The challenge of %s lapsed unanswered."),
    (T + "log.antar_menang", "Kita mengalahkan %s di lei tai; mereka kini menghormati kita.", "We beat %s on the lei tai; they respect us now."),
    (T + "log.antar_kalah", "%s membawa pulang gelar dari lei tai kita.", "%s carried the title away from our lei tai."),

    (T + "antar.tantangan", "%s menantang sektemu ke turnamen antar-sekte di lei tai!", "%s challenges your sect to an inter-sect tournament on the lei tai!"),
    (T + "antar.terima", "[Terima]", "[Accept]"),
    (T + "antar.tolak", "[Tolak]", "[Refuse]"),
    (T + "antar.tidak_ada", "Tidak ada tantangan yang menunggu.", "No challenge is waiting."),
    (T + "antar.ditolak", "Tantangan %s ditolak. Mereka sedikit tersinggung.", "You refused %s. They are a little offended."),

    (T + "ingat.kejutan", "Mengalahkan %s yang ranahnya lebih tinggi di lei tai.", "Beat %s, of a higher realm, on the lei tai."),
    (T + "ingat.dikejutkan", "Dikalahkan %s yang ranahnya lebih rendah di lei tai.", "Lost to %s, of a lower realm, on the lei tai."),
    (T + "ingat.juara", "Juara lei tai musim %s.", "Champion of the lei tai, season %s."),
    (T + "ingat.final", "Kalah di final yang terhormat melawan %s.", "Lost an honourable final to %s."),

    (T + "papan.judul", "Papan Turnamen — Musim %s", "Tournament Board — Season %s"),
    (T + "papan.kosong", "Belum ada murid yang bertanding.", "No disciple has fought yet."),
    (T + "papan.baris", "  Elo %s · %s menang / %s kalah", "  Elo %s · %s won / %s lost"),
    (T + "papan.juara", "Daftar Juara", "Roll of Champions"),
    (T + "papan.juara_baris", "Musim %s · %s · %s", "Season %s · %s · %s"),
    (T + "papan.antar", "Antar-sekte: %s menang, %s kalah", "Inter-sect: %s won, %s lost"),
    (T + "papan.menunggu", "Tantangan menunggu jawaban: %s", "A challenge awaits your answer: %s"),

    (T + "ui.judul", "Turnamen Sekte", "Sect Tournament"),
    (T + "ui.judul_musim", "Turnamen Sekte — Musim %s", "Sect Tournament — Season %s"),
    (T + "ui.memuat", "Membuka gulungan", "Unrolling the scroll"),
    (T + "ui.papan", "Papan Musim (Elo)", "Season Board (Elo)"),
    (T + "ui.elo_baris", "%s · %s-%s", "%s · %s-%s"),
    (T + "ui.berjalan", "Sedang berjalan: %s", "Now running: %s"),
    (T + "ui.sekarang", "Di panggung: %s melawan %s", "On the stage: %s against %s"),
    (T + "ui.perunggu", "Tempat ketiga:", "Third place:"),
    (T + "ui.berikutnya", "Turnamen Berikutnya", "Next Tournament"),
    (T + "ui.hari", "Hari ke-%s, tengah hari.", "Day %s, at noon."),
    (T + "ui.hari_elit", "Hari ke-%s, tengah hari — Turnamen Elit!", "Day %s, at noon — an Elite Tournament!"),
    (T + "ui.sepanjang_masa", "Sepanjang Masa", "All Time"),
    (T + "ui.sepanjang_baris", "%s. %s · %s poin · %s kali juara", "%s. %s · %s points · %s titles"),
    (T + "ui.tribun_ada", "Tribun lei tai berdiri.", "The lei tai tribune stands."),
    (T + "ui.tribun_belum", "Tribun belum dibangun (templat 6-9, tingkat 7).", "No tribune yet (templates 6-9, tier 7)."),
    (T + "ui.tantangan", "Tantangan dari %s!", "A challenge from %s!"),

    (T + "hud.judul", "Musim %s · Babak %s", "Season %s · Bout %s"),
    (T + "hud.final", "Musim %s · Babak Final", "Season %s · The Final"),
    (T + "hud.lawan", "%s ⚔ %s", "%s ⚔ %s"),
    (T + "hud.hasil", "%s mengalahkan %s", "%s beat %s"),
    (T + "hud.juara", "Juara: %s", "Champion: %s"),
    (T + "hud.tunggu", "Petarung berikutnya bersiap…", "The next fighters get ready…"),

    (T + "adegan.final", "Babak Final — Musim %s", "The Final — Season %s"),
    (T + "adegan.juara", "%s", "%s"),
    (T + "adegan.juara.sub", "Juara Lei Tai — Nomor Satu Daftar Kuat", "Champion of the Lei Tai — First of the Strong Ranking"),
    (T + "adegan.tamu", "Gelar musim %s dibawa pergi tamu", "A visitor carries away the title of season %s"),

    (T + "uji.mulai", "Turnamen dimulai (%s).", "Tournament started (%s)."),
    (T + "uji.lewati", "Babak ini diputuskan menurut kekuatan.", "This bout is decided by strength."),
    (T + "uji.tidak_jalan", "Tidak ada turnamen yang berjalan.", "No tournament is running."),
    (T + "uji.penonton", "%s penonton ditambah (sekarang %s).", "%s spectators added (now %s)."),
    (T + "uji.elo", "Elo %s sekarang %s.", "%s's Elo is now %s."),
    (T + "uji.tak_ketemu", "Tidak ada murid termuat bernama \"%s\".", "No loaded disciple named \"%s\"."),
    (T + "uji.reset", "Musim, papan dan Elo murid termuat dihapus.", "Season, board and the loaded disciples' Elo cleared."),
    (T + "uji.tribun", "Templat %s · pusat lei tai %s · kursi %s · tiang %s · tribun %s",
     "Template %s · lei tai centre %s · seats %s · poles %s · tribune %s"),

    (T + "sumber", "Sumber: Battle Through the Heavens bab 566-573 (Daftar Kuat); A Will Eternal (pertarungan Murid Terpilih); "
                   "I Shall Seal the Heavens bab 27-31 (final diumumkan Tetua Agung); perjanjian tiga tahun Xiao Yan dan Sekte Awan "
                   "Berkabut (Battle Through the Heavens); panggung lei tai (擂台) dalam tradisi bela diri Tiongkok; peringkat Elo "
                   "(Arpad Elo, catur).",
     "Sources: Battle Through the Heavens ch. 566-573 (the Strong Ranking); A Will Eternal (the Chosen battles); I Shall Seal "
     "the Heavens ch. 27-31 (the final announced by the Grand Elder); Xiao Yan's three-year agreement with the Misty Cloud Sect "
     "(Battle Through the Heavens); the lei tai (擂台) stage of Chinese martial contests; the Elo rating (Arpad Elo, chess)."),
]


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "lang-src")
    keys = [k for k, _, _ in ENTRIES]
    assert len(keys) == len(set(keys)), "duplicate keys"
    for code, index in (("id_id", 1), ("en_us", 2)):
        data = {entry[0]: entry[index] for entry in ENTRIES}
        path = os.path.join(root, "sekte_turnamen." + code + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("wrote", len(ENTRIES), "keys")


if __name__ == "__main__":
    main()
