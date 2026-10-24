"""Kota Air story data (DESIGN 38.T.5; written by 38TK-A, moved into the repository by D-TK1 so the data and the lang
fragment are reproducible): dialogs, quests, rumours, the schedule kota_tema_air.json, the demand kota_tema_air.json,
advancements tema_air_*, the system lines of the Java code (kultivator.kota_tema.air.*), the couplets of Pasangan Bait,
and the lang fragment pair tempat_tema_air.{id_id,en_us} (identical keys). Checks that every key the Java code uses
exists and that no key clashes with another fragment.

Run: python -I tools/gen_tempat_tema_air_cerita.py [repo]   (deterministic; LF output)
"""
import json
import os
import re
import sys

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(REPO, "src/main/resources/data/kultivator")
L = {}


def t(key, idn, en):
    if key in L and L[key] != (idn, en):
        raise SystemExit("duplicate key " + key)
    L[key] = (idn, en)


def tulis(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ====================================================================================================== dialogs

DIALOGS = []


def dialog(did, pembicara, prioritas, syarat, simpul, sekali=False):
    """simpul: list of (id, (idn, en) or "kabar", opts) where opts may hold gestur, prop, akibat, pilihan.
    pilihan: list of dicts with "teks": (idn, en) plus syarat/akibat/ke/tampil/alasan:(idn, en)."""
    nodes = []
    for sid, teks, opts in simpul:
        n = {"id": sid}
        if teks == "kabar":
            n["teks"] = "kabar"
        else:
            t("kultivator.cerita.%s.%s" % (did, sid), teks[0], teks[1])
        for k in ("gestur", "prop", "akibat"):
            if k in opts:
                n[k] = opts[k]
        if "pilihan" in opts:
            ps = []
            for i, p in enumerate(opts["pilihan"], 1):
                q = {}
                t("kultivator.cerita.%s.%s.%d" % (did, sid, i), p["teks"][0], p["teks"][1])
                for k in ("syarat", "akibat", "ke"):
                    if k in p:
                        q[k] = p[k]
                if "alasan" in p:
                    key = "kultivator.cerita.%s.%s.%d.alasan" % (did, sid, i)
                    t(key, p["alasan"][0], p["alasan"][1])
                    q["tampil"] = True
                    q["alasan"] = key
                ps.append(q)
            n["pilihan"] = ps
        nodes.append(n)
    obj = {"pembicara": pembicara if isinstance(pembicara, list) else [pembicara], "prioritas": prioritas}
    if syarat:
        obj["syarat"] = syarat
    if sekali:
        obj["sekali"] = True
    obj["mulai"] = simpul[0][0]
    obj["simpul"] = nodes
    DIALOGS.append((did, obj))


def P(idn, en, **kw):
    d = {"teks": (idn, en)}
    d.update(kw)
    return d


Q = "tema_air_benang"
SELESAI = "pilihan"
PAMIT = P("Permisi.", "Excuse me.")

# ------------------------------------------------------------------------------------------- Nyonya Su Jin
dialog("tema_air_su_jin_sapa", "tokoh:su_jin", 10, [], [
    ("mulai", ("Selamat datang di Rumah Sutra Su. Hati-hati dengan lengan bajumu: benang di sini lebih mahal daripada perak.",
               "Welcome to the House of Su Silk. Mind your sleeves: thread here costs more than silver."),
     {"gestur": "hormat", "pilihan": [
         P("Sudah berapa lama rumah ini menenun?", "How long has this house been weaving?", ke="usaha"),
         P("Ceritakan tentang kota ini.", "Tell me about this town.", ke="kota"),
         P("Ada kabar apa di kanal?", "Any news on the canals?", ke="kabar"),
         PAMIT]}),
    ("usaha", ("Lima generasi. Nenek buyutku memelihara ulat di nampan bambu di loteng; aku menulis surat untuk pedagang dari "
               "tiga kota pelabuhan. Yang tidak berubah hanya satu: sehelai benang yang putus tidak pernah disambung diam-diam.",
               "Five generations. My great-grandmother kept silkworms in bamboo trays in the attic; I write letters to merchants "
               "of three harbour towns. Only one thing never changed: a broken thread is never knotted in secret."),
     {"gestur": "hitung_sempoa", "prop": "sempoa", "pilihan": [
         P("Dan ke mana sutra itu dijual?", "And where does the silk go?", ke="dagang"), PAMIT]}),
    ("dagang", ("Sutra Awan laku paling mahal di ibu kota dan di kota gurun, di sini harganya paling murah karena kamilah yang "
                "menenunnya. Pedagang yang cerdas membeli di sini dan menjualnya jauh-jauh. Buku Dagangmu akan bercerita sisanya.",
                "Cloud Silk fetches the best price in the capital and the desert towns; here it is cheapest, because we weave it. "
                "A clever trader buys here and sells far away. Your Trade Book will tell you the rest."),
     {"gestur": "bicara_tangan"}),
    ("kota", ("Orang menyebutnya kota seribu jembatan. Hitunglah sendiri kalau kau sabar: enam puluh empat. Setiap rumah punya pintu "
              "depan ke jalan dan pintu belakang ke air. Kekayaan kami datang dari dua hal: sutra di selatan dan teh dari bukit di utara.",
              "People call it the town of a thousand bridges. Count them yourself if you are patient: sixty-four. Every house has "
              "a front door to the street and a back door to the water. Our wealth comes from two things: silk, and tea from the northern hill."),
     {"gestur": "tunjuk", "pilihan": [
         P("Kudengar ada legenda tentang seorang penenun.", "I heard there is a legend about a weaver.", ke="legenda"), PAMIT]}),
    ("legenda", ("Xiu Niang. Penenun terbaik yang pernah duduk di alat tenun rumah ini, lima puluh tahun lalu. Ia tenggelam di malam "
                 "pernikahannya dan kain pengantinnya tak pernah selesai. Ibuku bilang, pada malam tanpa bulan ada satu lentera yang "
                 "berlayar melawan arus. Aku tidak percaya cerita hantu. Tetapi aku juga tidak pernah menjual alat tenun merah itu.",
                 "Xiu Niang. The finest weaver who ever sat at this house's looms, fifty years ago. She drowned on her wedding night and "
                 "her bridal cloth was never finished. My mother said that on moonless nights one lantern sails against the current. "
                 "I do not believe ghost stories. But I never sold that red loom either."),
     {"gestur": "sikap_tangan_belakang"}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_su_jin_ulat", "tokoh:su_jin", 40, ["quest %s baru" % Q], [
    ("mulai", ("Kau orang luar, bukan? Bagus. Orang kota sudah terlalu banyak bicara. Ulat-ulatku sakit: mereka berhenti makan, "
               "tubuhnya memucat, daun di nampan menghitam dari tepinya. Separuh nampan sudah mati dalam tiga hari.",
               "You are from outside, are you not? Good. The townsfolk already talk too much. My silkworms are sick: they stop "
               "eating, their bodies go pale, the leaves in the trays blacken from the edges. Half the trays died in three days."),
     {"gestur": "sikap_lipat_tangan", "pilihan": [
         P("Aku akan mencari tahu penyebabnya.", "I will find out what causes it.", akibat=["quest_mulai %s" % Q], ke="terima"),
         P("Mungkin hanya penyakit ulat biasa?", "Maybe it is just an ordinary silkworm sickness?", ke="biasa"),
         P("Maaf, aku tidak bisa sekarang.", "Sorry, I cannot right now.")]}),
    ("biasa", ("Penyakit biasa membunuh satu nampan, bukan dua puluh. Dan penyakit biasa tidak membuat daun yang baru dipetik pagi "
               "ini menghitam sebelum siang. Ada yang salah dengan daunnya, atau dengan airnya.",
               "An ordinary sickness kills one tray, not twenty. And an ordinary sickness does not blacken leaves picked this very "
               "morning before noon. Something is wrong with the leaves, or with the water."),
     {"gestur": "marah_tunjuk", "pilihan": [
         P("Baik, akan kuperiksa.", "Fine, I will look into it.", akibat=["quest_mulai %s" % Q], ke="terima"),
         P("Lain kali.", "Another time.")]}),
    ("terima", ("Periksa daun murbei di kebun di belakang rumah ini: klik daunnya di pohon. Lalu isi sebotol air dari kanal di depan "
                "pintu air kami. Pemelihara ulat mencuci nampan dengan air itu setiap pagi.",
                "Examine the mulberry leaves in the orchard behind this house: click the leaves on a tree. Then fill a bottle from "
                "the canal in front of our water door. The silkworm keepers wash the trays with that water every morning."),
     {"gestur": "tunjuk"}),
])

dialog("tema_air_su_jin_lapor", "tokoh:su_jin", 45, ["quest %s tahap noda" % Q], [
    ("mulai", ("Daunnya bersih, airnya berminyak... dan baunya seperti lampu minyak murahan. Tidak ada rumah di hulu yang membuang "
               "minyak ke kanal: denda Kepala Pintu Air terlalu mahal. Seseorang melakukannya diam-diam, di malam hari.",
               "The leaves are clean, the water oily... and it smells like a cheap oil lamp. No house upstream dumps oil into the "
               "canal: the Water Gate Warden's fines are too steep. Someone does it in secret, at night."),
     {"gestur": "sikap_lipat_tangan", "pilihan": [
         P("Siapa yang tahu kanal di malam hari?", "Who knows the canals at night?", ke="chuan")]}),
    ("chuan", ("Bibi Chuan. Ia mendayung di kanal ini sejak sebelum aku lahir dan menyanyi di setiap jembatan. Ia menyewakan perahu "
               "di Galangan Perahu. Mintalah tur malam: kalau ada noda di air kita, matanya yang pertama melihat.",
               "Auntie Chuan. She has rowed these canals since before I was born and sings under every bridge. She hires out boats "
               "at the Boatyard. Ask her for a night tour: if there is a stain on our water, her eyes will see it first."),
     {"gestur": "tunjuk", "akibat": ["nama_baik 2"]}),
], sekali=True)

dialog("tema_air_su_jin_kain", "tokoh:su_jin", 45, ["quest %s tahap kain" % Q], [
    ("mulai", ("Wen Shi sudah menceritakan semuanya padaku. Xiu Niang... kain pengantinnya masih tergulung di peti di atas alat tenun "
               "merah. Setengahnya. Ibuku menyimpannya karena tak sanggup membakarnya.",
               "Wen Shi told me everything. Xiu Niang... her bridal cloth is still rolled in the chest above the red loom. Half of it. "
               "My mother kept it because she could not bring herself to burn it."),
     {"gestur": "sikap_tunduk", "pilihan": [
         P("Bisakah kain itu diselesaikan?", "Can the cloth be finished?", ke="bahan")]}),
    ("bahan", ("Bisa, kalau benangnya benar. Enam gulung Sutra Awan untuk badannya. Sepuluh helai Daun Teh Embun dari teras teh untuk "
               "pewarna hijau sungai. Dan satu botol Air Suci untuk merendam benang merahnya: biasanya dari kuil di kota oasis, tetapi "
               "kolam di Kuil Dewa Air di tepi danau kita juga diberkati, sekali untuk setiap orang yang tulus. Bawa semuanya ke Jin'er.",
               "It can, if the thread is right. Six rolls of Cloud Silk for the body. Ten Dew Tea Leaves from the tea terraces for the "
               "river-green dye. And one bottle of Holy Water to steep the red thread: usually from the shrine in the oasis towns, but "
               "the basin of the Water God's shrine by our lake is blessed too, once for every sincere soul. Bring it all to Jin'er."),
     {"gestur": "bicara_tangan", "pilihan": [
         P("Mengapa Jin'er?", "Why Jin'er?", ke="jiner")]}),
    ("jiner", ("Karena putriku menenun seperti Xiu Niang: terlalu cepat, terlalu berani, dan tidak pernah mendengarkan ibunya. Alat "
               "tenun merah itu hanya mau bergerak untuk tangan seperti itu.",
               "Because my daughter weaves like Xiu Niang: too fast, too bold, and never listening to her mother. That red loom only "
               "moves for hands like hers."),
     {"gestur": "tertawa"}),
], sekali=True)

dialog("tema_air_su_jin_bingkisan", "tokoh:su_jin", 35, ["air_misi antar_sutra", "!air_bingkisan"], [
    ("mulai", ("Kau dari Papan Gilda? Tepat waktu. Bingkisan ini berisi serbet sutra untuk Paviliun Teh Awan: Lu Yuqing tidak mau "
               "menuang teh di atas kain lain. Jangan sampai basah, dan jangan dibuka.",
               "From the Guild Board? Right on time. This parcel holds silk napkins for the Cloud Tea Pavilion: Lu Yuqing will not pour "
               "tea over any other cloth. Do not get it wet, and do not open it."),
     {"gestur": "angkat_bawa", "akibat": ["air_bingkisan"]}),
])

dialog("tema_air_su_jin_terima_kasih", "tokoh:su_jin", 20, ["quest %s selesai" % Q], [
    ("mulai", ("Ulat-ulatku makan lagi. Kau dengar suaranya? Seperti hujan halus di atas genteng. Rumah ini berutang padamu, Penenun "
               "Malam: harga untukmu selalu lebih ringan di sini.",
               "My silkworms are eating again. Do you hear it? Like fine rain on the roof tiles. This house is in your debt, Night "
               "Weaver: your price here will always be lighter."),
     {"gestur": "hormat_dalam", "pilihan": [
         # D-TK1 D4: what Madam Su says depends on how the cloth was released
         P("Bagaimana Jin'er?", "How is Jin'er?", ke="jiner"),
         P("Apakah Anda percaya cerita hantu sekarang?", "Do you believe in ghost stories now?",
           syarat=["quest %s hasil tenang" % Q], ke="hantu"),
         P("Bagaimana dengan alat tenun merah itu?", "What about the red loom?", ke="merah"),
         P("Apakah Xiu Niang sempat melihat kainnya?", "Did Xiu Niang ever see her cloth?", syarat=["quest %s hasil sepi" % Q],
           ke="sepi"),
         P("Ada kabar di kanal?", "Any news on the canals?", ke="kabar"),
         PAMIT]}),
    ("hantu", ("Aku melihat cahaya merah itu dari jendela loteng, berlayar ke tengah danau lalu naik seperti asap dupa. Aku tidak "
               "percaya cerita hantu. Aku percaya pada janji yang ditenun sampai selesai. Malam itu keduanya kebetulan sama.",
               "I saw that red light from the attic window, sailing to the middle of the lake and rising like incense smoke. I do "
               "not believe ghost stories. I believe in promises woven to the end. That night the two happened to be the same."),
     {"gestur": "tatap_langit"}),
    ("merah", ("Aku memindahkannya ke aula depan, di tempat semua pembeli bisa melihatnya. Tidak ada yang menenun di atasnya kecuali "
               "Jin'er, setahun sekali, pada malam bulan mati. Ia bilang benangnya tidak lagi menarik tangannya. Sekarang ia yang "
               "menarik benangnya.",
               "I moved it to the front hall, where every buyer can see it. Nobody weaves on it except Jin'er, once a year, on the "
               "new-moon night. She says the thread no longer pulls her hand. Now she pulls the thread."),
     {"gestur": "tunjuk"}),
    ("sepi", ("Tidak ada yang tahu. Kain itu tenggelam tanpa suara. Ulat-ulatku sembuh, airnya bersih, dan aku berterima kasih. Tetapi "
              "setiap malam bulan mati aku menyalakan satu lentera di dermaga, untuk berjaga-jaga kalau ia masih mencari jalan pulang.",
              "No one knows. The cloth sank without a sound. My silkworms recovered, the water is clean, and I am grateful. But "
              "every new-moon night I light one lantern on the jetty, in case she is still looking for the way home."),
     {"gestur": "sikap_tangan_belakang"}),
    ("kabar", "kabar", {"gestur": "bisik"}),
    ("jiner", ("Ia menenun motif sungai merah di setiap kain sekarang, dan pembeli dari ibu kota memintanya secara khusus. Jangan "
               "katakan padanya aku bangga. Ia akan semakin tidak mendengarkan ibunya.",
               "She weaves the red river pattern into every cloth now, and buyers from the capital ask for it by name. Do not tell her "
               "I am proud. She would listen to her mother even less."),
     {"gestur": "tertawa"}),
])

# ------------------------------------------------------------------------------------------- Su Jin'er
dialog("tema_air_jiner_sapa", "tokoh:su_jiner", 10, [], [
    ("mulai", ("Ssst, jangan bilang Ibu aku sedang istirahat. Kau mau belajar menenun? Iramanya seperti mendayung: tarik, injak, "
               "lempar, rapatkan. Kalau benangnya bernyanyi, berarti kau benar.",
               "Shh, do not tell Mother I am taking a break. Want to learn weaving? The rhythm is like rowing: pull, tread, throw, "
               "beat. If the thread sings, you are doing it right."),
     {"gestur": "tertawa", "pilihan": [
         P("Ceritakan tentang alat tenun merah itu.", "Tell me about that red loom.", syarat=["!quest %s selesai" % Q], ke="merah"),
         P("Apakah alat tenun merah masih bergerak sendiri?", "Does the red loom still move on its own?",
           syarat=["quest %s hasil tenang" % Q], ke="pamit"),
         P("Apakah alat tenun merah masih bergerak sendiri?", "Does the red loom still move on its own?",
           syarat=["quest %s hasil sepi" % Q], ke="diam"),
         P("Motif apa yang sedang kau tenun?", "What pattern are you weaving?", syarat=["quest %s selesai" % Q], ke="motif"),
         P("Apa yang dibicarakan para penenun?", "What are the weavers talking about?", ke="kabar"),
         PAMIT]}),
    ("pamit", ("Sekali lagi saja, malam setelah kain itu berlayar. Pedalnya turun pelan, satu kali, seperti orang yang mengangguk "
               "sebelum pergi. Sejak itu diam. Aku menangis sedikit. Jangan bilang Ibu.",
               "Just once more, the night after the cloth sailed. The pedal went down slowly, one time, like someone nodding before "
               "leaving. It has been still ever since. I cried a little. Do not tell Mother."),
     {"gestur": "tatap_langit"}),
    ("diam", ("Tidak pernah lagi. Aku duduk di sana setiap malam bulan mati dan menunggu pedalnya bergerak. Kadang kurasa benangnya "
              "bergetar, kadang kurasa itu hanya angin dari kanal. Mungkin ia masih marah karena kita mengusirnya.",
              "Never again. I sit there every new-moon night and wait for the pedal to move. Sometimes I think the thread trembles, "
              "sometimes I think it is only the wind off the canal. Perhaps she is still angry that we drove her away."),
     {"gestur": "sikap_tangan_belakang"}),
    ("motif", ("Sungai merah yang mengalir ke bulan. Motif Xiu Niang. Pembeli dari ibu kota memintanya dengan nama itu sekarang, dan "
               "Ibu pura-pura kesal karena harganya naik. Aku menenun satu benang emas di setiap kain, di tempat yang tidak terlihat. "
               "Itu untuknya.",
               "A red river flowing into the moon. Xiu Niang's pattern. Buyers from the capital ask for it by that name now, and "
               "Mother pretends to be annoyed that the price went up. I weave one gold thread into every cloth, where no one can see "
               "it. That one is for her."),
     {"gestur": "tenun"}),
    ("merah", ("Itu alat tenun Xiu Niang. Tidak ada yang boleh duduk di sana, katanya membawa sial. Aku pernah duduk di sana waktu "
               "kecil. Pedal-pedalnya bergerak sendiri sebelum kakiku menyentuhnya. Aku tidak takut. Rasanya seperti ada yang ingin "
               "mengajariku.",
               "That is Xiu Niang's loom. Nobody may sit there, they say it brings bad luck. I sat there once as a child. The pedals "
               "moved before my feet touched them. I was not afraid. It felt like someone wanted to teach me."),
     {"gestur": "tatap_langit"}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_jiner_kain", "tokoh:su_jiner", 45, ["quest %s tahap kain" % Q], [
    ("mulai", ("Ibu bilang kita akan menyelesaikan kain Xiu Niang? Akhirnya! Aku sudah menghafal polanya dari setengah yang tersisa: "
               "sungai merah yang mengalir ke bulan. Tiga babak: dasar, sungai, lalu bulannya.",
               "Mother says we will finish Xiu Niang's cloth? At last! I learned the pattern by heart from the half that remains: a red "
               "river flowing into the moon. Three passes: the ground, the river, then the moon."),
     {"gestur": "tepuk_tangan", "pilihan": [
         P("Apa yang harus kulakukan?", "What do I do?", ke="cara")]}),
    ("cara", ("Bawa enam Sutra Awan, sepuluh Daun Teh Embun dan satu Air Suci, lalu klik alat tenun merah di aula tenun. Ikuti "
              "iramaku. Kalau benangmu kusut, kita ulang babaknya, bahan tidak terbuang sampai kain jadi.",
              "Bring six Cloud Silk, ten Dew Tea Leaves and one Holy Water, then click the red loom in the weaving hall. Follow my "
              "rhythm. If your thread tangles we repeat the pass; nothing is used up until the cloth is done."),
     {"gestur": "tenun"}),
], sekali=True)

dialog("tema_air_jiner_lepas", "tokoh:su_jiner", 45, ["quest %s tahap lepas" % Q], [
    ("mulai", ("Kainnya cantik sekali, aku hampir tidak rela. Tapi itu bukan milik kita. Pada Malam Lentera Air berikutnya, bawa ke "
               "dermaga lentera di danau dan lepaskan seperti lentera. Biarkan airnya membawanya pulang.",
               "The cloth is so beautiful I almost cannot let it go. But it is not ours. On the next Night of Water Lanterns take it to "
               "a lantern jetty on the lake and release it like a lantern. Let the water carry it home."),
     {"gestur": "sikap_doa"}),
])

# ------------------------------------------------------------------------------------------- Lu Yuqing
dialog("tema_air_lu_sapa", "tokoh:lu_yuqing", 10, [], [
    ("mulai", ("Duduklah. Air untuk teh tidak boleh mendidih bergolak, cukup seperti mata kepiting: gelembung kecil yang naik "
               "pelan. Hal yang sama berlaku untuk percakapan.",
               "Sit. Water for tea must not boil wildly, only like crab eyes: small bubbles rising slowly. The same is true of "
               "conversation."),
     {"gestur": "tuang_teh", "prop": "teko", "pilihan": [
         P("Teh apa yang tumbuh di bukit utara?", "What tea grows on the northern hill?", ke="teh"),
         P("Aku membawa daun teh segar untuk dijual.", "I brought fresh tea leaves to sell.", akibat=["air_jual_daun"]),
         P("Bisakah kau mengajariku memetik teh?", "Could you teach me to pick tea?", syarat=["quest tema_air_teh_pertama baru"],
           ke="petik"),
         P("Untuk siapa cawan kedua yang kau tuang setiap senja?", "Who is the second cup you pour every dusk for?",
           syarat=["bendera air_wen_dengar"], ke="cawan"),
         P("Apa yang orang bicarakan di paviliun?", "What do people talk about in the pavilion?", ke="kabar"),
         PAMIT]}),
    ("cawan", ("Guruku, Tuan Lu Mingyuan, memungutku dari tangga cuci waktu aku masih bayi dan memberiku namanya. Lima puluh tahun "
               "lalu ia menunggu di rumahnya dengan baju pengantin, menunggu perahu yang tidak pernah tiba. Ia tidak pernah menikah. "
               "Sebelum wafat ia memintaku menuang cawan kedua setiap senja, untuk seseorang yang masih dalam perjalanan.",
               "My master, Lord Lu Mingyuan, picked me up from the washing steps when I was a baby and gave me his name. Fifty years "
               "ago he waited at his house in wedding clothes for a boat that never arrived. He never married. Before he died he "
               "asked me to pour a second cup every dusk, for someone still on her way."),
     {"gestur": "tuang_teh", "prop": "teko", "pilihan": [
         P("Dan sekarang?", "And now?", syarat=["quest %s hasil tenang" % Q], ke="kosong"),
         P("Dan sekarang?", "And now?", syarat=["quest %s hasil sepi" % Q], ke="masih"),
         P("Aku turut berduka.", "I am sorry.")]}),
    ("kosong", ("Pada malam kain itu berlayar, aku menuang cawan kedua seperti biasa. Paginya cawan itu kosong, dan tidak ada semut, "
                "tidak ada kucing, tidak ada angin. Aku masih menuangnya. Tetapi sekarang aku menuang untuk dua orang yang akhirnya "
                "bertemu.",
                "On the night the cloth sailed I poured the second cup as always. In the morning it was empty, and there were no ants, "
                "no cat, no wind. I still pour it. But now I pour for two people who finally met."),
     {"gestur": "seduh_teh", "prop": "cangkir"}),
    ("masih", ("Teh di cawan kedua selalu dingin ketika pagi tiba, dan masih penuh. Aku akan terus menuangnya. Guruku menunggu lima "
               "puluh tahun; aku bisa menunggu sedikit lebih lama.",
               "The tea in the second cup is always cold by morning, and still full. I will keep pouring it. My master waited fifty "
               "years; I can wait a little longer."),
     {"gestur": "sikap_tangan_belakang"}),
    ("petik", ("Teh yang kau petik dengan tanganmu sendiri rasanya berbeda. Bukan lebih enak. Lebih jujur. Datanglah ke teras teh di "
               "musim semi, saat fajar, pukul empat sampai delapan pagi. Petik sepuluh pucuk dari semak azalea teh: hanya pucuk dan "
               "dua daun di bawahnya. Lalu bawa ke sini.",
               "Tea you pick with your own hands tastes different. Not better. More honest. Come to the tea terraces in spring, at "
               "dawn, from four to eight in the morning. Pick ten shoots from the tea azaleas: only the bud and the two leaves below "
               "it. Then bring them here."),
     {"gestur": "petik_teh", "prop": "keranjang_teh", "pilihan": [
         P("Aku akan kembali dengan daunnya.", "I will come back with the leaves.", akibat=["quest_mulai tema_air_teh_pertama"]),
         P("Mungkin musim semi nanti.", "Perhaps next spring.")]}),
    ("teh", ("Bi Luo, 'siput hijau', karena daunnya menggulung seperti cangkang siput dan harumnya membuat orang terkejut. Dipetik "
             "di musim semi sebelum embun kering. Untuk yang bermeditasi, secangkir Bi Luo membuat pikiran sejernih danau tanpa angin.",
             "Bi Luo, the 'green snail', because the leaf curls like a snail shell and its scent startles people. Picked in spring "
             "before the dew dries. For one who meditates, a cup of Bi Luo makes the mind as clear as a windless lake."),
     {"gestur": "seduh_teh", "prop": "cangkir"}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_lu_minyak", "tokoh:lu_yuqing", 45, ["bendera air_minyak_dengar"], [
    ("mulai", ("Noda hitam yang tidak memantulkan cahaya lentera? Tunjukkan botolmu. ...Ya. Minyak Bayangan. Diperas dari akar pohon "
               "yang tumbuh di tanah iblis. Para penyelundup memakainya untuk melapisi peti agar tidak tercium anjing penjaga pintu air.",
               "A black stain that reflects no lantern light? Show me your bottle. ...Yes. Shadow Oil. Pressed from the roots of trees "
               "that grow on demonic soil. Smugglers coat their crates with it so the water-gate dogs cannot smell them."),
     {"gestur": "sikap_lipat_tangan", "pilihan": [
         P("Kau tahu banyak untuk seorang ahli teh.", "You know a lot for a tea master.", ke="rahasia"),
         P("Penyelundupan yang sama terjadi di kota pelabuhan.", "The same smuggling happens in the harbour towns.",
           syarat=["quest tema_pelabuhan_pelita aktif"], ke="pelabuhan"),
         P("Penyelundupan yang sama memadamkan pelita di kota pelabuhan.", "The same smuggling put out the beacon in the harbour towns.",
           syarat=["quest tema_pelabuhan_pelita selesai"], ke="pelabuhan"),
         P("Siapa yang menyelundupkannya di sini?", "Who smuggles it here?", ke="tikus")]}),
    ("rahasia", ("Teh dan rahasia sama-sama disimpan dalam toples tertutup. Tamuku pedagang, pejabat, kadang penyelundup yang ingin "
                 "terlihat terhormat. Mereka bicara di atas cangkir kedua. Aku hanya menuang.",
                 "Tea and secrets are both kept in sealed jars. My guests are merchants, officials, sometimes smugglers who wish to "
                 "look respectable. They talk over the second cup. I only pour."),
     {"gestur": "tuang_teh", "prop": "teko", "pilihan": [
         P("Lalu siapa penyelundupnya?", "Then who is the smuggler?", ke="tikus")]}),
    ("pelabuhan", ("Jadi kau sudah melihatnya di sana juga. Kalau begitu ini bukan ulah satu geng kanal. Ada yang membuka jalur dari "
                   "laut sampai ke kanal-kanal kita, dan jalur itu berawal di tempat yang jauh lebih gelap daripada pelabuhan.",
                   "So you have seen it there too. Then this is not one canal gang's doing. Someone opened a route from the sea to our "
                   "canals, and that route begins somewhere far darker than any harbour."),
     {"gestur": "bisik", "pilihan": [
         P("Siapa yang menyelundupkannya di sini?", "Who smuggles it here?", ke="tikus")]}),
    ("tikus", ("Tikus Air. Pencuri kanal yang dipimpin Lao Shu si Tikus Tua: mereka merampok perahu-lapak di malam hari dan kabur "
               "dengan sampan hitam. Kalau ada yang mengangkut minyak itu di kanal kita, pasti mereka. Berjagalah malam ini di dekat "
               "Pasar Air Pagi atau Rumah Sutra. Tikus selalu kembali ke lumbung yang sama.",
               "The Water Rats. Canal thieves led by Lao Shu the Old Rat: they rob the stall boats at night and flee in a black skiff. "
               "If anyone carries that oil on our canals, it is them. Keep watch tonight near the Morning Water Market or the House "
               "of Su. Rats always return to the same granary."),
     {"gestur": "tunjuk"}),
], sekali=True)

dialog("tema_air_lu_teh_selesai", "tokoh:lu_yuqing", 40, ["quest tema_air_teh_pertama selesai"], [
    ("mulai", ("Lihat: kau memetiknya terlalu panjang di dua pucuk, dan satu daun memar oleh kukumu. Tidak apa-apa. Aku sudah "
               "menyangrainya untukmu. Mulai hari ini kau boleh menyeduh sendiri di meja paviliun: Bi Luo untukmu lebih murah "
               "seperempat. Teh yang jujur untuk tangan yang jujur.",
               "Look: you picked two shoots too long, and one leaf is bruised by your nail. No matter. I roasted them for you. From "
               "today you may brew for yourself at the pavilion tables: Bi Luo costs you a quarter less. Honest tea for honest hands."),
     {"gestur": "hormat", "prop": "cangkir"}),
], sekali=True)

dialog("tema_air_lu_bingkisan", "tokoh:lu_yuqing", 35, ["air_misi antar_sutra", "air_bingkisan"], [
    ("mulai", ("Serbet dari Rumah Su? Kering, terlipat, tidak dibuka. Nyonya Su memilih kurir yang baik. Sampaikan terima kasihku, "
               "dan katakan teh musim ini terlalu harum untuk serbet yang biasa.",
               "Napkins from the House of Su? Dry, folded, unopened. Madam Su chose a good courier. Give her my thanks, and tell her "
               "this season's tea is too fragrant for ordinary napkins."),
     {"gestur": "hormat", "akibat": ["air_bingkisan ambil", "air_misi_selesai antar_sutra"]}),
])

# ------------------------------------------------------------------------------------------- Bibi Chuan
LAYANAN_CHUAN = [
    P("Aku mau menyewa perahu.", "I want to hire a boat.", akibat=["air_sewa"]),
    P("Antar aku tur keliling kanal.", "Take me on a canal tour.", akibat=["air_tur"]),
    P("Jual satu lentera teratai padaku.", "Sell me a lotus lantern.", syarat=["acara air lentera_air"], akibat=["air_lentera_beli"],
      alasan=("Lentera hanya dijual pada Malam Lentera Air.", "Lanterns are sold only on the Night of Water Lanterns.")),
]
dialog("tema_air_chuan_sapa", "tokoh:bibi_chuan", 10, [], [
    ("mulai", ("Naik, naik! Perahu Bibi Chuan tidak pernah terbalik, kecuali sekali, dan itu salah bebeknya. Mau ke mana? Kanal kota "
               "ini seperti benang di alat tenun: semuanya bersilangan.",
               "Get in, get in! Auntie Chuan's boat never tips over, except once, and that was the duck's fault. Where to? The canals "
               "of this town are like threads on a loom: they all cross each other."),
     {"gestur": "lambai", "prop": "dayung_yuloh", "pilihan": LAYANAN_CHUAN + [
         P("Ceritakan tentang Lomba Perahu Naga.", "Tell me about the Dragon Boat Race.", syarat=["quest tema_air_perahu_naga baru"],
           ke="naga"),
         P("Nyanyikan sebuah lagu perahu.", "Sing a boat song.", ke="lagu"),
         P("Ada kabar tentang Lao Shu?", "Any word of Lao Shu?", syarat=["bendera air_lao_shu_mata"], ke="mata"),
         P("Ada kabar tentang Lao Shu?", "Any word of Lao Shu?", syarat=["!bendera air_lao_shu_mata", "quest %s selesai" % Q],
           ke="penjara"),
         P("Ada kabar dari kanal?", "Any news from the canals?", ke="kabar"),
         PAMIT]}),
    ("mata", ("Si Tikus Tua menyelipkan surat di bawah dayungku setiap bulan purnama. Tulisannya jelek dan ejaannya lebih jelek, tapi "
              "isinya berguna: perahu mana yang membawa minyak, siapa yang berjubah merah. Aku membacanya, lalu membakarnya, lalu "
              "pura-pura tidak pernah membacanya. Ia bilang ia rindu bau kanal.",
              "The Old Rat slips a letter under my oar every full moon. His hand is ugly and his spelling uglier, but the content "
              "is useful: which boats carry oil, who wears red robes. I read it, burn it, then pretend I never read it. He says "
              "he misses the smell of the canals."),
     {"gestur": "bisik"}),
    ("penjara", ("Lao Shu mengukir perahu kecil dari tulang ikan di sel penjara pintu air. Penjaga menjualnya ke anak-anak, dua "
                 "keping tembaga sebuah. Katanya itu pertama kalinya ia mendapat uang dengan jujur. Ia tidak suka rasanya.",
                 "Lao Shu carves little boats out of fish bones in his cell at the Water Gate prison. The guards sell them to "
                 "children, two coppers each. He says it is the first honest money he ever made. He does not like the taste of it."),
     {"gestur": "tertawa"}),
    ("naga", ("Setiap empat belas hari kami mendayung dua putaran di Danau Lentera, empat perahu naga, dua puluh dayung masing-masing. "
              "Perahu Merah kekurangan penabuh genderang sejak Ah Bo patah lengan. Genderang memberi irama: tepat waktu, perahunya "
              "terbang; terlambat, perahunya tersandung. Mau menggantikannya?",
              "Every fourteen days we row two laps of Lantern Lake, four dragon boats, twenty oars each. The Red Boat has lacked a "
              "drummer since Ah Bo broke his arm. The drum gives the beat: on time and the boat flies; late and it stumbles. Will "
              "you take his place?"),
     {"gestur": "genderang", "prop": "pemukul", "pilihan": [
         P("Aku akan menabuh untuk kalian.", "I will drum for you.", akibat=["quest_mulai tema_air_perahu_naga"], ke="naga_ajar"),
         P("Mungkin lain kali.", "Maybe another time.")]}),
    ("naga_ajar", ("Datang ke Rumah Perahu Naga di galangan saat hari lomba, pukul dua siang, dan klik perahu yang mau kau tabuh. "
                   "Lalu ikuti iramanya.",
                   "Come to the Dragon Boat House at the boatyard on race day, at two in the afternoon, and click the boat you want "
                   "to drum for. Then follow the beat."),
     {"gestur": "tabuh_genderang_perahu", "prop": "pemukul"}),
    ("lagu", ("Dayung yuloh berderit pelan, jembatan batu menunduk diam; yang pergi dengan perahu pagi, pulang dengan lentera malam. "
              "...Ha! Suaraku dulu lebih bagus. Sekarang hanya ikan yang bertepuk tangan.",
              "The yuloh oar creaks softly, the stone bridge bows in silence; who leaves on the morning boat comes home by evening "
              "lantern. ...Ha! My voice was better once. Now only the fish applaud."),
     {"gestur": "nyanyi_perahu", "prop": "dayung_yuloh"}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_chuan_noda", "tokoh:bibi_chuan", 45, ["quest %s tahap noda" % Q], [
    ("mulai", ("Nyonya Su mengirimmu? Air berminyak... ya, aku juga mencium baunya beberapa malam ini di dekat pasar. Ayo kita lihat "
               "bersama. Tapi hanya di malam hari: di siang hari noda seperti itu bersembunyi di bawah kilau matahari.",
               "Madam Su sent you? Oily water... yes, I have smelled it a few nights now near the market. Let us look together. But "
               "only at night: by day a stain like that hides under the glitter of the sun."),
     {"gestur": "sikap_lipat_tangan", "prop": "dayung_yuloh", "pilihan": [
         P("Malam sudah tiba. Antar aku tur malam.", "Night has come. Take me on a night tour.", syarat=["air_malam"], akibat=["air_tur"],
           alasan=("Bibi Chuan menunggu malam (sesudah pukul 19.00).", "Auntie Chuan waits for night (after 19:00).")),
         P("Aku akan kembali saat malam.", "I will come back at night.", ke="tunggu")]}),
    ("tunggu", ("Bagus. Bawa uang tiga keping untuk tur, Bibi Chuan juga harus makan. Dan pakai baju gelap: tikus kanal tidak suka "
                "diperhatikan.",
                "Good. Bring three coins for the tour, Auntie Chuan has to eat too. And wear dark clothes: canal rats do not like "
                "being watched."),
     {"gestur": "tertawa"}),
])

dialog("tema_air_chuan_juara", "tokoh:bibi_chuan", 40, ["quest tema_air_perahu_naga selesai"], [
    ("mulai", ("Juara! Seluruh galangan masih membicarakannya. Ah Bo bilang ia tidak mau sembuh lagi, supaya kau tetap jadi penabuh "
               "kami. Bawalah pita itu dengan bangga.",
               "Champions! The whole boatyard is still talking about it. Ah Bo says he refuses to heal, so you can stay our drummer. "
               "Wear that ribbon with pride."),
     {"gestur": "tepuk_tangan"}),
], sekali=True)

# ------------------------------------------------------------------------------------------- Lao Shu
dialog("tema_air_lao_shu_tangkap", "tokoh:lao_shu", 50, ["quest %s tahap wen_shi" % Q, "!bendera air_lao_shu_putus"], [
    ("mulai", ("Ampun, ampun! Tikus tua ini cuma mengangkut peti, sumpah! Aku tidak tahu isinya! ...Baiklah, aku tahu baunya. "
               "Minyak Bayangan. Diturunkan dari kapal ke sampanku tiap bulan mati, dibayar dengan perak yang terlalu berat.",
               "Mercy, mercy! This old rat only carries crates, I swear! I don't know what's in them! ...All right, I know the smell. "
               "Shadow Oil. Handed from a ship down to my skiff every new moon, paid in silver that is too heavy."),
     {"gestur": "berlutut", "pilihan": [
         P("Siapa yang membayarmu?", "Who pays you?", ke="jubah")]}),
    ("jubah", ("Orang berjubah merah. Tidak pernah melepas tudungnya, tangannya dingin seperti ikan mati. Katanya dari Kota Iblis, "
               "kota yang tidak muncul di peta orang baik-baik. Minyaknya untuk 'tamu-tamu' di sana. Aku cuma tikus! Tikus tidak "
               "bertanya pada kucing mau ke mana.",
               "A man in a red robe. Never lowers his hood, hands cold as dead fish. Says he is from the Demon City, the town that is "
               "not on decent people's maps. The oil is for 'guests' there. I am only a rat! A rat does not ask the cat where it goes."),
     {"gestur": "takut_sembunyi", "pilihan": [
         P("Kau akan kuserahkan kepada penjaga.", "I am handing you to the guards.",
           akibat=["bendera air_lao_shu_putus", "bendera air_lao_shu_serah", "nama_baik 2", "kabar tema_air_lao_shu_ditahan"], ke="serah"),
         P("Pergilah. Tapi mulai sekarang kau mata-mataku.", "Go. But from now on you are my spy.",
           akibat=["bendera air_lao_shu_putus", "bendera air_lao_shu_mata", "air_lepas_lao_shu"], ke="mata")]}),
    ("serah", ("Penjara pintu air dingin dan basah! ...Yah, setidaknya di sana tidak ada orang berjubah merah.",
               "The water-gate jail is cold and wet! ...Well, at least there are no men in red robes there."),
     {"gestur": "menangis"}),
    ("mata", ("Mata-mata? Aku? Ha... baik, baik. Kalau jubah merah itu datang lagi, kau orang pertama yang tahu. Tikus tua ini "
              "punya telinga di setiap saluran.",
              "A spy? Me? Ha... fine, fine. If the red robe comes again, you will be the first to know. This old rat has ears in "
              "every drain."),
     {"gestur": "hitung_koin_gelap"}),
], sekali=True)

dialog("tema_air_lao_shu_mata", "tokoh:lao_shu", 30, ["bendera air_lao_shu_mata"], [
    ("mulai", ("Ssst! Jangan berdiri terlalu dekat, nanti orang tahu kita kenal. Kabar dari saluran: sampan-sampan hitam sekarang "
               "berlayar ke arah gunung, bukan ke laut. Ada yang memindahkan jalurnya.",
               "Shh! Don't stand so close, people will know we are acquainted. News from the drains: the black skiffs sail toward the "
               "mountains now, not the sea. Someone moved the route."),
     {"gestur": "bisik", "pilihan": [
         P("Ada kabar lain?", "Anything else?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "hitung_koin_gelap"}),
])

# ------------------------------------------------------------------------------------------- Sastrawan Wen Shi
dialog("tema_air_wen_sapa", "tokoh:wen_shi", 10, [], [
    ("mulai", ("Hujan di atas daun pisang, katak di kolam berliku. Taman ini dibuat untuk mendengar, bukan untuk dilihat. Duduklah, "
               "anak muda, jangan injak lumutnya.",
               "Rain on the banana leaves, frogs in the winding pond. This garden was made for listening, not for looking. Sit, young "
               "one, and do not step on the moss."),
     {"gestur": "kipas", "prop": "kipas_lipat", "pilihan": [
         P("Mengapa batu-batu itu berlubang?", "Why are those rocks full of holes?", ke="batu"),
         P("Ceritakan tentang gerbang bulan.", "Tell me about the moon gate.", ke="bulan"),
         P("Kakek tampak murung hari ini.", "You look troubled today, grandfather.", syarat=["quest tema_air_bait_tua baru"],
           ke="bait"),
         P("Apakah Kakek melihat lentera merah itu?", "Did you see the red lantern, grandfather?",
           syarat=["quest %s hasil tenang" % Q], ke="cucu_pulang"),
         P("Apakah Kakek baik-baik saja?", "Are you well, grandfather?", syarat=["quest %s hasil sepi" % Q], ke="cucu_sepi"),
         P("Kabar apa yang sampai ke taman?", "What news reaches the garden?", ke="kabar"),
         PAMIT]}),
    ("cucu_pulang", ("Aku melihatnya dari Paviliun Menyimak Hujan. Ia melambai, anak muda. Cucuku yang keras kepala itu melambai "
                     "padaku seperti waktu umurnya sembilan tahun dan berangkat menenun untuk pertama kali. Aku menulis bait baru "
                     "malam itu. Bait pertama dalam lima puluh tahun yang tidak sedih.",
                     "I watched from the Pavilion of Listening to Rain. She waved, young one. My stubborn granddaughter waved to me "
                     "the way she did at nine, leaving to weave for the first time. I wrote a new couplet that night. The first in "
                     "fifty years that is not sad."),
     {"gestur": "tatap_langit", "prop": "kipas_lipat"}),
    ("cucu_sepi", ("Air kanal kembali jernih; itu sudah lebih dari yang bisa diminta orang tua. Tetapi tamanku terlalu sunyi sekarang. "
                   "Dulu, pada malam bulan mati, aku mendengar alat tenun dari kejauhan. Sekarang hanya katak.",
                   "The canal water is clear again; that is more than an old man may ask. But my garden is too quiet now. On "
                   "new-moon nights I used to hear a loom in the distance. Now only frogs."),
     {"gestur": "sikap_tangan_belakang"}),
    ("batu", ("Batu danau. Air mengikisnya selama seribu tahun sampai yang tersisa hanya kehendaknya. Seorang sastrawan memilih batu "
              "seperti memilih teman: yang paling banyak lubangnya, karena angin bisa lewat dan bernyanyi.",
              "Lake rocks. Water wore at them for a thousand years until only their will remained. A scholar chooses rocks as he "
              "chooses friends: the ones with the most holes, because the wind can pass through and sing."),
     {"gestur": "tunjuk"}),
    ("bulan", ("Lewat gerbang bulan, pemandangan di baliknya menjadi lukisan berbingkai bundar. Orang-orang tua bilang, siapa yang "
               "lewat bersama kekasihnya di malam purnama akan bertemu lagi di kehidupan berikutnya. Aku sudah lewat seratus kali. "
               "Sendirian.",
               "Through the moon gate the view beyond becomes a painting in a round frame. The old folk say whoever walks through with "
               "their beloved on a full-moon night will meet again in the next life. I have walked through a hundred times. Alone."),
     {"gestur": "tatap_langit"}),
    ("bait", ("Bait terakhir yang kutulis untuk cucuku hilang. Baris pertamanya kuingat, baris keduanya tidak. Aku menyelipkannya "
              "di antara bait-bait Pasangan Bait supaya angin taman tidak mencurinya, lalu aku lupa yang mana. Maukah kau mencarinya "
              "untuk orang tua ini?",
              "The last couplet I wrote for my granddaughter is lost. I remember its first line, not its second. I slipped it among "
              "the Couplet Matching verses so the garden wind could not steal it, and then forgot which one. Would you look for it, "
              "for an old man?"),
     {"gestur": "kipas", "prop": "kipas_lipat", "pilihan": [
         P("Aku akan menemukannya.", "I will find it.", akibat=["quest_mulai tema_air_bait_tua"], ke="bait_cara"),
         P("Lain kali, Kakek.", "Another time, grandfather.")]}),
    ("bait_cara", ("Mainkan Pasangan Bait bersamaku. Bait yang hilang akan muncul pertama. Pilih baris kedua yang membuat hatimu "
                   "sakit dengan cara yang benar.",
                   "Play Couplet Matching with me. The lost couplet will come up first. Pick the second line that makes your heart "
                   "ache the right way."),
     {"gestur": "membaca", "prop": "kitab"}),
    ("kabar", "kabar", {"gestur": "membaca", "prop": "kitab"}),
])

dialog("tema_air_wen_kisah", "tokoh:wen_shi", 50, ["bendera air_wen_dengar"], [
    ("mulai", ("Kau bertanya tentang lentera yang melawan arus. Duduklah. Aku berumur seratus dua belas tahun; kultivasi tipis yang "
               "kupelajari waktu muda tidak membuatku kuat, hanya membuatku hidup terlalu lama untuk mengingat terlalu banyak.",
               "You ask about the lantern that sails against the current. Sit. I am one hundred and twelve years old; the thin "
               "cultivation I learned when young did not make me strong, it only made me live too long and remember too much."),
     {"gestur": "duduk_kursi", "pilihan": [
         P("Siapa Xiu Niang bagimu?", "Who was Xiu Niang to you?", ke="cucu")]}),
    ("cucu", ("Cucuku, anak dari putriku. Ia menenun di Rumah Su sejak umur sembilan. Di malam pernikahannya, sampan yang membawanya "
              "ke rumah pengantin pria menabrak tiang jembatan yang gelap: lentera buritan dicuri orang malam itu. Ia tenggelam dengan "
              "gaun pengantin yang ia tenun sendiri. Kainnya masih setengah.",
              "My granddaughter, my daughter's child. She wove at the House of Su from the age of nine. On her wedding night the skiff "
              "carrying her to the groom's house struck a dark bridge pier: someone had stolen the stern lantern that night. She "
              "drowned in the wedding gown she wove herself. Her cloth is still half done."),
     {"gestur": "menangis", "pilihan": [
         P("Lentera yang dicuri...", "A stolen lantern...", ke="lentera")]}),
    ("lentera", ("Ya. Kanal ini selalu punya tikus. Lima puluh tahun aku menulis puisi sedih tentangnya, dan tak satu pun membuat airnya "
                 "lebih terang. Kalau arwah-arwah gelisah naik dari danau, itu karena ada yang membangunkan mereka: air yang kotor "
                 "membuat orang mati tidak bisa tidur.",
                 "Yes. These canals always had rats. For fifty years I wrote sad poems about it, and not one made the water any "
                 "brighter. If restless spirits rise from the lake, it is because something woke them: foul water keeps the dead "
                 "from sleeping."),
     {"gestur": "tatap_langit", "pilihan": [
         P("Bagaimana aku menenangkan mereka?", "How do I calm them?", ke="tenang")]}),
    ("tenang", ("Ambil tiga lentera teratai ini, kubuat sendiri dari kertas puisiku yang paling buruk. Malam hari, pergilah ke Danau "
                "Lentera. Bila arwah kanal muncul, lepaskan lentera di dekatnya. Cahaya mengingatkan mereka jalan pulang. Lalu temui "
                "dia di Paviliun Bulan di Air. Ia selalu menunggu di sana.",
                "Take these three lotus lanterns; I made them myself from my worst poems. At night go to Lantern Lake. When the canal "
                "spirits rise, release a lantern near them. Light reminds them of the way home. Then meet her at the Moon-in-Water "
                "Pavilion. She always waits there."),
     {"gestur": "angkat_bawa", "prop": "lentera_teratai", "akibat": ["beri kultivator:tema/lentera_teratai 3"]}),
], sekali=True)

dialog("tema_air_wen_bait_selesai", "tokoh:wen_shi", 40, ["quest tema_air_bait_tua selesai"], [
    ("mulai", ("'Benang merah di bawah air menunggu tangan yang menyambungnya.' Ya... itu dia. Lima puluh tahun aku mencari baris "
               "itu, dan seorang asing menemukannya dalam satu sore. Mungkin puisi memang lebih suka orang asing.",
               "'The red thread beneath the water waits for the hand that ties it.' Yes... that is it. Fifty years I searched for "
               "that line, and a stranger found it in one afternoon. Perhaps poems simply prefer strangers."),
     {"gestur": "hormat_dalam"}),
], sekali=True)

# ------------------------------------------------------------------------------------------- Xiu Niang
dialog("tema_air_xiu_temui", "tokoh:xiu_niang", 60, ["quest %s tahap kain" % Q, "!bendera air_xiu_putus"], [
    ("mulai", ("Lentera buritan... ada yang mencurinya malam itu. Air jembatan sangat dingin. Gaunku berat. Aku berteriak memanggil "
               "kakek, tapi hanya katak yang menjawab. Siapa kau? Mengapa kau tidak lari seperti yang lain?",
               "The stern lantern... someone stole it that night. The water under the bridge was so cold. My gown was heavy. I cried "
               "for grandfather, but only the frogs answered. Who are you? Why do you not run like the others?"),
     {"gestur": "sikap_doa", "pilihan": [
         P("Aku di sini untuk mendengarkan. Ceritakan.", "I am here to listen. Tell me.", syarat=["nama_baik 20"],
           akibat=["bendera air_xiu_putus", "bendera air_xiu_bicara"], ke="dengar",
           alasan=("Arwah ini belum percaya padamu (Nama Baik di kota ini 20).",
                   "This spirit does not trust you yet (Nama Baik 20 in this town).")),
         P("Pergilah, arwah! Kembali ke air!", "Begone, spirit! Back to the water!",
           akibat=["bendera air_xiu_putus", "bendera air_xiu_usir", "npc_pergi"])]}),
    ("dengar", ("Kainku. Sungai merah yang mengalir ke bulan. Aku berjanji pada calon suamiku: ia akan melihat sungai itu selesai "
                "di malam pernikahan kami. Aku tidak bisa pergi sebelum janji itu tertenun. Benangnya masih menungguku di alat tenun "
                "merah.",
                "My cloth. A red river flowing into the moon. I promised my bridegroom he would see that river finished on our "
                "wedding night. I cannot leave before that promise is woven. The thread still waits for me at the red loom."),
     {"gestur": "menangis", "pilihan": [
         P("Aku akan menyelesaikannya untukmu.", "I will finish it for you.", ke="janji")]}),
    ("janji", ("Kau... sungguh? Lalu lepaskan kain itu ke air pada malam tanpa bulan, seperti lentera. Aku akan menunggu di danau. "
               "Aku akan menunggu, seperti dulu.",
               "You... truly? Then release the cloth to the water on a moonless night, like a lantern. I will wait on the lake. I will "
               "wait, as I did before."),
     {"gestur": "hormat_dalam", "akibat": ["npc_pergi"]}),
])

# ------------------------------------------------------------------------------------------- residents (roles)
dialog("tema_air_pendayung", "peran:pendayung", 5, [], [
    ("mulai", ("Hei, awas kepalamu di bawah jembatan! Perahu Pos lewat setiap enam menit, satu keping saja. Naik dari halte "
               "mana pun, turun di halte mana pun.",
               "Hey, mind your head under the bridge! The Post Boat comes every six minutes, just one coin. Board at any stop, get off "
               "at any stop."),
     {"gestur": "lambai", "prop": "dayung_yuloh", "pilihan": [
         P("Ada kabar dari kanal?", "Any news from the canals?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "nyanyi_perahu"}),
])

dialog("tema_air_pemelihara_sakit", "peran:pemelihara_ulat", 6, ["air_ulat_sakit"], [
    ("mulai", ("Satu nampan lagi pagi ini. Ulat-ulat itu cuma diam, tidak mau makan. Daunnya kupetik sendiri, segar! Aku tidak "
               "mengerti. Nyonya Su tidak tidur tiga malam.",
               "Another tray this morning. The worms just lie still, they will not eat. I picked the leaves myself, fresh! I do not "
               "understand. Madam Su has not slept for three nights."),
     {"gestur": "lempar_pakan", "prop": "keranjang", "pilihan": [
         P("Ada kabar lain?", "Other news?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_pemelihara", "peran:pemelihara_ulat", 5, [], [
    ("mulai", ("Dengar? Itu suara ulat makan daun murbei, seperti hujan kecil. Empat kali sehari mereka makan, dan empat kali sehari "
               "aku memanjat rak ini. Sutra yang kau pakai dimulai dari sini.",
               "Hear that? That is the sound of silkworms eating mulberry leaves, like a little rain. Four times a day they eat, and "
               "four times a day I climb these racks. The silk you wear starts here."),
     {"gestur": "lempar_pakan", "prop": "keranjang", "pilihan": [
         P("Ada kabar?", "Any news?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_penenun", "peran:penenun_sutra", 5, [], [
    ("mulai", ("Maaf, tanganku tidak boleh berhenti: kalau benang lungsin kendur, satu hari kerja hilang. Kau tahu, motif bangau "
               "butuh sebelas ribu lemparan teropong.",
               "Sorry, my hands must not stop: if the warp slackens, a whole day's work is lost. Did you know, the crane pattern takes "
               "eleven thousand throws of the shuttle."),
     {"gestur": "tenun", "pilihan": [
         P("Ada kabar dari aula tenun?", "News from the weaving hall?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "bicara"}),
])

dialog("tema_air_pemetik", "peran:pemetik_teh", 5, [], [
    ("mulai", ("Pucuk dan dua daun, tidak lebih. Tangan yang serakah membuat teh yang pahit. Di musim semi, saat embun belum kering, "
               "seluruh bukit harum sampai ke kanal.",
               "A bud and two leaves, no more. Greedy hands make bitter tea. In spring, before the dew dries, the whole hill smells "
               "sweet all the way down to the canal."),
     {"gestur": "petik_teh", "prop": "keranjang_teh", "pilihan": [
         P("Ada kabar dari bukit?", "News from the hill?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "bicara"}),
])

dialog("tema_air_ahli_teh", "peran:ahli_teh", 5, [], [
    ("mulai", ("Cangkir pertama untuk mencuci daun, cangkir kedua untuk tamu, cangkir ketiga untuk persahabatan. Silakan.",
               "The first cup washes the leaves, the second is for the guest, the third is for friendship. Please."),
     {"gestur": "tuang_teh", "prop": "teko", "pilihan": [
         P("Ada kabar?", "Any news?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "bisik"}),
])

dialog("tema_air_sastrawan", "peran:sastrawan", 5, [], [
    ("mulai", ("Sst. Aku hampir menemukan rima untuk 'kabut'. ...Hilang lagi. Kau tahu, di kota ini bahkan tukang perahu bisa "
               "berpantun. Sastrawan seperti kami harus bekerja dua kali lebih keras.",
               "Hush. I almost found a rhyme for 'mist'. ...Gone again. You know, in this town even the boatmen can make verses. "
               "Scholars like us must work twice as hard."),
     {"gestur": "menulis", "prop": "kuas", "pilihan": [
         P("Ada kabar?", "Any news?", ke="kabar"), PAMIT]}),
    ("kabar", "kabar", {"gestur": "kipas", "prop": "kipas_lipat"}),
])

# ====================================================================================================== quests

QUESTS = []


def quest(qid, pemberi, nama, ringkas, tahap, hasil, tempat="kota_air", ulang=False):
    stages = []
    for s in tahap:
        sid = s["id"]
        t("kultivator.cerita.quest.%s.%s" % (qid, sid), s["teks"][0], s["teks"][1])
        o = {k: v for k, v in s.items() if k != "teks"}
        stages.append(o)
    t("kultivator.cerita.quest.%s.nama" % qid, nama[0], nama[1])
    t("kultivator.cerita.quest.%s.ringkas" % qid, ringkas[0], ringkas[1])
    for sid, txt in hasil.items():
        t("kultivator.cerita.quest.%s.hasil.%s" % (qid, sid), txt[0], txt[1])
    obj = {"pemberi": pemberi, "tempat": tempat}
    if ulang:
        obj["ulang"] = True
    obj["tahap"] = stages
    QUESTS.append((qid, obj))


quest(Q, "tokoh:su_jin",
      ("Benang Merah di Bawah Air", "The Red Thread Beneath the Water"),
      ("Ulat sutra Rumah Su mati satu nampan demi satu nampan. Nyonya Su Jin yakin ada yang salah dengan daun murbei atau air kanal.",
       "The silkworms of the House of Su are dying tray by tray. Madam Su Jin is sure something is wrong with the mulberry leaves or "
       "the canal water."),
      [
          {"id": "ulat", "tujuan": ["air_periksa daun", "air_periksa air"], "akibat": ["nama_baik 2"],
           "teks": ("Periksa daun murbei di kebun Rumah Sutra (klik daunnya) dan isi botol kaca dari kanal di depan rumah itu.",
                    "Examine a mulberry leaf in the House of Su orchard (click the leaves) and fill a glass bottle from the canal in "
                    "front of the house.")},
          {"id": "lapor_su", "tujuan": ["bicara tokoh:su_jin"],
           "teks": ("Ceritakan temuanmu kepada Nyonya Su Jin.", "Tell Madam Su Jin what you found.")},
          {"id": "noda", "tujuan": ["air_noda lihat"],
           "teks": ("Ikut tur kanal Bibi Chuan di malam hari (19.00 sampai 05.00) dan perhatikan air dekat Pasar Air Pagi.",
                    "Take Auntie Chuan's canal tour at night (19:00 to 05:00) and watch the water near the Morning Water Market.")},
          {"id": "minyak", "tujuan": ["bicara tokoh:lu_yuqing"], "akibat_masuk": ["bendera air_minyak_dengar"], "cabang": [{"syarat": ["air_tanpa_tikus"], "ke": "wen_shi"}],
           "teks": ("Tunjukkan botol air berminyak kepada Lu Yuqing di Paviliun Teh Awan.",
                    "Show the bottle of oily water to Lu Yuqing at the Cloud Tea Pavilion.")},
          {"id": "kejar", "tujuan": ["air_tikus lao_shu"],
           "teks": ("Malam ini, berjagalah di dekat Pasar Air Pagi atau Rumah Sutra. Kejar sampan hitam Tikus Air dan hentikan dengan "
                    "pukulan.",
                    "Tonight, keep watch near the Morning Water Market or the House of Su. Chase the Water Rats' black skiff and stop it "
                    "with a blow.")},
          {"id": "lao_shu", "tujuan": ["bicara tokoh:lao_shu"], "batas_hari": 1, "gagal_ke": "wen_shi",
           "teks": ("Lao Shu tertangkap. Bicaralah dengannya sebelum ia menyelinap pergi.",
                    "Lao Shu is caught. Talk to him before he slinks away.")},
          {"id": "wen_shi", "tujuan": ["bicara tokoh:wen_shi"], "akibat_masuk": ["bendera air_wen_dengar"], "cabang": [{"syarat": ["air_tanpa_arwah"], "ke": "temui"}],
           "teks": ("Sastrawan Wen Shi di Taman Sang Sastrawan tahu kisah lentera yang melawan arus. Temui dia.",
                    "Scholar Wen Shi in the Scholar's Garden knows the tale of the lantern against the current. Go to him.")},
          {"id": "arwah", "tujuan": ["air_arwah tenang x3"],
           "teks": ("Malam hari di Danau Lentera: tenangkan tiga Arwah Kanal dengan melepas lentera teratai di dekat mereka.",
                    "At night on Lantern Lake: calm three Canal Spirits by releasing a lotus lantern near them.")},
          {"id": "temui", "tujuan": ["bicara tokoh:xiu_niang"],
           "teks": ("Di malam hari, pergilah ke Paviliun Bulan di Air di kolam taman. Xiu Niang menunggu di sana.",
                    "At night, go to the Moon-in-Water Pavilion on the garden pond. Xiu Niang waits there.")},
          {"id": "kain", "tujuan": ["air_kain jadi"],
           "teks": ("Tenun Kain Xiu Niang bersama Su Jin'er di alat tenun merah: 6 Sutra Awan, 10 Daun Teh Embun, 1 Air Suci (kolam Kuil "
                    "Dewa Air memberkati sebotol air untukmu).",
                    "Weave Xiu Niang's Cloth with Su Jin'er at the red loom: 6 Cloud Silk, 10 Dew Tea Leaves, 1 Holy Water (the Water God's "
                    "shrine basin blesses one bottle for you).")},
          {"id": "lepas", "tujuan": ["air_kain_lepas *"], "cabang": [{"syarat": ["bendera air_xiu_bicara"], "ke": "tenang"}],
           "ke": "sepi",
           "teks": ("Pada Malam Lentera Air berikutnya (malam bulan mati), lepaskan Kain Xiu Niang di dermaga lentera Danau Lentera.",
                    "On the next Night of Water Lanterns (new-moon night), release Xiu Niang's Cloth at a lantern jetty on Lantern Lake.")},
          {"id": "tenang", "tujuan": ["hari 0"], "ke": "selesai",
           "akibat": ["gelar penenun_malam", "air_jubah_merah", "bendera tema.air.diskon_sutra", "nama_baik 10",
                      "air_anugerah tema_penenun_malam", "air_ulat pulih", "kabar tema_air_benang_tenang pemain"],
           "teks": ("Xiu Niang pulang bersama cahaya.", "Xiu Niang goes home with the light.")},
          {"id": "sepi", "tujuan": ["hari 0"], "ke": "selesai",
           "akibat": ["gelar penenun_malam", "bendera tema.air.diskon_sutra", "nama_baik 4", "air_anugerah tema_penenun_malam",
                      "air_ulat pulih", "kabar tema_air_benang_sepi pemain"],
           "teks": ("Kain itu hanyut tanpa ada yang menunggunya.", "The cloth drifts away with no one waiting for it.")},
      ],
      {"tenang": ("Kain Xiu Niang berlayar seperti lentera besar, dan arwah sang penenun memudar menjadi cahaya. Ulat-ulat Rumah Su makan "
                  "lagi. Nyonya Su memberimu Jubah Sutra Benang Merah dan gelar Penenun Malam.",
                  "Xiu Niang's Cloth sailed like a great lantern, and the weaver's spirit faded into light. The silkworms of the House "
                  "of Su are eating again. Madam Su gave you the Red Thread Silk Robe and the title Night Weaver."),
       "sepi": ("Kain itu hanyut ke tengah danau dan tenggelam dalam diam. Air kanal kembali bersih dan ulat-ulat pulih, tetapi tidak "
                "ada yang tahu apakah Xiu Niang sempat melihatnya.",
                "The cloth drifted to the middle of the lake and sank in silence. The canal runs clean again and the silkworms "
                "recovered, but no one knows whether Xiu Niang ever saw it.")})

quest("tema_air_teh_pertama", "tokoh:lu_yuqing",
      ("Teh Pertama", "The First Tea"),
      ("Lu Yuqing ingin kau memetik teh dengan tanganmu sendiri, sekali saja, di musim semi.",
       "Lu Yuqing wants you to pick tea with your own hands, just once, in spring."),
      [
          {"id": "petik", "tujuan": ["air_petik x10"],
           "teks": ("Di musim semi pukul 04.00 sampai 08.00, petik 10 pucuk dari semak teh di teras Bukit Teh.",
                    "In spring from 04:00 to 08:00, pick 10 shoots from the tea bushes on the Tea Hill terraces.")},
          {"id": "antar", "tujuan": ["bicara tokoh:lu_yuqing"],
           "akibat": ["beri kultivator:tema/teh_bi_luo 5", "bendera tema.air.izin_seduh", "nama_baik 2", "air_anugerah tema_air_teh"],
           "teks": ("Bawa daun teh petikanmu kepada Lu Yuqing.", "Bring your picked leaves to Lu Yuqing.")},
      ],
      {"antar": ("Lu Yuqing menyangrai daun petikanmu dan memberimu lima Teh Bi Luo. Kau boleh menyeduh sendiri di Paviliun Teh Awan: "
                 "Bi Luo lebih murah seperempat untukmu.",
                 "Lu Yuqing roasted the leaves you picked and gave you five Bi Luo Teas. You may brew for yourself at the Cloud Tea "
                 "Pavilion: Bi Luo costs you a quarter less.")})

quest("tema_air_bait_tua", "tokoh:wen_shi",
      ("Bait yang Hilang", "The Lost Couplet"),
      ("Baris kedua dari bait terakhir Wen Shi untuk cucunya hilang di antara bait-bait permainan Pasangan Bait.",
       "The second line of Wen Shi's last couplet for his granddaughter is lost among the Couplet Matching verses."),
      [
          {"id": "bait", "tujuan": ["air_bait_tua ketemu"],
           "teks": ("Mainkan Pasangan Bait bersama Wen Shi dan temukan baris kedua dari bait yang hilang.",
                    "Play Couplet Matching with Wen Shi and find the second line of the lost couplet.")},
          {"id": "kembali", "tujuan": ["bicara tokoh:wen_shi"], "akibat": ["nama_baik 3", "air_anugerah tema_air_bait"],
           "teks": ("Bacakan bait yang utuh untuk Wen Shi.", "Recite the whole couplet to Wen Shi.")},
      ],
      {"kembali": ("Wen Shi menyalin bait itu dengan kuas terbaiknya dan menggantungnya di Paviliun Menyimak Hujan.",
                   "Wen Shi copied the couplet with his finest brush and hung it in the Pavilion of Listening to Rain.")})

quest("tema_air_perahu_naga", "tokoh:bibi_chuan",
      ("Genderang Perahu Naga", "The Dragon Boat Drum"),
      ("Perahu Bibi Chuan kehilangan penabuh genderangnya. Menangkan Lomba Perahu Naga sebagai penabuh.",
       "Auntie Chuan's boat has lost its drummer. Win the Dragon Boat Race as a drummer."),
      [
          {"id": "tabuh", "tujuan": ["air_naga_menang juara"], "akibat": ["nama_baik 1", "bendera tema.air.juara_naga"],
           "teks": ("Pada hari lomba (setiap 14 hari, pukul 14.00), daftar sebagai penabuh di Rumah Perahu Naga dan bawa perahumu menjadi "
                    "juara.",
                    "On race day (every 14 days, at 14:00) sign up as a drummer at the Dragon Boat House and drum your boat to "
                    "victory.")},
      ],
      {"tabuh": ("Perahumu juara Lomba Perahu Naga. Galangan masih menyanyikan iramamu.",
                 "Your boat won the Dragon Boat Race. The boatyard still sings your beat.")})

quest("tema_air_anak_jembatan", "peran:anak",
      ("Anak di Kanal", "The Child in the Canal"),
      ("Hujan deras membuat jembatan licin, dan seorang anak jatuh ke kanal.",
       "Heavy rain made the bridges slick, and a child fell into the canal."),
      [
          {"id": "tolong", "tujuan": ["air_anak selamat"], "batas_hari": 1, "gagal_ke": "ditolong_lain", "ke": "pulang",
           "teks": ("Berenanglah ke anak itu, klik dia, lalu bawa ke tangga cuci terdekat.",
                    "Swim to the child, click them, then bring them to the nearest washing steps.")},
          {"id": "pulang", "tujuan": ["hari 0"], "ke": "selesai",
           "teks": ("Anak itu selamat.", "The child is safe.")},
          {"id": "ditolong_lain", "tujuan": ["hari 0"], "ke": "gagal",
           "teks": ("Para pendayung menarik anak itu keluar sebelum kau sampai.", "The boatmen pulled the child out before you arrived.")},
      ],
      {"pulang": ("Kau membawa anak itu ke tangga cuci. Ibunya memelukmu dan anak itu, basah kuyup, lama sekali.",
                  "You carried the child to the washing steps. Their mother hugged you both, soaking wet, for a long time."),
       "ditolong_lain": ("Para pendayung menarik anak itu keluar dengan galah. Ia selamat, hanya kedinginan.",
                         "The boatmen pulled the child out with a pole. They are safe, only cold.")},
      ulang=True)

t("kultivator.cerita.tempat.kota_air", "Kota Air", "Water Town")
t("kultivator.cerita.gelar.penenun_malam", "Penenun Malam", "Night Weaver")

# ====================================================================================================== rumours

KABAR = []


def kabar(kid, pemicu, idn, en, bobot=10, umur=2, penyebar=None):
    o = {"id": kid, "pemicu": pemicu, "bobot": bobot, "umur_hari": umur}
    if penyebar:
        o["peran_penyebar"] = penyebar
    KABAR.append(o)
    t("kultivator.cerita.kabar." + kid, idn, en)


AIR_ROLES = ["pendayung", "penenun_sutra", "pemelihara_ulat", "ahli_teh", "pemetik_teh", "sastrawan", "nyonya_sutra"]
kabar("tema_air_lentera", "tema_air_lentera", "Malam ini bulan mati. Semua orang melepas lentera teratai di kanal, kau ikut?",
      "No moon tonight. Everyone is releasing lotus lanterns on the canals, are you coming?", umur=1)
kabar("tema_air_harapan", "tema_air_harapan", "%s melepas lentera di Danau Lentera. Harapannya hanya diketahui air.",
      "%s released a lantern on Lantern Lake. Only the water knows the wish.", umur=1)
kabar("tema_air_perahu_naga", "tema_air_perahu_naga", "Genderang sudah dipukul di galangan! Lomba Perahu Naga dimulai, cepat ke jembatan!",
      "The drums are sounding at the boatyard! The Dragon Boat Race is starting, hurry to the bridges!", umur=1)
kabar("tema_air_juara_naga", "tema_air_juara_naga", "Kau dengar? %s menabuh genderang perahu juara. Iramanya seperti jantung naga.",
      "Did you hear? %s drummed the winning dragon boat. The beat was like a dragon's heart.", umur=4)
kabar("tema_air_tikus", "tema_air_tikus", "Tikus Air merampok lagi tadi malam. Kunci peti lapakmu, dan jangan tidur di perahu.",
      "The Water Rats robbed again last night. Lock your stall chest, and do not sleep in the boat.", umur=3)
kabar("tema_air_tikus_tertangkap", "tema_air_tikus_tertangkap", "%s menangkap pencuri kanal dengan tangan kosong! Pedagang pasar air "
      "berutang padanya.", "%s caught a canal thief with bare hands! The water-market traders owe them.", umur=4)
kabar("tema_air_lao_shu_ditahan", "tema_air_lao_shu_ditahan", "Lao Shu si Tikus Tua mendekam di penjara pintu air. Para pencuri kanal "
      "kehilangan kepala.", "Lao Shu the Old Rat sits in the water-gate jail. The canal thieves have lost their head.", umur=6)
kabar("tema_air_benang_tenang", "tema_air_benang_tenang", "Tadi malam ada lentera merah besar di danau, dan bayangan seorang pengantin "
      "melambai dari atasnya. Kata orang, %s yang melepaskannya.", "Last night a great red lantern sailed the lake, and the shadow of "
      "a bride waved from it. They say %s released it.", umur=8)
kabar("tema_air_benang_sepi", "tema_air_benang_sepi", "%s melepas kain merah di danau. Ulat Rumah Su sudah makan lagi, syukurlah.",
      "%s released a red cloth on the lake. The House of Su's silkworms are eating again, thank heavens.", umur=6)
# background talk of the water towns (only the themed roles tell them)
UMUM = [
    ("Enam puluh empat jembatan, dan orang luar selalu bilang seribu. Kami tidak pernah membetulkannya.",
     "Sixty-four bridges, and outsiders always say a thousand. We never correct them."),
    ("Rumah Su membeli daun murbei dari tiga desa. Ulat-ulatnya lebih rewel daripada pejabat ibu kota.",
     "The House of Su buys mulberry leaves from three villages. Their silkworms are fussier than capital officials."),
    ("Teh Bi Luo musim semi ini harum sekali, sampai pemetik di teras bersin-bersin.",
     "This spring's Bi Luo is so fragrant the pickers on the terraces keep sneezing."),
    ("Bibi Chuan dulu bisa menyanyi dari Pintu Air sampai Jembatan Kembar tanpa menarik napas. Begitu katanya.",
     "Auntie Chuan once sang from the Water Gate to the Twin Bridges without drawing breath. So she says."),
    ("Pada malam tanpa bulan, jangan duduk di Paviliun Bulan di Air sendirian.",
     "On moonless nights, do not sit alone in the Moon-in-Water Pavilion."),
    ("Wisma Zhang punya enam halaman dan, kata pembantunya, tujuh hantu. Satu untuk setiap halaman, ditambah satu yang suka dapur.",
     "The Zhang Residence has six courtyards and, its servants say, seven ghosts. One for each courtyard, plus one who likes the "
     "kitchen."),
    ("Pelukis dari ibu kota datang lagi untuk melukis Jembatan Kembar. Mereka selalu menggambar perahunya terlalu kecil.",
     "Painters from the capital came again to paint the Twin Bridges. They always draw the boats too small."),
    ("Kalau hujan turun, sastrawan justru keluar ke Paviliun Menyimak Hujan. Orang aneh, para sastrawan itu.",
     "When it rains, the scholars actually go out to the Pavilion of Listening to Rain. Strange people, scholars."),
    ("Pintu Air ditutup pukul sembilan malam. Siapa pun yang masih di luar harus mendayung memutar lewat kanal lingkar.",
     "The Water Gate closes at nine at night. Anyone still outside must row the long way round the ring canal."),
    ("Ikan danau di Rumah Makan dimasak dengan arak beras dan jahe. Datanglah lapar.",
     "The lake fish at the Eating House is cooked with rice wine and ginger. Come hungry."),
]
# D-TK1 detail pass: more background talk (weather, seasons, the people of the canals)
UMUM += [
    ("Musim hujan datang lagi. Payung kertas Jembatan Kembar laku sebelum matahari terbit.",
     "The rainy season is back. The paper umbrellas of the Twin Bridges sell out before sunrise."),
    ("Nyonya Su menolak tiga lamaran untuk putrinya bulan ini. Jin'er bilang dia lebih suka menikahi alat tenun.",
     "Madam Su turned down three proposals for her daughter this month. Jin'er says she'd rather marry a loom."),
    ("Lu Yuqing menyeduh teh untuk dirinya sendiri setiap senja, dengan dua cawan. Tidak ada yang tahu cawan kedua untuk siapa.",
     "Lu Yuqing brews tea for herself every dusk, with two cups. Nobody knows who the second cup is for."),
    ("Sastrawan Wen Shi menulis puisi di jembatan, lalu melemparkannya ke air. Katanya kanal adalah pembaca terbaik.",
     "Scholar Wen Shi writes poems on the bridge, then throws them into the water. He says the canal is the best reader."),
    ("Kalau air kanal naik sampai anak tangga ketiga, Pintu Air ditutup separuh. Para pendayung menggerutu, tapi rumah tetap kering.",
     "When the canal rises to the third step, the Water Gate is half closed. The rowers grumble, but the houses stay dry."),
    ("Daun murbei dari desa barat datang terlambat lagi. Ulat Rumah Su lapar dan penenun ikut gelisah.",
     "The mulberry leaves from the western village are late again. The House of Su's silkworms are hungry and the weavers fret."),
    ("Para pemetik teh bilang ada kultivator yang bertapa di Teras Teh tiap subuh. Daun di sekitarnya tumbuh lebih harum.",
     "The tea pickers say a cultivator meditates on the Tea Terraces every dawn. The leaves around him grow more fragrant."),
    ("Kucing pasar air bisa membedakan perahu nelayan dari perahu pos. Ia hanya melompat ke perahu nelayan.",
     "The water market cat can tell a fishing boat from a post boat. It only ever jumps onto the fishing boats."),
    ("Di Wisma Zhang ada lukisan Jembatan Kembar yang lebih tua daripada jembatannya. Coba pikirkan itu baik-baik.",
     "In the Zhang Residence hangs a painting of the Twin Bridges older than the bridges themselves. Think about that."),
    ("Kabut pagi di danau setebal sutra. Bibi Chuan mendayung dengan mata tertutup dan tidak pernah menabrak apa pun.",
     "The morning mist on the lake is thick as silk. Auntie Chuan rows with her eyes shut and never hits a thing."),
]
for i, (a, b) in enumerate(UMUM, 1):
    kabar("tema_air_umum_%d" % i, "umum", a, b, bobot=6, umur=1, penyebar=AIR_ROLES)
# rumours when a player finishes a side quest of the water town (trigger quest_<id>, %s = the player)
kabar("tema_air_teh_pertama_selesai", "quest_tema_air_teh_pertama",
      "%s memetik teh musim semi bersama Lu Yuqing. Kata ahli teh itu, tangannya ringan seperti embun.",
      "%s picked the spring tea with Lu Yuqing. The tea master says those hands are light as dew.", umur=4)
kabar("tema_air_bait_tua_selesai", "quest_tema_air_bait_tua",
      "Bait terakhir Wen Shi untuk cucunya sudah utuh lagi; %s menemukannya. Sekarang tergantung di Paviliun Menyimak Hujan.",
      "Wen Shi's last couplet for his granddaughter is whole again; %s found it. It hangs in the Pavilion of Listening to Rain now.",
      umur=5)
kabar("tema_air_perahu_naga_selesai", "quest_tema_air_perahu_naga",
      "Perahu yang genderangnya ditabuh %s menang dengan selisih satu kepala naga. Para pendayungnya masih belum berhenti bersorak.",
      "The boat whose drum %s beat won by one dragon's head. Its rowers have not stopped cheering.", umur=4)
kabar("tema_air_anak_jembatan_selesai", "quest_tema_air_anak_jembatan",
      "Anak yang jatuh ke kanal waktu hujan deras sudah pulang dengan selamat. %s yang menariknya ke tangga cuci.",
      "The child who fell into the canal in the downpour is home safe. %s pulled the little one to the washing steps.", umur=4)
kabar("tema_air_benang_selesai", "quest_tema_air_benang",
      "Rumah Sutra Su menenun lagi siang dan malam. Nyonya Su bilang semua itu berkat %s, lalu menolak mengatakan lebih banyak.",
      "The House of Su is weaving day and night again. Madam Su says it is all thanks to %s, then refuses to say more.", umur=6)


# ====================================================================================================== part 2: system lines, couplets, schedule, demand, writer
K = "kultivator.kota_tema.air."


def k(key, idn, en):
    t(K + key, idn, en)


# ------------------------------------------------------------------------------------------- characters
TOKOH = {
    "su_jin": (("Nyonya Su Jin", "Madam Su Jin"), ("Pemilik Rumah Sutra Su", "Mistress of the House of Su Silk")),
    "su_jiner": (("Su Jin'er", "Su Jin'er"), ("Penenun Muda Rumah Su", "Young Weaver of the House of Su")),
    "lu_yuqing": (("Lu Yuqing", "Lu Yuqing"), ("Ahli Teh Paviliun Awan", "Tea Master of the Cloud Pavilion")),
    "bibi_chuan": (("Bibi Chuan", "Auntie Chuan"), ("Pendayung Seribu Jembatan", "Boatwoman of the Thousand Bridges")),
    "wen_shi": (("Sastrawan Wen Shi", "Scholar Wen Shi"), ("Penjaga Taman Sang Sastrawan", "Keeper of the Scholar's Garden")),
    "xiu_niang": (("Xiu Niang", "Xiu Niang"), ("Penenun yang Tenggelam", "The Drowned Weaver")),
    "lao_shu": (("Lao Shu \"Tikus Tua\"", "Lao Shu \"Old Rat\""), ("Kepala Tikus Air", "Boss of the Water Rats")),
}
for tid, (nama, gelar) in TOKOH.items():
    k("tokoh." + tid, *nama)
    k("tokoh." + tid + ".gelar", *gelar)

# ------------------------------------------------------------------------------------------- general
k("uang_kurang", "Uangmu kurang: perlu %s.", "Not enough money: you need %s.")
k("bea", "Petugas Pintu Air memeriksa muatanmu: %s barang selundupan disita, bea %s.",
  "The Water Gate clerks check your cargo: %s smuggled goods seized, toll %s.")
k("lukisan.judul", "Kenangan Kampung Halaman", "Memory of the Hometown")
k("lukisan.sub", "Sudut Jembatan Kembar, persis seperti dalam lukisan termasyhur itu",
  "The Twin Bridges corner, exactly as in the famous painting")
k("situs.taman_klasik", "Taman Klasik", "Classical Garden")
k("bingkisan", "Bingkisan Sutra Rumah Su", "House of Su Silk Parcel")
k("bingkisan.lore", "Serbet sutra untuk Paviliun Teh Awan. Jangan dibuka.", "Silk napkins for the Cloud Tea Pavilion. Do not open.")
k("cmd.arwah", "Arwah Kanal yang hidup di kota ini: %s", "Canal Spirits alive in this town: %s")
k("cmd.luar", "Kau tidak sedang berada di Kota Air.", "You are not in a Water Town.")
k("cmd.info", "Kota Air %s · putar %s · distrik %s · lokal (%s, %s) · mekanisme %s · acara: %s",
  "Water Town %s · rotation %s · district %s · local (%s, %s) · mechanisms %s · events: %s")
k("cmd.pos", "Perahu Pos sedang di blok ke-%s dari %s blok rute.", "The Post Boat is at block %s of a %s-block route.")
k("cmd.acara", "Acara %s dimulai paksa.", "Event %s force-started.")
k("cmd.acara_gagal", "Acara %s tidak dikenal atau tidak bisa dimulai di sini.", "Event %s is unknown or cannot start here.")

# ------------------------------------------------------------------------------------------- chain hooks
k("benang.daun", "Daun murbei ini segar dan hijau, tanpa bintik. Bukan daunnya yang meracuni ulat.",
  "This mulberry leaf is fresh and green, without a spot. The leaves are not what poisons the silkworms.")
k("benang.air", "Air kanal di botolmu berkilau seperti pelangi tipis, dan baunya pahit seperti lampu minyak murahan.",
  "The canal water in your bottle shimmers with a thin rainbow and smells bitter, like a cheap oil lamp.")
k("benang.air_suci", "Air kolam Kuil Dewa Air berpendar lembut di botolmu: Air Suci, diberkati sekali ini untukmu.",
  "The basin water of the Water God's shrine glows softly in your bottle: Holy Water, blessed this once for you.")
k("benang.noda", "Di bawah lentera buritan, permukaan kanal dekat pasar menghitam: noda minyak yang tidak memantulkan cahaya sedikit pun.",
  "Under the stern lantern the canal by the market turns black: an oily stain that reflects no light at all.")
k("benang.noda.kata", "Lihat itu. Tiga puluh tahun aku mendayung di sini, air kita tak pernah sehitam ini.",
  "Look there. Thirty years I've rowed these canals and our water was never this black.")
k("xiu.muncul", "Kau bisa melihatku...? Sudah lima puluh tahun tak ada yang menoleh.",
  "You can see me...? No one has turned to look in fifty years.")
k("xiu.lentera", "Sebuah lentera berlayar melawan arus menuju Paviliun Bulan di Air. Di lantainya berdiri seorang gadis berbaju pengantin yang basah kuyup.",
  "A lantern sails against the current toward the Moon-in-Water Pavilion. On its floor stands a girl in a soaking wedding dress.")
k("anak.pegang", "Pegang tanganku... jangan lepaskan!", "Hold my hand... don't let go!")
k("anak.bawa", "Bawa anak itu ke tangga cuci terdekat.", "Bring the child to the nearest washing steps.")
k("anak.tolong", "Tolong! Aku jatuh dari jembatan!", "Help! I fell off the bridge!")
k("anak.jatuh", "Seorang anak tergelincir dari jembatan yang licin ke kanal yang deras! Berenanglah ke sana, klik dia, lalu bawa ke tangga cuci.",
  "A child slipped off a slick bridge into the rushing canal! Swim over, click them, then bring them to the washing steps.")
k("anak.terima_kasih", "Terima kasih, Kak! Aku tidak akan main di jembatan saat hujan lagi.",
  "Thank you! I won't play on the bridge in the rain ever again.")
k("anak.selamat", "Kau menyelamatkan anak itu. Ibunya berlari menuruni tangga cuci sambil menangis lega.",
  "You saved the child. Their mother runs down the washing steps, weeping with relief.")
k("jasa.sutra", "Toko Rumah Sutra", "House of Su Shop")
k("jasa.sutra.catatan", "Sutra Awan, jubah pesanan, kipas sutra, magang tenun", "Cloud Silk, tailored robes, silk fans, weaving apprenticeship")
k("jasa.teh", "Daftar Teh Paviliun", "Pavilion Tea Menu")
k("jasa.teh.catatan", "Teh khas dan upacara teh", "House teas and the tea ceremony")
k("jasa.bait", "Pasangan Bait", "Couplet Matching")
k("jasa.bait.catatan", "Pilih baris kedua yang serasi", "Pick the matching second line")

# ------------------------------------------------------------------------------------------- lantern night
k("panggung.penyanyi", "Penyanyi Panggung Air", "Water Stage Singer")
k("panggung.pemusik", "Pemusik Panggung Air", "Water Stage Musician")
LAGU = [
    ("♪ Dayung yuloh berderit pelan, jembatan batu menunduk diam... ♪", "♪ The yuloh oar creaks softly, the stone bridge bows in silence... ♪"),
    ("♪ Teratai putih di Danau Lentera, menanti perahu yang tak kembali... ♪", "♪ White lotus on Lantern Lake, waiting for a boat that won't return... ♪"),
    ("♪ Sutra Su sehalus kabut pagi, terjual habis sebelum matahari... ♪", "♪ Su silk as fine as morning mist, sold out before the sun... ♪"),
    ("♪ Enam puluh empat jembatan, seribu kata orang kota... ♪", "♪ Sixty-four bridges, a thousand, so the townsfolk say... ♪"),
    ("♪ Teh dari bukit utara, harumnya sampai ke hilir... ♪", "♪ Tea from the northern hill, its scent drifts all the way downstream... ♪"),
    ("♪ Malam tanpa bulan, satu lentera melawan arus... ♪", "♪ On a moonless night, one lantern sails against the stream... ♪"),
]
for i, (a, b) in enumerate(LAGU, 1):
    k("lagu.%d" % i, a, b)
k("lentera.bukan_malam", "Lentera teratai hanya dilepas pada Malam Lentera Air (malam bulan mati, 19.00 sampai 24.00).",
  "Lotus lanterns are released only on the Night of Water Lanterns (new-moon night, 19:00 to 24:00).")
k("lentera.cukup", "Kau sudah membeli %s lentera malam ini.", "You already bought %s lanterns tonight.")
k("lentera.beli", "Lentera Teratai dibeli. Beri nama di landasan untuk menulis harapanmu (paling banyak %s huruf), lalu lepaskan di dermaga lentera.",
  "Lotus Lantern bought. Rename it at an anvil to write your wish (at most %s letters), then release it at a lantern jetty.")
k("lentera.ke_air", "Berdirilah di dermaga lentera atau menghadaplah ke air untuk melepasnya.",
  "Stand at a lantern jetty or face the water to release it.")
k("lentera.lepas_pertama", "Lenteramu hanyut perlahan menuju Danau Lentera. Warga di tepi kanal menangkupkan tangan untukmu.",
  "Your lantern drifts slowly toward Lantern Lake. The people on the bank press their palms together for you.")
k("lentera.lepas", "Satu lentera lagi hanyut menyusul.", "Another lantern drifts after it.")
k("kain.tunggu_malam", "Kain ini harus dilepas pada Malam Lentera Air.", "This cloth must be released on the Night of Water Lanterns.")
k("kain.harapan", "Untuk Xiu Niang: benangmu sudah selesai.", "For Xiu Niang: your thread is finished.")
k("kain.xiu_pamit", "Merah... merah seperti yang kujanjikan. Terima kasih. Sekarang aku boleh pulang.",
  "Red... red as I promised. Thank you. Now I may go home.")
k("kain.lepas", "Kain Xiu Niang berlayar sebagai lentera besar. Arwah sang penenun memudar menjadi cahaya, dan di sepanjang kanal warga bersujud dengan dupa.",
  "Xiu Niang's Cloth sails away as a great lantern. The weaver's spirit fades into light, and all along the canal people bow with incense.")
k("kain.lepas_sepi", "Kain itu hanyut tanpa ada yang menunggunya. Danau menelan cahayanya dalam diam.",
  "The cloth drifts away with no one waiting for it. The lake swallows its light in silence.")
k("arwah.nama", "Arwah Kanal", "Canal Spirit")
k("arwah.tenang", "Cahaya lentera menenangkan arwah itu. Ia memudar seperti kabut pagi.",
  "The lantern light calms the spirit. It fades like morning mist.")

# ------------------------------------------------------------------------------------------- dragon boats
k("naga.umum", "Lomba Perahu Naga dimulai dalam %s detik! Daftarlah sebagai penabuh genderang di Rumah Perahu Naga.",
  "The Dragon Boat Race starts in %s seconds! Sign up as a drummer at the Dragon Boat House.")
k("naga.irama", "Iramamu: %s. Perahumu melaju %s%% lebih cepat.", "Your rhythm: %s. Your boat runs %s%% faster.")
k("naga.mulai", "Genderang! Dayung!", "Drums! Row!")
for v, (a, b) in enumerate([("Perahu Naga Merah", "Red Dragon Boat"), ("Perahu Naga Hijau", "Green Dragon Boat"),
                            ("Perahu Naga Biru", "Blue Dragon Boat"), ("Perahu Naga Hitam", "Black Dragon Boat")]):
    k("naga.perahu.%d" % v, a, b)
k("naga.finis", "%s mencapai garis akhir di urutan ke-%s.", "%s crosses the finish line in place %s.")
k("naga.menang", "Perahumu juara! Bagianmu %s, Nama Baik +2 dan Pita Naga.", "Your boat wins! Your share is %s, Nama Baik +2 and the Dragon Ribbon.")
k("naga.ikut", "Perahumu finis ke-%s. Upah penabuh: %s.", "Your boat finished in place %s. Drummer's pay: %s.")
k("naga.sudah_mulai", "Lomba sudah berjalan.", "The race is already under way.")
k("naga.sudah_daftar", "Kau sudah terdaftar sebagai penabuh.", "You are already signed up as a drummer.")
k("naga.ada_penabuh", "Perahu ini sudah punya penabuh.", "This boat already has a drummer.")
k("naga.daftar", "Kau menjadi penabuh genderang %s. Ikuti iramanya saat lomba dimulai!",
  "You are now the drummer of the %s. Follow the beat when the race starts!")
k("naga.bukan_hari", "Lomba Perahu Naga diadakan setiap empat belas hari, pukul 14.00 sampai 16.00.",
  "The Dragon Boat Race is held every fourteen days, from 14:00 to 16:00.")

# ------------------------------------------------------------------------------------------- silk house
for m, (a, b) in {"awan": ("Awan", "Cloud"), "bangau": ("Bangau", "Crane"), "teratai": ("Teratai", "Lotus"), "naga": ("Naga", "Dragon"),
                  "benang_merah": ("Benang Merah", "Red Thread")}.items():
    k("motif." + m, a, b)
k("jubah.lore", "Sutra Su bermotif %s, warna %s", "Su silk, %s pattern, %s")
k("sutra.judul", "Rumah Sutra Su", "House of Su Silk")
k("sutra.awan.catatan", "Bahan dagang dan tempa; harganya ikut pasar", "Trade and forging material; the price follows the market")
k("sutra.kipas.catatan", "Kipas lipat sutra untuk dipegang (kosmetik)", "A folding silk fan to hold (cosmetic)")
k("sutra.tenun.besok", "Magang tenun hanya sekali sehari. Kembalilah besok.", "The weaving apprenticeship is once a day. Come back tomorrow.")
k("sutra.kain.petunjuk", "Tenun Kain Xiu Niang bersama Su Jin'er: klik alat tenun merah di aula tenun.",
  "Weave Xiu Niang's Cloth with Su Jin'er: click the red loom in the weaving hall.")
k("sutra.beli", "Kau membeli %s seharga %s.", "You bought %s for %s.")
k("sutra.jubah.ada", "Kau masih punya pesanan jubah yang belum diambil.", "You still have a robe order waiting to be collected.")
k("sutra.jubah.dipesan", "Jubah bermotif %s dipesan. Kembalilah dalam %s hari.", "A robe with the %s pattern is ordered. Come back in %s days.")
k("sutra.jubah.belum", "Jubahmu belum selesai ditenun.", "Your robe is not woven yet.")
k("sutra.jubah.ambil", "Jubahmu sudah jadi. Su Jin melipatnya dengan kertas tisu dan seutas pita.",
  "Your robe is ready. Su Jin folds it in tissue paper with a ribbon.")
k("sutra.tenun.gagal", "Benangmu kusut (skor %s). Coba lagi besok.", "Your thread tangled (score %s). Try again tomorrow.")
k("sutra.tenun.hadiah", "Skor %s! Nyonya Su memberimu %s Sutra Awan.", "Score %s! Madam Su gives you %s Cloud Silk.")
k("sutra.kain.bahan", "Untuk Kain Xiu Niang perlu %s Sutra Awan, %s Daun Teh Embun dan %s Air Suci.",
  "Xiu Niang's Cloth needs %s Cloud Silk, %s Dew Tea Leaves and %s Holy Water.")
k("sutra.kain.ulang", "Babak %s gagal (skor %s). Jin'er menggulung benangnya lagi: coba sekali lagi.",
  "Pass %s failed (score %s). Jin'er rewinds the thread: try once more.")
k("sutra.kain.babak", "Babak %s selesai (skor %s). Benang merah mulai membentuk sungai...",
  "Pass %s done (score %s). The red thread begins to form a river...")
k("sutra.kain.jadi", "Kain Xiu Niang selesai: sungai merah di atas sutra putih, mengalir ke bulan, persis seperti sketsa lima puluh tahun lalu.",
  "Xiu Niang's Cloth is finished: a red river on white silk flowing into the moon, just like the sketch from fifty years ago.")

# ------------------------------------------------------------------------------------------- tea pavilion
k("teh.judul", "Paviliun Teh Awan", "Cloud Tea Pavilion")
k("teh.efek.bi_luo", "Pemahaman +0,03 per menit meditasi selama 10 menit", "Comprehension +0.03 per minute of meditation for 10 minutes")
k("teh.efek.embun_gunung", "Regenerasi Qi +8 % selama 5 menit", "Qi regeneration +8 % for 5 minutes")
k("teh.efek.seribu_tahun", "Sekali sehari meredakan tekanan iblis hati 5; bila tak ada, pemahaman +0,05 per menit meditasi selama 10 menit",
  "Once a day eases heart-demon pressure by 5; without any, comprehension +0.05 per minute of meditation for 10 minutes")
k("teh.aktif.1", "Teh Embun Gunung menghangatkan meridianmu (%s menit).", "Mountain Dew Tea warms your meridians (%s minutes).")
k("teh.aktif.2", "Teh Bi Luo menjernihkan pikiranmu (%s menit). Bermeditasilah.", "Bi Luo Tea clears your mind (%s minutes). Meditate.")
k("teh.aktif.3", "Teh Seribu Tahun mengendap di dantianmu (%s menit).", "Thousand-Year Tea settles in your dantian (%s minutes).")
k("teh.lebih_kuat", "Teh yang lebih kuat masih bekerja di tubuhmu.", "A stronger tea is still working in your body.")
k("teh.seribu.hati", "Teh Seribu Tahun meredakan bisikan iblis hatimu (-%s).", "Thousand-Year Tea quiets your heart demon's whispers (-%s).")
k("teh.seribu.habis", "Teh Seribu Tahun hari ini sudah habis. Satu poci sehari, tidak lebih.",
  "Today's Thousand-Year Tea is gone. One pot a day, no more.")
k("teh.pita", "Hanya untuk kultivator pita %s ke atas.", "Only for cultivators of band %s and above.")
k("teh.beli", "Kau membeli %s seharga %s.", "You bought %s for %s.")
k("teh.upacara.besok", "Upacara teh hanya sekali sehari. Kembalilah besok.", "The tea ceremony is once a day. Come back tomorrow.")
k("teh.upacara.kata", "Cangkir pertama untuk mencuci daun, cangkir kedua untuk tamu. Cangkir ketiga untuk persahabatan.",
  "The first cup washes the leaves, the second is for the guest. The third is for friendship.")
k("teh.upacara.mulai", "Lu Yuqing menghangatkan poci tanah liat dan menuang air seperti mata kepiting...",
  "Lu Yuqing warms the clay pot and pours water like crab eyes...")
k("teh.upacara.selesai", "Upacara selesai. Harum Bi Luo masih tinggal di lidahmu.", "The ceremony ends. The scent of Bi Luo lingers on your tongue.")
k("petik.bukan_waktu", "Teh hanya dipetik di musim semi, pukul 04.00 sampai 08.00, saat embun belum kering.",
  "Tea is picked only in spring, from 04:00 to 08:00, before the dew dries.")
k("petik.cukup", "Cukup untuk pagi ini: %s pucuk. Semak teh butuh istirahat.", "Enough for this morning: %s shoots. The bushes need rest.")
k("petik.dapat", "Pucuk dan dua daun: %s/%s", "A bud and two leaves: %s/%s")
k("daun.cukup", "Lu Yuqing sudah membeli cukup daun hari ini.", "Lu Yuqing has bought enough leaves today.")
k("daun.tak_ada", "Kau tidak membawa Daun Teh Embun.", "You carry no Dew Tea Leaves.")
k("daun.jual", "Lu Yuqing membeli %s Daun Teh Embun seharga %s.", "Lu Yuqing buys %s Dew Tea Leaves for %s.")

# ------------------------------------------------------------------------------------------- couplets
BAIT = [
    ("Hujan menyisir genteng hitam di kota air", "Perahu menyisir kabut di bawah jembatan batu",
     "Rain combs the black roof tiles of the water town", "Boats comb the mist beneath the stone bridges"),
    ("Ulat memintal sutra dalam diam semalam", "Penenun mengurainya menjadi sungai sepanjang hari",
     "The silkworm spins its silk in silence all night", "The weaver unwinds it into a river all day"),
    ("Teh pertama dipetik saat embun belum kering", "Puisi pertama ditulis saat tinta belum dingin",
     "The first tea is picked before the dew has dried", "The first poem is written before the ink has cooled"),
    ("Bulan di danau tak bisa ditangkap dengan jala", "Rindu di dada tak bisa dibuang dengan dayung",
     "The moon in the lake cannot be caught with a net", "The longing in the heart cannot be rowed away"),
    ("Jembatan lengkung bundar memeluk air yang lewat", "Jembatan lengkung persegi menjaga sudut yang sepi",
     "The round-arched bridge embraces the passing water", "The square-arched bridge guards the quiet corner"),
    ("Teratai tumbuh dari lumpur tanpa membawa nodanya", "Sastrawan hidup di pasar tanpa menjual hatinya",
     "The lotus grows from the mud without carrying its stain", "The scholar lives in the market without selling his heart"),
    ("Angin musim semi membuka seribu pintu belakang", "Air musim semi membawa seribu perahu pulang",
     "The spring wind opens a thousand back doors", "The spring water carries a thousand boats home"),
    ("Lentera di buritan menerangi satu kanal", "Kebajikan di hati menerangi seribu jalan",
     "A lantern at the stern lights a single canal", "Virtue in the heart lights a thousand roads"),
    ("Bambu merunduk ketika angin bertiup kencang", "Bambu berdiri lagi ketika angin telah reda",
     "The bamboo bows when the wind blows hard", "The bamboo stands again once the wind is still"),
    ("Batu danau berlubang oleh seribu tahun ombak", "Hati sastrawan melunak oleh seribu malam hujan",
     "The lake rock is hollowed by a thousand years of waves", "The scholar's heart is softened by a thousand nights of rain"),
    ("Gerbang bulan membingkai taman di baliknya", "Jendela kisi menyaring cahaya di dalamnya",
     "The moon gate frames the garden beyond it", "The lattice window sifts the light within it"),
    ("Pendayung menyanyi supaya dayungnya tak lelah", "Penenun bersenandung supaya benangnya tak kusut",
     "The boatman sings so that his oar does not tire", "The weaver hums so that her thread does not tangle"),
    ("Secangkir teh mendingin menunggu tamu yang terlambat", "Sepucuk surat menguning menunggu balasan yang tak datang",
     "A cup of tea grows cold waiting for a late guest", "A letter grows yellow waiting for a reply that never comes"),
    ("Bangau berdiri satu kaki di kolam berliku", "Kura-kura bermimpi seribu tahun di bawah teratai",
     "The crane stands on one leg in the winding pond", "The tortoise dreams a thousand years beneath the lotus"),
    ("Pasar pagi ramai di atas perahu-perahu", "Danau malam sunyi di bawah lentera-lentera",
     "The morning market bustles on top of the boats", "The night lake lies hushed beneath the lanterns"),
    ("Air mengalir ke timur dan tak pernah kembali", "Waktu berlalu ke belakang dan tak bisa dipanggil",
     "Water flows east and never comes back", "Time slips behind us and cannot be called back"),
    ("Sutra yang halus lahir dari daun yang sederhana", "Pendekar yang agung lahir dari latihan yang sederhana",
     "Fine silk is born of a simple leaf", "A great cultivator is born of simple practice"),
    ("Kabut pagi menyembunyikan jembatan dari mata", "Senja merah mengembalikan jembatan kepada air",
     "Morning mist hides the bridge from the eye", "The red dusk gives the bridge back to the water"),
    ("Pohon murbei memberi daun tanpa bertanya untuk siapa", "Mata air memberi minum tanpa bertanya siapa yang haus",
     "The mulberry gives its leaves without asking for whom", "The spring gives water without asking who thirsts"),
    ("Satu genderang memberi irama bagi dua puluh dayung", "Satu tekad memberi arah bagi seribu langkah",
     "One drum gives the beat to twenty oars", "One resolve gives the way to a thousand steps"),
    ("Di jendela kanal seorang ibu menunggu perahu", "Di ujung jembatan seorang anak menunggu ayahnya",
     "At the canal window a mother waits for a boat", "At the end of the bridge a child waits for a father"),
    ("Tinta hitam di atas kertas putih menjadi puisi", "Benang merah di atas sutra putih menjadi sungai",
     "Black ink on white paper becomes a poem", "Red thread on white silk becomes a river"),
    ("Teh yang pahit menjadi manis di lidah yang sabar", "Hari yang berat menjadi ringan di hati yang tenang",
     "Bitter tea turns sweet on a patient tongue", "A heavy day turns light in a quiet heart"),
    ("Katak di kolam memanggil hujan musim panas", "Jangkrik di rumput mengantar bulan musim gugur",
     "Frogs in the pond call the summer rain", "Crickets in the grass escort the autumn moon"),
    ("Perahu kecil tak takut pada jembatan yang rendah", "Orang rendah hati tak takut pada pintu yang sempit",
     "A small boat fears no low bridge", "A humble soul fears no narrow door"),
    ("Genteng hitam menampung salju di musim dingin", "Dinding putih memantulkan bulan di malam purnama",
     "Black tiles hold the snow in winter", "White walls mirror the moon on full-moon nights"),
    ("Pemetik teh pulang dengan keranjang yang harum", "Penjala ikan pulang dengan jala yang berkilau",
     "The tea picker goes home with a fragrant basket", "The fisher goes home with a glittering net"),
    ("Arus yang deras tak bisa menghanyutkan batu yang dalam", "Gunjingan yang ramai tak bisa menggoyahkan hati yang lurus",
     "A swift current cannot carry off a deep stone", "Loud gossip cannot shake an upright heart"),
    ("Lentera teratai berlayar membawa doa yang lirih", "Asap dupa naik membawa nama yang dikenang",
     "The lotus lantern sails carrying a soft prayer", "The incense smoke rises carrying a remembered name"),
    ("Pagi membuka pintu depan ke jalan yang ramai", "Malam membuka pintu belakang ke air yang tenang",
     "Morning opens the front door to the busy street", "Night opens the back door to the quiet water"),
    # D-TK1 detail pass: eighteen more couplets (31-48)
    ("Ulat sutra tidur tiga kali sebelum berpintal", "Kultivator bertapa tiga musim sebelum menembus",
     "The silkworm sleeps three times before it spins", "The cultivator sits three seasons before breaking through"),
    ("Daun teh muda dipetik sebelum embun kering", "Kata yang baik diucapkan sebelum amarah datang",
     "Young tea leaves are picked before the dew dries", "Kind words are spoken before anger arrives"),
    ("Jembatan bundar memantulkan bulan yang utuh", "Jembatan persegi membingkai langit yang retak",
     "The round bridge reflects a whole moon", "The square bridge frames a broken sky"),
    ("Hujan musim semi menulis lingkaran di kanal", "Angin musim gugur menghapus bayangan di danau",
     "Spring rain writes circles on the canal", "Autumn wind erases the shadows on the lake"),
    ("Yuloh bergoyang ke kiri dan perahu melaju lurus", "Hati bergoyang ke sana sini dan jalan tetap satu",
     "The scull sways left and the boat runs straight", "The heart sways to and fro and the road stays one"),
    ("Penenun menghitung benang dalam gelap", "Pendayung menghitung jembatan dalam kabut",
     "The weaver counts her threads in the dark", "The rower counts his bridges in the fog"),
    ("Pinus dalam pot bermimpi tentang gunung", "Ikan dalam kolam bermimpi tentang lautan",
     "The potted pine dreams of mountains", "The pond fish dreams of the sea"),
    ("Gunungan batu berlubang menampung angin", "Paviliun kosong di tepi air menampung bulan",
     "The hollow rock garden holds the wind", "The empty pavilion by the water holds the moon"),
    ("Lonceng kuil berbunyi sekali di ujung kanal", "Seribu jendela menyala serentak di tepi air",
     "The temple bell sounds once at the canal's end", "A thousand windows light at once along the water"),
    ("Cawan pertama untuk tamu dan cawan kedua untuk sahabat", "Cawan ketiga untuk bulan yang duduk di antara kita",
     "The first cup for the guest and the second for the friend", "The third cup for the moon that sits between us"),
    ("Kain yang belum selesai menunggu tangan yang setia", "Lagu yang belum selesai menunggu suara yang kembali",
     "Unfinished cloth waits for a faithful hand", "An unfinished song waits for a voice that returns"),
    ("Anak-anak memancing bayangan dari atas jembatan", "Orang tua memancing kenangan dari tepi kanal",
     "Children fish for shadows from the bridge", "Old folk fish for memories from the canal's edge"),
    ("Pintu Air ditutup ketika lonceng malam berbunyi", "Pintu hati dibuka ketika teh pertama dituang",
     "The Water Gate closes when the night bell rings", "The heart's door opens when the first tea is poured"),
    ("Payung kertas mekar di jembatan saat hujan", "Lentera merah mekar di perahu saat senja",
     "Paper umbrellas bloom on the bridge in the rain", "Red lanterns bloom on the boats at dusk"),
    ("Benang yang kusut diurai dengan jari yang sabar", "Simpul Qi yang beku dicairkan dengan napas yang dalam",
     "A tangled thread is loosened by patient fingers", "A frozen knot of Qi is thawed by a deep breath"),
    ("Kanal lurus membawa barang ke pasar", "Kanal berliku membawa cerita ke rumah-rumah",
     "The straight canal carries goods to the market", "The winding canal carries stories to the houses"),
    ("Burung layang-layang membangun sarang di bawah genteng hitam", "Lumut hijau menulis usia di atas tangga batu",
     "Swallows build their nests beneath the black tiles", "Green moss writes the years upon the stone steps"),
    ("Sehelai sutra menyimpan seribu malam tenun", "Secangkir teh menyimpan seribu pagi pemetik",
     "One bolt of silk keeps a thousand nights of weaving", "One cup of tea keeps a thousand mornings of picking"),
]
assert len(BAIT) == 48
for i, (a, b, ea, eb) in enumerate(BAIT, 1):
    k("bait.%d.a" % i, a, ea)
    k("bait.%d.b" % i, b, eb)
k("bait.tua.a", "Lentera buritan padam di malam pengantin", "The stern lantern went out on the wedding night")
k("bait.tua.b", "Benang merah di bawah air menunggu tangan yang menyambungnya", "The red thread beneath the water waits for the hand that ties it")
k("bait.judul", "Pasangan Bait", "Couplet Matching")
k("bait.salah", "Bukan itu. Baris yang serasi: \"%s\"", "Not that one. The matching line: \"%s\"")
k("bait.wen_salah", "Hmm. Indah, tetapi bukan pasangannya.", "Hmm. Lovely, but not its partner.")
k("bait.tua_ketemu", "Bait yang hilang ditemukan! Bawa bait yang utuh kepada Wen Shi.", "The lost couplet is found! Bring the whole couplet to Wen Shi.")
k("bait.wen_tua", "Itu dia... itu bait untuk cucuku.", "That is it... that is the couplet for my granddaughter.")
k("bait.benar", "Serasi! %s dari %s berturut-turut.", "A match! %s of %s in a row.")
k("bait.tiga_lagi", "Tiga berturut-turut! Hadiah pemahaman hari ini sudah kau terima; teruslah bermain demi keindahannya.",
  "Three in a row! You already had today's comprehension reward; keep playing for the beauty of it.")
k("bait.tiga", "Tiga bait serasi berturut-turut! Dao-mu menjadi lebih jernih: pemahaman +%s.",
  "Three matching couplets in a row! Your Dao grows clearer: comprehension +%s.")

# ------------------------------------------------------------------------------------------- boats
for h, (a, b) in {"pusat": ("Halte Pusat Galangan", "Boatyard Central Stop"), "gerbang_air": ("Halte Gerbang Air", "Water Gate Stop"),
                  "danau": ("Halte Danau Lentera", "Lantern Lake Stop"), "jembatan_kembar": ("Halte Jembatan Kembar", "Twin Bridges Stop"),
                  "rumah_sutra": ("Halte Rumah Sutra", "House of Su Stop")}.items():
    k("halte." + h, a, b)
k("pos.halte", "Perahu Pos berhenti: %s. Turun sekarang atau tetap duduk.", "The Post Boat stops: %s. Get off now or stay seated.")
k("pos.penuh", "Perahu Pos penuh. Tunggu perahu berikutnya.", "The Post Boat is full. Wait for the next one.")
k("pos.naik", "Kau naik Perahu Pos. Turun di halte mana pun dengan tombol menyelinap.",
  "You board the Post Boat. Get off at any stop with the sneak key.")
k("lapak.sapa", "Ikan danau! Tahu bau wangi! Kue beras masih hangat!", "Lake fish! Fragrant tofu! Rice cakes still warm!")
k("perahu.habis", "Semua perahu sedang dipakai. Coba sebentar lagi.", "Every boat is out. Try again in a little while.")
k("tur.sudah", "Kau sedang mengikuti tur.", "You are already on a tour.")
k("tur.mulai", "Tur Kanal dimulai. Duduk saja, Bibi Chuan yang mendayung.", "The Canal Tour begins. Just sit, Auntie Chuan rows.")
k("tur.sapa", "Pegangan yang erat, kita lewat bawah jembatan rendah!", "Hold on tight, we are going under low bridges!")
k("tur.pemandu", "%s: %s", "%s: %s")
TUR = {
    "dermaga": (("Ini Galangan Perahu. Di rumah perahu itu perahu-perahu terbalik di atas kuda-kuda, menunggu didempul. Dua perahu naga "
                 "tidur di rumahnya sendiri: kepala naganya diukir dari satu batang kamper.",
                 "This is the Boatyard. In that shed the boats lie upside down on trestles waiting to be caulked. Two dragon boats sleep in "
                 "their own house: their dragon heads are carved from a single camphor trunk."),
                ("Galangan! Ciuman pertamaku di sini, di belakang perahu nomor tiga.", "The boatyard! My first kiss was here, behind boat number three.")),
    "pintu_air": (("Pintu Air. Daun kayunya turun setiap pukul sembilan malam dan naik pukul lima pagi. Petugas di menara memeriksa setiap "
                   "peti yang masuk: penyelundup takut pada anjing-anjingnya.",
                   "The Water Gate. Its wooden leaf comes down at nine every night and rises at five in the morning. The clerks in the "
                   "tower check every crate that comes in: smugglers fear their dogs."),
                  ("Pintu Air! Jangan sampai terkunci di luar.", "The Water Gate! Don't get locked outside.")),
    "jembatan_kembar": (("Jembatan Kembar. Yang satu lengkung bundar, yang satu lengkung persegi, bertemu di sudut dua kanal seperti kunci "
                         "dan anak kunci. Pelukis dari ibu kota menggambarnya dan menyebutnya Kenangan Kampung Halaman.",
                         "The Twin Bridges. One round-arched, one square-arched, meeting at the corner of two canals like a key and its "
                         "lock. A painter from the capital drew them and called it Memory of the Hometown."),
                        ("Jembatan Kembar, lihat lengkungnya!", "The Twin Bridges, look at the arches!")),
    "taman_sastra": (("Taman Sang Sastrawan. Di balik tembok putih itu ada kolam berliku, gunungan batu berlubang dan dua paviliun. Sastrawan "
                      "Wen Shi menjaganya. Ia sudah tua sekali, lebih tua dari jembatan termuda kita.",
                      "The Scholar's Garden. Behind that white wall lie a winding pond, hollow rock hills and two pavilions. Scholar Wen "
                      "Shi keeps it. He is very old, older than our youngest bridge."),
                     ("Taman sang sastrawan. Diam sedikit, jangan ganggu puisinya.", "The scholar's garden. Hush a little, don't disturb his poems.")),
    "rumah_sutra": (("Rumah Sutra Su, lima generasi. Di halaman pertama ulat sutra makan daun murbei di rak bertingkat, di aula tenun enam alat "
                     "tenun bernyanyi sepanjang hari. Yang merah di ujung tidak pernah dipakai. Jangan tanya kenapa. Tanyakan saja.",
                     "The House of Su Silk, five generations. In the first courtyard silkworms eat mulberry leaves on tiered racks; in the "
                     "weaving hall six looms sing all day. The red one at the end is never used. Don't ask why. Well, ask."),
                    ("Rumah Su. Dengar alat tenunnya?", "The House of Su. Hear the looms?")),
    "pasar_air": (("Pasar Air Pagi. Sebelum matahari, perahu-perahu tertambat jadi kios: ikan danau, tahu, kue beras, payung kertas. Di malam "
                   "hari tempat ini sunyi, terlalu sunyi untuk seleraku.",
                   "The Morning Water Market. Before sunrise the boats moor and become stalls: lake fish, tofu, rice cakes, paper "
                   "umbrellas. At night this place is quiet, too quiet for my taste."),
                  ("Pasar air! Pagi-pagi ramai sekali di sini.", "The water market! So busy here in the mornings.")),
    "panggung_air": (("Panggung Air di tengah Danau Lentera. Pada Malam Lentera Air penyanyi berdiri di sana dan seluruh kota melepas lentera "
                      "teratai. Pagoda kecil di pulau itu untuk Dewa Air; kuilnya di tepi danau, kolamnya diberkati.",
                      "The Water Stage in the middle of Lantern Lake. On the Night of Water Lanterns a singer stands there and the whole "
                      "town releases lotus lanterns. The little pagoda on the island is for the Water God; his shrine is on the shore, "
                      "and its basin is blessed."),
                     ("Panggung Air. Malam bulan mati nanti, kau harus lihat!", "The Water Stage. On the next new moon, you must see it!")),
}
for place, (panjang, singkat) in TUR.items():
    k("tur.tempat." + place, *panjang)
    k("tur.tempat." + place + ".singkat", *singkat)
k("tur.selesai", "Tur selesai. Bibi Chuan menambatkan perahu dan mengulurkan tangan untuk tip yang tidak wajib.",
  "The tour is over. Auntie Chuan moors the boat and holds out a hand for a tip that is not required.")
k("tur.paham", "Kau mengenal kota ini lebih baik: pemahaman +%s.", "You know this town better now: comprehension +%s.")
k("sewa.sudah", "Kau sudah menyewa perahu.", "You already hired a boat.")
k("sewa.jauh", "Perahu sewaan hanya ada di tambatan galangan. Mendekatlah.", "Hire boats are only at the boatyard moorings. Come closer.")
k("sewa.mulai", "Perahu sewaan siap: %s per jam dunia. Dayung sendiri, perahu hanya melaju di air.",
  "Your hired boat is ready: %s per world hour. Row it yourself; it only moves on water.")
k("sewa.kembali", "Perahu yang kau tinggalkan didayung pulang ke tambatan.", "The boat you left is rowed back to its mooring.")
k("sewa.bayar", "Sewa perahu satu jam lagi: %s.", "Boat hire, one more hour: %s.")
k("sewa.habis", "Uangmu habis. Perahu sewaan kembali ke galangan.", "You ran out of money. The hired boat returns to the boatyard.")

# ------------------------------------------------------------------------------------------- water rats
k("tikus.nama", "Tikus Air", "Water Rat")
k("tikus.teriak", "Pencuri! Pencuri di kanal!", "Thief! A thief on the canal!")
k("tikus.lao_shu", "Itu Lao Shu si Tikus Tua sendiri, merampok %s! Kejar sampan hitamnya!", "It's Lao Shu the Old Rat himself, robbing %s! Chase his black skiff!")
k("tikus.rampok", "Tikus Air merampok %s! Kejar sampan hitamnya dan pukul untuk menghentikannya.",
  "The Water Rats are robbing %s! Chase the black skiff and strike it to stop it.")
k("tikus.mangsa.lapak", "sebuah perahu-lapak", "a stall boat")
k("tikus.mangsa.sutra", "Rumah Sutra Su", "the House of Su Silk")
k("tikus.lao_shu_menyerah", "Ampun! Ampun! Tikus tua ini menyerah! Jangan pukul lagi, aku akan bicara!",
  "Mercy! Mercy! This old rat gives up! No more blows, I'll talk!")
k("tikus.menyerah", "Ampun, ampun! Ambil saja barangnya!", "Mercy, mercy! Take the goods back!")
k("tikus.tangkap", "Pencuri tertangkap dan barangnya kembali ke pemiliknya. Hadiah dari para pedagang: %s.",
  "The thief is caught and the goods return to their owner. A reward from the traders: %s.")
k("tikus.lolos", "Sampan hitam menghilang di kanal gelap. Tikus Air lolos malam ini.", "The black skiff vanishes into the dark canals. The Water Rats got away tonight.")

# ====================================================================================================== advancements
ADV = {
    "tur": ("minecraft:oak_boat", "task", ("Seribu Jembatan, Satu Lagu", "A Thousand Bridges, One Song"),
            ("Ikuti Tur Kanal Bibi Chuan sampai selesai.", "Finish Auntie Chuan's Canal Tour.")),
    "jubah": ("kultivator:tema/jubah_sutra", "task", ("Sehalus Kabut Pagi", "Fine as Morning Mist"),
              ("Ambil jubah sutra pesananmu dari Rumah Sutra Su.", "Collect your tailored silk robe from the House of Su Silk.")),
    "tikus": ("minecraft:lead", "task", ("Tikus di Lumbung", "A Rat in the Granary"),
              ("Hentikan sampan hitam Tikus Air.", "Stop the Water Rats' black skiff.")),
    "anak": ("minecraft:water_bucket", "task", ("Tangan di Air Deras", "A Hand in the Rushing Water"),
             ("Selamatkan anak yang jatuh ke kanal saat hujan deras.", "Save the child who fell into the canal in heavy rain.")),
    "perahu_naga": ("kultivator:tema/pita_naga", "goal", ("Jantung Naga", "Dragon's Heart"),
                    ("Jadilah penabuh genderang perahu juara Lomba Perahu Naga.", "Drum the winning boat of the Dragon Boat Race.")),
    "teh": ("kultivator:tema/teh_bi_luo", "task", ("Teh yang Jujur", "Honest Tea"),
            ("Petik teh pertamamu bersama Lu Yuqing.", "Pick your first tea with Lu Yuqing.")),
    "bait": ("minecraft:writable_book", "task", ("Baris yang Hilang", "The Missing Line"),
             ("Temukan bait terakhir Sastrawan Wen Shi.", "Find Scholar Wen Shi's last couplet.")),
}


def advancements():
    for name, (icon, frame, title, desc) in ADV.items():
        full = "tema_air_" + name
        tulis("advancement/tempat/%s.json" % full, {
            "parent": "kultivator:tempat/tema_air",
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": {"description": {"translate": "advancement.kultivator.tempat.%s.description" % full},
                        "icon": {"id": icon},
                        "title": {"translate": "advancement.kultivator.tempat.%s.title" % full},
                        "frame": frame},
            "requirements": [["done"]]})
        t("advancement.kultivator.tempat.%s.title" % full, *title)
        t("advancement.kultivator.tempat.%s.description" % full, *desc)


# ====================================================================================================== schedule + demand
def E(kegiatan, tujuan, klip, **kw):
    o = {"kegiatan": kegiatan, "tujuan": tujuan, "klip": klip}
    o.update(kw)
    return o


JADWAL = {
    "_catatan": "DESIGN 38.T.5.3, module 38TK-A: hours the built-in JadwalTema leaves to the theme (Madam Su's day, the boat songs at "
                "noon, stern lanterns at dusk, the silkworm keepers' evening round) and the residents' side of the lantern night and "
                "the dragon boat race that the built-in table does not name. Hours are clock hours (06:00 = tick 0).",
    "jenis": "kota_tema/air",
    "peran": {
        "nyonya_sutra": {
            "5": E("periksa_ulat", "kerja:rak", "lihat_barang"),
            "6-9": E("hitung_sutra", "kerja:timbangan", "hitung_sempoa", kanan="sempoa"),
            "10-13": E("pajang_kain", "lapak", "tawar"),
            "14-17": E("surat_dagang", "meja_tulis", "menulis", kanan="kuas"),
            "18-20": E("teh_sore", "kursi", "minum_teh", kanan="cangkir"),
        },
        "pendayung": {
            "10-11": E("dayung_lagu", "jalur_perahu", "dayung_yuloh", kanan="dayung_yuloh"),
            "12-13": E("nyanyi_perahu", "jalur_perahu", "nyanyi_perahu", kanan="dayung_yuloh"),
            "19-21": E("lentera_buritan", "tambatan", "ikat_tali", kanan="lentera_tangan"),
        },
        "pemelihara_ulat": {
            "18-19": E("bersih_nampan", "kerja:rak", "sapu", kanan="sapu"),
        },
    },
    "acara": {
        "tema.lentera_air": {
            "sastrawan": E("puisi_lentera", "pandang", "menulis", kanan="kuas"),
            "nyonya_sutra": E("doa_lentera", "dupa", "sujud_dupa", kanan="dupa"),
            "ahli_teh": E("teh_lentera", "kerja:teko", "tuang_teh", kanan="teko"),
        },
        "tema.perahu_naga": {
            "anak": E("sorak_anak", "penonton", "main_lompat"),
            "sastrawan": E("syair_lomba", "penonton", "kipas", kanan="kipas_lipat"),
        },
    },
}

PERMINTAAN = {
    "_catatan": "Kota Air (DESIGN 38.T.10, module 38TK-A): sutra, teh dan ikan kanal melimpah (murah); pewarna, tinta, kertas dan besi "
                "untuk alat tenun dicari; Air Suci dari kota oasis dibayar mahal. Barang dagang antar-kota memakai barang.json.",
    "*": 1.0,
    "minecraft:string": 0.75,
    "minecraft:white_wool": 0.85,
    "minecraft:cod": 0.85,
    "minecraft:salmon": 0.85,
    "minecraft:bamboo": 0.85,
    "minecraft:lily_pad": 0.8,
    "minecraft:red_dye": 1.3,
    "minecraft:blue_dye": 1.25,
    "minecraft:yellow_dye": 1.2,
    "minecraft:green_dye": 1.2,
    "minecraft:ink_sac": 1.2,
    "minecraft:paper": 1.15,
    "minecraft:book": 1.15,
    "minecraft:iron_ingot": 1.15,
    "minecraft:copper_ingot": 1.1,
    "minecraft:wheat": 1.05,
    "kultivator:jade_slip": 1.2,
    "kultivator:qi_gathering_pill": 1.1,
    "kultivator:tema/air_suci": 1.3,
    "kultivator:tema/ikan_roh": 0.9,
    "kultivator:tema/teh_bi_luo": 0.75,
    "kultivator:tema/teh_embun_gunung": 0.8,
    "kultivator:tema/daun_teh_embun": 0.8,
    "kultivator:tema/kipas_sutra": 0.75,
}


# ====================================================================================================== write + check
def kunci_java():
    """Every literal kultivator.kota_tema.air.* key of the Java code plus the dynamic families."""
    src = os.path.join(REPO, "src/main/java/dev/kultivator/dunia/tempat/tema/air")
    keys = set()
    for name in os.listdir(src):
        text = open(os.path.join(src, name), encoding="utf-8").read()
        for m in re.finditer(r'"(kultivator\.kota_tema\.air\.[a-z0-9_.]*[a-z0-9_])"', text):
            keys.add(m.group(1))
    dyn = []
    dyn += [K + "tokoh." + i for i in TOKOH] + [K + "tokoh." + i + ".gelar" for i in TOKOH]
    dyn += [K + "lagu.%d" % i for i in range(1, 7)]
    dyn += [K + "naga.perahu.%d" % i for i in range(4)]
    dyn += [K + "motif." + m for m in ("awan", "bangau", "teratai", "naga", "benang_merah")]
    dyn += [K + "teh.aktif.%d" % i for i in (1, 2, 3)] + [K + "teh.efek." + i for i in ("bi_luo", "embun_gunung", "seribu_tahun")]
    dyn += [K + "bait.%s.%s" % (i, s) for i in list(range(1, 49)) + ["tua"] for s in ("a", "b")]
    dyn += [K + "halte." + h for h in ("pusat", "gerbang_air", "danau", "jembatan_kembar", "rumah_sutra")]
    dyn += [K + "tur.tempat." + p + s for p in TUR for s in ("", ".singkat")]
    keys.update(dyn)
    # prefixes that are only stems in the code
    return {x for x in keys if not x.endswith(".") and x not in (K + "tokoh", K + "bait")}


def main():
    advancements()
    for did, obj in DIALOGS:
        tulis("tempat/dialog/%s.json" % did, obj)
    for qid, obj in QUESTS:
        tulis("tempat/quest/%s.json" % qid, obj)
    tulis("tempat/kabar/tema_air.json", {"templat": KABAR})
    tulis("tempat/jadwal/kota_tema_air.json", JADWAL)
    tulis("tempat/permintaan/kota_tema_air.json", PERMINTAAN)
    missing = sorted(x for x in kunci_java() if x not in L)
    if missing:
        raise SystemExit("missing lang keys:\n  " + "\n  ".join(missing))
    # dialogs/quests referenced from the code must exist
    for q in ("tema_air_benang", "tema_air_anak_jembatan", "tema_air_bait_tua"):
        assert any(qid == q for qid, _ in QUESTS), q
    # clash check against every other fragment
    lang = os.path.join(REPO, "lang-src")
    for name in sorted(os.listdir(lang)):
        if name.startswith("tempat_tema_air.") or not name.endswith(".json"):
            continue
        other = json.load(open(os.path.join(lang, name), encoding="utf-8"))
        clash = sorted(set(other) & set(L))
        if clash:
            raise SystemExit("keys clash with %s: %s" % (name, clash[:10]))
    for code, ix in (("id_id", 0), ("en_us", 1)):
        out = {key: L[key][ix] for key in sorted(L)}
        with open(os.path.join(lang, "tempat_tema_air.%s.json" % code), "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("dialogs %d, quests %d, kabar %d, adv %d, lang keys %d" % (len(DIALOGS), len(QUESTS), len(KABAR), len(ADV), len(L)))


main()
