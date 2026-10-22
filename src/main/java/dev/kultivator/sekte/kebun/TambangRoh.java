package dev.kultivator.sekte.kebun;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.flow.Ledger;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.kebun.Kebun.Aula;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Tambang Roh itself (DESIGN 36.2.10): built when the {@code sx_kebun_tambang} component stands (its
 * {@code tambang_mulut} anchor exists) or the test switch is on, depth level 1-9 kept in {@code sekte_kebun.tambang.lvl}.
 * A deeper level costs {@code tambangPK × level} PK from the hall pool and {@code 16 × level} Kayu and {@code 24 × level}
 * Batu from the Gudang Bahan (the building materials deposited for the sect, taken through the existing ledger). Depth is
 * data: no block is dug.
 */
public final class TambangRoh {

	public static final int MAX_LEVEL = 9;
	/** From this depth on every shift risks a cave-in ({@code runtuhPersen}). */
	public static final int DANGER_LEVEL = 5;

	/** The live {@code tambang} compound of the section. */
	static CompoundTag mine(CompoundTag sec) {
		CompoundTag mine = sec.getCompound("tambang").orElse(null);
		if (mine == null) {
			mine = new CompoundTag();
			sec.put("tambang", mine);
		}
		return mine;
	}

	/** The live miner slots ({@code "1".."6" → {soul, prog, today, total}}). */
	static CompoundTag slotsLive(CompoundTag sec) {
		CompoundTag mine = mine(sec);
		CompoundTag slots = mine.getCompound("slots").orElse(null);
		if (slots == null) {
			slots = new CompoundTag();
			mine.put("slots", slots);
		}
		return slots;
	}

	/** The miner slots, read only (an empty tag when none). */
	static CompoundTag miners(CompoundTag sec) {
		return sec.getCompoundOrEmpty("tambang").getCompoundOrEmpty("slots");
	}

	/** True when the mine stands (or the test switch {@code uji_tambang} is on). */
	public static boolean built(UUID owner) {
		return owner != null && (EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_MOUTH) != null
				|| Kebun.section(owner).getBooleanOr("uji_tambang", false));
	}

	/** The depth level 1-9, 0 while the mine is not built. */
	public static int level(UUID owner) {
		if (!built(owner)) {
			return 0;
		}
		return Math.max(1, Math.min(MAX_LEVEL, Kebun.section(owner).getCompoundOrEmpty("tambang").getIntOr("lvl", 1)));
	}

	static void setLevel(CompoundTag sec, int level) {
		mine(sec).putInt("lvl", Math.max(1, Math.min(MAX_LEVEL, level)));
	}

	/** The mouth (in front of the adit) in the world, or null without the building. */
	static @Nullable BlockPos mouth(UUID owner) {
		return EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_MOUTH);
	}

	/** The cost of the step to {@code next}: {PK, Kayu units, Batu units}. */
	public static int[] cost(int next) {
		int[] materials = TabelHasil.materials(next);
		return new int[]{KebunConfig.get().tambangPK * next, materials[0], materials[1]};
	}

	/** True when the hall pool holds {@code pk} (probed by taking and giving back: the pool has no public reader). */
	static boolean poolHas(UUID owner, int pk) {
		if (pk <= 0) {
			return true;
		}
		if (Halls.spendPool(owner, pk)) {
			Halls.addPool(owner, pk);
			return true;
		}
		return false;
	}

	/** Units of {@code cat} in the Gudang Bahan. */
	static long units(UUID owner, Bahan cat) {
		return Ledger.INSTANCE.centi(owner, cat) / Bahan.CENTI;
	}

	/** Deepens the mine by one level, paying from the pool and the Gudang; null on success, else why not. */
	static @Nullable Component deepen(Aula aula, @Nullable ServerPlayer player) {
		UUID owner = aula.owner();
		if (!built(owner)) {
			return Component.translatable("kultivator.sekte.kebun.mine.not_built");
		}
		int level = level(owner);
		if (level >= MAX_LEVEL) {
			return Component.translatable("kultivator.sekte.kebun.mine.deepest");
		}
		int next = level + 1;
		int[] cost = cost(next);
		if (units(owner, Bahan.KAYU) < cost[1] || units(owner, Bahan.BATU) < cost[2]) {
			return Component.translatable("kultivator.sekte.kebun.mine.no_materials", cost[1], cost[2]);
		}
		if (!Halls.spendPool(owner, cost[0])) {
			return Component.translatable("kultivator.sekte.kebun.mine.no_pk", cost[0]);
		}
		Ledger.INSTANCE.take(owner, Bahan.KAYU, (long) cost[1] * Bahan.CENTI);
		Ledger.INSTANCE.take(owner, Bahan.BATU, (long) cost[2] * Bahan.CENTI);
		setLevel(aula.sec(), next);
		Halls.setDirty();
		Component line = Component.translatable("kultivator.sekte.kebun.log.deepen", next, TabelHasil.slots(next));
		Kebun.note(owner, line, true);
		Kebun.tell(player, line, ChatFormatting.GOLD);
		BlockPos mouth = mouth(owner);
		if (mouth != null) {
			Vec3 at = Vec3.atBottomCenterOf(mouth);
			aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
			aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35F, 0.6F);
			KebunPayloads.fx(aula.level(), KebunPayloads.FX_DEEPEN, at, next);
		}
		return null;
	}

	private TambangRoh() {
	}
}
