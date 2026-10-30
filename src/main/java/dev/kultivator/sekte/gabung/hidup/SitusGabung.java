package dev.kultivator.sekte.gabung.hidup;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The meditation places of a member (DESIGN 41.4.5 last bullet, 41.6.6) as one {@code SpiritSites} provider, and the
 * runtime entry of module 41C-K (dwellings, spirit pools, the disciple tournament, the Shifu's lessons).
 * <p>
 * The provider offers the <b>largest</b> of the member's sect factors where he stands (41.4.5: "yang terbesar berlaku";
 * they never stack with each other, only with the old sites, and {@code SpiritSites.CAP 4.0} still bounds the product):
 * <ul>
 *   <li>{@code gabung_kompleks}: inside his own sect's compound, x1.10 / 1.15 / 1.20 / 1.25 / 1.30 by class
 *   ({@code KelasSekte.bonusMeditasi});</li>
 *   <li>{@code gabung_kediaman}: in his own dwelling ({@code Kediaman.Jenis.faktor}: dormitory x1.05 .. cloud pavilion
 *   x1.80), or x1.20 as a guest at a fellow member's private peak;</li>
 *   <li>{@code gabung_kolam}: at the seat of his running spirit-pool session, x1.30 ({@link KolamRoh}).</li>
 * </ul>
 * Only members of an active membership count; a reload that switches 41 off silences it. Cheap: a few map reads and
 * distances per evaluation ({@code SpiritSites} caches per block and 5 s).
 */
public final class SitusGabung {

	public static final String ID_KOMPLEKS = "gabung_kompleks";
	public static final String ID_KEDIAMAN = "gabung_kediaman";
	public static final String ID_KOLAM = "gabung_kolam";
	/** A guest at a fellow member's private peak (41.6.6 Puncak "tamu boleh meditasi x1.2"). */
	public static final double FAKTOR_TAMU = 1.20D;

	private static boolean initialised;
	private static long tickCount;

	private SitusGabung() {
	}

