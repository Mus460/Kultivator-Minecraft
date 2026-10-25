package dev.kultivator.client.duel.ui;

import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.duel.DuelKeys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.CincinWaktu;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuHasil;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.musuh.RogueCultivatorEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gulungan › Dunia › Duel (DESIGN 35.8.5, 43.5; the former duel screen, now a page: G82, G49, G50, R4, R6). Three subs:
 * <ul>
 * <li>{@code tantang}: whom the player can challenge now (the one under the crosshair first, then players, his own
 * disciples and rogue cultivators within 24 blocks, nearest first) and "Duel Tonton" to pick two of his disciples to
 * watch; the challenge rules ({@link ChallengeScreen}) open as the page's detail view (back = header control / Esc).</li>
 * <li>{@code berjalan}: an incoming challenge with Terima / Tolak and its countdown, the running duel with Menyerah
 * (confirm seal, sedang) and Tunda (ringan), the salute (closes the Gulungan so the pose is seen), the controls and the
 * link to the duel rows of Pengaturan (the quick settings moved there: 43.10.1).</li>
 * <li>{@code catatan}: the player's record from the server ({@code DuelCatatan}, asked for while shown: 43.14 S2) and
 * the last result as a result card.</li>
 * </ul>
 * Everything is drawn immediately with the single kit; text at 1.0, fits the 289 × 148 content box of GUI scale 4.
 */
public final class DuelScreen extends Page {

	public static final String TANTANG = "tantang";
	public static final String BERJALAN = "berjalan";
	public static final String CATATAN = "catatan";

	private static final String K = "kultivator.gui43.m14.";
	private static final String S = "kultivator.duel.ui.screen.";
	private static final int ROW = 14;
	private static final int BTN = 14;
	private static final double RANGE = 24.0D;

	/** One entity the player may challenge. */
	record Candidate(int id, Component name, String kind, int pair, float distance, Component detail, int rgb) {
	}

	private final List<Candidate> candidates = new ArrayList<>();
	private final DaftarGulir daftar = new DaftarGulir();
	private final DaftarGulir daftarCatatan = new DaftarGulir();
	private final KartuHasil kartu = new KartuHasil();
	private long candidatesAt = Long.MIN_VALUE / 2;
	private boolean pickingTwo;
	private int firstPick = -1;
	private @Nullable ChallengeScreen tantangan;
	private String sub = TANTANG;
	private @Nullable DuelPayloads.DuelResult hasilDiminta;

	/** Registers the page (from {@code DuelClient.init}). */
	public static void daftar() {
		HubPages.daftar(HubNav.DUNIA, Urutan.ID_DUEL, Urutan.DUEL, DuelScreen::new);
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.dunia.duel");
	}

	@Override
	public int glyph() {
		return Glyphs.TECHNIQUES;
	}

	@Override
	public String helpTopic() {
		return "duel_pedang";
	}

	@Override
	public List<Sub> subs() {
		int waiting = DuelClient.offers().size();
		Component berjalan = waiting > 0 ? Component.translatable(K + "sub.berjalan_n", waiting)
				: Component.translatable("kultivator.gui43.sub.duel.berjalan");
		return List.of(new Sub(TANTANG, Component.translatable("kultivator.gui43.sub.duel.tantang")), new Sub(BERJALAN, berjalan),
				new Sub(CATATAN, Component.translatable("kultivator.gui43.sub.duel.catatan")));
	}

	@Override
	public void showSub(String id) {
		if (TANTANG.equals(id) || BERJALAN.equals(id) || CATATAN.equals(id)) {
			sub = id;
		}
		if (CATATAN.equals(sub)) {
			mintaCatatan();
		}
	}

	@Override
	public boolean rinci() {
		return TANTANG.equals(sub) && (tantangan != null || pickingTwo);
	}

	// ================================================================== life

	@Override
	protected void build() {
		// immediate page: everything is drawn in lukis()
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		refreshCandidates();
		if (CATATAN.equals(sub)) {
			mintaCatatan();
		}
		super.onShow(fokus);
	}

	@Override
	public void tick() {
		if (TANTANG.equals(sub) && Util.getMillis() - candidatesAt > 1000L) {
			refreshCandidates();
		}
		// a new result while the record is shown: ask again
		if (CATATAN.equals(sub) && DuelClient.lastResult() != hasilDiminta) {
			mintaCatatan();
		}
	}

