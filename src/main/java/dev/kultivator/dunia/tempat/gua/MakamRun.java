package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
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
 * Makam Warisan (DESIGN 38.C.4): the rooms of an inheritance tomb, one run per anchor. The tomb's Dao is fixed by its
 * site ({@link GuaAnchors#daoMakam}), its tier by the template (1 Foundation, 2 Nascent Soul, 3 Dao Union). The story
 * is 38ST's quest {@code warisan_<dao>_<tier>} (stages {@code kisah}, {@code hati}, {@code makam}, {@code pamit}): the
 * remnant soul tells the ancestor's life and sets the Heart trial of three illusions by dialog; the Strength trial
 * ({@link Kekuatan}: 2-4 guardian puppets) and the Comprehension trial ({@link Pemahaman}: five glyph plates in the
 * generating cycle) are here, and when both are passed the quest event {@code ujian makam:<dao>:<tier>} is fired. The
 * inheritance ({@link Aula}) is given once per player per tomb after the soul's farewell: a jade slip of the tomb's Dao,
 * an artifact of the tomb's tier and an old herb. Holding a Giok Warisan through the Heart trial adds +10 points on the
 * Giok's own inheritance roll at this tomb ({@code TempatApi} factor {@code warisan}). Followers wait at the seal
 * (38.0 rule 18); the inner rooms hold the player as {@code Sibuk} {@code tempat.warisan}.
 */
public abstract class MakamRun extends GuaRun {

	protected final int tier;

	protected MakamRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.tier = Math.max(1, Math.min(3, anchor.data().getIntOr("tier", 1)));
	}

	public int tier() {
		return tier;
	}

	public Dao dao() {
		return GuaAnchors.daoMakam(situs());
	}

	/** 38ST's quest of this tomb. */
	public String quest() {
		return "warisan_" + dao().id() + "_" + tier;
	}

	/** The player's current stage id of this tomb's quest ("" when never started). */
	public String tahap(ServerPlayer player) {
		return QuestTracker.simpul(player, quest());
	}

	/** True when the player stands at the stage of the Strength and Comprehension trials. */
	public boolean bolehUji(ServerPlayer player) {
		return QuestTracker.aktif(player, quest()) && "makam".equals(tahap(player));
	}

	public int bits(ServerPlayer player) {
		return GuaData.get(level.getServer()).makam(situs(), player.getUUID());
	}

	public int tambah(ServerPlayer player, int bits) {
		return GuaData.get(level.getServer()).addMakam(situs(), player.getUUID(), bits);
	}

	/** Fires the quest event once both inner trials are passed at the right stage. */
	public void periksa(ServerPlayer player) {
		int b = bits(player);
		if ((b & GuaData.UJIAN) != 0 || (b & GuaData.KUAT) == 0 || (b & GuaData.PAHAM) == 0 || !bolehUji(player)) {
			return;
		}
		tambah(player, GuaData.UJIAN);
		CeritaApi.peristiwa(player, "ujian", "makam:" + dao().id() + ":" + tier);
		GuaPayload.stempel(player, "kultivator.gua.makam.ujian_lulus", "承", 0xFFD54A);
		Msg.chat(player, Component.translatable("kultivator.gua.makam.ujian_lulus.chat").withStyle(ChatFormatting.GOLD));
	}

	/** True while {@code player} is held by an inner room's trial (Sibuk {@code tempat.warisan}). */
	public boolean menahan(ServerPlayer player) {
		return false;
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
		for (ServerPlayer player : within) {
			periksa(player);
		}
	}

	/** The remnant soul's body, memory {@code dao}/{@code tier} for 38ST's dialog condition {@code makam <dao> <tier>}. */
	protected @Nullable KultivatorNpc jiwa(Vec3 at, float yaw) {
		Realm realm = switch (tier) {
			case 3 -> Realm.IMMORTAL_ASCENSION;
			case 2 -> Realm.DEITY_TRANSFORMATION;
			default -> Realm.NASCENT_SOUL;
		};
		KultivatorNpc soul = GuaTubuh.buat(this, NpcRole.SISA_JIWA, net.minecraft.core.BlockPos.containing(at), yaw, 4, realm, dao(),
				GerakId.SIKAP_TANGAN_BELAKANG, 0);
		if (soul != null) {
			soul.memory().putString("dao", dao().id());
			soul.memory().putInt("tier", tier);
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0D, at.z, 16, 0.3D, 0.8D, 0.3D, 0.02D);
		}
		return soul;
	}

	// ================================================================== the seal (gua/makam_penghalang)

	/**
	 * The realm seal before the tomb's halls: a sealed stone gate (module R's {@code gerbang_segel} collider, drawn by
	 * its client), 3 × 4 blocks. A touch opens it when the player's realm reaches the tomb's suggested realm (38.0 rule
	 * 13) and nothing else holds him ({@code Sibuk}); the player's following disciples are told to wait here. It seals
	 * again after the halls stood empty for ten seconds.
	 */
	public static final class Penghalang extends MakamRun {

		private @Nullable ColliderEntity seal;
		private int empty;

		public Penghalang(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		protected void spawn() {
			int px = data().getIntOr("px", 0);
			int pz = data().getIntOr("pz", -2);
			Vec3 at = spot(px, 0, pz);
			ColliderEntity made = make(REntities.GERBANG_SEGEL, at, frame.yaw(0.0F));
			if (made == null) {
				return;
			}
			boolean turned = (frame.quarter() & 1) == 1;
			made.setup(turned ? 0.3F : 1.5F, 4.0F, turned ? 1.5F : 0.3F, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
					ColliderEntity.FLAG_GATE | ColliderEntity.FLAG_CLICK, tier);
			made.setOwned(true);
			made.setClickListener((player, part, attack) -> coba(player));
			if (add(made)) {
				seal = made;
			}
		}

		private void coba(ServerPlayer player) {
			ColliderEntity s = seal;
			if (s == null || s.isOpen()) {
				return;
			}
			Realm need = GuaAnchors.ranahMakam(tier);
			if (!Cultivators.get(player).realm().atLeast(need)) {
				Msg.chat(player, Component.translatable("kultivator.gua.makam.segel_tolak", need.displayName()).withStyle(ChatFormatting.RED));
				level.playSound(null, s.getX(), s.getY() + 2.0D, s.getZ(), SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.8F, 0.5F);
				return;
			}
			String busy = Sibuk.whyExcept(player, Gua.SIBUK_WARISAN);
			if (busy != null) {
				Msg.chat(player, Sibuk.text(busy).withStyle(ChatFormatting.RED));
				return;
			}
			s.open();
			level.playSound(null, s.getX(), s.getY() + 2.0D, s.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.playSound(null, s.getX(), s.getY() + 2.0D, s.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, 0.4F);
			level.sendParticles(ParticleTypes.ENCHANT, s.getX(), s.getY() + 2.0D, s.getZ(), 40, 1.2D, 1.5D, 1.2D, 0.6D);
			Msg.chat(player, Component.translatable("kultivator.gua.makam.segel_buka").withStyle(ChatFormatting.LIGHT_PURPLE));
			GuaTempat.parkir(player, centre());
		}

		@Override
		protected void tick(long now) {
			ColliderEntity s = seal;
			if (s == null || !s.isOpen() || now % 20L != 0L) {
				return;
			}
			boolean anyone = !playersWithin(64.0D).isEmpty();
			empty = anyone ? 0 : empty + 1;
			if (empty >= 10) {
				s.close();
				empty = 0;
			}
		}

		@Override
		protected void despawn() {
			seal = null;
			super.despawn();
		}
	}

	// ================================================================== the Heart trial room (gua/makam_hati)

	/**
	 * The Heart trial room: the remnant soul stands here; its dialog (38ST) tells the story and sets the three illusions.
	 * This run watches the quest stage: when it moves past {@code hati}, the trial is recorded (bit {@link GuaData#HATI})
	 * and, if a Giok Warisan was in hand, bit {@link GuaData#GIOK}.
	 */
	public static final class Hati extends MakamRun {

		private @Nullable KultivatorNpc soul;
		private final Map<UUID, String> terakhir = new HashMap<>();

		public Hati(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		public double radius() {
			return 9.0D;
		}

		@Override
		protected void spawn() {
			soul = jiwa(Vec3.atBottomCenterOf(anchor.blockPosition()), frame.yaw(0.0F));
		}

		@Override
		protected void nearby(List<ServerPlayer> within) {
			for (ServerPlayer player : within) {
				if (!inside(player)) {
					continue;
				}
				String now = tahap(player);
				String before = terakhir.put(player.getUUID(), now);
				boolean past = "makam".equals(now) || "pamit".equals(now) || CeritaApi.tahap(player, quest()) == TempatEvents.QUEST_SELESAI;
				if (past && (bits(player) & GuaData.HATI) == 0) {
					int add = GuaData.HATI;
					if (memegangGiok(player)) {
						add |= GuaData.GIOK;
						Msg.chat(player, Component.translatable("kultivator.gua.makam.giok_bergetar").withStyle(ChatFormatting.AQUA));
					}
					tambah(player, add);
					if ("hati".equals(before)) {
						GuaPayload.stempel(player, "kultivator.gua.makam.hati_lulus", "心", 0xC890FF);
					}
				}
				periksa(player);
			}
		}

		private static boolean memegangGiok(ServerPlayer player) {
			return player.getMainHandItem().is(InheritanceTrials.GIOK_WARISAN) || player.getOffhandItem().is(InheritanceTrials.GIOK_WARISAN);
		}

		@Override
		public boolean menahan(ServerPlayer player) {
			return inside(player) && "hati".equals(terakhir.getOrDefault(player.getUUID(), ""));
		}

		@Override
		protected void tick(long now) {
			KultivatorNpc s = soul;
			if (s != null && s.isRemoved()) {
				soul = null;
			}
			if (s != null && now % 40L == 0L) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.getX(), s.getY() + 0.2D, s.getZ(), 2, 0.25D, 0.1D, 0.25D, 0.005D);
			}
		}

		@Override
		protected void despawn() {
			soul = null;
			terakhir.clear();
			super.despawn();
		}
	}

	// ================================================================== the Strength trial room (gua/makam_kekuatan)

	/**
	 * The Strength trial: 2, 3 or 4 guardian puppets (tier 1, 2, 3) sit on their plinths. A player at the {@code makam}
	 * stage who steps in wakes them one after another; when all are down every such player in the room gains bit
	 * {@link GuaData#KUAT}. An emptied room seats the puppets again, whole.
	 */
	public static final class Kekuatan extends MakamRun {

		private final List<BonekaPenjagaEntity> puppets = new ArrayList<>();
		private final Set<UUID> peserta = new HashSet<>();
		private boolean fight;
		private int sepi;
		private int bangkitKe = -1;

		public Kekuatan(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		public double radius() {
			return 9.5D;
		}

		@Override
		protected void spawn() {
			seat();
		}

		private void seat() {
			for (BonekaPenjagaEntity p : puppets) {
				if (!p.isRemoved()) {
					p.discard();
				}
			}
			puppets.clear();
			List<int[]> kursi = GuaAnchors.pairs(data(), "kursi");
			int n = Math.min(kursi.size(), tier + 1);
			Vec3 mid = centre();
			for (int i = 0; i < n; i++) {
				int[] k = kursi.get(i);
				Vec3 at = spot(k[0], 0, k[1]);
				Vec3 dir = mid.subtract(at);
				float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				BonekaPenjagaEntity puppet = make(GuaEntities.BONEKA_PENJAGA, at, yaw);
				if (puppet == null) {
					continue;
				}
				puppet.setup(tier);
				puppet.setYBodyRot(yaw);
				if (add(puppet)) {
					puppets.add(puppet);
				}
			}
			fight = false;
			bangkitKe = -1;
			peserta.clear();
		}

		@Override
		public boolean menahan(ServerPlayer player) {
			return fight && peserta.contains(player.getUUID()) && inside(player);
		}

		/** {@code uji tempat_gua penjaga}: wakes the puppets now (seating them first when the room was cleared). */
		void uji(ServerPlayer player) {
			puppets.removeIf(Entity::isRemoved);
			if (puppets.isEmpty()) {
				seat();
			}
			peserta.add(player.getUUID());
			fight = true;
			bangkitKe = 0;
		}

		@Override
		protected void nearby(List<ServerPlayer> within) {
			super.nearby(within);
			if (fight) {
				return;
			}
			for (ServerPlayer player : within) {
				if (!inside(player)) {
					continue;
				}
				if (!bolehUji(player)) {
					if ((bits(player) & GuaData.KUAT) == 0 && Gua.sekali(player, "kekuatan_tolak:" + situs(), 1200)) {
						Msg.chat(player, Component.translatable("kultivator.gua.makam.boneka_diam").withStyle(ChatFormatting.GRAY));
					}
					continue;
				}
				if ((bits(player) & GuaData.KUAT) != 0) {
					continue;
				}
				fight = true;
				bangkitKe = 0;
				Msg.chat(player, Component.translatable("kultivator.gua.makam.boneka_bangkit").withStyle(ChatFormatting.RED));
				level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0F, 0.6F);
				break;
			}
		}

		@Override
		protected void tick(long now) {
			if (!fight) {
				return;
			}
			if (bangkitKe >= 0 && now % 10L == 0L) {
				if (bangkitKe < puppets.size()) {
					puppets.get(bangkitKe).bangkit();
					bangkitKe++;
				} else {
					bangkitKe = -1;
				}
			}
			if (now % 20L != 0L) {
				return;
			}
			List<ServerPlayer> inRoom = new ArrayList<>();
			for (ServerPlayer p : playersWithin(radius())) {
				inRoom.add(p);
				if (bolehUji(p)) {
					peserta.add(p.getUUID());
				}
			}
			boolean allDown = true;
			for (BonekaPenjagaEntity p : puppets) {
				allDown &= p.isRemoved() || !p.isAlive();
			}
			if (allDown && !puppets.isEmpty()) {
				for (ServerPlayer p : inRoom) {
					if (peserta.contains(p.getUUID()) && bolehUji(p)) {
						tambah(p, GuaData.KUAT);
						GuaPayload.stempel(p, "kultivator.gua.makam.kuat_lulus", "力", 0xFF8A50);
						periksa(p);
					}
				}
				level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
				fight = false;
				puppets.clear();
				sepi = 0;
				return;
			}
			if (inRoom.isEmpty()) {
				if (++sepi >= 5) {
					// the room stood empty: the puppets sit again, whole
					for (BonekaPenjagaEntity p : puppets) {
						if (!p.isRemoved()) {
							p.duduk();
						}
					}
					fight = false;
					peserta.clear();
					sepi = 0;
				}
			} else {
				sepi = 0;
			}
		}

		@Override
		protected void despawn() {
			puppets.clear();
			peserta.clear();
			fight = false;
			super.despawn();
		}
	}

	// ================================================================== the Comprehension trial room (gua/makam_pemahaman)

	/**
	 * The Comprehension trial: five glyph plates in a ring. One is set in stone (it glows faintly and never turns); each
	 * use turns another to the next element. The ring is solved when, going round, every plate's element gives birth to
	 * the next one (the generating cycle wood → fire → earth → metal → water → wood); then the glyphs burn and every
	 * player at the {@code makam} stage in the room gains bit {@link GuaData#PAHAM}. A new visit scrambles the ring again.
	 */
	public static final class Pemahaman extends MakamRun {

		/** The element each element gives birth to (indexes of {@link LempengGlifEntity#FIRE} .. {@link LempengGlifEntity#EARTH}). */
		private static final int[] MELAHIRKAN = {LempengGlifEntity.EARTH, LempengGlifEntity.WOOD, LempengGlifEntity.FIRE,
				LempengGlifEntity.WATER, LempengGlifEntity.METAL};
		private final List<LempengGlifEntity> plates = new ArrayList<>();
		private final Set<UUID> dibisik = new HashSet<>();
		private boolean solved;

		public Pemahaman(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		public double radius() {
			return 8.5D;
		}

		@Override
		protected void spawn() {
			List<int[]> sockets = GuaAnchors.pairs(data(), "lempeng");
			if (sockets.size() < 5) {
				return;
			}
			long s = situs();
			int fixed = Math.floorMod((int) (s >>> 11), 5);
			int start = Math.floorMod((int) (s >>> 17), 5);
			RandomSource random = level.getRandom();
			int[] want = new int[5];
			want[fixed] = start;
			for (int k = 1; k < 5; k++) {
				int prev = want[Math.floorMod(fixed + k - 1, 5)];
				want[Math.floorMod(fixed + k, 5)] = MELAHIRKAN[prev];
			}
			for (int k = 0; k < 5; k++) {
				int[] at = sockets.get(k);
				LempengGlifEntity plate = make(GuaEntities.LEMPENG_GLIF, spot(at[0], 0, at[1]), frame.yaw(0.0F));
				if (plate == null) {
					continue;
				}
				int shown = k == fixed ? start : Math.floorMod(want[k] + 1 + random.nextInt(4), LempengGlifEntity.ELEMENTS);
				plate.setup(k, shown, k == fixed);
				plate.ikat(this::putar);
				if (add(plate)) {
					plates.add(plate);
				}
			}
			solved = false;
		}

		private void putar(ServerPlayer player, LempengGlifEntity plate) {
			if (solved) {
				return;
			}
			if (!bolehUji(player)) {
				Msg.actionBar(player, Component.translatable("kultivator.gua.makam.lempeng_diam").withStyle(ChatFormatting.GRAY));
				return;
			}
			if (plate.tetap()) {
				Msg.actionBar(player, Component.translatable("kultivator.gua.makam.lempeng_tetap",
						Component.translatable("kultivator.gua.unsur." + plate.jenis())).withStyle(ChatFormatting.GRAY));
				return;
			}
			plate.putarSatu();
			level.playSound(null, plate.getX(), plate.getY(), plate.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 1.3F);
			Msg.actionBar(player, Component.translatable("kultivator.gua.makam.lempeng_kini",
					Component.translatable("kultivator.gua.unsur." + plate.jenis())).withStyle(ChatFormatting.YELLOW));
			if (cocok()) {
				solved = true;
				for (LempengGlifEntity p : plates) {
					p.nyala(true);
				}
				level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
				level.sendParticles(ParticleTypes.ENCHANT, centre().x, centre().y + 1.0D, centre().z, 80, 3.0D, 0.6D, 3.0D, 0.8D);
				for (ServerPlayer p : playersWithin(radius())) {
					if (bolehUji(p)) {
						tambah(p, GuaData.PAHAM);
						GuaPayload.stempel(p, "kultivator.gua.makam.paham_lulus", "悟", 0x7FE8FF);
						periksa(p);
					}
				}
			}
		}

		private boolean cocok() {
			if (plates.size() < 5) {
				return false;
			}
			LempengGlifEntity[] ring = new LempengGlifEntity[5];
			for (LempengGlifEntity p : plates) {
				if (p.indeks() >= 0 && p.indeks() < 5) {
					ring[p.indeks()] = p;
				}
			}
			for (int k = 0; k < 5; k++) {
				LempengGlifEntity a = ring[k];
				LempengGlifEntity b = ring[(k + 1) % 5];
				if (a == null || b == null || MELAHIRKAN[a.jenis()] != b.jenis()) {
					return false;
				}
			}
			return true;
		}

		@Override
		protected void nearby(List<ServerPlayer> within) {
			super.nearby(within);
			for (ServerPlayer player : within) {
				if (inside(player) && bolehUji(player) && dibisik.add(player.getUUID())) {
					LempengGlifEntity start = null;
					for (LempengGlifEntity p : plates) {
						if (p.tetap()) {
							start = p;
						}
					}
					if (start != null) {
						Msg.chat(player, Component.translatable("kultivator.gua.makam.lempeng_petunjuk",
								Component.translatable("kultivator.gua.unsur." + start.jenis())).withStyle(ChatFormatting.AQUA));
					}
				}
			}
		}

		@Override
		public boolean menahan(ServerPlayer player) {
			return !solved && inside(player) && bolehUji(player);
		}

		@Override
		protected void tick(long now) {
			plates.removeIf(Entity::isRemoved);
		}

		@Override
		protected void despawn() {
			plates.clear();
			dibisik.clear();
			super.despawn();
		}
	}

	// ================================================================== the tomb hall (gua/makam_aula)

	/**
	 * The tomb hall: the ancestor's remnant soul sits before its own bones and says farewell (38ST {@code _pamit}
	 * dialog). Once a player finished the tomb's quest, the inheritance is given here, once per player per tomb.
	 */
	public static final class Aula extends MakamRun {

		private @Nullable KultivatorNpc soul;
		private final Set<UUID> dupa = new HashSet<>();

		public Aula(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		public double radius() {
			return 13.0D;
		}

		@Override
		protected void spawn() {
			soul = jiwa(Vec3.atBottomCenterOf(anchor.blockPosition()), frame.yaw(0.0F));
		}

		@Override
		protected void nearby(List<ServerPlayer> within) {
			super.nearby(within);
			for (ServerPlayer player : within) {
				if (dupa.add(player.getUUID())) {
					ListTag isi = new ListTag();
					isi.add(GuaPayload.hiasan("ASAP_DUPA", spot(0, 0, -1), 0.0F, 0.0F));
					GuaPayload.hias(player, "gua/makam/" + situs(), isi);
				}
				if (inside(player) && CeritaApi.tahap(player, quest()) == TempatEvents.QUEST_SELESAI
						&& (bits(player) & GuaData.HADIAH) == 0) {
					beri(player);
				}
			}
		}

		/** The inheritance (once per player per tomb). */
		public void beri(ServerPlayer player) {
			tambah(player, GuaData.HADIAH | GuaData.HATI | GuaData.KUAT | GuaData.PAHAM | GuaData.UJIAN);
			RandomSource random = player.getRandom();
			List<ItemStack> items = new ArrayList<>();
			TechniqueGrade from = switch (tier) {
				case 3 -> TechniqueGrade.DI;
				case 2 -> TechniqueGrade.XUAN;
				default -> TechniqueGrade.HUANG;
			};
			TechniqueGrade to = switch (tier) {
				case 3 -> TechniqueGrade.TIAN;
				case 2 -> TechniqueGrade.DI;
				default -> TechniqueGrade.XUAN;
			};
			items.add(RRewards.slip(random, from, to, dao()));
			items.add(ArtifactItem.create(bentuk(), tier, tier >= 3 ? 1 + random.nextInt(2) : random.nextInt(2), 0, dao().affinity(), null));
			items.add(RRewards.herb(random, 100 * tier * tier, 300 * tier * tier));
			if (tier >= 2) {
				items.add(RRewards.core(random, tier));
			}
			RRewards.giveAll(player, items, "kultivator.gua.makam.warisan_diterima");
			KultivatorNpc s = soul;
			Vec3 at = s != null ? s.position() : centre();
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0D, at.z, 60, 0.6D, 1.2D, 0.6D, 0.08D);
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0D, at.z, 30, 0.6D, 1.2D, 0.6D, 0.04D);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
			GuaPayload.stempel(player, "kultivator.gua.makam.pewaris", "承", 0xFFD54A);
			if (s != null) {
				HidupApi.klip(s, GerakId.HORMAT_DALAM, 0, 60);
				GuaTubuh.ucap(player, s, Gua.acak("kultivator.gua.makam.pesan." + tier, 3), ChatFormatting.LIGHT_PURPLE);
			}
			Advancements.grant(player, "tempat/pewaris_makam");
		}

		/** The artifact form of the tomb's Dao (sword: a blade, space: a bell, body: a cauldron-shield, life: an umbrella). */
		private String bentuk() {
			return switch (dao()) {
				case SWORD, LIGHTNING -> ArtifactItem.PEDANG;
				case SPACE, SHADOW -> ArtifactItem.LONCENG;
				case BODY, FIRE -> ArtifactItem.WAJAN;
				default -> ArtifactItem.PAYUNG;
			};
		}

		@Override
		protected void tick(long now) {
			KultivatorNpc s = soul;
			if (s != null && s.isRemoved()) {
				soul = null;
			}
		}

		@Override
		protected void despawn() {
			soul = null;
			dupa.clear();
			GuaPayload.hapusHias(level, centre(), "gua/makam/" + situs());
			super.despawn();
		}
	}
}
