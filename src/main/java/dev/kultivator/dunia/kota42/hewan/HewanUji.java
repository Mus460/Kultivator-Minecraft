package dev.kultivator.dunia.kota42.hewan;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaUji;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /kultivasi uji kota_hidup hewan [semua | kucing | anjing | ayam | merpati | kuda | unta | keledai | daftar]}
 * (DESIGN 42.14.3): a lineup of every coat of the chosen animals (all seven by default) in rows in front of the tester for
 * 30 s, standing still and showing each animation state for 3 s in turn (the model check of 42V-H); pigeon flocks behave
 * as in town. {@code daftar}: the live animals and candidates of every active place.
 */
final class HewanUji {

	static final String K = "kultivator.kota42.hewan.uji.";
	static final long LAMA = 30L * 20L;
	static final List<String> JENIS = List.of("kucing", "anjing", "ayam", "merpati", "kuda", "unta", "keledai");

	static void init() {
		KotaUji.daftar("hewan", HewanUji::node);
		KotaUji.chips("kota_hidup hewan daftar", "kota_hidup hewan cek");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hewan").executes(ctx -> baris(ctx, JENIS))
				.then(Commands.literal("semua").executes(ctx -> baris(ctx, JENIS)))
				.then(Commands.literal("daftar").executes(HewanUji::daftar))
				.then(Commands.literal("cek").executes(HewanUji::cek));
		for (String j : JENIS) {
			root.then(Commands.literal(j).executes(ctx -> baris(ctx, List.of(j))));
		}
		return root;
	}

