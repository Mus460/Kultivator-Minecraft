package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.math.Transformation;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.bersama.TanduEntity;
import dev.kultivator.dunia.kota42.hewan.HewanKota;
import dev.kultivator.dunia.kota42.hewan.KudaTambatEntity;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resident ceremonies (DESIGN 42.7.7, contract C42.D1): weddings, funerals, red eggs, the one-month feast and zhuazhou,
 * scheduled by 42A's life cycle ({@code KotaBus.NIKAH / WAFAT / LAHIR}, or {@link #jadwalkan}) for a following day at the
 * fixed hours of {@link JenisUpacara}, played with real resident bodies, staff stand-ins (the dead, the veiled bride, the
 * bridal horse, bearers, musicians), the {@code usungan_peti} and {@code meja_jamuan} entities and the red
 * {@link TanduEntity}. Never changes a block, never kills. Seen when a player is within {@code kotaAcara.jarakTampil} blocks of
 * the venue; otherwise the ceremony runs as data (phases fired on the bus, the zhuazhou object picked, mood and gossip).
 *
 * <h2>Stored records</h2>
 * {@code KotaAcaraData.bagian("upacara")["kota_<situs>"]}: {@code u} the list of records ({@code i} id, {@code j} kind
 * ordinal, {@code h} world day, {@code w} resident ids in the order of {@link #jadwalkan}, {@code s} stage 0 waiting, 1 the
 * day came, 2 started, 3 over, {@code f} last bus phase fired, {@code o} test offset in ticks, {@code d} snapshot of
 * those who left the data (the dead, with their look, household and the players who knew them), {@code k} calendar id),
 * {@code n} the id counter, {@code kabar} the death notices still to show. The play is a state machine over the clock: a
 * ceremony that finds a viewer starts at the phase the hour asks for, so a restart loses nothing.
 *
 * <h2>Bus and payload</h2>
 * {@code KotaBus.UPACARA(situs, jenis, id, fase)}: 0 = the day of the ceremony came, then 1.. the phases (wedding
 * 1 fetching the bride, 2 the sedan procession, 3 the three bows, 4 the banquet; funeral 1 the procession, 2 the grave, 3
 * the wake meal; birth and one-month feast 1; zhuazhou 1 the mat, 2 the meal), -1 = over. Each phase change also goes to the
 * players within {@code jarakTampil} as {@code kota_upacara} (path nodes: the procession line, participants: entity ids of
 * the stand-ins, the first being the lead: groom, coffin, baby).
 *
 * <h2>Player choices (no new screen)</h2>
 * Right-click a participant: the gift envelope of {@code 2 x (band + 1)} NB (shift: five times it) at a wedding (guests the
 * families know: impression 20 or more) or a funeral (anyone present), or a red egg from the father; hitting a participant is
 * refused. The 42U-2 card uses {@link #daftar}, {@link #batasBeri} and {@link #beri}.
 */
public final class Upacara {

	/** Lang prefix of the ceremonies in {@code kota_hidup_peristiwa}. */
	static final String L = PeristiwaJalan.L + "upacara.";
	static final String BAGIAN = "upacara";
	/** Tag of every Display made for a ceremony (the mat); never saved on purpose, swept on load. */
	static final String TAG_BENDA = "kultivator_upacara_benda";
	/** Gift cap as a multiple of the minimum. */
	static final int BERI_KALI_MAKS = 100;
	/** Blocks within which a player counts as present at the venue. */
	static final double JARAK_HADIR = 24.0D;
	static final double JARAK_BERI = 14.0D;
	/** Stage of a stored record. */
	static final int S_MENUNGGU = 0;
	static final int S_HARI = 1;
	static final int S_MULAI = 2;
	static final int S_SELESAI = 3;
	/** Game ticks without a viewer after which a running play pauses (it restarts when somebody comes). */
	static final int SEPI = 400;
	/** The zhuazhou objects in {@code Pekerjaan.ZHUAZHOU} order, 1-8: brush, abacus, wooden sword, seal, book, hammer, prayer beads, coin. */
	static final int[] PROP_BENDA = {0, PropId.KUAS, PropId.SEMPOA, PropId.PEDANG_KAYU, KotaGerakId.PROP_CAP_PEJABAT, PropId.KITAB, PropId.PALU,
			PropId.TASBIH_TANGAN, PropId.KANTONG_UANG};

	private static final Map<Long, Map<Integer, Jalan>> JALAN = new HashMap<>();
	private static final Set<UUID> DISAPU = new HashSet<>();
	/** Stray mat displays found on chunk load, removed on the next tick (not inside the load callback). */
	private static final List<Entity> SAPU = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static boolean initialised;

	private Upacara() {
	}

	// ================================================================== init

	/** From {@code PeristiwaJalan.init}. Safe to call twice. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KotaBus.NIKAH.register((server, situs, pria, wanita) -> {
			WargaData data = WargaData.get(server);
			WargaData.Situs s = data.peek(situs);
			if (s != null) {
				jadwalkan(server, situs, JenisUpacara.NIKAH, 0L, new Warga[]{pria, wanita}, rumahAsal(data, pria), rumahAsal(data, wanita));
			}
		});
		KotaBus.WAFAT.register((server, situs, warga, sebab) -> jadwalkan(server, situs, JenisUpacara.MAKAM, 0L, keluargaMati(server, warga)));
		KotaBus.LAHIR.register((server, situs, bayi, ibu) -> {
			Warga ayah = ibu == null || ibu.pasangan == Warga.TANPA ? null : WargaData.get(server).byId(ibu.pasangan);
			Warga[] set = ibu == null ? new Warga[]{bayi} : ayah == null ? new Warga[]{bayi, ibu} : new Warga[]{bayi, ibu, ayah};
			for (JenisUpacara jenis : new JenisUpacara[]{JenisUpacara.LAHIR, JenisUpacara.SEBULAN, JenisUpacara.ZHUAZHOU}) {
				jadwalkan(server, situs, jenis, 0L, set);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Upacara::tik);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> tutupSemua(server.overworld()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JALAN.clear();
			DISAPU.clear();
			SAPU.clear();
		});
		// a mat display left in a chunk by a crash is swept when the chunk loads
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.ItemDisplay && entity.entityTags().contains(TAG_BENDA) && !DISAPU.contains(entity.getUUID())) {
				SAPU.add(entity);
			}
		});
		KotaUji.daftar("acara", Upacara::uji);
	}

	static boolean boleh() {
		KotaConfig.Acara c = KotaConfig.acara();
		return c.aktif && c.upacara && WargaApi.aktif();
	}

	// ================================================================== contract C42.D1

	/**
	 * Schedules a ceremony of {@code jenis} for {@code warga} (wedding: groom, bride; funeral: the dead, then family; births:
	 * baby, mother, father). Returns the ceremony id, or -1 when refused (module off, nobody, an unknown place, the same
	 * ceremony already booked).
	 */
	public static int jadwalkan(JenisUpacara jenis, Warga... warga) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || jenis == null || warga == null || warga.length == 0) {
			return -1;
		}
		WargaData data = WargaData.get(server);
		long situs = 0L;
		for (Warga w : warga) {
			if (w != null && situs == 0L) {
				situs = data.situsDari(w.id);
			}
		}
		return situs == 0L ? -1 : jadwalkan(server, situs, jenis, 0L, warga);
	}

	/** {@link #jadwalkan(JenisUpacara, Warga...)} in place {@code situs}; {@code ekstra}: the household ids of the wedding houses. */
	static int jadwalkan(MinecraftServer server, long situs, JenisUpacara jenis, long geser, Warga[] warga, int... ekstra) {
		if (!boleh() || warga == null || warga.length == 0 || warga[0] == null) {
			return -1;
		}
		long hari = KalenderKota.hari(server);
		int jam = KalenderKota.jam(server);
		long day = hari + jenis.hariSetelah + (jenis.hariSetelah == 0 && jam >= jenis.jamAkhir ? 1 : 0);
		KotaAcaraData store = KotaAcaraData.get(server);
		CompoundTag town = store.situs(BAGIAN, situs);
		ListTag list = town.getListOrEmpty("u");
		if (!town.contains("u")) {
			town.put("u", list);
		}
		int first = warga[0].id;
		// twins share one feast: the same kind of the same mother and day is not booked twice
		int mother = jenis == JenisUpacara.NIKAH || jenis == JenisUpacara.MAKAM ? 0 : warga.length > 1 && warga[1] != null ? warga[1].id : 0;
		for (Tag t : list) {
			if (t instanceof CompoundTag c && c.getByteOr("j", (byte) -1) == jenis.ordinal() && c.getLongOr("h", -1L) == day
					&& c.getIntOr("s", 0) < S_SELESAI) {
				int[] ids = c.getIntArray("w").orElse(new int[0]);
				if (ids.length > 0 && (ids[0] == first || mother != 0 && ids.length > 1 && ids[1] == mother)) {
					if (jenis == JenisUpacara.ZHUAZHOU && ids[0] != first) {
						// the second twin sits on the same mat
						c.putIntArray("b", new int[]{first});
						store.ubah();
					}
					return c.getIntOr("i", -1);
				}
			}
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.getCompoundOrEmpty(i).getLongOr("h", 0L) < hari - 3L) {
				list.remove(i);
			}
		}
		int id = town.getIntOr("n", 0) + 1;
		town.putInt("n", id);
		CompoundTag r = new CompoundTag();
		r.putInt("i", id);
		r.putByte("j", (byte) jenis.ordinal());
		r.putLong("h", day);
		int[] ids = new int[warga.length];
		for (int i = 0; i < warga.length; i++) {
			ids[i] = warga[i] == null ? 0 : warga[i].id;
		}
		r.putIntArray("w", ids);
		r.putInt("o", (int) geser);
		CompoundTag d = new CompoundTag();
		WargaData data = WargaData.get(server);
		if (jenis == JenisUpacara.MAKAM) {
			d.put("mati", potret(data, warga[0]));
			ListTag fam = new ListTag();
			for (int i = 1; i < warga.length; i++) {
				if (warga[i] != null) {
					fam.add(net.minecraft.nbt.IntTag.valueOf(warga[i].id));
				}
			}
			d.put("fam", fam);
		}
		if (ekstra != null && ekstra.length >= 2) {
			d.putInt("rtP", ekstra[0]);
			d.putInt("rtW", ekstra[1]);
		}
		r.put("d", d);
		int kal = KalenderKota.daftar(situs, new KalenderKota.Acara(jenis.id(), day, jenis.jamMulai, Math.max(1, jenis.jamAkhir - jenis.jamMulai), ids,
				new CompoundTag()));
		r.putInt("k", kal);
		list.add(r);
		store.ubah();
		return id;
	}

	/** The family of a resident who just died, for the funeral: the dead first, then spouse, children, parents. */
	private static Warga[] keluargaMati(MinecraftServer server, Warga mati) {
		WargaData data = WargaData.get(server);
		List<Warga> out = new ArrayList<>();
		out.add(mati);
		List<Integer> ids = new ArrayList<>();
		ids.add(mati.pasangan);
		for (int c : mati.anak) {
			ids.add(c);
		}
		ids.add(mati.ayah);
		ids.add(mati.ibu);
		for (int id : ids) {
			Warga w = id == Warga.TANPA ? null : data.byId(id);
			if (w != null && w.hidup() && !out.contains(w)) {
				out.add(w);
			}
		}
		return out.toArray(new Warga[0]);
	}

	/** The household a person marries out of or into: a living parent's household, else its own. */
	private static int rumahAsal(WargaData data, Warga w) {
		for (int p : new int[]{w.ayah, w.ibu}) {
			Warga parent = p == Warga.TANPA ? null : data.byId(p);
			if (parent != null && parent.hidup() && parent.rumahTangga > 0 && parent.rumahTangga != w.rumahTangga) {
				return parent.rumahTangga;
			}
		}
		return w.rumahTangga;
	}

	private static CompoundTag potret(WargaData data, Warga w) {
		CompoundTag d = new CompoundTag();
		d.putInt("id", w.id);
		d.putInt("m", w.marga);
		d.putInt("n", w.nama);
		d.putInt("g", w.generasi);
		d.putByte("k", w.kelamin);
		d.putLong("l", w.lahir);
		d.putInt("lk", w.look);
		d.putString("pr", w.peran);
		d.putInt("rt", w.rumahTangga);
		d.putString("tk", w.tokoh == null ? "" : w.tokoh);
		ListTag kenal = new ListTag();
		for (Ingatan i : w.ingatan) {
			UUID u = i.kesan >= 20 ? data.pemain(i.pemain) : null;
			if (u != null && kenal.size() < 16) {
				kenal.add(StringTag.valueOf(u.toString()));
			}
		}
		d.put("kenal", kenal);
		return d;
	}

	/** A detached {@link Warga} carrying the name and look of a snapshot (never stored in the data). */
	static Warga dariPotret(CompoundTag d) {
		Warga w = new Warga(d.getIntOr("id", 0));
		w.marga = d.getIntOr("m", 0);
		w.nama = d.getIntOr("n", 0);
		w.generasi = d.getIntOr("g", 0);
		w.kelamin = d.getByteOr("k", Warga.PRIA);
		w.lahir = d.getLongOr("l", 0L);
		w.look = d.getIntOr("lk", 0);
		w.peran = d.getStringOr("pr", NpcRole.WARGA.id());
		w.rumahTangga = d.getIntOr("rt", 0);
		w.tokoh = d.getStringOr("tk", "");
		return w;
	}

	// ================================================================== read side for the 42U-2 card

	/** One family member of a ceremony's invitation card. */
	public record Keluarga(Component nama, int warga, Component peran) {
	}

	/** A ceremony of the card: {@code fase} is a {@code KotaLembar.Upacara.UF_*} id. */
	public record Info(int id, JenisUpacara jenis, long hari, int jam, int menit, String fase, long sisaTick, @Nullable BlockPos tempat,
			Component nama, List<Keluarga> keluarga, boolean berlangsung) {
	}

	/** The ceremonies of {@code situs} not over yet and not older than yesterday, soonest first. */
	public static List<Info> daftar(MinecraftServer server, long situs) {
		List<Info> out = new ArrayList<>();
		CompoundTag town = KotaAcaraData.get(server).situs(BAGIAN, situs);
		long hari = KalenderKota.hari(server);
		WargaData.Situs s = WargaData.get(server).peek(situs);
		for (Tag t : town.getListOrEmpty("u")) {
			if (!(t instanceof CompoundTag r)) {
				continue;
			}
			JenisUpacara jenis = JenisUpacara.byOrdinal(r.getByteOr("j", (byte) -1));
			long day = r.getLongOr("h", 0L);
			if (jenis == null || r.getIntOr("s", 0) >= S_SELESAI || day < hari) {
				continue;
			}
			int id = r.getIntOr("i", 0);
			Jalan run = jalan(situs, id);
			long sisa = Math.max(0L, (day - hari) * 24000L + (jenis.jamMulai - 6L) * 1000L - Kota.timeOfDay(server) % 24000L + r.getIntOr("o", 0));
			int f = run != null ? run.fase : sisa > 0L ? 0 : Math.max(0, r.getIntOr("f", 0));
			out.add(new Info(id, jenis, day, jenis.jamMulai, 0, fasaKartu(jenis, f), sisa, run == null ? null : run.tempat(),
					namaUpacara(jenis, r, s), keluargaKartu(server, jenis, r, s), run != null));
		}
		out.sort(Comparator.comparingLong(Info::sisaTick));
		return out;
	}

	/** The card id of bus phase {@code fase} of {@code jenis} (0 waiting, -1 over). */
	static String fasaKartu(JenisUpacara jenis, int fase) {
		if (fase < 0) {
			return KotaLembar.Upacara.UF_SELESAI;
		}
		if (fase == 0) {
			return KotaLembar.Upacara.UF_MENUNGGU;
		}
		return switch (jenis) {
			case NIKAH -> switch (fase) {
				case 1 -> KotaLembar.Upacara.UF_JEMPUT;
				case 2 -> KotaLembar.Upacara.UF_ARAK;
				case 3 -> KotaLembar.Upacara.UF_SEMBAH;
				default -> KotaLembar.Upacara.UF_JAMUAN;
			};
			case MAKAM -> fase == 1 ? KotaLembar.Upacara.UF_USUNG : fase == 2 ? KotaLembar.Upacara.UF_MAKAM : KotaLembar.Upacara.UF_JAMUAN;
			case ZHUAZHOU -> fase == 1 ? KotaLembar.Upacara.UF_PILIH_BENDA : KotaLembar.Upacara.UF_JAMUAN;
			default -> KotaLembar.Upacara.UF_JAMUAN;
		};
	}

	private static Component namaUpacara(JenisUpacara jenis, CompoundTag r, @Nullable WargaData.Situs s) {
		int[] ids = r.getIntArray("w").orElse(new int[0]);
		Warga a = pelaku(r, s, 0);
		Warga b = pelaku(r, s, 1);
		Component na = a == null ? Component.literal("?") : NamaWarga.komponen(a);
		Component nb = b == null ? Component.literal("?") : NamaWarga.komponen(b);
		return switch (jenis) {
			case NIKAH -> Component.translatable(L + "nama.nikah", na, nb);
			case MAKAM -> Component.translatable(L + "nama.makam", na);
			default -> Component.translatable(L + "nama." + jenis.id(), na, ids.length > 1 && b != null ? nb : Component.translatable(L + "tanpa_ibu"));
		};
	}

	/** Resident {@code i} of the record: a living one from the data, the dead from the snapshot, else null. */
	private static @Nullable Warga pelaku(CompoundTag r, @Nullable WargaData.Situs s, int i) {
		int[] ids = r.getIntArray("w").orElse(new int[0]);
		if (i >= ids.length || ids[i] == 0) {
			return null;
		}
		if (i == 0 && r.getByteOr("j", (byte) -1) == JenisUpacara.MAKAM.ordinal() && r.getCompoundOrEmpty("d").contains("mati")) {
			return dariPotret(r.getCompoundOrEmpty("d").getCompoundOrEmpty("mati"));
		}
		MinecraftServer server = KotaHidup.server();
		return server == null ? null : WargaData.get(server).byId(ids[i]);
	}

	private static List<Keluarga> keluargaKartu(MinecraftServer server, JenisUpacara jenis, CompoundTag r, @Nullable WargaData.Situs s) {
		List<Keluarga> out = new ArrayList<>();
		int[] ids = r.getIntArray("w").orElse(new int[0]);
		for (int i = 0; i < ids.length && out.size() < 6; i++) {
			Warga w = pelaku(r, s, i);
			if (w == null) {
				continue;
			}
			String peran = switch (jenis) {
				case NIKAH -> i == 0 ? "pengantin_pria" : "pengantin_wanita";
				case MAKAM -> i == 0 ? "almarhum" : "keluarga";
				default -> i == 0 ? "bayi" : i == 1 ? "ibu" : "ayah";
			};
			out.add(new Keluarga(NamaWarga.komponen(w), w.id, Component.translatable(L + "peran." + peran)));
		}
		return out;
	}

	/** The smallest gift of {@code player} (spirit-stone envelope: {@code 2 x (band + 1)} NB) and the largest ({@value #BERI_KALI_MAKS} x). */
	public static long[] batasBeri(ServerPlayer player) {
		long min = 2L * (Barang.pita(player) + 1L);
		return new long[]{min, min * BERI_KALI_MAKS};
	}

	/** True when {@code player} already gave to ceremony {@code id} of {@code situs}. */
	public static boolean sudahBeri(ServerPlayer player, long situs, int id) {
		Jalan run = jalan(situs, id);
		return run != null && run.pemberi.contains(player.getUUID());
	}

	/** True when {@code player} stands at the venue of the running ceremony. */
	public static boolean hadir(ServerPlayer player, long situs, int id) {
		Jalan run = jalan(situs, id);
		return run != null && run.jarakHadir(player) <= JARAK_HADIR;
	}

	/**
	 * A gift of {@code nb} spirit stones ({@code NB}) by {@code player} to the running ceremony {@code id} of {@code situs}: an
	 * angpao at a wedding, the sealed white envelope at a funeral. Sink. Returns true when given.
	 */
	public static boolean beri(ServerPlayer player, long situs, int id, long nb) {
		Jalan run = jalan(situs, id);
		if (run == null) {
			Msg.actionBar(player, Component.translatable(L + "beri.tidak_berlangsung"));
			return false;
		}
		return run.beri(player, nb);
	}

	static @Nullable Jalan jalan(long situs, int id) {
		Map<Integer, Jalan> m = JALAN.get(situs);
		return m == null ? null : m.get(id);
	}

	// ================================================================== routing of clicks

	/** A right-click of {@code player} on {@code e}: true when a running ceremony used it. */
	static boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
		for (Map<Integer, Jalan> m : List.copyOf(JALAN.values())) {
			for (Jalan j : List.copyOf(m.values())) {
				if (j.milik(e) && j.klik(level, player, e, shift)) {
					return true;
				}
			}
		}
		return false;
	}

	/** An attack of {@code player} on {@code e}: participants of a ceremony cannot be hurt (true = no damage). */
	static boolean pukul(ServerLevel level, ServerPlayer player, Entity e) {
		for (Map<Integer, Jalan> m : List.copyOf(JALAN.values())) {
			for (Jalan j : List.copyOf(m.values())) {
				if (j.milik(e)) {
					PeristiwaJalan.namaBaik(player, j.situs, -1);
					Msg.actionBar(player, Component.translatable(L + "pukul"));
					return true;
				}
			}
		}
		return false;
	}

	// ================================================================== scheduler

	private static void tik(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (!SAPU.isEmpty()) {
			for (Entity e : SAPU) {
				if (!e.isRemoved() && !DISAPU.contains(e.getUUID())) {
					e.discard();
				}
			}
			SAPU.clear();
		}
		for (var it = JALAN.entrySet().iterator(); it.hasNext(); ) {
			Map<Integer, Jalan> m = it.next().getValue();
			for (Jalan j : List.copyOf(m.values())) {
				int hasil;
				try {
					hasil = j.langkahTik(level, now);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.warn("Ceremony {} of place {} failed", j.jenis, j.situs, exception);
					hasil = Jalan.TUNDA;
				}
				if (hasil != Jalan.LANJUT) {
					m.remove(j.id);
					try {
						j.akhiri(level);
					} catch (RuntimeException exception) {
						Kultivator.LOGGER.warn("Ceremony {} cleanup failed", j.jenis, exception);
					}
					if (hasil == Jalan.SELESAI) {
						j.tutup(level, "play");
					}
				}
			}
			if (m.isEmpty()) {
				it.remove();
			}
		}
		if (now % 40L == 13L && boleh()) {
			pindai(server, level);
		}
		if (now % 200L == 77L && boleh()) {
			kabarDuka(server, level);
		}
	}

	/** Every 2 s: the day of a ceremony came, starts it where a player is near, finishes the missed ones as data. */
	private static void pindai(MinecraftServer server, ServerLevel level) {
		KotaAcaraData store = KotaAcaraData.get(server);
		CompoundTag part = store.bagian(BAGIAN);
		long hari = KalenderKota.hari(server);
		long tod = Kota.timeOfDay(server) % 24000L;
		for (String key : List.copyOf(part.keySet())) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException exception) {
				continue;
			}
			ListTag list = part.getCompoundOrEmpty(key).getListOrEmpty("u");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag r = list.getCompoundOrEmpty(i);
				JenisUpacara jenis = JenisUpacara.byOrdinal(r.getByteOr("j", (byte) -1));
				int stage = r.getIntOr("s", 0);
				if (jenis == null || stage >= S_SELESAI) {
					continue;
				}
				int id = r.getIntOr("i", 0);
				long day = r.getLongOr("h", 0L);
				tandai(server, situs, jenis, r, hari);
				if (day > hari) {
					continue;
				}
				long t = tod - (jenis.jamMulai - 6L) * 1000L - r.getIntOr("o", 0);
				long panjang = (jenis.jamAkhir - jenis.jamMulai) * 1000L;
				if (day == hari && t >= -400L && stage < S_HARI) {
					r.putInt("s", S_HARI);
					fase(server, situs, jenis, id, r, 0);
					store.ubah();
				}
				boolean lewat = day < hari || t > panjang + 600L;
				if (!lewat && day == hari && t >= 0L && jalan(situs, id) == null) {
					TempatHuni huni = Pemanggil.huni(situs);
					Jalan j = huni == null ? null : buat(level, huni, jenis, id, r);
					if (j != null && j.aktifPada(t) && j.adaPenonton(level)) {
						JALAN.computeIfAbsent(situs, k -> new HashMap<>()).put(id, j);
						if (stage < S_MULAI) {
							r.putInt("s", S_MULAI);
							store.ubah();
						}
						j.mulai(level, Math.max(0L, t));
					} else if (j != null) {
						j.buang();
					}
				}
				if (lewat && jalan(situs, id) == null) {
					selesaiData(server, level, situs, jenis, id, r);
				}
			}
		}
	}

	/** House marks of the atmosphere (囍 three days, 奠 from the day before) for ceremonies of a place. */
	private static void tandai(MinecraftServer server, long situs, JenisUpacara jenis, CompoundTag r, long hari) {
		long day = r.getLongOr("h", 0L);
		if (jenis != JenisUpacara.NIKAH && jenis != JenisUpacara.MAKAM || hari < day - 1L || hari > day + 2L || hari < day && jenis == JenisUpacara.NIKAH) {
			return;
		}
		TempatHuni huni = Pemanggil.huni(situs);
		WargaData.Situs s = WargaData.get(server).peek(situs);
		if (huni == null || s == null) {
			return;
		}
		int rt = jenis == JenisUpacara.NIKAH ? r.getCompoundOrEmpty("d").getIntOr("rtP", 0) : r.getCompoundOrEmpty("d").getCompoundOrEmpty("mati").getIntOr("rt", 0);
		int rumah = rumahId(huni, s, rt);
		if (rumah >= 0) {
			KalenderKota.tandaiRumah(situs, rumah, jenis == JenisUpacara.NIKAH ? KotaPayloads.RUMAH_NIKAH : KotaPayloads.RUMAH_DUKA,
					jenis == JenisUpacara.NIKAH ? day + 2L : day);
		}
	}

	/** Fires bus phase {@code f} once (phases only grow) and returns whether it was new. */
	static boolean fase(MinecraftServer server, long situs, JenisUpacara jenis, int id, CompoundTag r, int f) {
		if (f <= r.getIntOr("f", -1) && f >= 0) {
			return false;
		}
		r.putInt("f", f);
		KotaAcaraData.get(server).ubah();
		KotaBus.UPACARA.invoker().onUpacara(server, situs, jenis, id, f);
		return true;
	}

	/** A missed ceremony: every phase on the bus, the zhuazhou pick, mood and gossip, then over. */
	private static void selesaiData(MinecraftServer server, ServerLevel level, long situs, JenisUpacara jenis, int id, CompoundTag r) {
		fase(server, situs, jenis, id, r, 0);
		fase(server, situs, jenis, id, r, 1);
		if (jenis == JenisUpacara.ZHUAZHOU) {
			WargaData data = WargaData.get(server);
			int[] ids = r.getIntArray("w").orElse(new int[0]);
			Warga ibu = ids.length > 1 ? data.byId(ids[1]) : null;
			Warga ayah = ids.length > 2 ? data.byId(ids[2]) : null;
			List<Integer> bayi = new ArrayList<>();
			if (ids.length > 0) {
				bayi.add(ids[0]);
			}
			for (int x : r.getIntArray("b").orElse(new int[0])) {
				bayi.add(x);
			}
			for (int x : bayi) {
				Warga b = data.byId(x);
				if (b != null && b.hidup()) {
					Zhuazhou.terapkan(level, situs, b, Zhuazhou.pilih(data, b, ibu, ayah));
				}
			}
		}
		tutup(server, level, situs, jenis, id, r, "data");
	}

	private static void tutup(MinecraftServer server, ServerLevel level, long situs, JenisUpacara jenis, int id, CompoundTag r, String sebab) {
		r.putInt("s", S_SELESAI);
		r.putInt("f", 99);
		KotaAcaraData.get(server).ubah();
		KotaBus.UPACARA.invoker().onUpacara(server, situs, jenis, id, -1);
		TempatHuni h = Pemanggil.huni(situs);
		if (h != null) {
			KotaPayloads.siar(level, Vec3.atCenterOf(h.pusat()), 256.0D, new KotaPayloads.Upacara(id, jenis.ordinal(), new long[0], new int[0], -1));
		}
		int kal = r.getIntOr("k", -1);
		if (kal > 0) {
			KalenderKota.batal(situs, kal);
		}
	}

	private static void tutupSemua(ServerLevel level) {
		for (Map<Integer, Jalan> m : List.copyOf(JALAN.values())) {
			for (Jalan j : List.copyOf(m.values())) {
				j.akhiri(level);
			}
		}
		JALAN.clear();
	}

	// ================================================================== death notices

	/**
	 * Within three days of a death, a player who knew the person (impression 20 or more) is told as he walks into the place:
	 * "Kakek Xiang passed away last night".
	 */
	private static void kabarDuka(MinecraftServer server, ServerLevel level) {
		long hari = KalenderKota.hari(server);
		CompoundTag part = KotaAcaraData.get(server).bagian(BAGIAN);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			TempatInfo info = dev.kultivator.dunia.kota42.KotaHidupCommands.tempatPemain(player);
			if (info == null) {
				continue;
			}
			CompoundTag town = part.getCompoundOrEmpty("kota_" + info.situsId());
			for (Tag t : town.getListOrEmpty("u")) {
				if (!(t instanceof CompoundTag r) || r.getByteOr("j", (byte) -1) != JenisUpacara.MAKAM.ordinal()) {
					continue;
				}
				long wafat = r.getLongOr("h", 0L) - 1L;
				CompoundTag mati = r.getCompoundOrEmpty("d").getCompoundOrEmpty("mati");
				ListTag kenal = mati.getListOrEmpty("kenal");
				ListTag sudah = r.getListOrEmpty("sd");
				String me = player.getUUID().toString();
				boolean tahu = false;
				for (int i = 0; i < kenal.size(); i++) {
					tahu |= me.equals(kenal.getStringOr(i, ""));
				}
				boolean diberi = false;
				for (int i = 0; i < sudah.size(); i++) {
					diberi |= me.equals(sudah.getStringOr(i, ""));
				}
				if (tahu && !diberi && hari >= wafat && hari <= wafat + 3L) {
					if (!r.contains("sd")) {
						r.put("sd", sudah);
					}
					sudah.add(StringTag.valueOf(me));
					KotaAcaraData.get(server).ubah();
					Msg.chat(player, Component.translatable(L + "kabar_duka." + (hari == wafat ? "semalam" : "baru"), NamaWarga.komponen(dariPotret(mati)))
							.withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	// ================================================================== places and routes

	/** House id ({@code Penduduk.rumah}) of household {@code rt}, or -1. */
	static int rumahId(TempatHuni huni, WargaData.Situs s, int rt) {
		if (rt <= 0) {
			return -1;
		}
		for (Warga o : s.rumahTangga(rt)) {
			Penduduk p = o.slot == Warga.TANPA_SLOT ? null : huni.orang(o.slot);
			if (p != null) {
				return p.rumah();
			}
		}
		return -1;
	}

	/** House position of household {@code rt}, or null. */
	static @Nullable BlockPos rumahPos(TempatHuni huni, WargaData.Situs s, int rt) {
		if (rt <= 0) {
			return null;
		}
		for (Warga o : s.rumahTangga(rt)) {
			Penduduk p = o.slot == Warga.TANPA_SLOT ? null : huni.orang(o.slot);
			if (p != null) {
				return huni.rumahPos(p);
			}
		}
		return null;
	}

	/** The street in front of the door nearest to {@code rumah} (two blocks out), else a standing spot at the house. */
	static @Nullable BlockPos pintu(ServerLevel level, TempatHuni huni, BlockPos rumah) {
		Titik best = null;
		double bd = 20.0D * 20.0D;
		for (Titik t : huni.titik("pintu")) {
			double d = t.pos().distSqr(rumah);
			if (d < bd) {
				bd = d;
				best = t;
			}
		}
		BlockPos from = best == null ? rumah : best.pos().relative(best.hadap(), 2);
		BlockPos spot = PeristiwaJalan.berdiri(level, from);
		return spot != null ? spot : PeristiwaJalan.berdiri(level, rumah);
	}

	/** A polyline on the ground. */
	static final class Garis {
		final List<Vec3> p = new ArrayList<>();
		double[] c = new double[0];

		static Garis dari(List<Vec3> titik) {
			Garis g = new Garis();
			g.p.addAll(titik);
			g.c = new double[g.p.size()];
			for (int i = 1; i < g.p.size(); i++) {
				g.c[i] = g.c[i - 1] + g.p.get(i - 1).distanceTo(g.p.get(i));
			}
			return g;
		}

		double panjang() {
			return c.length == 0 ? 0.0D : c[c.length - 1];
		}

		private int ruas(double s) {
			int seg = 1;
			while (seg < c.length - 1 && c[seg] < s) {
				seg++;
			}
			return seg;
		}

		Vec3 di(double s) {
			if (p.isEmpty()) {
				return Vec3.ZERO;
			}
			if (p.size() == 1 || s <= 0.0D) {
				return p.getFirst();
			}
			if (s >= panjang()) {
				return p.getLast();
			}
			int seg = ruas(s);
			double len = Math.max(1.0E-4D, c[seg] - c[seg - 1]);
			return p.get(seg - 1).lerp(p.get(seg), Mth.clamp((s - c[seg - 1]) / len, 0.0D, 1.0D));
		}

		/** Yaw of the walking direction at {@code s}. */
		float arah(double s) {
			if (p.size() < 2) {
				return 0.0F;
			}
			int seg = ruas(Mth.clamp(s, 0.0D, panjang()));
			Vec3 a = p.get(seg - 1);
			Vec3 b = p.get(seg);
			return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90.0F;
		}

		Garis balik() {
			List<Vec3> r = new ArrayList<>(p);
			java.util.Collections.reverse(r);
			return dari(r);
		}
	}

	/**
	 * The street line from {@code a} to {@code b}: the {@code jalan} nodes inside a corridor of 14 blocks in order, thickened
	 * to a point every 2 blocks with the ground height read there; a line shorter than 24 blocks bends through {@code via}.
	 */
	static Garis rute(ServerLevel level, TempatHuni huni, BlockPos a, BlockPos b, BlockPos via) {
		Vec3 va = Vec3.atBottomCenterOf(a);
		Vec3 vb = Vec3.atBottomCenterOf(b);
		List<Vec3> kunci = new ArrayList<>();
		kunci.add(va);
		double dx = vb.x - va.x;
		double dz = vb.z - va.z;
		double len2 = dx * dx + dz * dz;
		if (len2 < 24.0D * 24.0D) {
			kunci.add(Vec3.atBottomCenterOf(via));
		} else {
			List<double[]> mid = new ArrayList<>();
			for (Titik t : huni.titik("jalan")) {
				Vec3 q = Vec3.atBottomCenterOf(t.pos());
				double proj = ((q.x - va.x) * dx + (q.z - va.z) * dz) / len2;
				if (proj <= 0.06D || proj >= 0.94D) {
					continue;
				}
				double off = Math.hypot(va.x + dx * proj - q.x, va.z + dz * proj - q.z);
				if (off <= 14.0D) {
					mid.add(new double[]{proj, q.x, q.y, q.z});
				}
			}
			mid.sort(Comparator.comparingDouble(m -> m[0]));
			Vec3 last = va;
			for (double[] m : mid) {
				Vec3 q = new Vec3(m[1], m[2], m[3]);
				if (q.distanceTo(last) >= 7.0D && kunci.size() < 8) {
					kunci.add(q);
					last = q;
				}
			}
		}
		kunci.add(vb);
		List<Vec3> padat = new ArrayList<>();
		for (int i = 0; i + 1 < kunci.size(); i++) {
			Vec3 p = kunci.get(i);
			Vec3 q = kunci.get(i + 1);
			int n = Math.max(1, (int) Math.ceil(p.distanceTo(q) / 2.0D));
			for (int k = 0; k < n; k++) {
				double f = k / (double) n;
				Vec3 at = p.lerp(q, f);
				BlockPos g = TempatApi.groundSpot(level, Mth.floor(at.x), Mth.floor(at.z), Mth.floor(at.y));
				padat.add(g != null && Math.abs(g.getY() - at.y) <= 4.0D ? new Vec3(at.x, g.getY(), at.z) : at);
			}
		}
		padat.add(vb);
		return Garis.dari(padat);
	}

	// ================================================================== the running play (base)

	/** One member of a procession: placed {@code jarak} blocks behind the lead point, {@code sisi} blocks to the side. */
	record Anggota(LivingEntity badan, double jarak, double sisi) {
	}

	/** A ceremony being played. */
	abstract static class Jalan {
		static final int LANJUT = 0;
		static final int SELESAI = 1;
		static final int TUNDA = 2;

		final MinecraftServer server;
		final int id;
		final JenisUpacara jenis;
		final TempatHuni huni;
		final long situs;
		final CompoundTag rec;
		final int[] w;
		final WargaData.Situs s;
		final List<KultivatorNpc> badan = new ArrayList<>();
		final List<Entity> benda = new ArrayList<>();
		final Set<Integer> dipakai = new HashSet<>();
		final Set<UUID> pemberi = new HashSet<>();
		final Set<UUID> hadirSet = new HashSet<>();
		/** Outfit of the real residents taking part (applied whenever their body exists). */
		final Map<Integer, Integer> busana = new HashMap<>();
		int fase;
		long t0;
		long fase0;
		long sepi;
		long tikTerakhir;
		boolean dimulai;
		/** True while the cortege's musicians are the hired Suona troupe of the town (42.7.1). */
		boolean sewa;
		private long musikTik = Long.MIN_VALUE;

		/** Hires the Suona troupe for the next {@code ticks} when it is in town; the cortege plays louder and the plates say who they are. */
		void sewaSuona(long now, long ticks) {
			sewa = Rombongan.sewa(server, situs, now + ticks);
		}

		/** Names a cortege musician after the troupe when hired. */
		void namaiPemusik(@Nullable KultivatorNpc m) {
			if (sewa && m != null) {
				m.setPlate(Component.translatable("kultivator.kota.papan_nama", Component.translatable("kultivator.kota42.acara.rombongan.nama.suona"),
						Component.translatable(L + "pemusik_sewa")));
			}
		}

		/** Plays the tune over the ticks since the last call (the cortege's own music; call once per tick). */
		void musik(ServerLevel level, long now, Vec3 di, Lagu.Melodi lagu) {
			long dari = musikTik == Long.MIN_VALUE ? now - 1L : musikTik;
			musikTik = now;
			Lagu.mainkan(level, BlockPos.containing(di), lagu, dari - fase0, now - fase0, sewa ? 56.0D : 36.0D);
		}

		Jalan(MinecraftServer server, TempatHuni huni, JenisUpacara jenis, int id, CompoundTag rec, WargaData.Situs s) {
			this.server = server;
			this.huni = huni;
			this.situs = huni.situsId;
			this.jenis = jenis;
			this.id = id;
			this.rec = rec;
			this.s = s;
			this.w = rec.getIntArray("w").orElse(new int[0]);
		}

		// ---- to be filled by the kinds

		/** Places that count for a viewer (any within the show distance). */
		abstract List<BlockPos> titikAmat();

		/** The phase the clock asks for at {@code t} ticks after the start hour. */
		abstract int fasaAwal(long t);

		/** False while nothing is to be seen (the long afternoon between a wedding's bows and its banquet). */
		boolean aktifPada(long t) {
			return true;
		}

		/** Builds what phase {@code f} needs (called when the phase begins, also the first one). */
		abstract void masuk(ServerLevel level, int f, long now);

		/** Per tick inside phase {@link #fase}; returns the phase to go to, or {@code -1} to stay, {@code 99} when over. */
		abstract int langkah(ServerLevel level, long now, long t);

		/** True for an entity the ceremony owns or a resident taking part. */
		boolean milik(Entity e) {
			if (badan.contains(e) || benda.contains(e)) {
				return true;
			}
			if (e instanceof KultivatorNpc n && n.penduduk() && n.situsPenduduk() == situs) {
				Warga x = WargaApi.dari(n);
				return x != null && dipakai.contains(x.id);
			}
			return false;
		}

		/** A right-click; false lets the street events and the normal dialog take it. */
		boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
			long[] b = batasBeri(player);
			return bolehBeri() && beri(player, shift ? b[0] * 5L : b[0]);
		}

		boolean bolehBeri() {
			return false;
		}

		/** What the guest gives is the same everywhere; kinds add their thanks. */
		boolean beri(ServerPlayer player, long nb) {
			long[] b = batasBeri(player);
			if (!bolehBeri() || jarakHadir(player) > JARAK_BERI) {
				Msg.actionBar(player, Component.translatable(L + "beri.jauh"));
				return false;
			}
			if (pemberi.contains(player.getUUID())) {
				Msg.actionBar(player, Component.translatable(L + "beri.sudah"));
				return false;
			}
			if (jenis == JenisUpacara.NIKAH && !diundang(player)) {
				Msg.actionBar(player, Component.translatable(L + "beri.tanpa_undangan"));
				return false;
			}
			if (nb < b[0] || nb > b[1]) {
				Msg.actionBar(player, Component.translatable(L + "beri.batas", NilaiBatu.text(b[0]), NilaiBatu.text(b[1])));
				return false;
			}
			if (!NilaiBatu.pay(player, nb)) {
				Msg.actionBar(player, Component.translatable(L + "beri.kurang", NilaiBatu.text(nb)));
				return false;
			}
			pemberi.add(player.getUUID());
			terima(player, nb, b[0]);
			return true;
		}

		/** The thanks for a gift: impressions, memory, the gift back, advancement. */
		void terima(ServerPlayer player, long nb, long min) {
		}

		/** Hosts whose impression counts (the couple, the family). */
		List<Warga> tuanRumah() {
			List<Warga> out = new ArrayList<>();
			for (int id : w) {
				Warga x = id == 0 ? null : WargaData.get(server).byId(id);
				if (x != null && x.hidup()) {
					out.add(x);
				}
			}
			return out;
		}

		boolean diundang(ServerPlayer player) {
			int idx = WargaData.get(server).cariPemain(player.getUUID());
			if (idx < 0) {
				return false;
			}
			for (Warga x : tuanRumah()) {
				Ingatan i = x.ingatanDari(idx);
				if (i != null && i.kesan >= 20) {
					return true;
				}
			}
			return false;
		}

		BlockPos tempat() {
			List<BlockPos> t = titikAmat();
			return t.isEmpty() ? huni.pusat() : t.getFirst();
		}

		double jarakHadir(ServerPlayer player) {
			double best = Double.MAX_VALUE;
			for (BlockPos p : titikAmat()) {
				best = Math.min(best, player.position().distanceTo(Vec3.atCenterOf(p)));
			}
			for (KultivatorNpc n : badan) {
				if (!n.isRemoved()) {
					best = Math.min(best, player.distanceTo(n));
				}
			}
			return best;
		}

		boolean adaPenonton(ServerLevel level) {
			double r = KotaConfig.acara().jarakTampil;
			for (BlockPos p : titikAmat()) {
				if (KalenderKota.adaPemain(level, p, r)) {
					return true;
				}
			}
			return false;
		}

		// ---- machinery

		void mulai(ServerLevel level, long t) {
			dimulai = true;
			long now = level.getGameTime();
			t0 = now - t;
			int f = fasaAwal(t);
			ganti(level, f, now);
		}

		/** Called every tick; returns {@link #LANJUT}, {@link #SELESAI} (over) or {@link #TUNDA} (paused, nobody near). */
		int langkahTik(ServerLevel level, long now) {
			long t = now - t0;
			if (now % 20L == 0L) {
				boolean ada = adaPenonton(level);
				sepi = ada ? 0L : sepi + 20L;
				if (sepi >= SEPI) {
					return TUNDA;
				}
				for (ServerPlayer p : level.players()) {
					if (!p.isSpectator() && jarakHadir(p) <= JARAK_HADIR) {
						hadirSet.add(p.getUUID());
						KotaHidup.tandaiSibuk(p, KotaHidup.SIBUK_UPACARA, 60);
					}
				}
				if (now % 100L == 0L) {
					kirimPayload(level, fase);
				}
			}
			if (now % 40L == 5L) {
				for (Map.Entry<Integer, Integer> e : busana.entrySet()) {
					Warga x = WargaData.get(server).byId(e.getKey());
					KultivatorNpc body = x == null ? null : PeristiwaJalan.tubuh(huni, x);
					if (body != null && SosokApi.gaya(body).busanaKhusus() != e.getValue()) {
						SosokApi.setBusanaKhusus(body, e.getValue());
					}
				}
			}
			int next = langkah(level, now, t);
			if (next == 99) {
				return SELESAI;
			}
			if (next >= 0 && next != fase) {
				ganti(level, next, now);
			}
			return LANJUT;
		}

		void ganti(ServerLevel level, int f, long now) {
			fase = f;
			fase0 = now;
			musikTik = Long.MIN_VALUE;
			masuk(level, f, now);
			if (f >= 1) {
				fase(server, situs, jenis, id, rec, f);
				kirimPayload(level, f);
				Component line = Component.translatable(L + "fase." + jenis.id() + "." + f, namaUtama()).withStyle(
						jenis == JenisUpacara.MAKAM ? ChatFormatting.GRAY : ChatFormatting.GOLD);
				PeristiwaJalan.kabar(level, Vec3.atCenterOf(tempat()), 48.0D, line, true);
			}
		}

		Component namaUtama() {
			Warga a = pelaku(rec, s, 0);
			return a == null ? Component.literal("?") : NamaWarga.komponen(a);
		}

		/** The path nodes and participants of the payload; the kinds override. */
		long[] simpul() {
			return new long[0];
		}

		int[] peserta() {
			int[] ids = new int[Math.min(KotaPayloads.PESERTA_MAKS, badan.size())];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = badan.get(i).getId();
			}
			return ids;
		}

		void kirimPayload(ServerLevel level, int f) {
			KotaPayloads.siar(level, Vec3.atCenterOf(tempat()), KotaConfig.acara().jarakTampil + 32.0D,
					new KotaPayloads.Upacara(id, jenis.ordinal(), simpul(), peserta(), f));
		}

		/** Ends the play: what was made goes. */
		void akhiri(ServerLevel level) {
			buang();
			for (int wid : dipakai) {
				Warga x = WargaData.get(server).byId(wid);
				PeristiwaJalan.lepas(x);
				KultivatorNpc body = x == null ? null : PeristiwaJalan.tubuh(huni, x);
				if (body != null) {
					SosokApi.setBusanaKhusus(body, Gaya.BUSANA_OTOMATIS);
				}
			}
			dipakai.clear();
			HidupApi.bubarkan(level, situs);
		}

		void buang() {
			for (KultivatorNpc n : badan) {
				PeristiwaJalan.hilangkan(n);
			}
			badan.clear();
			for (Entity e : benda) {
				if (!e.isRemoved()) {
					DISAPU.remove(e.getUUID());
					e.discard();
				}
			}
			benda.clear();
		}

		void tutup(ServerLevel level, String sebab) {
			Upacara.tutup(server, level, situs, jenis, id, rec, sebab);
		}

		// ---- helpers shared by the kinds

		long sampai(long now, long ticks) {
			return now + ticks;
		}

		/** Sends resident {@code x} to {@code pos} to play {@code klip} there until game time {@code sampai}. */
		void kirimWarga(Warga x, String kunci, BlockPos pos, int klip, int prop, long sampai, @Nullable Integer busana) {
			if (x == null || !x.hidup()) {
				return;
			}
			dipakai.add(x.id);
			PeristiwaJalan.tugas(x, situs, PeristiwaJalan.di(kunci, pos, klip, prop), sampai);
			if (busana != null) {
				this.busana.put(x.id, busana);
			}
		}

		@Nullable KultivatorNpc stafBaru(NpcRole role, BlockPos pos, float yaw, int k) {
			KultivatorNpc npc = PeristiwaJalan.staf(server.overworld(), situs, role, pos, yaw, (int) PeristiwaJalan.acak(situs, id, 0xCE0L + k));
			if (npc != null) {
				badan.add(npc);
			}
			return npc;
		}

		@Nullable KultivatorNpc wakilBaru(Warga x, BlockPos pos, float yaw, @Nullable String keadaan) {
			KultivatorNpc npc = PeristiwaJalan.wakil(server.overworld(), huni, x, pos, yaw, keadaan);
			if (npc != null) {
				badan.add(npc);
			}
			return npc;
		}

		/** Residents to fill a feast: {@code utama} first (family), then the people with a bond to them, then anyone free. */
		List<Warga> tamu(Set<Integer> utama, int n, Set<Integer> kecuali) {
			List<Warga> out = new ArrayList<>();
			List<Warga> semua = new ArrayList<>(s.warga);
			semua.sort(Comparator.comparingLong(x -> PeristiwaJalan.acak(x.id, id, 0x7A3L)));
			for (int pass = 0; pass < 3 && out.size() < n; pass++) {
				for (Warga x : semua) {
					if (out.size() >= n) {
						break;
					}
					boolean fam = utama.contains(x.id);
					boolean ikat = false;
					for (int u : utama) {
						ikat |= x.hubunganDengan(u) != 0L;
					}
					boolean cocok = pass == 0 ? fam : pass == 1 ? ikat : true;
					if (cocok && x.hidup() && x.slot != Warga.TANPA_SLOT && !out.contains(x) && !kecuali.contains(x.id)
							&& !PeristiwaJalan.bertugas(x) && WargaApi.umur(x) >= (pass == 0 ? 3 : 14)) {
						out.add(x);
					}
				}
			}
			return out;
		}

		/** The living resident of an id of the record. */
		@Nullable Warga hidup(int i) {
			if (i >= w.length || w[i] == 0) {
				return null;
			}
			Warga x = WargaData.get(server).byId(w[i]);
			return x != null && x.hidup() ? x : null;
		}

		/** The resident named as {@code tokoh} living in the place, or null. */
		@Nullable Warga tokoh(String tokoh) {
			for (Warga x : s.warga) {
				if (tokoh.equals(x.tokoh) && x.hidup() && !PeristiwaJalan.bertugas(x)) {
					return x;
				}
			}
			return null;
		}

		/** Puts a formation along {@code g} at lead distance {@code lead} (teleports every member, yaw along the line). */
		void atur(Garis g, double lead, List<Anggota> list) {
			for (Anggota a : list) {
				LivingEntity e = a.badan();
				if (e.isRemoved() || e.isPassenger()) {
					continue;
				}
				double ss = Math.max(0.0D, lead - a.jarak());
				Vec3 pt = g.di(ss);
				float yaw = g.arah(ss);
				double r = Math.toRadians(yaw);
				Vec3 kanan = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
				Vec3 at = pt.add(kanan.scale(a.sisi()));
				e.teleportTo(at.x, at.y, at.z);
				e.setYRot(yaw);
				e.setYHeadRot(yaw);
				e.setYBodyRot(yaw);
				if (e instanceof KultivatorNpc n) {
					n.setHome(BlockPos.containing(at));
				}
			}
		}

		void suara(@Nullable KultivatorNpc npc, @Nullable String suara, String kunci, Object... isian) {
			PeristiwaJalan.seru(npc, suara, "upacara." + kunci, isian);
		}
	}

	// ================================================================== table and guests (wedding, month feast, zhuazhou, wake)

	/** A meal at one or two {@link MejaJamuanEntity}: the guests stand round and eat, the toasts are in step. */
	static final class Jamuan {
		final Jalan ceremony;
		final List<MejaJamuanEntity> meja = new ArrayList<>();
		final List<Warga> tamu = new ArrayList<>();
		final Map<Integer, BlockPos> kursi = new HashMap<>();
		final int varian;
		long bersulangT;
		int bersulang;

		Jamuan(Jalan ceremony, int varian) {
			this.ceremony = ceremony;
			this.varian = varian;
		}

		/** Sets {@code n} tables round {@code pusat} 3 blocks apart and seats {@code orang}; returns false when there was no room. */
		boolean sajikan(ServerLevel level, BlockPos pusat, int n, List<Warga> orang, long now, long lama) {
			for (int i = 0; i < n; i++) {
				BlockPos at = PeristiwaJalan.berdiri(level, pusat.offset(i * 3 - (n - 1) * 3 / 2, 0, 0));
				if (at == null || !DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
					continue;
				}
				MejaJamuanEntity m = KotaAcaraEntities.MEJA_JAMUAN.create(level, EntitySpawnReason.EVENT);
				if (m == null) {
					continue;
				}
				m.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, i * 45.0F, 0.0F);
				m.ikat(ceremony.situs, pusat.asLong());
				m.setVarian(varian);
				m.pegang(now + lama + 400L);
				if (level.addFreshEntity(m)) {
					m.jamuan((int) lama);
					meja.add(m);
					ceremony.benda.add(m);
				}
			}
			if (meja.isEmpty()) {
				return false;
			}
			for (int i = 0; i < orang.size(); i++) {
				MejaJamuanEntity m = meja.get(i % meja.size());
				int slot = i / meja.size();
				double a = Math.PI * 2.0D * (slot + 0.5D * (i % meja.size())) / Math.max(4, (orang.size() + meja.size() - 1) / meja.size());
				BlockPos spot = PeristiwaJalan.berdiri(level, BlockPos.containing(m.getX() + Math.cos(a) * 1.7D, m.getY(), m.getZ() + Math.sin(a) * 1.7D));
				if (spot != null) {
					tamu.add(orang.get(i));
					kursi.put(orang.get(i).id, spot);
					ceremony.kirimWarga(orang.get(i), "upacara_tamu", spot, GerakId.MAKAN, 0, now + lama + 200L,
							ceremony.jenis == JenisUpacara.MAKAM ? Gaya.BUSANA_BERKABUNG : Gaya.BUSANA_PESTA);
				}
			}
			bersulangT = now + 400L;
			return true;
		}

		/** Every tick: turn the guests to their table, now and then a toast in step with a cheer of one guest. */
		void tik(ServerLevel level, long now) {
			if (meja.isEmpty()) {
				return;
			}
			if (now % 40L == 0L) {
				for (Warga x : tamu) {
					KultivatorNpc body = PeristiwaJalan.tubuh(ceremony.huni, x);
					BlockPos spot = kursi.get(x.id);
					if (body != null && spot != null && body.blockPosition().distSqr(spot) <= 4.0D) {
						HidupApi.tatap(body, meja.get(Math.floorMod(x.id, meja.size())));
					}
				}
			}
			if (now >= bersulangT && varian != MejaJamuanEntity.VARIAN_DUKA) {
				bersulangT = now + 700L + level.getRandom().nextInt(400);
				bersulang++;
				List<KultivatorNpc> bodies = new ArrayList<>();
				for (Warga x : tamu) {
					KultivatorNpc body = PeristiwaJalan.tubuh(ceremony.huni, x);
					if (body != null) {
						bodies.add(body);
					}
				}
				for (KultivatorNpc body : bodies) {
					SosokApi.gerakSinkron(body, KotaGerakId.BERSULANG, now);
				}
				for (KultivatorNpc body : ceremony.badan) {
					if (body.isAlive() && body.entityTags().contains("kultivator_tamu_kepala")) {
						SosokApi.gerakSinkron(body, KotaGerakId.BERSULANG, now);
					}
				}
				if (!bodies.isEmpty()) {
					KultivatorNpc speaker = bodies.get(level.getRandom().nextInt(bodies.size()));
					PeristiwaJalan.seru(speaker, "kota42.bersulang", "upacara.bersulang." + level.getRandom().nextInt(4));
					level.playSound(null, speaker.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.2F);
					level.sendParticles(ParticleTypes.NOTE, speaker.getX(), speaker.getY() + 2.2D, speaker.getZ(), 4, 0.8D, 0.3D, 0.8D, 1.0D);
				}
			}
		}

		void lepas() {
			for (MejaJamuanEntity m : meja) {
				m.pegang(1L);
			}
			meja.clear();
			tamu.clear();
			kursi.clear();
		}
	}

	// ================================================================== factory

	private static @Nullable Jalan buat(ServerLevel level, TempatHuni huni, JenisUpacara jenis, int id, CompoundTag rec) {
		MinecraftServer server = level.getServer();
		WargaData.Situs s = WargaData.get(server).peek(huni.situsId);
		if (s == null) {
			return null;
		}
		try {
			return switch (jenis) {
				case NIKAH -> Pernikahan.baru(level, server, huni, id, rec, s);
				case MAKAM -> Pemakaman.baru(level, server, huni, id, rec, s);
				case LAHIR -> TelurMerah.baru(level, server, huni, id, rec, s);
				case SEBULAN -> PestaSebulan.baru(level, server, huni, id, rec, s);
				case ZHUAZHOU -> Zhuazhou.baru(level, server, huni, id, rec, s);
			};
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Ceremony {} could not be prepared in {}", jenis, huni.situsId, exception);
			return null;
		}
	}

	// ================================================================== wedding

	/** Wedding (42.7.7): the matchmaker, the groom's horse procession, the red sedan home, the three bows, the banquet. */
	static final class Pernikahan extends Jalan {
		/** Nominal start of phases 1-4, in ticks after 09:00. */
		static final long[] MULAI = {0L, 1500L, 2500L, 9000L};
		/** Fases: 1 fetching (matchmaker + horse), 2 the sedan, 3 the bows, 4 the banquet. */
		private final BlockPos pintuPria;
		private final BlockPos pintuWanita;
		private final BlockPos alun;
		private final Warga pria;
		private final Warga wanita;
		private final Set<Integer> keluarga = new HashSet<>();
		private @Nullable Garis garis;
		private double laju;
		private long garisT0;
		private final List<Anggota> barisan = new ArrayList<>();
		private @Nullable KudaTambatEntity kuda;
		private @Nullable KultivatorNpc mempelaiPria;
		private @Nullable KultivatorNpc mempelaiWanita;
		private @Nullable TanduEntity tandu;
		private final List<KultivatorNpc> pemikul = new ArrayList<>();
		private @Nullable KultivatorNpc comblang;
		private @Nullable KultivatorNpc pemandu;
		private @Nullable Jamuan jamuan;
		private boolean ketuk;
		private boolean sembah1;
		private boolean sembah2;
		private boolean sembah3;
		private boolean tiba;
		private boolean tanduMulai;
		private boolean bersih;
		private @Nullable Warga meiWarga;

		private Pernikahan(MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s, Warga pria, Warga wanita,
				BlockPos pintuPria, BlockPos pintuWanita, BlockPos alun) {
			super(server, huni, JenisUpacara.NIKAH, id, rec, s);
			this.pria = pria;
			this.wanita = wanita;
			this.pintuPria = pintuPria;
			this.pintuWanita = pintuWanita;
			this.alun = alun;
			for (Warga x : List.of(pria, wanita)) {
				keluarga.add(x.id);
				for (int p : new int[]{x.ayah, x.ibu}) {
					if (p != Warga.TANPA) {
						keluarga.add(p);
					}
				}
			}
		}

		static @Nullable Pernikahan baru(ServerLevel level, MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s) {
			int[] ids = rec.getIntArray("w").orElse(new int[0]);
			WargaData data = WargaData.get(server);
			Warga pria = ids.length > 0 ? data.byId(ids[0]) : null;
			Warga wanita = ids.length > 1 ? data.byId(ids[1]) : null;
			if (pria == null || wanita == null || !pria.hidup() || !wanita.hidup()) {
				return null;
			}
			CompoundTag d = rec.getCompoundOrEmpty("d");
			BlockPos hp = rumahPos(huni, s, d.getIntOr("rtP", pria.rumahTangga));
			BlockPos hw = rumahPos(huni, s, d.getIntOr("rtW", wanita.rumahTangga));
			BlockPos alun = KalenderKota.alun(huni);
			if (hp == null) {
				hp = PeristiwaJalan.rumah(huni, pria);
			}
			if (hw == null) {
				hw = PeristiwaJalan.rumah(huni, wanita);
			}
			BlockPos pp = pintu(level, huni, hp);
			BlockPos pw = pintu(level, huni, hw);
			if (pp == null || pw == null) {
				return null;
			}
			if (pp.distSqr(pw) < 36.0D) {
				BlockPos jauh = PeristiwaJalan.berdiri(level, alun);
				if (jauh != null && jauh.distSqr(pp) > 36.0D) {
					pw = jauh;
				}
			}
			return new Pernikahan(server, huni, id, rec, s, pria, wanita, pp, pw, alun);
		}

		@Override
		List<BlockPos> titikAmat() {
			return List.of(pintuPria, pintuWanita, alun);
		}

		@Override
		boolean milik(Entity e) {
			return super.milik(e) || e == kuda || e == tandu;
		}

		@Override
		int fasaAwal(long t) {
			int f = 1;
			for (int i = 1; i < MULAI.length; i++) {
				if (t >= MULAI[i]) {
					f = i + 1;
				}
			}
			return Math.min(4, f);
		}

		/** Nothing to see between the bows' end and the banquet. */
		@Override
		boolean aktifPada(long t) {
			return t < MULAI[2] + 900L || t >= MULAI[3];
		}

		@Override
		long[] simpul() {
			if (garis == null) {
				return new long[]{pintuPria.asLong(), pintuWanita.asLong()};
			}
			int n = Math.min(KotaPayloads.SIMPUL_MAKS, garis.p.size());
			long[] out = new long[n];
			for (int i = 0; i < n; i++) {
				out[i] = BlockPos.containing(garis.p.get(i * (garis.p.size() - 1) / Math.max(1, n - 1))).asLong();
			}
			return out;
		}

		@Override
		boolean bolehBeri() {
			return fase >= 3;
		}

		@Override
		void masuk(ServerLevel level, int f, long now) {
			switch (f) {
				case 1 -> masukJemput(level, now);
				case 2 -> masukArak(level, now);
				case 3 -> masukSembah(level, now);
				default -> masukJamuan(level, now);
			}
		}

		private void bersihkanBarisan() {
			for (Anggota a : barisan) {
				if (a.badan() instanceof KultivatorNpc n) {
					PeristiwaJalan.hilangkan(n);
					badan.remove(n);
				}
			}
			barisan.clear();
			for (KultivatorNpc p : pemikul) {
				PeristiwaJalan.hilangkan(p);
				badan.remove(p);
			}
			pemikul.clear();
			if (kuda != null && !kuda.isRemoved()) {
				kuda.ejectPassengers();
				kuda.discard();
				benda.remove(kuda);
			}
			kuda = null;
		}

		/** The couple's own bodies stay indoors while their stand-ins are out (no duplicate faces in the street). */
		private void sembunyikanPasangan(long now, long ticks) {
			PeristiwaJalan.tugas(pria, situs, Kegiatan.dalam("upacara_tuan_rumah"), now + ticks);
			PeristiwaJalan.tugas(wanita, situs, Kegiatan.dalam("upacara_tuan_rumah"), now + ticks);
			dipakai.add(pria.id);
			dipakai.add(wanita.id);
		}

		private void masukJemput(ServerLevel level, long now) {
			sembunyikanPasangan(now, 4000L);
			sewaSuona(now, 4600L);
			Garis g = rute(level, huni, pintuPria, pintuWanita, alun);
			garis = g;
			laju = Mth.clamp(g.panjang() / 800.0D, 0.04D, 0.09D);
			garisT0 = now + Math.max(60L, 450L - (now - t0));
			bersihkanBarisan();
			// the matchmaker: Bibi Mei in person when the town has her, else a staff body
			meiWarga = tokoh("bibi_mei");
			KultivatorNpc mak = meiWarga == null ? null : PeristiwaJalan.tubuh(huni, meiWarga);
			if (meiWarga != null && mak != null) {
				dipakai.add(meiWarga.id);
				comblang = mak;
				PeristiwaJalan.tugas(meiWarga, situs, PeristiwaJalan.di("upacara_comblang", pintuWanita, KotaGerakId.KETUK_PINTU, KotaGerakId.PROP_ALMANAK_MERAH),
						now + 1500L);
			} else {
				meiWarga = null;
				comblang = stafBaru(NpcRole.MAK_COMBLANG, pintuPria, 0.0F, 90);
			}
			ketuk = false;
			// the groom on his bridal horse
			float yaw = g.arah(0.0D);
			mempelaiPria = wakilBaru(pria, pintuPria, yaw, "pengantin");
			kuda = HewanKota.kudaPengantin(level, situs, pintuPria, yaw);
			if (mempelaiPria != null) {
				if (kuda != null && mempelaiPria.startRiding(kuda, true, false)) {
					SosokApi.gerak(mempelaiPria, KotaGerakId.TUNGGANG_KUDA, 0, 0, 0, 0, 0);
				} else {
					SosokApi.gerak(mempelaiPria, GerakId.JALAN_SANTAI, 0, 0, 0, 0, 0);
				}
			}
			if (kuda != null) {
				benda.add(kuda);
			}
			rombongan(g, 0.0D);
			suara(mempelaiPria, "kota42.nikah_jemput", "jemput");
			level.playSound(null, pintuPria, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.4F, 1.4F);
		}

		/**
		 * The cortege on the line, the lead point being the groom: four musicians 6 and 8 blocks ahead, the groom (on the horse)
		 * with the red parasol bearer at his side, the little followers behind ({@code ekor} blocks further back when the
		 * sedan comes between).
		 */
		private void rombongan(Garis g, double ekor) {
			barisan.clear();
			int[][] musik = {{KotaGerakId.TIUP_SUONA, KotaGerakId.PROP_SUONA}, {KotaGerakId.TIUP_SUONA, KotaGerakId.PROP_SUONA},
					{KotaGerakId.TABUH_GENDERANG_KECIL, KotaGerakId.PROP_GENDERANG_KECIL}, {KotaGerakId.TABUH_GENDERANG_KECIL, KotaGerakId.PROP_GENDERANG_KECIL}};
			for (int i = 0; i < 4; i++) {
				KultivatorNpc m = stafBaru(NpcRole.PEMUSIK, BlockPos.containing(g.di(0.0D)), 0.0F, 10 + i);
				if (m != null) {
					SosokApi.setBusanaKhusus(m, Gaya.BUSANA_PESTA);
					namaiPemusik(m);
					SosokApi.gerak(m, musik[i][0], 0, 0, musik[i][1], 0, 0);
					barisan.add(new Anggota(m, -6.0D - (i / 2) * 1.8D, i % 2 == 0 ? -0.9D : 0.9D));
				}
			}
			if (kuda != null && mempelaiPria != null && mempelaiPria.isPassenger()) {
				barisan.add(new Anggota(kuda, 0.0D, 0.0D));
			} else if (mempelaiPria != null) {
				barisan.add(new Anggota(mempelaiPria, 0.0D, 0.0D));
			}
			KultivatorNpc payung = stafBaru(NpcRole.PELAYAN, BlockPos.containing(g.di(0.0D)), 0.0F, 20);
			if (payung != null) {
				SosokApi.gerak(payung, GerakId.JALAN_SANTAI, 0, 0, KotaGerakId.PROP_PAYUNG_MERAH, 0, 0);
				barisan.add(new Anggota(payung, 0.0D, 1.5D));
			}
			for (int i = 0; i < 3; i++) {
				KultivatorNpc kecil = stafBaru(NpcRole.ANAK, BlockPos.containing(g.di(0.0D)), 0.0F, 30 + i);
				if (kecil != null) {
					SosokApi.gerak(kecil, GerakId.LARI, 0, 0, KotaGerakId.PROP_PERMEN_GULA, 0, 0);
					barisan.add(new Anggota(kecil, 3.0D + ekor + i * 1.6D, i % 2 == 0 ? -1.2D : 1.2D));
				}
			}
			atur(g, 0.0D, barisan);
		}

		private void masukArak(ServerLevel level, long now) {
			sembunyikanPasangan(now, 4000L);
			bersihkanBarisan();
			sewaSuona(now, 3400L);
			Garis g = rute(level, huni, pintuWanita, pintuPria, alun);
			garis = g;
			laju = Mth.clamp(g.panjang() / 900.0D, 0.04D, 0.07D);
			garisT0 = now + 200L;
			tiba = false;
			tanduMulai = false;
			float yaw = g.arah(0.0D);
			BlockPos awal = BlockPos.containing(g.di(0.0D));
			// the bride, veiled in red, steps from the door to the chair and is carried out of sight inside it
			mempelaiWanita = wakilBaru(wanita, pintuWanita, yaw + 180.0F, "pengantin");
			if (mempelaiWanita != null) {
				SosokApi.gerak(mempelaiWanita, GerakId.SIKAP_TUNDUK, 0, 0, 0, 0, 0);
			}
			// the red chair and its four bearers
			if (DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
				tandu = TemaEntities.TANDU.create(level, EntitySpawnReason.EVENT);
				if (tandu != null) {
					Vec3 first = g.di(0.0D);
					tandu.snapTo(first.x, first.y, first.z, yaw, 0.0F);
					tandu.ikat(situs, pintuWanita.asLong());
					tandu.setVarian(TanduEntity.VARIAN_DEWI_LAUT);
					tandu.jadikanPengantin();
					if (level.addFreshEntity(tandu)) {
						DuniaBudget.track(KotaAcaraEntities.ANGGARAN, tandu);
						benda.add(tandu);
					} else {
						tandu = null;
					}
				}
			}
			pemikul.clear();
			for (int i = 0; i < 4; i++) {
				KultivatorNpc p = stafBaru(NpcRole.KULI, awal, yaw, 40 + i);
				if (p != null) {
					SosokApi.setBusanaKhusus(p, Gaya.BUSANA_PESTA);
					pemikul.add(p);
				}
			}
			// the groom leads on the horse again (6 blocks ahead of the chair), the music before him, the children behind
			mempelaiPria = wakilBaru(pria, awal, yaw, "pengantin");
			kuda = HewanKota.kudaPengantin(level, situs, awal, yaw);
			if (mempelaiPria != null && kuda != null && mempelaiPria.startRiding(kuda, true, false)) {
				SosokApi.gerak(mempelaiPria, KotaGerakId.TUNGGANG_KUDA, 0, 0, 0, 0, 0);
			}
			if (kuda != null) {
				benda.add(kuda);
			}
			rombongan(g, 8.0D);
			level.playSound(null, pintuWanita, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.6F, 1.0F);
			level.playSound(null, pintuWanita, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.2F, 1.0F);
			Warga ibuW = wanita.ibu == Warga.TANPA ? null : WargaData.get(server).byId(wanita.ibu);
			if (ibuW != null && ibuW.hidup()) {
				BlockPos tepi = PeristiwaJalan.berdiri(level, pintuWanita.relative(Direction.fromYRot(yaw + 90.0F), 2));
				kirimWarga(ibuW, "upacara_lambai", tepi == null ? pintuWanita : tepi, GerakId.LAMBAI, 0, now + 1400L, null);
			}
			suara(comblang, "kota42.comblang", "comblang");
		}

		private void masukSembah(ServerLevel level, long now) {
			bersihkanBarisan();
			if (tandu != null && !tandu.isRemoved()) {
				tandu.letakkan();
			}
			sembah1 = false;
			sembah2 = false;
			sembah3 = false;
			BlockPos d = PeristiwaJalan.berdiri(level, pintuPria);
			if (d == null) {
				d = pintuPria;
			}
			Direction hadap = Direction.fromYRot(garis == null ? 0.0F : garis.arah(garis.panjang())).getOpposite();
			BlockPos kiri = PeristiwaJalan.berdiri(level, d.relative(hadap.getClockWise(), 1));
			BlockPos kanan = PeristiwaJalan.berdiri(level, d.relative(hadap.getCounterClockWise(), 1));
			float yaw = hadap.toYRot();
			if (mempelaiPria != null && !mempelaiPria.isRemoved()) {
				mempelaiPria.stopRiding();
			} else {
				mempelaiPria = wakilBaru(pria, d, yaw, "pengantin");
			}
			if (mempelaiWanita == null || mempelaiWanita.isRemoved()) {
				mempelaiWanita = wakilBaru(wanita, d, yaw, "pengantin");
			}
			for (KultivatorNpc c : new KultivatorNpc[]{mempelaiPria, mempelaiWanita}) {
				if (c != null && !badan.contains(c)) {
					badan.add(c);
				}
			}
			if (mempelaiPria != null) {
				Vec3 at = Vec3.atBottomCenterOf(kiri == null ? d : kiri);
				mempelaiPria.teleportTo(at.x, at.y, at.z);
				mempelaiPria.setYRot(yaw);
				mempelaiPria.setYHeadRot(yaw);
			}
			if (mempelaiWanita != null) {
				Vec3 at = Vec3.atBottomCenterOf(kanan == null ? d : kanan);
				mempelaiWanita.teleportTo(at.x, at.y, at.z);
				mempelaiWanita.setYRot(yaw);
				mempelaiWanita.setYHeadRot(yaw);
			}
			if (kuda != null) {
				kuda.ejectPassengers();
				kuda.discard();
				kuda = null;
			}
			// the master of ceremonies and the groom's parents
			BlockPos belakang = PeristiwaJalan.berdiri(level, d.relative(hadap.getOpposite(), 3));
			pemandu = stafBaru(NpcRole.TETUA_DESA, belakang == null ? d : belakang, yaw + 180.0F, 50);
			if (pemandu != null) {
				SosokApi.setBusanaKhusus(pemandu, Gaya.BUSANA_PESTA);
				SosokApi.gerak(pemandu, GerakId.BICARA_TANGAN, 0, 0, 0, 0, 0);
			}
			Warga ayah = pria.ayah == Warga.TANPA ? null : WargaData.get(server).byId(pria.ayah);
			Warga ibu = pria.ibu == Warga.TANPA ? null : WargaData.get(server).byId(pria.ibu);
			BlockPos o1 = PeristiwaJalan.berdiri(level, d.relative(hadap.getOpposite(), 3).relative(hadap.getClockWise(), 2));
			BlockPos o2 = PeristiwaJalan.berdiri(level, d.relative(hadap.getOpposite(), 3).relative(hadap.getCounterClockWise(), 2));
			if (ayah != null && ayah.hidup() && o1 != null) {
				kirimWarga(ayah, "upacara_orang_tua", o1, GerakId.SIKAP_TANGAN_BELAKANG, 0, now + 1100L, Gaya.BUSANA_PESTA);
			}
			if (ibu != null && ibu.hidup() && o2 != null) {
				kirimWarga(ibu, "upacara_orang_tua", o2, GerakId.SIKAP_TANGAN_DEPAN, 0, now + 1100L, Gaya.BUSANA_PESTA);
			}
			// the neighbours cheer
			for (Warga x : tamu(keluarga, 6, dipakai)) {
				BlockPos spot = PeristiwaJalan.berdiri(level, d.relative(hadap.getOpposite(), 5 + level.getRandom().nextInt(2))
						.relative(hadap.getClockWise(), level.getRandom().nextInt(7) - 3));
				if (spot != null) {
					kirimWarga(x, "upacara_sorak", spot, GerakId.TEPUK_TANGAN, 0, now + 1100L, Gaya.BUSANA_PESTA);
				}
			}
			level.playSound(null, d, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.NEUTRAL, 1.6F, 0.9F);
			level.sendParticles(ParticleTypes.FIREWORK, d.getX() + 0.5D, d.getY() + 2.5D, d.getZ() + 0.5D, 30, 1.5D, 1.0D, 1.5D, 0.12D);
		}

		private void masukJamuan(ServerLevel level, long now) {
			bersihkanBarisan();
			if (tandu != null && !tandu.isRemoved()) {
				tandu.discard();
				benda.remove(tandu);
				tandu = null;
			}
			for (KultivatorNpc c : new KultivatorNpc[]{mempelaiPria, mempelaiWanita, pemandu}) {
				if (c != null) {
					PeristiwaJalan.hilangkan(c);
					badan.remove(c);
				}
			}
			mempelaiPria = null;
			mempelaiWanita = null;
			pemandu = null;
			sembunyikanPasangan(now, 3200L);
			BlockPos venue = PeristiwaJalan.berdiri(level, pintuPria.relative(Direction.fromYRot(garis == null ? 0.0F : garis.arah(garis.panjang())).getOpposite(), 4));
			if (venue == null) {
				venue = PeristiwaJalan.berdiri(level, alun);
			}
			if (venue == null) {
				return;
			}
			Set<Integer> tuan = new HashSet<>(keluarga);
			List<Warga> orang = tamu(tuan, 12, new HashSet<>(List.of(pria.id, wanita.id)));
			jamuan = new Jamuan(this, MejaJamuanEntity.VARIAN_PERNIKAHAN);
			jamuan.sajikan(level, venue, orang.size() > 6 ? 2 : 1, orang, now, 2800L);
			// the couple at the head of the first table, in red
			if (!jamuan.meja.isEmpty()) {
				MejaJamuanEntity m = jamuan.meja.getFirst();
				BlockPos a = PeristiwaJalan.berdiri(level, BlockPos.containing(m.getX() - 0.8D, m.getY(), m.getZ() - 1.8D));
				BlockPos b = PeristiwaJalan.berdiri(level, BlockPos.containing(m.getX() + 0.8D, m.getY(), m.getZ() - 1.8D));
				mempelaiPria = wakilBaru(pria, a == null ? venue : a, 0.0F, "pengantin");
				mempelaiWanita = wakilBaru(wanita, b == null ? venue : b, 0.0F, "pengantin");
				for (KultivatorNpc c : new KultivatorNpc[]{mempelaiPria, mempelaiWanita}) {
					if (c != null) {
						c.addTag("kultivator_tamu_kepala");
						SosokApi.gerak(c, GerakId.MAKAN, 0, 0, 0, 0, 0);
						HidupApi.tatap(c, m);
					}
				}
			}
			level.sendParticles(ParticleTypes.HEART, venue.getX() + 0.5D, venue.getY() + 2.4D, venue.getZ() + 0.5D, 6, 0.6D, 0.3D, 0.6D, 0.0D);
		}

		@Override
		int langkah(ServerLevel level, long now, long t) {
			switch (fase) {
				case 1, 2 -> {
					if (garis == null) {
						return -1;
					}
					double len = garis.panjang();
					double s = Mth.clamp((now - garisT0) * laju, 0.0D, len);
					if (fase == 1 && !ketuk && comblang != null && !comblang.isRemoved()) {
						ketukPintu(level, now);
					}
					atur(garis, s + (fase == 2 ? 6.0D : 0.0D), barisan);
					if (now >= garisT0 - 30L) {
						// the four musicians walk 6 blocks ahead of the cortege lead (atur lead = s, or s + 6 with the sedan)
						musik(level, now, garis.di(Math.min(len, s + (fase == 2 ? 12.0D : 6.0D))), Lagu.SUONA_MELODI);
					}
					if (fase == 2 && tandu != null && !tandu.isRemoved()) {
						if (!tanduMulai && now >= garisT0) {
							tanduMulai = true;
							tandu.arak(garis.p, laju, false);
						}
						if (tanduMulai) {
							tandu.bawaPemikul(pemikul, KotaGerakId.PIKUL_TANDU);
						} else {
							for (int i = 0; i < pemikul.size(); i++) {
								Vec3 at = tandu.titikPemikul(i);
								pemikul.get(i).teleportTo(at.x, at.y, at.z);
							}
						}
					}
					if (fase == 2 && mempelaiWanita != null && !mempelaiWanita.isRemoved()) {
						if (s > 3.5D) {
							// the bride is inside the cabin now
							badan.remove(mempelaiWanita);
							PeristiwaJalan.hilangkan(mempelaiWanita);
							mempelaiWanita = null;
						} else if (tandu != null) {
							// before the chair moves she walks the few steps from the door to its side
							double f = Mth.clamp((now - fase0) / 160.0D, 0.0D, 1.0D);
							Vec3 at = Vec3.atBottomCenterOf(pintuWanita).lerp(tandu.position(), f);
							mempelaiWanita.teleportTo(at.x, at.y, at.z);
						}
					}
					if (now % 40L == 0L) {
						Vec3 p = garis.di(Math.min(len, s + 6.0D));
						level.sendParticles(ParticleTypes.FIREWORK, p.x, p.y + 3.0D, p.z, 3, 1.0D, 0.5D, 1.0D, 0.05D);
					}
					boolean sampaiJalur = s >= len - 0.01D;
					if (t >= MULAI[fase] && sampaiJalur || t >= MULAI[fase] + 1600L) {
						return fase + 1;
					}
				}
				case 3 -> {
					long u = now - fase0;
					if (!sembah1 && u >= 60L) {
						sembah1 = true;
						suara(pemandu, "kota42.nikah_sembah", "sembah.1");
						if (mempelaiPria != null && mempelaiWanita != null) {
							SosokApi.gerakSinkron(mempelaiPria, KotaGerakId.SEMBAH_NIKAH, now);
							SosokApi.gerakSinkron(mempelaiWanita, KotaGerakId.SEMBAH_NIKAH, now);
						}
					}
					if (!sembah2 && u >= 260L) {
						sembah2 = true;
						suara(pemandu, null, "sembah.2");
					}
					if (!sembah3 && u >= 460L) {
						sembah3 = true;
						suara(pemandu, null, "sembah.3");
						suara(pemandu, "kota42.nikah_selamat", "selamat");
						level.playSound(null, pintuPria, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.4F, 1.1F);
					}
					if (u == 700L && mempelaiPria != null && mempelaiWanita != null) {
						HidupApi.klip(mempelaiPria, GerakId.TERTAWA, 0, 60);
						HidupApi.klip(mempelaiWanita, GerakId.HORMAT, 0, 60);
					}
					if (!bersih && t >= MULAI[2] + 900L) {
						bersih = true;
						for (KultivatorNpc c : new KultivatorNpc[]{mempelaiPria, mempelaiWanita, pemandu}) {
							if (c != null) {
								PeristiwaJalan.hilangkan(c);
								badan.remove(c);
							}
						}
						mempelaiPria = null;
						mempelaiWanita = null;
						pemandu = null;
						if (tandu != null && !tandu.isRemoved()) {
							tandu.discard();
							benda.remove(tandu);
							tandu = null;
						}
						// the couple's real bodies come out again; the long afternoon is off screen
						lepasPasangan();
					}
					if (t >= MULAI[3]) {
						return 4;
					}
				}
				default -> {
					if (jamuan != null) {
						jamuan.tik(level, now);
						if (now - fase0 < 600L && !jamuan.meja.isEmpty()) {
							// the welcome toast: the suona air once or twice over the first tables
							musik(level, now, jamuan.meja.getFirst().position(), Lagu.SUONA_MELODI);
						}
					}
					if (t >= (jenis.jamAkhir - jenis.jamMulai) * 1000L - 100L || now - fase0 > 3200L) {
						return 99;
					}
				}
			}
			return -1;
		}

		/** The matchmaker walks to the bride's door ahead of the horse and knocks three times. */
		private void ketukPintu(ServerLevel level, long now) {
			KultivatorNpc c = comblang;
			Vec3 to = Vec3.atBottomCenterOf(pintuWanita);
			if (c == null || c.position().distanceToSqr(to) <= 9.0D) {
				ketuk = true;
				if (c != null) {
					HidupApi.klip(c, KotaGerakId.KETUK_PINTU, 0, 40);
					suara(c, "kota42.comblang", "comblang");
				}
				level.playSound(null, pintuWanita, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 1.0F);
				return;
			}
			if (meiWarga == null && now % 10L == 0L) {
				c.setHome(pintuWanita);
				c.getNavigation().moveTo(to.x, to.y, to.z, 0.9D);
				if (c.position().distanceToSqr(to) > 24.0D * 24.0D) {
					c.teleportTo(to.x, to.y, to.z);
				}
			}
		}

		private void lepasPasangan() {
			PeristiwaJalan.lepas(pria);
			PeristiwaJalan.lepas(wanita);
		}

		@Override
		void akhiri(ServerLevel level) {
			if (jamuan != null) {
				jamuan.lepas();
			}
			lepasPasangan();
			super.akhiri(level);
		}

		@Override
		void terima(ServerPlayer player, long nb, long min) {
			WargaApi.peristiwaPemain(player, situs, "nikah", pria, wanita);
			if (nb >= min * 4L) {
				WargaApi.ubahKesan(pria, player, 3, "angpao", null);
				WargaApi.ubahKesan(wanita, player, 3, "angpao", null);
			}
			Msg.chat(player, Component.translatable(L + "angpao.terima", NilaiBatu.text(nb), NamaWarga.komponen(pria), NamaWarga.komponen(wanita))
					.withStyle(ChatFormatting.GOLD));
			Advancements.grant(player, "tempat/kota42_nikah");
			PeristiwaJalan.beri(player, new ItemStack(KotaAcaraItems.KUE_PENGANTIN));
			level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		}

		private ServerLevel level() {
			return server.overworld();
		}
	}

	// ================================================================== funeral

	/** Funeral (42.7.7): the house of mourning, the coffin on eight shoulders, the hill, the lowering, the wake meal. */
	static final class Pemakaman extends Jalan {
		/** Fases: 1 house and procession, 2 the grave, 3 the wake meal. */
		private final BlockPos pintuRumah;
		private final BlockPos makam;
		private final Warga mati;
		private final Set<Integer> keluarga = new HashSet<>();
		private final List<Warga> pelayat = new ArrayList<>();
		private @Nullable Garis garis;
		private double laju;
		private long garisT0;
		private final List<Anggota> barisan = new ArrayList<>();
		private final List<KultivatorNpc> pemikul = new ArrayList<>();
		private @Nullable UsunganPetiEntity peti;
		private @Nullable KultivatorNpc pengurus;
		private @Nullable KultivatorNpc penabur;
		private @Nullable Warga baiWarga;
		private boolean turun;
		private boolean sujud;
		private @Nullable Jamuan jamuan;
		private long tiba = -1L;

		private Pemakaman(MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s, Warga mati, BlockPos pintuRumah,
				BlockPos makam) {
			super(server, huni, JenisUpacara.MAKAM, id, rec, s);
			this.mati = mati;
			this.pintuRumah = pintuRumah;
			this.makam = makam;
			for (Tag t : rec.getCompoundOrEmpty("d").getListOrEmpty("fam")) {
				keluarga.add(t.asInt().orElse(0));
			}
			keluarga.remove(0);
		}

		static @Nullable Pemakaman baru(ServerLevel level, MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s) {
			CompoundTag potret = rec.getCompoundOrEmpty("d").getCompoundOrEmpty("mati");
			Warga mati = dariPotret(potret);
			BlockPos rumah = rumahPos(huni, s, potret.getIntOr("rt", 0));
			if (rumah == null) {
				for (Tag t : rec.getCompoundOrEmpty("d").getListOrEmpty("fam")) {
					Warga f = WargaData.get(server).byId(t.asInt().orElse(0));
					if (f != null && f.slot != Warga.TANPA_SLOT && huni.orang(f.slot) != null && rumah == null) {
						rumah = huni.rumahPos(huni.orang(f.slot));
					}
				}
			}
			if (rumah == null) {
				rumah = huni.pusat();
			}
			BlockPos pintu = pintu(level, huni, rumah);
			BlockPos hill = bukit(level, huni, s, mati);
			if (pintu == null || hill == null) {
				return null;
			}
			return new Pemakaman(server, huni, id, rec, s, mati, pintu, hill);
		}

		/** The grave: this person's {@code nisan_<n>} titik, else the nearest hill titik, else a temple, else null. */
		private static @Nullable BlockPos bukit(ServerLevel level, TempatHuni huni, WargaData.Situs s, Warga mati) {
			int n = -1;
			for (Tag t : s.arsip) {
				if (t instanceof CompoundTag c && c.getIntOr("i", -1) == mati.id) {
					n = c.getIntOr("h", -1);
				}
			}
			List<Titik> nisan = n > 0 ? huni.titik("nisan_" + n) : List.of();
			BlockPos from = null;
			if (!nisan.isEmpty()) {
				from = nisan.getFirst().pos().relative(nisan.getFirst().hadap(), 2);
			} else {
				for (String type : new String[]{"makam", "kuil", "dupa"}) {
					List<Titik> list = huni.titik(type);
					if (!list.isEmpty() && from == null) {
						from = list.getFirst().pos();
					}
				}
			}
			return from == null ? null : PeristiwaJalan.berdiri(level, from);
		}

		@Override
		List<BlockPos> titikAmat() {
			return List.of(pintuRumah, makam);
		}

		@Override
		boolean milik(Entity e) {
			return super.milik(e) || e == peti;
		}

		@Override
		int fasaAwal(long t) {
			return t < 1500L ? 1 : t < 2300L ? 2 : 3;
		}

		@Override
		boolean bolehBeri() {
			return fase >= 1;
		}

		@Override
		long[] simpul() {
			if (garis == null) {
				return new long[]{pintuRumah.asLong(), makam.asLong()};
			}
			int n = Math.min(KotaPayloads.SIMPUL_MAKS, garis.p.size());
			long[] out = new long[n];
			for (int i = 0; i < n; i++) {
				out[i] = BlockPos.containing(garis.p.get(i * (garis.p.size() - 1) / Math.max(1, n - 1))).asLong();
			}
			return out;
		}

		@Override
		void masuk(ServerLevel level, int f, long now) {
			switch (f) {
				case 1 -> masukRumah(level, now);
				case 2 -> masukMakam(level, now);
				default -> masukJamuan(level, now);
			}
		}

		private void masukRumah(ServerLevel level, long now) {
			sewaSuona(now, 3400L);
			Garis g = rute(level, huni, pintuRumah, makam, KalenderKota.alun(huni));
			garis = g;
			laju = Mth.clamp(g.panjang() / 1000.0D, 0.04D, 0.08D);
			garisT0 = now + 360L;
			float yaw = g.arah(0.0D);
			// the steward leads: Paman Bai when the town has him
			Warga bai = tokoh("paman_bai");
			KultivatorNpc bTubuh = bai == null ? null : PeristiwaJalan.tubuh(huni, bai);
			if (bai != null && bTubuh != null) {
				dipakai.add(bai.id);
				baiWarga = bai;
				pengurus = bTubuh;
				PeristiwaJalan.tugas(bai, situs, PeristiwaJalan.di("upacara_pimpin", pintuRumah, KotaGerakId.TABUR_UANG_KERTAS, KotaGerakId.PROP_UANG_KERTAS), now + 3200L);
			} else {
				baiWarga = null;
				pengurus = stafBaru(NpcRole.PENGURUS_DUKA, pintuRumah, yaw, 60);
			}
			penabur = pengurus;
			suara(pengurus, "kota42.makam_pimpin", "pimpin");
			// the coffin on the trestles, the bearers, the musicians
			if (DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
				peti = KotaAcaraEntities.USUNGAN_PETI.create(level, EntitySpawnReason.EVENT);
				if (peti != null) {
					Vec3 at = g.di(0.0D);
					peti.snapTo(at.x, at.y, at.z, yaw, 0.0F);
					peti.ikat(situs, pintuRumah.asLong());
					peti.setVarian(WargaApi.umur(mati) >= 80 ? UsunganPetiEntity.VARIAN_MERAH : UsunganPetiEntity.VARIAN_HITAM);
					peti.setKeadaan(UsunganPetiEntity.KEADAAN_DIAM);
					peti.pegang(now + 6000L);
					if (level.addFreshEntity(peti)) {
						benda.add(peti);
					} else {
						peti = null;
					}
				}
			}
			pemikul.clear();
			for (int i = 0; i < 8; i++) {
				KultivatorNpc p = stafBaru(NpcRole.KULI, pintuRumah, yaw, 70 + i);
				if (p != null) {
					SosokApi.setRupa(p, NpcRole.KULI.id() + ".berkabung");
					pemikul.add(p);
				}
			}
			barisan.clear();
			for (int i = 0; i < 2; i++) {
				KultivatorNpc m = stafBaru(NpcRole.PEMUSIK, pintuRumah, yaw, 90 + i);
				if (m != null) {
					SosokApi.setRupa(m, NpcRole.PEMUSIK.id() + ".berkabung");
					namaiPemusik(m);
					SosokApi.gerak(m, KotaGerakId.TIUP_SUONA, 0, 0, KotaGerakId.PROP_SUONA, 0, 0);
					barisan.add(new Anggota(m, 11.0D, i == 0 ? -1.0D : 1.0D));
				}
			}
			// the family: eldest first with the portrait, in white (real bodies)
			pelayat.clear();
			Set<Integer> kecuali = new HashSet<>(dipakai);
			for (Warga x : tamu(keluarga, 8, kecuali)) {
				pelayat.add(x);
				dipakai.add(x.id);
			}
			level.playSound(null, pintuRumah, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.2F, 0.6F);
			PeristiwaJalan.kabar(level, Vec3.atCenterOf(pintuRumah), 32.0D, Component.translatable(L + "makam.rumah", NamaWarga.komponen(mati)).withStyle(ChatFormatting.GRAY),
					false);
		}

		/** The bearers' places round the coffin (two rows of four) with the lead point as the coffin centre. */
		private Vec3 pemikulDi(Vec3 pusat, float yaw, int i) {
			double r = Math.toRadians(yaw);
			Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
			Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
			double f = (i / 2 - 1.5D) * 1.2D;
			double sd = i % 2 == 0 ? 0.85D : -0.85D;
			return pusat.add(maju.scale(f)).add(sisi.scale(sd));
		}

		private void masukMakam(ServerLevel level, long now) {
			turun = false;
			sujud = false;
			tiba = now;
			for (KultivatorNpc m : pemikul) {
				SosokApi.henti(m);
			}
			if (peti != null && !peti.isRemoved()) {
				peti.setKeadaan(UsunganPetiEntity.KEADAAN_DIAM);
			}
			level.playSound(null, makam, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.4F, 0.5F);
			for (int i = 0; i < pelayat.size(); i++) {
				BlockPos spot = PeristiwaJalan.berdiri(level, makam.offset(-2 + i % 5, 0, 2 + i / 5));
				if (spot != null) {
					kirimWarga(pelayat.get(i), "upacara_duka", spot, i % 2 == 0 ? GerakId.SUJUD_DUPA : KotaGerakId.RATAP_DUKA, i % 2 == 0 ? PropId.DUPA : 0,
							now + 1100L, Gaya.BUSANA_BERKABUNG);
				}
			}
			PeristiwaJalan.kabar(level, Vec3.atCenterOf(makam), 48.0D, Component.translatable(L + "makam.bukit", NamaWarga.komponen(mati)).withStyle(ChatFormatting.GRAY),
					true);
		}

		private void masukJamuan(ServerLevel level, long now) {
			for (KultivatorNpc p : pemikul) {
				PeristiwaJalan.hilangkan(p);
				badan.remove(p);
			}
			pemikul.clear();
			for (Anggota a : barisan) {
				if (a.badan() instanceof KultivatorNpc n) {
					PeristiwaJalan.hilangkan(n);
					badan.remove(n);
				}
			}
			barisan.clear();
			BlockPos venue = PeristiwaJalan.berdiri(level, makam.offset(3, 0, 4));
			if (venue != null) {
				jamuan = new Jamuan(this, MejaJamuanEntity.VARIAN_DUKA);
				List<Warga> orang = new ArrayList<>(pelayat);
				jamuan.sajikan(level, venue, 1, orang, now, 600L);
			}
		}

		@Override
		int langkah(ServerLevel level, long now, long t) {
			switch (fase) {
				case 1 -> {
					if (garis == null) {
						return -1;
					}
					if (now < garisT0) {
						// at the house: the steward scatters paper money, the family wails, the bearers lift
						if (now % 40L == 0L) {
							if (penabur != null && !penabur.isRemoved()) {
								HidupApi.klip(penabur, KotaGerakId.TABUR_UANG_KERTAS, KotaGerakId.PROP_UANG_KERTAS, 40);
							}
							level.sendParticles(ParticleTypes.CLOUD, pintuRumah.getX() + 0.5D, pintuRumah.getY() + 2.2D, pintuRumah.getZ() + 0.5D, 6, 0.9D, 0.4D, 0.9D, 0.02D);
						}
						if (now % 80L == 0L) {
							for (int i = 0; i < pelayat.size(); i++) {
								BlockPos spot = PeristiwaJalan.berdiri(level, pintuRumah.offset(-1 + i % 4, 0, 1 + i / 4));
								if (spot != null) {
									kirimWarga(pelayat.get(i), "upacara_duka", spot, KotaGerakId.RATAP_DUKA, 0, now + 400L, Gaya.BUSANA_BERKABUNG);
								}
							}
							if (!pelayat.isEmpty() && now % 160L == 0L) {
								KultivatorNpc ratap = PeristiwaJalan.tubuh(huni, pelayat.getFirst());
								suara(ratap, "kota42.makam_ratap", "ratap." + level.getRandom().nextInt(4));
							}
						}
						Vec3 p0 = garis.di(0.0D);
						for (int i = 0; i < pemikul.size(); i++) {
							Vec3 at = pemikulDi(p0, garis.arah(0.0D), i);
							pemikul.get(i).teleportTo(at.x, at.y, at.z);
							pemikul.get(i).setYRot(garis.arah(0.0D));
							pemikul.get(i).setYHeadRot(garis.arah(0.0D));
						}
						return -1;
					}
					double s = Mth.clamp((now - garisT0) * laju, 0.0D, garis.panjang());
					Vec3 pt = garis.di(s);
					float yaw = garis.arah(s);
					if (peti != null && !peti.isRemoved()) {
						peti.pindah(pt, yaw);
						peti.setKeadaan(UsunganPetiEntity.KEADAAN_DIUSUNG);
						peti.pegang(now + 600L);
					}
					for (int i = 0; i < pemikul.size(); i++) {
						KultivatorNpc p = pemikul.get(i);
						Vec3 at = pemikulDi(pt, yaw, i);
						p.teleportTo(at.x, at.y, at.z);
						p.setYRot(yaw);
						p.setYHeadRot(yaw);
						p.setYBodyRot(yaw);
						if (SosokApi.gerakSekarang(p) != KotaGerakId.PIKUL_USUNGAN) {
							SosokApi.gerakSinkron(p, KotaGerakId.PIKUL_USUNGAN, garisT0);
						}
					}
					atur(garis, s + 5.0D, barisan);
					musik(level, now, garis.di(Math.max(0.0D, s - 6.0D)), Lagu.SUONA_DUKA);
					// the steward walks ahead scattering paper money; the family follows behind
					if (pengurus != null && !pengurus.isRemoved()) {
						Vec3 lead = garis.di(Math.min(garis.panjang(), s + 6.0D));
						pengurus.setHome(BlockPos.containing(lead));
						if (baiWarga != null) {
							PeristiwaJalan.tugas(baiWarga, situs, PeristiwaJalan.di("upacara_pimpin", BlockPos.containing(lead), KotaGerakId.TABUR_UANG_KERTAS,
									KotaGerakId.PROP_UANG_KERTAS), now + 100L);
						} else {
							pengurus.teleportTo(lead.x, lead.y, lead.z);
							pengurus.setYRot(yaw);
							pengurus.setYHeadRot(yaw);
							if (SosokApi.gerakSekarang(pengurus) != KotaGerakId.TABUR_UANG_KERTAS) {
								HidupApi.klip(pengurus, KotaGerakId.TABUR_UANG_KERTAS, KotaGerakId.PROP_UANG_KERTAS, 0);
							}
						}
						if (now % 15L == 0L) {
							level.sendParticles(ParticleTypes.CLOUD, lead.x, lead.y + 2.0D, lead.z, 3, 0.6D, 0.3D, 0.6D, 0.02D);
						}
					}
					if (now % 20L == 0L) {
						for (int i = 0; i < pelayat.size(); i++) {
							Vec3 at = garis.di(Math.max(0.0D, s - 3.5D - i * 1.1D));
							int klip = i == 0 ? KotaGerakId.RATAP_DUKA : GerakId.SIKAP_TUNDUK;
							kirimWarga(pelayat.get(i), "upacara_duka", BlockPos.containing(at), klip, i == 0 ? KotaGerakId.PROP_POTRET_ALMARHUM : 0, now + 60L,
									Gaya.BUSANA_BERKABUNG);
						}
						// townsfolk along the road lower their heads while the coffin passes
						for (KultivatorNpc n : PeristiwaJalan.tubuhDekat(level, situs, pt, 7.0D)) {
							Warga x = WargaApi.dari(n);
							if (x != null && !dipakai.contains(x.id) && n.getRandom().nextInt(4) == 0 && SosokApi.gerakSekarang(n) == GerakId.TIDAK_ADA) {
								HidupApi.klip(n, GerakId.HORMAT_DALAM, 0, 50);
							}
						}
					}
					if (s >= garis.panjang() - 0.01D) {
						return 2;
					}
				}
				case 2 -> {
					long u = now - tiba;
					if (peti != null && !peti.isRemoved() && !turun && u < 200L) {
						Vec3 at = Vec3.atBottomCenterOf(makam.relative(Direction.NORTH, 1));
						peti.pindah(at, peti.getYRot());
						peti.setKeadaan(UsunganPetiEntity.KEADAAN_DIAM);
					}
					for (int i = 0; i < pemikul.size() && u < 200L; i++) {
						Vec3 at = pemikulDi(Vec3.atBottomCenterOf(makam.relative(Direction.NORTH, 1)), peti == null ? 0.0F : peti.getYRot(), i);
						pemikul.get(i).teleportTo(at.x, at.y, at.z);
					}
					if (!turun && u >= 220L) {
						turun = true;
						if (peti != null && !peti.isRemoved()) {
							peti.tenggelamkan();
						}
						for (KultivatorNpc m : pemikul) {
							HidupApi.klip(m, GerakId.SIKAP_TUNDUK, 0, 400);
						}
						suara(pengurus, null, "makam.turun");
					}
					if (!sujud && u >= 300L) {
						sujud = true;
						for (Warga x : pelayat) {
							KultivatorNpc body = PeristiwaJalan.tubuh(huni, x);
							if (body != null) {
								HidupApi.klip(body, GerakId.SUJUD_DUPA, PropId.DUPA, 160);
							}
						}
						if (!pelayat.isEmpty()) {
							suara(PeristiwaJalan.tubuh(huni, pelayat.getLast()), "kota42.makam_sujud", "makam.sujud");
						}
						level.playSound(null, makam, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 0.5F);
					}
					if (u >= 500L && u % 30L == 0L) {
						for (int i = 0; i < pelayat.size(); i++) {
							KultivatorNpc body = PeristiwaJalan.tubuh(huni, pelayat.get(i));
							if (body != null && i % 3 == 0) {
								HidupApi.klip(body, KotaGerakId.BAKAR_UANG_KERTAS, KotaGerakId.PROP_UANG_KERTAS, 90);
							}
						}
						level.sendParticles(ParticleTypes.FLAME, makam.getX() + 0.5D, makam.getY() + 0.3D, makam.getZ() + 1.5D, 2, 0.2D, 0.1D, 0.2D, 0.01D);
					}
					if (u >= 800L) {
						return 3;
					}
				}
				default -> {
					if (jamuan != null) {
						jamuan.tik(level, now);
					}
					if (now - fase0 >= 700L) {
						return 99;
					}
				}
			}
			return -1;
		}

		@Override
		void akhiri(ServerLevel level) {
			if (jamuan != null) {
				jamuan.lepas();
			}
			if (peti != null && !peti.isRemoved()) {
				peti.pegang(1L);
			}
			super.akhiri(level);
		}

		@Override
		List<Warga> tuanRumah() {
			return new ArrayList<>(pelayat);
		}

		@Override
		void terima(ServerPlayer player, long nb, long min) {
			List<Warga> fam = new ArrayList<>(pelayat);
			WargaApi.peristiwaPemain(player, situs, "makam", fam.isEmpty() ? new Object[0] : new Object[]{fam.getFirst()});
			if (nb >= min * 4L && !fam.isEmpty()) {
				WargaApi.ubahKesan(fam.getFirst(), player, 3, "uang_duka", null);
			}
			Msg.chat(player, Component.translatable(L + "duka.terima", NilaiBatu.text(nb), NamaWarga.komponen(mati)).withStyle(ChatFormatting.GRAY));
			Advancements.grant(player, "tempat/kota42_makam");
			level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
		}

		private ServerLevel level() {
			return server.overworld();
		}
	}

	// ================================================================== red eggs (the birth day)

	/** The birth day: the father (else the mother) hands out red eggs to the neighbours and to players the family likes. */
	static final class TelurMerah extends Jalan {
		private @Nullable Warga pembagi;
		private @Nullable BlockPos tempat;
		private final Set<UUID> sudah = new HashSet<>();
		private final List<Warga> tetangga = new ArrayList<>();
		private boolean seru;

		private TelurMerah(MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s, Warga pembagi, BlockPos tempat) {
			super(server, huni, JenisUpacara.LAHIR, id, rec, s);
			this.pembagi = pembagi;
			this.tempat = tempat;
		}

		static @Nullable TelurMerah baru(ServerLevel level, MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s) {
			int[] ids = rec.getIntArray("w").orElse(new int[0]);
			WargaData data = WargaData.get(server);
			Warga pembagi = ids.length > 2 ? data.byId(ids[2]) : null;
			if (pembagi == null || !pembagi.hidup() || pembagi.slot == Warga.TANPA_SLOT) {
				pembagi = ids.length > 1 ? data.byId(ids[1]) : null;
			}
			if (pembagi == null || !pembagi.hidup() || pembagi.slot == Warga.TANPA_SLOT) {
				return null;
			}
			BlockPos rumah = PeristiwaJalan.rumah(huni, pembagi);
			BlockPos pintu = pintu(level, huni, rumah);
			return pintu == null ? null : new TelurMerah(server, huni, id, rec, s, pembagi, pintu);
		}

		@Override
		List<BlockPos> titikAmat() {
			return List.of(tempat);
		}

		@Override
		int fasaAwal(long t) {
			return 1;
		}

		@Override
		void masuk(ServerLevel level, int f, long now) {
			Warga p = pembagi;
			if (p == null) {
				return;
			}
			Set<Integer> fam = new HashSet<>();
			for (int i : w) {
				fam.add(i);
			}
			fam.add(p.id);
			dipakai.add(p.id);
			PeristiwaJalan.tugas(p, situs, PeristiwaJalan.di("upacara_telur", tempat, KotaGerakId.BAGI_TELUR, KotaGerakId.PROP_KERANJANG_TELUR_MERAH), now + 3600L);
			tetangga.clear();
			tetangga.addAll(tamu(fam, 4, dipakai));
			for (int i = 0; i < tetangga.size(); i++) {
				BlockPos spot = PeristiwaJalan.berdiri(level, tempat.offset(-2 + i, 0, 2 + (i % 2)));
				if (spot != null) {
					kirimWarga(tetangga.get(i), "upacara_selamat", spot, KotaGerakId.UCAP_SELAMAT, 0, now + 3000L, null);
				}
			}
		}

		@Override
		boolean milik(Entity e) {
			return pembagi != null && e instanceof KultivatorNpc n && n.penduduk() && n.situsPenduduk() == situs && WargaApi.dari(n) == pembagi
					|| super.milik(e);
		}

		@Override
		boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
			Warga p = pembagi;
			if (p == null || !(e instanceof KultivatorNpc n) || WargaApi.dari(n) != p) {
				return false;
			}
			if (sudah.contains(player.getUUID())) {
				suara(n, null, "telur.sudah");
				return true;
			}
			int idx = WargaData.get(server).cariPemain(player.getUUID());
			Ingatan i = idx < 0 ? null : p.ingatanDari(idx);
			if (i == null || i.kesan < 10) {
				suara(n, null, "telur.asing");
				Msg.actionBar(player, Component.translatable(L + "telur.kenal"));
				return true;
			}
			sudah.add(player.getUUID());
			HidupApi.tatap(n, player);
			HidupApi.klip(n, KotaGerakId.BAGI_TELUR, KotaGerakId.PROP_KERANJANG_TELUR_MERAH, 40);
			PeristiwaJalan.beri(player, new ItemStack(KotaAcaraItems.TELUR_MERAH, 2));
			WargaApi.ubahKesan(p, player, 2, "telur_merah", null);
			suara(n, "kota42.bayi_lahir", "telur.beri." + level.getRandom().nextInt(3));
			Msg.chat(player, Component.translatable(L + "telur.terima", NamaWarga.komponen(p)).withStyle(ChatFormatting.RED));
			return true;
		}

		@Override
		int langkah(ServerLevel level, long now, long t) {
			if (!seru && now - fase0 > 40L) {
				seru = true;
				KultivatorNpc body = pembagi == null ? null : PeristiwaJalan.tubuh(huni, pembagi);
				suara(body, "kota42.bayi_lahir", "telur.mulai");
			}
			if (now % 100L == 0L) {
				// the neighbours come up one by one to say congratulations
				for (Warga x : tetangga) {
					KultivatorNpc body = PeristiwaJalan.tubuh(huni, x);
					if (body != null && pembagi != null) {
						HidupApi.tatap(body, PeristiwaJalan.tubuh(huni, pembagi));
						if (body.getRandom().nextInt(3) == 0) {
							HidupApi.klip(body, KotaGerakId.UCAP_SELAMAT, 0, 50);
						}
					}
				}
			}
			return now - fase0 >= 3000L || t >= 11000L ? 99 : -1;
		}
	}

	// ================================================================== the one-month feast

	/** The one-month feast (18:00-21:00): a small banquet at the house, the mother holding the swaddled baby. */
	static final class PestaSebulan extends Jalan {
		private final BlockPos venue;
		private final BlockPos pintuRumah;
		private @Nullable Jamuan jamuan;
		private @Nullable Warga ibu;
		private boolean selamat;

		private PestaSebulan(MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s, BlockPos venue, BlockPos pintuRumah) {
			super(server, huni, JenisUpacara.SEBULAN, id, rec, s);
			this.venue = venue;
			this.pintuRumah = pintuRumah;
		}

		static @Nullable PestaSebulan baru(ServerLevel level, MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s) {
			int[] ids = rec.getIntArray("w").orElse(new int[0]);
			WargaData data = WargaData.get(server);
			Warga ibu = ids.length > 1 ? data.byId(ids[1]) : null;
			Warga ayah = ids.length > 2 ? data.byId(ids[2]) : null;
			Warga tuan = ibu != null && ibu.hidup() ? ibu : ayah;
			if (tuan == null || !tuan.hidup()) {
				return null;
			}
			BlockPos pintu = pintu(level, huni, PeristiwaJalan.rumah(huni, tuan));
			if (pintu == null) {
				return null;
			}
			BlockPos venue = PeristiwaJalan.berdiri(level, pintu.offset(0, 0, 3));
			return new PestaSebulan(server, huni, id, rec, s, venue == null ? pintu : venue, pintu);
		}

		@Override
		List<BlockPos> titikAmat() {
			return List.of(venue);
		}

		@Override
		int fasaAwal(long t) {
			return 1;
		}

		@Override
		boolean bolehBeri() {
			return true;
		}

		@Override
		void masuk(ServerLevel level, int f, long now) {
			ibu = hidup(1);
			Set<Integer> fam = new HashSet<>();
			for (int i : w) {
				fam.add(i);
			}
			for (int i : w) {
				Warga x = i == 0 ? null : WargaData.get(server).byId(i);
				if (x != null) {
					fam.add(x.ayah);
					fam.add(x.ibu);
					for (int c : x.anak) {
						fam.add(c);
					}
				}
			}
			fam.remove(0);
			List<Warga> orang = tamu(fam, 7, dipakai);
			jamuan = new Jamuan(this, MejaJamuanEntity.VARIAN_PESTA);
			jamuan.sajikan(level, venue, 1, orang, now, 2800L);
			if (ibu != null) {
				// the mother stands at the table's head holding the swaddled baby
				BlockPos head = PeristiwaJalan.berdiri(level, venue.offset(0, 0, -2));
				dipakai.add(ibu.id);
				PeristiwaJalan.tugas(ibu, situs, PeristiwaJalan.di("upacara_gendong", head == null ? venue : head, KotaGerakId.GENDONG_BAYI, KotaGerakId.PROP_BAYI_BEDONG),
						now + 3000L);
			}
		}

		@Override
		boolean milik(Entity e) {
			return super.milik(e) || ibu != null && e instanceof KultivatorNpc n && n.penduduk() && WargaApi.dari(n) == ibu;
		}

		@Override
		void terima(ServerPlayer player, long nb, long min) {
			Warga tuan = ibu != null ? ibu : hidup(2);
			if (tuan != null) {
				WargaApi.peristiwaPemain(player, situs, "hadiah_biasa", tuan);
				WargaApi.ubahKesan(tuan, player, 6, "sebulan", null);
			}
			PeristiwaJalan.beri(player, new ItemStack(KotaAcaraItems.TELUR_MERAH, 2));
			Msg.chat(player, Component.translatable(L + "sebulan.terima", NilaiBatu.text(nb)).withStyle(ChatFormatting.RED));
		}

		@Override
		int langkah(ServerLevel level, long now, long t) {
			if (jamuan != null) {
				jamuan.tik(level, now);
				if (!selamat && now - fase0 >= 160L && !jamuan.tamu.isEmpty()) {
					selamat = true;
					suara(PeristiwaJalan.tubuh(huni, jamuan.tamu.getFirst()), "kota42.sebulan_selamat", "sebulan.selamat");
				}
			}
			return now - fase0 >= 2800L || t >= (jenis.jamAkhir - jenis.jamMulai) * 1000L - 100L ? 99 : -1;
		}

		@Override
		void akhiri(ServerLevel level) {
			if (jamuan != null) {
				jamuan.lepas();
			}
			super.akhiri(level);
		}
	}

	// ================================================================== zhuazhou

	/** Zhuazhou at one year (10:00-11:00): the baby crawls to one of eight objects on the mat; the choice shapes its path. */
	static final class Zhuazhou extends Jalan {
		/** The eight objects of the mat in {@code Pekerjaan.ZHUAZHOU} order (stand-in items: the objects are scenery). */
		private static final net.minecraft.world.item.Item[] BENDA = {null, Items.BRUSH, Items.BAMBOO_FENCE_GATE, Items.WOODEN_SWORD, Items.GOLD_BLOCK,
				Items.BOOK, Items.MACE, Items.IRON_CHAIN, Items.GOLD_NUGGET};
		private final BlockPos tikar;
		private final BlockPos pintuRumah;
		private final List<Warga> bayi = new ArrayList<>();
		private final List<Integer> pilihan = new ArrayList<>();
		private final List<KultivatorNpc> bodi = new ArrayList<>();
		private final List<Vec3> posBenda = new ArrayList<>();
		private final List<Display.ItemDisplay> hias = new ArrayList<>();
		private @Nullable Warga ibu;
		private @Nullable Warga ayah;
		private long mulaiPilih;
		private boolean berseru;
		private boolean diterapkan;
		private @Nullable Jamuan jamuan;

		private Zhuazhou(MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s, BlockPos tikar, BlockPos pintuRumah) {
			super(server, huni, JenisUpacara.ZHUAZHOU, id, rec, s);
			this.tikar = tikar;
			this.pintuRumah = pintuRumah;
		}

		static @Nullable Zhuazhou baru(ServerLevel level, MinecraftServer server, TempatHuni huni, int id, CompoundTag rec, WargaData.Situs s) {
			int[] ids = rec.getIntArray("w").orElse(new int[0]);
			WargaData data = WargaData.get(server);
			Warga bayi = ids.length > 0 ? data.byId(ids[0]) : null;
			Warga ibu = ids.length > 1 ? data.byId(ids[1]) : null;
			Warga ayah = ids.length > 2 ? data.byId(ids[2]) : null;
			Warga tuan = ibu != null && ibu.hidup() ? ibu : ayah != null && ayah.hidup() ? ayah : null;
			if (bayi == null || !bayi.hidup() || tuan == null) {
				return null;
			}
			BlockPos pintu = pintu(level, huni, PeristiwaJalan.rumah(huni, tuan));
			if (pintu == null) {
				return null;
			}
			BlockPos tikar = PeristiwaJalan.berdiri(level, pintu.offset(0, 0, 4));
			return new Zhuazhou(server, huni, id, rec, s, tikar == null ? pintu : tikar, pintu);
		}

		@Override
		List<BlockPos> titikAmat() {
			return List.of(tikar);
		}

		@Override
		int fasaAwal(long t) {
			return t >= 600L ? 2 : 1;
		}

		@Override
		boolean bolehBeri() {
			return fase >= 1;
		}

		/** The weighted pick (parents' trade, a spirit root, a hash) for baby {@code b}: 1-8. */
		static int pilih(WargaData data, Warga b, @Nullable Warga ibu, @Nullable Warga ayah) {
			if (b.zhuazhou > 0) {
				return b.zhuazhou;
			}
			int[] bobot = {0, 10, 10, 10, 10, 10, 10, 10, 10};
			for (Warga p : new Warga[]{ibu, ayah}) {
				if (p == null) {
					continue;
				}
				switch (p.peranNpc().kelompok) {
					case CENDEKIA -> {
						bobot[1] += 12;
						bobot[5] += 12;
					}
					case PEDAGANG -> {
						bobot[2] += 12;
						bobot[8] += 12;
					}
					case PENJAGA -> bobot[3] += 22;
					case PEJABAT -> bobot[4] += 22;
					case PEKERJA -> bobot[6] += 22;
					case RAHIB -> bobot[7] += 22;
					default -> {
					}
				}
			}
			if (b.akar >= 2) {
				bobot[3] += 30;
			}
			int total = 0;
			for (int x : bobot) {
				total += x;
			}
			int r = (int) Math.floorMod(PeristiwaJalan.acak(b.id, 0x5A5AL, b.lahir), (long) total);
			for (int i = 1; i <= 8; i++) {
				r -= bobot[i];
				if (r < 0) {
					return i;
				}
			}
			return 1;
		}

		/** Records the pick on the baby; the sword of a rooted baby is the news of the town. */
		static void terapkan(ServerLevel level, long situs, Warga b, int benda) {
			if (b.zhuazhou > 0 || benda < 1 || benda > 8) {
				return;
			}
			b.zhuazhou = benda;
			WargaData.get(level.getServer()).ubah();
			if (benda == 3 && b.akar >= 2) {
				GosipWarga.lahir(level, situs, "ACARA", Fakta.Rujuk.warga(b.id), Fakta.Rujuk.kata("calon_pendekar"));
			}
		}

		@Override
		void masuk(ServerLevel level, int f, long now) {
			if (f == 1) {
				masukTikar(level, now);
			} else {
				masukJamuan(level, now);
			}
		}

		private void masukTikar(ServerLevel level, long now) {
			WargaData data = WargaData.get(server);
			bayi.clear();
			for (int i = 0; i < w.length; i++) {
				Warga b = i == 0 || i >= 3 ? data.byId(w[i]) : null;
				if (b != null && b.hidup()) {
					bayi.add(b);
				}
			}
			for (int x : rec.getIntArray("b").orElse(new int[0])) {
				Warga b = data.byId(x);
				if (b != null && b.hidup() && !bayi.contains(b)) {
					bayi.add(b);
				}
			}
			ibu = w.length > 1 ? data.byId(w[1]) : null;
			ayah = w.length > 2 ? data.byId(w[2]) : null;
			pilihan.clear();
			for (Warga b : bayi) {
				pilihan.add(pilih(data, b, ibu, ayah));
			}
			// eight objects round the mat, 1.2 blocks from the middle
			posBenda.clear();
			for (int i = 0; i < 8; i++) {
				double a = Math.PI * 2.0D * i / 8.0D;
				posBenda.add(Vec3.atBottomCenterOf(tikar).add(Math.cos(a) * 1.2D, 0.0D, Math.sin(a) * 1.2D));
			}
			for (int i = 0; i < 8; i++) {
				Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
				if (d == null) {
					break;
				}
				Vec3 at = posBenda.get(i);
				d.setItemStack(new ItemStack(BENDA[i + 1]));
				d.setItemTransform(ItemDisplayContext.GROUND);
				d.setTransformation(new Transformation(new Vector3f(0.0F, 0.18F, 0.0F), null, new Vector3f(0.55F, 0.55F, 0.55F), null));
				d.snapTo(at.x, at.y + 0.02D, at.z, i * 45.0F, 0.0F);
				d.setViewRange(0.9F);
				d.addTag(TAG_BENDA);
				DISAPU.add(d.getUUID());
				if (level.addFreshEntity(d)) {
					hias.add(d);
					benda.add(d);
				}
			}
			bodi.clear();
			for (int i = 0; i < bayi.size(); i++) {
				Warga b = bayi.get(i);
				Vec3 mid = Vec3.atBottomCenterOf(tikar).add(i * 0.8D - (bayi.size() - 1) * 0.4D, 0.0D, 0.0D);
				KultivatorNpc body = PeristiwaJalan.staf(level, situs, NpcRole.ANAK, BlockPos.containing(mid), 0.0F, b.look);
				if (body == null) {
					continue;
				}
				badan.add(body);
				bodi.add(body);
				body.setPlate(Component.translatable("kultivator.kota.papan_nama", NamaWarga.komponen(b), Component.translatable(L + "peran.bayi")));
				Sosok sosok = SosokApi.sosok(body).withKelamin(b.pria() ? Sosok.LAKI : Sosok.PEREMPUAN).withUsia(Sosok.ANAK);
				SosokApi.setSosok(body, sosok);
				AttributeInstance skala = body.getAttribute(Attributes.SCALE);
				if (skala != null) {
					skala.setBaseValue(0.45D);
				}
				body.teleportTo(mid.x, mid.y, mid.z);
				SosokApi.gerak(body, GerakId.DUDUK_LANTAI, 0, 0, KotaGerakId.PROP_KERINCING_BAYI, 0, 0);
			}
			Warga tuan = ibu != null ? ibu : ayah;
			Set<Integer> fam = new HashSet<>();
			for (int i : w) {
				fam.add(i);
			}
			if (ibu != null && ibu.hidup()) {
				BlockPos a = PeristiwaJalan.berdiri(level, tikar.offset(-2, 0, 0));
				if (a != null) {
					kirimWarga(ibu, "upacara_tuan_rumah", a, KotaGerakId.GENDONG_BAYI, 0, now + 1400L, null);
				}
			}
			if (ayah != null && ayah.hidup()) {
				BlockPos a = PeristiwaJalan.berdiri(level, tikar.offset(2, 0, 0));
				if (a != null) {
					kirimWarga(ayah, "upacara_tuan_rumah", a, GerakId.TEPUK_TANGAN, 0, now + 1400L, null);
				}
			}
			int j = 0;
			for (Warga x : tamu(fam, 5, dipakai)) {
				BlockPos spot = PeristiwaJalan.berdiri(level, tikar.offset(-3 + (j % 3) * 3, 0, 3 + j / 3));
				j++;
				if (spot != null) {
					kirimWarga(x, "upacara_sorak", spot, GerakId.TEPUK_TANGAN, 0, now + 1400L, null);
				}
			}
			mulaiPilih = now + 160L;
			berseru = false;
			diterapkan = false;
			PeristiwaJalan.kabar(level, Vec3.atCenterOf(tikar), 32.0D, Component.translatable(L + "zhuazhou.mulai", tuan == null ? Component.literal("?") : NamaWarga.komponen(tuan))
					.withStyle(ChatFormatting.GOLD), false);
		}

		private void masukJamuan(ServerLevel level, long now) {
			terapkanSemua(level);
			for (KultivatorNpc b : bodi) {
				PeristiwaJalan.hilangkan(b);
				badan.remove(b);
			}
			bodi.clear();
			for (Display.ItemDisplay d : hias) {
				DISAPU.remove(d.getUUID());
				d.discard();
				benda.remove(d);
			}
			hias.clear();
			Set<Integer> fam = new HashSet<>();
			for (int i : w) {
				fam.add(i);
			}
			List<Warga> orang = tamu(fam, 6, new HashSet<>());
			jamuan = new Jamuan(this, MejaJamuanEntity.VARIAN_PESTA);
			jamuan.sajikan(level, tikar, 1, orang, now, 600L);
		}

		private void terapkanSemua(ServerLevel level) {
			if (diterapkan) {
				return;
			}
			diterapkan = true;
			for (int i = 0; i < bayi.size() && i < pilihan.size(); i++) {
				terapkan(level, situs, bayi.get(i), pilihan.get(i));
			}
		}

		@Override
		boolean milik(Entity e) {
			return super.milik(e) || hias.contains(e);
		}

		@Override
		boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
			if (e instanceof KultivatorNpc n && bodi.contains(n)) {
				HidupApi.tatap(n, player);
				PeristiwaJalan.seru(n, null, "upacara.zhuazhou.bayi." + level.getRandom().nextInt(4));
				level.sendParticles(ParticleTypes.HEART, n.getX(), n.getY() + 0.9D, n.getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
				return true;
			}
			return super.klik(level, player, e, shift);
		}

		@Override
		void terima(ServerPlayer player, long nb, long min) {
			Warga tuan = ibu != null ? ibu : ayah;
			if (tuan != null) {
				WargaApi.peristiwaPemain(player, situs, "hadiah_biasa", tuan);
				WargaApi.ubahKesan(tuan, player, 6, "zhuazhou", null);
			}
			Msg.chat(player, Component.translatable(L + "zhuazhou.terima", NilaiBatu.text(nb)).withStyle(ChatFormatting.GOLD));
		}

		@Override
		int langkah(ServerLevel level, long now, long t) {
			if (fase == 1) {
				if (now < mulaiPilih) {
					return -1;
				}
				long u = now - mulaiPilih;
				// each baby crawls to its object (100 ticks), then lifts it up
				for (int i = 0; i < bodi.size() && i < pilihan.size(); i++) {
					KultivatorNpc b = bodi.get(i);
					if (b.isRemoved()) {
						continue;
					}
					Vec3 target = posBenda.get(Mth.clamp(pilihan.get(i) - 1, 0, 7));
					Vec3 from = Vec3.atBottomCenterOf(tikar).add(i * 0.8D - (bayi.size() - 1) * 0.4D, 0.0D, 0.0D);
					if (u < 120L) {
						double f = u / 120.0D;
						Vec3 at = from.lerp(target.subtract(target.subtract(from).normalize().scale(0.35D)), f);
						b.teleportTo(at.x, at.y, at.z);
						float yaw = (float) (Mth.atan2(target.z - from.z, target.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
						b.setYRot(yaw);
						b.setYHeadRot(yaw);
						b.setYBodyRot(yaw);
						if (SosokApi.gerakSekarang(b) != KotaGerakId.MERANGKAK) {
							SosokApi.gerak(b, KotaGerakId.MERANGKAK, 0, 0, 0, 0, 0);
						}
					} else if (u >= 120L && u < 130L) {
						SosokApi.gerak(b, KotaGerakId.PILIH_BENDA, 0, 0, PROP_BENDA[Mth.clamp(pilihan.get(i), 0, 8)], 0, 0);
					}
				}
				if (!berseru && u >= 150L) {
					berseru = true;
					terapkanSemua(level);
					int benda1 = pilihan.isEmpty() ? 1 : pilihan.getFirst();
					boolean pedang = benda1 == 3;
					boolean sarjana = benda1 == 1 || benda1 == 5;
					KultivatorNpc penutur = PeristiwaJalan.tubuh(huni, ibu != null && ibu.hidup() ? ibu : ayah != null ? ayah : null);
					suara(penutur, pedang ? "kota42.zhuazhou_pedang" : sarjana ? "kota42.zhuazhou_kuas" : null, "zhuazhou.pilih." + benda1);
					level.playSound(null, tikar, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.4F, 1.3F);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, tikar.getX() + 0.5D, tikar.getY() + 1.4D, tikar.getZ() + 0.5D, 18, 1.4D, 0.6D, 1.4D, 0.0D);
					for (ServerPlayer p : level.players()) {
						if (!p.isSpectator() && jarakHadir(p) <= JARAK_HADIR) {
							Advancements.grant(p, "tempat/kota42_zhuazhou");
						}
					}
					PeristiwaJalan.kabar(level, Vec3.atCenterOf(tikar), 32.0D, Component.translatable(L + "zhuazhou.hasil." + benda1,
							bayi.isEmpty() ? Component.literal("?") : NamaWarga.komponen(bayi.getFirst())).withStyle(ChatFormatting.GOLD), false);
				}
				if (u >= 520L) {
					return 2;
				}
				return -1;
			}
			if (jamuan != null) {
				jamuan.tik(level, now);
			}
			return now - fase0 >= 560L ? 99 : -1;
		}

		@Override
		void akhiri(ServerLevel level) {
			terapkanSemua(level);
			if (jamuan != null) {
				jamuan.lepas();
			}
			for (Display.ItemDisplay d : hias) {
				DISAPU.remove(d.getUUID());
			}
			super.akhiri(level);
		}
	}

	// ================================================================== test commands

	/**
	 * {@code uji kota_hidup acara nikah|makam|lahir|sebulan|zhuazhou}: books the ceremony for random residents of the place the
	 * tester stands in and starts it now (the clock offset puts its first hour at this moment); merged with 42D's
	 * {@code acara} node by Brigadier.
	 */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("acara");
		for (JenisUpacara jenis : JenisUpacara.values()) {
			root.then(Commands.literal(jenis.id()).executes(ctx -> ujiJalan(ctx, jenis))
					.then(Commands.literal("data").executes(ctx -> ujiData(ctx, jenis))));
		}
		root.then(Commands.literal("upacara").executes(ctx -> ujiDaftar(ctx)));
		return root;
	}

	private static int ujiDaftar(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		TempatInfo info = dev.kultivator.dunia.kota42.KotaHidupCommands.tempatPemain(p);
		if (info == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.tanpa_tempat"));
			return 0;
		}
		List<Info> list = daftar(ctx.getSource().getServer(), info.situsId());
		ctx.getSource().sendSuccess(() -> Component.translatable(L + "uji.daftar", list.size()).withStyle(ChatFormatting.GOLD), false);
		for (Info i : list) {
			ctx.getSource().sendSuccess(() -> Component.literal("  #" + i.id() + " ").append(i.nama()).append(Component.literal(
					" h" + i.hari() + " " + i.jam() + ":00 " + i.fase() + (i.berlangsung() ? " *" : "")).withStyle(ChatFormatting.DARK_GRAY)), false);
		}
		return list.size();
	}

	private static int ujiJalan(CommandContext<CommandSourceStack> ctx, JenisUpacara jenis) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return ujiBuat(ctx, jenis, true);
	}

	private static int ujiData(CommandContext<CommandSourceStack> ctx, JenisUpacara jenis) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return ujiBuat(ctx, jenis, false);
	}

	private static int ujiBuat(CommandContext<CommandSourceStack> ctx, JenisUpacara jenis, boolean sekarang) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo info = dev.kultivator.dunia.kota42.KotaHidupCommands.tempatPemain(player);
		MinecraftServer server = ctx.getSource().getServer();
		if (info == null || !boleh()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.tanpa_tempat"));
			return 0;
		}
		long situs = info.situsId();
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		if (s == null || s.warga.size() < 4) {
			ctx.getSource().sendFailure(Component.translatable(L + "uji.tanpa_warga"));
			return 0;
		}
		RandomSource r = RandomSource.create(PeristiwaJalan.acak(situs, server.getTickCount(), 0xCEBL));
		List<Warga> dewasa = new ArrayList<>();
		List<Warga> kecil = new ArrayList<>();
		for (Warga w : s.warga) {
			if (w.hidup() && w.slot != Warga.TANPA_SLOT) {
				(WargaApi.umur(w) >= 16 ? dewasa : kecil).add(w);
			}
		}
		Warga[] set;
		int[] rt = null;
		switch (jenis) {
			case NIKAH -> {
				Warga a = null;
				Warga b = null;
				for (Warga w : dewasa) {
					if (w.pria() && a == null) {
						a = w;
					}
					if (!w.pria() && b == null && (a == null || w.rumahTangga != a.rumahTangga)) {
						b = w;
					}
				}
				if (a == null || b == null) {
					ctx.getSource().sendFailure(Component.translatable(L + "uji.tanpa_warga"));
					return 0;
				}
				set = new Warga[]{a, b};
				rt = new int[]{rumahAsal(data, a), rumahAsal(data, b)};
			}
			case MAKAM -> {
				Warga a = dewasa.isEmpty() ? null : dewasa.get(r.nextInt(dewasa.size()));
				if (a == null) {
					ctx.getSource().sendFailure(Component.translatable(L + "uji.tanpa_warga"));
					return 0;
				}
				set = keluargaMati(server, a);
			}
			default -> {
				Warga bayi = !kecil.isEmpty() ? kecil.get(r.nextInt(kecil.size())) : dewasa.get(r.nextInt(dewasa.size()));
				Warga ibu = bayi.ibu == Warga.TANPA ? null : data.byId(bayi.ibu);
				Warga ayah = bayi.ayah == Warga.TANPA ? null : data.byId(bayi.ayah);
				Warga m = ibu != null && ibu.hidup() ? ibu : dewasa.get(r.nextInt(dewasa.size()));
				set = ayah != null && ayah.hidup() ? new Warga[]{bayi, m, ayah} : new Warga[]{bayi, m};
			}
		}
		long tod = Kota.timeOfDay(server) % 24000L;
		long geser = sekarang ? tod - (jenis.jamMulai - 6L) * 1000L : 0L;
		int id = rt == null ? jadwalkan(server, situs, jenis, geser, set) : jadwalkan(server, situs, jenis, geser, set, rt);
		if (id < 0) {
			ctx.getSource().sendFailure(Component.translatable(L + "uji.ditolak"));
			return 0;
		}
		// the test ceremony is for today, not tomorrow
		CompoundTag town = KotaAcaraData.get(server).situs(BAGIAN, situs);
		for (Tag t : town.getListOrEmpty("u")) {
			if (t instanceof CompoundTag c && c.getIntOr("i", -1) == id) {
				c.putLong("h", KalenderKota.hari(server));
				if (!sekarang) {
					c.putInt("o", (int) (tod - (jenis.jamMulai - 6L) * 1000L - (jenis.jamAkhir - jenis.jamMulai) * 1000L - 700L));
				}
			}
		}
		KotaAcaraData.get(server).ubah();
		ctx.getSource().sendSuccess(() -> Component.translatable(L + "uji.dijadwalkan", jenis.nama(), id).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}
