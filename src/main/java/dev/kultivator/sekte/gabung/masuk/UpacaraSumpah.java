package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohAnchors;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The oath ceremony of DESIGN 41.5.6 at {@code kompleks/altar_sumpah} (an Iblis compound's blood altar; without a compound
 * {@code sektenpc/altar}, else the gate's master spot), about a minute, busy state {@code gabung.sumpah}, a short camera
 * shot ({@code AdeganPayload} "gabung_sumpah": 7b rules, skippable, card only when unsafe).
 * <p>
 * Order: the candidate kneels on the dais ({@code sumpah_berlutut} 300) and raises three incense sticks
 * ({@code dupa_tiga} 301, prop 150); the master (else the admissions elder) unrolls the rules ({@code tetua_menunjuk_aturan}
 * 311, prop 155) and reads the three oaths ({@code gabung.sumpah_aturan_1..3}); the candidate answers with the chat
 * buttons [Aku bersumpah] / [Aku belum siap] ({@code /kultivasi gabung sumpah ya|tidak}); three prostrations
 * ({@code kowtow} 302); the badge is handed over ({@code terima_lencana} 303, prop 151). Up to six sect disciples stand
 * in line and salute ({@code salam_sekte} 304).
 * <p>
 * Result: {@code GabungApi.terima} (ANGGOTA, standing +10, advancements, the badge sync that dresses the robe of 41V), the
 * <b>Lencana Anggota</b> ({@code kultivator:anggota} = sect, owner, rank), the verdict's PK bonus, the provisions per class
 * (Kecil 4 Healing Pills; Menengah +2 Qi Gathering; Besar +1 Foundation Pill; Agung +1 HUANG jade slip of the style's Dao;
 * Abadi +1 XUAN slip instead), advancement {@code sekte/gabung_sumpah}; then the elders choose (41.5.7, {@link PilihShifu}).
 * "Aku belum siap" cancels without penalty: the player stays CALON until the candidacy lapses. Leaving the dais, dying or
 * logging out also cancels it.
 */
public final class UpacaraSumpah {

	/**
	 * Ticks of the parts (the reading takes three lines of 3 s). Clip lengths on the client: kneel 60, incense 60 (it holds
	 * its kneeling pose, so it is not interrupted by a second kneel), prostrations 60, badge 50 (hold). The incense starts at
	 * 40 (the kneel has settled) and the first line follows when the clip has ended (40 + 60 = 100).
	 */
	static final int T_DUPA = 40;
	static final int T_ATURAN_1 = 100;
	static final int T_ATURAN_2 = 160;
	static final int T_ATURAN_3 = 220;
	static final int T_TANYA = 280;
	/** An unanswered question counts as "not ready" after this long. */
	static final int TANYA_MAKS = 600;
	static final int T_KOWTOW = 60;
	static final int T_LENCANA = 60;
	static final int T_AKHIR = 60;
	/** Leaving the dais by more than this ends the ceremony. */
	static final double JARI_ALAS = 8.0D;
	/** Disciples in line (41.5.6). */
	static final int BARISAN_MAKS = 6;

	private static final class Upacara {
		final UUID pemain;
		final String sekte;
		final Pangkat pangkat;
		final Pintu pintu;
		final int bonus;
		final boolean istimewa;
		final BlockPos altar;
		long mulai;
		/** 0 reading, 1 waiting for the answer, 2 sworn (kowtow/badge), 3 done. */
		int fase;
		long faseMulai;
		@Nullable UUID pemimpin;
		final List<UUID> tubuh = new ArrayList<>();

		Upacara(UUID pemain, String sekte, Pangkat pangkat, Pintu pintu, int bonus, boolean istimewa, BlockPos altar, long mulai) {
			this.pemain = pemain;
			this.sekte = sekte;
			this.pangkat = pangkat;
			this.pintu = pintu;
			this.bonus = bonus;
			this.istimewa = istimewa;
			this.altar = altar;
			this.mulai = mulai;
		}
	}

	private static final Map<UUID, Upacara> UPACARA = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(UpacaraSumpah::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			Upacara u = UPACARA.get(player.getUUID());
			if (u != null) {
				batal(player, u, true);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> UPACARA.clear());
	}

	/** True while {@code player} stands in an oath ceremony. */
	public static boolean sedang(ServerPlayer player) {
		return UPACARA.containsKey(player.getUUID());
	}

	/**
	 * Starts the oath for a player who passed the verdict (the passed note of {@link Putusan}). Null when it started, else
	 * why not (no passed verdict, busy, the altar not loaded or too far).
	 */
	public static @Nullable Component mulai(ServerPlayer player) {
		if (!Gabung.aktif()) {
			return Component.translatable("kultivator.gabung.cmd.mati");
		}
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag bag = k.bagian(Putusan.B);
		String sekte = bag.getStringOr(Putusan.L_SEKTE, "");
		if (sekte.isEmpty() || !Putusan.lulus(k, sekte)) {
			return Masuk.t("sumpah.belum_lulus");
		}
		Pangkat p = Pangkat.byOrdinal(bag.getIntOr(Putusan.L_PANGKAT, Pangkat.TERCATAT.ordinal()));
		Pintu pintu = Pintu.byId(bag.getStringOr(Putusan.L_PINTU, Pintu.GERBANG.id()), Pintu.GERBANG);
		return mulai(player, sekte, p, pintu, bag.getIntOr(Putusan.L_BONUS, 0), bag.getBooleanOr(Putusan.L_ISTIMEWA, false), false);
	}

	/** Starts the oath directly (test command {@code uji gabung sumpah}, or the verdict); {@code uji} ignores distance. */
	public static @Nullable Component mulai(ServerPlayer player, String sekte, Pangkat pangkat, Pintu pintu, int bonus, boolean istimewa,
			boolean uji) {
		if (UPACARA.containsKey(player.getUUID())) {
			return Masuk.t("sumpah.sudah_jalan");
		}
		String sibuk = Sibuk.whyExcept(player, Gabung.SIBUK_SUMPAH);
		if (sibuk != null) {
			return Sibuk.text(sibuk);
		}
		ServerLevel level = player.level();
		BlockPos altar = altar(sekte);
		if (altar == null || altar.getY() <= 0 || !level.isLoaded(altar)) {
			if (!uji) {
				return Masuk.t("sumpah.altar_jauh", Masuk.nama(sekte));
			}
			altar = player.blockPosition();
		}
		if (!uji && Masuk.datar(player.position(), altar) > HariUjian.JARI_WILAYAH) {
			return Masuk.t("sumpah.altar_jauh", Masuk.nama(sekte));
		}
		Vec3 dais = Vec3.atBottomCenterOf(altar);
		player.teleportTo(dais.x, dais.y, dais.z);
		Upacara u = new Upacara(player.getUUID(), sekte, pangkat, pintu, bonus, istimewa, altar, level.getGameTime());
		u.faseMulai = u.mulai;
		UPACARA.put(player.getUUID(), u);
		GabungApi.sibuk(player, Gabung.SIBUK_SUMPAH, T_TANYA + TANYA_MAKS + 400);
		KultivatorNpc pemimpin = pemimpin(level, player, sekte, altar);
		if (pemimpin != null) {
			u.pemimpin = pemimpin.getUUID();
			HidupApi.tatap(pemimpin, player);
		}
		barisan(level, u);
		GabungPayloads.kirimKlip(player, GabungGerakId.SUMPAH_BERLUTUT, 0);
		AdeganPayload.kirim(player, "gabung_sumpah", pemimpin, dais, 0);
		Msg.chat(player, Masuk.t("sumpah.mulai", Masuk.nama(sekte)).withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** The candidate's answer ({@code /kultivasi gabung sumpah ya|tidak}); false when no ceremony waits for it. */
	public static boolean jawab(ServerPlayer player, boolean ya) {
		Upacara u = UPACARA.get(player.getUUID());
		if (u == null || u.fase != 1) {
			return false;
		}
		if (!ya) {
			Msg.chat(player, Masuk.t("sumpah.belum_siap").withStyle(ChatFormatting.YELLOW));
			batal(player, u, false);
			return true;
		}
		u.fase = 2;
		u.faseMulai = player.level().getGameTime();
		GabungPayloads.kirimKlip(player, GabungGerakId.KOWTOW, 0);
		Msg.chat(player, Masuk.t("sumpah.bersumpah").withStyle(ChatFormatting.GOLD));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (UPACARA.isEmpty()) {
			return;
		}
		for (Upacara u : List.copyOf(UPACARA.values())) {
			ServerPlayer p = server.getPlayerList().getPlayer(u.pemain);
			if (p == null) {
				UPACARA.remove(u.pemain);
				continue;
			}
			try {
				tickUpacara(p, u);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Gabung: oath tick failed", exception);
				batal(p, u, true);
			}
		}
	}

	private static void tickUpacara(ServerPlayer p, Upacara u) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		if (!p.isAlive() || Masuk.datar(p.position(), u.altar) > JARI_ALAS) {
			Msg.chat(p, Masuk.t("sumpah.terputus").withStyle(ChatFormatting.RED));
			batal(p, u, false);
			return;
		}
		KultivatorNpc pemimpin = pemimpin(level, u);
		long t = now - u.faseMulai;
		switch (u.fase) {
			case 0 -> {
				if (t == T_DUPA) {
					GabungPayloads.kirimKlip(p, GabungGerakId.DUPA_TIGA, GabungGerakId.PROP_DUPA_SUMPAH);
					Msg.actionBar(p, Masuk.t("sumpah.dupa").withStyle(ChatFormatting.GOLD));
				} else if (t == T_ATURAN_1 || t == T_ATURAN_2 || t == T_ATURAN_3) {
					int n = t == T_ATURAN_1 ? 1 : t == T_ATURAN_2 ? 2 : 3;
					if (pemimpin != null) {
						HidupApi.klip(pemimpin, GabungGerakId.TETUA_MENUNJUK_ATURAN, GabungGerakId.PROP_GULUNGAN_ATURAN, 60);
						Masuk.ucap(pemimpin, "gabung.sumpah_aturan_" + n, "elder.welcome", Masuk.t("ucap.sumpah_aturan_" + n));
					}
					Msg.chat(p, Masuk.t("sumpah.aturan", n, Masuk.t("ucap.sumpah_aturan_" + n)).withStyle(ChatFormatting.YELLOW));
				} else if (t >= T_TANYA) {
					u.fase = 1;
					u.faseMulai = now;
					Msg.chat(p, Masuk.t("sumpah.tanya").withStyle(ChatFormatting.GOLD));
					Msg.chat(p, Putusan.tombol(Masuk.t("sumpah.ya"), "/kultivasi gabung sumpah ya", Masuk.t("sumpah.ya_hover"))
							.append(Component.literal("   "))
							.append(Putusan.tombol(Masuk.t("sumpah.tidak"), "/kultivasi gabung sumpah tidak", Masuk.t("sumpah.tidak_hover"))));
				}
			}
			case 1 -> {
				if (t >= TANYA_MAKS) {
					Msg.chat(p, Masuk.t("sumpah.diam").withStyle(ChatFormatting.YELLOW));
					batal(p, u, false);
				}
			}
			case 2 -> {
				if (t == T_KOWTOW) {
					GabungPayloads.kirimKlip(p, GabungGerakId.TERIMA_LENCANA, GabungGerakId.PROP_LENCANA_ANGGOTA);
					if (pemimpin != null) {
						HidupApi.klip(pemimpin, GabungGerakId.SALAM_SEKTE, 0, 40);
					}
				} else if (t >= T_KOWTOW + T_LENCANA) {
					u.fase = 3;
					u.faseMulai = now;
					selesai(p, u, pemimpin);
				}
			}
			default -> {
				if (t >= T_AKHIR) {
					UPACARA.remove(u.pemain);
					GabungPayloads.kirimKlip(p, 0, 0);
					pulangkan(level, u);
					GabungApi.lepasSibuk(p, Gabung.SIBUK_SUMPAH);
					if (u.pintu != Pintu.TAMU && !PilihShifu.mulai(p)) {
						Msg.chat(p, Masuk.t("shifu.tunda").withStyle(ChatFormatting.GRAY));
					}
				}
			}
		}
	}

	/** The membership is made: data door, badge, provisions, bonus, advancement, the line cheers. */
	private static void selesai(ServerPlayer p, Upacara u, @Nullable KultivatorNpc pemimpin) {
		GabungApi.terima(p, u.sekte, u.pangkat, u.pintu);
		Keanggotaan k = GabungApi.anggota(p);
		Putusan.lupakan(k);
		TokenNaik.hapusTandaCalon(p);
		GabungData.get(p.level().getServer()).setDirty();
		Masuk.beri(p, lencana(p, u.sekte, u.pangkat));
		for (ItemStack bekal : bekal(p.level().getServer(), u.sekte)) {
			Masuk.beri(p, bekal);
		}
		if (u.bonus > 0) {
			GabungApi.tambahPk(p, u.bonus, u.istimewa ? "gabung.istimewa" : "gabung.putusan");
		}
		Advancements.grant(p, "sekte/gabung_sumpah");
		p.level().playSound(null, u.altar, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 0.9F);
		if (pemimpin != null) {
			Masuk.ucap(pemimpin, "gabung.diterima", "elder.welcome", Masuk.t("ucap.diterima"));
		}
		for (UUID id : u.tubuh) {
			Entity e = p.level().getEntity(id);
			if (e instanceof KultivatorNpc npc) {
				HidupApi.klip(npc, GabungGerakId.SALAM_SEKTE, 0, 60);
			}
		}
		Msg.chat(p, Masuk.t("sumpah.selesai", Masuk.nama(u.sekte), u.pangkat.nama()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
	}

	private static void batal(ServerPlayer p, Upacara u, boolean diam) {
		UPACARA.remove(u.pemain);
		GabungPayloads.kirimKlip(p, 0, 0);
		pulangkan(p.level(), u);
		GabungApi.lepasSibuk(p, Gabung.SIBUK_SUMPAH);
		if (!diam) {
			Msg.chat(p, Masuk.t("sumpah.batal").withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== items

	/** A Lencana Anggota of {@code sekte} at {@code pangkat}, bound to {@code player}. */
	public static ItemStack lencana(ServerPlayer player, String sekte, Pangkat pangkat) {
		ItemStack stack = new ItemStack(GabungItems.LENCANA_ANGGOTA);
		stack.set(GabungItems.ANGGOTA, new GabungItems.Anggota(sekte, player.getUUID(), pangkat.ordinal(), Masuk.nama(sekte),
				SekteApi.diplomasi().color(sekte)));
		return stack;
	}

	/** The provisions of 41.5.6 by class (cumulative). */
	static List<ItemStack> bekal(MinecraftServer server, String sekte) {
		List<ItemStack> out = new ArrayList<>();
		int kelas = GabungApi.kelas(sekte);
		out.add(pil(AlchemyItems.HEALING_PILL, 4));
		if (kelas >= KelasSekte.MENENGAH.nomor()) {
			out.add(pil(AlchemyItems.QI_GATHERING_PILL, 2));
		}
		if (kelas >= KelasSekte.BESAR.nomor()) {
			out.add(pil(AlchemyItems.FOUNDATION_PILL, 1));
		}
		if (kelas >= KelasSekte.AGUNG.nomor()) {
			String spec = teknikCorak(server, sekte, kelas >= KelasSekte.ABADI.nomor() ? TechniqueGrade.XUAN : TechniqueGrade.HUANG);
			if (spec != null) {
				out.add(JadeSlips.stack(spec));
			}
		}
		return out;
	}

	private static ItemStack pil(Item item, int n) {
		ItemStack stack = AlchemyComponents.withQuality(new ItemStack(item), PillQuality.MID);
		stack.setCount(n);
		return stack;
	}

	/** A real technique of the style's Dao and {@code grade}, chosen by the sect's seed (null when none exists). */
	static @Nullable String teknikCorak(MinecraftServer server, String sekte, TechniqueGrade grade) {
		Corak corak = GabungApi.corak(sekte);
		List<TechniqueSpec> fit = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.ofGrade(grade)) {
			if (spec.dao() != null && corak.punyaDao(spec.dao())) {
				fit.add(spec);
			}
		}
		if (fit.isEmpty()) {
			return null;
		}
		long seed = server.overworld().getSeed() ^ sekte.hashCode() * 0x9E3779B97F4A7C15L;
		return fit.get((int) Math.floorMod(seed, (long) fit.size())).id();
	}

	// ================================================================== places and bodies

	/** The oath altar: compound altar, else the gate altar, else the gate's master spot. */
	static @Nullable BlockPos altar(String sekte) {
		BlockPos pos = TitikGabung.kompleks(sekte, TitikGabung.ALTAR_SUMPAH);
		if (pos == null) {
			pos = GabungApi.titik(sekte, TitikGabung.GERBANG_ALTAR);
		}
		if (pos == null) {
			pos = GabungApi.titik(sekte, TitikGabung.GERBANG_KETUA);
		}
		return pos;
	}

	private static @Nullable KultivatorNpc pemimpin(ServerLevel level, ServerPlayer player, String sekte, BlockPos altar) {
		MinecraftServer server = level.getServer();
		TokohSekte.Tokoh ketua = TokohSekte.ketua(server, sekte);
		TokohSekte.Tokoh tetua = ketua != null ? ketua : TokohSekte.penerimaan(server, sekte);
		if (tetua != null) {
			KultivatorNpc body = TokohAnchors.tubuh(level, sekte, tetua.id());
			if (body != null && body.distanceTo(player) < 48.0F) {
				return body;
			}
		}
		BlockPos at = altar.relative(player.getDirection(), 3);
		int look = tetua != null ? TokohSekte.look(server, tetua.id()) : sekte.hashCode() * 31 + 5;
		KultivatorNpc npc = HidupApi.tubuhStaf(level, Masuk.situs(level, altar), NpcRole.TETUA_SEKTE, at, player.getYRot() + 180.0F, look);
		if (npc != null) {
			npc.memory().putBoolean(Masuk.MEM_CALON, true);
			npc.memory().putBoolean("hold", true);
			if (tetua != null) {
				Component n = TokohSekte.nama(server, tetua.id());
				if (n != null) {
					npc.setPlate(n);
				}
			}
			Masuk.rupa(npc, ketua != null ? TokohAnchors.RUPA_KETUA : TokohAnchors.RUPA_TETUA, sekte);
		}
		return npc;
	}

	private static @Nullable KultivatorNpc pemimpin(ServerLevel level, Upacara u) {
		if (u.pemimpin == null) {
			return null;
		}
		Entity e = level.getEntity(u.pemimpin);
		return e instanceof KultivatorNpc npc && npc.isAlive() ? npc : null;
	}

	/** Up to six disciples in two lines beside the dais. */
	private static void barisan(ServerLevel level, Upacara u) {
		long situs = Masuk.situs(level, u.altar);
		for (int i = 0; i < BARISAN_MAKS; i++) {
			int side = i % 2 == 0 ? 1 : -1;
			BlockPos at = u.altar.offset(side * 3, 0, -2 + i / 2 * 2);
			KultivatorNpc npc = HidupApi.tubuh(level, situs, i < 4 ? NpcRole.MURID_SEKTE_LUAR : NpcRole.MURID_SEKTE_DALAM, at,
					side > 0 ? 90.0F : -90.0F);
			if (npc == null) {
				break;
			}
			npc.memory().putBoolean(Masuk.MEM_CALON, true);
			npc.memory().putBoolean("hold", true);
			Masuk.rupa(npc, i < 4 ? "murid.luar" : "murid.dalam", u.sekte);
			HidupApi.klip(npc, GabungGerakId.SALAM_SEKTE, 0, 40);
			u.tubuh.add(npc.getUUID());
		}
		if (u.pemimpin != null) {
			Entity e = level.getEntity(u.pemimpin);
			if (e instanceof KultivatorNpc npc && npc.memory().getBooleanOr(Masuk.MEM_CALON, false)) {
				u.tubuh.add(u.pemimpin);
			}
		}
	}

	private static void pulangkan(ServerLevel level, Upacara u) {
		for (UUID id : u.tubuh) {
			Entity e = level.getEntity(id);
			if (e instanceof KultivatorNpc npc && npc.memory().getBooleanOr(Masuk.MEM_CALON, false)) {
				npc.discard();
			}
		}
		u.tubuh.clear();
	}

	private UpacaraSumpah() {
	}
}