	@Override
	public boolean mundur() {
		if (TANTANG.equals(sub) && tantangan != null) {
			tantangan = null;
			return true;
		}
		if (TANTANG.equals(sub) && pickingTwo) {
			pickingTwo = false;
			firstPick = -1;
			refreshCandidates();
			return true;
		}
		return false;
	}

	private void mintaCatatan() {
		hasilDiminta = DuelClient.lastResult();
		DuelClient.mintaCatatan();
	}

	// ================================================================== candidates

	private void refreshCandidates() {
		int dipilih = daftar.pilihan();
		int dipilihId = dipilih >= 0 && dipilih < candidates.size() ? candidates.get(dipilih).id() : -1;
		refreshList();
		// keep the selection on the same entity while distances reorder the rows
		for (int i = 0; i < candidates.size(); i++) {
			if (candidates.get(i).id() == dipilihId) {
				daftar.setPilih(i);
				break;
			}
		}
	}

	private void refreshList() {
		candidates.clear();
		candidatesAt = Util.getMillis();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		List<Integer> seen = new ArrayList<>();
		Entity aimed = mc.crosshairPickEntity;
		if (!pickingTwo && aimed instanceof LivingEntity living && living.isAlive() && living.distanceTo(player) <= 12.0F) {
			Candidate c = candidate(player, living);
			if (c != null) {
				candidates.add(c);
				seen.add(living.getId());
			}
		}
		List<Candidate> near = new ArrayList<>();
		AABB box = player.getBoundingBox().inflate(RANGE);
		for (Entity entity : mc.level.getEntities(player, box)) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || seen.contains(entity.getId()) || entity.distanceTo(player) > RANGE) {
				continue;
			}
			if (pickingTwo && !(entity instanceof DiscipleEntity)) {
				continue;
			}
			Candidate c = candidate(player, living);
			if (c != null && (c.kind().equals("player") || c.kind().equals("murid") || c.kind().equals("perampok"))) {
				near.add(c);
			}
		}
		near.sort(Comparator.comparingDouble(Candidate::distance));
		candidates.addAll(near);
		// the challenged entity went away: close its form
		if (tantangan != null && mc.level.getEntity(tantangan.targetId()) == null) {
			tantangan = null;
		}
	}

	private static @Nullable Candidate candidate(LocalPlayer player, LivingEntity living) {
		float distance = living.distanceTo(player);
		if (living instanceof ArmorStand || living == player) {
			return null;
		}
		if (living instanceof Player other) {
			if (other.isSpectator()) {
				return null;
			}
			return new Candidate(other.getId(), other.getDisplayName(), "player", DuelIds.PAIR_PVP, distance,
					Component.translatable("kultivator.duel.ui.kind.player"), 0x9FD3FF);
		}
		if (living instanceof DiscipleEntity disciple) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || !disciple.isOwnedBy(player)) {
				return null;
			}
			Component realm = Realm.byOrdinal(disciple.syncedRealm()).displayName();
			return new Candidate(disciple.getId(), disciple.getDisplayName(), "murid", DuelIds.PAIR_MURID, distance,
					Component.translatable("kultivator.duel.ui.kind.murid", realm), 0x9FE08F);
		}
		if (living instanceof RogueCultivatorEntity rogue) {
			Component realm = Realm.byOrdinal(rogue.realm()).displayName();
			return new Candidate(rogue.getId(), rogue.getDisplayName(), "perampok", DuelIds.PAIR_NPC, distance,
					Component.translatable("kultivator.duel.ui.kind.perampok", realm, rogue.tier()), rogue.tier() >= 4 ? 0xFF7A6A : 0xFFC870);
		}
		// Any other figure under the crosshair (a wandering cultivator of the Dunia, a sect NPC): the server decides.
		return new Candidate(living.getId(), living.getDisplayName(), "npc", DuelIds.PAIR_NPC, distance,
				Component.translatable("kultivator.duel.ui.kind.npc"), 0xE8DCC0);
	}

	/** The row the player picked: opens the rules, or takes a disciple of a watched duel. */
	private void pakai(Candidate c) {
		if (pickingTwo) {
			if (firstPick < 0 || firstPick == c.id()) {
				firstPick = firstPick == c.id() ? -1 : c.id();
				return;
			}
			Component firstName = Component.literal("?");
			for (Candidate other : candidates) {
				if (other.id() == firstPick) {
					firstName = other.name();
				}
			}
			tantangan = new ChallengeScreen(firstPick, c.id(), Component.translatable("kultivator.duel.ui.challenge.pair_names", firstName, c.name()),
					DuelIds.PAIR_TONTON, -1);
			pickingTwo = false;
			firstPick = -1;
			return;
		}
		int realm = -1;
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(c.id());
		if (entity instanceof DiscipleEntity disciple) {
			realm = disciple.syncedRealm();
		} else if (entity instanceof RogueCultivatorEntity rogue) {
			realm = rogue.realm();
		}
		tantangan = new ChallengeScreen(c.id(), -1, c.name(), c.pair(), realm);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		int x0 = x + 4;
		int y0 = y + 3;
		int x1 = x + w - 4;
		int y1 = y + h - 3;
		switch (sub) {
			case BERJALAN -> berjalan(l, x0, y0, x1, y1);
			case CATATAN -> catatan(l, x0, y0, x1, y1);
			default -> {
				ChallengeScreen form = tantangan;
				if (form != null) {
					form.gambar(l, x0, y0, x1, y1, sent -> {
						tantangan = null;
						pesan(sent, Palette.OK);
					});
				} else {
					tantang(l, x0, y0, x1, y1);
				}
			}
		}
	}

	// ---------------------------------------------------------------- tantang

	private void tantang(Lukis l, int x0, int y0, int x1, int y1) {
		// toolbar: the heading of the list, Duel Tonton at the right
		Component tonton = Component.translatable(pickingTwo ? S + "tonton_cancel" : S + "tonton");
		int tw = TalismanButton.lebar(l.font, tonton, 60, (x1 - x0) / 2);
		if (TalismanButton.gambar(l, "duel#tonton", x1 - tw, y0, tw, BTN, tonton, true, null, () -> {
			pickingTwo = !pickingTwo;
			firstPick = -1;
			refreshCandidates();
		}, false, pickingTwo)) {
			l.tip(List.of(Component.translatable(S + "tonton"), Component.translatable(S + "tonton.tip").withStyle(ChatFormatting.GRAY)));
		}
		Component judul = Component.translatable(pickingTwo ? (firstPick < 0 ? S + "pick_first" : S + "pick_second") : S + "challenge");
		l.teks(Brush.bold(judul), x0, y0 + 3, x1 - x0 - tw - 6, l.gaya.aksen);
		int top = y0 + BTN + 4;
		int bottom = y1 - BTN - 4;
		if (candidates.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA,
					Component.translatable(pickingTwo ? S + "no_disciples" : S + "nobody"), null, null, x0, top, x1, y1);
			return;
		}
		l.sumur(x0, top, x1, bottom);
		daftar.gambar(l, "duel#calon", x0 + 1, top + 1, x1 - 1, bottom - 1, candidates.size(), ROW, this::baris, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < candidates.size()) {
				pakai(candidates.get(i));
			}
		}, null);
		// the action of the selected row (a second click or Enter does the same)
		int i = daftar.pilihan();
		Candidate c = i >= 0 && i < candidates.size() ? candidates.get(i) : null;
		Component aksi = c == null ? Component.translatable(K + "pilih_dulu")
				: Component.translatable(pickingTwo ? (firstPick < 0 ? K + "jadi_pertama" : firstPick == c.id() ? K + "lepas_pertama" : K + "jadi_kedua")
				: K + "atur_tantangan", c.name());
		int aw = TalismanButton.lebar(l.font, aksi, 90, x1 - x0);
		if (TalismanButton.gambar(l, "duel#pakai", x1 - aw, y1 - BTN, aw, BTN, aksi, c != null, null, () -> {
			if (c != null) {
				pakai(c);
			}
		}, c != null && !pickingTwo, false) && c != null) {
			l.tip(List.of(c.name(), c.detail(),
					Component.translatable(pickingTwo ? S + "pick.tip" : S + "row.tip").withStyle(ChatFormatting.GRAY)));
		}
		l.samar(Component.translatable(K + "jumlah_calon", candidates.size()), x0, y1 - BTN + 3, x1 - x0 - aw - 6);
	}

	private void baris(Lukis l, int i, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih) {
		Candidate c = candidates.get(i);
		boolean picked = c.id() == firstPick;
		l.kartu(rx0, ry0, rx1, ry1 - 1, sorot ? 1.0F : 0.0F, pilih || picked);
		int glyph = switch (c.kind()) {
			case "player" -> Glyphs.STATUS;
			case "murid" -> Glyphs.TALENT;
			case "perampok" -> Glyphs.TECHNIQUES;
			default -> Glyphs.PATH;
		};
		Glyphs.tab(l.g, glyph, rx0 + 8.0F, ry0 + 6.5F, 9.0F, Palette.onPaper(c.rgb()));
		Component jarak = Component.translatable(K + "jarak", Math.round(c.distance()));
		int jw = l.font.width(jarak);
		int lebar = rx1 - rx0 - 18 - jw - 6;
		int nameW = Math.min(l.font.width(c.name()), lebar * 6 / 10);
		l.teks(c.name(), rx0 + 16, ry0 + 3, nameW, picked ? Palette.SEAL_DARK : l.gaya.teks);
		l.teks(c.detail(), rx0 + 20 + nameW, ry0 + 3, lebar - nameW - 4, l.gaya.teksSamar);
		l.kanan(jarak, rx1 - 3, ry0 + 3, l.gaya.teksLemah);
	}

	// ---------------------------------------------------------------- berjalan

	private void berjalan(Lukis l, int x0, int y0, int x1, int y1) {
		int y = y0;
		List<DuelClient.Offer> offers = DuelClient.offers();
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		boolean jalan = s != null && s.phase() != DuelIds.PHASE_ENDED;
		int bottomRow = y1 - BTN;
		if (!offers.isEmpty()) {
			DuelClient.Offer o = offers.get(0);
			DuelPayloads.DuelChallengeOffer offer = o.offer();
			y += l.kepala(Component.translatable(K + "tantangan_masuk"), x0, y, x1);
			int cardBottom = y + 59;
			l.kartu(x0, y, x1, cardBottom, 0.0F, true);
			int tw = CincinWaktu.teks(l, x1 - 4, y + 3, o.ticksLeft(), offer.expiresTicks(), l.kini, true);
			l.teks(offer.name(), x0 + 5, y + 3, x1 - x0 - tw - 14, l.gaya.aksen);
			l.teks(offer.rankLine(), x0 + 5, y + 13, x1 - x0 - 10, l.gaya.teksLemah);
			l.teks(ChallengeOfferToast.rulesLine(offer.rules()), x0 + 5, y + 23, x1 - x0 - 10, l.gaya.teksSamar);
			l.teks(ChallengeOfferToast.stakeLine(offer), x0 + 5, y + 33, x1 - x0 - 10, l.gaya.teksSamar);
			int bw = (x1 - x0 - 14) / 2;
			Component terima = Component.translatable(K + "terima");
			if (TalismanButton.gambar(l, "duel#terima", x0 + 5, y + 44, bw, BTN - 1, terima, true, null,
					() -> DuelClient.answer(offer.challengeId(), DuelIds.ANSWER_TERIMA), true, false)) {
				l.tip(List.of(terima, ChallengeOfferToast.rulesLine(offer.rules()), ChallengeOfferToast.stakeLine(offer)));
			}
			Component tolak = Component.translatable(K + "tolak");
			if (TalismanButton.gambar(l, "duel#tolak", x0 + 9 + bw, y + 44, bw, BTN - 1, tolak, true, null,
					() -> DuelClient.answer(offer.challengeId(), DuelIds.ANSWER_TOLAK))) {
				l.tip(List.of(tolak, Component.translatable("kultivator.duel.ui.offer.decline.tip").withStyle(ChatFormatting.GRAY)));
			}
			y = cardBottom + 3;
			if (offers.size() > 1) {
				l.samar(Component.translatable(K + "tantangan_lain", offers.size() - 1), x0, y, x1 - x0);
				y += 11;
			}
		}
		if (jalan && y + 13 + 26 <= bottomRow) {
			LocalPlayer player = minecraft.player;
			boolean isA = player != null && s.aId() == player.getId();
			Component opponent = isA ? s.bName() : s.aName();
			y += l.kepala(Component.translatable(S + "running", Component.translatable("kultivator.duel.ui.phase." + s.phase())), x0, y, x1);
			int tw = 0;
			if (s.phase() == DuelIds.PHASE_FIGHT) {
				tw = CincinWaktu.teks(l, x1, y, DuelClient.fightTicksLeft(s), 0L, l.kini, true) + 4;
			}
			l.teks(Component.translatable(S + "versus", opponent), x0, y, x1 - x0 - tw, l.gaya.aksen);
			y += 11;
			int bw = (x1 - x0 - 4) / 2;
			Component menyerah = Component.translatable(S + "surrender");
			boolean bisa = DuelClient.inFight();
			if (TalismanButton.gambar(l, "duel#menyerah", x0, y, bw, BTN, menyerah, bisa, bisa ? null : Component.translatable(K + "hanya_bertarung"),
					() -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "yakin_menyerah", opponent),
							() -> command("kultivasi duel menyerah")), bisa, false)) {
				l.tip(List.of(menyerah, Component.translatable(S + "surrender.tip", DuelHud.parryKey()).withStyle(ChatFormatting.GRAY)));
			}
			Component tunda = Component.translatable(S + "pause");
			if (TalismanButton.gambar(l, "duel#tunda", x0 + bw + 4, y, bw, BTN, tunda, true, null,
					() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable(K + "yakin_tunda"), () -> {
						command("kultivasi duel tunda");
						pesan(Component.translatable(K + "tunda_dikirim"), Palette.OK);
					}))) {
				l.tip(List.of(tunda, Component.translatable(S + "pause.tip").withStyle(ChatFormatting.GRAY)));
			}
			y += BTN + 4;
		}
		if (offers.isEmpty() && !jalan) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(K + "tidak_berjalan"),
					Component.translatable(K + "ke_tantang"), () -> tuju(Tujuan.of(HubNav.DUNIA, Urutan.ID_DUEL).sub(TANTANG)), x0, y0, x1,
					bottomRow - 14);
			y = bottomRow - 14;
		}
		// the controls, wrapped in the room left
		Component keys = Component.translatable(S + "controls", DuelHud.parryKey(),
				DuelKeys.CAMERA.isUnbound() ? Component.literal("-") : DuelKeys.CAMERA.getTranslatedKeyMessage());
		float room = bottomRow - 3 - y;
		if (room >= 9.0F) {
			l.bungkus(keys, x0, y, x1 - x0, l.gaya.teksSamar, room);
		}
		// salute and the settings link
		int bw = (x1 - x0 - 4) / 2;
		Component hormat = Component.translatable(S + "salute");
		if (TalismanButton.gambar(l, "duel#hormat", x0, bottomRow, bw, BTN, hormat, true, null, () -> {
			command("kultivasi duel hormat");
			// the pose is meant to be seen (43.13 R6)
			HubNav.tutupSemua();
		})) {
			l.tip(List.of(hormat, Component.translatable(S + "salute.tip").withStyle(ChatFormatting.GRAY)));
		}
		Component atur = Component.translatable(K + "atur");
		if (TalismanButton.gambar(l, "duel#atur", x0 + bw + 4, bottomRow, bw, BTN, atur, true, null,
				() -> HubNav.openSettings(HubNav.GROUP_KONTROL))) {
			l.tip(List.of(atur, Component.translatable(K + "atur.tip").withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void command(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) {
			mc.getConnection().sendCommand(command);
		}
	}

	// ---------------------------------------------------------------- catatan

	private void catatan(Lukis l, int x0, int y0, int x1, int y1) {
		CompoundTag t = DuelClient.catatan();
		DuelPayloads.DuelResult last = DuelClient.lastResult();
		boolean kanan = tata() != Tata.SEMPIT && last != null;
		int lx1 = kanan ? x0 + (x1 - x0) * 11 / 20 : x1;
		if (kanan) {
			hasil(l, last, lx1 + 6, y0, x1, y1);
		}
		if (t == null) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN,
					Component.translatable(DuelClient.connected() ? K + "memuat" : "kultivator.duel.ui.challenge.offline"), null, null, x0, y0, lx1, y1);
			return;
		}
		int honour = t.getIntOr("kehormatan", 1000);
		Component gelar = Component.translatable("kultivator.duel.title." + t.getStringOr("gelar", "pendekar"));
		int y = y0;
		l.judul(gelar, x0, y, lx1 - x0, l.gaya.aksen);
		y += 15;
		l.teks(Component.translatable(K + "kehormatan", honour), x0, y, lx1 - x0, l.gaya.teks);
		y += 11;
		if (t.getIntOr("ada", 0) == 0) {
			l.bungkus(Component.translatable(K + "belum_duel"), x0, y, lx1 - x0, l.gaya.teksLemah, y1 - y);
			return;
		}
		l.teks(Component.translatable(K + "skor", t.getIntOr("menang", 0), t.getIntOr("kalah", 0), t.getIntOr("seri", 0)), x0, y, lx1 - x0,
				l.gaya.teks);
		y += 11;
		Component lebih = Component.translatable(K + "lebih", t.getIntOr("sempurna", 0), t.getIntOr("penentu", 0), t.getIntOr("kunci", 0),
				t.getIntOr("rantai", 0));
		y += Math.round(l.bungkus(lebih, x0, y, lx1 - x0, l.gaya.teksLemah, 18.0F)) + 2;
		if (!kanan && last != null && y + 11 <= y1) {
			l.teks(Component.translatable(S + "last", last.title(), alasan(last)), x0, y, lx1 - x0, l.gaya.teksSamar);
			y += 11;
		}
		ListTag baru = t.getListOrEmpty("baru");
		if (baru.isEmpty() || y + 13 + ROW > y1) {
			return;
		}
		y += l.kepala(Component.translatable(K + "terbaru"), x0, y, lx1);
		daftarCatatan.gambar(l, "duel#baru", x0, y, lx1, y1, baru.size(), 11, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			CompoundTag r = baru.getCompoundOrEmpty(i);
			int hasil = r.getIntOr("hasil", 0);
			String key = hasil > 0 ? K + "baru.menang" : hasil < 0 ? K + "baru.kalah" : K + "baru.seri";
			int warna = hasil > 0 ? Palette.OK : hasil < 0 ? Palette.DANGER : lk.gaya.teksLemah;
			Brush.disc(lk.g, rx0 + 4.0F, ry0 + 4.0F, 2.5F, warna);
			lk.teks(Component.translatable(key, r.getStringOr("nama", "?"), r.getIntOr("hari", 0)), rx0 + 10, ry0, rx1 - rx0 - 10, lk.gaya.teks);
		}, null, null);
	}

	/** The last result as a result card (G65). */
	private void hasil(Lukis l, DuelPayloads.DuelResult r, int x0, int y0, int x1, int y1) {
		LocalPlayer player = minecraft.player;
		int me = player == null ? -1 : player.getId();
		int tingkat = r.winnerId() < 0 ? 1 : r.winnerId() == me ? 2 : r.loserId() == me ? 0 : 1;
		List<KartuHasil.Bagian> isi = new ArrayList<>();
		isi.add(KartuHasil.Bagian.of(Component.translatable(K + "hasil.alasan", alasan(r))));
		isi.add(KartuHasil.Bagian.of(Component.translatable(K + "hasil.pukulan", r.hitsA(), r.hitsB())));
		isi.add(KartuHasil.Bagian.of(Component.translatable(K + "hasil.sempurna", r.perfectA(), r.perfectB(), r.maxChain())));
		isi.add(KartuHasil.Bagian.of(Component.translatable(K + "hasil.lama", Brush.waktu(r.durationTicks()))));
		if (r.honourDelta() != 0) {
			isi.add(new KartuHasil.Bagian(null, Component.translatable(K + "hasil.kehormatan", (r.honourDelta() > 0 ? "+" : "") + r.honourDelta()),
					r.honourDelta() > 0 ? KartuHasil.BERHASIL : KartuHasil.GAGAL, 0));
		}
		isi.add(KartuHasil.Bagian.of(r.stakeLine()));
		kartu.gambar(l, "duel#hasil", Glyphs.Atlas.PIALA, r.title(), Component.translatable(K + "hasil.judul"), KartuHasil.Nilai.of(tingkat), null,
				isi, x0, y0, x1, y1);
	}

	private static Component alasan(DuelPayloads.DuelResult r) {
		return Component.translatable("kultivator.duel.ui.reason." + Math.max(0, Math.min(6, r.reason())));
	}
}
