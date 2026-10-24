package dev.kultivator.dunia.tempat.hidup;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /kultivasi uji hidup ...} (DESIGN 38.N.8; op, through {@code DuniaCommands.test}, chip group {@code hidup}):
 * <ul>
 *   <li>{@code tempat}: the inhabited place here: kind, residents, bodies, titik by type;</li>
 *   <li>{@code jadwal}: the nearest resident body: role, activity now and in an hour, goal, state;</li>
 *   <li>{@code jam <0-23>}: the place's residents live that hour for 60 s (the world clock never changes);</li>
 *   <li>{@code klip <id|nama>}: the nearest NPC plays a clip for 5 s; {@code klip semua}: a line of 24 test NPCs plays
 *       every clip 1-107 in turn, five seconds each, the clip's name in its bubble (38.E.4);</li>
 *   <li>{@code prop <id|nama>}: the nearest NPC holds a prop for 10 s;</li>
 *   <li>{@code impostor}: active places, bodies, far crowd sizes;</li>
 *   <li>{@code saksi}: who sees the caller now (bodies with a line of sight, scheduled occupants of the lot);</li>
 *   <li>{@code bahaya}: danger at the caller for 60 s;</li>
 *   <li>{@code cuaca <hujan|badai|salju|kabut|gerhana|meteor|bulan|cerah>}: the residents react for 60 s (only the
 *       reaction is shown; the weather and the sky never change).</li>
 * </ul>
 */
public final class HidupCommands {

	private static final int BARISAN = 24;
	private static final int KLIP_TICK = 100;
	private static final List<KultivatorNpc> UJI = new ArrayList<>();
	private static int ujiPutaran;
	private static long ujiNext;
	private static boolean ticking;

