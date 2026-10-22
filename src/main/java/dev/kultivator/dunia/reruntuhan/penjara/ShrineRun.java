package dev.kultivator.dunia.reruntuhan.penjara;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.RItems;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kuil Segel on the mountain (anchor {@code penjara/kuil}, DESIGN 34.5.6): five seal talismans on the five pillars
 * (one {@link TekaPartEntity#TALISMAN} each, the client flutters the paper in 4-tick {@code DisplayKey} frames) and the
 * sealed well. A click on a talisman opens a warning card on the client; only a confirmed pull ({@code RNet.Action
 * "cabut"}) tears it: integrity −5, a Sobekan Segel for the puller (the temptation of Marshal Hong Xin). Red qi leaks
 * from the well as the seal weakens. While the seal lies broken a few lesser demons roam the shrine at night.
 */
public final class ShrineRun extends AnchorRun {

	public static final int ROAMERS = 3;
	public static final double ROAM = 48.0D;
	private final TekaPartEntity[] talismans = new TekaPartEntity[5];
	private final List<IblisKecilEntity> roamers = new ArrayList<>();

	public ShrineRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	public Prisons.Seal seal() {
		return Prisons.seal(level, anchor);
	}

	public long prisonId() {
		return Prisons.idOf(anchor);
	}

	@Override
	public double radius() {
		return 64.0D;
	}

	@Override
	protected void spawn() {
		Prisons.Seal seal = seal();
		int n = Frame.triples(data(), "pillars");
		for (int i = 0; i < Math.min(5, n); i++) {
			int[] off = Frame.triple(data(), "pillars", i);
			Vec3 pillar = frame.bottom(off[0], off[1], off[2]);
			// the talisman hangs on the pillar's face toward the well
			Vec3 face = frame.dir(-off[0], -off[2]);
			Vec3 dir = Math.abs(face.x) > Math.abs(face.z) ? new Vec3(Math.signum(face.x), 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, Math.signum(face.z));
			Vec3 at = pillar.add(dir.scale(0.56D));
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			TekaPartEntity made = make(REntities.SEGEL_JIMAT, at, yaw);
			if (made == null) {
				continue;
			}
			made.setRole(TekaPartEntity.TALISMAN);
			made.setCenter(centre());
			made.setArg(i);
			made.setState((seal.pulled() & (1 << i)) != 0 ? TekaPartEntity.LIT : TekaPartEntity.IDLE);
			final int index = i;
			made.setListener((player, part, attack) -> warn(player, index));
			if (add(made)) {
				talismans[i] = made;
			}
		}
	}

	private void warn(ServerPlayer player, int n) {
		Prisons.Seal seal = seal();
		if ((seal.pulled() & (1 << n)) != 0 || seal.broken()) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.jimat.gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "warn");
		tag.putInt("n", n);
		tag.putInt("integrity", seal.integrity());
		tag.putInt("pulled", Integer.bitCount(seal.pulled()));
		tag.putInt("king", seal.king());
		RNet.send(player, tag);
		Voice.inner(player, "demon.tempt", Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[seal.king()]));
	}

	/** A confirmed pull of talisman {@code n}. */
	void pull(ServerPlayer player, int n) {
		if (n < 0 || n > 4) {
			return;
		}
		TekaPartEntity talisman = talismans[n];
		Prisons.Seal seal = seal();
		if (talisman == null || talisman.position().distanceToSqr(player.position()) > 6.0D * 6.0D || (seal.pulled() & (1 << n)) != 0 || seal.broken()) {
			return;
		}
		seal.pulled |= 1 << n;
		talisman.setState(TekaPartEntity.LIT);
		RRewards.give(player, new ItemStack(RItems.SOBEKAN_SEGEL));
		dev.kultivator.util.Advancements.grant(player, "dunia/hong_xin");
		RFx.at(level, talisman.position(), RFx.TALISMAN_TORN, n, 0, 1.0F);
		level.playSound(null, talisman.getX(), talisman.getY(), talisman.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.5F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.5F, 0.6F);
		Voice.heavenNear(level, centre(), "demon.laugh", 32.0D);
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.jimat.torn", Integer.bitCount(seal.pulled()), 5)
				.withStyle(ChatFormatting.DARK_RED));
		Prisons.change(level, seal, seal.integrity - Prisons.PULL_COST);
		if (Integer.bitCount(seal.pulled()) >= 5 && !seal.broken()) {
			Prisons.breakSeal(level, seal, false);
		}
	}

	@Override
	protected void tick(long now) {
		Prisons.Seal seal = seal();
		// the well leaks red qi as the seal weakens
		if (now % 20L == 0L) {
			int integrity = seal.integrity();
			if (seal.broken() || integrity < 40) {
				RFx.at(level, centre().add(0.0D, -1.0D, 0.0D), RFx.SEAL_LEAK, seal.broken() ? 2 : integrity < 15 ? 1 : 0, integrity, 1.0F);
			}
		}
		for (int i = 0; i < 5; i++) {
			TekaPartEntity talisman = talismans[i];
			if (talisman != null) {
				talisman.setState((seal.pulled() & (1 << i)) != 0 || seal.broken() ? TekaPartEntity.LIT : TekaPartEntity.IDLE);
			}
		}
		roam(seal, now);
	}

	/** While broken, up to three lesser demons roam within 48 blocks of the shrine at night. */
	private void roam(Prisons.Seal seal, long now) {
		roamers.removeIf(e -> e.isRemoved() || !e.isAlive());
		boolean night = level.isDarkOutside();
		if (!seal.broken() || seal.calm(now) || !night) {
			for (IblisKecilEntity demon : roamers) {
				demon.setBrain(null);
				demon.discard();
			}
			roamers.clear();
			return;
		}
		if (now % 100L == 0L && roamers.size() < ROAMERS && !playersWithin(48.0D).isEmpty()) {
			double a = level.getRandom().nextDouble() * Math.PI * 2.0D;
			Vec3 at = centre().add(Math.cos(a) * 6.0D, 0.0D, Math.sin(a) * 6.0D);
			IblisKecilEntity demon = make(REntities.IBLIS_KECIL, at, (float) Math.toDegrees(a));
			if (demon == null) {
				return;
			}
			demon.setKind(level.getRandom().nextInt(3));
			demon.setTier(2);
			demon.setBrain(new LesserBrain(centre(), ROAM, 0.12D));
			if (add(demon)) {
				roamers.add(demon);
				RFx.at(level, at.add(0.0D, 1.0D, 0.0D), RFx.SOUL_APPEAR, 0xC02020, 1, 1.0F);
			}
		}
	}

	@Override
	protected void despawn() {
		for (IblisKecilEntity demon : roamers) {
			demon.setBrain(null);
		}
		roamers.clear();
		for (int i = 0; i < 5; i++) {
			talismans[i] = null;
		}
		super.despawn();
	}

	/** The talisman part {@code n}, or null. */
	public @Nullable TekaPartEntity talisman(int n) {
		return n >= 0 && n < 5 ? talismans[n] : null;
	}

	/** Pulls without the warning ({@code uji penjara cabut}). */
	public void testPull(ServerPlayer player, int n) {
		TekaPartEntity talisman = talisman(n);
		if (talisman != null) {
			player.teleportTo(talisman.getX(), talisman.getY(), talisman.getZ());
		}
		pull(player, n);
	}

	/** The re-sealed prison gives birth to a treasure at its shrine (T3, DESIGN 34.6 R, through TreasureService). */
	public static void birthAt(ServerLevel level, Vec3 at) {
		dev.kultivator.dunia.api.DuniaApi.treasures().spawn(level, net.minecraft.core.BlockPos.containing(at).above(2), 3, "pusaka");
	}

	static void stop(RCreature c) {
		c.setBrain(null);
		c.discard();
	}
}
