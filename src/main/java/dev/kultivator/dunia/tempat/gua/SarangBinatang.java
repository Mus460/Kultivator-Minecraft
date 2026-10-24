package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sarang Binatang (DESIGN 38.C.4, anchor {@code gua/sarang_bos} on the nest of straw and bones): the den of a beast
 * pack. The pack lives in the den; the first player who comes within {@link #WAKE} blocks wakes it: an elite pack
 * leader (the elite system of 19.3.1, {@code EliteMonsters.makeElite}, tier 1-3 by the distance ring, realm-scaled foe)
 * and 2-4 common beasts. Taiga, snow and grove dens hold a wolf pack (angry at the intruder), the others spiders.
 * Killing the leader clears the den for {@link #KOSONG_HARI} world days: the players nearby share a beast core and an
 * old herb (offering material of beast evolution), and at {@link #ANAK} a stray cub (a baby wolf or fox, tamed the
 * vanilla way) stays behind. Spirit beasts of players gain XP ×1.25 here ({@link GuaTempat}, {@code SpiritBeasts}
 * hook). Advancement {@code tempat/sarang_bersih}. Never a block change: vanilla mobs with the elite hardening.
 */
public final class SarangBinatang extends GuaRun {

	public static final double WAKE = 24.0D;
	public static final int KOSONG_HARI = 3;
	public static final double ANAK = 0.10D;

	private @Nullable Mob leader;
	private final List<Mob> pack = new ArrayList<>();
	private boolean awake;
	private final int tier;
	private final boolean serigala;

	public SarangBinatang(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.tier = GuaAnchors.sarangTier(anchor.origin() != null ? anchor.origin() : anchor.blockPosition());
		var biome = level.getBiome(anchor.blockPosition());
		String path = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		this.serigala = biome.is(BiomeTags.IS_TAIGA) || path.contains("snowy") || path.contains("grove") || path.contains("taiga");
	}

	@Override
	public double radius() {
		return WAKE;
	}

	/** True while the den lies empty after its leader fell. */
	public boolean kosong() {
		long day = state().getLongOr("bersih", -1L);
		return day >= 0L && today() < day + KOSONG_HARI;
	}

	@Override
	protected void spawn() {
		awake = false;
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
		if (awake || kosong()) {
			return;
		}
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(centre()) <= WAKE * WAKE) {
				bangun(player);
				return;
			}
		}
	}

	private void bangun(ServerPlayer intruder) {
		awake = true;
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> kind = serigala ? EntityTypes.WOLF : EntityTypes.SPIDER;
		Realm realm = GuaAnchors.ranahSarang(anchor.blockPosition());
		int sarang = Math.max(2, Math.min(6, data().getIntOr("sarang", 3)));
		Mob boss = make(kind, centre(), random.nextFloat() * 360.0F);
		if (boss != null && add(boss)) {
			EliteMonsters.makeElite(boss, tier, null, realm.ordinal());
			TechniquePower.markRealmFoe(boss);
			boss.setPersistenceRequired();
			marah(boss, intruder);
			leader = boss;
		}
		int n = 2 + random.nextInt(3);
		for (int i = 0; i < n; i++) {
			double a = random.nextDouble() * Math.PI * 2.0D;
			Vec3 at = centre().add(Math.cos(a) * sarang, 0.0D, Math.sin(a) * sarang);
			EntityType<? extends Mob> minion = serigala ? EntityTypes.WOLF : EntityTypes.CAVE_SPIDER;
			Mob beast = make(minion, at, (float) Math.toDegrees(a));
			if (beast != null && add(beast)) {
				beast.setPersistenceRequired();
				marah(beast, intruder);
				pack.add(beast);
			}
		}
		level.playSound(null, centre().x, centre().y, centre().z, serigala ? SoundEvents.WOLF_GROWL_BABY.value() : SoundEvents.SPIDER_AMBIENT,
				SoundSource.HOSTILE, 1.4F, serigala ? 0.45F : 0.6F);
		Msg.chat(intruder, Gua.acak(serigala ? "kultivator.gua.sarang.bangun_serigala" : "kultivator.gua.sarang.bangun_laba", 3)
				.withStyle(ChatFormatting.RED));
	}

	private static void marah(Mob mob, ServerPlayer target) {
		mob.setTarget(target);
		if (mob instanceof Wolf wolf) {
			wolf.setPersistentAngerTarget(EntityReference.<LivingEntity>of(target.getUUID()));
			wolf.startPersistentAngerTimer();
		}
	}

	@Override
	protected void tick(long now) {
		if (!awake || now % 20L != 0L) {
			return;
		}
		Mob boss = leader;
		if (boss != null && boss.isRemoved()) {
			leader = null;
			if (boss.getRemovalReason() == Entity.RemovalReason.KILLED) {
				bersih(boss.position());
			}
			return;
		}
		// keep the pack on the intruders of its den
		List<ServerPlayer> near = playersWithin(WAKE);
		if (!near.isEmpty()) {
			for (Mob m : pack) {
				LivingEntity t = m.getTarget();
				if (!m.isRemoved() && (t == null || !t.isAlive())) {
					marah(m, near.get(0));
				}
			}
			if (boss != null && (boss.getTarget() == null || !boss.getTarget().isAlive())) {
				marah(boss, near.get(0));
			}
		}
	}

	/** The leader fell: the den is cleared for a while and the players near share its spoils. */
	private void bersih(Vec3 at) {
		state().putLong("bersih", today());
		RandomSource random = level.getRandom();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5D, at.z, 12, 1.0D, 0.5D, 1.0D, 0.0D);
		for (ServerPlayer player : playersWithin(WAKE + 8.0D)) {
			List<ItemStack> items = new ArrayList<>();
			items.add(RRewards.core(random, Math.min(3, tier)));
			items.add(RRewards.herb(random, 60 * tier, 200 * tier));
			RRewards.giveAll(player, items, "kultivator.gua.sarang.rampasan");
			GuaPayload.stempel(player, "kultivator.gua.sarang.bersih", "獸", 0x9FD87A);
			Advancements.grant(player, "tempat/sarang_bersih");
		}
		if (random.nextDouble() < ANAK) {
			EntityType<? extends Mob> young0 = serigala ? EntityTypes.WOLF : EntityTypes.FOX;
			Mob cub = make(young0, centre(), random.nextFloat() * 360.0F);
			if (cub instanceof AgeableMob young) {
				young.setBaby(true);
			}
			if (cub != null && level.addFreshEntity(cub)) {
				// a stray cub stays: it is not owned, so it is saved and tamed like any wild one
				for (ServerPlayer player : playersWithin(WAKE + 8.0D)) {
					Msg.chat(player, Component.translatable("kultivator.gua.sarang.anak").withStyle(ChatFormatting.AQUA));
				}
			}
		}
	}

	@Override
	protected void despawn() {
		leader = null;
		pack.clear();
		awake = false;
		super.despawn();
	}
}
