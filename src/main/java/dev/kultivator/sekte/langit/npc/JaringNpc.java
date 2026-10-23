package dev.kultivator.sekte.langit.npc;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bodies of the NPC sky sects and their guests (DESIGN 37.7.6, 37.7.4 "Tamu"): unowned {@code RIVAL} disciple bodies
 * (the same entity as module D's sect members, made through the public {@code Disciples.spawnUnowned}) and the net that
 * keeps them on their island: a body that falls more than {@link #DROP} blocks below its home spot is set back on it with
 * a puff of cloud (the NPC side of {@code LangitJatuh}, locked to the anchor's spot). Never saved: every body is removed
 * when its anchor unloads or its visit ends. No block is touched.
 */
public final class JaringNpc {

	public static final double DROP = 12.0D;

	/** body → its home spot. */
	private static final Map<UUID, BlockPos> HOME = new HashMap<>();

	/**
	 * A sky sect body at {@code pos} with rank step {@code step}, the look of {@code seed}, named {@code name}; null when it
	 * could not be added.
	 */
	public static @Nullable DiscipleEntity spawn(ServerLevel level, Vec3 pos, long seed, int step, Component name, BlockPos home) {
		RandomSource random = level.getRandom();
		DiscipleData data = DiscipleData.fresh(random);
		data.setRank(Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, step))));
		for (Realm realm : Realm.values()) {
			if (!realm.isMortal() && realm.ordinal() <= data.realm().ordinal()) {
				data.setQuality(realm, BreakthroughQuality.NORMAL);
			}
		}
		data.setComprehension(20 + random.nextInt(40));
		data.setQi(DiscipleCultivation.maxQi(data));
		int skin = (int) Math.floorMod(seed, DiscipleEntity.SKIN_COUNT);
		boolean slim = Math.floorMod(seed >>> 3, 3) == 0;
		DiscipleEntity body = Disciples.spawnUnowned(level, pos, data, name, skin, slim, DiscipleEntity.Role.RIVAL, home);
		if (body == null) {
			return null;
		}
		body.setHealth(body.getMaxHealth());
		HOME.put(body.getUUID(), home.immutable());
		level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.6D, pos.z, 8, 0.3D, 0.4D, 0.3D, 0.01D);
		return body;
	}

	/** Removes a body in a puff of cloud (never a death, never loot). */
	public static void vanish(@Nullable DiscipleEntity body) {
		if (body == null) {
			return;
		}
		HOME.remove(body.getUUID());
		if (body.isRemoved()) {
			return;
		}
		if (body.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + 0.8D, body.getZ(), 12, 0.35D, 0.5D, 0.35D, 0.02D);
		}
		body.discard();
	}

	/** Once a second for the live bodies of {@code level}: a body far below its home is put back. */
	public static void second(ServerLevel level, List<DiscipleEntity> bodies) {
		for (DiscipleEntity body : bodies) {
			BlockPos home = HOME.get(body.getUUID());
			if (home == null || body.isRemoved() || body.level() != level) {
				continue;
			}
			if (body.getY() < home.getY() - DROP) {
				Vec3 back = Vec3.atBottomCenterOf(home);
				level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + 0.5D, body.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.02D);
				body.teleportTo(back.x, back.y, back.z);
				body.resetFallDistance();
				body.setDeltaMovement(Vec3.ZERO);
				level.sendParticles(ParticleTypes.CLOUD, back.x, back.y + 0.5D, back.z, 10, 0.3D, 0.3D, 0.3D, 0.02D);
			}
		}
	}

	static void forget(UUID body) {
		HOME.remove(body);
	}

	public static void clear() {
		HOME.clear();
	}

	private JaringNpc() {
	}
}
