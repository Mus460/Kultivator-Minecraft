package dev.kultivator.formation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * One registered protective formation: the lodestone it is anchored to, its owner, whether it is switched on, the power
 * left (in ticks) and how many monsters it has driven back. Only data; the blocks are never touched.
 */
public final class Formation {

	public static final Codec<Formation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Formation::pos),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Formation::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Formation::ownerName),
			Codec.BOOL.optionalFieldOf("active", false).forGetter(Formation::active),
			Codec.INT.optionalFieldOf("power", 0).forGetter(Formation::power),
			Codec.INT.optionalFieldOf("repels", 0).forGetter(Formation::repels)
	).apply(instance, Formation::new));

	private final BlockPos pos;
	private final UUID owner;
	private String ownerName;
	private boolean active;
	private int power;
	private int repels;
	/** Not saved: entity id to the game time of its last push, so a monster pushed again and again counts once. */
	private final Map<Integer, Long> recentPushes = new HashMap<>();

	public Formation(BlockPos pos, UUID owner, String ownerName, boolean active, int power, int repels) {
		this.pos = pos.immutable();
		this.owner = owner;
		this.ownerName = ownerName;
		this.active = active;
		this.power = Math.max(0, Math.min(ProtectiveFormations.MAX_POWER, power));
		this.repels = Math.max(0, repels);
	}

	public BlockPos pos() {
		return pos;
	}

	public UUID owner() {
		return owner;
	}

	public boolean isOwner(ServerPlayer player) {
		return owner.equals(player.getUUID());
	}

	public String ownerName() {
		return ownerName;
	}

	public void setOwnerName(String name) {
		ownerName = name == null ? "" : name;
	}

	public boolean active() {
		return active;
	}

	public void setActive(boolean on) {
		active = on;
	}

	/** Power left in ticks. */
	public int power() {
		return power;
	}

	public void setPower(int ticks) {
		power = Math.max(0, Math.min(ProtectiveFormations.MAX_POWER, ticks));
	}

	public int repels() {
		return repels;
	}

	public void addRepels(int count) {
		repels = (int) Math.min(Integer.MAX_VALUE, (long) repels + Math.max(0, count));
	}

	/**
	 * Remembers that entity {@code id} was pushed at {@code now}; true when this is a new repel (never pushed, or not
	 * within the last {@code memory} ticks).
	 */
	boolean notePush(int id, long now, long memory) {
		Long last = recentPushes.put(id, now);
		return last == null || now - last > memory;
	}

	/** Forgets pushes older than {@code memory} ticks. */
	void forgetOldPushes(long now, long memory) {
		Iterator<Map.Entry<Integer, Long>> iterator = recentPushes.entrySet().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().getValue() > memory) {
				iterator.remove();
			}
		}
	}
}
