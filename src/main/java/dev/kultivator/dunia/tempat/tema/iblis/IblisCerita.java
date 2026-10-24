package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The story words of the Kota Iblis for the 38ST engine (DESIGN 38.T.7.11, 38.S.1): conditions, effects and goal kinds
 * that {@code data/kultivator/tempat/{dialog,quest}/tema_iblis*.json} use, and the scripted parts of the chain
 * {@code tema_iblis_rantai} "Rantai yang Putus" (seven chapters, three routes Lurus / Penebusan / Darah) and its four
 * side quests. Ids are prefixed {@code iblis_} so they never clash with 38ST's, inti's or another theme's.
 * <pre>
 * syarat  iblis_di_kota | iblis_rute R|- | iblis_penguasa ID | iblis_gelar ID | iblis_serbu | iblis_pesta
 *         | iblis_afu_bebas | iblis_kenal_minyak | iblis_xue_kalah | iblis_kitab_ada | iblis_tawanan_ada
 *         | iblis_lao_ada | iblis_audiensi
 * akibat  iblis_bayar_upeti | iblis_rute R | iblis_afu abai|pastikan | iblis_minyak beli | iblis_beri peta|topeng|kitab_kuno
 *         | iblis_serbu | iblis_pihak lurus|kota | iblis_pemburu menyerah|lawan|lari | iblis_turnamen daftar
 *         | iblis_xue_bos | iblis_hadiah kipas|kitab | iblis_akhir lurus|penebusan|darah | iblis_jual_kontrak
 *         | iblis_lao pensiun|terhormat | iblis_anak bantu|lapor | iblis_topeng_regu | iblis_akses_lantai | iblis_tebus_lagi
 *         | iblis_ye_pergi
 * tujuan  iblis_upeti | iblis_arena | iblis_bebas | iblis_pulang | iblis_afu | iblis_kitab | iblis_kontrak | iblis_buronan
 *         | iblis_berburu | iblis_pemburu | iblis_serbu | iblis_minyak | iblis_xue | iblis_topeng | iblis_lao | iblis_anak
 *         (each "&lt;kind&gt; &lt;id|*&gt;"; no argument matches any id)
 * </pre>
 * Xue Yao's battle (chapter 6, routes Lurus/Penebusan): realm band + 2, three phases - the blood sword (Strength), at
 * two thirds two gladiators answer her call, at one third the blood pool pulses (an area of 6 blocks, red dust, 3 damage
 * every 3 s); at a tenth she yields and leaves the city for good (no death of a ruler; flag {@code xue_yao_kalah}).
 */
public final class IblisCerita {

	public static final String QUEST = "tema_iblis_rantai";
	public static final String QUEST_TOPENG = "tema_iblis_topeng";
	public static final String QUEST_LAO = "tema_iblis_gladiator_tua";
	public static final String QUEST_KITAB = "tema_iblis_kitab";
	public static final String QUEST_ANAK = "tema_iblis_anak_liar";

	public static final String UPETI = "iblis_upeti";
	public static final String MENANG_ARENA = "iblis_arena";
	public static final String TAWANAN_BEBAS = "iblis_bebas";
	public static final String TAWANAN_PULANG = "iblis_pulang";
	public static final String AFU = "iblis_afu";
	public static final String KITAB = "iblis_kitab";
	public static final String KONTRAK = "iblis_kontrak";
	public static final String BURONAN = "iblis_buronan";
	public static final String BERBURU = "iblis_berburu";
	public static final String PEMBURU = "iblis_pemburu";
	public static final String SERBU = "iblis_serbu";
	public static final String MINYAK = "iblis_minyak";
	public static final String XUE = "iblis_xue";
	public static final String TOPENG = "iblis_topeng";
	public static final String LAO = "iblis_lao";
	public static final String ANAK = "iblis_anak";

	static final List<String> TUJUAN = List.of(UPETI, MENANG_ARENA, TAWANAN_BEBAS, TAWANAN_PULANG, AFU, KITAB, KONTRAK, BURONAN, BERBURU, PEMBURU,
			SERBU, MINYAK, XUE, TOPENG, LAO, ANAK);

