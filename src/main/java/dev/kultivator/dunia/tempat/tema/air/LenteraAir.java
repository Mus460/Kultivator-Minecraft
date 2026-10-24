package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.dunia.tempat.tema.bersama.TemaCerita;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Malam Lentera Air (DESIGN 38.T.5.4) and the Arwah Kanal: calendar event {@code air/lentera_air} on new-moon nights
 * 13000-18000 (inti's runner sends {@code TemaNet.FESTIVAL} to the town and the residents release lanterns from the
 * {@code lentera_air} points, 38NL). The Panggung Air gets a singer and two musicians while it runs.
 * <ul>
 * <li>A player buys up to {@link #BELI_MAKS} Lentera Teratai per festival at {@link #HARGA} NB from the singer's stall
 * (dialog effect {@code air_lentera_beli}). The wish is the lantern's own name (rename it at an anvil, at most
 * {@link #HARAPAN_MAKS} letters); it is sent only to its owner ({@code FESTIVAL} data {@code lentera} with
 * {@code teks}); everybody else sees the lantern without text. Released within {@link #JARAK_TITIK} blocks of a
 * {@code lentera_air} point (or the lake shore): the first one of a festival gives Nama Baik +1, a small rumour, quest
 * events {@code lentera_dilepas} (inti) and {@code air_lentera}. Player lanterns live 600 ticks on the client
 * ({@code EpicBudget}).</li>
 * <li>Arwah Kanal (only while the festival runs or a player is at stage {@code arwah} of {@code tema_air_benang}, and
 * dangers are on): 2-4 weak lost souls (SISA_JIWA bodies, realm foes) rise from the lake near players; a lantern
 * released within {@link #JARAK_ARWAH} blocks calms one (quest event {@code air_arwah}).</li>
 * <li>Chain stage {@code lepas}: the Kain Xiu Niang released as a lantern boat on the lake (use the cloth at a jetty
 * during the festival): Xiu Niang fades into light, the residents around bow ({@code sujud_dupa}); event
 * {@code air_kain_lepas}.</li>
 * </ul>
 */
public final class LenteraAir {

	public static final String ACARA = "lentera_air";
	public static final int DARI = 13000;
	public static final int SAMPAI = 18000;
	public static final long HARGA = 1L;
	public static final int BELI_MAKS = 3;
	public static final int HARAPAN_MAKS = 32;
	public static final int UMUR_LENTERA = 600;
	public static final double JARAK_TITIK = 4.5D;
	public static final double JARAK_ARWAH = 6.0D;
	public static final int ARWAH_MAKS = 4;
	public static final String QUEST = "tema_air_benang";

	/** Per running festival: who already got the Nama Baik, lanterns bought. */
	private static final Map<Long, Set<UUID>> DIBERI = new HashMap<>();
	private static final Map<Long, Map<UUID, Integer>> DIBELI = new HashMap<>();
	private static final Map<Long, List<KultivatorNpc>> PANGGUNG = new HashMap<>();
	private static final Map<Long, List<KultivatorNpc>> ARWAH = new HashMap<>();

	static void init() {
		TemaKalender.daftar(AirKota.TEMA, ACARA, DARI, SAMPAI, TemaKalender.bulan(MoonPhase.NEW_MOON), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				LenteraAir.mulai(level, situsId);
			}

			@Override
			public void tick(ServerLevel level, long situsId, int tickKe) {
				LenteraAir.tick(level, situsId);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				LenteraAir.akhir(level, situsId);
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(TemaItems.LENTERA_TERATAI)) {
				return lepas(sp, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (stack.is(TemaItems.KAIN_XIU_NIANG)) {
				return lepasKain(sp, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
	}

	static void reset() {
		DIBERI.clear();
		DIBELI.clear();
		PANGGUNG.clear();
		ARWAH.clear();
	}

	/** True while the lantern night runs in the town. */
	public static boolean berjalan(long situs) {
		return TemaKalender.berjalan(situs, AirKota.TEMA, ACARA);
	}

	private static boolean boleh() {
		return AirKota.aktif() && AirKota.config().lenteraAir && TemaConfig.inti().acara;
	}

	// ================================================================== the event

	private static void mulai(ServerLevel level, long situs) {
		if (!boleh()) {
			return;
		}
		DIBERI.put(situs, new HashSet<>());
		DIBELI.put(situs, new HashMap<>());
		CeritaApi.kabar(level, situs, "tema_air_lentera");
	}

	private static void tick(ServerLevel level, long situs) {
		if (!boleh()) {
			return;
		}
		AirKota.Hidup kota = AirKota.cari(situs);
		if (kota != null && TemaConfig.inti().bahaya) {
			arwah(level, kota);
		}
	}

	private static void akhir(ServerLevel level, long situs) {
		DIBERI.remove(situs);
		DIBELI.remove(situs);
		hapus(PANGGUNG.remove(situs));
		if (!adaTahapArwah(level, situs)) {
			hapus(ARWAH.remove(situs));
		}
	}

	private static void hapus(@Nullable List<KultivatorNpc> list) {
		if (list != null) {
			for (KultivatorNpc npc : list) {
				if (!npc.isRemoved()) {
					npc.vanish();
				}
			}
		}
	}

	/** The stage anchor is near a player: singer and musicians while the festival runs. */
	static void panggungDekat(ServerLevel level, AirKota.Hidup kota, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		List<KultivatorNpc> crew = PANGGUNG.computeIfAbsent(kota.situs, k -> new ArrayList<>());
		crew.removeIf(Entity::isRemoved);
		if (!berjalan(kota.situs) || !boleh()) {
			hapus(crew);
			crew.clear();
			return;
		}
		if (crew.size() < 3) {
			int i = crew.size();
			BlockPos at = anchor.blockPosition().offset(i == 0 ? 0 : i == 1 ? -3 : 3, 0, i == 0 ? 1 : 3);
			KultivatorNpc npc = HidupApi.tubuh(level, kota.situs, NpcRole.WARGA, at, 180.0F);
			if (npc != null) {
				npc.memory().putBoolean("hold", true);
				npc.memory().putString("air_panggung", i == 0 ? "penyanyi" : "pemusik");
				npc.setPlate(Component.translatable(i == 0 ? "kultivator.kota_tema.air.panggung.penyanyi" : "kultivator.kota_tema.air.panggung.pemusik"));
				crew.add(npc);
			}
		}
		long now = level.getGameTime();
		if (now % 120L < 20L) {
			for (int i = 0; i < crew.size(); i++) {
				KultivatorNpc npc = crew.get(i);
				if (i == 0) {
					HidupApi.klip(npc, GerakId.id("nyanyi_perahu"), PropId.id("kipas_sutra"), 120);
					npc.say(Component.translatable("kultivator.kota_tema.air.lagu." + (1 + (int) Math.floorMod(now / 120L, 6L))), 110);
				} else {
					HidupApi.klip(npc, GerakId.id(i == 1 ? "main_seruling" : "tabuh_genderang_perahu"), 0, 120);
				}
			}
			level.playSound(null, anchor.blockPosition(), (i(now) ? SoundEvents.NOTE_BLOCK_FLUTE : SoundEvents.NOTE_BLOCK_HARP).value(),
					SoundSource.RECORDS, 0.8F, 0.8F + (now / 120L % 5L) * 0.1F);
		}
	}

	private static boolean i(long now) {
		return (now / 120L) % 2L == 0L;
	}

	// ================================================================== buying and releasing

	/** Dialog effect {@code air_lentera_beli}: one lantern for 1 NB (3 per festival). */
	static void beli(ServerPlayer player) {
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null) {
			return;
		}
		Map<UUID, Integer> bought = DIBELI.get(kota.situs);
		int n = bought == null ? 0 : bought.getOrDefault(player.getUUID(), 0);
		if (bought == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.lentera.bukan_malam").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (n >= BELI_MAKS) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.lentera.cukup", BELI_MAKS).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!NilaiBatu.pay(player, HARGA)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(HARGA)).withStyle(ChatFormatting.RED));
			return;
		}
		bought.put(player.getUUID(), n + 1);
		Barang.beri(player, new ItemStack(TemaItems.LENTERA_TERATAI));
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.lentera.beli", HARAPAN_MAKS).withStyle(ChatFormatting.GOLD));
	}

	private static @Nullable Titik titikDekat(ServerLevel level, AirKota.Hidup kota, BlockPos pos, double r) {
		Titik best = null;
		double bestD = r * r;
		for (Titik t : AirKota.titik(level, kota, "lentera_air")) {
			double d = t.pos().distSqr(pos);
			if (d <= bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	private static boolean lepas(ServerPlayer player, ItemStack stack) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null) {
			return false;
		}
		boolean festival = berjalan(kota.situs);
		KultivatorNpc spirit = arwahDekat(kota.situs, player.position());
		Titik at = titikDekat(level, kota, player.blockPosition(), JARAK_TITIK);
		Vec3 water = at != null ? Vec3.atCenterOf(at.pos()) : airDekat(level, kota, player);
		if (spirit == null && (!festival || water == null)) {
			Msg.actionBar(player, Component.translatable(festival ? "kultivator.kota_tema.air.lentera.ke_air" : "kultivator.kota_tema.air.lentera.bukan_malam")
					.withStyle(ChatFormatting.YELLOW));
			return true;
		}
		String wish = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
		if (wish.length() > HARAPAN_MAKS) {
			wish = wish.substring(0, HARAPAN_MAKS);
		}
		stack.shrink(1);
		Vec3 from = water != null ? water : player.position();
		level.playSound(null, BlockPos.containing(from), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 0.6D, from.z, 6, 0.2D, 0.2D, 0.2D, 0.01D);
		kirimLentera(level, kota, player, from, wish);
		if (spirit != null) {
			tenangkan(level, kota, spirit, player);
		}
		if (festival) {
			Set<UUID> given = DIBERI.get(kota.situs);
			if (given != null && given.add(player.getUUID())) {
				AirKota.tambahNamaBaik(player, kota, 1);
				Msg.chat(player, Component.translatable("kultivator.kota_tema.air.lentera.lepas_pertama").withStyle(ChatFormatting.GOLD));
				CeritaApi.kabar(level, kota.situs, "tema_air_harapan", player.getName().getString());
			} else {
				Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.lentera.lepas").withStyle(ChatFormatting.AQUA));
			}
			TemaCerita.lapor(player, TemaCerita.LENTERA_DILEPAS, AirKota.TEMA);
			AirCerita.peristiwa(player, "air_lentera", "lepas");
		}
		return true;
	}

	/** The FESTIVAL packet of one lantern: with the wish for its owner, without it for everybody else near. */
	private static void kirimLentera(ServerLevel level, AirKota.Hidup kota, ServerPlayer owner, Vec3 at, String wish) {
		long now = level.getGameTime();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(at) > 160.0D * 160.0D) {
				continue;
			}
			CompoundTag data = new CompoundTag();
			data.putString("acara", ACARA);
			data.putBoolean("mulai", true);
			data.putLong("tick", now);
			CompoundTag l = new CompoundTag();
			l.putInt("x", (int) Math.floor(at.x));
			l.putInt("y", (int) Math.floor(kota.permukaan() + 0.5D));
			l.putInt("z", (int) Math.floor(at.z));
			l.putLong("tick", now);
			l.putInt("umur", UMUR_LENTERA);
			if (p == owner && !wish.isEmpty()) {
				l.putString("teks", wish);
			}
			l.putBoolean("milik", p == owner);
			data.put("lentera", l);
			TemaNet.kirim(p, TemaNet.FESTIVAL, kota.situs, data);
		}
	}

	/** A water column at the shore within 3 blocks in front of the player, or null. */
	private static @Nullable Vec3 airDekat(ServerLevel level, AirKota.Hidup kota, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		for (int d = 1; d <= 3; d++) {
			Vec3 p = player.position().add(look.x * d, 0.0D, look.z * d);
			if (Perahu.air(level, kota, p)) {
				return new Vec3(p.x, kota.permukaan(), p.z);
			}
		}
		return null;
	}

	// ================================================================== the Kain Xiu Niang

	private static boolean lepasKain(ServerPlayer player, ItemStack stack) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null || !QuestTracker.aktif(player, QUEST) || !"lepas".equals(QuestTracker.simpul(player, QUEST))) {
			return false;
		}
		if (!berjalan(kota.situs)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.kain.tunggu_malam").withStyle(ChatFormatting.YELLOW));
			return true;
		}
		Titik at = titikDekat(level, kota, player.blockPosition(), JARAK_TITIK + 2.0D);
		Vec3 water = at != null ? Vec3.atCenterOf(at.pos()) : airDekat(level, kota, player);
		if (water == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.lentera.ke_air").withStyle(ChatFormatting.YELLOW));
			return true;
		}
		stack.shrink(1);
		kirimLentera(level, kota, player, water, Component.translatable("kultivator.kota_tema.air.kain.harapan").getString());
		level.playSound(null, BlockPos.containing(water), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
		boolean bicara = CeritaApi.bendera(player, "air_xiu_bicara");
		if (bicara) {
			KultivatorNpc xiu = AirTokoh.panggil(level, kota.situs, AirTokoh.XIU_NIANG, BlockPos.containing(water).above(), player.getYRot() + 180.0F);
			if (xiu != null) {
				xiu.memory().putBoolean("hold", true);
				HidupApi.tatap(xiu, player);
				xiu.say(Component.translatable("kultivator.kota_tema.air.kain.xiu_pamit"), 80);
				Air.nanti(level.getServer(), 80, () -> {
					if (!xiu.isRemoved()) {
						level.sendParticles(ParticleTypes.END_ROD, xiu.getX(), xiu.getY() + 1.0D, xiu.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.04D);
						level.playSound(null, xiu.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
						xiu.vanish();
					}
				});
			}
		}
		// the residents around bow to the cloth boat
		for (Entity e : level.getEntities((Entity) null, player.getBoundingBox().inflate(16.0D), e -> e instanceof KultivatorNpc)) {
			KultivatorNpc npc = (KultivatorNpc) e;
			if (npc.role() != NpcRole.PENJAGA && npc.role() != NpcRole.SISA_JIWA) {
				HidupApi.klip(npc, GerakId.id("sujud_dupa"), PropId.id("dupa"), 80);
			}
		}
		Msg.chat(player, Component.translatable(bicara ? "kultivator.kota_tema.air.kain.lepas" : "kultivator.kota_tema.air.kain.lepas_sepi")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		AirKota.kota(level.getServer(), kota.situs).putBoolean("ulat_pulih", true);
		AirKota.ubah(level.getServer());
		AirCerita.peristiwa(player, "air_kain_lepas", bicara ? "pamit" : "sepi");
		return true;
	}

	// ================================================================== Arwah Kanal

	private static boolean adaTahapArwah(ServerLevel level, long situs) {
		AirKota.Hidup kota = AirKota.cari(situs);
		if (kota == null) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (kota.jarak(p.position()) < AirKota.JANGKAU_KOTA && QuestTracker.aktif(p, QUEST) && "arwah".equals(QuestTracker.simpul(p, QUEST))) {
				return true;
			}
		}
		return false;
	}

	/** Every 20 ticks from the event runner or from {@link Air} at night for stage {@code arwah}. */
	static void arwah(ServerLevel level, AirKota.Hidup kota) {
		List<KultivatorNpc> live = ARWAH.computeIfAbsent(kota.situs, k -> new ArrayList<>());
		live.removeIf(e -> e.isRemoved() || !e.isAlive());
		if (live.size() >= ARWAH_MAKS || level.getGameTime() % 100L != 0L) {
			return;
		}
		// near a player at the lake (danau_lentera): rise from the water
		for (ServerPlayer p : level.players()) {
			if (!"danau_lentera".equals(kota.distrik(p.blockPosition())) || p.isSpectator()) {
				continue;
			}
			if (dev.kultivator.util.Sibuk.why(p) != null) {
				continue;
			}
			double a = level.getRandom().nextDouble() * Math.PI * 2.0D;
			Vec3 at = p.position().add(Math.cos(a) * 8.0D, 0.0D, Math.sin(a) * 8.0D);
			if (!Perahu.air(level, kota, at)) {
				continue;
			}
			KultivatorNpc npc = HidupApi.tubuh(level, kota.situs, NpcRole.SISA_JIWA, BlockPos.containing(at.x, kota.lantai(), at.z), 0.0F);
			if (npc == null) {
				return;
			}
			npc.memory().putString("air_arwah", "kanal");
			npc.setPlate(Component.translatable("kultivator.kota_tema.air.arwah.nama"));
			npc.setNoGravity(true);
			TechniquePower.markRealmFoe(npc);
			level.sendParticles(ParticleTypes.SOUL, at.x, kota.lantai(), at.z, 12, 0.4D, 0.6D, 0.4D, 0.02D);
			level.playSound(null, npc.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.6F);
			live.add(npc);
			return;
		}
	}

	private static @Nullable KultivatorNpc arwahDekat(long situs, Vec3 at) {
		List<KultivatorNpc> live = ARWAH.get(situs);
		if (live == null) {
			return null;
		}
		for (KultivatorNpc npc : live) {
			if (!npc.isRemoved() && npc.position().distanceToSqr(at) <= JARAK_ARWAH * JARAK_ARWAH) {
				return npc;
			}
		}
		return null;
	}

	private static void tenangkan(ServerLevel level, AirKota.Hidup kota, KultivatorNpc spirit, ServerPlayer player) {
		level.sendParticles(ParticleTypes.END_ROD, spirit.getX(), spirit.getY() + 1.0D, spirit.getZ(), 24, 0.3D, 0.6D, 0.3D, 0.03D);
		level.playSound(null, spirit.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.6F);
		spirit.vanish();
		List<KultivatorNpc> live = ARWAH.get(kota.situs);
		if (live != null) {
			live.remove(spirit);
		}
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.arwah.tenang").withStyle(ChatFormatting.AQUA));
		AirCerita.peristiwa(player, "air_arwah", "tenang");
	}

	/** The live lost souls of a town (tests). */
	static int jumlahArwah(long situs) {
		List<KultivatorNpc> live = ARWAH.get(situs);
		return live == null ? 0 : (int) live.stream().filter(e -> !e.isRemoved()).count();
	}

	/** True when a player stands at chain stage {@code arwah} (the souls rise at night without the festival). */
	static boolean tahapArwah(ServerPlayer player) {
		return QuestTracker.aktif(player, QUEST) && "arwah".equals(QuestTracker.simpul(player, QUEST));
	}

	private LenteraAir() {
	}
}