	private static int baris(CommandContext<CommandSourceStack> ctx, List<String> jenis) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!KotaConfig.hewan().aktif) {
			ctx.getSource().sendFailure(Component.translatable(K + "mati"));
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 depan = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 kanan = new Vec3(-depan.z, 0.0D, depan.x);
		float hadap = player.getYRot() + 180.0F;
		int n = 0;
		int baris = 0;
		for (String j : jenis) {
			Vec3 awal = player.position().add(depan.scale(4.0D + baris * 3.0D));
			int varian = jumlahVarian(j);
			for (int v = 0; v < varian; v++) {
				Vec3 at = awal.add(kanan.scale((v - (varian - 1) / 2.0D) * 2.5D));
				Entity e = HewanKota.buatUji(level, tipe(j), at, hadap);
				if (e == null) {
					continue;
				}
				if (e instanceof HewanKotaEntity h) {
					h.setVarian(v);
					h.ikat(0L, h.blockPosition().asLong());
					if (h instanceof Mob m) {
						m.setNoAi(true);
					}
					if (h instanceof KucingKotaEntity k && v == KucingKotaEntity.VARIAN_ORANYE && j.equals("kucing")) {
						k.jadikanLaoHua();
					}
					if (h instanceof KudaTambatEntity kuda && v == KudaTambatEntity.VARIAN_PUTIH) {
						kuda.setHias(true);
					}
					if (h instanceof KeledaiKotaEntity d) {
						d.setMuatan(v == KeledaiKotaEntity.VARIAN_COKLAT);
					}
				} else if (e instanceof KawananMerpatiEntity m) {
					m.setVarian(v);
					m.setJumlah(KawananMerpatiEntity.JUMLAH_MAKS);
					m.setBenih(v * 7919 + 13);
					m.setTempat(at, List.of());
				}
				HewanKota.uji(e, jumlahKeadaan(j), LAMA);
				n++;
			}
			baris++;
		}
		int made = n;
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "baris", made, String.join(", ", jenis)).withStyle(ChatFormatting.GOLD), false);
		return made;
	}

	private static int daftar(CommandContext<CommandSourceStack> ctx) {
		List<String> rows = HewanKota.ringkas();
		ctx.getSource().sendSuccess(() -> Component.translatable(K + "daftar", rows.size(), HewanKota.jumlahDunia(), HewanKota.MAKS_DUNIA)
				.withStyle(ChatFormatting.GOLD), false);
		for (String row : rows) {
			ctx.getSource().sendSuccess(() -> Component.literal(row), false);
		}
		return rows.size();
	}

	/** Self-check of the pure parts: deterministic in-range picks, unique keys, kind limits within the default place cap. */
	private static int cek(CommandContext<CommandSourceStack> ctx) {
		List<String> salah = new java.util.ArrayList<>();
		net.minecraft.core.BlockPos a = new net.minecraft.core.BlockPos(12, 64, -40);
		for (int n = 1; n <= 7; n++) {
			int v = HewanKota.acak(77L, a, 3, n);
			if (v < 0 || v >= n || v != HewanKota.acak(77L, a, 3, n)) {
				salah.add("acak " + n);
			}
		}
		if (HewanKota.kunci(HewanKota.KUCING, a, 0).equals(HewanKota.kunci(HewanKota.KUCING, a, 1))) {
			salah.add("kunci");
		}
		for (String jenis : List.of("kota/kota_besar", "kota/kota", "kota_tema/pelabuhan", "kota_tema/oasis", "kota_tema/iblis", "kampung/desa", "pos/dagang")) {
			int sum = HewanKota.batas(jenis).values().stream().mapToInt(Integer::intValue).sum();
			if (sum > new KotaConfig.Hewan().perTempat || !HewanKota.bolehTempat(jenis)) {
				salah.add("batas " + jenis + "=" + sum);
			}
		}
		if (HewanKota.bolehTempat("sekte/kompleks")) {
			salah.add("kompleks");
		}
		boolean ok = salah.isEmpty();
		ctx.getSource().sendSuccess(() -> ok ? Component.translatable(K + "cek_ok").withStyle(ChatFormatting.GREEN)
				: Component.translatable(K + "cek_gagal", String.join(", ", salah)).withStyle(ChatFormatting.RED), false);
		return ok ? 1 : 0;
	}

	private static EntityType<? extends Entity> tipe(String j) {
		return switch (j) {
			case "kucing" -> HewanEntities.KUCING_KOTA;
			case "anjing" -> HewanEntities.ANJING_KOTA;
			case "ayam" -> HewanEntities.AYAM_KOTA;
			case "merpati" -> HewanEntities.KAWANAN_MERPATI;
			case "kuda" -> HewanEntities.KUDA_TAMBAT;
			case "unta" -> HewanEntities.UNTA_TAMBAT;
			default -> HewanEntities.KELEDAI_KOTA;
		};
	}

	private static int jumlahVarian(String j) {
		return switch (j) {
			case "kucing" -> KucingKotaEntity.VARIAN_JUMLAH;
			case "anjing" -> AnjingKotaEntity.VARIAN_JUMLAH;
			case "ayam" -> AyamKotaEntity.VARIAN_JUMLAH;
			case "merpati" -> KawananMerpatiEntity.VARIAN_JUMLAH;
			case "kuda" -> KudaTambatEntity.VARIAN_JUMLAH;
			case "unta" -> UntaTambatEntity.VARIAN_JUMLAH;
			default -> KeledaiKotaEntity.VARIAN_JUMLAH;
		};
	}

	/** Animation states per animal (the lineup cycles through them). */
	private static int jumlahKeadaan(String j) {
		return switch (j) {
			case "kucing" -> KucingKotaEntity.KEADAAN_LOMPAT + 1;
			case "anjing" -> AnjingKotaEntity.KEADAAN_KIBAS + 1;
			case "ayam" -> AyamKotaEntity.KEADAAN_KABUR + 1;
			case "merpati" -> 1;
			case "kuda" -> KudaTambatEntity.KEADAAN_HENTAK + 1;
			case "unta" -> UntaTambatEntity.KEADAAN_DENGUS + 1;
			default -> KeledaiKotaEntity.KEADAAN_MERINGKIK + 1;
		};
	}

	private HewanUji() {
	}
}