	/** Shadow Oil share: total 200 x (band + 1) NB paid in 5 daily parts. */
	public static final int MINYAK_BAGI = 200;
	public static final int MINYAK_HARI = 5;
	public static final long PENSIUN_LAO = 120L;
	public static final long ROTI_ANAK = 20L;

	/** Xue Yao's battle: her body -> its state. */
	private static final class Bos {
		final UUID player;
		final long situs;
		int fase = 1;
		long denyut;
		final List<KultivatorNpc> panggilan = new ArrayList<>();

		Bos(UUID player, long situs) {
			this.player = player;
			this.situs = situs;
		}
	}

	private static final Map<UUID, Bos> BOS = new HashMap<>();
	private static final Map<UUID, KultivatorNpc> BOS_TUBUH = new HashMap<>();
	/** Side quest Topeng: the squad carrying the stolen mask, per player. */
	private static final Map<UUID, List<LivingEntity>> TOPENG_REGU = new HashMap<>();

	private IblisCerita() {
	}

	static void init() {
		syarat();
		akibat();
		for (String kind : TUJUAN) {
			QuestTracker.daftarTujuan(kind, (args, jenis, id) -> jenis.equals(kind) && (args.isEmpty() || "*".equals(args.get(0)) || args.get(0).equals(id)));
		}
		// the chain starts with the first toll paid in any Kota Iblis (upetiPertama); the side quests are offered by their characters
		DuelEndListeners.add((session, winner, loser) -> {
			if (winner instanceof ServerPlayer p && loser instanceof KultivatorNpc npc) {
				if (TokohIblis.adalah(npc, TokohIblis.HAN_QINGSHUANG)) {
					CeritaApi.peristiwa(p, SERBU, "han_mundur");
				} else if (TokohIblis.adalah(npc, TokohIblis.YE_WUHEN)) {
					CeritaApi.peristiwa(p, XUE, "ye_duel");
				} else if (TokohIblis.adalah(npc, TokohIblis.LAO_KUANG) && QuestTracker.aktif(p, QUEST_LAO)) {
					lao(p, "terhormat");
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(IblisCerita::bolehLuka);
		ServerLivingEntityEvents.AFTER_DEATH.register(IblisCerita::mati);
	}

	static void clear() {
		BOS.clear();
		BOS_TUBUH.clear();
		TOPENG_REGU.clear();
	}

	/** From {@link Gerbang#bayar}: the first toll ever paid starts the chain (Ye Wuhen waits at the Warning Field). */
	static void upetiPertama(ServerPlayer p, TemaKota.Kota kota) {
		if (QuestTracker.tahap(p, QUEST) == 0) {
			CeritaApi.mulaiQuest(p, QUEST, kota.situsId());
		}
	}

	// ================================================================== conditions

	private static void syarat() {
		Syarat.daftar("iblis_di_kota", (p, a) -> IblisKota.di(p) != null);
		Syarat.daftar("iblis_rute", (p, a) -> {
			String want = a.isEmpty() ? "-" : a.get(0);
			String rute = IblisData.rute(p);
			return "-".equals(want) ? rute.isEmpty() : want.equals(rute);
		});
		Syarat.daftar("iblis_penguasa", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && !a.isEmpty() && a.get(0).equals(IblisData.penguasa(p.level().getServer(), kota.situsId()));
		});
		Syarat.daftar("iblis_gelar", (p, a) -> !a.isEmpty() && IblisData.gelar(p, a.get(0)));
		Syarat.daftar("iblis_serbu", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && PenyerbuanLurus.berlangsung(kota.situsId());
		});
		Syarat.daftar("iblis_pesta", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && PestaDarah.berjalan(kota.situsId());
		});
		Syarat.daftar("iblis_afu_bebas", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && IblisData.kota(p.level().getServer(), kota.situsId()).getBooleanOr("a_fu_bebas", false);
		});
		// Hong Pao recognises a player who met his oil in the harbour (chapter 2) or the canal city (chapter 3)
		Syarat.daftar("iblis_kenal_minyak", (p, a) -> QuestTracker.tahap(p, "tema_pelabuhan_pelita") >= 3 || QuestTracker.tahap(p, "tema_air_benang") >= 4);
		Syarat.daftar("iblis_xue_kalah", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && IblisData.kota(p.level().getServer(), kota.situsId()).getBooleanOr("xue_yao_kalah", false);
		});
		Syarat.daftar("iblis_kitab_ada", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && IblisData.kota(p.level().getServer(), kota.situsId()).getIntOr("kitab_stok", 1) > 0;
		});
		Syarat.daftar("iblis_tawanan_ada", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && !KandangTawanan.daftar(p.level(), kota).isEmpty();
		});
		Syarat.daftar("iblis_lao_ada", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && !IblisData.kota(p.level().getServer(), kota.situsId()).getBooleanOr("lao_kuang_pergi", false);
		});
		Syarat.daftar("iblis_audiensi", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			return kota != null && (IblisMeter.gentar(p, kota.situsId()) >= IblisMeter.GENTAR_AUDIENSI || "darah".equals(IblisData.rute(p))
					|| QuestTracker.aktif(p, QUEST) && "pilih".equals(QuestTracker.simpul(p, QUEST)));
		});
	}

	/** The Kota Iblis the player stands in, else the nearest within 600 blocks (talks at the hunters' camp). */
	static @Nullable TemaKota.Kota kota(ServerPlayer p) {
		TemaKota.Kota kota = IblisKota.di(p);
		return kota != null ? kota : IblisKota.terdekat(p.level(), p.blockPosition(), 600);
	}

	// ================================================================== effects

	private static void akibat() {
		Akibat.daftar("iblis_bayar_upeti", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			if (kota != null) {
				Gerbang.bayar(p, kota);
			}
		});
		Akibat.daftar("iblis_rute", (p, a) -> {
			if (!a.isEmpty() && List.of("lurus", "penebusan", "darah").contains(a.get(0))) {
				IblisData.setRute(p, a.get(0));
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.rute." + a.get(0)).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
		Akibat.daftar("iblis_afu", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			if (kota == null) {
				return;
			}
			if (!a.isEmpty() && "abai".equals(a.get(0))) {
				CeritaApi.setBendera(p, "iblis_afu_abai", true);
				CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_afu_kabur");
				CeritaApi.peristiwa(p, AFU, "abai");
			} else {
				KandangTawanan.pastikanAfu(p.level(), kota);
			}
		});
		Akibat.daftar("iblis_minyak", (p, a) -> {
			if (!a.isEmpty() && "beli".equals(a.get(0))) {
				beliBagian(p);
			}
		});
		Akibat.daftar("iblis_beri", (p, a) -> {
			if (a.isEmpty()) {
				return;
			}
			ItemStack stack = switch (a.get(0)) {
				case "peta" -> new ItemStack(TemaItems.PETA_GERBANG_RAHASIA);
				case "topeng" -> new ItemStack(TemaItems.TOPENG_TANPA_WAJAH);
				case "kontrak_kepala" -> new ItemStack(TemaItems.KONTRAK_KEPALA);
				default -> ItemStack.EMPTY;
			};
			if (!stack.isEmpty()) {
				Barang.beri(p, stack);
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.dapat", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
			}
		});
		Akibat.daftar("iblis_serbu", (p, a) -> {
			CeritaApi.setBendera(p, "iblis_bab5_tunggu", true);
			if (PenyerbuanLurus.panggilBab5(p)) {
				CeritaApi.setBendera(p, "iblis_bab5_tunggu", false);
			}
		});
		Akibat.daftar("iblis_pihak", (p, a) -> PenyerbuanLurus.pihak(p, a.isEmpty() ? "kota" : a.get(0)));
		Akibat.daftar("iblis_pemburu", (p, a) -> PemburuLurus.jawab(p, a.isEmpty() ? "lawan" : a.get(0)));
		Akibat.daftar("iblis_turnamen", (p, a) -> PestaDarah.daftar(p));
		Akibat.daftar("iblis_xue_bos", (p, a) -> mulaiBos(p));
		Akibat.daftar("iblis_hadiah", (p, a) -> hadiah(p, a.isEmpty() ? "" : a.get(0)));
		Akibat.daftar("iblis_akhir", (p, a) -> akhir(p, a.isEmpty() ? "" : a.get(0)));
		Akibat.daftar("iblis_jual_kontrak", (p, a) -> BuronanIblis.jualKontrak(p));
		Akibat.daftar("iblis_lao", (p, a) -> lao(p, a.isEmpty() ? "pensiun" : a.get(0)));
		Akibat.daftar("iblis_anak", (p, a) -> anak(p, a.isEmpty() ? "bantu" : a.get(0)));
		Akibat.daftar("iblis_topeng_regu", (p, a) -> topengRegu(p));
		// the Darah route's loyalty test, accepted: Ye Wuhen is never seen in the city again
		Akibat.daftar("iblis_ye_pergi", (p, a) -> {
			TemaKota.Kota kota = kota(p);
			if (kota != null) {
				IblisData.kota(p.level().getServer(), kota.situsId()).putBoolean("ye_wuhen_pergi", true);
				IblisData.ubah(p.level().getServer());
				CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_ye_hilang");
			}
		});
		Akibat.daftar("iblis_akses_lantai", (p, a) -> {
			CeritaApi.setBendera(p, "iblis_akses_lantai", true);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.menara.akses").withStyle(ChatFormatting.DARK_PURPLE));
		});
		Akibat.daftar("iblis_tebus_lagi", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (npc != null) {
				dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.buka(p, dev.kultivator.dunia.tempat.tema.bersama.TemaLayar.Tawanan.LAYAR, npc.getId());
			}
		});
	}

	// ================================================================== chapter 3: the Shadow Oil

	/** All six barrels broken: Bai She's ring is cut (Infami -5, her grudge: market x1.2). */
	static void sabotase(ServerPlayer p, TemaKota.Kota kota) {
		MinecraftServer server = p.level().getServer();
		IblisData.pemainKota(server, kota.situsId(), p.getUUID()).putBoolean("bai_she_musuh", true);
		IblisData.kota(server, kota.situsId()).putBoolean("minyak_sabotase", true);
		IblisData.ubah(server);
		IblisMeter.tambahInfami(p, -5, "sabotase");
		CeritaApi.setBendera(p, "iblis_minyak_sabotase", true);
		CeritaApi.peristiwa(p, MINYAK, "sabotase");
		CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_minyak_sabotase", p.getName().getString());
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.minyak.sabotase").withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_tema.iblis.minyak.sabotase.sub").withStyle(ChatFormatting.GRAY), 10, 60, 20);
	}

	/** The buy choice: Infami +10, a share of 200 x (band + 1) NB paid over 5 days. */
	private static void beliBagian(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		CompoundTag mine = IblisData.pemain(server, p.getUUID());
		long total = (long) MINYAK_BAGI * (IblisKota.band(p) + 1L);
		mine.putInt("minyak_sisa", MINYAK_HARI);
		mine.putLong("minyak_nilai", Math.max(1L, total / MINYAK_HARI));
		mine.putLong("minyak_hari", IblisKota.hari(p.level()));
		IblisData.ubah(server);
		IblisMeter.tambahInfami(p, 10, "minyak");
		CeritaApi.setBendera(p, "iblis_minyak_beli", true);
		CeritaApi.peristiwa(p, MINYAK, "beli");
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.minyak.beli", NilaiBatu.text(total), MINYAK_HARI).withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** Every 600 ticks per online player: the day's oil share, the chapter-5 siege retry. */
	static void tickPemain(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		CompoundTag mine = IblisData.pemain(server, p.getUUID());
		int left = mine.getIntOr("minyak_sisa", 0);
		long hari = IblisKota.hari(p.level());
		if (left > 0 && hari > mine.getLongOr("minyak_hari", hari)) {
			long part = mine.getLongOr("minyak_nilai", 1L);
			NilaiBatu.give(p, part);
			mine.putInt("minyak_sisa", left - 1);
			mine.putLong("minyak_hari", hari);
			IblisData.ubah(server);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.minyak.bagian", NilaiBatu.text(part), left - 1).withStyle(ChatFormatting.DARK_PURPLE));
		}
		if (CeritaApi.bendera(p, "iblis_bab5_tunggu") && QuestTracker.aktif(p, QUEST) && QuestTracker.simpul(p, QUEST).startsWith("serbu")
				&& IblisKota.terdekat(p.level(), p.blockPosition(), 300) != null && PenyerbuanLurus.panggilBab5(p)) {
			CeritaApi.setBendera(p, "iblis_bab5_tunggu", false);
		}
	}

	// ================================================================== chapter 6: Xue Yao

	private static void mulaiBos(ServerPlayer p) {
		ServerLevel level = p.level();
		TemaKota.Kota kota = kota(p);
		if (kota == null) {
			return;
		}
		KultivatorNpc xue = TempatDialog.npc(p);
		if (xue == null || !TokohIblis.adalah(xue, TokohIblis.XUE_YAO)) {
			for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, p.getBoundingBox().inflate(32.0D), n -> TokohIblis.adalah(n, TokohIblis.XUE_YAO))) {
				xue = n;
				break;
			}
		}
		if (xue == null || BOS.containsKey(xue.getUUID())) {
			return;
		}
		int realm = Mth.clamp(Cultivators.get(p).realm().ordinal() + 2, 0, Realm.values().length - 1);
		int tier = Mth.clamp(4 + IblisKota.band(p) / 2, 4, 8);
		xue.setup(NpcRole.PENGUASA_IBLIS, tier, xue.look(), realm, 3, xue.dao(), xue.voice(), xue.robe(), xue.plate());
		TokohIblis.tandai(xue, TokohIblis.XUE_YAO);
		xue.memory().putBoolean("hold", false);
		xue.setDiamDiTitik(false);
		xue.arm(true);
		xue.setHealth(xue.getMaxHealth());
		xue.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 60 * 5, 1));
		TechniquePower.markRealmFoe(xue);
		xue.markHostile(p);
		BOS.put(xue.getUUID(), new Bos(p.getUUID(), kota.situsId()));
		BOS_TUBUH.put(xue.getUUID(), xue);
		Voice.say(xue, "tema.iblis.lord", null);
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.xue.judul").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("kultivator.kota_tema.iblis.xue.fase.1").withStyle(ChatFormatting.RED), 10, 60, 20);
	}

	private static boolean bolehLuka(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof KultivatorNpc npc)) {
			return true;
		}
		Bos bos = BOS.get(npc.getUUID());
		if (bos == null) {
			// the named characters are not struck down outside their scenes (a Sword Duel is fought to its own end)
			String tokoh = TokohIblis.dari(npc);
			if (!tokoh.isEmpty() && !TokohIblis.HAN_QINGSHUANG.equals(tokoh) && !TokohIblis.HONG_PAO.equals(tokoh) && DuelSessions.byEntity(npc) == null
					&& npc.getHealth() - amount <= npc.getMaxHealth() * 0.25F) {
				npc.setHealth(npc.getMaxHealth() * 0.25F);
				HidupApi.klip(npc, GerakId.MARAH_TUNJUK, 0, 40);
				return false;
			}
			return true;
		}
		float after = npc.getHealth() - amount;
		float max = npc.getMaxHealth();
		ServerPlayer p = victim.level().getServer() == null ? null : victim.level().getServer().getPlayerList().getPlayer(bos.player);
		if (p == null) {
			return true;
		}
		if (bos.fase == 1 && after <= max * 0.66F) {
			bos.fase = 2;
			panggilGladiator(p, npc, bos);
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.xue.fase.2").withStyle(ChatFormatting.RED));
		} else if (bos.fase == 2 && after <= max * 0.33F) {
			bos.fase = 3;
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.xue.fase.3").withStyle(ChatFormatting.DARK_RED));
		}
		if (after <= max * 0.10F) {
			npc.setHealth(max * 0.10F);
			kalahBos(p, npc, bos);
			return false;
		}
		return true;
	}

	private static void panggilGladiator(ServerPlayer p, KultivatorNpc xue, Bos bos) {
		ServerLevel level = p.level();
		for (int i = 0; i < 2; i++) {
			BlockPos at = xue.blockPosition().offset(i == 0 ? 3 : -3, 0, 2);
			KultivatorNpc g = HidupApi.tubuh(level, bos.situs, NpcRole.GLADIATOR_IBLIS, at, xue.getYRot(), xue.look() * 31 + i * 7919);
			if (g == null) {
				continue;
			}
			int realm = Math.max(0, xue.realm() - 1);
			g.setup(NpcRole.GLADIATOR_IBLIS, Math.max(2, 2 + IblisKota.band(p) / 2), g.look(), realm, 2, g.dao(), g.voice(), g.robe(),
					ArenaDarah.namaGladiator(g.look()));
			g.memory().putBoolean("hold", false);
			g.arm(true);
			TechniquePower.markRealmFoe(g);
			g.markHostile(p);
			bos.panggilan.add(g);
		}
		HidupApi.klip(xue, GerakId.TUNJUK, 0, 30);
		level.playSound(null, xue.getX(), xue.getY(), xue.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
	}

	/** Every 20 ticks: the blood pool of phase 3, a fight whose player left or died is reset. */
	static void tickBos(MinecraftServer server) {
		if (BOS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Bos> e : new HashMap<>(BOS).entrySet()) {
			KultivatorNpc xue = BOS_TUBUH.get(e.getKey());
			Bos bos = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(bos.player);
			if (xue == null || xue.isRemoved() || !xue.isAlive()) {
				BOS.remove(e.getKey());
				BOS_TUBUH.remove(e.getKey());
				continue;
			}
			if (p == null || !p.isAlive() || p.level() != xue.level() || p.distanceToSqr(xue) > 48.0D * 48.0D) {
				reset(xue, bos, p);
				continue;
			}
			if (bos.fase == 3 && xue.level() instanceof ServerLevel level) {
				long now = level.getGameTime();
				if (now - bos.denyut >= 60L) {
					bos.denyut = now;
					Vec3 c = xue.position();
					for (int i = 0; i < 36; i++) {
						double a = i * Math.PI * 2.0D / 36.0D;
						level.sendParticles(new DustParticleOptions(0x9C0A14, 1.6F), c.x + Math.cos(a) * 6.0D, c.y + 0.2D, c.z + Math.sin(a) * 6.0D, 1, 0.0D, 0.05D, 0.0D, 0.0D);
					}
					level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4F, 0.5F);
					if (p.distanceToSqr(xue) < 36.0D) {
						p.hurtServer(level, level.damageSources().magic(), 3.0F);
					}
				}
			}
		}
	}

	private static void reset(KultivatorNpc xue, Bos bos, @Nullable ServerPlayer p) {
		BOS.remove(xue.getUUID());
		BOS_TUBUH.remove(xue.getUUID());
		for (KultivatorNpc g : bos.panggilan) {
			g.vanish();
		}
		if (p != null) {
			xue.forgive(p.getUUID());
		}
		xue.setHealth(xue.getMaxHealth());
		xue.memory().putBoolean("hold", true);
		xue.setDiamDiTitik(true);
		xue.setTarget(null);
		HidupApi.klip(xue, GerakId.SIKAP_ANGKUH, 0, 0);
	}

	private static void kalahBos(ServerPlayer p, KultivatorNpc xue, Bos bos) {
		BOS.remove(xue.getUUID());
		BOS_TUBUH.remove(xue.getUUID());
		for (KultivatorNpc g : bos.panggilan) {
			g.vanish();
		}
		MinecraftServer server = p.level().getServer();
		IblisData.kota(server, bos.situs).putBoolean("xue_yao_kalah", true);
		IblisData.ubah(server);
		xue.forgive(p.getUUID());
		xue.setTarget(null);
		HidupApi.klip(xue, GerakId.BERLUTUT, 0, 80);
		Voice.say(xue, "tema.iblis.lord", null);
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.xue.kalah").withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_tema.iblis.xue.kalah.sub").withStyle(ChatFormatting.YELLOW), 10, 80, 20);
		Advancements.grant(p, "tempat/tema_iblis_xue_yao");
		CeritaApi.peristiwa(p, XUE, "kalah");
		CeritaApi.kabar(p.level(), bos.situs, "tema_iblis_xue_kalah", p.getName().getString());
		xue.vanish();
	}

	// ================================================================== rewards and endings

	private static void hadiah(ServerPlayer p, String what) {
		switch (what) {
			case "kipas" -> {
				ItemStack fan = SenjataKota.stack(SenjataKota.Bentuk.KIPAS_BESI, SenjataKota.Mutu.ROH_TINGGI);
				fan.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.iblis.kipas").withStyle(ChatFormatting.DARK_RED));
				fan.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.iblis.kipas.lore").withStyle(ChatFormatting.GRAY))));
				Barang.beri(p, fan);
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.dapat", fan.getHoverName()).withStyle(ChatFormatting.GOLD));
			}
			case "kitab" -> {
				TemaKota.Kota kota = kota(p);
				if (kota == null || !MenaraTerlarang.beriKitab(p, kota.situsId())) {
					// the town's one copy is gone: Xue Yao pays its worth instead
					NilaiBatu.give(p, MenaraTerlarang.HARGA_KITAB);
					Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.kitab.ganti", NilaiBatu.text(MenaraTerlarang.HARGA_KITAB)).withStyle(ChatFormatting.GOLD));
				}
			}
			default -> {
			}
		}
	}

	/** Chapter 7: the city's new order by the player's route (data of 38.T.7.11). */
	private static void akhir(ServerPlayer p, String rute) {
		TemaKota.Kota kota = kota(p);
		if (kota == null) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		String lurus = SekteApi.diplomasi().nearest(kota.pusat(), 0);
		switch (rute) {
			case "lurus" -> {
				town.putString("penguasa", IblisData.PENGUASA_TU);
				town.putBoolean("kandang_tutup", true);
				town.putBoolean("pasar_naik", true);
				town.putBoolean("markas_lurus", true);
				IblisData.daftar(town, "tawanan").clear();
				IblisData.beriGelar(p, IblisData.GELAR_PEMUTUS);
				Advancements.grant(p, "tempat/tema_pemutus_rantai");
				IblisMeter.tambahInfami(p, -30, "rantai_lurus");
				if (lurus != null) {
					SekteApi.diplomasi().adjust(p.getUUID(), lurus, 10, "38tk.rantai");
				}
			}
			case "penebusan" -> {
				town.putString("penguasa", IblisData.PENGUASA_YE);
				town.putBoolean("rumah_tebusan", true);
				IblisData.beriGelar(p, IblisData.GELAR_FAJAR);
				Advancements.grant(p, "tempat/tema_pembawa_fajar");
				IblisMeter.tambahInfami(p, -20, "rantai_tebus");
				Voice.say(p, "tema.iblis.dawn", null);
			}
			default -> {
				IblisData.beriGelar(p, IblisData.GELAR_TAMU);
				town.putBoolean("segel_terbuka", true);
				Advancements.grant(p, "tempat/tema_iblis_tamu_darah");
				IblisMeter.tambahInfami(p, 25, "rantai_darah");
			}
		}
		IblisData.ubah(server);
		CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_akhir_" + (rute.isEmpty() ? "darah" : rute), p.getName().getString());
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.akhir." + (rute.isEmpty() ? "darah" : rute)).withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_tema.iblis.akhir.sub").withStyle(ChatFormatting.YELLOW), 15, 100, 25);
	}

	// ================================================================== side quests

	/** Lao Kuang's end: {@code pensiun} (his freedom bought from Tu Gang, 120 NB) or {@code terhormat} (after the duel). */
	private static void lao(ServerPlayer p, String how) {
		TemaKota.Kota kota = kota(p);
		if (kota == null) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		if ("pensiun".equals(how)) {
			if (!NilaiBatu.pay(p, PENSIUN_LAO)) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(PENSIUN_LAO)).withStyle(ChatFormatting.RED));
				return;
			}
			IblisData.tambahKas(server, kota.situsId(), PENSIUN_LAO);
			CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_lao_pensiun");
		} else {
			IblisMeter.tambahGentar(p, kota.situsId(), 5, "lao");
			CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_lao_terhormat", p.getName().getString());
		}
		IblisData.kota(server, kota.situsId()).putBoolean("lao_kuang_pergi", true);
		IblisData.ubah(server);
		CeritaApi.setBendera(p, "iblis_lao_" + how, true);
		CeritaApi.peristiwa(p, LAO, how);
		KultivatorNpc npc = TempatDialog.npc(p);
		if (npc != null && TokohIblis.adalah(npc, TokohIblis.LAO_KUANG)) {
			HidupApi.klip(npc, GerakId.HORMAT_DALAM, 0, 60);
		}
	}

	/** The slum children: {@code bantu} (bread money, Infami -2) or {@code lapor} (to the enforcers: Gentar +3, Infami +2). */
	private static void anak(ServerPlayer p, String how) {
		TemaKota.Kota kota = kota(p);
		if (kota == null) {
			return;
		}
		if ("bantu".equals(how)) {
			if (!NilaiBatu.pay(p, ROTI_ANAK)) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(ROTI_ANAK)).withStyle(ChatFormatting.RED));
				return;
			}
			IblisMeter.tambahInfami(p, -2, "anak");
			CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_anak_bantu");
		} else {
			IblisMeter.tambahGentar(p, kota.situsId(), 3, "anak");
			IblisMeter.tambahInfami(p, 2, "anak");
			CeritaApi.kabar(p.level(), kota.situsId(), "tema_iblis_anak_lapor");
		}
		CeritaApi.setBendera(p, "iblis_anak_" + how, true);
		CeritaApi.peristiwa(p, ANAK, how);
	}

	/** Side quest Topeng: the righteous squad that took Bai She's mask camps 40 blocks out of the gate. */
	private static void topengRegu(ServerPlayer p) {
		ServerLevel level = p.level();
		TemaKota.Kota kota = kota(p);
		if (kota == null || TOPENG_REGU.containsKey(p.getUUID())) {
			return;
		}
		Vec3 out = IblisKota.lokal(kota, Gerbang.DISTRIK, Gerbang.GX, IblisKota.LY + 1, Gerbang.GZ + 40.0D);
		if (out == null) {
			return;
		}
		CeritaApi.setBendera(p, "iblis_topeng_cari", true);
		CompoundTag mine = IblisData.pemain(level.getServer(), p.getUUID());
		mine.putInt("topeng_x", (int) out.x);
		mine.putInt("topeng_z", (int) out.z);
		IblisData.ubah(level.getServer());
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.topeng.arah", (int) out.x, (int) out.z).withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** Every 40 ticks per player on the mask quest: the squad appears near its mark; when it falls the mask is back. */
	static void tickTopeng(ServerPlayer p) {
		if (!CeritaApi.bendera(p, "iblis_topeng_cari")) {
			return;
		}
		ServerLevel level = p.level();
		CompoundTag mine = IblisData.pemain(level.getServer(), p.getUUID());
		Vec3 mark = new Vec3(mine.getIntOr("topeng_x", 0) + 0.5D, p.getY(), mine.getIntOr("topeng_z", 0) + 0.5D);
		List<LivingEntity> squad = TOPENG_REGU.get(p.getUUID());
		if (squad != null) {
			squad.removeIf(e -> e.isRemoved());
			if (squad.isEmpty() || squad.stream().allMatch(e -> !e.isAlive())) {
				TOPENG_REGU.remove(p.getUUID());
				CeritaApi.setBendera(p, "iblis_topeng_cari", false);
				Barang.beri(p, new ItemStack(TemaItems.TOPENG_TANPA_WAJAH));
				CeritaApi.peristiwa(p, TOPENG, "rebut");
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.topeng.rebut").withStyle(ChatFormatting.GOLD));
			} else if (p.position().distanceToSqr(mark) > 128.0D * 128.0D) {
				for (LivingEntity e : squad) {
					if (e instanceof KultivatorNpc n) {
						n.vanish();
					}
				}
				TOPENG_REGU.remove(p.getUUID());
			}
			return;
		}
		if (p.position().distanceToSqr(mark) < 40.0D * 40.0D) {
			List<LivingEntity> made = PemburuLurus.reguKontrak(level, mark, p, Mth.clamp(1 + IblisKota.band(p) / 2, 1, 4), 3, "kultivator.iblis_topeng");
			if (!made.isEmpty()) {
				TOPENG_REGU.put(p.getUUID(), made);
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.topeng.ketemu").withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (entity instanceof KultivatorNpc npc && BOS.containsKey(npc.getUUID())) {
			BOS.remove(npc.getUUID());
			BOS_TUBUH.remove(npc.getUUID());
		}
	}
}
