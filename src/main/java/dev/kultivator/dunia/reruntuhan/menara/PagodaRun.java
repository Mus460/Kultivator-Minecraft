package dev.kultivator.dunia.reruntuhan.menara;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.RNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The gate of the nine-tier pagoda in the overworld (anchor {@code menara/gerbang}, DESIGN 34.5.7): the bronze doors of
 * the ground hall are a Gerbang Segel (clickable); a click takes the party (up to four within six blocks) to the lobby
 * of the tower's dimension. Every five seconds the players within 192 blocks learn where the pagoda stands, so their
 * client can draw the cloud ring that hides its top ({@code op: "luar"}).
 */
public final class PagodaRun extends AnchorRun {

	/** Height of the pagoda above the gate anchor (the stacked tiers, DESIGN 34.5.7). */
	public static final int HEIGHT = 112;

	public PagodaRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return 192.0D;
	}

	@Override
	protected void spawn() {
		int w = data().getIntOr("w", 5);
		int h = data().getIntOr("h", 6);
		Vec3 facing = frame.dir(0.0D, 1.0D);
		boolean alongZ = Math.abs(facing.x) > Math.abs(facing.z);
		// the doors stand against the north wall: the collider sits one block in front of the anchor's wall
		ColliderEntity made = make(REntities.GERBANG_SEGEL, frame.bottom(0, 0, -1), frame.yaw(0.0F));
		if (made == null) {
			return;
		}
		made.setup(alongZ ? 0.35F : w / 2.0F, h, alongZ ? w / 2.0F : 0.35F, Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState(),
				ColliderEntity.FLAG_GATE | ColliderEntity.FLAG_TOWER | ColliderEntity.FLAG_CLICK, Glyphs.DOOR_TOWER);
		made.setOwned(true);
		made.setClickListener((player, part, attack) -> enter(player));
		add(made);
	}

	private void enter(ServerPlayer player) {
		RFx.at(level, centre().add(0.0D, 2.0D, 0.0D), RFx.BAND_GATE, 0, 0, 2.0F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.3F, 1.4F);
		Menara.enterFromPagoda(player, centre());
	}

	@Override
	protected void tick(long now) {
		if (now % 100L != 0L) {
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "luar");
		tag.putDouble("x", centre().x);
		tag.putDouble("y", centre().y);
		tag.putDouble("z", centre().z);
		tag.putInt("h", HEIGHT);
		RNet.near(level, centre(), 192.0D, tag);
	}
}