	/**
	 * Runtime wiring of 41C-K, called once from {@code HidupAnggota.init()} while 41 is on: the site provider, the dwelling
	 * upkeep and seat watcher, the tournament, the lessons, one server tick (every 20 ticks), the test subs and the player
	 * subs {@code kediaman} and {@code pelajaran}. Safe to call twice.
	 */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Kediaman.init();
		MuridTitipan.init();
		TurnamenMurid.init();
		// F41-1: the 39F named roster fights in the Disciple Tournament (elders and the wounded stay out)
		TurnamenMurid.setSumberRoster(new TurnamenMurid.SumberRoster() {
			@Override
			public List<TurnamenMurid.Peserta> peserta(MinecraftServer server, String sekte) {
				long hari = dev.kultivator.sekte.Ingatan.today(server);
				List<TurnamenMurid.Peserta> out = new ArrayList<>();
				for (dev.kultivator.sekte.mandiri.seteru.Roster.Tokoh t : dev.kultivator.sekte.mandiri.seteru.Roster.daftar(server, sekte)) {
					if (t.aktif() && !t.luka(hari) && t.peranNomor() < TurnamenMurid.PERAN_TETUA) {
						out.add(new TurnamenMurid.Peserta(t.id(), t.nama(), t.perempuan(), t.rank().step(), t.peranNomor(), t.sifat()));
					}
				}
				return out;
			}

			@Override
			public void kalahDari(MinecraftServer server, String id, UUID pemain) {
				dev.kultivator.sekte.mandiri.seteru.Roster.ubahSikap(server, id, pemain, -10);
			}
		});
		Pelajaran.init();
		SpiritSites.addSiteProvider(SitusGabung::situs);
		ServerTickEvents.END_SERVER_TICK.register(SitusGabung::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			KolamRoh.reset();
			Kediaman.reset();
		});
		GabungApi.daftarUji("turnamen", TurnamenMurid::ujiNode);
		GabungApi.daftarUji("pelajaran", Pelajaran::ujiNode);
		GabungApi.daftarUji("sewa", SitusGabung::ujiSewa);
		GabungApi.daftarUji("kediaman", SitusGabung::ujiKediaman);
		dev.kultivator.sekte.gabung.GabungUji.chips("gabung kediaman [jenis]", "gabung pelajaran [mulai|selesai|lindungi|lampaui|reset]");
		GabungCommands.daftar("kediaman", SitusGabung::pemainKediaman);
		GabungCommands.daftar("pelajaran", Pelajaran::pemainNode);
	}

	private static void tick(MinecraftServer server) {
		if (!GabungApi.aktif()) {
			return;
		}
		tickCount++;
		if (tickCount % 20L != 0L) {
			return;
		}
		TurnamenMurid.tick(server);
		Pelajaran.tick(server);
		Kediaman.sambut(server);
		KolamRoh.suasana(server);
		if (tickCount % Kediaman.SEAT_JEDA == 0L) {
			Kediaman.tick(server);
		}
	}

	// ================================================================== the site provider

	/** The member's sect site where he stands, or null (the {@code SpiritSites} provider). */
	static SpiritSites.@Nullable ExtraSite situs(ServerPlayer player) {
		if (!GabungApi.aktif() || player.level().dimension() != Level.OVERWORLD) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		if (sekte == null) {
			return null;
		}
		String id = null;
		double best = 1.0D;
		Component nama = null;
		Kediaman.Hunian h = Kediaman.hunian(k);
		if (h != null && h.jenis() != Kediaman.Jenis.BILIK && Kediaman.diDalam(player, h) && h.jenis().faktor() > best) {
			best = h.jenis().faktor();
			id = ID_KEDIAMAN;
			nama = Component.translatable("kultivator.gabung.situs.kediaman", h.nama());
		}
		if (KolamRoh.aktifDi(player) && Kediaman.KOLAM_FAKTOR > best) {
			best = Kediaman.KOLAM_FAKTOR;
			id = ID_KOLAM;
			nama = Component.translatable("kultivator.gabung.situs.kolam");
		}
		var kompleks = KompleksApi.kompleksDi(player.level(), player.blockPosition());
		boolean diKompleks = kompleks != null && sekte.equals(kompleks.sekte());
		if (diKompleks) {
			KelasSekte kelas = GabungApi.kelasSekte(sekte);
			if (kelas.bonusMeditasi() > best) {
				best = kelas.bonusMeditasi();
				id = ID_KOMPLEKS;
				nama = Component.translatable("kultivator.gabung.situs.kompleks", kelas.nama());
			}
			if (h != null && h.jenis() == Kediaman.Jenis.BILIK && Kediaman.Jenis.BILIK.faktor() > best) {
				best = Kediaman.Jenis.BILIK.faktor();
				id = ID_KEDIAMAN;
				nama = Component.translatable("kultivator.gabung.situs.kediaman", Kediaman.Jenis.BILIK.nama());
			}
			if (FAKTOR_TAMU > best && tamuPuncak(server, player, sekte)) {
				best = FAKTOR_TAMU;
				id = ID_KEDIAMAN;
				nama = Component.translatable("kultivator.gabung.situs.tamu");
			}
		}
		if (id == null) {
			return null;
		}
		return new SpiritSites.ExtraSite(id, best, null, nama);
	}

	/** The factor the sect gives the player where he stands (1.0 for none), for screens. */
	public static double faktor(ServerPlayer player) {
		SpiritSites.ExtraSite site = situs(player);
		return site == null ? 1.0D : site.multiplier();
	}

	/** True when the player sits at a private peak another member of his sect rents. */
	private static boolean tamuPuncak(MinecraftServer server, ServerPlayer player, String sekte) {
		GabungData data = GabungData.get(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			if (uuid.equals(player.getUUID())) {
				continue;
			}
			Keanggotaan other = data.peek(uuid);
			Kediaman.Hunian h = other == null ? null : Kediaman.hunian(other);
			if (h == null || h.jenis() != Kediaman.Jenis.PUNCAK) {
				continue;
			}
			BlockPos at = Kediaman.posisi(server, sekte, h.jenis(), h.titik());
			if (at != null && player.position().distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D) <= Kediaman.DEKAT * Kediaman.DEKAT) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== player sub /kultivasi gabung kediaman

	private static LiteralArgumentBuilder<CommandSourceStack> pemainKediaman() {
		return Commands.literal("kediaman")
				.executes(SitusGabung::bukaKediaman)
				.then(Commands.literal("sewa").then(Commands.argument("titik", StringArgumentType.greedyString())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(titikSaran(ctx.getSource()), b))
						.executes(ctx -> aksi(ctx, Kediaman.sewa(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "titik"))))))
				.then(Commands.literal("perpanjang").executes(ctx -> aksi(ctx, Kediaman.perpanjang(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("lepas").executes(ctx -> aksi(ctx, Kediaman.lepas(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("jimat").executes(ctx -> aksi(ctx, Kediaman.jadikanJimat(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("kolam")
						.executes(ctx -> {
							KolamRoh.ringkas(ctx.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.argument("seat", StringArgumentType.greedyString())
								.executes(ctx -> aksi(ctx, KolamRoh.pesan(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "seat"))))));
	}

	/** Opens the 41U dwelling screen when it exists, else the chat summary. */
	private static int bukaKediaman(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Keanggotaan k = GabungApi.anggota(player);
		if (k.sentuh(player, GabungApi.hari(ctx.getSource().getServer()))) {
			GabungData.get(ctx.getSource().getServer()).setDirty();
		}
		if (!TempatLayar.bukaDari(player, GabungLembar.Kediaman.LAYAR, -1)) {
			Kediaman.ringkas(player);
		}
		return 1;
	}

	private static int aksi(CommandContext<CommandSourceStack> ctx, @Nullable Component why) {
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static List<String> titikSaran(CommandSourceStack source) {
		List<String> out = new ArrayList<>();
		for (Kediaman.Jenis jenis : Kediaman.Jenis.values()) {
			out.addAll(jenis.semuaTitik());
		}
		return out;
	}

	// ================================================================== test subs

	/** {@code uji gabung sewa lewat}: the dwelling's rent falls due now. */
	private static LiteralArgumentBuilder<CommandSourceStack> ujiSewa() {
		return Commands.literal("sewa").then(Commands.literal("lewat").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (!Kediaman.sewaLewat(player)) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.kediaman.tanpa_gua"));
				return 0;
			}
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.kediaman.uji.lewat"), true);
			return 1;
		}));
	}

	/**
	 * {@code uji gabung kediaman [jenis]}: the dwelling state and the factor where the tester stands; with a kind, the first
	 * free titik of that kind is rented with the rank lent for the call and the PK given back (the class and building checks
	 * still apply) so the seat, chest and factor can be tried.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> ujiKediaman() {
		return Commands.literal("kediaman")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Kediaman.ringkas(player);
					KolamRoh.ringkas(player);
					SpiritSites.ExtraSite site = situs(player);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.kediaman.uji.faktor",
							site == null ? Component.literal("-") : site.name(), String.format(java.util.Locale.ROOT, "%.2f",
									site == null ? 1.0D : site.multiplier())), false);
					return 1;
				})
				.then(Commands.argument("jenis", StringArgumentType.word())
						.suggests((ctx, b) -> {
							List<String> ids = new ArrayList<>();
							for (Kediaman.Jenis j : Kediaman.Jenis.values()) {
								ids.add(j.id());
							}
							return SharedSuggestionProvider.suggest(ids, b);
						})
						.executes(ctx -> ujiSewaGratis(ctx, StringArgumentType.getString(ctx, "jenis"))));
	}

	private static int ujiSewaGratis(CommandContext<CommandSourceStack> ctx, String jenisId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Kediaman.Jenis jenis = Kediaman.Jenis.byId(jenisId);
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (jenis == null || sekte == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		java.util.Map<String, Component> taken = Kediaman.penghuni(server, sekte, player.getUUID());
		String pick = null;
		for (String titik : jenis.semuaTitik()) {
			if (!taken.containsKey(titik)) {
				pick = titik;
				break;
			}
		}
		if (pick == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.kediaman.penuh", jenis.nama()));
			return 0;
		}
		Pangkat lama = k.pangkat;
		int pk = k.pk;
		// a test rent: lend the rank and the PK for the one call, then give the PK back
		if (k.pangkat.tingkat() < jenis.pangkatMin()) {
			k.pangkat = Pangkat.byOrdinal(Math.max(jenis.pangkatMin(), Pangkat.TETUA.ordinal()));
		}
		k.pk = Math.max(k.pk, 1_000_000);
		Component why = Kediaman.sewa(player, pick);
		k.pangkat = lama;
		k.pk = pk;
		GabungData.get(server).setDirty();
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		String chosen = pick;
		BlockPos at = Kediaman.posisi(server, sekte, jenis, chosen);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.kediaman.uji.sewa", Kediaman.nama(jenis, chosen),
				at == null ? Component.translatable("kultivator.gabung.kediaman.uji.posisi_belum")
						: Component.literal(at.getX() + " " + at.getY() + " " + at.getZ()).withStyle(ChatFormatting.AQUA)), true);
		Msg.actionBar(player, Component.translatable("kultivator.gabung.kediaman.uji.datangi").withStyle(ChatFormatting.GRAY));
		return 1;
	}
}
