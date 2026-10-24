package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sarang Kultivator Iblis (DESIGN 38.C.4): the hideout of demonic cultivators who kidnap mortals to refine them into
 * forbidden pills. One run per anchor: the false-stone cliff door ({@link Pintu}), the guard band of rogue cultivators
 * ({@link Band}, {@code RogueCultivators.spawnBand}, once a world day), the prison with its captives in cages
 * ({@link Sel}) and the Demon Elder in the master's chamber ({@link Bos}, a {@code TETUA_IBLIS} body of tier 4, the
 * lair's suggested realm + 1, realm-scaled foe). The suggested realm grows with the distance ring (Foundation, Golden
 * Core, Nascent Soul). Freed captives fire {@code bebaskan tawanan} (38ST's quest {@code tawanan_pulang}: escort them
 * home); five freed earn {@code tempat/pembebas}. With the Elder slain the lair stays quiet for {@link #SEPI_HARI} days.
 * <p>
 * Sumber ({@code kultivator.gua.sarang_iblis.inspired}): inspired by the hideouts of demonic-path cultivators who
 * kidnap mortals for refining, a common trope of xianxia novels; the design is the game's own.
 */
public abstract class SarangIblis extends GuaRun {

	public static final int SEPI_HARI = 7;

	protected SarangIblis(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	/** The lair's suggested realm (by the distance ring of its structure). */
	public Realm ranah() {
		return GuaAnchors.ranahIblis(anchor.origin() != null ? anchor.origin() : anchor.blockPosition());
	}

	/** The lair-wide state (shared by its anchors through {@link GuaData}, keyed by the lair's site). */
	protected CompoundTag lair() {
		return GuaData.get(level.getServer()).site(situs());
	}

	/** True while the lair is quiet after its Elder fell. */
	public boolean sepi() {
		long day = lair().getLongOr("bos_kalah", -1L);
		return day >= 0L && today() < day + SEPI_HARI;
	}

	// ================================================================== the cliff door (gua/iblis_pintu)

	/**
	 * Six false stones fill the 3 × 4 doorway in the cliff. A touch slides them aside when the visitor's realm is at
	 * least one below the lair's suggested realm (38.0 rule 13); they slide back once nobody stood near for 20 s.
	 */
	public static final class Pintu extends SarangIblis {

		private final List<PintuTebingEntity> stones = new ArrayList<>();
		private int sepiDetik;

		public Pintu(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		protected void spawn() {
			int px = data().getIntOr("px", 0);
			int pz = data().getIntOr("pz", -1);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 2; dy += 2) {
					PintuTebingEntity stone = make(GuaEntities.PINTU_TEBING, spot(px + dx, dy, pz), frame.yaw(0.0F));
					if (stone == null) {
						continue;
					}
					stone.ikat(this::sentuh);
					if (add(stone)) {
						stones.add(stone);
					}
				}
			}
		}

		private void sentuh(ServerPlayer player, PintuTebingEntity door) {
			Realm need = Realm.byOrdinal(Math.max(0, ranah().ordinal() - 1));
			if (!Cultivators.get(player).realm().atLeast(need)) {
				Msg.chat(player, Component.translatable("kultivator.gua.iblis.pintu_tolak").withStyle(ChatFormatting.GRAY));
				level.playSound(null, door.getX(), door.getY() + 1.0D, door.getZ(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.4F, 0.6F);
				return;
			}
			for (PintuTebingEntity stone : stones) {
				stone.buka();
			}
			Msg.chat(player, Component.translatable("kultivator.gua.iblis.pintu_buka").withStyle(ChatFormatting.DARK_RED));
		}

		@Override
		protected void tick(long now) {
			if (now % 20L != 0L || stones.isEmpty() || !stones.get(0).terbuka()) {
				return;
			}
			sepiDetik = playersWithin(12.0D).isEmpty() ? sepiDetik + 1 : 0;
			if (sepiDetik >= 20) {
				for (PintuTebingEntity stone : stones) {
					stone.tutup();
				}
				sepiDetik = 0;
			}
		}

		@Override
		protected void despawn() {
			stones.clear();
			super.despawn();
		}
	}

	// ================================================================== the guard band (gua/iblis_band)

	/** The guards of the blood-pool hall: a band of rogue cultivators, once per world day, while the Elder lives. */
	public static final class Band extends SarangIblis {

		public Band(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		protected void spawn() {
		}

		@Override
		protected void nearby(List<ServerPlayer> within) {
			if (sepi() || lair().getLongOr("band", -1L) == today()) {
				return;
			}
			for (ServerPlayer player : within) {
				if (player.position().distanceToSqr(centre()) > 14.0D * 14.0D) {
					continue;
				}
				int[] tiers = switch (GuaAnchors.ring(anchor.blockPosition())) {
					case 4 -> new int[]{2, 2, 3, 3};
					case 3 -> new int[]{1, 2, 2, 3};
					default -> new int[]{1, 1, 2};
				};
				int made = RogueCultivators.spawnBand(level, centre(), tiers, ranah().ordinal(), RogueCultivators.ROLE_CAMP, anchor.blockPosition(),
						cleared -> {
						});
				if (made > 0) {
					lair().putLong("band", today());
					GuaData.get(level.getServer()).touch();
					Msg.chat(player, Component.translatable("kultivator.gua.iblis.band").withStyle(ChatFormatting.RED));
				}
				return;
			}
		}

		@Override
		protected void tick(long now) {
		}
	}

	// ================================================================== the prison (gua/iblis_sel)

	/**
	 * Four cells behind iron bars, each closed by a cage door entity. 2-4 captives (mortal {@code TAWANAN} bodies in
	 * chains) wait inside, new ones each world day while the Elder lives. A cage gives only when no guard of the lair
	 * stands within 12 blocks; the freed captive bows and asks to be taken home (38ST dialog {@code tawanan_sapa}).
	 */
	public static final class Sel extends SarangIblis {

		private final Map<Integer, SangkarEntity> cages = new HashMap<>();
		private final Map<Integer, KultivatorNpc> captives = new HashMap<>();

		public Sel(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		/** The day-stamp of the freed cells ({@code bebas.<i>} = day). */
		private CompoundTag bebas() {
			return state().getCompoundOrEmpty("bebas");
		}

		private boolean dibebaskan(int i) {
			return bebas().getLongOr(Integer.toString(i), -1L) == today();
		}

		@Override
		protected void spawn() {
			List<int[]> sel = GuaAnchors.triples(data(), "sel");
			long s = situs();
			int n = sepi() ? 0 : Math.min(sel.size(), 2 + Math.floorMod((int) (s ^ today()), 3));
			for (int i = 0; i < sel.size(); i++) {
				int[] c = sel.get(i);
				SangkarEntity cage = make(GuaEntities.SANGKAR, spot(c[0], 0, c[1]), frame.yaw(c[2]));
				if (cage == null) {
					continue;
				}
				boolean open = i >= n || dibebaskan(i);
				cage.ikat(i, this::coba);
				if (add(cage)) {
					if (open) {
						cage.buka(true);
					}
					cages.put(i, cage);
				}
				if (!open) {
					int inner = c[1] < 0 ? c[1] - 3 : c[1] + 3;
					KultivatorNpc captive = GuaTubuh.buat(this, NpcRole.TAWANAN, frame.block(c[0], 0, inner), frame.yaw(c[2]), 1, Realm.MORTAL, null,
							GerakId.BERLUTUT_RANTAI, PropId.RANTAI_TANGAN);
					if (captive != null) {
						captives.put(i, captive);
					}
				}
			}
		}

		private void coba(ServerPlayer player, SangkarEntity cage) {
			if (penjagaDekat()) {
				Msg.chat(player, Component.translatable("kultivator.gua.iblis.sel_dijaga").withStyle(ChatFormatting.RED));
				level.playSound(null, cage.getX(), cage.getY() + 0.8D, cage.getZ(), SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.7F, 0.8F);
				return;
			}
			cage.buka(false);
			CompoundTag freed = bebas().copy();
			freed.putLong(Integer.toString(cage.sel()), today());
			state().put("bebas", freed);
			KultivatorNpc captive = captives.remove(cage.sel());
			if (captive == null || captive.isRemoved()) {
				return;
			}
			HidupApi.klip(captive, GerakId.HORMAT_DALAM, 0, 60);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, captive.getX(), captive.getY() + 1.0D, captive.getZ(), 8, 0.4D, 0.5D, 0.4D, 0.0D);
			CeritaApi.peristiwa(player, "bebaskan", "tawanan");
			int count = GuaData.get(level.getServer()).count(player.getUUID(), "bebas");
			GuaTubuh.ucap(player, captive, Gua.acak("kultivator.gua.iblis.tawanan_terima_kasih", 5), ChatFormatting.YELLOW);
			if (count >= 5) {
				Advancements.grant(player, "tempat/pembebas");
			}
			// the freed captive is no longer the prison's: it stays with the player (38ST's escort) and leaves on its own
			owned.remove(captive);
		}

		/** A guard of the lair (a living rogue cultivator or the Elder) within 12 blocks of the prison. */
		private boolean penjagaDekat() {
			AABB box = new AABB(centre(), centre()).inflate(12.0D, 4.0D, 12.0D);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Entity::isAlive)) {
				if (RogueCultivators.isRogue(mob) || mob instanceof KultivatorNpc npc && npc.role() == NpcRole.TETUA_IBLIS) {
					return true;
				}
			}
			return false;
		}

		@Override
		protected void tick(long now) {
			if (now % 100L != 0L) {
				return;
			}
			for (KultivatorNpc captive : captives.values()) {
				if (!captive.isRemoved()) {
					HidupApi.klip(captive, GerakId.BERLUTUT_RANTAI, PropId.RANTAI_TANGAN, 0);
				}
			}
			captives.values().removeIf(Entity::isRemoved);
		}

		@Override
		protected void despawn() {
			cages.clear();
			captives.clear();
			super.despawn();
		}
	}

	// ================================================================== the Demon Elder (gua/iblis_bos)

	/**
	 * The master's chamber: the Demon Elder meditates over a bowl of blood until a visitor comes within 10 blocks; then
	 * he threatens and fights. His fall quiets the lair for {@link #SEPI_HARI} days and leaves a demonic-path jade slip,
	 * a beast core and an old herb for the players near.
	 */
	public static final class Bos extends SarangIblis {

		private @Nullable KultivatorNpc elder;
		private boolean marah;

		public Bos(DuniaAnchorEntity anchor, ServerLevel level) {
			super(anchor, level);
		}

		@Override
		public double radius() {
			return 12.0D;
		}

		@Override
		protected void spawn() {
			if (sepi()) {
				return;
			}
			Realm realm = Realm.byOrdinal(Math.min(Realm.DEITY_TRANSFORMATION.ordinal(), ranah().ordinal() + 1));
			KultivatorNpc made = GuaTubuh.buat(this, NpcRole.TETUA_IBLIS, anchor.blockPosition(), frame.yaw(0.0F), 4, realm, Dao.SHADOW,
					GerakId.SEMEDI_DARAH, 0);
			if (made != null) {
				TechniquePower.markRealmFoe(made);
				elder = made;
			}
		}

		@Override
		protected void nearby(List<ServerPlayer> within) {
			KultivatorNpc e = elder;
			if (e == null || e.isRemoved()) {
				return;
			}
			for (ServerPlayer player : within) {
				if (player.distanceToSqr(e) > 10.0D * 10.0D || e.isHostileTo(player.getUUID())) {
					continue;
				}
				e.markHostile(player);
				if (!marah) {
					marah = true;
					// he leaves his seat to fight (stationary roles hold their spot otherwise)
					e.memory().remove("hold");
					HidupApi.klip(e, GerakId.ANCAM, 0, 40);
					GuaTubuh.ucap(player, e, Gua.acak("kultivator.gua.iblis.tetua_ancam", 4), ChatFormatting.DARK_RED);
					level.playSound(null, e.getX(), e.getY() + 1.0D, e.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.6F);
				}
			}
		}

		@Override
		protected void tick(long now) {
			KultivatorNpc e = elder;
			if (e == null || !e.isRemoved()) {
				return;
			}
			elder = null;
			if (e.getRemovalReason() != Entity.RemovalReason.KILLED) {
				return;
			}
			lair().putLong("bos_kalah", today());
			GuaData.get(level.getServer()).touch();
			RandomSource random = level.getRandom();
			int ring = GuaAnchors.ring(anchor.blockPosition());
			Vec3 at = e.position();
			level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 1.0D, at.z, 24, 0.5D, 0.8D, 0.5D, 0.05D);
			for (ServerPlayer player : playersWithin(24.0D)) {
				List<ItemStack> items = new ArrayList<>();
				items.add(RRewards.slip(random, ring >= 3 ? TechniqueGrade.XUAN : TechniqueGrade.HUANG, ring >= 4 ? TechniqueGrade.DI : TechniqueGrade.XUAN,
						Dao.SHADOW));
				items.add(RRewards.core(random, Math.max(1, Math.min(4, ring))));
				items.add(RRewards.herb(random, 100 * ring, 300 * ring));
				RRewards.giveAll(player, items, "kultivator.gua.iblis.rampasan");
				GuaPayload.stempel(player, "kultivator.gua.iblis.tetua_kalah", "滅", 0xC03040);
				Msg.chat(player, Component.translatable("kultivator.gua.iblis.tetua_kalah.chat").withStyle(ChatFormatting.GOLD));
			}
		}

		@Override
		protected void despawn() {
			elder = null;
			marah = false;
			super.despawn();
		}
	}

	/** The anchor of a lair kind ({@code iblis_*}) as its run. */
	static @Nullable GuaRun buat(DuniaAnchorEntity anchor, ServerLevel level) {
		return switch (anchor.what()) {
			case GuaAnchors.IBLIS_PINTU -> new Pintu(anchor, level);
			case GuaAnchors.IBLIS_BAND -> new Band(anchor, level);
			case GuaAnchors.IBLIS_SEL -> new Sel(anchor, level);
			case GuaAnchors.IBLIS_BOS -> new Bos(anchor, level);
			default -> null;
		};
	}
}
