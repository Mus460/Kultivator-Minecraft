package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.mandiri.CitaCita;
import dev.kultivator.sekte.mandiri.KebijakanAula;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every button of the Jalan Hidup tab and the board (payload {@code mandiri_aksi}, DESIGN 39.7.3/39.7.4). The server checks
 * everything again: only the master of the hall acts on its disciples (an op may read); hand-to-hand ops (giving stones,
 * taking an item) need the body within {@code Disciples.ACTION_RANGE}; the rest work from the board too.
 * <p>
 * Disciple ops ({@code soul} = the disciple): {@code harian} (the page), {@code tegur}, {@code puji}, {@code tebus},
 * {@code dorong} (arg path id), {@code bebaskan}, {@code ikut_aula}, {@code cita_dukung} / {@code cita_batal} (arg goal
 * kind), {@code larang} / {@code izinkan} (arg permission group), {@code saku} (n %), {@code mode} / {@code risiko} (arg),
 * {@code beri_uang} (the stones in the main hand), {@code ambil} (n bag slot). Hall ops ({@code soul} ""): {@code papan},
 * {@code aula_mode}, {@code aula_risiko} (arg), {@code aula_saku}, {@code aula_izin}, {@code aula_beritahu},
 * {@code aula_titipan} (n), {@code aula_jenjang} (arg), {@code aula_tunggu} (arg terobosan|dao, n days),
 * {@code aula_hati} (n 0/1), {@code aula_seteru} (arg).
 * <p>
 * Other DESIGN 39 modules add their ops with {@link #daftar} (39C {@code panggil}, {@code jelmaan}; 39F feud ops), since
 * they may not import this package's classes but may call this one entry from their init.
 */
public final class AksiMandiri {

	/** An op of another module: the master, the body when loaded (else null), the soul, the argument, the number. */
	@FunctionalInterface
	public interface Penangan {
		void tangani(ServerPlayer master, @Nullable DiscipleEntity body, UUID soul, String arg, int n);
	}

	private static final Map<String, Penangan> LAIN = new ConcurrentHashMap<>();
	private static final String K = "kultivator.sekte.mandiri.cerita.aksi.";

	/** Registers {@code op} (replaces an earlier one; built-in ops cannot be replaced). */
	public static void daftar(String op, Penangan penangan) {
		if (op != null && !op.isEmpty() && penangan != null) {
			LAIN.put(op, penangan);
		}
	}

	static void terima(ServerPlayer player, MandiriPayloads.Aksi aksi) {
		if (player == null || aksi == null || !MandiriApi.aktif()) {
			return;
		}
		try {
			jalankan(player, aksi);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri action {} failed", aksi.op(), exception);
		}
	}

	private static void jalankan(ServerPlayer player, MandiriPayloads.Aksi aksi) {
		UUID owner = player.getUUID();
		String op = aksi.op();
		if ("papan".equals(op)) {
			PapanMandiri.kirim(player, owner);
			return;
		}
		if (op.startsWith("aula_")) {
			aula(player, owner, op, aksi.arg(), aksi.n());
			PapanMandiri.kirim(player, owner);
			return;
		}
		UUID soul;
		try {
			soul = UUID.fromString(aksi.soul());
		} catch (IllegalArgumentException exception) {
			return;
		}
		DiscipleEntity body = Perencana.cari(owner, soul);
		if (body != null && !owner.equals(body.ownerId())) {
			return;
		}
		if ("harian".equals(op)) {
			BukuHarian.kirim(player, owner, soul);
			return;
		}
		Penangan other = LAIN.get(op);
		if (other != null) {
			other.tangani(player, body, soul, aksi.arg(), aksi.n());
			segarkan(player, owner, soul, body);
			return;
		}
		if (body == null) {
			jawab(player, "jauh", ChatFormatting.YELLOW);
			return;
		}
		boolean dekat = body.level() == player.level() && body.distanceToSqr(player) <= Disciples.ACTION_RANGE * Disciples.ACTION_RANGE;
		switch (op) {
			case "tegur" -> jawab(player, MandiriApi.tegur(body, player) ? "tegur.ok" : "sudah_hari_ini", ChatFormatting.GRAY);
			case "puji" -> jawab(player, MandiriApi.puji(body, player) ? "puji.ok" : "sudah_hari_ini", ChatFormatting.GRAY);
			case "tebus" -> {
				Component why = MandiriApi.tebus(body, player);
				if (why != null) {
					Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
				}
			}
			case "dorong" -> {
				JalanHidup jalan = JalanHidup.byId(aksi.arg());
				if (jalan != null && jalan.pilihanAwal()) {
					MandiriApi.dorongJalan(body, jalan);
					jawab(player, "dorong.ok", ChatFormatting.GRAY, body.getName(), Component.translatable(jalan.kunciNama()));
				}
			}
			case "bebaskan" -> {
				MandiriApi.bebaskan(body);
				jawab(player, "bebaskan.ok", ChatFormatting.GREEN, body.getName());
			}
			case "ikut_aula" -> {
				KebijakanAula.setTumpang(body, null);
				jawab(player, "ikut_aula.ok", ChatFormatting.GRAY, body.getName());
			}
			case "cita_dukung" -> {
				JenisCita jenis = JenisCita.byId(aksi.arg());
				if (jenis != null) {
					CitaCita.dukung(body, jenis);
					jawab(player, "cita_dukung.ok", ChatFormatting.GRAY, body.getName(), Component.translatable(jenis.kunciNama()));
				}
			}
			case "cita_batal" -> {
				JenisCita jenis = JenisCita.byId(aksi.arg());
				if (jenis != null) {
					jawab(player, CitaCita.batalkan(body, jenis) ? "cita_batal.ok" : "cita_batal.tidak", ChatFormatting.GRAY, body.getName(),
							Component.translatable(jenis.kunciNama()));
				}
			}
			case "larang", "izinkan" -> {
				IzinKegiatan group = IzinKegiatan.byId(aksi.arg());
				if (group != null) {
					int izin = MandiriApi.kebijakan(body).izin();
					izin = "larang".equals(op) ? izin & ~group.bit() : izin | group.bit();
					KebijakanAula.setTumpangAngka(body, "izin", izin);
				}
			}
			case "saku" -> KebijakanAula.setTumpangAngka(body, "uangSaku", Math.max(0, Math.min(200, aksi.n())));
			case "mode" -> KebijakanAula.setTumpangKunci(body, "mode", ModeMandiri.byId(aksi.arg(), ModeMandiri.SEIMBANG).id());
			case "risiko" -> KebijakanAula.setTumpangKunci(body, "risiko", Kehatian.byId(aksi.arg(), Kehatian.WAJAR).id());
			case "beri_uang" -> {
				if (!dekat) {
					jawab(player, "terlalu_jauh", ChatFormatting.YELLOW);
				} else {
					beriUang(player, body);
				}
			}
			case "ambil" -> {
				if (!dekat) {
					jawab(player, "terlalu_jauh", ChatFormatting.YELLOW);
				} else {
					ambil(player, body, aksi.n());
				}
			}
			default -> {
				return;
			}
		}
		segarkan(player, owner, soul, body);
	}

	/** The fresh details and page after an op (the tab redraws from both). */
	private static void segarkan(ServerPlayer player, UUID owner, UUID soul, @Nullable DiscipleEntity body) {
		if (body != null && body.level() == player.level() && body.distanceToSqr(player) <= Disciples.VIEW_RANGE * Disciples.VIEW_RANGE) {
			Disciples.sendInfo(player, body);
		}
		BukuHarian.kirim(player, owner, soul);
	}

	/** [Beri uang]: the spirit stones in the master's main hand go into the purse, as many as fit. */
	private static void beriUang(ServerPlayer player, DiscipleEntity body) {
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		int grade = SpiritStones.grade(hand);
		if (grade < 0) {
			jawab(player, "beri_uang.tanpa_batu", ChatFormatting.YELLOW);
			return;
		}
		DiscipleData data = body.data();
		long room = MandiriApi.dompetMaks(data) - MandiriApi.dompet(data);
		long per = NilaiBatu.VALUE[grade];
		int count = (int) Math.min(hand.getCount(), room / per);
		if (count <= 0) {
			jawab(player, "beri_uang.penuh", ChatFormatting.YELLOW, body.getName());
			return;
		}
		long applied = MandiriApi.ubahDompet(data, per * count, "guru");
		if (applied <= 0L) {
			return;
		}
		hand.shrink(count);
		jawab(player, "beri_uang.ok", ChatFormatting.GREEN, body.getName(), NilaiBatu.text(applied));
		Ingatan.remember(player.level().getServer(), data, "mandiri.uang_guru", player.getUUID(), 2,
				"kultivator.sekte.mandiri.cerita.ingatan.uang_guru", "");
	}

	/** [Ambil barang]: bag slot {@code slot} to the master (a memory -1: a disciple likes to keep what it bought). */
	private static void ambil(ServerPlayer player, DiscipleEntity body, int slot) {
		DiscipleData data = body.data();
		List<ItemStack> bag = MandiriApi.tas(data, player.level().getServer().registryAccess());
		List<ItemStack> filled = new java.util.ArrayList<>(bag);
		filled.removeIf(ItemStack::isEmpty);
		if (slot < 0 || slot >= filled.size()) {
			return;
		}
		ItemStack want = filled.get(slot);
		ItemStack taken = MandiriApi.ambilTas(data, player.level().getServer().registryAccess(),
				s -> ItemStack.isSameItemSameComponents(s, want), want.getCount());
		if (taken.isEmpty()) {
			return;
		}
		Component name = taken.getHoverName();
		int count = taken.getCount();
		if (!player.getInventory().add(taken) && !taken.isEmpty()) {
			// block-safe like every item the mod drops (NoGrief tag): no portal travel, no pressure plates
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(),
					player.getY() + 0.5D, player.getZ(), taken.copy());
			item.setTarget(player.getUUID());
			item.setNoPickUpDelay();
			item.addTag(dev.kultivator.util.NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
		Ingatan.remember(player.level().getServer(), data, "mandiri.barang_diambil", player.getUUID(), -1,
				"kultivator.sekte.mandiri.cerita.ingatan.barang_diambil", "");
		jawab(player, "ambil.ok", ChatFormatting.GRAY, body.getName(), Component.literal(count + "× ").append(name));
	}

	/** The hall policy ops (the board's editor). */
	private static void aula(ServerPlayer player, UUID owner, String op, String arg, int n) {
		Kebijakan now = MandiriApi.kebijakanAula(owner);
		Kebijakan next = switch (op) {
			case "aula_mode" -> now.withMode(ModeMandiri.byId(arg, now.mode()));
			case "aula_risiko" -> now.withRisiko(Kehatian.byId(arg, now.risiko()));
			case "aula_saku" -> now.withUangSaku(n);
			case "aula_izin" -> now.withIzin(n);
			case "aula_beritahu", "aula_titipan", "aula_jenjang", "aula_tunggu", "aula_hati", "aula_seteru" -> salin(now, op, arg, n);
			default -> null;
		};
		if (next != null && !next.equals(now)) {
			KebijakanAula.setAula(player.level().getServer(), owner, next, player);
			jawab(player, "aula.ok", ChatFormatting.GRAY);
		}
	}

	/** The fields without a {@code with*} (all through the tag form, so the record stays 39A's). */
	private static Kebijakan salin(Kebijakan now, String op, String arg, int n) {
		net.minecraft.nbt.CompoundTag tag = now.toTag();
		switch (op) {
			case "aula_beritahu" -> tag.putInt("beritahu", n & ((1 << Kebijakan.Pemberitahuan.values().length) - 1));
			case "aula_titipan" -> tag.putInt("titipan", Math.max(0, Math.min(5000, n)));
			case "aula_jenjang" -> tag.putString("jenjang", arg == null ? "" : arg.toUpperCase(java.util.Locale.ROOT));
			case "aula_tunggu" -> tag.putInt("dao".equals(arg) ? "tungguDao" : "tungguTerobosan", Math.max(0, Math.min(30, n)));
			case "aula_hati" -> tag.putBoolean("hatiHati", n != 0);
			case "aula_seteru" -> tag.putString("seteru", arg == null ? "" : arg.toUpperCase(java.util.Locale.ROOT));
			default -> {
			}
		}
		return Kebijakan.fromTag(tag, now);
	}

	private static void jawab(ServerPlayer player, String key, ChatFormatting tone, Object... args) {
		player.sendSystemMessage(Component.translatable(K + key, args).withStyle(tone), true);
	}

	private AksiMandiri() {
	}
}