	static void init() {
		DuniaCommands.test("hidup", HidupCommands::node);
		if (!ticking) {
			ticking = true;
			ServerTickEvents.END_SERVER_TICK.register(HidupCommands::tick);
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("hidup")
				.then(Commands.literal("tempat").executes(HidupCommands::tempat))
				.then(Commands.literal("jadwal").executes(HidupCommands::jadwal))
				.then(Commands.literal("jam").then(Commands.argument("jam", IntegerArgumentType.integer(0, 23))
						.executes(ctx -> jam(ctx, IntegerArgumentType.getInteger(ctx, "jam")))))
				.then(Commands.literal("klip")
						.then(Commands.literal("semua").executes(HidupCommands::klipSemua))
						.then(Commands.argument("klip", StringArgumentType.word()).executes(ctx -> klip(ctx, StringArgumentType.getString(ctx, "klip")))))
				.then(Commands.literal("prop").then(Commands.argument("prop", StringArgumentType.word())
						.executes(ctx -> prop(ctx, StringArgumentType.getString(ctx, "prop")))))
				.then(Commands.literal("impostor").executes(HidupCommands::impostor))
				.then(Commands.literal("saksi").executes(HidupCommands::saksi))
				.then(Commands.literal("bahaya").executes(HidupCommands::bahaya))
				.then(Commands.literal("keadaan").then(Commands.argument("keadaan", StringArgumentType.word())
						.suggests((ctx, b) -> {
							for (String s : KEADAAN) {
								b.suggest(s);
							}
							return b.buildFuture();
						})
						.executes(ctx -> keadaan(ctx, StringArgumentType.getString(ctx, "keadaan")))))
				.then(Commands.literal("cuaca").then(Commands.argument("cuaca", StringArgumentType.word())
						.suggests((ctx, b) -> {
							for (String s : new String[]{"hujan", "badai", "salju", "kabut", "gerhana", "meteor", "bulan", "cerah"}) {
								b.suggest(s);
							}
							return b.buildFuture();
						})
						.executes(ctx -> cuaca(ctx, StringArgumentType.getString(ctx, "cuaca")))));
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
		ctx.getSource().sendSuccess(() -> Component.translatable(key, args), false);
	}

	private static int gagal(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
		ctx.getSource().sendFailure(Component.translatable(key, args));
		return 0;
	}

	/** The active inhabited place the player stands in or nearest to (within 128 blocks), or null. */
	private static @Nullable TempatHuni dekat(ServerPlayer player) {
		TempatHuni best = null;
		double bestD = Pemanggil.JAUH;
		for (TempatHuni h : Pemanggil.semua()) {
			if (!h.dimensi.equals(player.level().dimension())) {
				continue;
			}
			double d = Pemanggil.jarak(h.kotak, player.position());
			if (d <= bestD) {
				bestD = d;
				best = h;
			}
		}
		return best;
	}

	private static @Nullable KultivatorNpc npcTerdekat(ServerPlayer player, boolean pendudukSaja) {
		KultivatorNpc best = null;
		double bestSq = 16.0D * 16.0D;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(16.0D),
				n -> n.isAlive() && (!pendudukSaja || TubuhPenduduk.adalah(n)))) {
			double d = npc.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				best = npc;
			}
		}
		return best;
	}

	// ================================================================== tempat, jadwal, jam

	private static int tempat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatHuni h = dekat(player);
		if (h == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_tempat");
		}
		ok(ctx, "kultivator.hidup.uji.tempat", h.jenis, Long.toHexString(h.situsId), h.penduduk().size(), h.jumlahTubuh(),
				HidupConfig.get().tubuhUntuk(h.jenis), h.jumlahTitik());
		StringBuilder types = new StringBuilder();
		for (Map.Entry<String, Integer> e : h.ringkasTitik().entrySet()) {
			if (!types.isEmpty()) {
				types.append(", ");
			}
			types.append(e.getKey()).append(' ').append(e.getValue());
		}
		String text = types.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
		Jadwal.Cuaca c = h.cuaca;
		ok(ctx, "kultivator.hidup.uji.cuaca_tempat", c.hujan(), c.badai(), c.salju(), c.kabut(), c.langit() == null ? "-" : c.langit().id(),
				String.valueOf(Pemanggil.acara(h, player.level().getGameTime())));
		return 1;
	}

	private static int jadwal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc npc = npcTerdekat(player, true);
		TempatHuni h = npc == null ? null : TubuhPenduduk.huni(npc);
		Penduduk p = npc == null ? null : TubuhPenduduk.penduduk(npc);
		if (npc == null || h == null || p == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_penduduk");
		}
		ServerLevel level = player.level();
		long waktu = Pemanggil.waktu(h, level);
		Jadwal.Saat s = Jadwal.saat(p, h.jenis, waktu);
		// as the place lives it: the night drum, a crowd scene and the resident's life state already applied
		Kegiatan now = h.kegiatan(p, waktu, level.getGameTime());
		Kegiatan next = h.kegiatan(p, waktu + 1000L, level.getGameTime());
		ok(ctx, "kultivator.hidup.uji.jadwal", npc.plate(), Component.translatable(p.peran().titleKey()), p.id(), p.kelompok(),
				Component.translatable("kultivator.hidup.kelompok." + p.peran().kelompok.id()), String.format("%02d:%02d", s.jam(), s.menit()));
		ok(ctx, "kultivator.hidup.uji.kegiatan", Component.translatableWithFallback(now.kunciLang(), now.kunci()), now.tujuan(), GerakId.namaAtauId(now.klip()),
				Component.translatableWithFallback(next.kunciLang(), next.kunci()), TubuhPenduduk.keadaan(npc));
		int state = h.keadaanDari(p.id());
		if (state != PendudukData.SEHAT) {
			ok(ctx, "kultivator.hidup.uji.keadaan_jadwal", Component.translatable("kultivator.hidup.keadaan_nama." + KEADAAN[state]));
		}
		return 1;
	}

	private static int jam(CommandContext<CommandSourceStack> ctx, int jam) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatHuni h = dekat(player);
		if (h == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_tempat");
		}
		Pemanggil.pratinjauJam(h.situsId, jam, player.level().getGameTime() + 1200L);
		for (KultivatorNpc body : h.tubuh.values()) {
			TubuhPenduduk.segarkan(body);
		}
		ok(ctx, "kultivator.hidup.uji.jam", String.format("%02d:00", jam));
		return 1;
	}

	// ================================================================== clips and props

	private static int klip(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = GerakId.id(name);
		if (id <= 0 || !GerakId.ada(id)) {
			return gagal(ctx, "kultivator.hidup.uji.klip_tidak", name);
		}
		KultivatorNpc npc = npcTerdekat(player, false);
		if (npc == null) {
			npc = HidupApi.tubuh(player.level(), 0L, NpcRole.WARGA, depan(player, 3.0D), player.getYRot() + 180.0F);
		}
		if (npc == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_penduduk");
		}
		SosokApi.gerak(npc, id, 0, KLIP_TICK, 0, 0, 0);
		ok(ctx, "kultivator.hidup.uji.klip", GerakId.namaAtauId(id), id, npc.plate());
		return 1;
	}

	private static int prop(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = PropId.id(name);
		if (id <= 0) {
			return gagal(ctx, "kultivator.hidup.uji.prop_tidak", name);
		}
		KultivatorNpc npc = npcTerdekat(player, false);
		if (npc == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_penduduk");
		}
		SosokApi.gerak(npc, GerakId.BERDIRI_JAGA, 0, 200, id, 0, 0);
		ok(ctx, "kultivator.hidup.uji.prop", String.valueOf(PropId.nama(id)), id, npc.plate());
		return 1;
	}

	/** {@code klip semua}: a line of 24 test NPCs (varied roles), every clip 1-107 in turn, 5 s each. */
	private static int klipSemua(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		bersihkanUji();
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
		Vec3 side = new Vec3(-look.z, 0.0D, look.x);
		NpcRole[] roles = {NpcRole.WARGA, NpcRole.PETANI, NpcRole.KOKI, NpcRole.EMPU, NpcRole.ANAK, NpcRole.TETUA_DESA, NpcRole.PENJAGA_GERBANG,
				NpcRole.PEDAGANG_KELILING, NpcRole.RAHIB, NpcRole.MURID_SEKTE_LUAR, NpcRole.PELAUT, NpcRole.PENENUN_SUTRA};
		for (int i = 0; i < BARISAN; i++) {
			Vec3 at = player.position().add(look.scale(6.0D + (i / 12) * 3.0D)).add(side.scale((i % 12 - 5.5D) * 1.6D));
			BlockPos ground = dev.kultivator.dunia.tempat.TempatApi.groundSpot(level, (int) Math.floor(at.x), (int) Math.floor(at.z), player.getBlockY());
			KultivatorNpc npc = HidupApi.tubuh(level, 0L, roles[i % roles.length], ground != null ? ground : BlockPos.containing(at),
					player.getYRot() + 180.0F);
			if (npc == null) {
				break;
			}
			npc.memory().putBoolean("hold", true);
			UJI.add(npc);
		}
		if (UJI.isEmpty()) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_penduduk");
		}
		ujiPutaran = 0;
		ujiNext = level.getGameTime();
		ok(ctx, "kultivator.hidup.uji.klip_semua", UJI.size(), (GerakId.TEMPAT_LAST + UJI.size() - 1) / UJI.size());
		return UJI.size();
	}

	private static void tick(MinecraftServer server) {
		if (UJI.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		if (now < ujiNext) {
			return;
		}
		UJI.removeIf(KultivatorNpc::isRemoved);
		int start = ujiPutaran * BARISAN + 1;
		if (UJI.isEmpty() || start > GerakId.TEMPAT_LAST) {
			bersihkanUji();
			return;
		}
		for (int i = 0; i < UJI.size(); i++) {
			KultivatorNpc npc = UJI.get(i);
			int id = start + i;
			if (id > GerakId.TEMPAT_LAST || !GerakId.ada(id)) {
				SosokApi.henti(npc);
				npc.say(Component.literal("-"), KLIP_TICK);
				continue;
			}
			SosokApi.gerak(npc, id, 0, KLIP_TICK, 0, 0, 0);
			npc.say(Component.literal(id + " " + GerakId.namaAtauId(id)), KLIP_TICK);
		}
		ujiPutaran++;
		ujiNext = now + KLIP_TICK + 10L;
	}

	private static void bersihkanUji() {
		for (KultivatorNpc npc : UJI) {
			if (!npc.isRemoved()) {
				npc.discard();
			}
		}
		UJI.clear();
	}

	private static BlockPos depan(ServerPlayer player, double d) {
		Vec3 look = player.getLookAngle();
		return BlockPos.containing(player.position().add(look.x * d, 0.0D, look.z * d));
	}

	// ================================================================== impostor, witnesses, danger, weather

	private static int impostor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int bodies = 0;
		int residents = 0;
		for (TempatHuni h : Pemanggil.semua()) {
			bodies += h.jumlahTubuh();
			residents += h.penduduk().size();
		}
		ok(ctx, "kultivator.hidup.uji.impostor", Pemanggil.semua().size(), residents, bodies, HidupConfig.get().tubuhGlobal,
				HidupConfig.get().perPemain, HidupConfig.get().impostor ? HidupConfig.get().impostorMaks : 0, JadwalData.jumlah());
		TempatHuni h = dekat(player);
		if (h != null) {
			int far = 0;
			for (Penduduk p : h.penduduk()) {
				TempatHuni.Tujuan t = Pemanggil.posisi(h.situsId, p.id());
				if (t != null && !h.tubuh.containsKey(p.id())) {
					double d = player.position().distanceTo(Vec3.atBottomCenterOf(t.pos()));
					if (d > Pemanggil.PANGGIL && d <= Pemanggil.JAUH) {
						far++;
					}
				}
			}
			ok(ctx, "kultivator.hidup.uji.impostor_tempat", h.jenis, far, h.titik("penonton").size());
		}
		return 1;
	}

	private static int saksi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		int seen = 0;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(HidupApi.SAKSI_JARAK),
				n -> n.isAlive() && n.role().townsfolk() && n.hasLineOfSight(player))) {
			seen++;
			// each witness turns its head to the caller for 3 s, so the tester sees who they are
			SosokApi.tatap(npc, player);
			SosokApi.gerak(npc, GerakId.TUNJUK, 0, 60, 0, 0, 0);
		}
		TempatHuni h = Saksi.tempat(level, player.blockPosition());
		int lot = h == null ? 0 : Saksi.penghuniLot(h, player.blockPosition());
		ok(ctx, "kultivator.hidup.uji.saksi", seen, lot, HidupApi.saksi(level, player.blockPosition(), player));
		return 1;
	}

	/** Life state names in {@link PendudukData} order (SEHAT, PULIH, DUKA, DATANG). */
	private static final String[] KEADAAN = {"sehat", "pulih", "duka", "datang"};

	/**
	 * {@code uji hidup keadaan <sehat|pulih|duka|datang>}: gives the nearest resident body a life state now (saved like a
	 * real one, lasting its usual time) so the tester sees the limp and sling, the household's mourning or the newcomer's
	 * first day without waiting for a wound or a death.
	 */
	private static int keadaan(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int state = java.util.Arrays.asList(KEADAAN).indexOf(name);
		if (state < 0) {
			return gagal(ctx, "kultivator.hidup.uji.keadaan_tidak", name);
		}
		KultivatorNpc npc = npcTerdekat(player, true);
		TempatHuni h = npc == null ? null : TubuhPenduduk.huni(npc);
		Penduduk p = npc == null ? null : TubuhPenduduk.penduduk(npc);
		if (npc == null || h == null || p == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_penduduk");
		}
		int rumahTangga = p.tamu() ? 0 : p.kelompok();
		if (state == PendudukData.DUKA && rumahTangga <= 0) {
			return gagal(ctx, "kultivator.hidup.uji.keadaan_sendiri");
		}
		long now = player.level().getGameTime();
		PendudukData data = PendudukData.get(player.level().getServer());
		data.ujiKeadaan(h.situsId, p.id(), rumahTangga, state, now);
		int changed = 0;
		for (Penduduk each : h.penduduk()) {
			int s = data.keadaan(h.situsId, each.id(), each.tamu() ? 0 : each.kelompok(), now);
			if (s == PendudukData.SEHAT) {
				h.keadaan.remove(each.id());
			} else {
				h.keadaan.put(each.id(), s);
			}
			KultivatorNpc body = h.tubuh.get(each.id());
			if (body != null && (each.id() == p.id() || state == PendudukData.DUKA && each.kelompok() == rumahTangga)) {
				TubuhPenduduk.segarkan(body);
				changed++;
			}
		}
		ok(ctx, "kultivator.hidup.uji.keadaan", npc.getDisplayName(), Component.translatable("kultivator.hidup.keadaan_nama." + name),
				rumahTangga, changed);
		return 1;
	}

	private static int bahaya(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		HidupApi.bahaya(player.level(), player.blockPosition(), 60);
		ok(ctx, "kultivator.hidup.uji.bahaya", 60);
		return 1;
	}

	private static int cuaca(CommandContext<CommandSourceStack> ctx, String kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatHuni h = dekat(player);
		if (h == null) {
			return gagal(ctx, "kultivator.hidup.uji.tanpa_tempat");
		}
		Jadwal.Cuaca c = switch (kind) {
			case "hujan" -> new Jadwal.Cuaca(true, false, false, false, false, null);
			case "badai" -> new Jadwal.Cuaca(true, true, false, false, false, null);
			case "salju" -> new Jadwal.Cuaca(false, false, true, true, false, null);
			case "kabut" -> new Jadwal.Cuaca(false, false, false, false, true, null);
			case "gerhana" -> new Jadwal.Cuaca(false, false, false, false, false, SkyKind.BLOOD_ECLIPSE);
			case "meteor" -> new Jadwal.Cuaca(false, false, false, false, false, SkyKind.METEOR_SHOWER);
			case "bulan" -> new Jadwal.Cuaca(false, false, false, false, false, SkyKind.TWIN_MOONS);
			case "cerah" -> Jadwal.Cuaca.CERAH;
			default -> null;
		};
		if (c == null) {
			return gagal(ctx, "kultivator.hidup.uji.cuaca_tidak", kind);
		}
		Pemanggil.pratinjauCuaca(h.situsId, c, player.level().getGameTime() + 1200L);
		for (KultivatorNpc body : h.tubuh.values()) {
			TubuhPenduduk.segarkan(body);
		}
		ok(ctx, "kultivator.hidup.uji.cuaca", kind);
		return 1;
	}

	private HidupCommands() {
	}
}
