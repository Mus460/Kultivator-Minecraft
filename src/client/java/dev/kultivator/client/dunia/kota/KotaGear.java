package dev.kultivator.client.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.client.sosok.busana.SosokGear;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The gear of module K's people (DESIGN 34.5.1-34.5.4, detail pass): cuboid models on the player skeleton (head, body,
 * arms), so every piece nods, walks, bows and duels with the body through the PlayerModel animation, the meditation seat
 * and the cast/duel poses. One outfit per role (four for the townsfolk, two for the wanderers, a leader's for the rival
 * groups):
 * <ul>
 * <li>Juru Lelang: the black futou with its stiff side feet, a sash, a jade pendant and a folded fan at the waist (gold
 * futou for the Balai Lelang Abadi).</li>
 * <li>Pemilik Teh: a hair bun with a wooden pin, an apron, a towel over the shoulder.</li>
 * <li>Makelar Gelap: a hood with a face veil, a gold earring, a mantle and a dagger at the belt.</li>
 * <li>Pedagang Pil: a merchant cap with a gold knob, a pill gourd and a purse (gold cap and abacus in the Pasar Abadi).</li>
 * <li>Pandai Pusaka: a crimson headband, a leather apron and bracers.</li>
 * <li>Penjaga Kota: a steel helmet with gold crest and crimson plume, cheek and neck guards, a lamellar cuirass with its
 * heart-protecting mirror, a tabard in the town's colour and pauldrons.</li>
 * <li>Warga: a straw sun hat, a headscarf, a bun with a jade pin, or a topknot with a white cloth; a sash.</li>
 * <li>Pengembara: the bamboo douli with gauze veils, a cloak, a sword in its sheath at the left hip and a wine gourd; or
 * a topknot under a jade guan with ribbons and a sword across the back.</li>
 * <li>Saingan: the group's headband with flying tails, a hip sword, a leather shoulder guard; the leader wears a gold guan
 * and a cloak.</li>
 * <li>Abadi: a jade guan with a long gold pin, long hair and the floating pibo shawl.</li>
 * <li>Penceramah: a tall scholar's hat, a long white beard and brows, a horsetail whisk.</li>
 * <li>Utusan: the winged official's hat (wushamao) with gold wings and a jade belt.</li>
 * <li>Penjaga Kebun: a crown of peach blossoms and a basket of peaches.</li>
 * </ul>
 * Each outfit has a fixed-colour mesh and a cloth mesh tinted per NPC ({@link #tint}). The sheathed sword's hilt is a part
 * named {@link #HILT} that the layer hides while the blade is drawn. One texture serves all ({@link #TEXTURE}, 64 × 2048):
 * sixty-four material bands of 32 px; every cube's unwrap fits inside its band. Bands 0-15 come from
 * {@code tools/gen_dunia_kota_gear.py}; bands 16-47 (DESIGN 38.O.1 and 38.T.8.2) and 48-63 (DESIGN 42.12.4) from
 * {@code tools/gen_tempat_gear.py}, which copies the bands already in the atlas pixel for pixel.
 *
 * <p>Kinds 21-60 dress the DESIGN 38 roles: one fixed headwear/accessory outfit per role (headwear column of DESIGN
 * 40.4.1). Body garments and hair of those roles are Sosok's (DESIGN 40.7.4), so these kinds carry no robe, apron or
 * hair parts; each is registered with {@code SosokGear.daftarTopi} (C40.K2) so a covering hat hides the Sosok hair under
 * it. Other client modules may choose a role's outfit with {@link #timpa}.
 *
 * <p>Kinds 61-79 (DESIGN 42.12.4, Kota yang Hidup, 42V-B): the thirteen new town roles' headwear and tools, and the
 * outfits of a state the server sets as a look key ({@code SosokApi.setRupa}, synced on the NPC): {@code <key>.pengantin}
 * (the groom's red official hat and silk flower, the bride's phoenix crown with pearl tassels and red veil),
 * {@code <key>.berkabung} (the white mourning cap and hemp belt), {@code <key>.dingin} (the winter fur hat and padded
 * scarf), {@code akrobat} / {@code akrobat.egrang} (the troupe's headband, on stilts), {@code penari} (water sleeves),
 * {@code topeng_kertas} (the Ghost Market's paper mask), {@code penjaga_malam} and {@code pencopet} ({@link #kindOf}).
 * The garments themselves are Sosok styles ({@code PENGANTIN_L/P}, {@code BERKABUNG}, {@code MANTEL_KAPAS},
 * {@code AKROBAT}, {@code PENARI}, {@code PENCOPET} in {@code resep.json}).
 */
public final class KotaGear {

	public static final Identifier TEXTURE = Kultivator.id("textures/dunia/kota/entity/perlengkapan.png");
	/** The sheathed sword's hilt (hidden while the blade is in hand). */
	public static final String HILT = "hilt";

	public static final int NONE = 0;
	public static final int FUTOU = 1;
	public static final int FUTOU_GOLD = 2;
	public static final int TEA = 3;
	public static final int BROKER = 4;
	public static final int MERCHANT = 5;
	public static final int MERCHANT_GOLD = 6;
	public static final int SMITH = 7;
	public static final int GUARD = 8;
	public static final int FOLK_HAT = 9;
	public static final int FOLK_SCARF = 10;
	public static final int FOLK_BUN = 11;
	public static final int FOLK_KNOT = 12;
	public static final int WANDERER_DOULI = 13;
	public static final int WANDERER_GUAN = 14;
	public static final int RIVAL = 15;
	public static final int RIVAL_LEADER = 16;
	public static final int IMMORTAL = 17;
	public static final int SAGE = 18;
	public static final int ENVOY = 19;
	public static final int GARDEN = 20;

	// ---------------------------------------------------------------- DESIGN 38.O.1 (capital, guilds, villages; 38MD)

	/** {@code mahkota_guan_giok}: jade guan crown with a gold pin and crimson ribbons (Pangeran Kota, Patriark Klan). */
	public static final int GUAN_GIOK = 21;
	/** {@code topi_pejabat}: black lacquered-gauze official hat with stiff paddle wings (Menteri, Hakim, Petugas Bea). */
	public static final int PEJABAT = 22;
	/** {@code helm_berumbai}: red-laced lamellar helm with a horsehair plume (Pengawal Istana). */
	public static final int HELM_BERUMBAI = 23;
	/** {@code helm_berumbai} + {@code perisai_bundar}: the gate guard's helm and round painted shield (Penjaga Gerbang). */
	public static final int HELM_PERISAI = 24;
	/** {@code helm_kapten}: gilded captain's helm with flame wings and a tall plume (Kapten Penjaga). */
	public static final int HELM_KAPTEN = 25;
	/** {@code topi_bulat_pedagang}: round coin-brocade cap with a black brim; a coin purse (Bankir, Pemilik Penginapan). */
	public static final int TOPI_BULAT = 26;
	/** {@code sarung_tangan_empu}: scorched smith's gauntlets, a crimson headband, tongs at the belt (Empu). */
	public static final int SARUNG_EMPU = 27;
	/** {@code kacamata_batu} + {@code kain_kepala}: quartz goggles over a head cloth (Pemotong Batu). */
	public static final int KACAMATA = 28;
	/** {@code topi_alkemis}: tiered purple cloud-silk cone with a gold ball; a pill gourd (Alkemis). */
	public static final int TOPI_ALKEMIS = 29;
	/** {@code topi_sarjana}: scholar's cap with a mortar board and trailing ribbons (Pustakawan). */
	public static final int TOPI_SARJANA = 30;
	/** {@code topi_sarjana} + {@code kipas_lipat_pinggang} (Penceramah Fana). */
	public static final int SARJANA_KIPAS = 31;
	/** {@code topi_nakhoda}: wide tarred-straw captain's hat with chin cords (Nakhoda Langit). */
	public static final int TOPI_NAKHODA = 32;
	/** {@code kain_kepala}: a knotted head cloth with two tails (Kuli, Penjaga Suar, Penjual Ikan, Penjaga Lift, Kuli Gunung). */
	public static final int KAIN_KEPALA = 33;
	/** {@code kain_kepala} + {@code sabuk_alat}: tool belt with pouches, hammer and trowel (Tukang). */
	public static final int TUKANG = 34;
	/** {@code topi_koki}: puffed linen chef's cap; a towel on the shoulder (Koki). */
	public static final int TOPI_KOKI = 35;
	/** {@code tasbih}: ten sandalwood beads hanging over the chest with a guru bead and tassel (Rahib, Pertapa, Biksu). */
	public static final int TASBIH = 36;
	/** {@code caping}: three-tier conical straw hat with a chin cord (Petani, Nelayan, Kepala Desa, ...). */
	public static final int CAPING = 37;
	/** {@code keranjang_punggung}: wicker back basket full of goods (Pedagang Keliling). */
	public static final int KERANJANG = 38;
	/** {@code topi_bulu}: fur hat with ear flaps and a tail (Pedagang Binatang). */
	public static final int TOPI_BULU = 39;
	/** {@code kipas_lipat_pinggang}: folding fan tucked in a gold-plaqued belt, a jade pendant (Tuan Muda). */
	public static final int KIPAS_PINGGANG = 40;
	/** {@code lencana_bintang}: the guild master's large star badge on a gold cord (Ketua Gilda). */
	public static final int LENCANA_KETUA = 41;
	/** {@code lencana_bintang}: the clerk's star badge, a brush behind the ear, an ink pouch (Petugas Gilda). */
	public static final int LENCANA_PETUGAS = 42;
	/** A great sword across the back and a dagger at the belt (Pedagang Senjata). */
	public static final int PEDANG_PUNGGUNG = 43;

	// ---------------------------------------------------------------- DESIGN 38.T.8.2 (themed cities; non-body items, 38MD)

	/** {@code topi_syahbandar}: navy official's hat with an up-curved front wing and a jade knob (Syahbandar). */
	public static final int TOPI_SYAHBANDAR = 44;
	/** {@code kalung_gigi_hiu}: seven shark teeth on a rope, a gold earring, a rope belt and knife (Pelaut). */
	public static final int KALUNG_HIU = 45;
	/** {@code serban_seberang}: white wound turban with a green stripe and a hanging end (Pedagang Seberang, Bazar). */
	public static final int SERBAN = 46;
	/** {@code mahkota_laut}: silver-blue crown of five wave petals and a pearl (Pendeta Laut). */
	public static final int MAHKOTA_LAUT = 47;
	/** {@code serban_emas}: gold brocade turban with a brooch and plume; a curved sword at the hip (Tuan Benteng). */
	public static final int SERBAN_EMAS = 48;
	/** {@code kerudung_pasir}: sand veil leaving only the eyes open, rope circlet, trailing tail (Penuntun Unta). */
	public static final int KERUDUNG_PASIR = 49;
	/** {@code topi_bulu_lebar}: broad fur-brimmed caravan hat with a brocade crown (Kafilah Bashi). */
	public static final int TOPI_BULU_LEBAR = 50;
	/** {@code helm_runcing}: spiked bronze helm with a nose guard and a mail curtain (Pengawal Gurun). */
	public static final int HELM_RUNCING = 51;
	/** {@code caping_biru}: indigo straw hat with a blue cloth curtain (Pendayung). */
	public static final int CAPING_BIRU = 52;
	/** {@code ikat_kepala_kuning}: yellow sutra headband with two tails (Peziarah). */
	public static final int IKAT_KUNING = 53;
	/** {@code tali_melingkar}: climbing rope across the chest and a coil at the hip (Pemetik Herba). */
	public static final int TALI_MELINGKAR = 54;
	/** {@code topeng_besi} + {@code rantai_bahu}: iron mask with eye holes and tusks, shoulder chain, iron pauldron (Penegak Iblis). */
	public static final int TOPENG_RANTAI = 55;
	/** {@code rantai_bahu}: shoulder chain, a butcher's hook at the belt (Jagal Arena). */
	public static final int RANTAI_BAHU = 56;
	/** {@code mahkota_tanduk}: blood-lacquer circlet with two three-segment horns (Penguasa Iblis). */
	public static final int MAHKOTA_TANDUK = 57;
	/** {@code cadar_ular}: dark gauze veil below the eyes and a rearing snake crown (the Kota Iblis broker, via {@link #timpa}). */
	public static final int CADAR_ULAR = 58;
	/** {@code penutup_mata}: rune-stitched blindfold with trailing ends (Penjaga Kitab Terlarang). */
	public static final int PENUTUP_MATA = 59;
	/** {@code tudung_pembunuh}: deep charcoal hood, mouth mask, shoulder cape, crossed knife sheaths (Pembunuh Bayaran). */
	public static final int TUDUNG = 60;

	// ---------------------------------------------------------------- DESIGN 42.12.4 (Kota yang Hidup, 42V-B)

	/** {@code pengantin_pria}: red official's hat with gold flower and paddle wings, the red silk ball flower on a sash. */
	public static final int TOPI_PENGANTIN = 61;
	/** {@code pengantin_wanita}: the phoenix crown (fengguan) of kingfisher enamel, three gold phoenixes, pearl tassels, red veil. */
	public static final int MAHKOTA_FENGGUAN = 62;
	/** {@code berkabung}: white mourning cap with a hemp band and tails, hemp rope belt, hemp armband. */
	public static final int TOPI_KABUNG = 63;
	/** {@code topi_bulu_dingin}: winter rabbit-fur hat with ear flaps, fur collar, quilted scarf and cuffs (mantel kapas). */
	public static final int TOPI_DINGIN = 64;
	/** {@code kostum_akrobat}: red-yellow headband with flying tails, sash, wrist and ankle wraps (Akrobat). */
	public static final int IKAT_AKROBAT = 65;
	/** {@code penari}: long water sleeves, the pibo shawl, a silk flower hairpin and a sash with tails (Penari). */
	public static final int SELENDANG_PENARI = 66;
	/** {@code topeng_kertas}: a painted paper opera mask on a cord, a green-jade charm (Ghost Market sellers). */
	public static final int TOPENG_KERTAS = 67;
	/** {@code topi_penjaga_malam}: oiled-cloth hat with a neck curtain, padded collar, belt with a bamboo whistle (Penjaga Malam). */
	public static final int CAPING_RONDA = 68;
	/** {@code egrang}: red-lacquered stilts 2 × 16 × 2 strapped under both legs, plus the acrobat's headband and sash. */
	public static final int EGRANG = 69;
	/** {@code pencopet}: dark patched headband, a cross strap with a small bag, a patched shoulder cloth (Pencopet). */
	public static final int IKAT_COPET = 70;
	/** A big red silk flower over the ear with a gold pin, a red fan at a brocade belt (Mak Comblang). */
	public static final int BUNGA_COMBLANG = 71;
	/** A tall black scholar's soft cap, a folding fan and the wooden clapper (xingmu) at the belt (Pendongeng). */
	public static final int TOPI_DONGENG = 72;
	/** A worn straw hat with a broken brim and an erhu slung across the back (Pengamen). */
	public static final int TOPI_PENGAMEN = 73;
	/** The medicine banner (药) carried on a pole over the back, a gourd and a plaster pouch (Penjual Obat). */
	public static final int PANJI_OBAT = 74;
	/** A square scholar's cap with ribbons, the teacher's ruler and a scroll at the belt (Guru Sekolah). */
	public static final int TOPI_GURU = 75;
	/** The funeral master's tall white cap, hemp sash, hand bell and paper money pouch (Pengurus Duka). */
	public static final int TOPI_DUKA = 76;
	/** A key ring and a bound ledger at a leather belt, sleeve guards (Penjaga Gudang). */
	public static final int KUNCI_GUDANG = 77;
	/** A black melon-rind skullcap with a red knot, an abacus and a coin purse at the belt (Pialang). */
	public static final int TOPI_PIALANG = 78;
	/** A red musician's cap with a gold band, a red sash and a small drum at the hip (Pemusik). */
	public static final int TOPI_PEMUSIK = 79;

	public static final int KINDS = 80;

	/** Debug/command names of the kinds ({@link #nama}), index = kind. */
	private static final String[] NAMA = {"none", "futou", "futou_gold", "tea", "broker", "merchant", "merchant_gold", "smith", "guard",
			"folk_hat", "folk_scarf", "folk_bun", "folk_knot", "wanderer_douli", "wanderer_guan", "rival", "rival_leader", "immortal", "sage",
			"envoy", "garden", "mahkota_guan_giok", "topi_pejabat", "helm_berumbai", "helm_perisai", "helm_kapten", "topi_bulat_pedagang",
			"sarung_tangan_empu", "kacamata_batu", "topi_alkemis", "topi_sarjana", "topi_sarjana_kipas", "topi_nakhoda", "kain_kepala",
			"sabuk_alat", "topi_koki", "tasbih", "caping", "keranjang_punggung", "topi_bulu", "kipas_lipat_pinggang", "lencana_ketua",
			"lencana_petugas", "pedang_punggung", "topi_syahbandar", "kalung_gigi_hiu", "serban_seberang", "mahkota_laut", "serban_emas",
			"kerudung_pasir", "topi_bulu_lebar", "helm_runcing", "caping_biru", "ikat_kepala_kuning", "tali_melingkar", "topeng_besi",
			"rantai_bahu", "mahkota_tanduk", "cadar_ular", "penutup_mata", "tudung_pembunuh", "topi_pengantin", "mahkota_fengguan",
			"topi_kabung", "topi_bulu_dingin", "ikat_akrobat", "selendang_penari", "topeng_kertas", "topi_penjaga_malam", "egrang",
			"ikat_pencopet", "bunga_comblang", "topi_pendongeng", "topi_pengamen", "panji_obat", "topi_guru", "topi_pengurus_duka",
			"kunci_gudang", "topi_pialang", "topi_pemusik"};

	/** Kinds that cover the head (Sosok hides its hair cubes under them, C40.K2). */
	private static final int[] MENUTUP = {PEJABAT, HELM_BERUMBAI, HELM_PERISAI, HELM_KAPTEN, TOPI_BULAT, KACAMATA, TOPI_ALKEMIS, TOPI_SARJANA,
			SARJANA_KIPAS, TOPI_NAKHODA, KAIN_KEPALA, TUKANG, TOPI_KOKI, CAPING, TOPI_BULU, TOPI_SYAHBANDAR, SERBAN, SERBAN_EMAS, KERUDUNG_PASIR,
			TOPI_BULU_LEBAR, HELM_RUNCING, CAPING_BIRU, MAHKOTA_TANDUK, TUDUNG, TOPI_PENGANTIN, MAHKOTA_FENGGUAN, TOPI_KABUNG, TOPI_DINGIN,
			CAPING_RONDA, TOPI_DONGENG, TOPI_PENGAMEN, TOPI_GURU, TOPI_DUKA, TOPI_PIALANG, TOPI_PEMUSIK};

	/** Per-role overrides registered by other client modules ({@link #timpa}); empty by default. */
	private static final Map<NpcRole, ToIntFunction<KultivatorNpc>> TIMPA = new EnumMap<>(NpcRole.class);

	public static final int BAND_BLACK = 0;
	public static final int BAND_GOLD = 1;
	public static final int BAND_JADE = 2;
	public static final int BAND_CRIMSON = 3;
	public static final int BAND_WHITE = 4;
	public static final int BAND_STRAW = 5;
	public static final int BAND_LEATHER = 6;
	public static final int BAND_STEEL = 7;
	public static final int BAND_WOOD = 8;
	public static final int BAND_CLOTH = 9;
	public static final int BAND_PEACH = 10;
	public static final int BAND_GOURD = 11;
	public static final int BAND_BRONZE = 12;
	public static final int BAND_PAPER = 13;
	public static final int BAND_HAIR = 14;
	public static final int BAND_GAUZE = 15;
	// bands 16-31 (DESIGN 38.O.1) and 32-47 (38.T.8.2), tools/gen_tempat_gear.py
	public static final int BAND_LACQUER_GAUZE = 16;
	public static final int BAND_LAMELLAR_RED = 17;
	public static final int BAND_SHIELD_HIDE = 18;
	public static final int BAND_COIN_BROCADE = 19;
	public static final int BAND_SCORCHED = 20;
	public static final int BAND_LENS = 21;
	public static final int BAND_PURPLE_SILK = 22;
	public static final int BAND_DARK_STRAW = 23;
	public static final int BAND_LINEN = 24;
	public static final int BAND_SANDALWOOD = 25;
	public static final int BAND_WICKER = 26;
	public static final int BAND_FUR = 27;
	public static final int BAND_STAR_GOLD = 28;
	public static final int BAND_SILVER = 29;
	public static final int BAND_BONE = 30;
	public static final int BAND_PLUME = 31;
	public static final int BAND_NAVY_GOLD = 32;
	public static final int BAND_TOOTH = 33;
	public static final int BAND_TURBAN = 34;
	public static final int BAND_SEA_SILVER = 35;
	public static final int BAND_GOLD_BROCADE = 36;
	public static final int BAND_SAND_CLOTH = 37;
	public static final int BAND_DESERT_BRONZE = 38;
	public static final int BAND_BLUE_STRAW = 39;
	public static final int BAND_SUTRA_CLOTH = 40;
	public static final int BAND_ROPE = 41;
	public static final int BAND_BLACK_IRON = 42;
	public static final int BAND_HORN = 43;
	public static final int BAND_SNAKE = 44;
	public static final int BAND_CHARCOAL = 45;
	public static final int BAND_RUNE_CLOTH = 46;
	public static final int BAND_BLOOD_LACQUER = 47;
	// bands 48-63 (DESIGN 42.12.4), tools/gen_tempat_gear.py
	public static final int BAND_PHOENIX_SILK = 48;
	public static final int BAND_DIANCUI = 49;
	public static final int BAND_PEARLS = 50;
	/** Red silk gauze with open mesh holes (drawn cutout: the face shows through the veil). */
	public static final int BAND_RED_GAUZE = 51;
	public static final int BAND_HEMP = 52;
	public static final int BAND_MOURNING = 53;
	public static final int BAND_WINTER_FUR = 54;
	public static final int BAND_QUILTED = 55;
	public static final int BAND_ACROBAT = 56;
	/** Paper; the painted mask face fills x 1-8, y 1-6: the north face of an 8 × 6 × 1 cube at texOffs(0, band * 32). */
	public static final int BAND_PAPER_MASK = 57;
	public static final int BAND_OILED = 58;
	public static final int BAND_PATCHED = 59;
	public static final int BAND_STILT = 60;
	public static final int BAND_SILK_FLOWER = 61;
	/** White banner cloth; each 16 × 16 cell holds 药 in x 1-14, y 1-14 (the north face of a 14 × 14 × 1 cube). */
	public static final int BAND_MEDICINE = 62;
	public static final int BAND_DANCER = 63;
	/** Atlas height: 64 bands of 32 px. */
	public static final int TEXTURE_HEIGHT = 64 * 32;

	private static final ModelLayerLocation[] SOLID = new ModelLayerLocation[KINDS];
	private static final ModelLayerLocation[] CLOTH = new ModelLayerLocation[KINDS];

	static {
		for (int i = 1; i < KINDS; i++) {
			SOLID[i] = new ModelLayerLocation(Kultivator.id("kota_gear_" + i), "main");
			CLOTH[i] = new ModelLayerLocation(Kultivator.id("kota_gear_" + i), "cloth");
		}
	}

	public static ModelLayerLocation solid(int kind) {
		return SOLID[Mth.clamp(kind, 1, KINDS - 1)];
	}

	public static ModelLayerLocation cloth(int kind) {
		return CLOTH[Mth.clamp(kind, 1, KINDS - 1)];
	}

	/** Registers every outfit's two layers (client init, before the entity models are baked). */
	public static void register() {
		for (int i = 1; i < KINDS; i++) {
			int kind = i;
			ModelLayerRegistry.registerModelLayer(SOLID[i], () -> mesh(kind, false));
			ModelLayerRegistry.registerModelLayer(CLOTH[i], () -> mesh(kind, true));
		}
		// DESIGN 40.4.5 / C40.K2: the 38 kinds carry no hair parts; the covering ones hide the Sosok hair cubes under them.
		for (int kind = GUAN_GIOK; kind < KINDS; kind++) {
			SosokGear.daftarTopi(kind, menutup(kind));
		}
	}

	/** True when a kind covers the head (hats, helmets, hoods, turbans, head cloths). */
	public static boolean menutup(int kind) {
		for (int k : MENUTUP) {
			if (k == kind) {
				return true;
			}
		}
		return false;
	}

	/** The debug/command name of a kind ("caping", "helm_kapten", ...), "none" outside the range. */
	public static String nama(int kind) {
		return kind > 0 && kind < KINDS ? NAMA[kind] : NAMA[0];
	}

	/** The kind of a debug/command name, or {@link #NONE}. */
	public static int dariNama(String nama) {
		for (int i = 1; i < KINDS; i++) {
			if (NAMA[i].equals(nama)) {
				return i;
			}
		}
		return NONE;
	}

	/**
	 * Lets another client module choose the outfit of a role (for example the Kota Iblis broker in {@link #CADAR_ULAR}
	 * when it can tell the theme on the client): the function returns a kind, or a negative number to keep the default.
	 */
	public static void timpa(NpcRole role, ToIntFunction<KultivatorNpc> pilih) {
		TIMPA.put(role, pilih);
	}

	/** The outfit of an NPC (role, look seed, the leader flag, overrides from {@link #timpa}). */
	public static int kindOf(KultivatorNpc npc) {
		ToIntFunction<KultivatorNpc> override = TIMPA.get(npc.role());
		if (override != null) {
			int kind = override.applyAsInt(npc);
			if (kind >= 0 && kind < KINDS) {
				return kind;
			}
		}
		int state = kindRupa(npc);
		return state > NONE ? state : kindDefault(npc);
	}

	/**
	 * The outfit of a look key the server set for a moment of town life (DESIGN 42.12.4): {@code <key>.pengantin} or
	 * {@code pengantin} (groom or bride by the body's sex), {@code .berkabung}, {@code .dingin}, {@code akrobat},
	 * {@code akrobat.egrang}, {@code penari}, {@code topeng_kertas}, {@code penjaga_malam}, {@code pencopet}; NONE otherwise.
	 */
	static int kindRupa(KultivatorNpc npc) {
		String rupa = npc.syncedRupa();
		if (rupa.isEmpty()) {
			return NONE;
		}
		int bar = rupa.indexOf('|');
		String key = bar < 0 ? rupa : rupa.substring(0, bar);
		if (key.equals("akrobat.egrang")) {
			return EGRANG;
		}
		String last = key.substring(key.lastIndexOf('.') + 1);
		return switch (last) {
			case "pengantin" -> npc.female() ? MAHKOTA_FENGGUAN : TOPI_PENGANTIN;
			case "berkabung" -> npc.role() == NpcRole.PENGURUS_DUKA ? TOPI_DUKA : TOPI_KABUNG;
			case "dingin" -> TOPI_DINGIN;
			case "akrobat" -> IKAT_AKROBAT;
			case "penari" -> SELENDANG_PENARI;
			case "topeng_kertas" -> TOPENG_KERTAS;
			case "penjaga_malam" -> CAPING_RONDA;
			case "pencopet" -> IKAT_COPET;
			default -> NONE;
		};
	}

	private static int kindDefault(KultivatorNpc npc) {
		int look = npc.look();
		return switch (npc.role()) {
			case JURU_LELANG -> FUTOU;
			case JURU_LELANG_ABADI -> FUTOU_GOLD;
			case PEMILIK_TEH -> TEA;
			case MAKELAR_GELAP -> BROKER;
			case PEDAGANG_PIL -> MERCHANT;
			case PEDAGANG_ABADI -> MERCHANT_GOLD;
			case PANDAI_PUSAKA -> SMITH;
			case PENJAGA -> GUARD;
			case WARGA -> FOLK_HAT + Math.floorMod(look >>> 9, 4);
			case PENGEMBARA -> Math.floorMod(look >>> 21, 2) == 0 ? WANDERER_DOULI : WANDERER_GUAN;
			case SAINGAN -> npc.hasFlag(KultivatorNpc.FLAG_LEADER) ? RIVAL_LEADER : RIVAL;
			case ABADI -> IMMORTAL;
			case PENCERAMAH -> SAGE;
			case UTUSAN -> ENVOY;
			case PENJAGA_KEBUN -> GARDEN;
			// DESIGN 38.N.2 roles (headwear column of 40.4.1 B; body garments and hair are Sosok's, 40.7.4)
			case PANGERAN_KOTA, PATRIARK_KLAN -> GUAN_GIOK;
			case MENTERI, HAKIM, PETUGAS_BEA -> PEJABAT;
			case PENGAWAL_ISTANA -> HELM_BERUMBAI;
			case PENJAGA_GERBANG -> HELM_PERISAI;
			case KAPTEN_PENJAGA -> HELM_KAPTEN;
			case BANKIR -> TOPI_BULAT;
			case PEMILIK_PENGINAPAN -> npc.female() ? NONE : TOPI_BULAT;
			case EMPU -> SARUNG_EMPU;
			case PEMOTONG_BATU -> KACAMATA;
			case ALKEMIS -> TOPI_ALKEMIS;
			case PUSTAKAWAN -> TOPI_SARJANA;
			case PENCERAMAH_FANA -> SARJANA_KIPAS;
			case NAKHODA -> TOPI_NAKHODA;
			case KULI, PENJAGA_SUAR, PENJUAL_IKAN, PENJAGA_LIFT, KULI_GUNUNG -> KAIN_KEPALA;
			case TUKANG -> TUKANG;
			case KOKI -> TOPI_KOKI;
			case RAHIB, PERTAPA, BIKSU_GUNUNG -> TASBIH;
			case PETANI, NELAYAN, KEPALA_DESA, PEMELIHARA_ULAT, PEMETIK_TEH -> CAPING;
			case PEDAGANG_KELILING -> KERANJANG;
			case PEDAGANG_BINATANG -> TOPI_BULU;
			case TUAN_MUDA -> KIPAS_PINGGANG;
			case KETUA_GILDA -> LENCANA_KETUA;
			case PETUGAS_GILDA -> LENCANA_PETUGAS;
			case PEDAGANG_SENJATA -> PEDANG_PUNGGUNG;
			// DESIGN 38.T.3.3-38.T.7.3 themed-city roles (38.T.8.2 non-body items)
			case SYAHBANDAR -> TOPI_SYAHBANDAR;
			case PELAUT -> KALUNG_HIU;
			case PEDAGANG_SEBERANG -> SERBAN;
			case PEDAGANG_BAZAR -> Math.floorMod(look >>> 17, 2) == 0 ? SERBAN : NONE;
			case PENDETA_LAUT -> MAHKOTA_LAUT;
			case TUAN_BENTENG -> SERBAN_EMAS;
			case PENUNTUN_UNTA -> KERUDUNG_PASIR;
			case KAFILAH_BASHI -> TOPI_BULU_LEBAR;
			case PENGAWAL_GURUN -> HELM_RUNCING;
			case PENDAYUNG -> CAPING_BIRU;
			case PEZIARAH -> IKAT_KUNING;
			case PEMETIK_HERBA -> TALI_MELINGKAR;
			case PENEGAK_IBLIS -> TOPENG_RANTAI;
			case JAGAL_ARENA -> RANTAI_BAHU;
			case PENGUASA_IBLIS -> MAHKOTA_TANDUK;
			case PENJAGA_KITAB_TERLARANG -> PENUTUP_MATA;
			case PEMBUNUH_BAYARAN -> TUDUNG;
			// DESIGN 42.12.2 Kota yang Hidup roles (42.12.4 headwear and tools)
			case PENCOPET -> IKAT_COPET;
			case PENJAGA_MALAM -> CAPING_RONDA;
			case MAK_COMBLANG -> BUNGA_COMBLANG;
			case PENDONGENG -> TOPI_DONGENG;
			case PENGAMEN -> TOPI_PENGAMEN;
			case AKROBAT -> IKAT_AKROBAT;
			case PENJUAL_OBAT -> PANJI_OBAT;
			case GURU_SEKOLAH -> TOPI_GURU;
			case PENGURUS_DUKA -> TOPI_DUKA;
			case PENJAGA_GUDANG -> KUNCI_GUDANG;
			case PIALANG -> TOPI_PIALANG;
			case PENARI -> SELENDANG_PENARI;
			case PEMUSIK -> TOPI_PEMUSIK;
			// roles whose look is all Sosok (garments, hair, props): no fixed headwear
			default -> NONE;
		};
	}

	/** The cloth colour of an outfit for a robe colour: darker trims, a light apron, the rival's own colour. */
	public static int tint(int kind, int robe) {
		int r = ARGB.red(robe);
		int g = ARGB.green(robe);
		int b = ARGB.blue(robe);
		return switch (kind) {
			case TEA, FOLK_SCARF -> ARGB.color(255, (r + 3 * 236) / 4, (g + 3 * 232) / 4, (b + 3 * 220) / 4);
			// homespun head cloths: undyed linen with a trace of the robe colour
			case KAIN_KEPALA, TUKANG, KACAMATA -> ARGB.color(255, (r + 2 * 218) / 3, (g + 2 * 206) / 3, (b + 2 * 182) / 3);
			// silk sashes and hat bands of the rich keep the robe colour bright
			case GUAN_GIOK, KIPAS_PINGGANG, SERBAN_EMAS, TOPI_NAKHODA -> ARGB.color(255, Math.min(255, r + 20), Math.min(255, g + 20), Math.min(255, b + 20));
			case RIVAL, RIVAL_LEADER -> ARGB.color(255, Math.min(255, r + 30), Math.min(255, g + 30), Math.min(255, b + 30));
			case IMMORTAL, SAGE, ENVOY -> ARGB.color(255, 250, 238, 200);
			case GUARD -> ARGB.color(255, Math.max(40, r * 3 / 4), Math.max(40, g * 3 / 4), Math.max(60, b * 3 / 4));
			default -> ARGB.color(255, r * 3 / 4, g * 3 / 4, b * 3 / 4);
		};
	}

	private static float rad(float degrees) {
		return degrees * Mth.DEG_TO_RAD;
	}

	// ================================================================== meshes

	/** Builds one pass of an outfit: the fixed materials ({@code cloth} false) or the tinted cloth ({@code cloth} true). */
	private static final class Pass {
		final boolean cloth;

		Pass(boolean cloth) {
			this.cloth = cloth;
		}

		/** A part of fixed material {@code band} (empty in the cloth pass, so children keep their parents). */
		PartDefinition solid(PartDefinition parent, String name, int band, PartPose pose, float... boxes) {
			return parent.addOrReplaceChild(name, cloth ? CubeListBuilder.create() : cubes(band, boxes), pose);
		}

		/** A part of the tinted cloth (empty in the fixed pass). */
		PartDefinition tinted(PartDefinition parent, String name, PartPose pose, float... boxes) {
			return parent.addOrReplaceChild(name, cloth ? cubes(BAND_CLOTH, boxes) : CubeListBuilder.create(), pose);
		}

		private static CubeListBuilder cubes(int band, float[] boxes) {
			CubeListBuilder builder = CubeListBuilder.create().texOffs(0, band * 32);
			for (int i = 0; i + 5 < boxes.length; i += 6) {
				builder.addBox(boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3], boxes[i + 4], boxes[i + 5]);
			}
			return builder;
		}
	}

	/** The player skeleton with every skin cube removed (the gear's parts hang on it). */
	private static MeshDefinition skeleton() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		body.addOrReplaceChild("jacket", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		rightArm.addOrReplaceChild("right_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		leftArm.addOrReplaceChild("left_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		rightLeg.addOrReplaceChild("right_pants", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		leftLeg.addOrReplaceChild("left_pants", CubeListBuilder.create(), PartPose.ZERO);
		return mesh;
	}

	static LayerDefinition mesh(int kind, boolean cloth) {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");
		Pass p = new Pass(cloth);
		PartPose zero = PartPose.ZERO;
		switch (kind) {
			case FUTOU, FUTOU_GOLD -> {
				int cap = kind == FUTOU_GOLD ? BAND_GOLD : BAND_BLACK;
				p.solid(head, "cap", cap, zero, -4.4F, -9.2F, -4.4F, 8.8F, 3.2F, 8.8F, -3.0F, -11.0F, 0.4F, 6.0F, 2.0F, 4.0F);
				p.solid(head, "band", kind == FUTOU_GOLD ? BAND_JADE : BAND_GOLD, zero, -4.55F, -6.6F, -4.55F, 9.1F, 0.6F, 9.1F);
				p.solid(head, "foot_l", cap, PartPose.offsetAndRotation(3.2F, -9.6F, 3.4F, 0.0F, 0.0F, rad(-10.0F)), 0.0F, -0.4F, -0.4F, 5.5F, 0.8F, 0.8F);
				p.solid(head, "foot_r", cap, PartPose.offsetAndRotation(-3.2F, -9.6F, 3.4F, 0.0F, 0.0F, rad(10.0F)), -5.5F, -0.4F, -0.4F, 5.5F, 0.8F, 0.8F);
				p.tinted(body, "sash", zero, -4.35F, 6.4F, -2.35F, 8.7F, 1.6F, 4.7F);
				p.solid(body, "pendant", BAND_JADE, zero, -0.7F, 8.1F, -2.7F, 1.4F, 2.2F, 0.4F);
				p.solid(body, "pendant_cord", BAND_CRIMSON, zero, -0.3F, 10.3F, -2.65F, 0.6F, 1.6F, 0.3F);
				PartDefinition fan = p.solid(body, "fan", BAND_PAPER, PartPose.offsetAndRotation(3.3F, 7.4F, -2.5F, 0.0F, 0.0F, rad(-14.0F)),
						-0.5F, 0.0F, -0.45F, 1.0F, 4.6F, 0.9F);
				p.solid(fan, "fan_rib", BAND_WOOD, zero, -0.55F, -0.6F, -0.5F, 1.1F, 0.6F, 1.0F);
				p.solid(fan, "fan_tassel", BAND_CRIMSON, zero, -0.25F, 4.6F, -0.25F, 0.5F, 1.8F, 0.5F);
			}
			case TEA -> {
				p.solid(head, "bun", BAND_HAIR, zero, -2.0F, -11.0F, -0.6F, 4.0F, 3.0F, 4.0F);
				p.solid(head, "pin", BAND_WOOD, PartPose.offsetAndRotation(0.0F, -10.0F, 1.4F, 0.0F, rad(22.0F), rad(-6.0F)),
						-3.6F, -0.3F, -0.3F, 7.2F, 0.6F, 0.6F);
				p.solid(head, "pin_bead", BAND_JADE, PartPose.offset(3.2F, -10.3F, 2.6F), -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F);
				p.tinted(head, "hairband", zero, -4.3F, -8.3F, -4.3F, 8.6F, 0.9F, 8.6F);
				p.tinted(body, "apron", zero, -3.6F, 5.4F, -2.55F, 7.2F, 7.8F, 0.5F);
				p.tinted(body, "apron_tie", zero, -4.35F, 5.5F, -2.35F, 8.7F, 1.1F, 4.7F, -1.0F, 6.6F, 2.3F, 0.8F, 3.0F, 0.3F);
				p.solid(body, "towel", BAND_WHITE, zero, 1.4F, -0.6F, -2.45F, 3.0F, 1.0F, 4.9F, 2.0F, 0.4F, -2.65F, 2.0F, 4.2F, 0.4F);
			}
			case BROKER -> {
				p.tinted(head, "hood", zero, -4.6F, -8.9F, -4.6F, 9.2F, 1.4F, 9.2F, -4.6F, -8.9F, 3.4F, 9.2F, 9.6F, 1.2F,
						-4.6F, -8.9F, -4.2F, 1.0F, 9.2F, 7.6F, 3.6F, -8.9F, -4.2F, 1.0F, 9.2F, 7.6F);
				p.solid(head, "veil", BAND_GAUZE, zero, -3.9F, -4.3F, -4.6F, 7.8F, 3.8F, 0.5F);
				p.solid(head, "earring", BAND_GOLD, zero, -4.8F, -3.2F, -1.0F, 0.4F, 1.4F, 0.4F);
				p.tinted(body, "mantle", zero, -4.6F, -0.3F, 2.2F, 9.2F, 12.5F, 0.7F, -4.6F, -0.4F, -2.5F, 9.2F, 2.2F, 4.9F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				PartDefinition dagger = p.solid(body, "dagger", BAND_WOOD, PartPose.offsetAndRotation(-3.2F, 7.6F, -2.5F, 0.0F, 0.0F, rad(28.0F)),
						-0.45F, 0.0F, -0.45F, 0.9F, 4.0F, 0.9F);
				p.solid(dagger, "dagger_hilt", BAND_GOLD, zero, -0.3F, -1.6F, -0.3F, 0.6F, 1.6F, 0.6F, -0.8F, -0.3F, -0.5F, 1.6F, 0.4F, 1.0F);
			}
			case MERCHANT, MERCHANT_GOLD -> {
				if (kind == MERCHANT_GOLD) {
					p.solid(head, "cap", BAND_GOLD, zero, -4.4F, -9.4F, -4.4F, 8.8F, 2.4F, 8.8F);
					p.solid(head, "knob", BAND_JADE, zero, -0.8F, -10.6F, -0.8F, 1.6F, 1.2F, 1.6F);
				} else {
					p.tinted(head, "cap", zero, -4.4F, -9.4F, -4.4F, 8.8F, 2.4F, 8.8F);
					p.solid(head, "knob", BAND_GOLD, zero, -0.8F, -10.6F, -0.8F, 1.6F, 1.2F, 1.6F);
				}
				p.solid(head, "rim", BAND_BLACK, zero, -4.6F, -7.3F, -4.6F, 9.2F, 0.6F, 9.2F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				p.solid(body, "purse", BAND_LEATHER, zero, 2.4F, 8.2F, -2.9F, 1.8F, 2.2F, 1.0F);
				if (kind == MERCHANT_GOLD) {
					PartDefinition abacus = p.solid(body, "abacus", BAND_BRONZE, PartPose.offset(-4.7F, 8.0F, 0.0F), -0.4F, 0.0F, -2.0F, 0.6F, 3.0F, 4.0F);
					p.solid(abacus, "beads", BAND_GOLD, zero, -0.6F, 0.6F, -1.6F, 1.0F, 0.6F, 3.2F, -0.6F, 1.8F, -1.6F, 1.0F, 0.6F, 3.2F);
					p.solid(body, "pendant", BAND_JADE, zero, -0.7F, 8.2F, -2.7F, 1.4F, 2.0F, 0.4F);
				} else {
					PartDefinition gourd = p.solid(body, "gourd", BAND_GOURD, PartPose.offset(-4.6F, 8.6F, 0.4F),
							-1.2F, 0.0F, -1.2F, 2.4F, 2.4F, 2.4F, -0.7F, -1.4F, -0.7F, 1.4F, 1.4F, 1.4F);
					p.solid(gourd, "cord", BAND_CRIMSON, zero, -0.25F, -2.6F, -0.25F, 0.5F, 1.2F, 0.5F);
				}
			}
			case SMITH -> {
				p.solid(head, "headband", BAND_CRIMSON, zero, -4.35F, -6.7F, -4.35F, 8.7F, 1.2F, 8.7F);
				p.solid(head, "knot", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -6.2F, 4.3F, rad(25.0F), 0.0F, 0.0F),
						-1.2F, 0.0F, -0.2F, 0.9F, 3.2F, 0.4F, 0.3F, 0.0F, -0.2F, 0.9F, 2.6F, 0.4F);
				p.solid(body, "apron", BAND_LEATHER, zero, -3.8F, 1.0F, -2.6F, 7.6F, 11.6F, 0.5F, -3.6F, -0.2F, -2.45F, 1.0F, 1.4F, 4.9F,
						2.6F, -0.2F, -2.45F, 1.0F, 1.4F, 4.9F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				p.solid(body, "buckle", BAND_BRONZE, zero, -0.9F, 6.9F, -2.75F, 1.8F, 1.4F, 0.4F);
				p.solid(rightArm, "bracer", BAND_LEATHER, zero, -3.35F, 5.6F, -2.35F, 4.7F, 4.0F, 4.7F);
				p.solid(leftArm, "bracer", BAND_LEATHER, zero, -1.35F, 5.6F, -2.35F, 4.7F, 4.0F, 4.7F);
			}
			case GUARD -> {
				p.solid(head, "helm", BAND_STEEL, zero, -4.6F, -9.2F, -4.6F, 9.2F, 4.6F, 9.2F, -4.7F, -4.8F, -3.0F, 0.6F, 3.6F, 4.6F,
						4.1F, -4.8F, -3.0F, 0.6F, 3.6F, 4.6F, -4.6F, -5.0F, 3.5F, 9.2F, 4.2F, 1.1F);
				p.solid(head, "brow", BAND_GOLD, zero, -4.75F, -5.4F, -4.75F, 9.5F, 0.6F, 9.5F);
				p.solid(head, "crest", BAND_GOLD, zero, -0.6F, -10.3F, -4.8F, 1.2F, 1.4F, 9.6F);
				p.solid(head, "spike", BAND_GOLD, zero, -0.5F, -11.8F, -0.5F, 1.0F, 1.6F, 1.0F);
				p.solid(head, "plume", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -11.6F, 0.6F, rad(-20.0F), 0.0F, 0.0F),
						-0.7F, -4.6F, -0.7F, 1.4F, 4.6F, 3.4F);
				p.solid(body, "cuirass", BAND_STEEL, zero, -4.45F, 0.4F, -2.65F, 8.9F, 7.6F, 0.6F, -4.45F, 0.4F, 2.05F, 8.9F, 7.6F, 0.6F,
						-4.45F, 8.0F, -2.6F, 8.9F, 4.4F, 0.5F, -4.45F, 8.0F, 2.1F, 8.9F, 4.4F, 0.5F);
				p.solid(body, "mirror", BAND_GOLD, zero, -1.6F, 2.0F, -3.0F, 3.2F, 3.2F, 0.4F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.4F, 7.2F, -2.4F, 8.8F, 1.2F, 4.8F);
				p.tinted(body, "tabard", zero, -2.3F, 8.3F, -2.8F, 4.6F, 4.8F, 0.3F);
				p.solid(rightArm, "pauldron", BAND_STEEL, PartPose.rotation(0.0F, 0.0F, rad(8.0F)), -3.6F, -2.7F, -2.6F, 5.2F, 3.6F, 5.2F);
				p.solid(leftArm, "pauldron", BAND_STEEL, PartPose.rotation(0.0F, 0.0F, rad(-8.0F)), -1.6F, -2.7F, -2.6F, 5.2F, 3.6F, 5.2F);
			}
			case FOLK_HAT -> {
				p.solid(head, "straw", BAND_STRAW, zero, -6.0F, -8.9F, -6.0F, 12.0F, 0.8F, 12.0F, -3.6F, -10.0F, -3.6F, 7.2F, 1.2F, 7.2F,
						-1.4F, -10.8F, -1.4F, 2.8F, 0.8F, 2.8F);
				p.tinted(head, "ribbon", zero, -3.7F, -9.3F, -3.7F, 7.4F, 0.5F, 7.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			case FOLK_SCARF -> {
				p.tinted(head, "scarf", zero, -4.4F, -8.7F, -4.4F, 8.8F, 2.8F, 8.8F, -1.0F, -6.4F, 4.2F, 2.0F, 4.2F, 0.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				PartDefinition basket = p.solid(body, "basket", BAND_STRAW, PartPose.offset(5.0F, 8.4F, 0.0F), -1.6F, 0.0F, -1.8F, 3.2F, 2.6F, 3.6F);
				p.solid(basket, "handle", BAND_WOOD, zero, -0.3F, -2.4F, -0.3F, 0.6F, 2.4F, 0.6F);
			}
			case FOLK_BUN -> {
				p.solid(head, "bun", BAND_HAIR, zero, -1.8F, -10.8F, -0.6F, 3.6F, 2.8F, 3.6F);
				p.solid(head, "pin", BAND_JADE, PartPose.offsetAndRotation(0.0F, -9.8F, 1.2F, 0.0F, rad(-25.0F), 0.0F),
						-3.0F, -0.3F, -0.3F, 6.0F, 0.6F, 0.6F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			case FOLK_KNOT -> {
				p.solid(head, "knot", BAND_HAIR, zero, -1.2F, -10.6F, -1.2F, 2.4F, 2.6F, 2.4F);
				p.solid(head, "wrap", BAND_WHITE, zero, -1.5F, -9.6F, -1.5F, 3.0F, 1.0F, 3.0F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				p.solid(body, "pouch", BAND_LEATHER, zero, 2.6F, 8.0F, -2.8F, 1.6F, 2.0F, 0.9F);
			}
			case WANDERER_DOULI -> {
				p.solid(head, "douli", BAND_STRAW, zero, -7.5F, -8.8F, -7.5F, 15.0F, 0.8F, 15.0F, -5.4F, -9.6F, -5.4F, 10.8F, 0.8F, 10.8F,
						-3.4F, -10.4F, -3.4F, 6.8F, 0.8F, 6.8F, -1.4F, -11.2F, -1.4F, 2.8F, 0.8F, 2.8F);
				p.solid(head, "veil", BAND_GAUZE, zero, -7.3F, -8.0F, -4.0F, 0.2F, 5.5F, 8.0F, 7.1F, -8.0F, -4.0F, 0.2F, 5.5F, 8.0F,
						-5.0F, -8.0F, 7.1F, 10.0F, 6.0F, 0.2F);
				p.solid(head, "tie", BAND_CRIMSON, zero, -0.4F, -8.0F, -4.45F, 0.8F, 0.8F, 0.3F);
				p.tinted(body, "cloak", PartPose.rotation(rad(6.0F), 0.0F, 0.0F), -4.6F, -0.2F, 2.1F, 9.2F, 13.0F, 0.6F);
				p.tinted(body, "belt", zero, -4.35F, 6.8F, -2.35F, 8.7F, 1.3F, 4.7F);
				hipSword(p, body);
				PartDefinition gourd = p.solid(body, "gourd", BAND_GOURD, PartPose.offset(-4.4F, 8.4F, 1.0F),
						-1.3F, 0.0F, -1.3F, 2.6F, 2.6F, 2.6F, -0.8F, -1.5F, -0.8F, 1.6F, 1.5F, 1.6F);
				p.solid(gourd, "cord", BAND_CRIMSON, zero, -0.25F, -2.7F, -0.25F, 0.5F, 1.2F, 0.5F);
			}
			case WANDERER_GUAN -> {
				p.solid(head, "knot", BAND_HAIR, zero, -1.3F, -10.8F, -1.3F, 2.6F, 2.8F, 2.6F);
				p.solid(head, "guan", BAND_JADE, zero, -1.6F, -11.6F, -1.6F, 3.2F, 1.6F, 3.2F);
				p.solid(head, "pin", BAND_GOLD, zero, -2.7F, -11.1F, -0.3F, 5.4F, 0.6F, 0.6F);
				p.tinted(head, "ribbon_l", PartPose.offsetAndRotation(0.8F, -10.0F, 1.4F, rad(18.0F), 0.0F, rad(-4.0F)), -0.6F, 0.0F, 0.0F, 1.2F, 9.0F, 0.2F);
				p.tinted(head, "ribbon_r", PartPose.offsetAndRotation(-0.8F, -10.0F, 1.4F, rad(18.0F), 0.0F, rad(4.0F)), -0.6F, 0.0F, 0.0F, 1.2F, 8.0F, 0.2F);
				p.tinted(body, "belt", zero, -4.35F, 6.8F, -2.35F, 8.7F, 1.3F, 4.7F);
				p.solid(body, "strap", BAND_LEATHER, PartPose.offsetAndRotation(0.0F, 5.0F, -2.45F, 0.0F, 0.0F, rad(-42.0F)), -0.4F, -7.5F, -0.2F, 0.8F, 15.0F, 0.4F);
				PartDefinition back = p.solid(body, "scabbard", BAND_WOOD, PartPose.offsetAndRotation(0.0F, 6.0F, 2.6F, 0.0F, 0.0F, rad(40.0F)),
						-0.6F, -5.5F, -0.6F, 1.2F, 12.5F, 1.2F);
				p.solid(back, "chape", BAND_GOLD, zero, -0.7F, 6.4F, -0.7F, 1.4F, 1.0F, 1.4F);
				PartDefinition hilt = p.solid(back, HILT, BAND_GOLD, zero, -1.3F, -6.2F, -0.9F, 2.6F, 0.7F, 1.8F, -0.6F, -10.4F, -0.6F, 1.2F, 0.6F, 1.2F);
				p.solid(hilt, "grip", BAND_BLACK, zero, -0.45F, -9.8F, -0.45F, 0.9F, 3.6F, 0.9F);
				p.solid(hilt, "tassel", BAND_CRIMSON, zero, -0.2F, -10.4F, 0.4F, 0.4F, 2.2F, 0.4F);
			}
			case RIVAL, RIVAL_LEADER -> {
				if (kind == RIVAL_LEADER) {
					p.solid(head, "knot", BAND_HAIR, zero, -1.3F, -10.6F, -1.3F, 2.6F, 2.6F, 2.6F);
					p.solid(head, "guan", BAND_GOLD, zero, -2.0F, -11.6F, -2.0F, 4.0F, 2.2F, 4.0F, -2.6F, -11.0F, -0.4F, 5.2F, 0.6F, 0.8F);
					p.solid(head, "gem", BAND_CRIMSON, zero, -0.5F, -11.2F, -2.3F, 1.0F, 1.0F, 0.4F);
					p.tinted(body, "cloak", PartPose.rotation(rad(5.0F), 0.0F, 0.0F), -4.7F, -0.4F, 2.1F, 9.4F, 13.5F, 0.6F,
							-4.7F, -0.6F, -2.6F, 9.4F, 1.2F, 5.2F);
				} else {
					p.tinted(head, "band", zero, -4.35F, -7.1F, -4.35F, 8.7F, 1.4F, 8.7F);
					p.tinted(head, "tail_l", PartPose.offsetAndRotation(0.6F, -6.6F, 4.3F, rad(35.0F), 0.0F, rad(-10.0F)), -0.4F, 0.0F, -0.15F, 0.8F, 4.2F, 0.3F);
					p.tinted(head, "tail_r", PartPose.offsetAndRotation(-0.6F, -6.6F, 4.3F, rad(30.0F), 0.0F, rad(12.0F)), -0.4F, 0.0F, -0.15F, 0.8F, 3.6F, 0.3F);
					p.solid(leftArm, "guard", BAND_LEATHER, zero, -1.4F, -2.5F, -2.4F, 4.8F, 3.0F, 4.8F);
				}
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				hipSword(p, body);
			}
			case IMMORTAL -> {
				p.solid(head, "guan", BAND_JADE, zero, -1.8F, -11.8F, -1.8F, 3.6F, 3.0F, 3.6F);
				p.solid(head, "pin", BAND_GOLD, zero, -4.2F, -10.8F, -0.4F, 8.4F, 0.8F, 0.8F);
				p.solid(head, "hair", BAND_HAIR, zero, -4.3F, -8.6F, -4.3F, 8.6F, 1.4F, 8.6F, -4.3F, -8.0F, 3.3F, 8.6F, 11.0F, 1.1F);
				p.tinted(body, "shawl", zero, -4.6F, -0.5F, -2.6F, 9.2F, 1.2F, 5.2F);
				p.tinted(rightArm, "ribbon", PartPose.rotation(rad(-10.0F), 0.0F, rad(14.0F)), -3.7F, 3.0F, -0.2F, 0.3F, 11.0F, 0.5F);
				p.tinted(leftArm, "ribbon", PartPose.rotation(rad(-10.0F), 0.0F, rad(-14.0F)), 3.4F, 3.0F, -0.2F, 0.3F, 11.0F, 0.5F);
				p.solid(body, "pendant", BAND_JADE, zero, -0.8F, 7.8F, -2.7F, 1.6F, 2.4F, 0.4F);
				p.solid(body, "belt", BAND_GOLD, zero, -4.35F, 7.0F, -2.35F, 8.7F, 0.8F, 4.7F);
			}
			case SAGE -> {
				p.solid(head, "hat", BAND_BLACK, zero, -3.4F, -12.6F, -3.4F, 6.8F, 4.6F, 6.8F);
				p.solid(head, "hat_band", BAND_GOLD, zero, -3.6F, -9.2F, -3.6F, 7.2F, 0.7F, 7.2F);
				p.solid(head, "beard", BAND_WHITE, zero, -2.3F, -1.6F, -4.7F, 4.6F, 7.6F, 1.0F, -2.7F, -2.7F, -4.6F, 5.4F, 0.8F, 0.6F);
				p.solid(head, "brows", BAND_WHITE, zero, -3.6F, -5.8F, -4.45F, 2.8F, 0.7F, 0.4F, 0.8F, -5.8F, -4.45F, 2.8F, 0.7F, 0.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				PartDefinition whisk = p.solid(body, "whisk", BAND_WOOD, PartPose.offsetAndRotation(-4.6F, 4.0F, -1.2F, rad(-8.0F), 0.0F, rad(10.0F)),
						-0.4F, 0.0F, -0.4F, 0.8F, 5.0F, 0.8F);
				p.solid(whisk, "tail", BAND_WHITE, zero, -0.9F, 5.0F, -0.9F, 1.8F, 6.0F, 1.8F);
			}
			case ENVOY -> {
				p.solid(head, "hat", BAND_BLACK, zero, -4.5F, -10.6F, -4.5F, 9.0F, 2.8F, 9.0F, -3.5F, -12.2F, -1.0F, 7.0F, 1.6F, 5.0F);
				p.solid(head, "wing_l", BAND_GOLD, PartPose.offset(4.4F, -11.0F, 2.6F), 0.0F, -0.5F, -1.2F, 8.5F, 1.0F, 2.4F);
				p.solid(head, "wing_r", BAND_GOLD, PartPose.offset(-4.4F, -11.0F, 2.6F), -8.5F, -0.5F, -1.2F, 8.5F, 1.0F, 2.4F);
				p.solid(head, "jewel", BAND_JADE, zero, -0.8F, -10.2F, -4.8F, 1.6F, 1.6F, 0.4F);
				p.solid(body, "belt", BAND_JADE, zero, -4.45F, 7.0F, -2.45F, 8.9F, 1.4F, 4.9F);
				p.solid(body, "plaques", BAND_GOLD, zero, -3.4F, 7.1F, -2.65F, 1.4F, 1.2F, 0.3F, -0.7F, 7.1F, -2.65F, 1.4F, 1.2F, 0.3F,
						2.0F, 7.1F, -2.65F, 1.4F, 1.2F, 0.3F);
				p.tinted(body, "collar", zero, -4.5F, -0.4F, -2.55F, 9.0F, 1.6F, 5.1F);
			}
			case GARDEN -> {
				p.solid(head, "crown", BAND_JADE, zero, -4.5F, -8.6F, -4.5F, 9.0F, 0.9F, 9.0F);
				p.solid(head, "blossoms", BAND_PEACH, zero, -4.7F, -9.4F, -2.6F, 1.4F, 1.4F, 1.4F, 3.3F, -9.4F, -0.4F, 1.4F, 1.4F, 1.4F,
						-1.6F, -9.5F, -4.8F, 1.6F, 1.6F, 1.4F, 0.8F, -9.4F, 3.4F, 1.4F, 1.4F, 1.4F, -4.6F, -9.3F, 2.0F, 1.2F, 1.2F, 1.2F,
						2.6F, -9.5F, -4.7F, 1.4F, 1.4F, 1.4F);
				PartDefinition basket = p.solid(body, "basket", BAND_STRAW, PartPose.offset(5.3F, 8.4F, 0.0F), -1.8F, 0.0F, -1.9F, 3.6F, 2.6F, 3.8F);
				p.solid(basket, "handle", BAND_WOOD, zero, -0.3F, -2.4F, -0.3F, 0.6F, 2.4F, 0.6F);
				p.solid(basket, "peaches", BAND_PEACH, zero, -1.3F, -0.8F, -1.4F, 1.4F, 1.2F, 1.4F, -0.1F, -0.7F, 0.1F, 1.3F, 1.1F, 1.3F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			default -> {
				if (kind >= TOPI_PENGANTIN) {
					mesh42(kind, p, root);
				} else {
					mesh38(kind, p, head, body, rightArm, leftArm);
				}
			}
		}
		return LayerDefinition.create(mesh, 64, TEXTURE_HEIGHT);
	}

	// ================================================================== DESIGN 38 kinds (38.O.1, 38.T.8.2)

	private static float[] b(float... boxes) {
		return boxes;
	}

	/** The DESIGN 38 outfits (kinds 21-60). Coordinates in model pixels on the player skeleton; front = -z. */
	private static void mesh38(int kind, Pass p, PartDefinition head, PartDefinition body, PartDefinition rightArm, PartDefinition leftArm) {
		PartPose zero = PartPose.ZERO;
		switch (kind) {
			case GUAN_GIOK -> {
				// the small jade crown on the topknot, its gold band and long pin, crimson ribbons down the back
				p.solid(head, "guan", BAND_JADE, zero, -2.0F, -10.0F, -1.5F, 4.0F, 2.0F, 3.0F, -2.4F, -10.6F, -1.9F, 4.8F, 0.6F, 3.8F);
				p.solid(head, "guan_band", BAND_GOLD, zero, -2.15F, -8.6F, -1.65F, 4.3F, 0.5F, 3.3F);
				p.solid(head, "pin", BAND_GOLD, zero, -3.5F, -9.8F, -0.3F, 7.0F, 0.6F, 0.6F, -4.1F, -10.0F, -0.5F, 0.8F, 1.0F, 1.0F,
						3.3F, -10.0F, -0.5F, 0.8F, 1.0F, 1.0F);
				p.solid(head, "jewel", BAND_JADE, zero, -0.5F, -9.7F, -2.05F, 1.0F, 1.0F, 0.4F);
				p.solid(head, "ribbon_l", BAND_CRIMSON, PartPose.offsetAndRotation(1.2F, -9.2F, 1.6F, rad(16.0F), 0.0F, rad(-5.0F)),
						-0.4F, 0.0F, 0.0F, 0.8F, 7.5F, 0.2F);
				p.solid(head, "ribbon_r", BAND_CRIMSON, PartPose.offsetAndRotation(-1.2F, -9.2F, 1.6F, rad(14.0F), 0.0F, rad(5.0F)),
						-0.4F, 0.0F, 0.0F, 0.8F, 6.5F, 0.2F);
				p.tinted(body, "sash", zero, -4.4F, 6.4F, -2.4F, 8.8F, 1.6F, 4.8F);
				p.solid(body, "plaques", BAND_GOLD, zero, -3.2F, 6.6F, -2.6F, 1.2F, 1.2F, 0.3F, -0.6F, 6.6F, -2.6F, 1.2F, 1.2F, 0.3F,
						2.0F, 6.6F, -2.6F, 1.2F, 1.2F, 0.3F);
				p.solid(body, "pendant", BAND_JADE, zero, -0.9F, 8.2F, -2.7F, 1.8F, 2.4F, 0.4F);
				p.solid(body, "tassel", BAND_CRIMSON, zero, -0.3F, 10.6F, -2.65F, 0.6F, 2.2F, 0.3F);
			}
			case PEJABAT -> {
				// wushamao: lacquered gauze crown, a raised back hump and two stiff paddle wings
				p.solid(head, "crown", BAND_LACQUER_GAUZE, zero, -4.2F, -10.4F, -4.2F, 8.4F, 2.6F, 8.4F, -3.4F, -12.2F, 0.2F, 6.8F, 2.0F, 4.0F);
				p.solid(head, "brim", BAND_BLACK, zero, -4.4F, -8.3F, -4.4F, 8.8F, 0.6F, 8.8F);
				p.solid(head, "wing_l", BAND_LACQUER_GAUZE, PartPose.offsetAndRotation(3.4F, -11.2F, 2.6F, 0.0F, 0.0F, rad(-4.0F)),
						0.0F, -0.25F, -0.3F, 3.0F, 0.5F, 0.6F, 3.0F, -0.3F, -1.2F, 3.4F, 0.6F, 2.4F);
				p.solid(head, "wing_r", BAND_LACQUER_GAUZE, PartPose.offsetAndRotation(-3.4F, -11.2F, 2.6F, 0.0F, 0.0F, rad(4.0F)),
						-3.0F, -0.25F, -0.3F, 3.0F, 0.5F, 0.6F, -6.4F, -0.3F, -1.2F, 3.4F, 0.6F, 2.4F);
				p.solid(head, "wing_rims", BAND_GOLD, zero, -0.6F, -10.6F, -4.45F, 1.2F, 1.0F, 0.3F);
				p.solid(body, "belt", BAND_BLACK, zero, -4.45F, 7.0F, -2.45F, 8.9F, 1.3F, 4.9F);
				p.solid(body, "plaques", BAND_GOLD, zero, -3.4F, 7.1F, -2.65F, 1.3F, 1.1F, 0.3F, -0.65F, 7.1F, -2.65F, 1.3F, 1.1F, 0.3F,
						2.1F, 7.1F, -2.65F, 1.3F, 1.1F, 0.3F);
				p.solid(body, "seal", BAND_JADE, PartPose.offset(3.6F, 8.2F, -2.0F), -0.6F, 0.0F, -0.6F, 1.2F, 1.6F, 1.2F);
				p.solid(body, "seal_cord", BAND_CRIMSON, zero, 3.3F, 7.4F, -2.3F, 0.5F, 1.0F, 0.5F);
			}
			case HELM_BERUMBAI -> helmBerumbai(p, head);
			case HELM_PERISAI -> {
				helmBerumbai(p, head);
				// the round shield on the left forearm: a dodecagon of painted hide over a bronze rim, a bronze boss
				for (int i = 0; i < 3; i++) {
					PartDefinition disc = p.solid(leftArm, "shield_" + i, BAND_SHIELD_HIDE, PartPose.offsetAndRotation(3.7F, 5.0F, 0.0F, rad(i * 30.0F), 0.0F, 0.0F),
							-0.5F, -5.0F, -5.0F, 1.0F, 10.0F, 10.0F);
					p.solid(disc, "rim", BAND_BRONZE, PartPose.rotation(rad(15.0F), 0.0F, 0.0F), -0.4F, -5.4F, -5.4F, 0.8F, 10.8F, 10.8F);
					if (i == 0) {
						p.solid(disc, "boss", BAND_BRONZE, zero, 0.5F, -1.5F, -1.5F, 1.5F, 3.0F, 3.0F, 1.9F, -0.6F, -0.6F, 0.4F, 1.2F, 1.2F);
						p.solid(disc, "strap", BAND_LEATHER, zero, -1.1F, -3.5F, -0.4F, 0.6F, 7.0F, 0.8F);
					}
				}
			}
			case HELM_KAPTEN -> {
				p.solid(head, "dome", BAND_GOLD, zero, -4.5F, -9.8F, -4.5F, 9.0F, 3.0F, 9.0F);
				p.solid(head, "ring", BAND_LAMELLAR_RED, zero, -4.6F, -6.9F, -4.6F, 9.2F, 2.2F, 9.2F);
				p.solid(head, "visor", BAND_GOLD, zero, -4.75F, -5.2F, -4.9F, 9.5F, 0.6F, 1.8F);
				p.solid(head, "neck", BAND_LAMELLAR_RED, zero, -4.6F, -5.0F, 3.4F, 9.2F, 4.6F, 1.2F, -4.7F, -4.8F, -3.2F, 0.6F, 3.8F, 6.6F,
						4.1F, -4.8F, -3.2F, 0.6F, 3.8F, 6.6F);
				p.solid(head, "wing_l", BAND_GOLD, PartPose.offsetAndRotation(4.4F, -7.6F, 0.4F, 0.0F, rad(-10.0F), rad(-18.0F)),
						0.0F, -3.0F, -0.25F, 4.0F, 3.0F, 0.5F, 2.6F, -4.2F, -0.2F, 1.2F, 1.4F, 0.4F);
				p.solid(head, "wing_r", BAND_GOLD, PartPose.offsetAndRotation(-4.4F, -7.6F, 0.4F, 0.0F, rad(10.0F), rad(18.0F)),
						-4.0F, -3.0F, -0.25F, 4.0F, 3.0F, 0.5F, -3.8F, -4.2F, -0.2F, 1.2F, 1.4F, 0.4F);
				p.solid(head, "crest", BAND_GOLD, zero, -0.6F, -10.6F, -4.8F, 1.2F, 1.0F, 9.6F, -0.6F, -12.2F, -0.6F, 1.2F, 1.6F, 1.2F);
				p.solid(head, "gem", BAND_CRIMSON, zero, -0.7F, -8.4F, -4.75F, 1.4F, 1.4F, 0.4F);
				p.solid(head, "plume", BAND_PLUME, PartPose.offsetAndRotation(0.0F, -12.0F, 0.4F, rad(-18.0F), 0.0F, 0.0F),
						-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F, -0.8F, -4.2F, 1.0F, 1.6F, 3.4F, 2.6F);
			}
			case TOPI_BULAT -> {
				p.solid(head, "cap", BAND_COIN_BROCADE, zero, -4.2F, -10.2F, -4.2F, 8.4F, 3.0F, 8.4F);
				p.solid(head, "brim", BAND_BLACK, zero, -5.0F, -7.4F, -5.0F, 10.0F, 0.6F, 10.0F);
				p.solid(head, "button", BAND_GOLD, zero, -0.9F, -11.0F, -0.9F, 1.8F, 0.8F, 1.8F);
				p.solid(head, "tassel", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -10.6F, 0.6F, rad(30.0F), 0.0F, 0.0F),
						-0.3F, 0.0F, -0.3F, 0.6F, 3.0F, 0.6F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				PartDefinition purse = p.solid(body, "purse", BAND_COIN_BROCADE, PartPose.offset(2.8F, 8.2F, -2.4F), -1.1F, 0.0F, -1.0F, 2.2F, 2.6F, 1.2F);
				p.solid(purse, "tie", BAND_GOLD, zero, -0.8F, -0.4F, -1.1F, 1.6F, 0.6F, 1.4F);
				p.solid(body, "coins", BAND_GOLD, zero, -3.8F, 8.2F, -2.6F, 1.0F, 1.0F, 0.3F, -3.8F, 9.3F, -2.6F, 1.0F, 1.0F, 0.3F,
						-3.8F, 10.4F, -2.6F, 1.0F, 1.0F, 0.3F);
				p.solid(body, "coin_cord", BAND_CRIMSON, zero, -3.45F, 7.8F, -2.5F, 0.3F, 4.0F, 0.2F);
			}
			case SARUNG_EMPU -> {
				p.solid(head, "headband", BAND_CRIMSON, zero, -4.35F, -6.8F, -4.35F, 8.7F, 1.2F, 8.7F);
				p.solid(head, "knot", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -6.3F, 4.3F, rad(25.0F), 0.0F, 0.0F),
						-1.2F, 0.0F, -0.2F, 0.9F, 3.0F, 0.4F, 0.3F, 0.0F, -0.2F, 0.9F, 2.4F, 0.4F);
				gauntlet(p, rightArm, -3.2F);
				gauntlet(p, leftArm, -1.2F);
				p.solid(body, "belt", BAND_SCORCHED, zero, -4.4F, 7.0F, -2.4F, 8.8F, 1.4F, 4.8F);
				PartDefinition tongs = p.solid(body, "tongs", BAND_STEEL, PartPose.offsetAndRotation(-4.6F, 7.8F, 0.4F, 0.0F, 0.0F, rad(8.0F)),
						-0.3F, 0.0F, -0.6F, 0.6F, 5.0F, 0.4F, -0.3F, 0.0F, 0.2F, 0.6F, 5.0F, 0.4F);
				p.solid(tongs, "rivet", BAND_BRONZE, zero, -0.4F, 1.2F, -0.7F, 0.8F, 0.8F, 1.4F);
			}
			case KACAMATA -> {
				kainKepala(p, head);
				p.solid(head, "strap", BAND_LEATHER, zero, -4.5F, -6.3F, -4.5F, 9.0F, 0.6F, 9.0F);
				p.solid(head, "frames", BAND_BRONZE, zero, -3.5F, -6.7F, -4.95F, 2.7F, 2.3F, 0.6F, 0.8F, -6.7F, -4.95F, 2.7F, 2.3F, 0.6F,
						-0.8F, -5.9F, -4.85F, 1.6F, 0.5F, 0.4F);
				p.solid(head, "lenses", BAND_LENS, zero, -3.15F, -6.35F, -5.05F, 2.0F, 1.6F, 0.2F, 1.15F, -6.35F, -5.05F, 2.0F, 1.6F, 0.2F);
				p.solid(body, "dust_cloth", BAND_CLOTH, zero, -4.4F, 6.8F, -2.4F, 8.8F, 1.2F, 4.8F);
				p.solid(body, "chisels", BAND_STEEL, zero, 3.0F, 7.4F, -2.9F, 0.5F, 3.6F, 0.5F, 3.7F, 7.6F, -2.9F, 0.5F, 3.0F, 0.5F);
			}
			case TOPI_ALKEMIS -> {
				p.solid(head, "cone", BAND_PURPLE_SILK, zero, -3.5F, -10.4F, -3.5F, 7.0F, 2.6F, 7.0F, -2.0F, -13.0F, -2.0F, 4.0F, 2.6F, 4.0F);
				p.solid(head, "rim", BAND_GOLD, zero, -3.7F, -8.3F, -3.7F, 7.4F, 0.5F, 7.4F, -2.15F, -10.6F, -2.15F, 4.3F, 0.4F, 4.3F);
				p.solid(head, "ball", BAND_GOLD, zero, -1.0F, -14.6F, -1.0F, 2.0F, 1.8F, 2.0F);
				p.solid(head, "rim_band", BAND_BLACK, zero, -4.2F, -8.0F, -4.2F, 8.4F, 0.6F, 8.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				PartDefinition gourd = p.solid(body, "gourd", BAND_GOURD, PartPose.offset(4.6F, 8.6F, 0.4F),
						-1.2F, 0.0F, -1.2F, 2.4F, 2.4F, 2.4F, -0.7F, -1.4F, -0.7F, 1.4F, 1.4F, 1.4F);
				p.solid(gourd, "cord", BAND_CRIMSON, zero, -0.25F, -2.6F, -0.25F, 0.5F, 1.2F, 0.5F);
				p.solid(gourd, "stopper", BAND_WOOD, zero, -0.4F, -1.9F, -0.4F, 0.8F, 0.6F, 0.8F);
				p.solid(rightArm, "sleeve_tie", BAND_PURPLE_SILK, zero, -3.3F, 3.0F, -2.3F, 4.6F, 0.8F, 4.6F);
				p.solid(leftArm, "sleeve_tie", BAND_PURPLE_SILK, zero, -1.3F, 3.0F, -2.3F, 4.6F, 0.8F, 4.6F);
			}
			case TOPI_SARJANA, SARJANA_KIPAS -> {
				p.solid(head, "cap", BAND_BLACK, zero, -4.0F, -10.6F, -4.0F, 8.0F, 3.0F, 8.0F);
				p.solid(head, "board", BAND_LACQUER_GAUZE, zero, -4.5F, -11.2F, -4.5F, 9.0F, 0.6F, 9.0F);
				p.solid(head, "bead", BAND_JADE, zero, -0.5F, -9.6F, -4.3F, 1.0F, 1.0F, 0.4F);
				p.solid(head, "ribbon_l", BAND_BLACK, PartPose.offsetAndRotation(1.5F, -9.0F, 4.0F, rad(12.0F), 0.0F, 0.0F),
						-0.5F, 0.0F, -0.2F, 1.0F, 6.0F, 0.4F);
				p.solid(head, "ribbon_r", BAND_BLACK, PartPose.offsetAndRotation(-1.5F, -9.0F, 4.0F, rad(12.0F), 0.0F, 0.0F),
						-0.5F, 0.0F, -0.2F, 1.0F, 6.0F, 0.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				if (kind == SARJANA_KIPAS) {
					waistFan(p, body, 3.3F);
				} else {
					p.solid(body, "brush_case", BAND_WOOD, PartPose.offsetAndRotation(3.4F, 7.2F, -2.5F, 0.0F, 0.0F, rad(-10.0F)),
							-0.4F, 0.0F, -0.4F, 0.8F, 4.0F, 0.8F);
				}
			}
			case TOPI_NAKHODA -> {
				p.solid(head, "brim", BAND_DARK_STRAW, zero, -6.0F, -8.6F, -6.0F, 12.0F, 0.6F, 12.0F);
				p.solid(head, "crown", BAND_DARK_STRAW, zero, -3.5F, -11.4F, -3.5F, 7.0F, 2.8F, 7.0F);
				p.tinted(head, "band", zero, -3.7F, -9.2F, -3.7F, 7.4F, 0.7F, 7.4F);
				p.solid(head, "cords", BAND_ROPE, zero, -4.35F, -8.0F, -0.6F, 0.3F, 8.0F, 0.3F, 4.05F, -8.0F, -0.6F, 0.3F, 8.0F, 0.3F);
				p.solid(head, "toggle", BAND_BONE, zero, -0.5F, -0.6F, -4.3F, 1.0F, 0.8F, 0.6F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				p.solid(body, "compass", BAND_BRONZE, zero, 2.2F, 7.8F, -2.9F, 1.8F, 1.8F, 0.6F);
			}
			case KAIN_KEPALA -> {
				kainKepala(p, head);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			case TUKANG -> {
				kainKepala(p, head);
				p.solid(body, "tool_belt", BAND_LEATHER, zero, -4.4F, 6.6F, -2.4F, 8.8F, 1.5F, 4.8F);
				p.solid(body, "buckle", BAND_BRONZE, zero, -0.8F, 6.5F, -2.75F, 1.6F, 1.7F, 0.4F);
				p.solid(body, "pouches", BAND_SCORCHED, zero, -3.8F, 7.4F, -3.9F, 2.0F, 3.0F, 1.5F, 1.8F, 7.4F, -3.9F, 2.0F, 3.0F, 1.5F,
						2.0F, 7.4F, 2.4F, 2.0F, 3.0F, 1.5F);
				PartDefinition hammer = p.solid(body, "hammer", BAND_WOOD, PartPose.offsetAndRotation(-4.7F, 7.4F, 0.6F, 0.0F, 0.0F, rad(6.0F)),
						-0.4F, 0.0F, -0.4F, 0.8F, 4.4F, 0.8F);
				p.solid(hammer, "hammer_head", BAND_STEEL, zero, -0.9F, 3.8F, -1.6F, 1.8F, 1.6F, 3.2F);
				PartDefinition trowel = p.solid(body, "trowel", BAND_WOOD, PartPose.offsetAndRotation(4.7F, 7.4F, -0.6F, 0.0F, 0.0F, rad(-6.0F)),
						-0.35F, 0.0F, -0.35F, 0.7F, 2.4F, 0.7F);
				p.solid(trowel, "blade", BAND_STEEL, zero, -0.2F, 2.4F, -1.3F, 0.4F, 2.8F, 2.6F);
			}
			case TOPI_KOKI -> {
				p.solid(head, "cuff", BAND_LINEN, zero, -4.15F, -9.2F, -4.15F, 8.3F, 1.4F, 8.3F);
				p.solid(head, "puff", BAND_LINEN, zero, -4.0F, -12.0F, -4.0F, 8.0F, 2.8F, 8.0F, -4.5F, -12.9F, -4.5F, 9.0F, 1.2F, 9.0F);
				p.solid(body, "towel", BAND_LINEN, zero, -4.4F, -0.6F, -2.45F, 3.0F, 1.0F, 4.9F, -4.0F, 0.4F, -2.65F, 2.0F, 4.6F, 0.4F);
				p.solid(body, "apron_tie", BAND_LINEN, zero, -4.35F, 6.2F, -2.35F, 8.7F, 1.0F, 4.7F);
				p.solid(body, "ladle", BAND_WOOD, PartPose.offsetAndRotation(4.6F, 6.8F, 0.0F, 0.0F, 0.0F, rad(-6.0F)), -0.3F, 0.0F, -0.3F, 0.6F, 4.4F, 0.6F,
						-0.8F, 4.4F, -0.8F, 1.6F, 0.8F, 1.6F);
			}
			case TASBIH -> {
				// ten beads hanging from the neck in a U over the chest, a larger guru bead and a crimson tassel
				float[] beads = new float[10 * 6];
				for (int i = 0; i < 10; i++) {
					double t = Math.PI * i / 9.0D;
					float x = (float) (-3.0D * Math.cos(t));
					float y = (float) (0.6D + 5.6D * Math.sin(t));
					System.arraycopy(b(x - 0.5F, y - 0.5F, -2.95F, 1.0F, 1.0F, 1.0F), 0, beads, i * 6, 6);
				}
				p.solid(body, "beads", BAND_SANDALWOOD, zero, beads);
				p.solid(body, "guru_bead", BAND_SANDALWOOD, zero, -0.75F, 6.3F, -3.05F, 1.5F, 1.5F, 1.2F);
				p.solid(body, "tassel", BAND_CRIMSON, zero, -0.35F, 7.8F, -2.85F, 0.7F, 2.4F, 0.6F);
				p.solid(body, "back_cord", BAND_SANDALWOOD, zero, -3.5F, -0.3F, 1.9F, 7.0F, 0.6F, 0.6F);
				p.tinted(body, "sash", zero, -4.35F, 6.9F, -2.35F, 8.7F, 1.0F, 4.7F);
			}
			case CAPING -> caping(p, head, BAND_STRAW, BAND_ROPE);
			case KERANJANG -> {
				PartDefinition basket = p.solid(body, "basket", BAND_WICKER, PartPose.offset(0.0F, 0.0F, 0.0F), -3.5F, 0.5F, 2.2F, 7.0F, 9.0F, 5.0F);
				p.solid(basket, "rim", BAND_WOOD, zero, -3.8F, 0.0F, 2.0F, 7.6F, 0.8F, 5.4F, -3.8F, 8.9F, 2.0F, 7.6F, 0.8F, 5.4F);
				p.tinted(basket, "cloth_bolts", zero, -2.8F, -1.6F, 3.0F, 2.4F, 2.0F, 3.4F, -0.2F, -1.2F, 3.4F, 2.0F, 1.8F, 3.0F);
				p.solid(basket, "scroll", BAND_PAPER, PartPose.offsetAndRotation(2.0F, -0.4F, 5.2F, rad(-12.0F), 0.0F, 0.0F),
						-0.6F, -3.0F, -0.6F, 1.2F, 3.4F, 1.2F);
				p.solid(basket, "umbrella", BAND_WOOD, PartPose.offsetAndRotation(-2.6F, 0.0F, 6.4F, rad(-15.0F), 0.0F, rad(8.0F)),
						-0.3F, -6.0F, -0.3F, 0.6F, 6.0F, 0.6F);
				p.solid(body, "straps", BAND_LEATHER, zero, -2.8F, -0.2F, -2.5F, 1.0F, 9.0F, 0.5F, 1.8F, -0.2F, -2.5F, 1.0F, 9.0F, 0.5F,
						-2.8F, -0.45F, -2.4F, 1.0F, 0.5F, 4.8F, 1.8F, -0.45F, -2.4F, 1.0F, 0.5F, 4.8F);
				p.solid(body, "buckles", BAND_BRONZE, zero, -2.9F, 4.0F, -2.7F, 1.2F, 0.8F, 0.3F, 1.7F, 4.0F, -2.7F, 1.2F, 0.8F, 0.3F);
			}
			case TOPI_BULU -> {
				p.solid(head, "fur", BAND_FUR, zero, -4.5F, -10.6F, -4.5F, 9.0F, 3.4F, 9.0F, -3.8F, -11.0F, -3.8F, 7.6F, 0.6F, 7.6F,
						-4.7F, -7.6F, -2.0F, 0.6F, 3.4F, 4.0F, 4.1F, -7.6F, -2.0F, 0.6F, 3.4F, 4.0F);
				p.solid(head, "tail", BAND_FUR, PartPose.offsetAndRotation(0.0F, -9.5F, 4.0F, rad(35.0F), 0.0F, 0.0F), -1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F);
				p.solid(head, "toggle", BAND_BONE, zero, -0.6F, -9.4F, -4.75F, 1.2F, 1.4F, 0.4F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				for (int i = 0; i < 3; i++) {
					p.solid(body, "coil_" + i, BAND_ROPE, PartPose.offsetAndRotation(-4.7F, 9.2F, 0.0F, rad(i * 30.0F), 0.0F, 0.0F),
							-0.5F, -1.8F, -1.8F, 1.0F, 3.6F, 3.6F);
				}
				p.solid(body, "fang", BAND_BONE, zero, 2.4F, 8.0F, -2.75F, 0.6F, 1.6F, 0.4F);
			}
			case KIPAS_PINGGANG -> {
				p.tinted(body, "sash", zero, -4.4F, 6.6F, -2.4F, 8.8F, 1.5F, 4.8F);
				p.solid(body, "plaques", BAND_GOLD, zero, -3.4F, 6.8F, -2.6F, 1.2F, 1.1F, 0.3F, 2.2F, 6.8F, -2.6F, 1.2F, 1.1F, 0.3F);
				p.solid(body, "pendant", BAND_JADE, zero, -0.8F, 8.2F, -2.75F, 1.6F, 1.6F, 0.4F, -0.4F, 9.8F, -2.7F, 0.8F, 0.8F, 0.3F);
				p.solid(body, "pendant_cord", BAND_CRIMSON, zero, -0.25F, 10.6F, -2.65F, 0.5F, 2.0F, 0.3F);
				waistFan(p, body, -3.3F);
			}
			case LENCANA_KETUA -> {
				p.solid(body, "badge", BAND_STAR_GOLD, zero, 0.8F, 1.6F, -2.75F, 2.4F, 2.4F, 0.4F);
				p.solid(body, "badge_rim", BAND_BRONZE, zero, 0.6F, 1.4F, -2.6F, 2.8F, 2.8F, 0.3F);
				p.solid(body, "chain", BAND_GOLD, zero, 1.8F, -0.2F, -2.55F, 0.4F, 1.7F, 0.3F, -3.4F, -0.2F, -2.55F, 0.4F, 3.4F, 0.3F,
						-3.4F, 3.0F, -2.55F, 4.2F, 0.4F, 0.3F);
				p.tinted(body, "sash", zero, -4.4F, 6.6F, -2.4F, 8.8F, 1.6F, 4.8F);
				p.solid(body, "seal", BAND_JADE, PartPose.offset(-3.6F, 8.2F, -2.0F), -0.6F, 0.0F, -0.6F, 1.2F, 1.6F, 1.2F);
			}
			case LENCANA_PETUGAS -> {
				p.solid(body, "badge", BAND_STAR_GOLD, zero, 1.0F, 1.8F, -2.7F, 2.0F, 2.0F, 0.3F);
				p.solid(head, "brush", BAND_WOOD, PartPose.offsetAndRotation(4.1F, -5.6F, 0.6F, rad(-20.0F), 0.0F, 0.0F),
						-0.3F, -2.0F, -0.3F, 0.6F, 5.0F, 0.6F);
				p.solid(head, "brush_tip", BAND_BLACK, PartPose.offsetAndRotation(4.1F, -5.6F, 0.6F, rad(-20.0F), 0.0F, 0.0F),
						-0.4F, -3.0F, -0.4F, 0.8F, 1.0F, 0.8F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				PartDefinition pouch = p.solid(body, "ink_pouch", BAND_LEATHER, PartPose.offset(-3.0F, 8.0F, -2.6F), -1.0F, 0.0F, -0.5F, 2.0F, 2.2F, 1.0F);
				p.solid(pouch, "ink_stone", BAND_BLACK, zero, -0.7F, -0.6F, -0.4F, 1.4F, 0.6F, 0.8F);
			}
			case PEDANG_PUNGGUNG -> {
				p.solid(body, "strap", BAND_LEATHER, PartPose.offsetAndRotation(0.0F, 5.0F, -2.45F, 0.0F, 0.0F, rad(40.0F)), -0.5F, -7.5F, -0.2F, 1.0F, 15.0F, 0.4F);
				p.solid(body, "strap_buckle", BAND_BRONZE, PartPose.offsetAndRotation(0.0F, 5.0F, -2.6F, 0.0F, 0.0F, rad(40.0F)), -0.7F, -0.6F, -0.2F, 1.4F, 1.2F, 0.3F);
				PartDefinition back = p.solid(body, "scabbard", BAND_BLACK, PartPose.offsetAndRotation(0.0F, 6.0F, 2.7F, 0.0F, 0.0F, rad(-38.0F)),
						-0.8F, -6.0F, -0.6F, 1.6F, 14.0F, 1.2F);
				p.solid(back, "fittings", BAND_GOLD, zero, -0.95F, 7.4F, -0.75F, 1.9F, 1.0F, 1.5F, -0.95F, -1.0F, -0.75F, 1.9F, 0.6F, 1.5F,
						-0.95F, 3.0F, -0.75F, 1.9F, 0.6F, 1.5F);
				PartDefinition hilt = p.solid(back, HILT, BAND_GOLD, zero, -1.8F, -6.8F, -1.0F, 3.6F, 0.8F, 2.0F, -0.7F, -12.0F, -0.7F, 1.4F, 0.8F, 1.4F);
				p.solid(hilt, "grip", BAND_LEATHER, zero, -0.5F, -11.2F, -0.5F, 1.0F, 4.4F, 1.0F);
				p.solid(hilt, "tassel", BAND_CRIMSON, zero, -0.25F, -12.0F, 0.6F, 0.5F, 2.6F, 0.5F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				PartDefinition dagger = p.solid(body, "dagger", BAND_WOOD, PartPose.offsetAndRotation(-3.2F, 7.6F, -2.5F, 0.0F, 0.0F, rad(28.0F)),
						-0.45F, 0.0F, -0.45F, 0.9F, 4.0F, 0.9F);
				p.solid(dagger, "dagger_hilt", BAND_STEEL, zero, -0.3F, -1.6F, -0.3F, 0.6F, 1.6F, 0.6F, -0.8F, -0.3F, -0.5F, 1.6F, 0.4F, 1.0F);
			}
			case TOPI_SYAHBANDAR -> {
				p.solid(head, "crown", BAND_NAVY_GOLD, zero, -4.2F, -10.6F, -4.2F, 8.4F, 3.5F, 8.4F);
				p.solid(head, "front_wing", BAND_NAVY_GOLD, PartPose.offsetAndRotation(0.0F, -8.4F, -4.2F, rad(-28.0F), 0.0F, 0.0F),
						-4.5F, -0.3F, -3.0F, 9.0F, 0.6F, 3.0F);
				p.solid(head, "band", BAND_GOLD, zero, -4.35F, -7.6F, -4.35F, 8.7F, 0.6F, 8.7F);
				p.solid(head, "knob", BAND_JADE, zero, -0.75F, -12.1F, -0.75F, 1.5F, 1.5F, 1.5F);
				p.solid(head, "knob_seat", BAND_GOLD, zero, -1.1F, -10.9F, -1.1F, 2.2F, 0.4F, 2.2F);
				p.solid(body, "belt", BAND_NAVY_GOLD, zero, -4.4F, 7.0F, -2.4F, 8.8F, 1.4F, 4.8F);
				p.solid(body, "seal", BAND_JADE, PartPose.offset(3.6F, 8.2F, -2.0F), -0.6F, 0.0F, -0.6F, 1.2F, 1.6F, 1.2F);
			}
			case KALUNG_HIU -> {
				float[] teeth = new float[7 * 6];
				float[] cord = new float[8 * 6];
				for (int i = 0; i < 7; i++) {
					double t = Math.PI * (i + 1) / 8.0D;
					float x = (float) (-2.8D * Math.cos(t));
					float y = (float) (0.8D + 3.6D * Math.sin(t));
					System.arraycopy(b(x - 0.4F, y, -2.95F, 0.8F, 1.5F, 0.6F), 0, teeth, i * 6, 6);
				}
				for (int i = 0; i < 8; i++) {
					double t = Math.PI * (i + 0.5D) / 8.0D;
					float x = (float) (-2.8D * Math.cos(t));
					float y = (float) (0.8D + 3.6D * Math.sin(t));
					System.arraycopy(b(x - 0.6F, y - 0.25F, -2.75F, 1.2F, 0.5F, 0.4F), 0, cord, i * 6, 6);
				}
				p.solid(body, "teeth", BAND_TOOTH, zero, teeth);
				p.solid(body, "cord", BAND_ROPE, zero, cord);
				p.solid(head, "earring", BAND_GOLD, zero, -4.8F, -3.4F, -1.0F, 0.4F, 1.4F, 0.4F, 4.4F, -3.4F, -1.0F, 0.4F, 1.4F, 0.4F);
				p.solid(body, "rope_belt", BAND_ROPE, zero, -4.4F, 7.2F, -2.4F, 8.8F, 1.0F, 4.8F);
				PartDefinition knife = p.solid(body, "knife", BAND_LEATHER, PartPose.offsetAndRotation(3.4F, 7.8F, -2.5F, 0.0F, 0.0F, rad(-20.0F)),
						-0.45F, 0.0F, -0.4F, 0.9F, 3.6F, 0.8F);
				p.solid(knife, "knife_hilt", BAND_BONE, zero, -0.35F, -1.6F, -0.35F, 0.7F, 1.6F, 0.7F);
			}
			case SERBAN -> {
				p.solid(head, "wrap", BAND_TURBAN, zero, -4.5F, -10.2F, -4.5F, 9.0F, 3.6F, 9.0F);
				p.solid(head, "dome", BAND_TURBAN, zero, -3.6F, -11.2F, -3.6F, 7.2F, 1.0F, 7.2F);
				p.solid(head, "end", BAND_TURBAN, PartPose.offsetAndRotation(2.2F, -8.0F, 4.4F, rad(15.0F), 0.0F, rad(-6.0F)), -1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 1.0F);
				p.solid(head, "gem", BAND_JADE, zero, -0.6F, -9.2F, -4.85F, 1.2F, 1.2F, 0.4F);
				p.solid(head, "gem_seat", BAND_GOLD, zero, -0.9F, -9.5F, -4.7F, 1.8F, 1.8F, 0.3F);
				p.tinted(body, "sash", zero, -4.4F, 6.6F, -2.4F, 8.8F, 1.6F, 4.8F);
			}
			case MAHKOTA_LAUT -> {
				p.solid(head, "base", BAND_SEA_SILVER, zero, -3.0F, -9.5F, -3.0F, 6.0F, 1.5F, 6.0F);
				for (int i = 0; i < 5; i++) {
					float a = -60.0F + 30.0F * i;
					float h = i == 2 ? 3.6F : (i == 1 || i == 3 ? 3.0F : 2.4F);
					p.solid(head, "petal_" + i, BAND_SEA_SILVER,
							PartPose.offsetAndRotation(3.0F * Mth.sin(rad(a)), -9.4F, -3.0F * Mth.cos(rad(a)), rad(-12.0F), rad(-a), 0.0F),
							-0.5F, -h, -0.25F, 1.0F, h, 0.5F);
				}
				p.solid(head, "pearl", BAND_WHITE, zero, -0.6F, -10.8F, -3.8F, 1.2F, 1.2F, 1.0F);
				p.solid(body, "shell_pendant", BAND_SEA_SILVER, zero, -1.0F, 2.0F, -2.7F, 2.0F, 1.6F, 0.4F);
				p.solid(body, "pendant_cord", BAND_WHITE, zero, -2.4F, -0.2F, -2.55F, 0.3F, 2.4F, 0.2F, 2.1F, -0.2F, -2.55F, 0.3F, 2.4F, 0.2F);
			}
			case SERBAN_EMAS -> {
				p.solid(head, "wrap", BAND_GOLD_BROCADE, zero, -4.7F, -10.6F, -4.7F, 9.4F, 4.5F, 9.4F);
				p.solid(head, "dome", BAND_GOLD_BROCADE, zero, -3.8F, -11.6F, -3.8F, 7.6F, 1.0F, 7.6F);
				p.solid(head, "brooch", BAND_CRIMSON, zero, -1.0F, -9.8F, -5.1F, 2.0F, 2.0F, 0.5F);
				p.solid(head, "brooch_rim", BAND_GOLD, zero, -1.3F, -10.1F, -4.95F, 2.6F, 2.6F, 0.3F);
				p.solid(head, "plume", BAND_WHITE, PartPose.offsetAndRotation(0.0F, -10.4F, -4.9F, rad(-8.0F), 0.0F, 0.0F), -0.5F, -4.0F, -0.4F, 1.0F, 4.0F, 0.8F);
				p.tinted(body, "sash", zero, -4.45F, 6.4F, -2.45F, 8.9F, 1.8F, 4.9F);
				hipSword(p, body);
			}
			case KERUDUNG_PASIR -> {
				p.solid(head, "hood", BAND_SAND_CLOTH, zero, -4.4F, -8.8F, -4.4F, 8.8F, 0.8F, 8.8F, -4.4F, -8.8F, 3.4F, 8.8F, 9.4F, 1.0F,
						-4.4F, -8.8F, -4.4F, 0.8F, 9.0F, 8.0F, 3.6F, -8.8F, -4.4F, 0.8F, 9.0F, 8.0F);
				p.solid(head, "brow", BAND_SAND_CLOTH, zero, -4.4F, -8.8F, -4.6F, 8.8F, 4.4F, 0.6F);
				p.solid(head, "veil", BAND_SAND_CLOTH, zero, -4.4F, -3.0F, -4.6F, 8.8F, 3.4F, 0.6F);
				p.solid(head, "circlet", BAND_ROPE, zero, -4.6F, -7.9F, -4.7F, 9.2F, 0.7F, 9.2F);
				p.solid(head, "tail", BAND_SAND_CLOTH, PartPose.offsetAndRotation(0.0F, -2.0F, 4.4F, rad(10.0F), 0.0F, 0.0F), -2.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F);
				p.solid(body, "belt", BAND_ROPE, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.0F, 4.6F);
				PartDefinition skin = p.solid(body, "water_skin", BAND_LEATHER, PartPose.offset(4.7F, 8.0F, 0.6F), -1.0F, 0.0F, -1.4F, 2.0F, 3.4F, 2.8F);
				p.solid(skin, "skin_neck", BAND_LEATHER, zero, -0.5F, -1.0F, -0.5F, 1.0F, 1.0F, 1.0F);
			}
			case TOPI_BULU_LEBAR -> {
				p.solid(head, "brim", BAND_FUR, zero, -5.5F, -9.6F, -5.5F, 11.0F, 2.0F, 11.0F);
				p.solid(head, "crown", BAND_COIN_BROCADE, zero, -3.5F, -12.4F, -3.5F, 7.0F, 2.8F, 7.0F);
				p.solid(head, "tassel", BAND_CRIMSON, zero, -0.4F, -13.6F, -0.4F, 0.8F, 1.2F, 0.8F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.4F, 7.0F, -2.4F, 8.8F, 1.4F, 4.8F);
				p.solid(body, "buckle", BAND_DESERT_BRONZE, zero, -1.0F, 6.8F, -2.8F, 2.0F, 1.8F, 0.4F);
				for (int i = 0; i < 3; i++) {
					p.solid(body, "whip_" + i, BAND_LEATHER, PartPose.offsetAndRotation(4.7F, 9.0F, 0.0F, rad(i * 30.0F), 0.0F, 0.0F),
							-0.4F, -1.6F, -1.6F, 0.8F, 3.2F, 3.2F);
				}
			}
			case HELM_RUNCING -> {
				p.solid(head, "helm", BAND_DESERT_BRONZE, zero, -4.4F, -9.4F, -4.4F, 8.8F, 5.0F, 8.8F);
				p.solid(head, "spike", BAND_DESERT_BRONZE, zero, -3.0F, -10.6F, -3.0F, 6.0F, 1.2F, 6.0F, -2.0F, -11.8F, -2.0F, 4.0F, 1.2F, 4.0F,
						-1.0F, -13.4F, -1.0F, 2.0F, 1.6F, 2.0F);
				p.solid(head, "tip", BAND_GOLD, zero, -0.4F, -14.4F, -0.4F, 0.8F, 1.0F, 0.8F);
				p.solid(head, "nasal", BAND_DESERT_BRONZE, zero, -0.4F, -5.2F, -4.85F, 0.8F, 2.6F, 0.5F);
				p.solid(head, "rim", BAND_GOLD, zero, -4.55F, -4.9F, -4.55F, 9.1F, 0.5F, 9.1F);
				p.solid(head, "mail", BAND_STEEL, zero, -4.65F, -4.6F, -3.0F, 0.4F, 4.0F, 7.2F, 4.25F, -4.6F, -3.0F, 0.4F, 4.0F, 7.2F,
						-4.65F, -4.6F, 3.8F, 9.3F, 4.4F, 0.8F);
				p.solid(head, "pennant", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -13.0F, 0.0F, rad(30.0F), 0.0F, 0.0F), -0.2F, 0.0F, 0.0F, 0.4F, 3.0F, 1.6F);
			}
			case CAPING_BIRU -> {
				caping(p, head, BAND_BLUE_STRAW, BAND_ROPE);
				p.tinted(head, "cloth_rim", zero, -5.9F, -8.2F, -5.9F, 11.8F, 0.4F, 11.8F);
				p.tinted(head, "curtain", zero, -5.6F, -7.9F, 3.8F, 11.2F, 3.4F, 0.3F, -5.9F, -7.9F, -2.0F, 0.3F, 2.6F, 5.8F,
						5.6F, -7.9F, -2.0F, 0.3F, 2.6F, 5.8F);
			}
			case IKAT_KUNING -> {
				p.solid(head, "band", BAND_SUTRA_CLOTH, zero, -4.3F, -7.4F, -4.3F, 8.6F, 1.5F, 8.6F);
				p.solid(head, "tail_l", BAND_SUTRA_CLOTH, PartPose.offsetAndRotation(0.6F, -6.8F, 4.3F, rad(25.0F), 0.0F, rad(-8.0F)), -0.5F, 0.0F, -0.25F, 1.0F, 4.0F, 0.5F);
				p.solid(head, "tail_r", BAND_SUTRA_CLOTH, PartPose.offsetAndRotation(-0.6F, -6.8F, 4.3F, rad(20.0F), 0.0F, rad(10.0F)), -0.5F, 0.0F, -0.25F, 1.0F, 3.4F, 0.5F);
				p.solid(rightArm, "wrist_beads", BAND_SANDALWOOD, zero, -3.2F, 7.4F, -2.2F, 4.4F, 0.8F, 4.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			case TALI_MELINGKAR -> {
				p.solid(body, "rope_front", BAND_ROPE, PartPose.offsetAndRotation(0.0F, 5.5F, -2.5F, 0.0F, 0.0F, rad(40.0F)), -0.7F, -7.0F, -0.6F, 1.4F, 14.0F, 1.2F);
				p.solid(body, "rope_back", BAND_ROPE, PartPose.offsetAndRotation(0.0F, 5.5F, 2.5F, 0.0F, 0.0F, rad(40.0F)), -0.7F, -7.0F, -0.6F, 1.4F, 14.0F, 1.2F);
				for (int i = 0; i < 3; i++) {
					p.solid(body, "coil_" + i, BAND_ROPE, PartPose.offsetAndRotation(-4.8F, 8.6F, 0.0F, rad(i * 30.0F), 0.0F, 0.0F),
							-0.6F, -2.4F, -2.4F, 1.2F, 4.8F, 4.8F);
				}
				p.solid(body, "hook", BAND_STEEL, zero, -5.6F, 6.4F, -0.4F, 0.6F, 1.6F, 0.8F);
				p.solid(body, "herb_pouch", BAND_LEATHER, zero, 2.4F, 7.6F, -2.9F, 1.8F, 2.2F, 0.9F);
				p.solid(body, "herb_leaves", BAND_JADE, zero, 2.6F, 7.0F, -2.8F, 1.4F, 0.8F, 0.6F);
			}
			case TOPENG_RANTAI -> {
				ironMask(p, head);
				shoulderChain(p, body);
				p.solid(leftArm, "pauldron", BAND_BLACK_IRON, PartPose.rotation(0.0F, 0.0F, rad(-10.0F)), -1.6F, -2.8F, -2.7F, 5.4F, 3.8F, 5.4F,
						-1.2F, -3.6F, -1.0F, 4.6F, 0.8F, 2.0F);
				p.solid(leftArm, "spikes", BAND_BONE, PartPose.rotation(0.0F, 0.0F, rad(-10.0F)), 1.0F, -4.4F, -0.4F, 0.8F, 1.0F, 0.8F,
						2.6F, -4.2F, -0.4F, 0.8F, 0.8F, 0.8F);
			}
			case RANTAI_BAHU -> {
				shoulderChain(p, body);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.4F, 7.0F, -2.4F, 8.8F, 1.6F, 4.8F);
				PartDefinition hook = p.solid(body, "meat_hook", BAND_BLACK_IRON, PartPose.offset(-4.7F, 7.6F, -0.4F), -0.3F, 0.0F, -0.3F, 0.6F, 3.0F, 0.6F);
				p.solid(hook, "curve", BAND_BLACK_IRON, zero, -0.3F, 2.6F, -1.4F, 0.6F, 0.6F, 1.4F, -0.3F, 1.8F, -1.6F, 0.6F, 1.0F, 0.5F);
			}
			case MAHKOTA_TANDUK -> {
				p.solid(head, "circlet", BAND_BLOOD_LACQUER, zero, -4.3F, -8.8F, -4.3F, 8.6F, 2.0F, 8.6F);
				p.solid(head, "gem", BAND_CRIMSON, zero, -0.7F, -8.6F, -4.7F, 1.4F, 1.6F, 0.5F);
				p.solid(head, "points", BAND_BLACK_IRON, zero, -2.4F, -9.8F, -4.45F, 0.8F, 1.2F, 0.6F, 1.6F, -9.8F, -4.45F, 0.8F, 1.2F, 0.6F,
						-0.4F, -10.2F, -4.45F, 0.8F, 1.6F, 0.6F);
				horn(p, head, 1.0F);
				horn(p, head, -1.0F);
			}
			case CADAR_ULAR -> {
				p.solid(head, "veil", BAND_GAUZE, zero, -4.2F, -3.0F, -4.7F, 8.4F, 4.0F, 0.4F);
				p.solid(head, "veil_coins", BAND_GOLD, zero, -3.4F, 0.8F, -4.8F, 0.8F, 0.8F, 0.2F, -1.2F, 0.9F, -4.8F, 0.8F, 0.8F, 0.2F,
						1.0F, 0.9F, -4.8F, 0.8F, 0.8F, 0.2F, 2.8F, 0.8F, -4.8F, 0.8F, 0.8F, 0.2F);
				p.solid(head, "crown", BAND_SNAKE, zero, -3.0F, -9.6F, -3.0F, 6.0F, 1.6F, 6.0F);
				PartDefinition snake = p.solid(head, "snake", BAND_SNAKE, PartPose.offsetAndRotation(0.0F, -9.4F, -3.0F, rad(-18.0F), 0.0F, 0.0F),
						-0.6F, -3.0F, -0.6F, 1.2F, 3.0F, 1.2F, -0.9F, -4.2F, -1.6F, 1.8F, 1.2F, 2.2F);
				p.solid(snake, "eyes", BAND_GOLD, zero, -1.0F, -4.0F, -1.5F, 0.3F, 0.4F, 0.4F, 0.7F, -4.0F, -1.5F, 0.3F, 0.4F, 0.4F);
				p.solid(snake, "tongue", BAND_CRIMSON, zero, -0.15F, -3.4F, -2.2F, 0.3F, 0.2F, 0.8F);
				p.solid(head, "earring", BAND_GOLD, zero, -4.8F, -3.4F, -1.0F, 0.4F, 1.6F, 0.4F, 4.4F, -3.4F, -1.0F, 0.4F, 1.6F, 0.4F);
			}
			case PENUTUP_MATA -> {
				p.solid(head, "blindfold", BAND_RUNE_CLOTH, zero, -4.3F, -4.8F, -4.3F, 8.6F, 2.0F, 8.6F);
				p.solid(head, "knot", BAND_RUNE_CLOTH, zero, -0.8F, -4.6F, 4.2F, 1.6F, 1.6F, 0.8F);
				p.solid(head, "end_l", BAND_RUNE_CLOTH, PartPose.offsetAndRotation(0.5F, -3.8F, 4.7F, rad(12.0F), 0.0F, rad(-6.0F)), -0.5F, 0.0F, -0.2F, 1.0F, 6.0F, 0.4F);
				p.solid(head, "end_r", BAND_RUNE_CLOTH, PartPose.offsetAndRotation(-0.5F, -3.8F, 4.7F, rad(10.0F), 0.0F, rad(7.0F)), -0.5F, 0.0F, -0.2F, 1.0F, 5.0F, 0.4F);
				p.solid(body, "talisman", BAND_PAPER, zero, -2.8F, 6.8F, -2.7F, 1.4F, 3.6F, 0.2F, 1.4F, 6.8F, -2.7F, 1.4F, 3.6F, 0.2F);
				p.solid(body, "belt", BAND_RUNE_CLOTH, zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.0F, 4.7F);
			}
			case TUDUNG -> {
				p.solid(head, "hood", BAND_CHARCOAL, zero, -4.5F, -9.0F, -5.5F, 9.0F, 1.0F, 10.0F, -4.5F, -9.0F, 3.5F, 9.0F, 9.5F, 1.0F,
						-4.5F, -9.0F, -5.5F, 1.0F, 8.6F, 9.0F, 3.5F, -9.0F, -5.5F, 1.0F, 8.6F, 9.0F);
				p.solid(head, "lip", BAND_CHARCOAL, zero, -4.5F, -8.6F, -5.8F, 9.0F, 1.2F, 0.6F);
				p.solid(head, "mask", BAND_CHARCOAL, zero, -4.2F, -3.0F, -4.6F, 8.4F, 3.0F, 0.5F);
				p.solid(body, "cape", BAND_CHARCOAL, zero, -4.6F, -0.4F, -2.6F, 9.2F, 2.6F, 5.2F);
				p.solid(body, "sheath_l", BAND_LEATHER, PartPose.offsetAndRotation(1.6F, 4.0F, 2.6F, 0.0F, 0.0F, rad(30.0F)), -0.5F, -3.0F, -0.4F, 1.0F, 6.0F, 0.8F);
				p.solid(body, "sheath_r", BAND_LEATHER, PartPose.offsetAndRotation(-1.6F, 4.0F, 2.6F, 0.0F, 0.0F, rad(-30.0F)), -0.5F, -3.0F, -0.4F, 1.0F, 6.0F, 0.8F);
				p.solid(body, "belt", BAND_BLACK, zero, -4.35F, 7.0F, -2.35F, 8.7F, 1.0F, 4.7F);
				p.solid(body, "darts", BAND_STEEL, zero, 2.0F, 6.6F, -2.8F, 0.4F, 1.6F, 0.4F, 2.7F, 6.6F, -2.8F, 0.4F, 1.6F, 0.4F, 3.4F, 6.6F, -2.8F,
						0.4F, 1.6F, 0.4F);
			}
			default -> {
			}
		}
	}

	// ================================================================== DESIGN 42 kinds (42.12.4, 42V-B)

	/**
	 * The Kota yang Hidup outfits (kinds 61-79). Coordinates in model pixels on the player skeleton; front = -z; the head
	 * spans y -8..0, the body y 0..12, an arm y -2..10 from its shoulder pivot, a leg y 0..12 from its hip pivot.
	 */
	private static void mesh42(int kind, Pass p, PartDefinition root) {
		PartPose zero = PartPose.ZERO;
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");
		PartDefinition rightLeg = root.getChild("right_leg");
		PartDefinition leftLeg = root.getChild("left_leg");
		switch (kind) {
			case TOPI_PENGANTIN -> {
				// the groom: a red official's hat with a back hump, gold flower and two paddle wings; the great red silk ball
				// flower on the chest with its sash over the shoulder; a red belt with jade plaques
				p.solid(head, "crown", BAND_CRIMSON, zero, -4.3F, -10.6F, -4.3F, 8.6F, 2.8F, 8.6F, -3.4F, -12.4F, 0.4F, 6.8F, 2.0F, 3.8F);
				p.solid(head, "brim", BAND_PHOENIX_SILK, zero, -4.5F, -8.3F, -4.5F, 9.0F, 0.7F, 9.0F);
				p.solid(head, "hat_flower", BAND_GOLD, zero, -0.9F, -10.2F, -4.75F, 1.8F, 1.8F, 0.4F, -0.5F, -11.0F, -4.7F, 1.0F, 0.8F, 0.3F);
				p.solid(head, "wing_l", BAND_CRIMSON, PartPose.offsetAndRotation(3.4F, -11.4F, 2.8F, 0.0F, 0.0F, rad(-8.0F)),
						0.0F, -0.25F, -0.3F, 2.8F, 0.5F, 0.6F, 2.8F, -0.3F, -1.1F, 3.2F, 0.6F, 2.2F);
				p.solid(head, "wing_r", BAND_CRIMSON, PartPose.offsetAndRotation(-3.4F, -11.4F, 2.8F, 0.0F, 0.0F, rad(8.0F)),
						-2.8F, -0.25F, -0.3F, 2.8F, 0.5F, 0.6F, -6.0F, -0.3F, -1.1F, 3.2F, 0.6F, 2.2F);
				p.solid(head, "wing_gold", BAND_GOLD, zero, 6.1F, -11.8F, 1.6F, 0.4F, 0.8F, 2.4F, -6.5F, -11.8F, 1.6F, 0.4F, 0.8F, 2.4F);
				p.solid(body, "sash_front", BAND_SILK_FLOWER, PartPose.offsetAndRotation(0.0F, 5.6F, -2.45F, 0.0F, 0.0F, rad(-38.0F)),
						-0.9F, -7.6F, -0.2F, 1.8F, 15.2F, 0.4F);
				p.solid(body, "sash_back", BAND_SILK_FLOWER, PartPose.offsetAndRotation(0.0F, 5.6F, 2.45F, 0.0F, 0.0F, rad(38.0F)),
						-0.9F, -7.6F, -0.2F, 1.8F, 15.2F, 0.4F);
				PartDefinition flower = p.solid(body, "ball_flower", BAND_SILK_FLOWER, PartPose.offset(0.0F, 2.6F, -3.0F),
						-1.8F, -1.6F, -1.0F, 3.6F, 3.2F, 1.6F, -2.4F, -1.0F, -0.6F, 4.8F, 2.0F, 1.0F, -1.2F, -2.2F, -0.6F, 2.4F, 4.4F, 1.0F);
				p.solid(flower, "flower_heart", BAND_GOLD, zero, -0.6F, -0.6F, -1.3F, 1.2F, 1.2F, 0.4F);
				p.solid(flower, "flower_tails", BAND_SILK_FLOWER, zero, -1.6F, 1.4F, -0.4F, 0.8F, 4.0F, 0.3F, 0.8F, 1.4F, -0.4F, 0.8F, 3.4F, 0.3F);
				p.solid(body, "belt", BAND_PHOENIX_SILK, zero, -4.4F, 6.8F, -2.4F, 8.8F, 1.4F, 4.8F);
				p.solid(body, "plaques", BAND_JADE, zero, -3.3F, 6.95F, -2.6F, 1.3F, 1.1F, 0.3F, -0.65F, 6.95F, -2.6F, 1.3F, 1.1F, 0.3F,
						2.0F, 6.95F, -2.6F, 1.3F, 1.1F, 0.3F);
			}
			case MAHKOTA_FENGGUAN -> {
				// the bride: the phoenix crown (8 x 4 x 8 of kingfisher enamel on a gold rim), three gold phoenixes with
				// silk tails, pearl tassels at the temples and a fringe over the brow, the red gauze veil over the face, a
				// phoenix-silk cloud collar on the shoulders
				p.solid(head, "crown", BAND_DIANCUI, zero, -4.0F, -11.6F, -4.0F, 8.0F, 4.0F, 8.0F);
				p.solid(head, "crown_rim", BAND_GOLD, zero, -4.4F, -8.2F, -4.4F, 8.8F, 0.8F, 8.8F, -4.2F, -11.9F, -4.2F, 8.4F, 0.5F, 8.4F);
				p.solid(head, "crown_points", BAND_GOLD, zero, -3.9F, -12.9F, -4.3F, 1.0F, 1.2F, 0.4F, -0.5F, -13.3F, -4.3F, 1.0F, 1.6F, 0.4F,
						2.9F, -12.9F, -4.3F, 1.0F, 1.2F, 0.4F, -4.3F, -12.9F, -0.5F, 0.4F, 1.2F, 1.0F, 3.9F, -12.9F, -0.5F, 0.4F, 1.2F, 1.0F);
				phoenix(p, head, "phoenix_c", 0.0F, -12.4F, -0.6F, 1.0F);
				phoenix(p, head, "phoenix_l", 3.2F, -11.8F, 0.8F, 0.75F);
				phoenix(p, head, "phoenix_r", -3.2F, -11.8F, 0.8F, 0.75F);
				p.solid(head, "fringe", BAND_PEARLS, zero, -4.0F, -8.0F, -4.6F, 8.0F, 1.8F, 0.3F);
				for (int side = -1; side <= 1; side += 2) {
					String s = side > 0 ? "_l" : "_r";
					float x = side * 4.5F;
					p.solid(head, "tassel_a" + s, BAND_PEARLS, PartPose.offset(x, -8.0F, -1.6F), -0.4F, 0.0F, -0.4F, 0.8F, 7.0F, 0.8F);
					p.solid(head, "tassel_b" + s, BAND_PEARLS, PartPose.offset(x, -8.0F, 0.2F), -0.4F, 0.0F, -0.4F, 0.8F, 8.5F, 0.8F);
					p.solid(head, "tassel_c" + s, BAND_PEARLS, PartPose.offset(x, -8.0F, 2.0F), -0.4F, 0.0F, -0.4F, 0.8F, 6.0F, 0.8F);
					p.solid(head, "tassel_end" + s, BAND_SILK_FLOWER, zero, x - 0.5F, -1.0F, -0.1F, 1.0F, 1.0F, 1.0F);
				}
				p.solid(head, "veil", BAND_RED_GAUZE, zero, -4.4F, -7.8F, -4.9F, 8.8F, 9.0F, 0.1F);
				p.solid(head, "veil_hem", BAND_GOLD, zero, -4.45F, 1.1F, -4.95F, 8.9F, 0.4F, 0.2F);
				p.solid(body, "cloud_collar", BAND_PHOENIX_SILK, zero, -4.9F, -0.4F, -2.9F, 9.8F, 2.2F, 5.8F, -2.6F, 1.6F, -2.95F, 5.2F, 1.8F, 0.4F);
				p.solid(body, "collar_tassels", BAND_GOLD, zero, -2.4F, 3.4F, -3.0F, 0.4F, 1.6F, 0.3F, -0.2F, 3.4F, -3.0F, 0.4F, 2.0F, 0.3F,
						2.0F, 3.4F, -3.0F, 0.4F, 1.6F, 0.3F);
				p.solid(body, "belt", BAND_PHOENIX_SILK, zero, -4.4F, 6.6F, -2.4F, 8.8F, 1.4F, 4.8F);
				p.solid(body, "belt_jade", BAND_JADE, zero, -0.8F, 7.6F, -2.7F, 1.6F, 2.0F, 0.4F);
			}
			case TOPI_KABUNG -> kabung(p, head, body, leftArm, false);
			case TOPI_DUKA -> {
				// the funeral master: the tall mourning cap, a hemp sash over the shoulder, a bronze hand bell and the pouch
				// of paper money he scatters before the coffin
				kabung(p, head, body, leftArm, true);
				p.solid(body, "hemp_sash", BAND_HEMP, PartPose.offsetAndRotation(0.0F, 5.6F, -2.5F, 0.0F, 0.0F, rad(36.0F)), -0.8F, -7.4F, -0.2F,
						1.6F, 14.8F, 0.4F);
				PartDefinition bell = p.solid(body, "bell", BAND_BRONZE, PartPose.offset(-4.7F, 8.4F, -0.6F), -0.9F, 0.0F, -0.9F, 1.8F, 2.0F, 1.8F,
						-0.6F, 2.0F, -0.6F, 1.2F, 0.4F, 1.2F);
				p.solid(bell, "bell_handle", BAND_WOOD, zero, -0.3F, -1.6F, -0.3F, 0.6F, 1.6F, 0.6F);
				p.solid(body, "money_pouch", BAND_PAPER, zero, 2.6F, 7.8F, -2.9F, 1.8F, 2.4F, 0.8F);
				p.solid(body, "money_tie", BAND_HEMP, zero, 2.5F, 7.6F, -3.0F, 2.0F, 0.4F, 1.0F);
			}
			case TOPI_DINGIN -> {
				// winter: a rabbit-fur hat with a rolled brim and ear flaps, a red button, the fur collar of the padded coat, a
				// quilted indigo scarf with one hanging end, padded cuffs
				p.solid(head, "fur_crown", BAND_WINTER_FUR, zero, -4.5F, -10.2F, -4.5F, 9.0F, 3.0F, 9.0F);
				p.solid(head, "fur_brim", BAND_WINTER_FUR, zero, -4.9F, -7.8F, -4.9F, 9.8F, 1.4F, 9.8F);
				p.solid(head, "flap_l", BAND_WINTER_FUR, zero, 4.2F, -7.0F, -2.2F, 0.8F, 4.8F, 4.4F);
				p.solid(head, "flap_r", BAND_WINTER_FUR, zero, -5.0F, -7.0F, -2.2F, 0.8F, 4.8F, 4.4F);
				p.solid(head, "flap_ties", BAND_ROPE, zero, 4.4F, -2.2F, -0.3F, 0.4F, 1.8F, 0.4F, -4.8F, -2.2F, -0.3F, 0.4F, 1.8F, 0.4F);
				p.solid(head, "button", BAND_CRIMSON, zero, -0.7F, -10.9F, -0.7F, 1.4F, 0.8F, 1.4F);
				p.solid(body, "fur_collar", BAND_WINTER_FUR, zero, -5.0F, -0.8F, -3.0F, 10.0F, 2.2F, 6.0F);
				p.solid(body, "scarf", BAND_QUILTED, zero, -4.6F, 0.6F, -2.8F, 9.2F, 1.4F, 5.6F);
				p.solid(body, "scarf_end", BAND_QUILTED, PartPose.offsetAndRotation(-2.0F, 1.6F, -2.9F, 0.0F, 0.0F, rad(6.0F)), -0.9F, 0.0F, -0.3F,
						1.8F, 6.2F, 0.6F);
				p.solid(rightArm, "cuff", BAND_QUILTED, zero, -3.4F, 7.0F, -2.4F, 4.8F, 2.2F, 4.8F);
				p.solid(leftArm, "cuff", BAND_QUILTED, zero, -1.4F, 7.0F, -2.4F, 4.8F, 2.2F, 4.8F);
			}
			case IKAT_AKROBAT -> akrobat(p, head, body, rightArm, leftArm, rightLeg, leftLeg);
			case EGRANG -> {
				// on stilts: red-lacquered poles 2 x 16 x 2 strapped to the shins with two rope bindings, a footrest under each
				// sole; the pole reaches 12 px below the foot (the clip egrang_jalan lifts the body by 0.75 block)
				akrobat(p, head, body, rightArm, leftArm, rightLeg, leftLeg);
				for (PartDefinition leg : new PartDefinition[] {rightLeg, leftLeg}) {
					p.solid(leg, "stilt", BAND_STILT, zero, -1.0F, 8.0F, -1.0F, 2.0F, 16.0F, 2.0F);
					p.solid(leg, "footrest", BAND_WOOD, zero, -2.2F, 12.0F, -2.6F, 4.4F, 0.8F, 4.8F);
					p.solid(leg, "bindings", BAND_ROPE, zero, -2.3F, 7.4F, -2.3F, 4.6F, 1.0F, 4.6F, -2.3F, 10.0F, -2.3F, 4.6F, 1.0F, 4.6F);
					p.solid(leg, "stilt_foot", BAND_BLACK_IRON, zero, -1.2F, 23.2F, -1.2F, 2.4F, 0.8F, 2.4F);
				}
			}
			case SELENDANG_PENARI -> {
				// the dancer: long water sleeves flowing from the wrists, the pibo shawl over the shoulder blades and down both
				// arms, a silk flower hairpin with a pearl dangle, a sash with two long tails
				p.solid(rightArm, "water_sleeve", BAND_DANCER, PartPose.offsetAndRotation(-1.0F, 9.0F, 0.0F, rad(6.0F), 0.0F, 0.0F),
						-2.2F, 0.0F, -0.4F, 4.4F, 13.0F, 0.8F);
				p.solid(leftArm, "water_sleeve", BAND_DANCER, PartPose.offsetAndRotation(1.0F, 9.0F, 0.0F, rad(6.0F), 0.0F, 0.0F),
						-2.2F, 0.0F, -0.4F, 4.4F, 13.0F, 0.8F);
				p.solid(rightArm, "sleeve_cuff", BAND_DANCER, zero, -3.3F, 8.0F, -2.3F, 4.6F, 1.6F, 4.6F);
				p.solid(leftArm, "sleeve_cuff", BAND_DANCER, zero, -1.3F, 8.0F, -2.3F, 4.6F, 1.6F, 4.6F);
				p.solid(body, "pibo_back", BAND_DANCER, PartPose.offsetAndRotation(0.0F, 2.6F, 2.6F, rad(18.0F), 0.0F, 0.0F),
						-6.4F, 0.0F, -0.2F, 12.8F, 1.6F, 0.4F);
				p.solid(rightArm, "pibo", BAND_DANCER, PartPose.offsetAndRotation(-3.2F, 1.6F, 1.6F, rad(10.0F), 0.0F, rad(4.0F)),
						-0.3F, 0.0F, -0.8F, 0.6F, 15.0F, 1.6F);
				p.solid(leftArm, "pibo", BAND_DANCER, PartPose.offsetAndRotation(3.2F, 1.6F, 1.6F, rad(10.0F), 0.0F, rad(-4.0F)),
						-0.3F, 0.0F, -0.8F, 0.6F, 15.0F, 1.6F);
				p.solid(head, "hair_flower", BAND_SILK_FLOWER, zero, 3.0F, -8.6F, -1.4F, 1.8F, 1.8F, 1.8F);
				p.solid(head, "flower_dangle", BAND_PEARLS, zero, 3.6F, -6.8F, -0.8F, 0.6F, 3.0F, 0.6F);
				p.solid(body, "sash", BAND_DANCER, zero, -4.4F, 5.6F, -2.4F, 8.8F, 1.8F, 4.8F);
				p.solid(body, "sash_tails", BAND_DANCER, PartPose.offsetAndRotation(1.4F, 7.4F, -2.6F, 0.0F, 0.0F, rad(-6.0F)), -0.6F, 0.0F, -0.2F,
						1.2F, 9.0F, 0.4F, 0.9F, 0.0F, -0.2F, 1.0F, 7.5F, 0.4F);
			}
			case TOPENG_KERTAS -> {
				// the Ghost Market seller: an 8 x 6 painted paper mask (its north face is the painted field of the band), tied
				// with a hemp cord, and a green jade charm at the belt that glints under the green lanterns
				p.solid(head, "mask", BAND_PAPER_MASK, zero, -4.0F, -7.0F, -4.9F, 8.0F, 6.0F, 1.0F);
				p.solid(head, "mask_cord", BAND_ROPE, zero, -4.3F, -5.6F, -3.9F, 0.3F, 0.4F, 8.2F, 4.0F, -5.6F, -3.9F, 0.3F, 0.4F, 8.2F,
						-4.3F, -5.6F, 4.0F, 8.6F, 0.4F, 0.3F);
				p.solid(head, "cord_knot", BAND_ROPE, zero, -0.6F, -6.0F, 4.2F, 1.2F, 1.2F, 0.6F, -0.3F, -4.8F, 4.4F, 0.6F, 2.4F, 0.3F);
				p.solid(body, "belt", BAND_CHARCOAL, zero, -4.35F, 7.0F, -2.35F, 8.7F, 1.0F, 4.7F);
				p.solid(body, "charm", BAND_JADE, zero, 1.8F, 8.0F, -2.75F, 1.4F, 1.8F, 0.4F);
				p.solid(body, "charm_cord", BAND_SNAKE, zero, 2.3F, 9.8F, -2.7F, 0.4F, 1.6F, 0.3F);
			}
			case CAPING_RONDA -> {
				// the night watchman: a three-tier oiled-cloth hat with a neck curtain against the dew, a padded collar, a belt
				// with a bamboo whistle and the tally of watches (five notched sticks)
				p.solid(head, "hat", BAND_OILED, zero, -6.2F, -8.9F, -6.2F, 12.4F, 0.9F, 12.4F, -4.2F, -10.2F, -4.2F, 8.4F, 1.3F, 8.4F,
						-2.0F, -11.4F, -2.0F, 4.0F, 1.2F, 4.0F);
				p.solid(head, "hat_tip", BAND_BRONZE, zero, -0.5F, -12.0F, -0.5F, 1.0F, 0.6F, 1.0F);
				p.solid(head, "curtain", BAND_OILED, zero, -4.6F, -8.0F, 3.8F, 9.2F, 7.0F, 0.4F, -4.8F, -8.0F, -1.0F, 0.4F, 6.0F, 4.8F,
						4.4F, -8.0F, -1.0F, 0.4F, 6.0F, 4.8F);
				p.solid(head, "chin_cord", BAND_ROPE, zero, -4.3F, -0.4F, -4.3F, 8.6F, 0.3F, 0.4F);
				p.solid(body, "padded_collar", BAND_QUILTED, zero, -4.8F, -0.6F, -2.8F, 9.6F, 2.0F, 5.6F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.35F, 7.0F, -2.35F, 8.7F, 1.2F, 4.7F);
				p.solid(body, "whistle", BAND_WOOD, PartPose.offsetAndRotation(2.8F, 7.6F, -2.6F, 0.0F, 0.0F, rad(-20.0F)), -0.3F, 0.0F, -0.3F,
						0.6F, 3.0F, 0.6F);
				p.solid(body, "tally", BAND_BONE, zero, -3.4F, 8.2F, -2.7F, 0.4F, 2.0F, 0.3F, -2.8F, 8.2F, -2.7F, 0.4F, 2.0F, 0.3F, -2.2F, 8.2F,
						-2.7F, 0.4F, 2.0F, 0.3F, -1.6F, 8.2F, -2.7F, 0.4F, 2.0F, 0.3F, -1.0F, 8.2F, -2.7F, 0.4F, 2.0F, 0.3F);
			}
			case IKAT_COPET -> {
				// the pickpocket: a dark patched headband knotted at the back, a cross strap with a small bag at the right hip,
				// a patched cloth over the left shoulder
				p.solid(head, "headband", BAND_PATCHED, zero, -4.35F, -7.4F, -4.35F, 8.7F, 1.4F, 8.7F);
				p.solid(head, "knot", BAND_PATCHED, PartPose.offsetAndRotation(0.0F, -6.8F, 4.3F, rad(20.0F), 0.0F, 0.0F), -1.0F, 0.0F, -0.2F,
						0.8F, 2.6F, 0.4F, 0.3F, 0.0F, -0.2F, 0.8F, 2.0F, 0.4F);
				p.solid(body, "strap", BAND_LEATHER, PartPose.offsetAndRotation(0.0F, 5.0F, -2.45F, 0.0F, 0.0F, rad(36.0F)), -0.4F, -7.0F, -0.2F,
						0.8F, 14.0F, 0.4F);
				p.solid(body, "strap_back", BAND_LEATHER, PartPose.offsetAndRotation(0.0F, 5.0F, 2.45F, 0.0F, 0.0F, rad(-36.0F)), -0.4F, -7.0F,
						-0.2F, 0.8F, 14.0F, 0.4F);
				PartDefinition bag = p.solid(body, "bag", BAND_PATCHED, PartPose.offset(-4.9F, 8.4F, -0.2F), -0.9F, 0.0F, -1.3F, 1.8F, 2.4F, 2.6F);
				p.solid(bag, "bag_flap", BAND_LEATHER, zero, -1.0F, -0.2F, -1.4F, 2.0F, 0.9F, 2.8F);
				p.solid(body, "shoulder_cloth", BAND_PATCHED, zero, 0.6F, -0.4F, -2.6F, 4.0F, 3.0F, 5.2F);
			}
			case BUNGA_COMBLANG -> {
				// the matchmaker: a big red silk peony over the left ear with green leaves and a gold pin, a red fan in a
				// phoenix-brocade belt, a red handkerchief tucked at the front
				PartDefinition peony = p.solid(head, "peony", BAND_SILK_FLOWER, PartPose.offset(4.0F, -7.6F, -1.2F), -1.2F, -1.4F, -1.4F, 2.4F, 2.8F,
						2.8F, -1.6F, -0.8F, -0.8F, 3.2F, 1.6F, 1.6F);
				p.solid(peony, "leaves", BAND_JADE, zero, -0.4F, 0.8F, -2.0F, 0.8F, 0.6F, 1.6F, -0.4F, 1.0F, 1.0F, 0.8F, 0.6F, 1.6F);
				p.solid(head, "pin", BAND_GOLD, zero, -3.4F, -9.0F, 1.2F, 6.8F, 0.5F, 0.5F, 3.2F, -9.3F, 0.9F, 1.0F, 1.0F, 1.0F);
				p.solid(body, "belt", BAND_PHOENIX_SILK, zero, -4.4F, 6.6F, -2.4F, 8.8F, 1.6F, 4.8F);
				PartDefinition fan = p.solid(body, "fan", BAND_SILK_FLOWER, PartPose.offsetAndRotation(-3.2F, 6.4F, -2.6F, 0.0F, 0.0F, rad(14.0F)),
						-0.5F, 0.0F, -0.25F, 1.0F, 6.0F, 0.5F);
				p.solid(fan, "fan_rib", BAND_WOOD, zero, -0.55F, -0.6F, -0.3F, 1.1F, 0.6F, 0.6F);
				p.solid(fan, "fan_tassel", BAND_GOLD, zero, -0.25F, 6.0F, -0.25F, 0.5F, 1.6F, 0.5F);
				p.solid(body, "handkerchief", BAND_CRIMSON, zero, 1.6F, 8.2F, -2.75F, 2.0F, 2.8F, 0.3F);
			}
			case TOPI_DONGENG -> {
				// the storyteller: a tall black soft cap (Dongpo style) with a square back panel, a folding fan and the
				// wooden clapper he slaps on the table at the turn of every tale
				p.solid(head, "cap", BAND_LACQUER_GAUZE, zero, -4.4F, -10.4F, -4.4F, 8.8F, 2.8F, 8.8F, -3.6F, -12.4F, -0.6F, 7.2F, 2.0F, 5.0F);
				p.solid(head, "cap_fold", BAND_BLACK, zero, -3.7F, -12.6F, -0.7F, 7.4F, 0.4F, 5.2F);
				p.solid(head, "ribbons", BAND_BLACK, PartPose.offsetAndRotation(0.0F, -8.2F, 4.4F, rad(14.0F), 0.0F, 0.0F), -1.6F, 0.0F, -0.15F,
						0.8F, 5.0F, 0.3F, 0.8F, 0.0F, -0.15F, 0.8F, 4.4F, 0.3F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				waistFan(p, body, 3.3F);
				p.solid(body, "clapper", BAND_WOOD, zero, -4.9F, 7.2F, -1.4F, 1.2F, 1.4F, 2.8F);
				p.solid(body, "clapper_cord", BAND_CRIMSON, zero, -4.8F, 8.6F, -0.3F, 0.4F, 1.6F, 0.4F);
			}
			case TOPI_PENGAMEN -> {
				// the street musician: a weathered straw hat with a broken brim, an erhu slung across the back (python-skin
				// drum, rosewood neck, two pegs, a horsehair bow beside it) on a rope strap
				p.solid(head, "hat", BAND_DARK_STRAW, zero, -5.6F, -8.8F, -5.6F, 9.0F, 0.8F, 11.2F, 3.4F, -8.8F, -5.6F, 2.2F, 0.8F, 6.0F,
						-3.4F, -10.2F, -3.4F, 6.8F, 1.6F, 6.8F);
				p.solid(head, "hat_frayed", BAND_STRAW, zero, 3.4F, -8.4F, 0.6F, 1.6F, 0.4F, 0.6F, -5.8F, -8.4F, 2.0F, 0.6F, 0.6F, 1.2F);
				p.solid(head, "hat_band", BAND_ROPE, zero, -3.5F, -9.0F, -3.5F, 7.0F, 0.4F, 7.0F);
				PartDefinition erhu = p.solid(body, "erhu_neck", BAND_WOOD, PartPose.offsetAndRotation(0.0F, 5.0F, 3.0F, 0.0F, 0.0F, rad(22.0F)),
						-0.4F, -9.0F, -0.4F, 0.8F, 12.0F, 0.8F);
				p.solid(erhu, "erhu_drum", BAND_SNAKE, zero, -1.2F, 3.0F, -1.2F, 2.4F, 2.0F, 2.4F);
				p.solid(erhu, "erhu_pegs", BAND_WOOD, zero, -1.4F, -8.4F, -0.3F, 2.8F, 0.6F, 0.6F, -1.2F, -7.2F, -0.3F, 2.4F, 0.6F, 0.6F);
				p.solid(erhu, "erhu_bow", BAND_HAIR, zero, 0.8F, -6.0F, -0.2F, 0.4F, 10.0F, 0.4F);
				p.solid(body, "strap", BAND_ROPE, PartPose.offsetAndRotation(0.0F, 5.0F, -2.45F, 0.0F, 0.0F, rad(-30.0F)), -0.3F, -7.0F, -0.2F,
						0.6F, 14.0F, 0.4F);
			}
			case PANJI_OBAT -> {
				// the medicine hawker: a bamboo pole on his back carrying the white banner with 药 above his head (red frame,
				// tassels), a pill gourd and a pouch of tiger-bone plasters at the belt
				PartDefinition pole = p.solid(body, "pole", BAND_WOOD, PartPose.offset(3.0F, 0.0F, 3.0F), -0.4F, -30.0F, -0.4F, 0.8F, 20.0F, 0.8F,
						-0.4F, -10.0F, -0.4F, 0.8F, 20.0F, 0.8F);
				p.solid(pole, "crossbar", BAND_WOOD, zero, -10.0F, -28.4F, -0.4F, 14.0F, 0.6F, 0.8F);
				p.solid(pole, "banner", BAND_MEDICINE, zero, -10.0F, -27.8F, 0.0F, 14.0F, 14.0F, 1.0F);
				p.solid(pole, "banner_frame", BAND_CRIMSON, zero, -10.4F, -27.9F, -0.1F, 0.4F, 14.2F, 1.2F, 3.6F, -27.9F, -0.1F, 0.4F, 14.2F, 1.2F,
						-10.4F, -13.8F, -0.1F, 14.4F, 0.6F, 1.2F);
				p.solid(pole, "banner_tassels", BAND_CRIMSON, zero, -9.8F, -13.2F, 0.3F, 0.6F, 2.0F, 0.4F, -3.3F, -13.2F, 0.3F, 0.6F, 2.4F, 0.4F,
						2.8F, -13.2F, 0.3F, 0.6F, 2.0F, 0.4F);
				p.solid(pole, "pole_cap", BAND_BRONZE, zero, -0.6F, -31.0F, -0.6F, 1.2F, 1.0F, 1.2F);
				p.solid(body, "pole_strap", BAND_ROPE, PartPose.offsetAndRotation(0.0F, 5.0F, -2.45F, 0.0F, 0.0F, rad(-32.0F)), -0.3F, -7.0F,
						-0.2F, 0.6F, 14.0F, 0.4F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.35F, 7.0F, -2.35F, 8.7F, 1.2F, 4.7F);
				PartDefinition gourd = p.solid(body, "gourd", BAND_GOURD, PartPose.offset(-4.6F, 8.6F, 0.4F), -1.2F, 0.0F, -1.2F, 2.4F, 2.4F, 2.4F,
						-0.7F, -1.4F, -0.7F, 1.4F, 1.4F, 1.4F);
				p.solid(gourd, "cord", BAND_CRIMSON, zero, -0.25F, -2.6F, -0.25F, 0.5F, 1.2F, 0.5F);
				p.solid(body, "plasters", BAND_PAPER, zero, 1.8F, 8.2F, -2.8F, 2.2F, 2.0F, 0.6F);
			}
			case TOPI_GURU -> {
				// the schoolteacher: a square scholar's cap with a flat board and two black ribbons, the teacher's ruler (jiechi)
				// slanting from the sash and a rolled lesson scroll
				p.solid(head, "cap", BAND_LACQUER_GAUZE, zero, -4.4F, -10.2F, -4.4F, 8.8F, 2.6F, 8.8F);
				p.solid(head, "board", BAND_BLACK, zero, -4.8F, -10.8F, -4.8F, 9.6F, 0.6F, 9.6F);
				p.solid(head, "ribbons", BAND_BLACK, PartPose.offsetAndRotation(0.0F, -8.0F, 4.5F, rad(10.0F), 0.0F, 0.0F), -1.8F, 0.0F, -0.15F,
						1.0F, 6.0F, 0.3F, 0.8F, 0.0F, -0.15F, 1.0F, 6.0F, 0.3F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				p.solid(body, "ruler", BAND_SANDALWOOD, PartPose.offsetAndRotation(-3.6F, 6.0F, -2.6F, 0.0F, 0.0F, rad(18.0F)), -0.6F, 0.0F, -0.2F,
						1.2F, 7.0F, 0.4F);
				PartDefinition scroll = p.solid(body, "scroll", BAND_PAPER, PartPose.offsetAndRotation(3.0F, 6.2F, -2.6F, 0.0F, 0.0F, rad(-10.0F)),
						-0.6F, 0.0F, -0.6F, 1.2F, 4.4F, 1.2F);
				p.solid(scroll, "scroll_ends", BAND_WOOD, zero, -0.8F, -0.4F, -0.8F, 1.6F, 0.4F, 1.6F, -0.8F, 4.4F, -0.8F, 1.6F, 0.4F, 1.6F);
			}
			case KUNCI_GUDANG -> {
				// the warehouse keeper: a leather belt with a heavy ring of bronze keys and a bound ledger, cloth sleeve guards
				p.solid(body, "belt", BAND_LEATHER, zero, -4.35F, 7.0F, -2.35F, 8.7F, 1.2F, 4.7F);
				PartDefinition ring = p.solid(body, "key_ring", BAND_BRONZE, PartPose.offset(3.2F, 8.2F, -2.8F), -1.0F, 0.0F, -0.2F, 2.0F, 0.4F,
						0.4F, -1.0F, 0.0F, -0.2F, 0.4F, 1.6F, 0.4F, 0.6F, 0.0F, -0.2F, 0.4F, 1.6F, 0.4F, -1.0F, 1.4F, -0.2F, 2.0F, 0.4F, 0.4F);
				p.solid(ring, "keys", BAND_BRONZE, zero, -0.9F, 1.8F, -0.15F, 0.4F, 2.4F, 0.3F, -0.2F, 1.8F, -0.15F, 0.4F, 2.0F, 0.3F, 0.5F, 1.8F,
						-0.15F, 0.4F, 2.6F, 0.3F);
				PartDefinition ledger = p.solid(body, "ledger", BAND_PAPER, PartPose.offset(-4.7F, 7.8F, 0.0F), -0.6F, 0.0F, -1.6F, 1.2F, 3.4F, 3.2F);
				p.solid(ledger, "ledger_cover", BAND_LEATHER, zero, -0.7F, -0.1F, -1.7F, 0.3F, 3.6F, 3.4F, 0.4F, -0.1F, -1.7F, 0.3F, 3.6F, 3.4F);
				p.tinted(rightArm, "sleeve_guard", zero, -3.3F, 4.4F, -2.3F, 4.6F, 4.4F, 4.6F);
				p.tinted(leftArm, "sleeve_guard", zero, -1.3F, 4.4F, -2.3F, 4.6F, 4.4F, 4.6F);
			}
			case TOPI_PIALANG -> {
				// the broker: a black melon-rind skullcap of six panels with a red knot, a gold-beaded abacus and a coin purse
				p.solid(head, "skullcap", BAND_LACQUER_GAUZE, zero, -4.3F, -9.0F, -4.3F, 8.6F, 1.6F, 8.6F, -3.6F, -9.6F, -3.6F, 7.2F, 0.7F, 7.2F);
				p.solid(head, "cap_rim", BAND_BLACK, zero, -4.45F, -7.6F, -4.45F, 8.9F, 0.4F, 8.9F);
				p.solid(head, "cap_jade", BAND_JADE, zero, -0.6F, -8.6F, -4.55F, 1.2F, 1.0F, 0.3F);
				p.solid(head, "knot", BAND_CRIMSON, zero, -0.6F, -10.4F, -0.6F, 1.2F, 0.9F, 1.2F);
				p.solid(body, "belt", BAND_BLACK, zero, -4.35F, 7.0F, -2.35F, 8.7F, 1.0F, 4.7F);
				PartDefinition abacus = p.solid(body, "abacus", BAND_WOOD, PartPose.offset(-4.7F, 7.6F, 0.0F), -0.4F, 0.0F, -2.0F, 0.6F, 3.0F, 4.0F);
				p.solid(abacus, "beads", BAND_GOLD, zero, -0.6F, 0.6F, -1.6F, 1.0F, 0.6F, 3.2F, -0.6F, 1.8F, -1.6F, 1.0F, 0.6F, 3.2F);
				p.solid(body, "purse", BAND_COIN_BROCADE, zero, 2.4F, 8.0F, -2.9F, 1.8F, 2.2F, 1.0F);
			}
			case TOPI_PEMUSIK -> {
				// the wedding and funeral musician: a red cloth cap with a turned-up brim and gold band, a red sash, a small
				// drum on a cord at the left hip
				p.solid(head, "cap", BAND_CRIMSON, zero, -4.3F, -9.8F, -4.3F, 8.6F, 2.2F, 8.6F);
				p.solid(head, "brim", BAND_CRIMSON, zero, -4.7F, -7.8F, -4.7F, 9.4F, 0.8F, 9.4F);
				p.solid(head, "cap_band", BAND_GOLD, zero, -4.4F, -8.4F, -4.4F, 8.8F, 0.5F, 8.8F);
				p.solid(head, "pompom", BAND_PLUME, zero, -0.8F, -10.8F, -0.8F, 1.6F, 1.0F, 1.6F);
				p.solid(body, "sash", BAND_SILK_FLOWER, PartPose.offsetAndRotation(0.0F, 5.6F, -2.45F, 0.0F, 0.0F, rad(-36.0F)), -0.8F, -7.4F,
						-0.2F, 1.6F, 14.8F, 0.4F);
				PartDefinition drum = p.solid(body, "drum", BAND_CRIMSON, PartPose.offset(4.9F, 8.6F, 0.0F), -0.8F, 0.0F, -1.6F, 1.6F, 2.0F, 3.2F);
				p.solid(drum, "drum_heads", BAND_LEATHER, zero, -0.9F, -0.1F, -1.4F, 1.8F, 0.3F, 2.8F, -0.9F, 1.8F, -1.4F, 1.8F, 0.3F, 2.8F);
				p.solid(drum, "drum_cord", BAND_ROPE, zero, -0.2F, -2.6F, -0.2F, 0.4F, 2.6F, 0.4F);
			}
			default -> {
			}
		}
	}

	/** A small gold phoenix on the bride's crown at (x, y, z), scaled by {@code s}: body, curved neck and head, raised tail. */
	private static void phoenix(Pass p, PartDefinition head, String name, float x, float y, float z, float s) {
		PartPose zero = PartPose.ZERO;
		PartDefinition bird = p.solid(head, name, BAND_GOLD, PartPose.offset(x, y, z), -0.9F * s, -1.0F * s, -1.6F * s, 1.8F * s, 1.4F * s,
				3.0F * s, -0.4F * s, -2.6F * s, -2.4F * s, 0.8F * s, 1.8F * s, 0.8F * s);
		p.solid(bird, name + "_beak", BAND_CRIMSON, zero, -0.25F * s, -2.4F * s, -3.0F * s, 0.5F * s, 0.5F * s, 0.7F * s);
		p.solid(bird, name + "_tail", BAND_PHOENIX_SILK, PartPose.offsetAndRotation(0.0F, -0.6F * s, 1.2F * s, rad(-40.0F), 0.0F, 0.0F),
				-1.2F * s, -0.3F * s, 0.0F, 2.4F * s, 0.6F * s, 3.6F * s);
		p.solid(bird, name + "_wings", BAND_DIANCUI, zero, -1.6F * s, -0.9F * s, -1.0F * s, 0.6F * s, 1.0F * s, 2.2F * s, 1.0F * s, -0.9F * s,
				-1.0F * s, 0.6F * s, 1.0F * s, 2.2F * s);
	}

	/**
	 * Mourning: a white cap of coarse cotton folded to a peak with two tails behind, a hemp band round it, a hemp rope belt
	 * with a hanging knot, a hemp armband on the left arm; {@code tall} raises the funeral master's stiff cap.
	 */
	private static void kabung(Pass p, PartDefinition head, PartDefinition body, PartDefinition leftArm, boolean tall) {
		PartPose zero = PartPose.ZERO;
		p.solid(head, "mourning_cap", BAND_MOURNING, zero, -4.5F, -9.4F, -4.5F, 9.0F, 2.6F, 9.0F);
		p.solid(head, "cap_peak", BAND_MOURNING, zero, -3.0F, tall ? -12.6F : -10.6F, -1.4F, 6.0F, tall ? 3.4F : 1.4F, 5.2F);
		p.solid(head, "cap_tails", BAND_MOURNING, PartPose.offsetAndRotation(0.0F, -8.0F, 4.5F, rad(8.0F), 0.0F, 0.0F), -1.8F, 0.0F, -0.2F,
				1.2F, 7.0F, 0.4F, 0.6F, 0.0F, -0.2F, 1.2F, 6.0F, 0.4F);
		p.solid(head, "hemp_band", BAND_HEMP, zero, -4.65F, -7.4F, -4.65F, 9.3F, 0.9F, 9.3F);
		p.solid(body, "hemp_belt", BAND_HEMP, zero, -4.45F, 6.8F, -2.45F, 8.9F, 1.1F, 4.9F);
		p.solid(body, "belt_knot", BAND_HEMP, zero, -0.7F, 7.4F, -2.8F, 1.4F, 1.2F, 0.5F, -0.9F, 8.6F, -2.7F, 0.6F, 3.6F, 0.3F, 0.3F, 8.6F,
				-2.7F, 0.6F, 3.0F, 0.3F);
		p.solid(leftArm, "armband", BAND_HEMP, zero, -1.3F, 1.6F, -2.3F, 4.6F, 1.6F, 4.6F);
	}

	/** The troupe's red-yellow headband with two flying tails, a striped sash with hanging ends, wrist and ankle wraps. */
	private static void akrobat(Pass p, PartDefinition head, PartDefinition body, PartDefinition rightArm, PartDefinition leftArm,
			PartDefinition rightLeg, PartDefinition leftLeg) {
		PartPose zero = PartPose.ZERO;
		p.solid(head, "acro_band", BAND_ACROBAT, zero, -4.35F, -7.2F, -4.35F, 8.7F, 1.2F, 8.7F);
		p.solid(head, "acro_tails", BAND_ACROBAT, PartPose.offsetAndRotation(0.0F, -6.6F, 4.3F, rad(55.0F), 0.0F, 0.0F), -1.4F, 0.0F, -0.2F,
				1.0F, 5.0F, 0.4F, 0.4F, 0.0F, -0.2F, 1.0F, 4.2F, 0.4F);
		p.solid(body, "acro_sash", BAND_ACROBAT, zero, -4.4F, 6.4F, -2.4F, 8.8F, 1.8F, 4.8F);
		p.solid(body, "acro_sash_ends", BAND_ACROBAT, PartPose.offsetAndRotation(3.2F, 7.6F, -2.6F, 0.0F, 0.0F, rad(-10.0F)), -0.6F, 0.0F,
				-0.2F, 1.2F, 5.0F, 0.4F);
		p.solid(rightArm, "wrist_wrap", BAND_ACROBAT, zero, -3.3F, 7.6F, -2.3F, 4.6F, 1.6F, 4.6F);
		p.solid(leftArm, "wrist_wrap", BAND_ACROBAT, zero, -1.3F, 7.6F, -2.3F, 4.6F, 1.6F, 4.6F);
		p.solid(rightLeg, "ankle_wrap", BAND_ACROBAT, zero, -2.3F, 9.6F, -2.3F, 4.6F, 1.4F, 4.6F);
		p.solid(leftLeg, "ankle_wrap", BAND_ACROBAT, zero, -2.3F, 9.6F, -2.3F, 4.6F, 1.4F, 4.6F);
	}

	/** The palace helm: red-laced lamellar dome, steel cap, gold brow band, cheek and neck guards, a horsehair plume. */
	private static void helmBerumbai(Pass p, PartDefinition head) {
		PartPose zero = PartPose.ZERO;
		p.solid(head, "helm", BAND_LAMELLAR_RED, zero, -4.4F, -9.4F, -4.4F, 8.8F, 5.0F, 8.8F, -4.6F, -5.0F, 3.6F, 9.2F, 4.4F, 1.0F,
				-4.6F, -4.6F, -3.2F, 0.5F, 3.8F, 4.6F, 4.1F, -4.6F, -3.2F, 0.5F, 3.8F, 4.6F);
		p.solid(head, "cap", BAND_STEEL, zero, -3.0F, -10.4F, -3.0F, 6.0F, 1.0F, 6.0F);
		p.solid(head, "brow", BAND_GOLD, zero, -4.55F, -5.2F, -4.55F, 9.1F, 0.7F, 9.1F);
		p.solid(head, "finial", BAND_GOLD, zero, -0.5F, -11.6F, -0.5F, 1.0F, 1.4F, 1.0F);
		p.solid(head, "plume", BAND_PLUME, PartPose.offsetAndRotation(0.0F, -11.4F, 0.4F, rad(-15.0F), 0.0F, 0.0F),
				-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, -0.8F, -3.2F, 1.0F, 1.6F, 2.4F, 2.8F);
	}

	/** A knotted head cloth (tinted homespun) covering the crown, with two short tails at the nape. */
	private static void kainKepala(Pass p, PartDefinition head) {
		PartPose zero = PartPose.ZERO;
		p.tinted(head, "cloth", zero, -4.3F, -8.7F, -4.3F, 8.6F, 2.2F, 8.6F, -3.8F, -9.1F, -3.8F, 7.6F, 0.6F, 7.6F);
		p.tinted(head, "cloth_knot", zero, -1.0F, -8.0F, 4.2F, 2.0F, 2.0F, 1.6F);
		p.tinted(head, "cloth_tail_l", PartPose.offsetAndRotation(0.5F, -7.0F, 5.2F, rad(25.0F), 0.0F, rad(-6.0F)), -0.4F, 0.0F, -0.15F, 0.8F, 3.6F, 0.3F);
		p.tinted(head, "cloth_tail_r", PartPose.offsetAndRotation(-0.5F, -7.0F, 5.2F, rad(22.0F), 0.0F, rad(8.0F)), -0.4F, 0.0F, -0.15F, 0.8F, 3.0F, 0.3F);
	}

	/** A three-tier conical hat with a wooden tip and a cord down both cheeks to the chin. */
	private static void caping(Pass p, PartDefinition head, int straw, int cord) {
		PartPose zero = PartPose.ZERO;
		p.solid(head, "caping", straw, zero, -6.0F, -8.9F, -6.0F, 12.0F, 1.0F, 12.0F, -4.0F, -10.4F, -4.0F, 8.0F, 1.5F, 8.0F,
				-2.0F, -11.9F, -2.0F, 4.0F, 1.5F, 4.0F);
		p.solid(head, "caping_tip", BAND_WOOD, zero, -0.6F, -12.5F, -0.6F, 1.2F, 0.6F, 1.2F);
		p.solid(head, "caping_cord", cord, zero, -4.3F, -8.0F, -0.4F, 0.3F, 8.0F, 0.4F, 4.0F, -8.0F, -0.4F, 0.3F, 8.0F, 0.4F,
				-4.3F, -0.3F, -4.3F, 8.6F, 0.3F, 0.4F);
	}

	/** A smith's gauntlet at the wrist of an arm whose cube starts at x {@code x0}. */
	private static void gauntlet(Pass p, PartDefinition arm, float x0) {
		PartPose zero = PartPose.ZERO;
		p.solid(arm, "gauntlet", BAND_SCORCHED, zero, x0, 5.6F, -2.2F, 4.4F, 5.0F, 4.4F);
		p.solid(arm, "cuff", BAND_LEATHER, zero, x0 - 0.3F, 5.0F, -2.5F, 5.0F, 1.2F, 5.0F);
		p.solid(arm, "studs", BAND_BRONZE, zero, x0 + 1.4F, 7.0F, -2.45F, 0.6F, 0.6F, 0.3F, x0 + 2.4F, 7.0F, -2.45F, 0.6F, 0.6F, 0.3F);
	}

	/** A closed folding fan tucked in the belt at {@code x} (left side positive), with its rib and crimson tassel. */
	private static void waistFan(Pass p, PartDefinition body, float x) {
		PartPose zero = PartPose.ZERO;
		PartDefinition fan = p.solid(body, "fan", BAND_PAPER, PartPose.offsetAndRotation(x, 6.4F, -2.6F, 0.0F, 0.0F, rad(x > 0 ? -14.0F : 14.0F)),
				-0.5F, 0.0F, -0.25F, 1.0F, 6.0F, 0.5F);
		p.solid(fan, "fan_rib", BAND_WOOD, zero, -0.55F, -0.6F, -0.3F, 1.1F, 0.6F, 0.6F, -0.55F, 5.6F, -0.3F, 1.1F, 0.5F, 0.6F);
		p.solid(fan, "fan_tassel", BAND_CRIMSON, zero, -0.25F, 6.1F, -0.25F, 0.5F, 1.8F, 0.5F);
	}

	/** The iron mask of the demonic enforcers: brow plate, eye holes, cheek plate with two bone tusks, a strap. */
	private static void ironMask(Pass p, PartDefinition head) {
		PartPose zero = PartPose.ZERO;
		p.solid(head, "mask", BAND_BLACK_IRON, zero, -4.2F, -7.6F, -4.7F, 8.4F, 3.2F, 0.6F, -4.2F, -3.0F, -4.7F, 8.4F, 1.6F, 0.6F,
				-0.7F, -4.4F, -4.75F, 1.4F, 1.4F, 0.6F, -4.2F, -4.4F, -4.7F, 0.9F, 1.4F, 0.6F, 3.3F, -4.4F, -4.7F, 0.9F, 1.4F, 0.6F);
		p.solid(head, "tusks", BAND_BONE, zero, -2.3F, -1.6F, -4.9F, 0.6F, 1.2F, 0.4F, 1.7F, -1.6F, -4.9F, 0.6F, 1.2F, 0.4F);
		p.solid(head, "rivets", BAND_BLOOD_LACQUER, zero, -3.6F, -7.0F, -4.85F, 0.5F, 0.5F, 0.2F, 3.1F, -7.0F, -4.85F, 0.5F, 0.5F, 0.2F);
		p.solid(head, "mask_strap", BAND_LEATHER, zero, -4.4F, -6.4F, -4.4F, 8.8F, 0.6F, 8.8F);
	}

	/** Six alternating iron links from the left shoulder across the chest to the right hip, and over the shoulder behind. */
	private static void shoulderChain(Pass p, PartDefinition body) {
		float[] links = new float[6 * 6];
		for (int i = 0; i < 6; i++) {
			float y = i * 1.3F;
			System.arraycopy(i % 2 == 0 ? b(-0.5F, y, -0.25F, 1.0F, 1.5F, 0.5F) : b(-0.25F, y, -0.5F, 0.5F, 1.5F, 1.0F), 0, links, i * 6, 6);
		}
		p.solid(body, "chain_front", BAND_BLACK_IRON, PartPose.offsetAndRotation(3.4F, 0.0F, -2.6F, 0.0F, 0.0F, rad(31.0F)), links);
		p.solid(body, "chain_back", BAND_BLACK_IRON, PartPose.offsetAndRotation(3.4F, 0.0F, 2.6F, 0.0F, 0.0F, rad(31.0F)), links);
		p.solid(body, "chain_shoulder", BAND_BLACK_IRON, PartPose.ZERO, 2.9F, -0.5F, -2.6F, 1.0F, 0.6F, 5.2F);
		p.solid(body, "chain_ring", BAND_BLACK_IRON, PartPose.ZERO, -2.6F, 6.6F, -2.9F, 1.6F, 1.6F, 0.4F);
	}

	/** One horn of the demon crown: three tapering segments curving outward and back ({@code side} 1 = left, -1 = right). */
	private static void horn(Pass p, PartDefinition head, float side) {
		String s = side > 0 ? "l" : "r";
		PartDefinition a = p.solid(head, "horn_" + s, BAND_HORN, PartPose.offsetAndRotation(side * 3.0F, -9.0F, -0.5F, rad(-15.0F), 0.0F, rad(side * 22.5F)),
				-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F);
		PartDefinition bseg = p.solid(a, "mid", BAND_HORN, PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, rad(-15.0F), 0.0F, rad(side * 22.5F)),
				-0.75F, -3.0F, -0.75F, 1.5F, 3.0F, 1.5F);
		p.solid(bseg, "tip", BAND_HORN, PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, rad(-10.0F), 0.0F, rad(side * -22.5F)),
				-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F);
	}

	/** The sword in its sheath at the left hip, tip back and down, the hilt forward and up (hidden while drawn). */
	private static void hipSword(Pass p, PartDefinition body) {
		PartPose zero = PartPose.ZERO;
		PartDefinition sheath = p.solid(body, "scabbard", BAND_WOOD, PartPose.offsetAndRotation(4.7F, 7.4F, -0.4F, rad(55.0F), 0.0F, rad(6.0F)),
				-0.6F, 0.0F, -0.9F, 1.2F, 11.0F, 1.8F);
		p.solid(sheath, "chape", BAND_GOLD, zero, -0.75F, 10.0F, -1.05F, 1.5F, 1.2F, 2.1F, -0.75F, 3.0F, -1.05F, 1.5F, 0.6F, 2.1F);
		PartDefinition hilt = p.solid(sheath, HILT, BAND_GOLD, zero, -1.2F, -0.7F, -1.4F, 2.4F, 0.7F, 2.8F, -0.6F, -4.9F, -0.6F, 1.2F, 0.6F, 1.2F);
		p.solid(hilt, "grip", BAND_BLACK, zero, -0.45F, -4.3F, -0.45F, 0.9F, 3.6F, 0.9F);
		p.solid(hilt, "tassel", BAND_CRIMSON, zero, -0.2F, -4.9F, 0.5F, 0.4F, 2.4F, 0.4F);
	}

	private KotaGear() {
	}
}
